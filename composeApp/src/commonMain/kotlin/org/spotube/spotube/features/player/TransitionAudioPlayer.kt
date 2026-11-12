package org.spotube.spotube.features.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.modules.settings.UserSettingsSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Dos motores nativos; la cola pública es independiente del búfer de cada motor. */
class TransitionAudioPlayer(
    first: AudioPlayerInterface,
    private val createDeck: () -> AudioPlayerInterface,
    private val settings: UserSettingsSource,
) : AudioPlayerInterface {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private val active = MutableStateFlow(first)
    val activeDeck: StateFlow<AudioPlayerInterface> = active.asStateFlow()
    private var standby: AudioPlayerInterface? = null
    private var prepared: MediaItem? = null
    private var fading = false
    private var announcementJob: Job? = null
    private var fadeDuration = Duration.ZERO
    private var incomingStart = Duration.ZERO
    private var index = -1
    private var original = emptyList<MediaItem>()
    private var disposed = false
    private var wantsPlay = false
    var playlistContext = false
    // El DJ intercepta el inicio antes de reproducir; cancelar la operación cancela también la voz.
    var beforeTrack: (suspend (MediaItem) -> Unit)? = null
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
        if (fading) {
            // El reloj del audio entrante se detiene con pause y no avanza durante buffering.
            val elapsed = standby!!.positionFlow.value - incomingStart
            val progress = (elapsed / fadeDuration).toFloat().coerceIn(0f, 1f)
            val (a, b) = equalPower(progress)
            deck.setVolume(volumeFlow.value * a)
            standby!!.setVolume(volumeFlow.value * b)
            if (progress >= 1f || deck.playerStateFlow.value == PlayerState.COMPLETED) promote()
            return
        }
        val next = nextIndex()
        if (next == null) {
            if (deck.playerStateFlow.value == PlayerState.COMPLETED) {
                wantsPlay = false
                completion.tryEmit(Unit)
            }
            return
        }
        val item = playlistFlow.value[next]
        val prefs = settings.userSettings.value
        val plan = TransitionPlan.create(prefs.crossfadeSeconds, prefs.autoMixEnabled && !synchronizedPlayback.value,
            playlistContext, currentMediaItemFlow.value?.bpm, item.bpm)
        val seconds = if (announcementsEnabled || synchronizedPlayback.value || loopStateFlow.value == LoopState.ONE) 0 else plan.seconds
        if (duration <= Duration.ZERO || !duration.isFinite()) {
            if (deck.playerStateFlow.value == PlayerState.COMPLETED) start(next, true)
            return
        }
        val remaining = duration - position
        val preloadMs = maxOf(5_000L, seconds * 1_000L + 5_000L)
        if ((prefs.gaplessEnabled || seconds > 0) && remaining <= preloadMs.milliseconds && prepared != item) {
            val buffer = standby ?: createDeck().also { standby = it }
            buffer.setVolume(0f)
            buffer.prepareForTransition(item)
            buffer.setPlaybackSpeed(playbackSpeedFlow.value * plan.tempoRatio)
            if (plan.beatMatching && item.beatOffsetMs != null) {
                buffer.seekTo(item.beatOffsetMs.coerceAtLeast(0).milliseconds)
            }
            prepared = item
        }
        val ended = deck.playerStateFlow.value == PlayerState.COMPLETED || remaining <= Duration.ZERO
        if (ended) {
            if (prepared == item && !announcementsEnabled) {
                standby!!.setVolume(volumeFlow.value)
                standby!!.play()
                promote()
            } else start(next, true)
            return
        }
        val length = minOf((seconds * 1_000L).milliseconds, duration / 2)
        // La rejilla solo es válida si el proveedor informa el primer beat; BPM solo no da su fase.
        val current = currentMediaItemFlow.value
        val beatAlignedStart = if (plan.beatMatching && current?.beatOffsetMs != null &&
            item.beatOffsetMs != null && current.bpm != null) {
            val beatMs = 60_000.0 / current.bpm
            val offset = current.beatOffsetMs.toDouble()
            (offset + kotlin.math.ceil(((duration - length).inWholeMilliseconds - offset) / beatMs) * beatMs)
                .toLong().milliseconds
        } else duration - length
        val ready = standby?.playerStateFlow?.value in listOf(PlayerState.READY, PlayerState.PAUSED)
        if (seconds > 0 && prepared == item && ready && position >= beatAlignedStart &&
            deck.playerStateFlow.value == PlayerState.PLAYING) {
            // Algunos motores aceptan el tempo solo cuando el medio ya está preparado.
            standby!!.setPlaybackSpeed(playbackSpeedFlow.value * plan.tempoRatio)
            fadeDuration = remaining.coerceAtLeast(1.milliseconds)
            incomingStart = standby!!.positionFlow.value
            standby!!.play()
            fading = true
        }
    }

    private suspend fun promote() {
        val next = nextIndex() ?: return
        val old = active.value
        active.value = standby!!
        standby = old
        old.stop()
        index = next
        currentMediaItemFlow.value = playlistFlow.value[index]
        active.value.setVolume(volumeFlow.value)
        active.value.setPlaybackSpeed(playbackSpeedFlow.value)
        prepared = null
        fading = false
    }

    private suspend fun cancelTransition() {
        standby?.stop()
        prepared = null
        fading = false
        active.value.setVolume(volumeFlow.value)
    }

    private suspend fun start(target: Int, autoPlay: Boolean) {
        announcementJob?.cancelAndJoin()
        cancelTransition()
        active.value.stop()
        index = target
        val item = playlistFlow.value.getOrNull(index)
        currentMediaItemFlow.value = item
        wantsPlay = autoPlay && item != null
        active.value.load(listOfNotNull(item), false, 0)
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
        if (fading) standby?.play()
    }
    override suspend fun pause() = mutex.withLock {
        wantsPlay = false
        announcementJob?.cancelAndJoin()
        active.value.pause()
        if (fading) standby?.pause()
    }
    override suspend fun stop() = mutex.withLock {
        wantsPlay = false
        announcementJob?.cancelAndJoin()
        cancelTransition()
        active.value.stop()
    }
    override suspend fun seekTo(position: Duration) = mutex.withLock {
        cancelTransition()
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
    override suspend fun skipToPrevious() = mutex.withLock {
        if (index >= 0) start((index - 1).coerceAtLeast(0), true)
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
    }
    override suspend fun setPlaybackSpeed(speed: Float) = mutex.withLock {
        playbackSpeedFlow.value = speed.coerceIn(0.25f, 4f)
        active.value.setPlaybackSpeed(playbackSpeedFlow.value)
    }
    override fun isDisposed() = disposed
    override fun dispose() {
        disposed = true
        scope.cancel()
        active.value.dispose()
        standby?.dispose()
    }
}
