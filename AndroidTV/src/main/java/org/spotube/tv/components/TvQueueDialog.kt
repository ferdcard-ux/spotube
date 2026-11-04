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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.spotube.tv.focus.tvBringIntoView
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.LocalTvType
import org.spotube.tv.theme.TvDimens
import org.spotube.tv.theme.tvSafeArea

/**
 * TV queue. Reordering is done with the D-pad while a row has focus: left = move up,
 * right = move down (up/down keep moving focus). This replaces the phone's drag handle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvQueueDialog(
    visible: Boolean,
    items: List<TvQueueItem>,
    title: String,
    emptyLabel: String,
    closeLabel: String,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: ((Int) -> Unit)? = null,
    closeIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    if (!visible) return

    val colors = LocalTvColors.current
    val type = LocalTvType.current
    val firstRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstRequester.requestFocus() }
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
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TvTitle(text = title)
                    }
                    if (closeIcon != null) {
                        TvIconButton(
                            icon = closeIcon,
                            contentDescription = closeLabel,
                            onClick = onDismiss
                        )
                    } else {
                        TvButton(label = closeLabel, onClick = onDismiss)
                    }
                }

                if (items.isEmpty()) {
                    TvEmptyState(title = emptyLabel)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = items,
                            key = { _, item -> item.id }
                        ) { position, item ->
                            QueueRow(
                                item = item,
                                position = position,
                                total = items.size,
                                focusRequester = if (position == 0) firstRequester else null,
                                onJump = { onJump(item.index) },
                                onMove = onMove,
                                onRemove = onRemove
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    item: TvQueueItem,
    position: Int,
    total: Int,
    focusRequester: FocusRequester?,
    onJump: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: ((Int) -> Unit)?
) {
    val colors = LocalTvColors.current
    val type = LocalTvType.current
    val scope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.01f else 1f, label = "tvQueueRowScale")
    val shape = RoundedCornerShape(14.dp)

    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TvDimens.MinTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (item.isCurrent) colors.accent.copy(alpha = 0.18f) else colors.surface)
            .border(
                width = if (focused) TvDimens.FocusRing else 0.dp,
                color = if (focused) colors.focusRing else Color.Transparent,
                shape = shape
            )
            .then(focusModifier)
            .tvBringIntoView(bringIntoView)
            .onFocusChanged {
                focused = it.isFocused
                if (it.hasFocus) {
                    scope.launch { runCatching { bringIntoView.bringIntoView() } }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (position > 0) {
                                onMove(item.index, position - 1)
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (position < total - 1) {
                                onMove(item.index, position + 1)
                                true
                            } else {
                                false
                            }
                        }
                        Key.Menu -> {
                            if (onRemove != null) {
                                onRemove(item.index)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onJump
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = (position + 1).toString(),
            style = type.caption.copy(color = colors.onSurfaceVariant),
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = item.title,
                style = type.label.copy(color = if (item.isCurrent) colors.accent else colors.onBackground),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = item.subtitle,
                style = type.caption.copy(color = colors.onSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(16.dp))
        BasicText(
            text = item.durationLabel,
            style = type.caption.copy(color = colors.onSurfaceVariant)
        )
    }
}
