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

package org.spotube.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.spotube.tv.theme.LocalTvColors
import org.spotube.tv.theme.TvTheme

/**
 * Default TV root: only design tokens and a background. The real app content is supplied through
 * [TvBridge]; this fallback is used if the bridge is not installed (for example a preview or a
 * manifest-merger smoke test) and deliberately contains no text, because the library cannot
 * resolve the app's string resources.
 */
@Composable
fun TvApp(content: @Composable () -> Unit) {
    TvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalTvColors.current.background)
        ) {
            content()
        }
    }
}
