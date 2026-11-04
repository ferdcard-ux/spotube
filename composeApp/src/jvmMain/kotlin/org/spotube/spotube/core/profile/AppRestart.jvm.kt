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

import kotlin.system.exitProcess

/**
 * Starts a new copy of this process (same executable and arguments: the packaged/native binary,
 * or `java …` in development) and exits. Returns false when the command line is not available.
 */
actual fun restartApplication(): Boolean {
    val info = ProcessHandle.current().info()
    val command = info.command().orElse(null) ?: return false
    val arguments = info.arguments().orElse(emptyArray())
    return runCatching {
        ProcessBuilder(listOf(command) + arguments)
            .inheritIO()
            .start()
        exitProcess(0)
    }.getOrDefault(false)
}
