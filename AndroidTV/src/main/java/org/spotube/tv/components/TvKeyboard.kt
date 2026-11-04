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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.spotube.tv.theme.tvSafeArea
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.LocalTvType
import org.spotube.tv.theme.TvDimens

data class TvKeyboardLabels(
    val title: String,
    val clear: String,
    val backspace: String,
    val space: String,
    val submit: String,
    val dismiss: String
)

private val TvKeyboardRows = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("z", "x", "c", "v", "b", "n", "m")
)

/**
 * Self-contained on-screen keyboard for TV search/filters. It keeps the IME available for touch
 * devices (the shared screens still use their normal text fields) but makes search operable with a
 * D-pad on remotes without a system keyboard.
 */
@Composable
fun TvKeyboard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    labels: TvKeyboardLabels,
    modifier: Modifier = Modifier
) {
    val colors = LocalTvColors.current
    val type = LocalTvType.current
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstKey.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TvDimens.CornerRadius))
            .background(colors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TvTitle(text = labels.title)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TvDimens.MinTarget)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.background)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = query.ifEmpty { " " },
                style = type.body.copy(color = colors.onBackground),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        TvKeyboardRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    TvButton(
                        label = key,
                        onClick = { onQueryChange(query + key) },
                        minWidth = 64.dp,
                        modifier = if (key == TvKeyboardRows[1].first()) {
                            Modifier.focusRequester(firstKey)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvButton(label = labels.clear, onClick = { onQueryChange("") })
            TvButton(
                label = labels.backspace,
                onClick = {
                    if (query.isNotEmpty()) {
                        onQueryChange(query.dropLast(1))
                    }
                }
            )
            TvButton(label = labels.space, onClick = { onQueryChange("$query ") })
            Spacer(modifier = Modifier.size(8.dp))
            TvButton(label = labels.submit, onClick = onSubmit, selected = true)
            TvButton(label = labels.dismiss, onClick = onDismiss)
        }
    }
}

/**
 * Full-screen host for [TvKeyboard]. On a 1080p TV the content area between the rail header and
 * the mini player is too short for the key grid, so the keyboard is shown over everything instead
 * of inline. Back dismisses it.
 */
@Composable
fun TvKeyboardDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    labels: TvKeyboardLabels
) {
    val colors = LocalTvColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .tvSafeArea(),
            contentAlignment = Alignment.Center
        ) {
            TvKeyboard(
                query = query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onDismiss = onDismiss,
                labels = labels,
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
