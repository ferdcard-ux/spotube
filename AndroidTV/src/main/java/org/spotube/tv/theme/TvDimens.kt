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

package org.spotube.tv.theme

import androidx.compose.ui.unit.dp

/**
 * 10-foot layout tokens for the TV layer.
 *
 * The XML mirror lives in `res/values/dimens.xml` for manifest/launcher consumers; Compose code
 * uses these values directly so the module has a single readable source inside the composition.
 */
object TvDimens {
    /** 5% horizontal overscan on a 1920x1080 panel. */
    val OverscanHorizontal = 48.dp

    /** 5% vertical overscan on a 1920x1080 panel. */
    val OverscanVertical = 27.dp

    val RailWidth = 96.dp
    val RailItemHeight = 64.dp
    val MinTarget = 56.dp
    val FocusRing = 3.dp
    val GridCell = 280.dp
    val Gutter = 16.dp
    val MiniPlayerHeight = 96.dp
    val CornerRadius = 16.dp
    val DialogMaxWidth = 1080.dp
}
