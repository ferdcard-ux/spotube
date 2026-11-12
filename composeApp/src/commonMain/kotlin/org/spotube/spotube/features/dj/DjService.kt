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

/** El nombre es presentación local; nunca altera las recomendaciones ni los identificadores. */
fun normalizeDjName(value: String): String = value.filterNot { it.isISOControl() }.trim().take(24).ifBlank { "Livi" }

data class DjUiState(
    val name: String = "Livi",
    val enabled: Boolean = false,
    val available: Boolean = true,
    val playing: Boolean = false,
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
    private val aiPlanner: DjAiPlanner? = null,
) {
    private val player = audioPlayer as TransitionAudioPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val state = MutableStateFlow(DjUiState())
    val uiState = state.asStateFlow()
    private var blocksJob: Job? = null
    private var generation = 0L
    private val lifecycle = DjSetLifecycle()

    init {
        player.beforeTrack = {
            if (state.value.enabled && lifecycle.introduction(queue.currentCollectionEntryFlow.value)) {
                player.announcementsEnabled = false
                val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
                val greeting = getString(when (hour) {
                    in 5..11 -> Res.string.dj_good_morning
                    in 12..18 -> Res.string.dj_good_afternoon
                    else -> Res.string.dj_good_evening
                })
                // La plantilla solo afirma el nombre configurado y el tamaño real de la cola.
                speak(getString(Res.string.dj_set_intro, greeting, state.value.name, player.playlistFlow.value.size))
            }
        }
        player.afterQueue = {
            if (state.value.enabled && lifecycle.ending(queue.currentCollectionEntryFlow.value)) {
                speak(getString(Res.string.dj_set_outro, state.value.name))
            }
        }
        scope.launch {
            combine(settings.userSettings, player.synchronizedPlaybackFlow) { prefs, synced ->
                Triple(normalizeDjName(prefs.djName), prefs.djMode, !synced)
            }.distinctUntilChanged().collect { (name, enabled, available) ->
                state.update { it.copy(name = name, enabled = enabled, available = available) }
                if (!enabled || !available) {
                    generation++
                    blocksJob?.cancelAndJoin()
                    lifecycle.clear()
                    player.announcementsEnabled = false
                    player.finishAnnouncement()
                    speech.stop()
                    val message = if (available) null else getString(Res.string.dj_jam_paused)
                    state.update { it.copy(speaking = false, loading = false, script = null, message = message) }
                }
                // Activar el ajuste no inicia ni reemplaza la música que ya suena.
            }
        }
        scope.launch {
            combine(queue.currentCollectionEntryFlow, player.playerStateFlow) { collection, playback ->
                lifecycle.owns(collection) to (playback == PlayerState.PLAYING)
            }.distinctUntilChanged().collect { (owns, playing) ->
                state.update { it.copy(playing = owns && playing) }
                if (!owns) {
                    player.announcementsEnabled = false
                    if (state.value.speaking) { player.finishAnnouncement(); speech.stop() }
                }
            }
        }
        scope.launch {
            player.currentMediaItemFlow.collectLatest { item ->
                if (item == null) return@collectLatest
                player.playerStateFlow.first { it == PlayerState.PLAYING }
                delay(10_000)
                if (player.playerStateFlow.value != PlayerState.PLAYING) return@collectLatest
                val listening = when (val entry = queue.getCurrentQueueEntry()) {
                    is QueueEntry.StreamingTrack -> ListeningEntry(entry.track.id, entry.track.title,
                        entry.track.artists.map { it.name }, entry.track.genre)
                    is QueueEntry.LocalTrack -> ListeningEntry(entry.url, entry.name, entry.artists, item.genre, streaming = false)
                    null -> return@collectLatest
                }
                history.record(listening)
            }
        }
    }

    private suspend fun speak(script: String) {
        if (!state.value.enabled || !state.value.available || !lifecycle.owns(queue.currentCollectionEntryFlow.value)) return
        state.update { it.copy(speaking = true, script = script, message = null) }
        try { withTimeout(15_000) { speech.speak(script, settings.userSettings.value.language.locale) } }
        catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
            val message = getString(Res.string.dj_unavailable)
            state.update { it.copy(message = message) }
        } catch (_: Exception) {
            val message = getString(Res.string.dj_unavailable)
            state.update { it.copy(message = message) }
        } finally { speech.stop(); state.update { it.copy(speaking = false, script = null) } }
    }

    /** Cada toque sustituye la petición anterior; una respuesta atrasada nunca reemplaza el set nuevo. */
    fun startNewSet() {
        if (!state.value.enabled || !state.value.available) {
            scope.launch {
                val message = getString(Res.string.dj_enable_in_settings)
                state.update { it.copy(message = message) }
            }
            return
        }
        val previous = blocksJob
        val token = ++generation
        previous?.cancel()
        blocksJob = scope.launch {
            previous?.join()
            player.finishAnnouncement()
            state.update { it.copy(loading = true, message = null) }
            try {
                val recent = history.recent()
                val candidates = collectCandidates(recent)
                val blockedTracks = blacklist.blacklistedTracks.first().map { it.id }.toSet()
                val blockedArtists = blacklist.blacklistedArtists.first().map { it.id }.toSet()
                val favorites = recent.flatMap { it.artists }.groupingBy { it }.eachCount()
                val genre = recent.mapNotNull { it.genre }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val eligible = candidates.distinctBy { it.id }.filter { track ->
                    track.id !in blockedTracks && track.artists.none { it.id in blockedArtists }
                }
                val recentIds = recent.map { it.trackId }.toSet()
                val ranked = eligible.shuffled().sortedByDescending { track ->
                    track.artists.sumOf { favorites[it.name] ?: 0 } + if (genre != null && genre == track.genre) 2 else 0
                }
                // Alterna afinidad y descubrimiento; no exige historial para aprovechar playlists.
                val familiar = ranked.filter { it.id in recentIds }
                val discoveries = ranked.filter { it.id !in recentIds }
                val pool = buildList {
                    repeat(maxOf(familiar.size, discoveries.size)) { index ->
                        discoveries.getOrNull(index)?.let(::add)
                        familiar.getOrNull(index)?.let(::add)
                    }
                }
                val currentIds = queue.queueFlow.value.filterIsInstance<QueueEntry.StreamingTrack>().map { it.track.id }.toSet()
                val fresh = pool.filter { it.id !in currentIds }
                val choices = (fresh + pool).distinctBy { it.id }
                val tracks = aiPlanner?.order(choices) ?: choices.take(DJ_SET_SIZE)
                if (tracks.size < DJ_SET_SIZE) error("Insufficient DJ candidates")
                ensureActive()
                if (token != generation || !state.value.enabled || !state.value.available) return@launch
                val setId = "${Clock.System.now().toEpochMilliseconds()}-$token"
                lifecycle.begin(setId)
                player.announcementsEnabled = true
                player.loop(LoopState.NONE)
                queue.load(tracks.map { QueueEntry.StreamingTrack(it, "") }, collectionEntry = QueueCollectionEntry.DjSet(setId))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val message = getString(Res.string.dj_no_recommendations)
                player.announcementsEnabled = false
                state.update { it.copy(message = message) }
            } finally {
                if (token == generation) state.update { it.copy(loading = false) }
            }
        }
    }

    private suspend fun collectCandidates(recent: List<ListeningEntry>): List<org.spotube.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack> {
        val provider = plugins.selectedMetadataPlugin.value ?: error("No metadata provider")
        val candidates = mutableListOf<org.spotube.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack>()
        candidates += queue.queueFlow.value.filterIsInstance<QueueEntry.StreamingTrack>().map { it.track }
        suspend fun <T> optional(action: suspend () -> T): T? = try { withTimeoutOrNull(12_000) { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        val saved = optional { provider.use { metadataPlaylistAPI.savedPlaylists().items } }.orEmpty()
        for (playlist in saved.shuffled().take(3)) {
            candidates += optional { provider.use { metadataPlaylistAPI.getPlaylistTracks(playlist.id).items } }.orEmpty().take(30)
        }
        val featured = optional { provider.use { metadataBrowseAPI.featured() } }.orEmpty()
        candidates += featured.filterIsInstance<org.spotube.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseItem.Track>().map { it.data }
        featured.filterIsInstance<org.spotube.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseItem.Playlist>()
            .shuffled().firstOrNull()?.let { playlist ->
                candidates += optional { provider.use { metadataPlaylistAPI.getPlaylistTracks(playlist.data.id).items } }.orEmpty().take(20)
            }
        val seeds = (recent.filter { it.streaming }.map { it.trackId } + candidates.map { it.id }).distinct().take(5)
        if (seeds.isNotEmpty()) candidates.addAll(0,
            optional { provider.use { metadataTrackAPI.recommendationsBasedOnTracks(seeds, limit = 20) } }.orEmpty())
        return candidates
    }

    fun dismissMessage() { state.update { it.copy(message = null) } }
    fun close() {
        scope.cancel(); speech.stop(); player.beforeTrack = null; player.afterQueue = null; player.announcementsEnabled = false
    }
}
