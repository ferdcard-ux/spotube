package org.spotube.spotube.features.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.modules.settings.*
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransitionAudioPlayerTest {
    private class Deck : AudioPlayerInterface {
        override val playerStateFlow = MutableStateFlow(PlayerState.IDLE)
        override val currentMediaItemFlow = MutableStateFlow<MediaItem?>(null)
        override val playlistFlow = MutableStateFlow<List<MediaItem>>(emptyList())
        override val durationFlow = MutableStateFlow(Duration.ZERO)
        override val positionFlow = MutableStateFlow(Duration.ZERO)
        override val bufferingPositionFlow = MutableStateFlow(Duration.ZERO)
        override val loopStateFlow = MutableStateFlow(LoopState.NONE)
        override val shuffleModeFlow = MutableStateFlow(false)
        override val playbackSpeedFlow = MutableStateFlow(1f)
        override val volumeFlow = MutableStateFlow(1f)
        override val completionFlow = MutableSharedFlow<Unit>()
        override val errorFlow = MutableSharedFlow<Throwable>()
        override suspend fun play() { playerStateFlow.value = PlayerState.PLAYING }
        override suspend fun pause() { playerStateFlow.value = PlayerState.PAUSED }
        override suspend fun stop() { playerStateFlow.value = PlayerState.IDLE; positionFlow.value = Duration.ZERO }
        override suspend fun load(playlist: List<MediaItem>, autoPlay: Boolean, startPosition: Int) {
            playlistFlow.value = playlist
            currentMediaItemFlow.value = playlist.getOrNull(startPosition)
            durationFlow.value = currentMediaItemFlow.value?.duration ?: Duration.ZERO
            positionFlow.value = Duration.ZERO
            playerStateFlow.value = if (autoPlay) PlayerState.PLAYING else PlayerState.READY
        }
        override suspend fun seekTo(position: Duration) { positionFlow.value = position }
        override suspend fun loop(state: LoopState) { loopStateFlow.value = state }
        override suspend fun shuffle(enabled: Boolean) = Unit
        override suspend fun addMediaItem(mediaItem: MediaItem) = Unit
        override suspend fun insertMediaItemAtNextIndex(mediaItem: MediaItem) = Unit
        override suspend fun removeMediaItem(mediaItem: MediaItem) = Unit
        override suspend fun moveMediaItem(fromIndex: Int, toIndex: Int) = Unit
        override suspend fun skipToNext() = Unit
        override suspend fun skipToPrevious() = Unit
        override suspend fun jumpTo(index: Int) = Unit
        override suspend fun setVolume(volume: Float) { volumeFlow.value = volume }
        override suspend fun setPlaybackSpeed(speed: Float) { playbackSpeedFlow.value = speed }
        override fun isDisposed() = false
        override fun dispose() = Unit
    }

    private fun track(name: String) = MediaItem(name, "Artist", "Album", 30.seconds, "", name)

    @Test fun equalPowerPreservesPower() {
        for (step in 0..100) {
            val (a, b) = equalPower(step / 100f)
            assertEquals(1f, a * a + b * b, 0.00001f)
        }
    }

    @Test fun automixRequiresValidMetadataAndPlaylist() {
        assertEquals(TransitionPlan(8, 1.05f, true), TransitionPlan.create(5, true, true, 105f, 100f))
        assertTrue(TransitionPlan.create(5, true, true, 100f, 100f).beatMatching)
        assertEquals(TransitionPlan(5), TransitionPlan.create(5, true, false, 105f, 100f))
        assertEquals(TransitionPlan(5), TransitionPlan.create(5, true, true, null, 100f))
        assertEquals(TransitionPlan(5), TransitionPlan.create(5, true, true, Float.NaN, 100f))
        assertEquals(TransitionPlan(5), TransitionPlan.create(5, true, true, 110f, 100f))
    }

    @Test fun preloadsAtFiveSecondsThenPromotesWithoutReload() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val settings = object : UserSettingsSource {
            override val userSettings = MutableStateFlow(UserSettings(crossfadeSeconds = 0))
        }
        val player = TransitionAudioPlayer(a, { b }, settings)
        try {
            val tracks = listOf(track("one"), track("two"))
            player.load(tracks, true, 0)
            a.positionFlow.value = 24.seconds
            runCurrent()
            assertNull(b.currentMediaItemFlow.value)
            a.positionFlow.value = 25.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(tracks[1], b.currentMediaItemFlow.value)
            assertEquals(PlayerState.READY, b.playerStateFlow.value)
            a.positionFlow.value = 30.seconds
            advanceTimeBy(25); runCurrent()
            assertSame(b, player.activeDeck.value)
            assertEquals(Duration.ZERO, b.positionFlow.value)
            assertEquals(PlayerState.PLAYING, b.playerStateFlow.value)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }

    @Test fun pauseAndSeekDuringCrossfadeControlBothDecks() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val settings = object : UserSettingsSource {
            override val userSettings = MutableStateFlow(UserSettings(crossfadeSeconds = 6))
        }
        val player = TransitionAudioPlayer(a, { b }, settings)
        try {
            player.load(listOf(track("one"), track("two")), true, 0)
            player.setVolume(0.5f)
            a.positionFlow.value = 24.seconds
            runCurrent()
            b.positionFlow.value = 3.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(0.353553f, a.volumeFlow.value, 0.001f)
            assertEquals(0.353553f, b.volumeFlow.value, 0.001f)
            player.pause()
            assertEquals(PlayerState.PAUSED, a.playerStateFlow.value)
            assertEquals(PlayerState.PAUSED, b.playerStateFlow.value)
            player.seekTo(10.seconds)
            assertEquals(PlayerState.IDLE, b.playerStateFlow.value)
            assertEquals(0.5f, a.volumeFlow.value)
            assertEquals(10.seconds, a.positionFlow.value)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }

    @Test fun synchronizedPlaybackSuspendsMixingAndAnnouncementsWithoutChangingSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val settings = object : UserSettingsSource {
            override val userSettings = MutableStateFlow(UserSettings(crossfadeSeconds = 6, autoMixEnabled = true))
        }
        val player = TransitionAudioPlayer(a, { b }, settings)
        var announcements = 0
        player.beforeTrack = { announcements++ }
        try {
            player.setSynchronizedPlayback(true)
            player.load(listOf(track("one"), track("two")), true, 0)
            runCurrent()
            assertEquals(0, announcements)
            a.positionFlow.value = 25.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(PlayerState.READY, b.playerStateFlow.value)
            assertEquals(PlayerState.PLAYING, a.playerStateFlow.value)
            assertEquals(6, settings.userSettings.value.crossfadeSeconds)
            assertTrue(settings.userSettings.value.autoMixEnabled)
            player.setSynchronizedPlayback(false)
            player.skipToNext()
            runCurrent()
            assertEquals(1, announcements)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }

    @Test fun removingPreloadedSongInvalidatesBuffer() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val settings = object : UserSettingsSource {
            override val userSettings = MutableStateFlow(UserSettings(crossfadeSeconds = 0))
        }
        val player = TransitionAudioPlayer(a, { b }, settings)
        try {
            val tracks = listOf(track("one"), track("two"), track("three"))
            player.load(tracks, true, 0)
            a.positionFlow.value = 25.seconds
            runCurrent()
            player.removeMediaItem(tracks[1])
            advanceTimeBy(25); runCurrent()
            assertEquals(tracks[2], b.currentMediaItemFlow.value)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }
}
