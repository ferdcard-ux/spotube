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

package org.spotube.tv.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.spotube.tv.components.TvIcon
import org.spotube.tv.navigation.TvRailItem
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.LocalTvType
import org.spotube.tv.theme.TvDimens

/**
 * Fixed left rail. It replaces the phone's 840dp sidebar and bottom navigation: one vertical
 * column of >=56dp targets, always visible (no collapse animation competing with focus).
 *
 * @param selectedId id of the destination currently displayed, highlighted even when unfocused.
 * @param focusRequestId when it changes and is not null, the matching entry requests focus.
 * @param onFocused optional callback so the app can restore the last focused rail entry.
 * @param topItem optional entry above the destinations (the profile button); never selected.
 */
@Composable
fun TvRail(
    items: List<TvRailItem>,
    selectedId: String?,
    modifier: Modifier = Modifier,
    focusRequestId: String? = null,
    onFocused: ((String) -> Unit)? = null,
    topItem: TvRailItem? = null
) {
    val colors = LocalTvColors.current
    val entries = listOfNotNull(topItem) + items
    val requesters = remember(entries.map { it.id }) {
        entries.associate { it.id to FocusRequester() }
    }

    LaunchedEffect(focusRequestId) {
        val id = focusRequestId ?: return@LaunchedEffect
        runCatching { requesters[id]?.requestFocus() }
    }

    Column(
        modifier = modifier
            .width(TvDimens.RailWidth)
            .fillMaxHeight()
            .background(colors.surface)
            // Coming back from the content (D-pad left) lands on the selected destination, not on
            // whichever entry happens to be level with the previously focused item.
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Left) {
                        selectedId?.let { requesters[it] }?.requestFocus()
                    }
                }
            }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        entries.forEachIndexed { index, item ->
            RailEntry(
                item = item,
                selected = item.id == selectedId,
                isFirst = index == 0,
                isLast = index == entries.lastIndex,
                focusRequester = requesters.getValue(item.id),
                onFocused = onFocused
            )
        }
    }
}

@Composable
private fun RailEntry(
    item: TvRailItem,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    focusRequester: FocusRequester,
    onFocused: ((String) -> Unit)?
) {
    val colors = LocalTvColors.current
    val type = LocalTvType.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        label = "tvRailScale"
    )
    val shape = RoundedCornerShape(14.dp)
    // Focus and selection stay distinguishable: a focused selected entry keeps the accent tint.
    val contentColor = when {
        selected -> colors.accent
        focused -> colors.onBackground
        else -> colors.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .focusRequester(focusRequester)
            // The rail is the left edge: D-pad must not wrap past its ends or leave to the left.
            .focusProperties {
                left = FocusRequester.Cancel
                if (isFirst) up = FocusRequester.Cancel
                if (isLast) down = FocusRequester.Cancel
            }
            .fillMaxWidth()
            .heightIn(min = TvDimens.RailItemHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                when {
                    selected -> colors.accent.copy(alpha = if (focused) 0.28f else 0.16f)
                    focused -> colors.background
                    else -> colors.surface
                }
            )
            .border(
                width = if (focused) TvDimens.FocusRing else 0.dp,
                color = if (focused) colors.focusRing else Color.Transparent,
                shape = shape
            )
            .semantics { contentDescription = item.label }
            // Must precede clickable: onFocusChanged only observes focus targets after it.
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused?.invoke(item.id)
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = item.onClick
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TvIcon(
            imageVector = item.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.size(4.dp))
        BasicText(
            text = item.label,
            style = type.caption.copy(
                color = contentColor,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
