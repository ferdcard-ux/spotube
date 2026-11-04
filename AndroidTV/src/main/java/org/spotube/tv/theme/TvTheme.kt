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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours used by the TV layer. The app provides them from the active Millennium theme, so the
 * TV shell and the shared screens always paint the same palette.
 */
data class TvColors(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val accent: Color,
    val focusRing: Color
) {
    companion object {
        val Default = TvColors(
            background = Color(0xFF0C0C10),
            surface = Color(0xFF17171C),
            onBackground = Color(0xFFF5F5F5),
            onSurfaceVariant = Color(0xFFB3B3B8),
            accent = Color(0xFF1DB954),
            focusRing = Color(0xFF1DB954)
        )
    }
}

val LocalTvColors = staticCompositionLocalOf { TvColors.Default }
val LocalTvType = staticCompositionLocalOf { TvType.Default }

/**
 * Installs the TV design tokens. It does not install a Material theme: the app already wraps the
 * whole composition in [org.spotube.spotube.core.ui.theming.SpotubeTheme].
 */
@Composable
fun TvTheme(
    colors: TvColors = TvColors.Default,
    type: TvType = TvType.Default,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTvColors provides colors,
        LocalTvType provides type,
        content = content
    )
}
