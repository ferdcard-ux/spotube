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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.spotube.tv.focus.tvBringIntoView
import org.spotube.tv.focus.tvFocusGroup
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.LocalTvType
import org.spotube.tv.theme.TvDimens

/**
 * Horizontal TV row. Each item observes focus and brings itself into view, replacing the
 * touch-only drag scrollbars used on phones.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TvRow(
    title: String?,
    items: List<T>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = TvDimens.Gutter,
    itemContent: @Composable (T) -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let {
            TvTitle(
                text = it,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusGroup(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing)
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
}
