package org.spotube.spotube.features.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.modules.settings.*
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
        var loads = 0
        override suspend fun load(playlist: List<MediaItem>, autoPlay: Boolean, startPosition: Int) {
            loads++
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

    @Test fun curvesKeepTheirDirectionAndExpectedPower() {
        for (curve in CrossfadeCurve.entries) {
            val start = crossfadeGains(0f, curve)
            val end = crossfadeGains(1f, curve)
            assertEquals(1f, start.first, 0.0001f)
            assertEquals(0f, start.second, 0.0001f)
            assertEquals(0f, end.first, 0.0001f)
            assertEquals(1f, end.second, 0.0001f)
        }
        assertEquals(0.5f to 0.5f, crossfadeGains(0.5f, CrossfadeCurve.LINEAR))
        val smooth = crossfadeGains(0.5f, CrossfadeCurve.SMOOTH)
        assertEquals(0.5f, smooth.first, 0.0001f)
        assertEquals(0.5f, smooth.second, 0.0001f)
        assertEquals(1f to 0f, crossfadeGains(Float.NaN, CrossfadeCurve.LINEAR))
    }

    @Test fun existingPreferencesKeepTheirDefaults() {
        val settings = kotlinx.serialization.json.Json.decodeFromString<UserSettings>(
            """{"crossfadeSeconds":6,"autoMixEnabled":true,"djMode":false}""")
        assertEquals(6, settings.crossfadeSeconds)
        assertTrue(settings.autoMixEnabled)
        assertEquals(CrossfadeCurve.EQUAL_POWER, settings.crossfadeCurve)
        assertEquals(4, settings.autoMixBars)
        assertEquals("Livi", settings.djName)
    }

    @Test fun musicalTimingRequiresBothGridsAndUsesFourBeatsPerBar() {
        assertEquals(4000L, mixDurationMillis(120f, 0, 0, 2))
        assertEquals(8000L, mixDurationMillis(120f, 0, 0, 4))
        assertEquals(16000L, mixDurationMillis(120f, 0, 0, 8))
        assertNull(mixDurationMillis(120f, null, 0, 4))
        assertNull(mixDurationMillis(120f, 0, -1, 4))
        assertNull(mixDurationMillis(Float.NaN, 0, 0, 4))
    }

    @Test fun automixRestoresTempoGraduallyAfterPromotionAndHonorsManualSpeed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val settings = object : UserSettingsSource {
            override val userSettings = MutableStateFlow(UserSettings(autoMixEnabled = false, autoMixBars = 2))
        }
        val player = TransitionAudioPlayer(a, { b }, settings)
        try {
            player.playlistContext = true
            player.playlistMixOverride = true
            player.load(listOf(track("one").copy(bpm = 105f, beatOffsetMs = 0),
                track("two").copy(bpm = 100f, beatOffsetMs = 0, duration = 120.seconds)), true, 0)
            a.positionFlow.value = 26.seconds
            runCurrent()
            assertEquals(PlayerState.PLAYING, b.playerStateFlow.value)
            assertEquals(1.05f, b.playbackSpeedFlow.value, 0.0001f)
            b.positionFlow.value = 2.1.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(0.707107f, a.volumeFlow.value, 0.0001f)
            assertEquals(0.707107f, b.volumeFlow.value, 0.0001f)
            b.positionFlow.value = 4.seconds
            a.playerStateFlow.value = PlayerState.COMPLETED
            advanceTimeBy(25); runCurrent()
            assertSame(b, player.activeDeck.value)
            assertEquals(1.05f, b.playbackSpeedFlow.value, 0.0001f)
            b.positionFlow.value = 23.2.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(1.025f, b.playbackSpeedFlow.value, 0.0001f)
            player.pause()
            advanceTimeBy(10_000); runCurrent()
            assertEquals(1.025f, b.playbackSpeedFlow.value, 0.0001f)
            player.play()
            b.positionFlow.value = 42.4.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(1f, b.playbackSpeedFlow.value, 0.0001f)
            player.setPlaybackSpeed(0.9f)
            advanceTimeBy(25); runCurrent()
            assertEquals(0.9f, b.playbackSpeedFlow.value, 0.0001f)
        } finally { player.dispose(); Dispatchers.resetMain() }
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

    @Test fun djSpeaksAtSetBoundariesAndNeverDuringPlaylistMix() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val deck = Deck()
        val settings = object : UserSettingsSource {
            override val userSettings = MutableStateFlow(UserSettings(crossfadeSeconds = 0, djMode = true, autoMixEnabled = true))
        }
        val player = TransitionAudioPlayer(deck, { Deck() }, settings)
        val lifecycle = org.spotube.spotube.features.dj.DjSetLifecycle()
        var collection: QueueCollectionEntry = QueueCollectionEntry.DjSet("set-one")
        lifecycle.begin(collection.id, "intro", "outro")
        var introductions = 0
        var endings = 0
        player.beforeTrack = { if (lifecycle.introduction(collection)) introductions++ }
        player.afterQueue = { if (lifecycle.ending(collection)) endings++ }
        try {
            player.load((1..6).map { track("song-$it") }, true, 0)
            runCurrent()
            assertEquals(1, introductions)
            repeat(5) { player.skipToNext(); runCurrent() }
            assertEquals(1, introductions)
            assertEquals(0, endings)
            deck.playerStateFlow.value = PlayerState.COMPLETED
            advanceTimeBy(100); runCurrent()
            assertEquals(1, endings)
            advanceTimeBy(100); runCurrent()
            assertEquals(1, endings)
            collection = QueueCollectionEntry.Playlist("playlist")
            player.playlistContext = true
            player.load(listOf(track("playlist-song")), true, 0)
            runCurrent()
            assertEquals(1, introductions)
            deck.playerStateFlow.value = PlayerState.COMPLETED
            advanceTimeBy(100); runCurrent()
            assertEquals(1, endings)
            collection = QueueCollectionEntry.DjSet("set-two")
            lifecycle.begin(collection.id, "intro", "outro")
            player.load((1..6).map { track("new-$it") }, true, 0)
            runCurrent()
            assertEquals(2, introductions)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }

    private fun player(a: Deck, b: Deck, settings: UserSettings, scope: TestScope) = TransitionAudioPlayer(
        a, { b }, object : UserSettingsSource { override val userSettings = MutableStateFlow(settings) },
        nowMs = { scope.testScheduler.currentTime })

    @Test fun longTracksPreloadTheNextOneAfterTheFirstMinute() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val player = player(a, b, UserSettings(crossfadeSeconds = 10), this)
        try {
            val tracks = listOf(track("one").copy(duration = 290.seconds), track("two").copy(duration = 200.seconds))
            player.load(tracks, true, 0)
            a.positionFlow.value = 59.seconds
            advanceTimeBy(25); runCurrent()
            assertNull(b.currentMediaItemFlow.value)
            a.positionFlow.value = 60.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(tracks[1], b.currentMediaItemFlow.value)
            assertEquals(PlayerState.READY, b.playerStateFlow.value)
            // El fundido empieza exactamente 10 s antes del final, no cuando la precarga termina.
            a.positionFlow.value = 279.9.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(PlayerState.READY, b.playerStateFlow.value)
            a.positionFlow.value = 280.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(PlayerState.PLAYING, b.playerStateFlow.value)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }

    @Test fun aFailedPreloadIsRetriedAndNeverPromoted() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val player = player(a, b, UserSettings(crossfadeSeconds = 0), this)
        try {
            val tracks = listOf(track("one").copy(duration = 120.seconds), track("two"))
            player.load(tracks, true, 0)
            a.positionFlow.value = 61.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(1, b.loads)
            b.errorFlow.emit(IllegalStateException("stream expired"))
            b.playerStateFlow.value = PlayerState.IDLE
            advanceTimeBy(1_000); runCurrent()
            assertEquals(1, b.loads)
            advanceTimeBy(PRELOAD_RETRY_DELAY_MS); runCurrent()
            assertEquals(2, b.loads)
            // Si el reintento también falla y llega el final, la pista se carga de nuevo en el motor activo.
            b.errorFlow.emit(IllegalStateException("still failing"))
            b.playerStateFlow.value = PlayerState.IDLE
            val loadsBefore = a.loads
            a.positionFlow.value = 120.seconds
            advanceTimeBy(25); runCurrent()
            assertSame(a, player.activeDeck.value)
            assertEquals(loadsBefore + 1, a.loads)
            assertEquals(tracks[1], a.currentMediaItemFlow.value)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }

    @Test fun gaplessStartsTheNextTrackJustBeforeTheEnd() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val player = player(a, b, UserSettings(crossfadeSeconds = 0, gaplessEnabled = true), this)
        try {
            val tracks = listOf(track("one"), track("two"))
            player.load(tracks, true, 0)
            a.positionFlow.value = 25.seconds
            advanceTimeBy(25); runCurrent()
            assertEquals(PlayerState.READY, b.playerStateFlow.value)
            a.positionFlow.value = 30.seconds - 100.milliseconds
            advanceTimeBy(25); runCurrent()
            assertEquals(PlayerState.PLAYING, b.playerStateFlow.value)
            assertEquals(1f, b.volumeFlow.value)
            assertSame(a, player.activeDeck.value)
            a.playerStateFlow.value = PlayerState.COMPLETED
            advanceTimeBy(25); runCurrent()
            assertSame(b, player.activeDeck.value)
            assertEquals(tracks[1], player.currentMediaItemFlow.value)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }

    @Test fun previousRewindsFirstAndGoesBackOnTheSecondTap() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val a = Deck(); val b = Deck()
        val player = player(a, b, UserSettings(crossfadeSeconds = 0), this)
        try {
            val tracks = listOf(track("one"), track("two"))
            player.load(tracks, true, 1)
            a.positionFlow.value = 12.seconds
            player.skipToPrevious()
            assertEquals(Duration.ZERO, a.positionFlow.value)
            assertEquals(tracks[1], player.currentMediaItemFlow.value)
            a.positionFlow.value = 1.seconds
            player.skipToPrevious()
            assertEquals(tracks[0], player.currentMediaItemFlow.value)
            // En la primera pista, «Anterior» sólo puede rebobinar.
            a.positionFlow.value = 1.seconds
            player.skipToPrevious()
            assertEquals(tracks[0], player.currentMediaItemFlow.value)
            assertEquals(Duration.ZERO, a.positionFlow.value)
        } finally { player.dispose(); Dispatchers.resetMain() }
    }
}
