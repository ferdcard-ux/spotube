package org.spotube.spotube.features.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.spotube.spotube.core.audioplayer.AudioPlayerInterface
import org.spotube.spotube.modules.settings.SettingsRepository

data class PlaylistMixUiState(val enabled: Boolean = false, val available: Boolean = true)

/** Cada lista conserva su elección sin cambiar las otras listas ni modificar su orden. */
class PlaylistMixViewModel(
    private val playlistId: String,
    private val settings: SettingsRepository,
    player: AudioPlayerInterface,
) : ViewModel() {
    private val synced = (player as? TransitionAudioPlayer)?.synchronizedPlaybackFlow ?: MutableStateFlow(false)
    val uiState = combine(settings.userSettings, synced) { prefs, synchronized ->
        PlaylistMixUiState(prefs.playlistMixOverrides[playlistId] ?: prefs.autoMixEnabled, !synchronized)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistMixUiState())

    fun toggle() {
        if (!uiState.value.available) return
        viewModelScope.launch {
            val current = settings.userSettings.value
            val enabled = current.playlistMixOverrides[playlistId] ?: current.autoMixEnabled
            settings.updateSettings(current.copy(playlistMixOverrides = current.playlistMixOverrides + (playlistId to !enabled)))
        }
    }
}
