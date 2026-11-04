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

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable

/**
 * One-way bridge between the Android TV library and the application module.
 *
 * AGP does not allow a library to depend on the app module, so `:AndroidTV` owns the Leanback
 * activity while `:composeApp/androidMain` installs the actual root composition (which needs
 * Koin, navigation, ViewModels and the shared screens). This object is only mutated once, from
 * `MyApplication`, before any TV activity is created.
 */
object TvBridge {
    private var content: (@Composable () -> Unit)? = null
    private var onActivityCreated: ((ComponentActivity) -> Unit)? = null
    private var onNewIntent: ((ComponentActivity, Intent) -> Unit)? = null

    fun install(
        content: @Composable () -> Unit,
        onActivityCreated: ((ComponentActivity) -> Unit)? = null,
        onNewIntent: ((ComponentActivity, Intent) -> Unit)? = null
    ) {
        this.content = content
        this.onActivityCreated = onActivityCreated
        this.onNewIntent = onNewIntent
    }

    internal fun content(): (@Composable () -> Unit)? = content

    internal fun notifyActivityCreated(activity: ComponentActivity) {
        onActivityCreated?.invoke(activity)
    }

    internal fun notifyNewIntent(activity: ComponentActivity, intent: Intent) {
        onNewIntent?.invoke(activity, intent)
    }
}
