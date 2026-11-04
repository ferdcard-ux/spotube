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

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Copy and icons are supplied by the app. The library never hardcodes user-facing text: every
 * label comes from `composeApp/src/commonMain/composeResources/values/strings.xml`.
 */
data class TvPlayerLabels(
    val play: String,
    val pause: String,
    val next: String,
    val previous: String,
    val queue: String,
    val openPlayer: String,
    val close: String,
    val seekBackward: String,
    val seekForward: String,
    val seekBar: String,
    val lyrics: String = ""
)

data class TvPlayerIcons(
    val play: ImageVector,
    val pause: ImageVector,
    val next: ImageVector,
    val previous: ImageVector,
    val queue: ImageVector,
    val close: ImageVector,
    val seekBackward: ImageVector,
    val seekForward: ImageVector,
    val lyrics: ImageVector? = null
)

/** One queue row, already mapped from the app's [QueueItemUi]. */
data class TvQueueItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val isCurrent: Boolean,
    val index: Int
)
