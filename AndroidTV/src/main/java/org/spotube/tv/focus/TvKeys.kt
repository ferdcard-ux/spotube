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

package org.spotube.tv.focus

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** True for the key-down part of a key event. */
val KeyEvent.isKeyDown: Boolean
    get() = type == KeyEventType.KeyDown

/**
 * Routes hardware media keys to playback. Media3's MediaSession may not receive them while the
 * TV activity is in the foreground, so the shell forwards them explicitly.
 */
fun Modifier.tvMediaKeys(
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRewind: (() -> Unit)? = null,
    onFastForward: (() -> Unit)? = null
): Modifier = onPreviewKeyEvent { event ->
    if (!event.isKeyDown) {
        false
    } else {
        when (event.key) {
            Key.MediaPlayPause -> {
                onPlayPause()
                true
            }
            Key.MediaNext -> {
                onNext()
                true
            }
            Key.MediaPrevious -> {
                onPrevious()
                true
            }
            Key.MediaRewind -> onRewind?.let { it(); true } ?: false
            Key.MediaFastForward -> onFastForward?.let { it(); true } ?: false
            else -> false
        }
    }
}

/**
 * Intercepts the D-pad directions a component needs for a non-navigation action (queue reorder,
 * player seek). Unhandled keys fall through to Compose's spatial focus system.
 */
fun Modifier.tvDirectionKeys(
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null
): Modifier = onPreviewKeyEvent { event ->
    if (!event.isKeyDown) {
        false
    } else {
        when (event.key) {
            Key.DirectionLeft -> onLeft?.let { it(); true } ?: false
            Key.DirectionRight -> onRight?.let { it(); true } ?: false
            Key.DirectionUp -> onUp?.let { it(); true } ?: false
            Key.DirectionDown -> onDown?.let { it(); true } ?: false
            else -> false
        }
    }
}
