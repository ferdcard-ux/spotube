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

package org.spotube.spotube

import androidx.compose.runtime.Composable
import org.spotube.spotube.core.navigation.NavigationState
import org.spotube.spotube.core.navigation.Navigator

/**
 * Selects the shell that wraps the navigation content.
 *
 * Mobile, desktop and iOS use the existing [org.spotube.spotube.modules.shell.AppShell]. Android
 * additionally exposes a TV shell from the `:AndroidTV` module, which cannot be referenced from
 * `commonMain`; the androidMain actual keeps that branch platform-local.
 */
@Composable
expect fun AppShellHost(
    navigator: Navigator,
    navigationState: NavigationState,
    content: @Composable () -> Unit
)
