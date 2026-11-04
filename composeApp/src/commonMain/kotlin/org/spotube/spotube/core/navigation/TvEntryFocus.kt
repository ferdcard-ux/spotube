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

package org.spotube.spotube.core.navigation

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * TV only: every navigation entry is a focus group that takes D-pad focus when it is shown and
 * remembers its focused child.
 *
 * Without it, opening a screen (or the app itself) leaves focus nowhere, or on the rail, and the
 * next key press starts again from the top-left corner. With it, a new screen focuses its first
 * item, and going back focuses the item that opened the next screen. The saved child lives in the
 * entry's saveable state, so this decorator must come after the saveable state holder decorator.
 */
@Composable
internal fun rememberTvEntryFocusDecorator(): NavEntryDecorator<NavKey> = remember {
    NavEntryDecorator { entry ->
        TvEntryFocus { entry.Content() }
    }
}

@Composable
private fun TvEntryFocus(content: @Composable () -> Unit) {
    val requester = remember { FocusRequester() }
    // Plain holder: read only by the effect below, must not recompose the entry.
    val hasFocus = remember { BooleanArray(1) }

    Box(
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { hasFocus[0] = it.hasFocus }
            // focusRestorer only records the child when focus leaves the group; opening another
            // screen with OK never does, so record it before every key is handled.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) runCatching { requester.saveFocusedChild() }
                false
            }
            .focusRestorer()
            .focusGroup()
    ) {
        content()
    }

    LaunchedEffect(Unit) {
        // Let lazy lists compose their items (and screens request their own focus) first. A
        // screen that is still loading has nothing focusable yet, so keep trying for a while.
        val deadline = TimeSource.Monotonic.markNow() + 5.seconds
        repeat(3) { withFrameNanos { } }
        while (!hasFocus[0] && deadline.hasNotPassedNow()) {
            val focused = runCatching { requester.requestFocus(FocusDirection.Enter) }
                .getOrDefault(false)
            if (focused) break
            delay(100)
        }
    }
}
