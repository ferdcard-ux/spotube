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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.LocalTvType

/** 10-foot empty/error state with an optional focusable action. */
@Composable
fun TvEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalTvColors.current
    val type = LocalTvType.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.text.BasicText(
            text = title,
            style = type.title.copy(color = colors.onBackground, textAlign = TextAlign.Center)
        )
        message?.let {
            androidx.compose.foundation.text.BasicText(
                text = it,
                style = type.body.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            TvButton(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}
