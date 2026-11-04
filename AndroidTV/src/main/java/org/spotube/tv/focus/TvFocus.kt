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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Modifier

/**
 * Row/grid containers should form a single focus group so the D-pad does not wander into
 * unrelated siblings.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvFocusGroup(): Modifier = focusGroup()

/**
 * Attaches a [BringIntoViewRequester] so focused cards scroll into view when a LazyRow/LazyGrid
 * moves the item out of the viewport.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvBringIntoView(requester: BringIntoViewRequester): Modifier =
    bringIntoViewRequester(requester)
