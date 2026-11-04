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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.spotube.tv.focus.tvBringIntoView
import org.spotube.tv.focus.tvFocusGroup
import org.spotube.tv.theme.TvDimens

/**
 * TV grid: at least 280dp per cell (instead of the phone's 160dp) so the artwork and title stay
 * readable at 3 meters. Focused cells scroll into view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TvGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    columns: GridCells = GridCells.Adaptive(TvDimens.GridCell),
    itemContent: @Composable (T) -> Unit
) {
    val scope = rememberCoroutineScope()
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier
            .fillMaxSize()
            .tvFocusGroup(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(TvDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(TvDimens.Gutter)
    ) {
        items(count = items.size) { index ->
            val requester = remember(index) { BringIntoViewRequester() }
            Box(
                modifier = Modifier
                    .tvBringIntoView(requester)
                    .onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
                            scope.launch {
                                runCatching { requester.bringIntoView() }
                            }
                        }
                    }
            ) {
                itemContent(items[index])
            }
        }
    }
}
