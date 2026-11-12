package org.spotube.spotube.features.dj

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.spotube.spotube.core.i18n.getString
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.core.db.Database
import org.spotube.spotube.features.player.TransitionAudioPlayer
import org.spotube.spotube.modules.blacklist.BlacklistRepository
import org.spotube.spotube.modules.plugin.PluginProvider
import org.spotube.spotube.modules.settings.SettingsRepository
import spotube.composeapp.generated.resources.*
import kotlin.time.Clock

@Serializable
data class ListeningEntry(val trackId: String, val title: String, val artists: List<String>, val genre: String?,
                          val streaming: Boolean = true)

class DjHistory(private val database: Database) {
    private val key = stringPreferencesKey("dj_listening_history")
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun recent(): List<ListeningEntry> = database.audioPlayerQueueDataStore.data.first()[key]
        ?.let { runCatching { json.decodeFromString<List<ListeningEntry>>(it) }.getOrNull() }.orEmpty()

    suspend fun record(entry: ListeningEntry) {
        database.audioPlayerQueueDataStore.edit { prefs ->
            val previous = prefs[key]?.let {
                runCatching { json.decodeFromString<List<ListeningEntry>>(it) }.getOrNull()
            }.orEmpty()
            // Conserva repeticiones: son evidencia de preferencias, no duplicados accidentales.
            prefs[key] = json.encodeToString((listOf(entry) + previous).take(20))
        }
    }
}

data class DjUiState(
    val enabled: Boolean = false,
    val available: Boolean = true,
    val speaking: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val script: String? = null,
)

class DjService(
    audioPlayer: AudioPlayerInterface,
    private val queue: AudioPlayerQueue,
    private val settings: SettingsRepository,
    private val plugins: PluginProvider,
    private val history: DjHistory,
    private val blacklist: BlacklistRepository,
    private val speech: NativeSpeech,
) {
    private val player = audioPlayer as TransitionAudioPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val state = MutableStateFlow(DjUiState())
    val uiState = state.asStateFlow()
    private var blocksJob: Job? = null
    private var blockUrls = emptySet<String>()
    private var blockIntroPending = true

    init {
        player.beforeTrack = { item -> introduce(item) }
        scope.launch {
            combine(settings.userSettings.map { it.djMode }, player.synchronizedPlaybackFlow) { enabled, synced ->
                (enabled && !synced) to !synced
            }.distinctUntilChanged().collect { (enabled, available) ->
                state.update { it.copy(enabled = enabled, available = available) }
                player.announcementsEnabled = enabled
                blocksJob?.cancel()
                if (enabled) blocksJob = scope.launch { loadBlock() }
                else {
                    player.finishAnnouncement()
                    speech.stop()
                    val message = if (available) null else getString(Res.string.dj_jam_paused)
                    state.update { it.copy(speaking = false, loading = false, script = null, message = message) }
                }
            }
        }
        scope.launch {
            player.currentMediaItemFlow.collectLatest { item ->
                if (item == null) return@collectLatest
                // Una canción cargada pero pausada no cuenta como escucha.
                player.playerStateFlow.first { it == PlayerState.PLAYING }
                delay(10_000)
                if (player.playerStateFlow.value != PlayerState.PLAYING) return@collectLatest
                val listening = when (val entry = queue.getCurrentQueueEntry()) {
                    is QueueEntry.StreamingTrack -> ListeningEntry(entry.track.id, entry.track.title,
                        entry.track.artists.map { it.name }, entry.track.genre)
                    is QueueEntry.LocalTrack -> ListeningEntry(entry.url, entry.name, entry.artists,
                        item.genre, streaming = false)
                    null -> return@collectLatest
                }
                history.record(listening)
            }
        }
        scope.launch {
            player.currentMediaItemFlow.filterNotNull().collect { item ->
                if (state.value.enabled && item.url in blockUrls &&
                    item == player.playlistFlow.value.lastOrNull() && blocksJob?.isActive != true) {
                    blocksJob = scope.launch { loadBlock(append = true) }
                }
            }
        }
    }

    private suspend fun introduce(item: MediaItem) {
        if (!state.value.enabled) return
        val entries = history.recent()
        val favorite = entries.flatMap { it.artists }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val introduction = if (favorite != null && blockIntroPending) getString(Res.string.dj_intro_artist, favorite, item.title, item.artist)
        else getString(Res.string.dj_intro_track, item.title, item.artist)
        val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
        val greeting = if (!blockIntroPending) "" else getString(when (hour) {
            in 5..11 -> Res.string.dj_good_morning
            in 12..18 -> Res.string.dj_good_afternoon
            else -> Res.string.dj_good_evening
        })
        val script = listOf(greeting, introduction).filter { it.isNotBlank() }.joinToString(" ")
        blockIntroPending = false
        state.update { it.copy(speaking = true, script = script, message = null) }
        try {
            withTimeout(15_000) { speech.speak(script, settings.userSettings.value.language.locale) }
        } catch (e: TimeoutCancellationException) {
            val message = getString(Res.string.dj_unavailable)
            state.update { it.copy(message = message) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val message = getString(Res.string.dj_unavailable)
            state.update { it.copy(message = message) }
        } finally {
            speech.stop()
            state.update { it.copy(speaking = false, script = null) }
        }
    }

    private suspend fun loadBlock(append: Boolean = false) {
        state.update { it.copy(loading = true, message = null) }
        try {
            val recent = history.recent()
            if (recent.isEmpty()) {
                val message = getString(Res.string.dj_history_empty)
                state.update { it.copy(message = message) }
                return
            }
            val provider = plugins.selectedMetadataPlugin.value ?: error("No metadata provider")
            // Los identificadores de archivos locales no son válidos para el proveedor de recomendaciones.
            val seeds = recent.filter { it.streaming }.distinctBy { it.trackId }.take(5).map { it.trackId }
            if (seeds.isEmpty()) {
                val message = getString(Res.string.dj_history_empty)
                state.update { it.copy(message = message) }
                return
            }
            val candidates = provider.use {
                metadataTrackAPI.recommendationsBasedOnTracks(seeds, limit = 20)
            }
            val blockedTracks = blacklist.blacklistedTracks.first().map { it.id }.toSet()
            val blockedArtists = blacklist.blacklistedArtists.first().map { it.id }.toSet()
            val favorites = recent.flatMap { it.artists }.groupingBy { it }.eachCount()
            val genre = recent.mapNotNull { it.genre }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val tracks = candidates.distinctBy { it.id }.filter { track ->
                track.id !in blockedTracks && track.artists.none { it.id in blockedArtists }
            }.sortedByDescending { track ->
                track.artists.sumOf { favorites[it.name] ?: 0 } + if (genre != null && genre == track.genre) 2 else 0
            }.take(4)
            if (tracks.isEmpty()) {
                val message = getString(Res.string.dj_no_recommendations)
                state.update { it.copy(message = message) }
                return
            }
            val entries = tracks.map { QueueEntry.StreamingTrack(it, "") }
            if (!state.value.enabled || player.synchronizedPlaybackFlow.value) return
            blockIntroPending = true
            // El siguiente bloque se añade antes del final para que no haya espera de red entre bloques.
            if (append) queue.addAllToQueue(entries) else queue.load(entries)
            blockUrls = player.playlistFlow.value.takeLast(tracks.size).map { it.url }.toSet()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val message = getString(Res.string.dj_no_recommendations)
            state.update { it.copy(message = message) }
        } finally { state.update { it.copy(loading = false) } }
    }

    fun toggle() {
        scope.launch { settings.updateSettings(settings.userSettings.value.copy(djMode = !settings.userSettings.value.djMode)) }
    }

    fun close() {
        scope.cancel()
        speech.stop()
        player.beforeTrack = null
        player.announcementsEnabled = false
    }
}
