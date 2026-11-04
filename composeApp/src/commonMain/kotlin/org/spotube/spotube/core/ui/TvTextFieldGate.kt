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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
 * Keeps a text field from grabbing the D-pad on TV.
 *
 * A focused text field opens the system keyboard and swallows Up/Down, so walking past one with
 * the remote traps focus. On TV the field sits behind a focusable gate: moving onto it only shows
 * a focus ring, OK starts editing (the field takes focus and the IME opens), and Up/Down or Back
 * leave editing and continue normal navigation. Off TV, [field] is rendered unchanged.
 *
 * The editing flag is a focus mechanic of this one control, not screen state, so it lives here.
 */
@Composable
fun TvTextFieldGate(
    modifier: Modifier = Modifier,
    field: @Composable (fieldModifier: Modifier) -> Unit
) {
    if (!LocalIsTv.current) {
        field(modifier)
        return
    }

    var editing by remember { mutableStateOf(false) }
    var gateFocused by remember { mutableStateOf(false) }
    val gateRequester = remember { FocusRequester() }
    val fieldRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val ringShape = AppTheme.shapes.medium

    LaunchedEffect(editing) {
        if (editing) runCatching { fieldRequester.requestFocus() }
    }

    fun stopEditing(then: FocusDirection? = null) {
        editing = false
        runCatching { gateRequester.requestFocus() }
        if (then != null) focusManager.moveFocus(then)
    }

    Box(
        modifier = modifier
            .focusRequester(gateRequester)
            .onFocusChanged { gateFocused = it.isFocused }
            .border(
                width = if (gateFocused) 3.dp else 0.dp,
                color = if (gateFocused) AppTheme.colors.primary else Color.Transparent,
                shape = ringShape
            )
            .onPreviewKeyEvent { event ->
                val isSelect = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                when {
                    !editing && isSelect -> {
                        if (event.type == KeyEventType.KeyUp) editing = true
                        true
                    }
                    editing && event.key == Key.Back -> {
                        if (event.type == KeyEventType.KeyUp) stopEditing()
                        true
                    }
                    editing && event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> {
                        stopEditing(
                            if (event.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down
                        )
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        field(
            Modifier
                .focusRequester(fieldRequester)
                .focusProperties { canFocus = editing }
                .onFocusChanged { state ->
                    if (editing && !state.hasFocus && !gateFocused) editing = false
                }
        )
    }
}
