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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.LocalTvType
import org.spotube.tv.theme.TvDimens

/** Icon helper so the module never depends on Material's Icon composable. */
@Composable
fun TvIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalTvColors.current.onBackground
) {
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
    )
}

/**
 * Text + optional icon button with a focus ring and a slight scale-up. It is the only
 * "clickable chrome" primitive used by the TV layer.
 */
@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 0.dp
) {
    val colors = LocalTvColors.current
    val type = LocalTvType.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        label = "tvButtonScale"
    )
    val shape = RoundedCornerShape(12.dp)
    val contentColor = if (enabled) colors.onBackground else colors.onSurfaceVariant

    Row(
        modifier = modifier
            .widthIn(min = minWidth)
            .heightIn(min = TvDimens.MinTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused || selected) colors.surface else Color.Transparent)
            .border(
                width = if (focused) TvDimens.FocusRing else 0.dp,
                color = if (focused) colors.focusRing else Color.Transparent,
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            TvIcon(
                imageVector = it,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
        }
        BasicText(
            text = label,
            style = type.label.copy(color = contentColor),
            maxLines = 1
        )
    }
}

/** Square/circular focusable icon control used by the mini player and dialogs. */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = TvDimens.MinTarget,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val colors = LocalTvColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        label = "tvIconButtonScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (focused || selected) colors.surface else Color.Transparent)
            .border(
                width = if (focused) TvDimens.FocusRing else 0.dp,
                color = if (focused) colors.focusRing else Color.Transparent,
                shape = CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        TvIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.onBackground else colors.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Thin, non-interactive progress indicator (seek is done with the D-pad, never a drag). */
@Composable
fun TvProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    val colors = LocalTvColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.onSurfaceVariant.copy(alpha = 0.25f))
    ) {
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(colors.accent)
            )
        }
    }
}

/** Small labelled column used by empty states / dialog summaries. */
@Composable
fun TvCaption(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = LocalTvType.current.caption.copy(color = LocalTvColors.current.onSurfaceVariant),
        modifier = modifier,
        maxLines = 2
    )
}

@Composable
fun TvBody(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = LocalTvType.current.body.copy(color = LocalTvColors.current.onBackground),
        modifier = modifier
    )
}

@Composable
fun TvTitle(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = LocalTvType.current.title.copy(color = LocalTvColors.current.onBackground),
        modifier = modifier
    )
}

/** Column helper so rows and dialogs share the same spacing. */
@Composable
fun TvTextColumn(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        TvBody(text = title)
        subtitle?.let {
            TvCaption(text = it)
        }
    }
}
