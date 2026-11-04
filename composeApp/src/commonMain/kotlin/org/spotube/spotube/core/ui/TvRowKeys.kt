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

package org.spotube.spotube.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import org.spotube.spotube.LocalIsTv
import org.spotube.spotube.core.ui.theming.AppTheme

/**
 * TV behaviour for a list row whose secondary actions (options button, inline links) cannot be
 * reached with the D-pad: a visible focus ring, OK runs [onClick], and holding OK or pressing Menu
 * runs [onLongClick] (usually "show options"). Must be applied before the row's own `clickable`
 * so it observes the row's focus. Off TV it returns the modifier unchanged.
 */
@Composable
fun Modifier.tvRowKeys(
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier {
    if (!LocalIsTv.current) return this

    // Plain holder: key repeat bookkeeping must not trigger recomposition.
    val press = remember { BooleanArray(2) } // [0] = select held, [1] = long press already fired
    val rowFocused = remember { BooleanArray(1) }

    return this
        .tvFocusRing(shape)
        .onFocusChanged { rowFocused[0] = it.isFocused }
        .onPreviewKeyEvent { event ->
            // A focused inner button (options, links) handles its own keys.
            if (!rowFocused[0]) return@onPreviewKeyEvent false
            val isSelect = event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter
            when {
                event.key == Key.Menu -> {
                    if (event.type == KeyEventType.KeyUp) onLongClick()
                    true
                }
                isSelect && event.type == KeyEventType.KeyDown -> {
                    if (!press[0]) {
                        press[0] = true
                        press[1] = false
                    } else if (!press[1]) {
                        // A repeated KeyDown means the key is being held.
                        press[1] = true
                        onLongClick()
                    }
                    true
                }
                isSelect && event.type == KeyEventType.KeyUp -> {
                    val wasHeld = press[0]
                    val longFired = press[1]
                    press[0] = false
                    press[1] = false
                    if (wasHeld && !longFired) onClick()
                    true
                }
                else -> false
            }
        }
}

/**
 * TV only: makes the small buttons inside a focusable card or row (play, add to queue, options,
 * links) reachable with the D-pad.
 *
 * Compose's spatial search never moves from a container to something inside its own bounds, so
 * those buttons were unreachable. With this modifier, Right on the focused container moves into
 * its buttons ([entry] when given, otherwise the first one); Left from the leftmost button goes
 * back to the container. Other directions leave normally. Must be applied right before the
 * container's own `clickable`. Off TV it returns the modifier unchanged.
 */
@Composable
fun Modifier.tvInnerActions(entry: FocusRequester? = null): Modifier {
    if (!LocalIsTv.current) return this

    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Plain holder, focus bookkeeping only: [0] = container focused, [1] = a button inside it.
    val state = remember { BooleanArray(2) }

    return this
        .focusRequester(requester)
        .onFocusChanged {
            state[0] = it.isFocused
            state[1] = it.hasFocus && !it.isFocused
        }
        .focusProperties {
            onExit = {
                if (state[1] && requestedFocusDirection == FocusDirection.Left) {
                    runCatching { requester.requestFocus() }
                }
            }
        }
        .onPreviewKeyEvent { event ->
            if (!state[0] || event.key != Key.DirectionRight) return@onPreviewKeyEvent false
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (entry != null) {
                runCatching { entry.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
            } else {
                focusManager.moveFocus(FocusDirection.Enter)
            }
        }
}

/**
 * TV only: keeps D-pad focus inside a dialog that is drawn in the screen's own window (Millennium
 * dialogs are overlays, not separate windows), so arrows cannot move focus to the screen behind it.
 * Apply it to a container around all of the dialog's content. Off TV it returns the modifier
 * unchanged.
 */
@Composable
fun Modifier.tvFocusTrap(): Modifier {
    if (!LocalIsTv.current) return this
    return this
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()
}

/**
 * TV only: keeps D-pad focus on a button while it shows a loading state.
 *
 * A loading button is disabled, so it drops focus and Android hands it to the first focusable in
 * the window (the rail). Applied to the button's modifier, this adds a focus target around it that
 * holds focus while [busy] and gives it back to the button when the work ends. Off TV it returns
 * the modifier unchanged.
 */
@Composable
fun Modifier.tvKeepFocusWhileBusy(busy: Boolean): Modifier {
    if (!LocalIsTv.current) return this

    val requester = remember { FocusRequester() }
    // Plain holder, focus bookkeeping only: [0] = button focused, [1] = holding focus for it.
    val state = remember { BooleanArray(2) }
    // Composition sees `busy` before the disabled button loses focus, so record it here.
    if (busy && state[0]) state[1] = true

    LaunchedEffect(busy) {
        if (state[1]) {
            withFrameNanos { }
            // Busy: the outer target can focus and takes it. Done: it cannot, so the request
            // passes to the button inside it.
            runCatching { requester.requestFocus() }
            if (!busy) state[1] = false
        }
    }

    return this
        .focusRequester(requester)
        .onFocusChanged { state[0] = it.hasFocus }
        .focusProperties { canFocus = busy }
        .focusTarget()
}

/**
 * Visible focus ring for a clickable that has no focus styling of its own. TV only; must precede
 * the `clickable` it decorates.
 */
@Composable
fun Modifier.tvFocusRing(shape: Shape): Modifier {
    if (!LocalIsTv.current) return this

    var focused by remember { mutableStateOf(false) }
    val ringColor = AppTheme.colors.primary

    return this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) ringColor else Color.Transparent,
            shape = shape
        )
}
