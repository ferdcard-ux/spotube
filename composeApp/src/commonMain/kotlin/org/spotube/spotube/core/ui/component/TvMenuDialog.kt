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

package org.spotube.spotube.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.composeunstyled.Text
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import org.jetbrains.compose.resources.stringResource
import org.spotube.millennium.composables.MillenniumIcon
import org.spotube.millennium.composables.MillenniumSeparator
import org.spotube.spotube.core.ui.theming.AppTheme
import org.spotube.spotube.core.ui.tvFocusRing
import spotube.composeapp.generated.resources.Res
import spotube.composeapp.generated.resources.accessibility_selected

/**
 * TV presentation of [AdaptiveDropdownBottomSheet]. The library dropdown keeps D-pad focus on its
 * panel (Up/Down never reach the items on TV), so on TV the options are a centered dialog of plain
 * focusable rows: focus starts on the selected option, Up/Down walk the list, OK picks, Back closes.
 */
@Composable
internal fun TvMenuDialog(
    items: List<AdaptiveMenuItem>,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null
) {
    val initialFocus = remember { FocusRequester() }
    val initialIndex = items.indexOfFirst { it.selected && it.enabled }
        .takeIf { it >= 0 }
        ?: items.indexOfFirst { it.enabled }.coerceAtLeast(0)
    val hasLeading = items.any { it.icon != null || it.leadingContent != null }
    val shape = AppTheme.shapes.medium

    LaunchedEffect(Unit) {
        // The dialog window attaches a frame after composition.
        withFrameNanos { }
        runCatching { initialFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 560.dp)
                .heightIn(max = 480.dp)
                .clip(shape)
                .background(AppTheme.colors.surface)
                .padding(8.dp)
        ) {
            header?.let {
                Box(modifier = Modifier.padding(8.dp)) { it() }
                MillenniumSeparator()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEachIndexed { index, item ->
                    if (item.dividerBefore) MillenniumSeparator()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == initialIndex) {
                                    Modifier.focusRequester(initialFocus)
                                } else {
                                    Modifier
                                }
                            )
                            .tvFocusRing(shape)
                            .clip(shape)
                            .background(
                                if (item.selected) AppTheme.colors.primary.copy(alpha = 0.16f)
                                else AppTheme.colors.surface
                            )
                            .clickable(enabled = item.enabled) {
                                item.onClick()
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when {
                            item.leadingContent != null -> item.leadingContent.invoke()
                            item.icon != null -> MillenniumIcon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            hasLeading -> Spacer(Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                style = AppTheme.typography.bodyLarge,
                                color = if (item.enabled) {
                                    AppTheme.colors.onSurface
                                } else {
                                    AppTheme.colors.onSurface.copy(alpha = 0.38f)
                                }
                            )
                            item.subtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = AppTheme.typography.bodySmall,
                                    color = AppTheme.colors.onSurfaceVariant
                                )
                            }
                        }
                        if (item.selected) {
                            MillenniumIcon(
                                imageVector = FeatherIcons.Check,
                                contentDescription = stringResource(Res.string.accessibility_selected),
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
