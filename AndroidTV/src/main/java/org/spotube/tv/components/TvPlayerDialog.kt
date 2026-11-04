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

package org.spotube.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.spotube.tv.focus.tvDirectionKeys
import org.spotube.tv.focus.tvMediaKeys
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.LocalTvType
import org.spotube.tv.theme.TvDimens
import org.spotube.tv.theme.tvSafeArea

/**
 * Full-screen TV player. Seeking is done with the D-pad on the focused progress bar (left/right =
 * +/- 10s) or with the ±10 s buttons, because dragging a slider is a touch-only flow; play/pause/next/previous are focusable buttons and the hardware
 * media keys are also routed here.
 */
@Composable
fun TvPlayerDialog(
    visible: Boolean,
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    labels: TvPlayerLabels,
    icons: TvPlayerIcons,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: (() -> Unit)? = null,
    artwork: (@Composable (Modifier) -> Unit)? = null
) {
    if (!visible) return

    val colors = LocalTvColors.current
    val type = LocalTvType.current
    val playRequester = remember { FocusRequester() }
    var seekFocused by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(Unit) {
        runCatching { playRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .tvSafeArea()
                .tvMediaKeys(
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onRewind = onSeekBackward,
                    onFastForward = onSeekForward
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TvIconButton(
                        icon = icons.close,
                        contentDescription = labels.close,
                        onClick = onDismiss
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(360.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.onSurfaceVariant.copy(alpha = 0.12f))
                    ) {
                        artwork?.invoke(Modifier.fillMaxSize())
                    }

                    Spacer(Modifier.width(40.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = title,
                            style = type.display.copy(color = colors.onBackground),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.size(8.dp))
                        BasicText(
                            text = subtitle,
                            style = type.body.copy(color = colors.onSurfaceVariant),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.size(32.dp))
                        // Seeking is a focus stop of its own: left/right only seek while the bar is
                        // focused, so the transport buttons stay reachable with the D-pad.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (seekFocused) TvDimens.FocusRing else 0.dp,
                                    color = if (seekFocused) colors.focusRing else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .semantics { contentDescription = labels.seekBar }
                                .onFocusChanged { seekFocused = it.isFocused }
                                .tvDirectionKeys(onLeft = onSeekBackward, onRight = onSeekForward)
                                .focusable()
                                .padding(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            TvProgressBar(progress = progress, height = if (seekFocused) 10.dp else 6.dp)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TvCaption(formatTime(positionMs))
                            TvCaption(formatTime(durationMs))
                        }

                        Spacer(Modifier.size(32.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvIconButton(
                                icon = icons.seekBackward,
                                contentDescription = labels.seekBackward,
                                onClick = onSeekBackward
                            )
                            TvIconButton(
                                icon = icons.previous,
                                contentDescription = labels.previous,
                                onClick = onPrevious
                            )
                            Box(modifier = Modifier.focusRequester(playRequester)) {
                                TvIconButton(
                                    icon = if (isPlaying) icons.pause else icons.play,
                                    contentDescription = if (isPlaying) labels.pause else labels.play,
                                    onClick = onPlayPause,
                                    selected = true,
                                    size = 72.dp
                                )
                            }
                            TvIconButton(
                                icon = icons.next,
                                contentDescription = labels.next,
                                onClick = onNext
                            )
                            TvIconButton(
                                icon = icons.seekForward,
                                contentDescription = labels.seekForward,
                                onClick = onSeekForward
                            )
                            if (onLyrics != null && icons.lyrics != null) {
                                TvIconButton(
                                    icon = icons.lyrics,
                                    contentDescription = labels.lyrics,
                                    onClick = onLyrics
                                )
                            }
                            TvIconButton(
                                icon = icons.queue,
                                contentDescription = labels.queue,
                                onClick = onQueue
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
