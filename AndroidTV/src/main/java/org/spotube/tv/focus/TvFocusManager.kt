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

package org.spotube.tv.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/**
 * Keeps one [FocusRequester] per semantic destination key (rail item, dialog row, ...). Screen
 * composables request focus by key instead of owning ad-hoc requesters, which keeps focus
 * restoration predictable when navigating back.
 */
class TvFocusManager {
    private val requesters = mutableMapOf<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** Returns false when the requester has not been attached yet (e.g. first frame). */
    fun requestFocus(key: String): Boolean {
        val requester = requesters[key] ?: return false
        return runCatching {
            requester.requestFocus()
            true
        }.getOrDefault(false)
    }

    fun clear(key: String) {
        requesters.remove(key)
    }
}

@Composable
fun rememberTvFocusManager(): TvFocusManager = remember { TvFocusManager() }
