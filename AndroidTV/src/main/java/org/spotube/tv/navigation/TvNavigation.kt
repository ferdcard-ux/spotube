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

package org.spotube.tv.navigation

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One rail entry. The label is already resolved by the app (`stringResource(Res.string.*)`),
 * because this module cannot see the Compose resources generated in `:composeApp`.
 */
data class TvRailItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/** One in-screen tab (Library sections, Settings sections, ...). */
data class TvTabItem(
    val id: String,
    val label: String,
    val onClick: () -> Unit
)

/** Focus keys shared by the shell and the screens. */
object TvNavigation {
    private const val RailPrefix = "rail:"
    private const val ContentPrefix = "content:"

    fun railKey(id: String): String = "$RailPrefix$id"

    fun contentKey(destinationId: String): String = "$ContentPrefix$destinationId"
}
