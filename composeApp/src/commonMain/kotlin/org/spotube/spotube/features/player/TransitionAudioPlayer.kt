package org.spotube.spotube.features.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.modules.settings.UserSettingsSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Dos motores nativos; la cola pública es independiente del búfer de cada motor. */
class TransitionAudioPlayer(
    first: AudioPlayerInterface,
    private val createDeck: () -> AudioPlayerInterface,
    private val settings: UserSettingsSource,
    /** Reloj monótono en ms; las pruebas lo sustituyen. */
    private val nowMs: () -> Long = { MONOTONIC_START.elapsedNow().inWholeMilliseconds },
) : AudioPlayerInterface {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val log = co.touchlab.kermit.Logger.withTag("Transition")
    private var lastFadeLog = -1
    private val mutex = Mutex()
    private val active = MutableStateFlow(first)
    val activeDeck: StateFlow<AudioPlayerInterface> = active.asStateFlow()
    private var standby: AudioPlayerInterface? = null
    private var prepared: MediaItem? = null
    private var fading = false
    /** Sin fundido: la siguiente ya arrancó y la saliente termina sus últimos milisegundos. */
    private var handoff = false
    private var handoffAt = 0L
    /** La precarga falló (error del motor en espera); se reintenta con espera entre intentos. */
    @Volatile private var standbyFailed = false
    private var preloadAttempts = 0
    private var retryAfter = 0L
    /** Última posición informada por cada motor y cuándo cambió, para estimar la real entre informes. */
    private class DeckClock(var position: Duration, var mark: Long)
    private val clocks = HashMap<AudioPlayerInterface, DeckClock>()
    private var announcementJob: Job? = null
    private var fadeDuration = Duration.ZERO
    private var incomingStart = Duration.ZERO
    private var recoveringTempo: Float? = null
    private var recoveryStart = Duration.ZERO
    private var fadeCurve = CrossfadeCurve.EQUAL_POWER
    private var fadeClockRatio = 1.0
    private var index = -1
    private var original = emptyList<MediaItem>()
    private var disposed = false
    private var wantsPlay = false
    var playlistContext = false
    var playlistMixOverride: Boolean? = null
    // El DJ intercepta el inicio antes de reproducir; cancelar la operación cancela también la voz.
    var beforeTrack: (suspend (MediaItem) -> Unit)? = null
    var afterQueue: (suspend () -> Unit)? = null
    var announcementsEnabled = false
    private val synchronizedPlayback = MutableStateFlow(false)
    val synchronizedPlaybackFlow: StateFlow<Boolean> = synchronizedPlayback.asStateFlow()

    override val playlistFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    override val currentMediaItemFlow = MutableStateFlow<MediaItem?>(null)
    override val loopStateFlow = MutableStateFlow(LoopState.NONE)
    override val shuffleModeFlow = MutableStateFlow(false)
    override val volumeFlow = MutableStateFlow(1f)
    override val playbackSpeedFlow = MutableStateFlow(1f)
    private val completion = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    override val completionFlow = completion.asSharedFlow()
    override val errorFlow = errors.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> follow(initial: T, flow: (AudioPlayerInterface) -> StateFlow<T>): StateFlow<T> =
        active.flatMapLatest(flow).stateIn(scope, SharingStarted.Eagerly, initial)

    override val playerStateFlow = follow(PlayerState.IDLE) { it.playerStateFlow }
    override val durationFlow = follow(Duration.ZERO) { it.durationFlow }
    override val positionFlow = follow(Duration.ZERO) { it.positionFlow }
    override val bufferingPositionFlow = follow(Duration.ZERO) { it.bufferingPositionFlow }

    init {
        scope.launch {
            while (isActive) {
                try { mutex.withLock { tick() } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    errors.tryEmit(e)
                    mutex.withLock { cancelTransition() }
                }
                delay(20)
            }
        }
        scope.launch {
            active.collectLatest { deck -> deck.errorFlow.collect { errors.emit(it) } }
        }
        watchStandbyErrors(first)
    }

    /** Un error del motor que no suena es un fallo de precarga: se marca para reintentarla. */
    private fun watchStandbyErrors(deck: AudioPlayerInterface) {
        scope.launch { deck.errorFlow.collect { if (deck !== active.value) standbyFailed = true } }
    }

    /**
     * Algunos motores (ExoPlayer en Android) informan la posición cada 250 ms. Para detectar el
     * final con precisión se extrapola desde el último informe mientras suena, con un tope.
     */
    private fun estimatedPosition(deck: AudioPlayerInterface): Duration {
        val reported = deck.positionFlow.value
        val now = nowMs()
        val clock = clocks.getOrPut(deck) { DeckClock(reported, now) }
        if (reported != clock.position) {
            clock.position = reported; clock.mark = now
            return reported
        }
        if (deck.playerStateFlow.value != PlayerState.PLAYING) { clock.mark = now; return reported }
        val elapsed = (now - clock.mark).coerceIn(0, MAX_EXTRAPOLATION_MS)
        return reported + (elapsed * deck.playbackSpeedFlow.value.toDouble()).milliseconds
    }

    private fun nextIndex(): Int? = when {
        index !in playlistFlow.value.indices -> null
        loopStateFlow.value == LoopState.ONE -> index
        index < playlistFlow.value.lastIndex -> index + 1
        loopStateFlow.value == LoopState.ALL -> 0
        else -> null
    }

    private suspend fun tick() {
        if (!wantsPlay || disposed) return
        val deck = active.value
        val duration = deck.durationFlow.value
        val position = deck.positionFlow.value
        recoveringTempo?.let { ratio ->
            val bpm = currentMediaItemFlow.value?.bpm
            if (bpm != null && bpm.isFinite() && bpm > 0f) {
                val restored = restoredTempoRatio(ratio, (position - recoveryStart).inWholeMilliseconds, bpm)
                val targetSpeed = playbackSpeedFlow.value * restored
                if (kotlin.math.abs(deck.playbackSpeedFlow.value - targetSpeed) > 0.00001f) deck.setPlaybackSpeed(targetSpeed)
                if (kotlin.math.abs(restored - 1f) < 0.0001f) recoveringTempo = null
            } else recoveringTempo = null
        }
        if (handoff) {
            val outgoingEnded = deck.playerStateFlow.value == PlayerState.COMPLETED ||
                (duration.isFinite() && duration > Duration.ZERO && estimatedPosition(deck) >= duration)
            if (outgoingEnded || nowMs() - handoffAt >= GAPLESS_LEAD_MS + HANDOFF_GRACE_MS) promote()
            return
        }
        if (fading) {
            // El reloj del audio entrante se detiene con pause y no avanza durante buffering.
            // Posición estimada entre informes: el volumen avanza en cada ciclo (20 ms), no a saltos de 250 ms.
            val elapsed = (estimatedPosition(standby!!) - incomingStart).coerceAtLeast(Duration.ZERO) * fadeClockRatio
            val progress = (elapsed / fadeDuration).toFloat().coerceIn(0f, 1f)
            val (a, b) = crossfadeGains(progress, fadeCurve)
            deck.setVolume(volumeFlow.value * a)
            standby!!.setVolume(volumeFlow.value * b)
            val step = (progress * 10).toInt()
            if (step != lastFadeLog) {
                lastFadeLog = step
                log.i { "fade progress=${(progress * 100).toInt()}% out=${(a * 1000).toInt() / 1000f} in=${(b * 1000).toInt() / 1000f} " +
                    "master=${volumeFlow.value} inPos=${standby!!.positionFlow.value.inWholeMilliseconds} outPos=${deck.positionFlow.value.inWholeMilliseconds}" }
            }
            if (progress >= 1f || deck.playerStateFlow.value == PlayerState.COMPLETED) promote()
            return
        }
        val next = nextIndex()
        if (next == null) {
            if (deck.playerStateFlow.value == PlayerState.COMPLETED) {
                wantsPlay = false
                completion.tryEmit(Unit)
                afterQueue?.takeUnless { synchronizedPlayback.value }?.let { finish ->
                    announcementJob = scope.launch { finish() }
                }
            }
            return
        }
        val item = playlistFlow.value[next]
        val prefs = settings.userSettings.value
        val plan = TransitionPlan.create(prefs.crossfadeSeconds, (playlistMixOverride ?: prefs.autoMixEnabled) && !synchronizedPlayback.value,
            playlistContext, currentMediaItemFlow.value?.bpm, item.bpm)
        val seconds = if (announcementsEnabled || synchronizedPlayback.value || loopStateFlow.value == LoopState.ONE) 0 else plan.seconds
        val currentBeatOffset = currentMediaItemFlow.value?.beatOffsetMs?.takeIf { it >= 0 && it < duration.inWholeMilliseconds }
        val nextBeatOffset = item.beatOffsetMs?.takeIf { it >= 0 && it < item.duration.inWholeMilliseconds }
        val musicalMillis = if (seconds > 0 && plan.beatMatching) mixDurationMillis(
            currentMediaItemFlow.value?.bpm, currentBeatOffset,
            nextBeatOffset, prefs.autoMixBars) else null
        val length = minOf((musicalMillis ?: seconds * 1_000L).milliseconds, duration / 2)
        if (duration <= Duration.ZERO || !duration.isFinite()) {
            if (deck.playerStateFlow.value == PlayerState.COMPLETED) start(next, true)
            return
        }
        val remaining = duration - estimatedPosition(deck)
        // Precarga a partir del primer minuto: resolver el audio y llenar el búfer lleva varios
        // segundos. En pistas cortas se conserva la ventana mínima antes del fundido.
        val fallbackWindow = maxOf(5_000L, length.inWholeMilliseconds + 5_000L).milliseconds
        val preloadDue = position >= PRELOAD_AFTER || remaining <= fallbackWindow
        val retryDue = prepared == item && standbyFailed && preloadAttempts < MAX_PRELOAD_ATTEMPTS && nowMs() >= retryAfter
        if ((prefs.gaplessEnabled || seconds > 0) && preloadDue && (prepared != item || retryDue)) {
            val buffer = standby ?: createDeck().also { standby = it; watchStandbyErrors(it) }
            if (prepared != item) preloadAttempts = 0
            standbyFailed = false
            preloadAttempts++
            retryAfter = nowMs() + PRELOAD_RETRY_DELAY_MS
            buffer.setVolume(0f)
            buffer.prepareForTransition(item)
            buffer.setPlaybackSpeed(playbackSpeedFlow.value * plan.tempoRatio)
            if (plan.beatMatching && nextBeatOffset != null) {
                buffer.seekTo(nextBeatOffset.milliseconds)
            }
            prepared = item
        }
        // Un motor en espera que falló (o quedó inactivo) no se promociona: se carga de nuevo.
        val standbyUsable = prepared == item && !standbyFailed &&
            standby?.playerStateFlow?.value.let { it != null && it != PlayerState.IDLE }
        val ended = deck.playerStateFlow.value == PlayerState.COMPLETED || remaining <= Duration.ZERO
        if (ended) {
            if (standbyUsable && !announcementsEnabled) {
                standby!!.setVolume(volumeFlow.value)
                standby!!.play()
                promote()
            } else start(next, true)
            return
        }
        // La rejilla solo es válida si el proveedor informa el primer beat; BPM solo no da su fase.
        val current = currentMediaItemFlow.value
        val beatAlignedStart = if (musicalMillis != null && currentBeatOffset != null && current?.bpm != null) {
            val beatMs = 60_000.0 / current.bpm
            val offset = currentBeatOffset.toDouble()
            (offset + kotlin.math.ceil(((duration - length).inWholeMilliseconds - offset) / beatMs) * beatMs)
                .toLong().milliseconds
        } else duration - length
        val ready = standbyUsable && standby?.playerStateFlow?.value in listOf(PlayerState.READY, PlayerState.PAUSED)
        // Sin pausas: la siguiente arranca unos milisegundos antes del final para cubrir lo que
        // tarda el motor en sacar audio; la saliente termina sola y después se promociona.
        if (seconds == 0 && prefs.gaplessEnabled && ready && !announcementsEnabled &&
            remaining <= GAPLESS_LEAD_MS.milliseconds && deck.playerStateFlow.value == PlayerState.PLAYING) {
            standby!!.setVolume(volumeFlow.value)
            standby!!.play()
            handoff = true
            handoffAt = nowMs()
            return
        }
        if (seconds > 0 && prepared == item && ready && estimatedPosition(deck) >= beatAlignedStart &&
            deck.playerStateFlow.value == PlayerState.PLAYING) {
            // Algunos motores aceptan el tempo solo cuando el medio ya está preparado.
            standby!!.setPlaybackSpeed(deck.playbackSpeedFlow.value * plan.tempoRatio)
            recoveringTempo = null
            fadeDuration = remaining.coerceAtLeast(1.milliseconds)
            fadeCurve = prefs.crossfadeCurve
            incomingStart = standby!!.positionFlow.value
            // Ambos relojes expresan posición en su archivo; se convierten al reloj de salida.
            fadeClockRatio = deck.playbackSpeedFlow.value.toDouble() / standby!!.playbackSpeedFlow.value
            standby!!.play()
            fading = true
            lastFadeLog = -1
            log.i { "fade start curve=$fadeCurve length=${fadeDuration.inWholeMilliseconds}ms configured=${length.inWholeMilliseconds}ms " +
                "plan=$plan bpm=${current?.bpm}->${item.bpm} mix=${playlistMixOverride ?: prefs.autoMixEnabled} playlist=$playlistContext" }
        }
    }

    private suspend fun promote() {
        val next = nextIndex() ?: return
        log.i { "promote fading=$fading handoff=$handoff" }
        val old = active.value
        val incomingRatio = standby!!.playbackSpeedFlow.value / playbackSpeedFlow.value
        active.value = standby!!
        standby = old
        old.stop()
        index = next
        currentMediaItemFlow.value = playlistFlow.value[index]
        active.value.setVolume(volumeFlow.value)
        // Se recupera el tempo durante 16 compases de la pista entrante, sin salto al promocionarla.
        recoveryStart = active.value.positionFlow.value
        recoveringTempo = incomingRatio.takeIf { kotlin.math.abs(it - 1f) > 0.0001f }
        if (recoveringTempo == null) active.value.setPlaybackSpeed(playbackSpeedFlow.value)
        prepared = null
        fading = false
        handoff = false
        standbyFailed = false
        preloadAttempts = 0
    }

    private suspend fun cancelTransition() {
        if (fading || handoff) log.w { "transition cancelled mid-fade (queue change, seek or loop)" }
        standby?.stop()
        prepared = null
        fading = false
        handoff = false
        standbyFailed = false
        preloadAttempts = 0
        active.value.setVolume(volumeFlow.value)
    }

    private suspend fun start(target: Int, autoPlay: Boolean) {
        announcementJob?.cancelAndJoin()
        cancelTransition()
        active.value.stop()
        recoveringTempo = null
        index = target
        val item = playlistFlow.value.getOrNull(index)
        currentMediaItemFlow.value = item
        wantsPlay = autoPlay && item != null
        active.value.load(listOfNotNull(item), false, 0)
        active.value.setPlaybackSpeed(playbackSpeedFlow.value)
        if (wantsPlay && item != null) {
            val announce = beforeTrack.takeUnless { synchronizedPlayback.value }
            if (announce == null) active.value.play()
            else announcementJob = scope.launch {
                try { announce(item) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { errors.emit(e) }
                mutex.withLock { if (wantsPlay) active.value.play() }
            }
        }
    }

    override suspend fun load(playlist: List<MediaItem>, autoPlay: Boolean, startPosition: Int) = mutex.withLock {
        original = playlist.toList()
        playlistFlow.value = original
        shuffleModeFlow.value = false
        start(if (playlist.isEmpty()) -1 else startPosition.coerceIn(playlist.indices), autoPlay)
    }
    override suspend fun play() = mutex.withLock {
        wantsPlay = true
        if (announcementJob?.isActive == true) return@withLock
        active.value.play()
        if (fading || handoff) standby?.play()
    }
    override suspend fun pause() = mutex.withLock {
        wantsPlay = false
        announcementJob?.cancelAndJoin()
        active.value.pause()
        if (fading || handoff) standby?.pause()
    }
    override suspend fun stop() = mutex.withLock {
        wantsPlay = false
        recoveringTempo = null
        announcementJob?.cancelAndJoin()
        cancelTransition()
        active.value.stop()
        active.value.setPlaybackSpeed(playbackSpeedFlow.value)
    }
    override suspend fun seekTo(position: Duration) = mutex.withLock {
        cancelTransition()
        recoveringTempo = null
        active.value.setPlaybackSpeed(playbackSpeedFlow.value)
        active.value.seekTo(position)
    }
    suspend fun finishAnnouncement() = mutex.withLock {
        announcementJob?.cancelAndJoin()
        if (wantsPlay) active.value.play()
    }
    override suspend fun loop(state: LoopState) = mutex.withLock {
        cancelTransition()
        loopStateFlow.value = state
    }
    override suspend fun setSynchronizedPlayback(enabled: Boolean) = mutex.withLock {
        if (synchronizedPlayback.value == enabled) return@withLock
        cancelTransition()
        synchronizedPlayback.value = enabled
        if (enabled) {
            recoveringTempo = null
            active.value.setPlaybackSpeed(playbackSpeedFlow.value)
            announcementJob?.cancelAndJoin()
            if (wantsPlay) active.value.play()
        }
    }
    override suspend fun shuffle(enabled: Boolean) = mutex.withLock {
        cancelTransition()
        val current = currentMediaItemFlow.value
        playlistFlow.value = if (enabled) listOfNotNull(current) +
            original.filterNot { it == current }.shuffled() else original
        index = playlistFlow.value.indexOf(current)
        shuffleModeFlow.value = enabled
    }
    override suspend fun skipToNext() = mutex.withLock {
        // Siguiente explícito avanza incluso con repetición de una canción.
        val next = when {
            index < 0 -> null
            index < playlistFlow.value.lastIndex -> index + 1
            loopStateFlow.value == LoopState.ALL -> 0
            else -> null
        }
        next?.let { start(it, true) }
        Unit
    }
    /**
     * Como en cualquier reproductor: con la pista avanzada, el primer toque vuelve a su inicio;
     * un segundo toque (ya en los primeros segundos) pasa a la pista anterior.
     */
    override suspend fun skipToPrevious() = mutex.withLock {
        if (index < 0) return@withLock
        if (active.value.positionFlow.value > REWIND_THRESHOLD || index == 0) {
            cancelTransition()
            recoveringTempo = null
            active.value.setPlaybackSpeed(playbackSpeedFlow.value)
            active.value.seekTo(Duration.ZERO)
        } else start(index - 1, true)
    }
    override suspend fun jumpTo(index: Int) = mutex.withLock {
        if (index in playlistFlow.value.indices) start(index, true)
    }
    override suspend fun addMediaItem(mediaItem: MediaItem) = mutex.withLock {
        cancelTransition()
        original = original + mediaItem
        playlistFlow.value = playlistFlow.value + mediaItem
        if (index < 0) start(0, false)
    }
    override suspend fun insertMediaItemAtNextIndex(mediaItem: MediaItem) = mutex.withLock {
        cancelTransition()
        original = original + mediaItem
        playlistFlow.value = playlistFlow.value.toMutableList().apply { add((index + 1).coerceAtLeast(0), mediaItem) }
        if (index < 0) start(0, false)
    }
    override suspend fun removeMediaItem(mediaItem: MediaItem) = mutex.withLock {
        cancelTransition()
        val current = currentMediaItemFlow.value
        original = original.filterNot { it.url == mediaItem.url }
        playlistFlow.value = playlistFlow.value.filterNot { it.url == mediaItem.url }
        if (current?.url == mediaItem.url) start(
            if (playlistFlow.value.isEmpty()) -1 else index.coerceAtMost(playlistFlow.value.lastIndex), wantsPlay)
        else index = playlistFlow.value.indexOf(current)
    }
    override suspend fun moveMediaItem(fromIndex: Int, toIndex: Int) = mutex.withLock {
        if (fromIndex in playlistFlow.value.indices && toIndex in playlistFlow.value.indices) {
            cancelTransition()
            playlistFlow.value = playlistFlow.value.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            if (!shuffleModeFlow.value) original = playlistFlow.value
            index = playlistFlow.value.indexOf(currentMediaItemFlow.value)
        }
    }
    override suspend fun setVolume(volume: Float) = mutex.withLock {
        volumeFlow.value = volume.coerceIn(0f, 1f)
        if (!fading) active.value.setVolume(volumeFlow.value)
        if (handoff) standby?.setVolume(volumeFlow.value)
    }
    override suspend fun setPlaybackSpeed(speed: Float) = mutex.withLock {
        recoveringTempo = null
        val incomingRatio = standby?.playbackSpeedFlow?.value?.div(playbackSpeedFlow.value) ?: 1f
        playbackSpeedFlow.value = speed.coerceIn(0.25f, 4f)
        active.value.setPlaybackSpeed(playbackSpeedFlow.value)
        if (fading || prepared != null) standby?.setPlaybackSpeed(playbackSpeedFlow.value * incomingRatio)
        if (fading) fadeClockRatio = active.value.playbackSpeedFlow.value.toDouble() / standby!!.playbackSpeedFlow.value
    }
    override fun isDisposed() = disposed
    override fun dispose() {
        disposed = true
        scope.cancel()
        active.value.dispose()
        standby?.dispose()
    }
}

/** Inicio de la precarga de la siguiente pista, contado desde el principio de la actual. */
internal val PRELOAD_AFTER = 60.seconds
internal const val MAX_PRELOAD_ATTEMPTS = 3
/** Pasado este punto, «Anterior» rebobina la pista actual en lugar de retroceder. */
internal val REWIND_THRESHOLD = 3.seconds
internal const val PRELOAD_RETRY_DELAY_MS = 3_000L
/** Anticipo con que arranca la siguiente pista sin fundido (latencia de salida del motor). */
internal const val GAPLESS_LEAD_MS = 150L
private const val HANDOFF_GRACE_MS = 250L
private const val MAX_EXTRAPOLATION_MS = 300L
private val MONOTONIC_START = TimeSource.Monotonic.markNow()
