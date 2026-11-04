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

package org.spotube.spotube.core.remote

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.spotube.spotube.core.audioplayer.AudioPlayerInterface
import org.spotube.spotube.core.audioplayer.AudioPlayerQueue
import org.spotube.spotube.core.audioplayer.QueueEntry
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Where playback actions go while this device controls another one. */
sealed interface ActivePlaybackDevice {
    data object Local : ActivePlaybackDevice

    /** The connected remote device ([name] as it advertises itself). */
    data class Remote(val name: String) : ActivePlaybackDevice
}

/**
 * The "active device" of a controller, like Spotify Connect: while connected to a remote
 * device, everything this device plays goes there; switching device moves the queue, the
 * current track and its position, and pauses the device that stops playing.
 *
 * Connecting makes the remote device active (the local player is paused); losing the
 * connection falls back to this device.
 */
class PlaybackDeviceManager(
    private val client: RemoteControlClient,
    private val audioPlayer: AudioPlayerInterface,
    private val audioPlayerQueue: AudioPlayerQueue
) {
    private val logger = Logger.withTag("PlaybackDeviceManager")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The user's choice while connected; the connection itself decides when it applies. */
    private val remoteSelected = MutableStateFlow(true)

    val activeDevice: StateFlow<ActivePlaybackDevice> =
        combine(client.connectionState, remoteSelected) { connection, selected ->
            if (connection is ConnectionState.Connected && selected) {
                ActivePlaybackDevice.Remote(connection.deviceName)
            } else {
                ActivePlaybackDevice.Local
            }
        }.stateIn(scope, SharingStarted.Eagerly, ActivePlaybackDevice.Local)

    val isRemoteActive: Boolean get() = activeDevice.value is ActivePlaybackDevice.Remote

    init {
        scope.launch {
            client.connectionState
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .collect { connected ->
                    if (connected) {
                        // Connecting is choosing that device: only one of them plays.
                        remoteSelected.value = true
                        runCatching { audioPlayer.pause() }
                    }
                }
        }
    }

    /** Moves playback to the connected remote device. */
    fun switchToRemote() {
        if (client.connectionState.value !is ConnectionState.Connected || isRemoteActive) return
        scope.launch {
            val queue = audioPlayerQueue.queueFlow.value
            val current = audioPlayerQueue.currentQueueEntryFlow.value
            val tracks = queue.filterIsInstance<QueueEntry.StreamingTrack>()
            val startIndex = (current as? QueueEntry.StreamingTrack)
                ?.let { entry -> tracks.indexOfFirst { it.track.id == entry.track.id } }
                ?.takeIf { it >= 0 } ?: 0
            val position = if (current is QueueEntry.StreamingTrack) {
                audioPlayer.positionFlow.value.inWholeMilliseconds
            } else {
                0L
            }
            remoteSelected.value = true
            runCatching { audioPlayer.pause() }
            if (tracks.isNotEmpty()) {
                runCatching {
                    client.sendCommand(
                        RemoteControlCommand.LoadQueue(
                            tracks = tracks.map { it.track },
                            startIndex = startIndex,
                            positionMs = position,
                            autoPlay = true
                        )
                    )
                }.onFailure { logger.e(it) { "Transfer to remote failed" } }
            }
        }
    }

    /** Moves playback from the remote device to this one. */
    fun switchToLocal() {
        if (!isRemoteActive) return
        scope.launch {
            val queue = client.latestQueue.value
            val state = client.latestPlayerState.value
            remoteSelected.value = false
            runCatching { client.sendCommand(RemoteControlCommand.Pause) }

            val entries = queue?.entries.orEmpty()
            val tracks = entries.mapNotNull { it.track }
            if (tracks.isEmpty()) return@launch
            val currentEntry = queue?.currentIndex?.let { entries.getOrNull(it) }
            val startIndex = currentEntry?.track
                ?.let { current -> tracks.indexOfFirst { it.id == current.id } }
                ?.takeIf { it >= 0 } ?: 0
            runCatching {
                audioPlayerQueue.load(
                    entries = tracks.map { QueueEntry.StreamingTrack(track = it, url = "") },
                    autoPlay = state?.isPlaying ?: true,
                    startPosition = startIndex,
                    collectionEntry = null
                )
                val position = state?.positionMs ?: 0L
                if (position > 0) audioPlayer.seekWhenReady(position)
            }.onFailure { logger.e(it) { "Transfer to this device failed" } }
        }
    }

    /** Local-only content (files on this device): it can only play here. */
    fun useLocalWithoutTransfer() {
        if (!isRemoteActive) return
        remoteSelected.value = false
        scope.launch { runCatching { client.sendCommand(RemoteControlCommand.Pause) } }
    }
}

/**
 * Seeks to [positionMs] once a freshly loaded queue is playing from its start: the reload
 * resets the position (also when the same track was already loaded, which the current-item
 * flow does not report) and the track has a duration. Seeking earlier is ignored by the player.
 */
internal suspend fun AudioPlayerInterface.seekWhenReady(positionMs: Long) {
    val ready = withTimeoutOrNull(10.seconds) {
        delay(300)
        combine(durationFlow, positionFlow) { duration, position -> duration to position }
            .first { (duration, position) ->
                duration.inWholeMilliseconds > 0 && position.inWholeMilliseconds < 5_000
            }
    }
    if (ready != null) seekTo(positionMs.milliseconds)
}
