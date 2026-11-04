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

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

/**
 * Keeps interactive content inside the 5% overscan-safe area used by TV panels. Apply it once,
 * at the root of the TV shell, inside `enableEdgeToEdge()`.
 */
fun Modifier.tvSafeArea(
    horizontal: androidx.compose.ui.unit.Dp = TvDimens.OverscanHorizontal,
    vertical: androidx.compose.ui.unit.Dp = TvDimens.OverscanVertical
): Modifier = padding(horizontal = horizontal, vertical = vertical)
