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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composeunstyled.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumDialog
import org.spotube.millennium.composables.MillenniumDialogBody
import org.spotube.millennium.composables.MillenniumDialogClose
import org.spotube.millennium.composables.MillenniumDialogHeader
import org.spotube.millennium.composables.MillenniumDialogTitle
import org.spotube.millennium.composables.MillenniumIcon
import org.spotube.millennium.composables.MillenniumIconButton
import org.spotube.millennium.composables.MillenniumIconButtonStyle
import org.spotube.millennium.composables.MillenniumProgress
import org.spotube.millennium.theme.MillenniumTheme
import org.spotube.millennium.theme.millenniumPanelBrush
import org.spotube.millennium.theme.millenniumSurface
import org.spotube.spotube.core.ui.component.TvDialogFocus
import org.spotube.spotube.core.ui.theming.AppTheme
import org.spotube.spotube.core.ui.tvFocusRing
import org.spotube.spotube.resources.iconsax.Iconsax
import org.spotube.spotube.resources.iconsax.IconsaxMirrorScreenRegular
import org.spotube.spotube.resources.iconsax.IconsaxMusic
import org.spotube.spotube.resources.iconsax.IconsaxNext
import org.spotube.spotube.resources.iconsax.IconsaxPause
import org.spotube.spotube.resources.iconsax.IconsaxPlay
import org.spotube.spotube.resources.iconsax.IconsaxCheckCircle
import spotube.composeapp.generated.resources.Res
import spotube.composeapp.generated.resources.player_next_content_description
import spotube.composeapp.generated.resources.player_pause_content_description
import spotube.composeapp.generated.resources.player_play_content_description
import spotube.composeapp.generated.resources.playback_device_button
import spotube.composeapp.generated.resources.playback_device_playing_on
import spotube.composeapp.generated.resources.playback_device_picker_title
import spotube.composeapp.generated.resources.playback_device_this_device
import spotube.composeapp.generated.resources.playback_device_transfer_note

/**
 * "Device" button for the players: only shown while a remote device is connected. Highlighted
 * when the remote device is the one playing; opens [PlaybackDevicePicker].
 */
@Composable
fun PlaybackDeviceButton(
    modifier: Modifier = Modifier,
    style: MillenniumIconButtonStyle = MillenniumIconButtonStyle.Quiet,
    viewModel: PlaybackDeviceViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.isConnected) return

    MillenniumIconButton(onClick = viewModel::openPicker, style = style, modifier = modifier) {
        MillenniumIcon(
            imageVector = Iconsax.IconsaxMirrorScreenRegular,
            contentDescription = stringResource(Res.string.playback_device_button),
            tint = if (state.isRemoteActive) AppTheme.colors.primary else AppTheme.colors.onSurfaceVariant
        )
    }
    if (state.isPickerVisible) {
        PlaybackDevicePicker(state, viewModel)
    }
}

@Composable
private fun PlaybackDevicePicker(state: PlaybackDeviceUiState, viewModel: PlaybackDeviceViewModel) {
    MillenniumDialog(visible = true, onDismissRequest = viewModel::dismissPicker) {
        TvDialogFocus {
            MillenniumDialogHeader {
                MillenniumDialogTitle(stringResource(Res.string.playback_device_picker_title))
                MillenniumDialogClose()
            }
            MillenniumDialogBody {
                DeviceRow(
                    name = stringResource(Res.string.playback_device_this_device),
                    selected = !state.isRemoteActive,
                    onClick = viewModel::selectThisDevice
                )
                DeviceRow(
                    name = state.remoteName,
                    selected = state.isRemoteActive,
                    onClick = viewModel::selectRemoteDevice
                )
                Text(
                    stringResource(Res.string.playback_device_transfer_note),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) AppTheme.colors.primaryContainer else AppTheme.colors.surfaceVariant)
            .tvFocusRing(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MillenniumIcon(
            imageVector = Iconsax.IconsaxMirrorScreenRegular,
            contentDescription = null,
            tint = if (selected) AppTheme.colors.primary else AppTheme.colors.onSurfaceVariant
        )
        Text(
            name,
            modifier = Modifier.weight(1f),
            color = AppTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            MillenniumIcon(
                imageVector = Iconsax.IconsaxCheckCircle,
                contentDescription = null,
                tint = AppTheme.colors.primary
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * Mini player shown instead of the local one while the connected device is the active one:
 * what it plays, play/pause, next and the device button. Tapping it opens the remote player.
 */
@Composable
fun RemoteFloatingPlayer(
    modifier: Modifier = Modifier,
    contentHeight: Dp = 64.dp,
    bottomBleed: Dp = 16.dp,
    viewModel: PlaybackDeviceViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight + bottomBleed)
            .millenniumSurface(
                background = millenniumPanelBrush(MillenniumTheme.colors),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                borderColor = MillenniumTheme.colors.border,
                borderWidth = 1.dp,
            )
            .clickable(onClick = viewModel::openRemotePlayer)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = bottomBleed)) {
            MillenniumProgress(progress = state.progress, modifier = Modifier.fillMaxWidth().height(2.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppTheme.colors.onSurface.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.coverUrl != null) {
                        AsyncImage(
                            model = state.coverUrl,
                            contentDescription = state.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        MillenniumIcon(
                            Iconsax.IconsaxMusic,
                            contentDescription = null,
                            tint = AppTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        text = state.title ?: stringResource(Res.string.playback_device_playing_on, state.remoteName),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.playback_device_playing_on, state.remoteName),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlaybackDeviceButton(viewModel = viewModel)
                MillenniumIconButton(
                    onClick = viewModel::togglePlayPause,
                    style = MillenniumIconButtonStyle.Primary
                ) {
                    MillenniumIcon(
                        if (state.isPlaying) Iconsax.IconsaxPause else Iconsax.IconsaxPlay,
                        contentDescription = stringResource(
                            if (state.isPlaying) Res.string.player_pause_content_description
                            else Res.string.player_play_content_description
                        )
                    )
                }
                MillenniumIconButton(onClick = viewModel::skipNext, style = MillenniumIconButtonStyle.Quiet) {
                    MillenniumIcon(
                        Iconsax.IconsaxNext,
                        contentDescription = stringResource(Res.string.player_next_content_description)
                    )
                }
            }
        }
    }
}
