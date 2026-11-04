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

package org.spotube.spotube.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Applies the [NavigationCommands] sent by non-composable code (view models, dialogs dismissing
 * themselves, WebView controller) to [navigator]. Every app shell must host it once, otherwise
 * those commands are silently dropped.
 */
@Composable
fun NavigationCommandsEffect(
    navigator: Navigator,
    navigationState: NavigationState
) {
    val navigatorCommands: NavigationCommands = koinInject()
    LaunchedEffect(navigatorCommands, navigator) {
        launch {
            navigatorCommands.navigationCommandFlow.collect { route ->
                navigator.navigate(route)
            }
        }
        launch {
            navigatorCommands.navigationPopCommandFlow.collect { route ->
                navigationState.backStacks[navigationState.topLevelRoute]?.let { backStack ->
                    backStack.lastOrNull()?.let { currentRoute ->
                        if (route == null || route == currentRoute) {
                            navigator.pop()
                        }
                    }
                }
            }
        }
    }
}
