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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.LocalTvType
import org.spotube.tv.theme.TvDimens

/**
 * Persistent mini player. It replaces the phone's swipeable bottom sheet: opening the full player
 * is an explicit D-pad action ([onOpen]) and never a drag.
 */
@Composable
fun TvMiniPlayer(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    progress: Float,
    labels: TvPlayerLabels,
    icons: TvPlayerIcons,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    playPauseFocusRequester: FocusRequester? = null,
    onLyrics: (() -> Unit)? = null,
    artwork: (@Composable (Modifier) -> Unit)? = null
) {
    val colors = LocalTvColors.current
    val type = LocalTvType.current
    val shape = RoundedCornerShape(TvDimens.CornerRadius)
    val infoShape = RoundedCornerShape(10.dp)
    var infoFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TvDimens.MiniPlayerHeight)
            .clip(shape)
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.onSurfaceVariant.copy(alpha = 0.12f))
            ) {
                artwork?.invoke(Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(infoShape)
                    .border(
                        width = if (infoFocused) TvDimens.FocusRing else 0.dp,
                        color = if (infoFocused) colors.focusRing else Color.Transparent,
                        shape = infoShape
                    )
                    .semantics { contentDescription = labels.openPlayer }
                    // Must precede clickable: onFocusChanged only observes focus targets after it.
                    .onFocusChanged { infoFocused = it.isFocused }
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                BasicText(
                    text = title,
                    style = type.label.copy(color = colors.onBackground),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = subtitle,
                    style = type.caption.copy(color = colors.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIconButton(
                    icon = icons.previous,
                    contentDescription = labels.previous,
                    onClick = onPrevious
                )
                TvIconButton(
                    icon = if (isPlaying) icons.pause else icons.play,
                    contentDescription = if (isPlaying) labels.pause else labels.play,
                    onClick = onPlayPause,
                    selected = true,
                    modifier = if (playPauseFocusRequester != null) {
                        Modifier.focusRequester(playPauseFocusRequester)
                    } else {
                        Modifier
                    }
                )
                TvIconButton(
                    icon = icons.next,
                    contentDescription = labels.next,
                    onClick = onNext
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
        Spacer(Modifier.size(8.dp))
        TvProgressBar(progress = progress)
    }
}
