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

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.spotube.tv.focus.tvMediaKeys
import org.spotube.tv.navigation.TvRailItem
import org.spotube.tv.navigation.TvTabItem
import org.spotube.tv.theme.LocalTvColors

/** Two presses of the same vertical arrow within this window count as a double press. */
private const val DoublePressWindowMs = 400L

/**
 * Smallest content height, in dp, the app screens are laid out for (see [TvFitContent]).
 */
private const val MinContentHeightDp = 440f

/**
 * Root TV layout: fixed rail on the left, optional top tabs, scrollable content and a persistent
 * mini player. It intentionally does not use `ModalBottomSheet`, `MillenniumBottomNavigationBar`
 * or the swipeable player used on phones.
 *
 * The composable is a renderer: destinations, selection and the player bar are computed by the
 * app and handed in. Hardware media keys are forwarded here so playback keeps working while the
 * TV activity is in the foreground.
 *
 * Shortcuts: pressing Down twice quickly focuses the mini player's play/pause button; pressing Up
 * twice quickly focuses the selected top tab, or the topmost item of the screen when there are no
 * tabs.
 *
 * @param miniPlayer receives the requester for the control that a double Down press focuses.
 * @param contentScale fraction of the device density the content is drawn at; null fits the
 *   content to the available height automatically.
 */
@Composable
fun TvShell(
    railItems: List<TvRailItem>,
    selectedRailId: String?,
    modifier: Modifier = Modifier,
    topTabs: List<TvTabItem> = emptyList(),
    selectedTabId: String? = null,
    railFocusRequestId: String? = null,
    onMediaPlayPause: (() -> Unit)? = null,
    onMediaNext: (() -> Unit)? = null,
    onMediaPrevious: (() -> Unit)? = null,
    miniPlayer: (@Composable (FocusRequester) -> Unit)? = null,
    contentScale: Float? = null,
    railTopItem: TvRailItem? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalTvColors.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val miniPlayerRequester = remember { FocusRequester() }
    val tabRequester = remember { FocusRequester() }
    // Plain holders, key bookkeeping only (must not recompose):
    // [0] = event time of the last Down press, [1] = of the last Up press.
    val lastPress = remember { LongArray(2) }
    // [0] = content has focus, [1] = a double Up walk is running.
    val flags = remember { BooleanArray(2) }

    fun jumpToTop() {
        if (topTabs.isNotEmpty()) {
            runCatching { tabRequester.requestFocus() }
            return
        }
        if (flags[1]) return
        flags[1] = true
        scope.launch {
            try {
                // Walk up one item at a time, letting lazy lists finish scrolling (and compose
                // what is above) before the next step; a move tried mid-scroll can find nothing.
                // The content blocks Up from leaving it meanwhile, so the walk ends at its top.
                repeat(200) {
                    if (!flags[0]) return@launch
                    var moved = focusManager.moveFocus(FocusDirection.Up)
                    if (!moved) {
                        repeat(12) { withFrameNanos { } }
                        moved = flags[0] && focusManager.moveFocus(FocusDirection.Up)
                    }
                    if (!moved) return@launch
                    repeat(4) { withFrameNanos { } }
                }
            } finally {
                flags[1] = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .then(
                if (onMediaPlayPause != null && onMediaNext != null && onMediaPrevious != null) {
                    Modifier.tvMediaKeys(
                        onPlayPause = onMediaPlayPause,
                        onNext = onMediaNext,
                        onPrevious = onMediaPrevious
                    )
                } else {
                    Modifier
                }
            )
            .onPreviewKeyEvent { event ->
                val slot = when (event.key) {
                    Key.DirectionDown -> 0
                    Key.DirectionUp -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount > 0) {
                    return@onPreviewKeyEvent false
                }
                val now = event.nativeKeyEvent.eventTime
                val isDouble = now - lastPress[slot] <= DoublePressWindowMs
                // A third quick press starts a new pair instead of jumping again.
                lastPress[slot] = if (isDouble) 0L else now
                when {
                    !isDouble -> false
                    slot == 0 && miniPlayer != null ->
                        runCatching { miniPlayerRequester.requestFocus() }.isSuccess
                    slot == 1 -> {
                        jumpToTop()
                        true
                    }
                    else -> false
                }
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            TvRail(
                items = railItems,
                selectedId = selectedRailId,
                focusRequestId = railFocusRequestId,
                topItem = railTopItem
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Full-bleed layout: the rail touches the screen edges and the content keeps a
                    // small margin. Current TVs do not overscan, so the old 5 % safe area only
                    // left empty borders.
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp)
            ) {
                if (topTabs.isNotEmpty()) {
                    TvTopTabs(
                        tabs = topTabs,
                        selectedId = selectedTabId,
                        selectedFocusRequester = tabRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                TvFitContent(
                    contentScale = contentScale,
                    minHeightDp = MinContentHeightDp,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { flags[0] = it.hasFocus }
                        .focusProperties {
                            onExit = {
                                // While walking to the top, stop at the content's first item
                                // instead of escaping to the rail.
                                if (flags[1] && requestedFocusDirection == FocusDirection.Up) {
                                    cancelFocusChange()
                                }
                            }
                        }
                        .focusGroup()
                ) {
                    content()
                }

                miniPlayer?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 16.dp)
                    ) {
                        it(miniPlayerRequester)
                    }
                }
            }
        }
    }
}
