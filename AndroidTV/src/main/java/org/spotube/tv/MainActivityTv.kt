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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Leanback entry point. The phone keeps [org.spotube.spotube.MainActivity] (LAUNCHER); this
 * activity is declared with LEANBACK_LAUNCHER and shares the same APK, applicationId, signing
 * key, Media3 service and DataStore.
 */
class MainActivityTv : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The app installs FileKit/NewPipe/Paths/PlaybackService initialization here.
        TvBridge.notifyActivityCreated(this)

        val root = TvBridge.content()
        setContent {
            if (root != null) {
                root()
            } else {
                TvApp {}
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        TvBridge.notifyNewIntent(this, intent)
    }
}
