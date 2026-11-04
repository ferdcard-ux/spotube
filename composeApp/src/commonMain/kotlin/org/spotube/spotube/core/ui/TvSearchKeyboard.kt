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

package org.spotube.spotube.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Optional on-screen keyboard supplied by the TV shell.
 *
 * `commonMain` cannot reference `:AndroidTV`, so the TV host installs this slot and the shared
 * search screen renders it when [org.spotube.spotube.LocalIsTv] is true. It is `null` on every
 * other target, which keeps the regular IME path untouched.
 */
val LocalTvSearchKeyboard = staticCompositionLocalOf<
    (@Composable (
        query: String,
        onQueryChange: (String) -> Unit,
        onSubmit: () -> Unit,
        onDismiss: () -> Unit
    ) -> Unit)?
    > { null }
