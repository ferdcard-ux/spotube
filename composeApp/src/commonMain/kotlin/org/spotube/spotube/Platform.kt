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
import androidx.compose.runtime.staticCompositionLocalOf

enum class PlatformType {
    Android, IOS, Windows, Linux, MacOS, Unknown
}

interface Platform {
    val name: String
    val type: PlatformType
}

expect fun getPlatform(): Platform

expect fun openUrlInBrowser(url: String)

/**
 * True when the current device is an Android TV / Leanback device. Implementations on desktop and
 * iOS always return false, so the TV layer stays Android-only without branching on platform type.
 */
expect fun isTelevision(): Boolean

/**
 * Configuration-aware variant used by Composables. Android resolves it from the activity context
 * and falls back to the package-manager Leanback/Television features; other targets return false.
 */
@Composable
expect fun rememberIsTv(): Boolean

/** Composition-local flag installed by the TV root, so shared screens can adapt their layout. */
val LocalIsTv = staticCompositionLocalOf { false }

fun Platform.isDesktop(): Boolean {
    return type == PlatformType.Windows ||
            type == PlatformType.Linux ||
            type == PlatformType.MacOS
}

fun Platform.isMobile(): Boolean {
    return type == PlatformType.Android ||
            type == PlatformType.IOS
}