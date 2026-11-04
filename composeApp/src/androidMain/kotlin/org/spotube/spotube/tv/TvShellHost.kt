/*
 * Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.spotube.spotube.tv

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.spotube.App
import org.spotube.spotube.LocalIsTv
import org.spotube.spotube.NavigationItem
import org.spotube.spotube.bottombarTabs
import org.spotube.spotube.core.audioplayer.AudioPlayerInterface
import org.spotube.spotube.core.audioplayer.AudioPlayerQueue
import org.spotube.spotube.core.deeplink.ExternalUriHandler
import org.spotube.spotube.core.navigation.NavigationCommandsEffect
import org.spotube.spotube.core.navigation.NavigationState
import org.spotube.spotube.core.navigation.Navigator
import org.spotube.spotube.core.navigation.Routes
import org.spotube.spotube.modules.settings.SettingsRepository
import org.spotube.tv.shell.TvFitContent
import org.spotube.spotube.core.profile.ProfileStore
import org.spotube.spotube.resources.iconsax.User
import spotube.composeapp.generated.resources.profiles_main_name
import org.spotube.spotube.modules.settings.TV_CONTENT_SCALE_AUTO
import androidx.compose.ui.focus.FocusRequester
import org.spotube.spotube.resources.iconsax.IconsaxMusic
import spotube.composeapp.generated.resources.player_lyrics
import org.spotube.spotube.core.newpipe.NewPipeDownloader
import org.spotube.spotube.core.paths.Paths
import org.spotube.spotube.core.remote.ConnectionRequestDialogHost
import org.spotube.spotube.core.ui.LocalTvSearchKeyboard
import org.spotube.spotube.core.ui.theming.AppTheme
import org.spotube.spotube.media.PlaybackService
import org.spotube.spotube.modules.devices.PlayDestinationPickerHost
import org.spotube.spotube.modules.library.LibraryState
import org.spotube.spotube.modules.library.LibraryTab
import org.spotube.spotube.modules.shell.rememberPlayerUiState
import org.spotube.spotube.modules.shell.player_queue.PlayerQueueContentViewModel
import org.spotube.spotube.resources.iconsax.FluentDismiss
import org.spotube.spotube.resources.iconsax.Iconsax
import org.spotube.spotube.resources.iconsax.IconsaxMusicPlaylist
import org.spotube.spotube.resources.iconsax.IconsaxNext
import org.spotube.spotube.resources.iconsax.IconsaxPause
import org.spotube.spotube.resources.iconsax.IconsaxPlay
import org.spotube.spotube.resources.iconsax.IconsaxPrevious
import compose.icons.FeatherIcons
import compose.icons.feathericons.RotateCcw
import compose.icons.feathericons.RotateCw
import org.spotube.tv.components.TvKeyboardDialog
import org.spotube.tv.components.TvKeyboardLabels
import org.spotube.tv.components.TvMiniPlayer
import org.spotube.tv.components.TvPlayerDialog
import org.spotube.tv.components.TvPlayerIcons
import org.spotube.tv.components.TvPlayerLabels
import org.spotube.tv.components.TvQueueDialog
import org.spotube.tv.components.TvQueueItem
import org.spotube.tv.navigation.TvRailItem
import org.spotube.tv.navigation.TvTabItem
import org.spotube.tv.shell.TvShell
import org.spotube.tv.theme.TvColors
import org.spotube.tv.theme.TvTheme
import spotube.composeapp.generated.resources.Res
import spotube.composeapp.generated.resources.common_close
import spotube.composeapp.generated.resources.player_next_content_description
import spotube.composeapp.generated.resources.player_now_playing
import spotube.composeapp.generated.resources.player_pause_content_description
import spotube.composeapp.generated.resources.player_play_content_description
import spotube.composeapp.generated.resources.player_previous_content_description
import spotube.composeapp.generated.resources.queue_no_queue_entries
import spotube.composeapp.generated.resources.queue_title
import spotube.composeapp.generated.resources.tv_keyboard_backspace
import spotube.composeapp.generated.resources.tv_keyboard_clear
import spotube.composeapp.generated.resources.tv_keyboard_search
import spotube.composeapp.generated.resources.tv_keyboard_space
import spotube.composeapp.generated.resources.tv_seek_backward
import spotube.composeapp.generated.resources.tv_seek_bar
import spotube.composeapp.generated.resources.tv_seek_forward
import kotlin.time.Duration.Companion.milliseconds

/** Height, in dp, the onboarding card needs to show its pages without cutting text. */
private const val WELCOME_MIN_HEIGHT_DP = 640f

/** Installs the one-way bridge the `:AndroidTV` activity uses to render the app composition. */
internal fun installTvBridge() {
    org.spotube.tv.TvBridge.install(
        content = { App() },
        onActivityCreated = { activity ->
            initTvRuntime(activity)
            activity.intent?.dataString?.let(ExternalUriHandler::onNewUri)
        },
        onNewIntent = { _, intent -> intent.dataString?.let(ExternalUriHandler::onNewUri) }
    )
}

/** Same runtime initialization `MainActivity` performs, shared by the Leanback entry point. */
private fun initTvRuntime(activity: ComponentActivity) {
    FileKit.init(activity)
    NewPipeDownloader.init(Paths())
    activity.startService(Intent(activity, PlaybackService::class.java))
}

/**
 * App-side root of the TV shell.
 *
 * It lives in `androidMain` because it needs the shared Koin graph, navigation and ViewModels,
 * then hands purely presentational data to `:AndroidTV`. The module cannot depend on the app
 * module, so this direction is the only allowed one.
 */
@Composable
internal fun TvShellHost(
    navigator: Navigator,
    navigationState: NavigationState,
    content: @Composable () -> Unit
) {
    // Same as AppShell: commands from dialogs/view models (e.g. Settings closing itself) are
    // applied here, otherwise they are dropped on TV.
    NavigationCommandsEffect(navigator, navigationState)

    val settingsRepository: SettingsRepository = koinInject()
    val userSettings by settingsRepository.userSettings.collectAsStateWithLifecycle()
    val contentScale = userSettings.tvContentScale
        .takeIf { it != TV_CONTENT_SCALE_AUTO }
        ?.let { it / 100f }

    // Onboarding keeps its full-screen flow; the shell appears once Home is the start route.
    // It is still drawn at the TV content scale: its card is sized for phones and its text was
    // cut on TVs with a large system font.
    if (navigationState.topLevelRoute == Routes.Welcome) {
        CompositionLocalProvider(LocalIsTv provides true) {
            TvFitContent(
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                minHeightDp = WELCOME_MIN_HEIGHT_DP
            ) {
                content()
            }
        }
        return
    }

    val audioPlayer: AudioPlayerInterface = koinInject()
    val audioPlayerQueue: AudioPlayerQueue = koinInject()
    val playerUiState = rememberPlayerUiState(audioPlayer, audioPlayerQueue)
    val queueViewModel: PlayerQueueContentViewModel = koinViewModel()
    val queueState by queueViewModel.queueContentUiState.collectAsStateWithLifecycle()
    val libraryState: LibraryState = koinInject()
    val libraryUiState by libraryState.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var isPlayerVisible by remember { mutableStateOf(false) }
    var isQueueVisible by remember { mutableStateOf(false) }

    val appColors = AppTheme.colors
    val tvColors = remember(appColors) {
        TvColors(
            background = appColors.background,
            surface = appColors.surface,
            onBackground = appColors.onBackground,
            onSurfaceVariant = appColors.onSurfaceVariant,
            accent = appColors.primary,
            focusRing = appColors.primary
        )
    }

    val railItems = bottombarTabs.mapNotNull { item ->
        when (item) {
            is NavigationItem.Tab<*> -> TvRailItem(
                id = item.route.toString(),
                label = stringResource(item.title),
                icon = item.icon,
                onClick = { navigator.navigate(item.route) }
            )

            else -> null
        }
    }
    val selectedRailId = navigationState.topLevelRoute.toString()

    val profileStore: ProfileStore = koinInject()
    val profiles by profileStore.snapshot.collectAsStateWithLifecycle()
    val profileName = profiles.profiles.firstOrNull { it.id == profiles.activeId }?.name
        ?: stringResource(Res.string.profiles_main_name)
    val profileRailItem = TvRailItem(
        id = "profile",
        label = profileName,
        icon = Iconsax.User,
        onClick = { navigator.navigate(Routes.Profiles) }
    )

    val isLibrary = navigationState.topLevelRoute == Routes.Library
    val topTabs = if (isLibrary) {
        LibraryTab.entries.map { tab ->
            TvTabItem(
                id = tab.name,
                label = stringResource(tab.titleResource),
                onClick = { libraryState.onTabSelected(tab) }
            )
        }
    } else {
        emptyList()
    }

    val playerLabels = TvPlayerLabels(
        play = stringResource(Res.string.player_play_content_description),
        pause = stringResource(Res.string.player_pause_content_description),
        next = stringResource(Res.string.player_next_content_description),
        previous = stringResource(Res.string.player_previous_content_description),
        queue = stringResource(Res.string.queue_title),
        openPlayer = stringResource(Res.string.player_now_playing),
        close = stringResource(Res.string.common_close),
        seekBackward = stringResource(Res.string.tv_seek_backward),
        seekForward = stringResource(Res.string.tv_seek_forward),
        seekBar = stringResource(Res.string.tv_seek_bar),
        lyrics = stringResource(Res.string.player_lyrics)
    )
    val playerIcons = remember {
        TvPlayerIcons(
            play = Iconsax.IconsaxPlay,
            pause = Iconsax.IconsaxPause,
            next = Iconsax.IconsaxNext,
            previous = Iconsax.IconsaxPrevious,
            queue = Iconsax.IconsaxMusicPlaylist,
            close = Iconsax.FluentDismiss,
            seekBackward = FeatherIcons.RotateCcw,
            seekForward = FeatherIcons.RotateCw,
            lyrics = Iconsax.IconsaxMusic
        )
    }
    val keyboardLabels = TvKeyboardLabels(
        title = stringResource(Res.string.tv_keyboard_search),
        clear = stringResource(Res.string.tv_keyboard_clear),
        backspace = stringResource(Res.string.tv_keyboard_backspace),
        space = stringResource(Res.string.tv_keyboard_space),
        submit = stringResource(Res.string.tv_keyboard_search),
        dismiss = stringResource(Res.string.common_close)
    )

    val coverArtwork: @Composable (Modifier) -> Unit = { modifier ->
        playerUiState.coverModel?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
    }

    fun togglePlayPause() {
        scope.launch {
            if (playerUiState.isPlaying) audioPlayer.pause() else audioPlayer.play()
        }
    }

    fun skipNext() {
        scope.launch { audioPlayer.skipToNext() }
    }

    fun skipPrevious() {
        scope.launch { audioPlayer.skipToPrevious() }
    }

    fun seekBy(deltaMilliseconds: Long) {
        val duration = playerUiState.seekDuration.inWholeMilliseconds
        val target = (playerUiState.position.inWholeMilliseconds + deltaMilliseconds)
            .coerceAtLeast(0L)
            .let { if (duration > 0L) it.coerceAtMost(duration) else it }
        scope.launch { audioPlayer.seekTo(target.milliseconds) }
    }

    fun openQueue() {
        queueViewModel.onDragStart()
        isQueueVisible = true
    }

    fun closeQueue() {
        queueViewModel.onDragStop()
        isQueueVisible = false
    }

    val queueItems = queueState.displayItems.map { item ->
        TvQueueItem(
            id = item.id,
            title = item.title,
            subtitle = item.subtitle,
            durationLabel = item.durationLabel,
            isCurrent = item.isCurrent,
            index = item.originalIndex
        )
    }

    fun openLyrics() {
        isPlayerVisible = false
        navigator.navigate(Routes.Lyrics)
    }

    val miniPlayer: (@Composable (FocusRequester) -> Unit)? =
        if (playerUiState.currentQueueEntry != null) {
            { playPauseRequester ->
                TvMiniPlayer(
                    title = playerUiState.title,
                    subtitle = playerUiState.artists,
                    isPlaying = playerUiState.isPlaying,
                    progress = playerUiState.progress,
                    labels = playerLabels,
                    icons = playerIcons,
                    onOpen = { isPlayerVisible = true },
                    onPlayPause = ::togglePlayPause,
                    onNext = ::skipNext,
                    onPrevious = ::skipPrevious,
                    onQueue = ::openQueue,
                    playPauseFocusRequester = playPauseRequester,
                    onLyrics = ::openLyrics,
                    artwork = coverArtwork
                )
            }
        } else {
            null
        }

    TvTheme(colors = tvColors) {
        CompositionLocalProvider(
            LocalIsTv provides true,
            LocalTvSearchKeyboard provides { query, onQueryChange, onSubmit, onDismiss ->
                TvKeyboardDialog(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = onSubmit,
                    onDismiss = onDismiss,
                    labels = keyboardLabels
                )
            }
        ) {
            ConnectionRequestDialogHost()
            PlayDestinationPickerHost()

            Box(modifier = Modifier.fillMaxSize()) {
                TvShell(
                    railItems = railItems,
                    selectedRailId = selectedRailId,
                    topTabs = topTabs,
                    selectedTabId = if (isLibrary) libraryUiState.currentTab.name else null,
                    onMediaPlayPause = ::togglePlayPause,
                    onMediaNext = ::skipNext,
                    onMediaPrevious = ::skipPrevious,
                    miniPlayer = miniPlayer,
                    contentScale = contentScale,
                    railTopItem = profileRailItem,
                    content = content
                )

                TvPlayerDialog(
                    visible = isPlayerVisible,
                    title = playerUiState.title,
                    subtitle = playerUiState.artists,
                    isPlaying = playerUiState.isPlaying,
                    positionMs = playerUiState.position.inWholeMilliseconds,
                    durationMs = playerUiState.seekDuration.inWholeMilliseconds,
                    labels = playerLabels,
                    icons = playerIcons,
                    onDismiss = { isPlayerVisible = false },
                    onPlayPause = ::togglePlayPause,
                    onNext = ::skipNext,
                    onPrevious = ::skipPrevious,
                    onSeekBackward = { seekBy(-10_000L) },
                    onSeekForward = { seekBy(10_000L) },
                    onQueue = ::openQueue,
                    onLyrics = ::openLyrics,
                    artwork = coverArtwork
                )

                TvQueueDialog(
                    visible = isQueueVisible,
                    items = queueItems,
                    title = stringResource(Res.string.queue_title),
                    emptyLabel = stringResource(Res.string.queue_no_queue_entries),
                    closeLabel = stringResource(Res.string.common_close),
                    onDismiss = ::closeQueue,
                    onJump = { index -> queueViewModel.playQueueItem(index) },
                    onMove = { fromOriginal, toDisplay ->
                        if (!queueState.isReadOnly) {
                            val fromDisplay = queueItems.indexOfFirst { it.index == fromOriginal }
                            if (fromDisplay >= 0) {
                                queueViewModel.onMove(fromDisplay, toDisplay)
                            }
                        }
                    },
                    onRemove = if (queueState.isReadOnly) null else queueViewModel::removeQueueItem,
                    closeIcon = Iconsax.FluentDismiss
                )
            }
        }
    }
}
