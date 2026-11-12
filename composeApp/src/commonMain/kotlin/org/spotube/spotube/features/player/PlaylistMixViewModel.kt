package org.spotube.spotube.features.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.modules.settings.SettingsRepository

data class PlaylistMixUiState(val enabled: Boolean = false, val available: Boolean = true, val playing: Boolean = false)

/** Cada lista conserva su elección sin cambiar las otras listas ni modificar su orden. */
class PlaylistMixViewModel(
    private val playlistId: String,
    private val settings: SettingsRepository,
    player: AudioPlayerInterface,
    queue: AudioPlayerQueue,
) : ViewModel() {
    private val synced = (player as? TransitionAudioPlayer)?.synchronizedPlaybackFlow ?: MutableStateFlow(false)
    val uiState = combine(settings.userSettings, synced, queue.currentCollectionEntryFlow, player.playerStateFlow) { prefs, synchronized, collection, playback ->
        val enabled = prefs.playlistMixOverrides[playlistId] ?: prefs.autoMixEnabled
        PlaylistMixUiState(enabled, !synchronized, enabled && !synchronized && collection?.id == playlistId &&
            (collection is QueueCollectionEntry.Playlist || collection is QueueCollectionEntry.SavedTracks) && playback == PlayerState.PLAYING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistMixUiState())

    fun toggle() {
        if (!uiState.value.available) return
        viewModelScope.launch {
            settings.mutateSettings { current ->
                val enabled = current.playlistMixOverrides[playlistId] ?: current.autoMixEnabled
                current.copy(playlistMixOverrides = current.playlistMixOverrides + (playlistId to !enabled))
            }
        }
    }
}
