package org.spotube.spotube.features.dj

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.browse.MetadataBrowseItem
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.PaginationStrategy
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack
import org.spotube.spotube.core.i18n.getString
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.features.player.TransitionAudioPlayer
import org.spotube.spotube.modules.blacklist.BlacklistRepository
import org.spotube.spotube.modules.plugin.PluginProvider
import org.spotube.spotube.modules.settings.DjSpeechEngine
import org.spotube.spotube.modules.settings.SettingsRepository
import org.spotube.spotube.modules.settings.UserSettings
import spotube.composeapp.generated.resources.*
import kotlin.time.Clock

/** El nombre es presentación local; nunca altera las recomendaciones ni los identificadores. */
fun normalizeDjName(value: String): String = value.filterNot { it.isISOControl() }.trim().take(24).ifBlank { "Livi" }

/** Motor con el que el DJ está hablando ahora (icono del botón). */
enum class DjActiveVoice { GEMINI, DEVICE }

data class DjUiState(
    val name: String = "Livi",
    val activeVoice: DjActiveVoice = DjActiveVoice.DEVICE,
    val enabled: Boolean = false,
    val available: Boolean = true,
    val playing: Boolean = false,
    val speaking: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val script: String? = null,
)

/** Un bloque ya calculado: no toca la cola hasta que llegue el relevo. */
private data class PreparedDjSet(
    val tracks: List<MetadataTrack>,
    val intro: String,
    val outro: String,
    /** Intervención que cierra el set anterior y presenta este (sólo en sets encadenados). */
    val bridge: String? = null,
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
    private val voice: GeminiDjVoice? = null,
) {
    private val player = audioPlayer as TransitionAudioPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val state = MutableStateFlow(DjUiState())
    val uiState = state.asStateFlow()
    private var blocksJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchedSet: PreparedDjSet? = null
    private var generation = 0L
    /** Audio de la voz en la nube del set actual; se genera antes de que haga falta. */
    private var introClip: Deferred<DjVoiceClip?>? = null
    private var outroClip: Deferred<DjVoiceClip?>? = null
    /** Texto con el que se generó [outroClip]: sólo se reproduce si coincide con lo que se va a decir. */
    private var outroClipText: String? = null
    private val lifecycle = DjSetLifecycle()
    /** Gemini falló y habla la voz del dispositivo; el aviso sólo se muestra al entrar en este estado. */
    private val geminiDegraded = MutableStateFlow(false)

    init {
        player.beforeTrack = {
            if (state.value.enabled && lifecycle.introduction(queue.currentCollectionEntryFlow.value)) {
                player.announcementsEnabled = false
                // La plantilla solo afirma el nombre configurado y el tamaño real de la cola.
                speak(lifecycle.intro ?: DjDialogueSkill.fallbackIntro(state.value.name, player.playlistFlow.value.size, generation.toInt()),
                    introClip)
            }
        }
        player.afterQueue = {
            if (state.value.enabled && lifecycle.ending(queue.currentCollectionEntryFlow.value)) {
                // Si el siguiente set ya está listo, la misma intervención cierra este y lo presenta.
                val next = prefetchedSet
                val bridge = next?.bridge
                val script = bridge ?: lifecycle.outro ?: DjDialogueSkill.fallbackOutro(state.value.name, generation.toInt())
                speak(script, outroClip.takeIf { outroClipText == script })
                // Un set terminado no deja al usuario en el último fotograma: tras el cierre,
                // prepara otro bloque. La comprobación evita reemplazar una acción manual.
                if (state.value.enabled && lifecycle.owns(queue.currentCollectionEntryFlow.value)) {
                    // afterQueue se ejecuta como anuncio del reproductor. Se difiere un turno
                    // para no cancelar esa misma corrutina cuando la nueva cola se carga.
                    scope.launch { yield(); startNewSet(usePrefetched = true, introSpoken = bridge != null) }
                }
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
                    prefetchJob?.cancelAndJoin()
                    prefetchedSet = null
                    discardClips()
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
            combine(settings.userSettings, geminiDegraded) { prefs, degraded ->
                when (prefs.djSpeechEngine) {
                    DjSpeechEngine.GEMINI_CLOUD -> if (degraded || voice == null) DjActiveVoice.DEVICE else DjActiveVoice.GEMINI
                    DjSpeechEngine.SYSTEM -> DjActiveVoice.DEVICE
                }
            }.distinctUntilChanged().collect { active -> state.update { it.copy(activeVoice = active) } }
        }
        scope.launch {
            // Los avisos se ocultan solos; el de Jam permanece mientras la sesión pausa al DJ.
            state.map { it.message to it.available }.distinctUntilChanged().collectLatest { (message, available) ->
                if (message == null || !available) return@collectLatest
                delay(MESSAGE_VISIBLE_MS)
                state.update { if (it.message == message) it.copy(message = null) else it }
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
                        entry.track.artists.map { it.name }, entry.track.genre,
                        artistIds = entry.track.artists.map { it.id })
                    is QueueEntry.LocalTrack -> ListeningEntry(entry.url, entry.name, entry.artists, item.genre, streaming = false)
                    null -> return@collectLatest
                }
                history.record(listening)
            }
        }
    }

    private fun speechOptions(): DjSpeechOptions = settings.userSettings.value.djSpeechOptions()

    private fun cloudVoiceSelected() =
        voice != null && settings.userSettings.value.djSpeechEngine == DjSpeechEngine.GEMINI_CLOUD

    private suspend fun notify(message: org.jetbrains.compose.resources.StringResource) {
        val text = getString(message)
        state.update { it.copy(message = text) }
    }

    /** Sintetiza con Gemini; un fallo se avisa en la tarjeta y la locución usa la voz del dispositivo. */
    private suspend fun synthesizeNow(script: String): DjVoiceClip? {
        val synthesizer = voice?.takeIf { cloudVoiceSelected() } ?: return null
        return when (val result = synthesizer.synthesize(script, state.value.name, speechOptions())) {
            is DjVoiceResult.Ready -> { geminiDegraded.value = false; result.clip }
            is DjVoiceResult.Failed -> {
                // Un solo aviso por cambio de motor; el icono del botón sigue indicando el motor real.
                if (geminiDegraded.value) return null
                geminiDegraded.value = true
                notify(when (result.reason) {
                    DjVoiceFailure.NO_KEY -> Res.string.dj_voice_gemini_no_key
                    DjVoiceFailure.RATE_LIMIT -> Res.string.dj_voice_gemini_limit
                    DjVoiceFailure.AUTHENTICATION -> Res.string.dj_voice_gemini_auth
                    DjVoiceFailure.UNAVAILABLE -> Res.string.dj_voice_gemini_unavailable
                })
                null
            }
        }
    }

    /** Voz del sistema generada por adelantado (Android), que luego suena con realce de volumen. */
    private suspend fun synthesizeDevice(script: String): DjVoiceClip? = runCatching {
        val prefs = settings.userSettings.value
        speech.synthesize(script, prefs.language.locale, prefs.djSpeechOptions(DjSpeechEngine.SYSTEM))
    }.getOrNull()

    /**
     * Empieza a generar la voz en segundo plano para que esté lista cuando haga falta: Gemini si
     * está elegido y, si falla o no está elegido, la voz del sistema.
     */
    private fun synthesizeAhead(script: String): Deferred<DjVoiceClip?> =
        scope.async { (if (cloudVoiceSelected()) synthesizeNow(script) else null) ?: synthesizeDevice(script) }

    /**
     * El cierre se genera cuando ya se sabe qué viene después: espera un poco (no se gasta cuota si
     * el usuario cambia de set enseguida) y a que el siguiente set esté preparado con su enlace.
     */
    private fun scheduleOutroClip(outro: String): Deferred<DjVoiceClip?> = scope.async {
        delay(OUTRO_SYNTHESIS_DELAY_MS)
        prefetchJob?.join()
        val script = prefetchedSet?.bridge ?: outro
        outroClipText = script
        (if (cloudVoiceSelected()) synthesizeNow(script) else null) ?: synthesizeDevice(script)
    }

    private fun discardClips() {
        introClip?.cancel(); outroClip?.cancel()
        introClip = null; outroClip = null; outroClipText = null
    }

    private suspend fun speak(script: String, clip: Deferred<DjVoiceClip?>? = null) {
        if (!state.value.enabled || !state.value.available || !lifecycle.owns(queue.currentCollectionEntryFlow.value)) return
        state.update { it.copy(speaking = true, script = script) }
        scope.launch { runCatching { history.recordLine(script) } }
        try {
            // Normalmente el clip ya está listo; si no, se espera poco y se usa la voz del dispositivo.
            val ready = if (clip != null) {
                withTimeoutOrNull(CLOUD_CLIP_WAIT_MS) {
                    // Un clip descartado no cancela esta locución, solo la deja sin audio en la nube.
                    try { clip.await() } catch (cancelled: CancellationException) { currentCoroutineContext().ensureActive(); null }
                }
            } else null
            if (ready != null) {
                val played = try {
                    withTimeout(ready.durationMs + 10_000L) { speech.play(ready) }
                    true
                } catch (cancelled: CancellationException) {
                    if (cancelled !is TimeoutCancellationException) throw cancelled
                    false
                } catch (_: Exception) { false }
                if (played) return
                speech.stop()
            }
            val prefs = settings.userSettings.value
            // Sin clip en la nube, la voz del dispositivo (con la voz elegida para ella) toma el relevo.
            val options = if (prefs.djSpeechEngine == DjSpeechEngine.GEMINI_CLOUD) prefs.djSpeechOptions(DjSpeechEngine.SYSTEM)
                else prefs.djSpeechOptions()
            // Generar y decir el texto lleva tiempo proporcional a su longitud: un límite fijo de
            // 15 s cortaba los saludos largos con la voz del sistema.
            withTimeout(deviceSpeechTimeoutMs(script)) {
                speech.speak(script, prefs.language.locale, options)
            }
        }
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
    fun startNewSet() = startNewSet(usePrefetched = false, introSpoken = false)

    /**
     * El toque del usuario descarta el relevo anticipado para pedir un set realmente nuevo.
     * El final natural del set, en cambio, consume el bloque ya preparado en memoria; si su enlace
     * ya sonó ([introSpoken]), el nuevo set arranca sin otro saludo.
     */
    private fun startNewSet(usePrefetched: Boolean, introSpoken: Boolean) {
        if (!state.value.enabled || !state.value.available) {
            scope.launch {
                // Justo al abrir la app los ajustes aún no se han leído: se consulta el valor
                // guardado antes de decir que el DJ está desactivado.
                val enabled = state.value.available && runCatching { settings.getCurrentSettings().djMode }.getOrDefault(false)
                if (enabled) {
                    state.update { it.copy(enabled = true) }
                    startNewSet(usePrefetched, introSpoken)
                } else {
                    val message = getString(Res.string.dj_enable_in_settings)
                    state.update { it.copy(message = message) }
                }
            }
            return
        }
        val previous = blocksJob
        val token = ++generation
        previous?.cancel()
        if (!usePrefetched) {
            prefetchJob?.cancel()
            prefetchedSet = null
        }
        blocksJob = scope.launch {
            previous?.join()
            player.finishAnnouncement()
            state.update { it.copy(loading = true, message = null) }
            try {
                val prefetched = if (usePrefetched) prefetchedSet.also { prefetchedSet = null } else null
                val prepared = prefetched ?: prepareSet(token, withDialogue = true)
                val skipIntro = introSpoken && prefetched != null
                ensureActive()
                if (token != generation || !state.value.enabled || !state.value.available) return@launch
                discardClips()
                // El saludo se sintetiza mientras la primera pista se resuelve y se carga.
                introClip = if (skipIntro) null else synthesizeAhead(prepared.intro)
                outroClip = scheduleOutroClip(prepared.outro)
                val setId = "${Clock.System.now().toEpochMilliseconds()}-$token"
                lifecycle.begin(setId, prepared.intro, prepared.outro, introduced = skipIntro)
                player.announcementsEnabled = true
                player.loop(LoopState.NONE)
                queue.load(prepared.tracks.map { QueueEntry.StreamingTrack(it, "") }, collectionEntry = QueueCollectionEntry.DjSet(setId))
                runCatching { history.recordSet(prepared.tracks) }
                schedulePrefetch(setId, prepared.tracks)
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

    /**
     * Calcula el siguiente bloque mientras el actual sigue sonando, sin alterar la reproducción, y
     * escribe el enlace que une ambos.
     */
    private fun schedulePrefetch(setId: String, current: List<MetadataTrack>) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val seed = generation + 1
            val prepared = try { prepareSet(seed, withDialogue = false, exclude = current) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { return@launch }
            val taste = tasteContext(settings.userSettings.value, DjTasteProfile.from(history.recent()), emptyList())
            val bridge = try { aiPlanner?.bridge(state.value.name, current, prepared.tracks, taste) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                ?: DjDialogueSkill.fallbackBridge(prepared.tracks.mapNotNull { it.artists.firstOrNull()?.name }, seed.toInt())
            if (state.value.enabled && lifecycle.owns(QueueCollectionEntry.DjSet(setId))) {
                prefetchedSet = prepared.copy(bridge = bridge)
            }
        }
    }

    private suspend fun tasteContext(prefs: UserSettings, profile: DjTasteProfile, avoidArtists: List<String>) = DjTasteContext(
        moment = DjMoment.describe(DjMoment.now()),
        concise = prefs.djConcise,
        recentLines = runCatching { history.recentLines() }.getOrDefault(emptyList()),
        memory = if (!prefs.djMemoryEnabled) "" else listOf(profile.describe(), prefs.djMemoryNotes.trim())
            .filter(String::isNotBlank).joinToString(". ").take(900),
        personality = prefs.djPersonality.trim().take(400),
        avoidArtists = avoidArtists,
    )

    private suspend fun prepareSet(seed: Long, withDialogue: Boolean, exclude: List<MetadataTrack> = emptyList()): PreparedDjSet {
        val prefs = settings.userSettings.value
        val recent = history.recent()
        val sets = history.recentSets()
        val profile = DjTasteProfile.from(recent)
        val queued = queue.queueFlow.value.filterIsInstance<QueueEntry.StreamingTrack>().map { it.track } + exclude
        // Canciones de los últimos sets y de la cola: no se repiten. Artistas de los dos últimos
        // sets: descansan salvo que no haya alternativas.
        val playedIds = sets.flatMap { it.trackIds }.toSet() + queued.map { it.id }
        val restingArtists = sets.take(2).flatMap { it.artists }.toSet() + exclude.map(::artistKey)
        val restingNames = (sets.take(2).flatMap { it.artistNames } + exclude.mapNotNull { it.artists.firstOrNull()?.name }).distinct()
        val taste = tasteContext(prefs, profile, restingNames)

        val sourced = collectCandidates(profile, taste, recent)
        val blockedTracks = blacklist.blacklistedTracks.first().map { it.id }.toSet()
        val blockedArtists = blacklist.blacklistedArtists.first().map { it.id }.toSet()
        val eligible = sourced.distinctBy { it.track.id }.filter { (track, _) ->
            track.id !in blockedTracks && track.id !in playedIds && track.artists.none { it.id in blockedArtists }
        }
        val rested = eligible.filter { artistKey(it.track) !in restingArtists }
        val pool = if (rested.map { artistKey(it.track) }.distinct().size >= DJ_SET_SIZE) rested else eligible

        val favorites = profile.topArtists.toSet()
        val topGenre = profile.topGenres.firstOrNull()
        val scored = pool.shuffled().sortedByDescending { (track, source) ->
            source.weight + (if (track.artists.any { it.name in favorites }) 1 else 0) +
                (if (topGenre != null && topGenre == track.genre) 1 else 0)
        }
        // Una canción por artista: la variedad se garantiza antes de que decida la IA.
        val choices = scored.distinctBy { artistKey(it.track) }.map { it.track }.take(MAX_CHOICES)
        if (choices.size < DJ_SET_SIZE) error("Insufficient DJ candidates")
        val ordered = aiPlanner?.order(choices, taste) ?: localMix(choices, favorites)
        val tracks = enforceArtistDiversity(ordered, choices)
        val dialogue = if (withDialogue) aiPlanner?.dialogue(state.value.name, tracks, taste) else null
        return PreparedDjSet(
            tracks = tracks,
            intro = dialogue?.first ?: DjDialogueSkill.fallbackIntro(state.value.name,
                tracks.mapNotNull { it.artists.firstOrNull()?.name }, DjMoment.now(), concise = prefs.djConcise),
            outro = dialogue?.second ?: DjDialogueSkill.fallbackOutro(state.value.name, seed.toInt()),
        )
    }

    /** Sin IA: unas dos favoritas y el resto descubrimientos, alternados. */
    private fun localMix(choices: List<MetadataTrack>, favorites: Set<String>): List<MetadataTrack> {
        val (familiar, discoveries) = choices.partition { track -> track.artists.any { it.name in favorites } }
        val picked = familiar.take(2) + discoveries.take(DJ_SET_SIZE)
        return (picked + choices).distinctBy { it.id }.take(DJ_SET_SIZE).shuffled()
    }

    private enum class CandidateSource(val weight: Int) { AI_SUGGESTION(3), RELATED(2), RECOMMENDED(2), FAVORITE(1), PLAYLIST(0), FEATURED(0) }
    private data class Candidate(val track: MetadataTrack, val source: CandidateSource)

    /** Fuentes variadas en paralelo; cada una es opcional y con tiempo límite. */
    private suspend fun collectCandidates(profile: DjTasteProfile, taste: DjTasteContext, recent: List<ListeningEntry>): List<Candidate> = coroutineScope {
        val provider = plugins.selectedMetadataPlugin.value ?: error("No metadata provider")
        suspend fun <T> optional(action: suspend () -> T): T? = try { withTimeoutOrNull(12_000) { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        fun List<MetadataTrack>.from(source: CandidateSource) = map { Candidate(it, source) }

        val suggestions = async {
            val recentTitles = recent.take(30).map { "${it.title} — ${it.artists.joinToString()}" }
            val ideas = optional { aiPlanner?.suggest(AI_SUGGESTIONS, taste, recentTitles) }.orEmpty()
            ideas.map { (artist, title) ->
                async {
                    optional {
                        provider.use { metadataSearchAPI.searchTracks("$title $artist", PaginationStrategy.Offset(0, 5)).items }
                    }.orEmpty().map { it.data }.let { found ->
                        found.firstOrNull { track -> track.artists.any { it.name.equals(artist, ignoreCase = true) } }
                            ?: found.firstOrNull { track -> track.artists.any { it.name.contains(artist, ignoreCase = true) || artist.contains(it.name, ignoreCase = true) } }
                    }
                }
            }.awaitAll().filterNotNull().from(CandidateSource.AI_SUGGESTION)
        }
        val recommended = async {
            // Semillas al azar entre lo escuchado: siempre las mismas devolvían las mismas canciones.
            val seeds = recent.filter { it.streaming }.map { it.trackId }.distinct().shuffled().take(5)
            if (seeds.isEmpty()) emptyList()
            else optional { provider.use { metadataTrackAPI.recommendationsBasedOnTracks(seeds, limit = 30) } }.orEmpty()
                .from(CandidateSource.RECOMMENDED)
        }
        val related = async {
            profile.topArtistIds.shuffled().take(2).map { artistId ->
                async {
                    val similar = optional { provider.use { metadataArtistAPI.relatedArtists(artistId, PaginationStrategy.Offset(0, 10)).items } }.orEmpty()
                    similar.shuffled().take(3).map { artist ->
                        async { optional { provider.use { metadataArtistAPI.getArtistTop10Tracks(artist.id) } }.orEmpty().shuffled().take(2) }
                    }.awaitAll().flatten()
                }
            }.awaitAll().flatten().from(CandidateSource.RELATED)
        }
        val favorite = async {
            profile.topArtistIds.shuffled().take(3).map { artistId ->
                async { optional { provider.use { metadataArtistAPI.getArtistTop10Tracks(artistId) } }.orEmpty().shuffled().take(2) }
            }.awaitAll().flatten().from(CandidateSource.FAVORITE)
        }
        val playlists = async {
            val saved = optional { provider.use { metadataPlaylistAPI.savedPlaylists().items } }.orEmpty()
            saved.shuffled().take(3).map { playlist ->
                async { optional { provider.use { metadataPlaylistAPI.getPlaylistTracks(playlist.id).items } }.orEmpty().shuffled().take(15) }
            }.awaitAll().flatten().from(CandidateSource.PLAYLIST)
        }
        val featured = async {
            optional { provider.use { metadataBrowseAPI.featured() } }.orEmpty()
                .filterIsInstance<MetadataBrowseItem.Track>().map { it.data }.from(CandidateSource.FEATURED)
        }
        listOf(suggestions, recommended, related, favorite, playlists, featured).awaitAll().flatten()
    }

    fun dismissMessage() { state.update { it.copy(message = null) } }
    fun close() {
        discardClips(); scope.cancel(); prefetchedSet = null; speech.stop(); player.beforeTrack = null; player.afterQueue = null; player.announcementsEnabled = false
    }
}

/** Opciones de voz para [engine], con la voz concreta elegida para ese motor. */
fun UserSettings.djSpeechOptions(engine: DjSpeechEngine = djSpeechEngine) = DjSpeechOptions(
    djVoiceCharacter, djVoiceGender, djVoiceAccent, djVoiceExpressiveness, djVoiceSpeed, engine,
    djVoiceNames[engine.name].orEmpty())

/** ~10 caracteres por segundo hablados, más margen para generar el audio; entre 15 y 90 s. */
internal fun deviceSpeechTimeoutMs(text: String): Long = (10_000L + text.length * 140L).coerceIn(15_000L, 90_000L)

/** Tiempo que un aviso del DJ permanece en pantalla. */
private const val MESSAGE_VISIBLE_MS = 5_000L

/** Espera máxima del saludo en la nube antes de usar la voz del dispositivo. */
private const val CLOUD_CLIP_WAIT_MS = 8_000L
private const val OUTRO_SYNTHESIS_DELAY_MS = 45_000L
private const val MAX_CHOICES = 30
private const val AI_SUGGESTIONS = 10

/** Garantiza seis artistas distintos; sólo repite artista si no hay alternativas. */
private fun enforceArtistDiversity(selected: List<MetadataTrack>, pool: List<MetadataTrack>): List<MetadataTrack> {
    val artists = linkedSetOf<String>(); val result = mutableListOf<MetadataTrack>()
    (selected + pool).forEach { track -> if (result.size < DJ_SET_SIZE && artists.add(artistKey(track))) result += track }
    (selected + pool).forEach { track -> if (result.size < DJ_SET_SIZE && track !in result) result += track }
    return result
}
