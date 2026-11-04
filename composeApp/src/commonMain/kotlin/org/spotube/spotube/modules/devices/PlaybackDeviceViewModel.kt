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

package org.spotube.spotube.modules.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.spotube.spotube.core.navigation.NavigationCommands
import org.spotube.spotube.core.navigation.Routes
import org.spotube.spotube.core.remote.ActivePlaybackDevice
import org.spotube.spotube.core.remote.ConnectionState
import org.spotube.spotube.core.remote.PlaybackDeviceManager
import org.spotube.spotube.core.remote.RemoteControlClient
import org.spotube.spotube.core.remote.RemoteControlCommand

data class PlaybackDeviceUiState(
    /** A remote device is connected, so there is a choice of device. */
    val isConnected: Boolean = false,
    val isRemoteActive: Boolean = false,
    val remoteName: String = "",
    val isPickerVisible: Boolean = false,
    // What the remote device is playing, for the remote mini player.
    val title: String? = null,
    val artists: String? = null,
    val coverUrl: String? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f
)

/**
 * The device button and the remote mini player: which device plays (this one or the
 * connected one), switching between them, and the remote device's now-playing.
 */
class PlaybackDeviceViewModel(
    private val deviceManager: PlaybackDeviceManager,
    private val client: RemoteControlClient,
    private val navigationCommands: NavigationCommands
) : ViewModel() {
    private val pickerVisible = MutableStateFlow(false)

    val uiState: StateFlow<PlaybackDeviceUiState> = combine(
        deviceManager.activeDevice,
        client.connectionState,
        client.latestPlayerState,
        pickerVisible
    ) { active, connection, player, picker ->
        val connected = connection as? ConnectionState.Connected
        PlaybackDeviceUiState(
            isConnected = connected != null,
            isRemoteActive = active is ActivePlaybackDevice.Remote,
            remoteName = connected?.deviceName.orEmpty().ifBlank { connected?.host.orEmpty() },
            isPickerVisible = picker && connected != null,
            title = player?.currentTrackTitle,
            artists = player?.currentTrackArtists,
            coverUrl = player?.currentTrackCoverUrl,
            isPlaying = player?.isPlaying == true,
            progress = player?.let {
                if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else 0f
            } ?: 0f
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackDeviceUiState())

    fun openPicker() {
        pickerVisible.value = true
    }

    fun dismissPicker() {
        pickerVisible.value = false
    }

    fun selectThisDevice() {
        pickerVisible.value = false
        deviceManager.switchToLocal()
    }

    fun selectRemoteDevice() {
        pickerVisible.value = false
        deviceManager.switchToRemote()
    }

    fun openRemotePlayer() {
        navigationCommands.navigateTo(Routes.RemoteControl)
    }

    fun togglePlayPause() {
        viewModelScope.launch { client.sendCommand(RemoteControlCommand.TogglePlayPause) }
    }

    fun skipNext() {
        viewModelScope.launch { client.sendCommand(RemoteControlCommand.SkipNext) }
    }
}
