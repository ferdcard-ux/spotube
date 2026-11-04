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

package org.spotube.tv.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The content is never scaled below this fraction of the device density. */
private const val MinContentScale = 0.75f

/**
 * Draws phone-sized app screens so they fit a TV. The screens are laid out for at least
 * [minHeightDp] of height; on a 1080p TV (density 2) the area is shorter, and a system font scale
 * above 100 % makes their text taller still, so cards and text got cut. When that happens the
 * content is drawn with a smaller density (between 75 % and 100 %).
 *
 * @param contentScale fixed fraction chosen by the user; null computes it from the height and the
 *   system font scale.
 */
@Composable
fun TvFitContent(
    modifier: Modifier = Modifier,
    contentScale: Float? = null,
    minHeightDp: Float,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val scale = contentScale ?: run {
            val heightFit = maxHeight.value / minHeightDp
            // Text grows with the font scale; shrink the density to compensate.
            (heightFit / density.fontScale.coerceAtLeast(1f)).coerceIn(MinContentScale, 1f)
        }
        val contentDensity = remember(density, scale) {
            Density(density.density * scale, density.fontScale)
        }
        CompositionLocalProvider(LocalDensity provides contentDensity) {
            content()
        }
    }
}
