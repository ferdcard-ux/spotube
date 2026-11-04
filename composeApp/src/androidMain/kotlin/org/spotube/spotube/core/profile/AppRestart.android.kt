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

package org.spotube.spotube.core.profile

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import org.koin.core.context.GlobalContext
import org.spotube.spotube.isTelevision

actual fun restartApplication(): Boolean {
    val context = runCatching { GlobalContext.get().get<Context>() }.getOrNull() ?: return false
    // Login web views share one process-wide cookie jar: never carry a session to another profile.
    runCatching {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
    val pm = context.packageManager
    val intent = (if (isTelevision()) pm.getLeanbackLaunchIntentForPackage(context.packageName) else null)
        ?: pm.getLaunchIntentForPackage(context.packageName)
        ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    // A fresh process rebuilds Koin, the plugins and the player for the new profile.
    Runtime.getRuntime().exit(0)
    return true
}
