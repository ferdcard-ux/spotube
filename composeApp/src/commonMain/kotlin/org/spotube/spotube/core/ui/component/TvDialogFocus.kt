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

package org.spotube.spotube.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.spotube.spotube.LocalIsTv
import org.spotube.spotube.core.ui.tvFocusTrap

/**
 * Wraps all of a `MillenniumDialog`'s content. Millennium dialogs draw in the screen's own window,
 * so on TV the D-pad could move focus to the screen behind them, and a dialog without a close
 * button opened with focus still on that screen. On TV this keeps focus inside the dialog and,
 * when nothing in it took focus as it opened, moves focus to its first item. Elsewhere it is a
 * plain full-width column.
 */
@Composable
fun TvDialogFocus(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!LocalIsTv.current) {
        Column(modifier = modifier.fillMaxWidth(), content = content)
        return
    }

    val requester = remember { FocusRequester() }
    // Plain holder: read only by the effect below.
    val hasFocus = remember { BooleanArray(1) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged { hasFocus[0] = it.hasFocus }
            .tvFocusTrap(),
        content = content
    )

    LaunchedEffect(Unit) {
        // Let the dialog's enter animation and its own initial focus run first.
        repeat(20) { withFrameNanos { } }
        if (!hasFocus[0]) runCatching { requester.requestFocus(FocusDirection.Enter) }
    }
}
