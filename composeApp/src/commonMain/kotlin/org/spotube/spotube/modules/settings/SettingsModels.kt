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

package org.spotube.spotube.modules.settings

import org.spotube.plugin_interfaces.plugin_apis.audio.AudioFormat
import org.spotube.plugin_interfaces.plugin_apis.audio.AudioQuality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.spotube.millennium.theme.MillenniumPalette

@Serializable
const val TV_CONTENT_SCALE_AUTO = 0

/** Safe TV content scales: below 75 % text is hard to read from a sofa, above 100 % covers crop. */
val TV_CONTENT_SCALE_OPTIONS = listOf(TV_CONTENT_SCALE_AUTO, 75, 80, 85, 90, 95, 100)

enum class Theme {
    LIGHT, DARK, SYSTEM
}

/** Preferencias portables: cada plataforma elige la mejor voz disponible. */
@Serializable enum class DjVoiceCharacter { NATURAL, WARM, ENERGETIC, CALM }
@Serializable enum class DjVoiceGender { AUTO, FEMALE, MALE, NEUTRAL }
@Serializable enum class DjVoiceAccent { SYSTEM, COLOMBIA, MEXICO, SPAIN, ARGENTINA }
@Serializable enum class DjVoiceExpressiveness { NATURAL, SOFT, EXPRESSIVE }

@Serializable
data class UserSettings(
    // Language and Region
    // Stored under a new name: the old "language" field was persisted as EN for everyone while
    // it had no effect, so reading it would force English on existing users.
    @SerialName("appLanguage")
    val language: SupportedLanguages = SupportedLanguages.SYSTEM,
    val country: CountryCode = CountryCode.BD,

    // Appearance
    val theme: Theme = Theme.SYSTEM,
    val palette: MillenniumPalette = MillenniumPalette.Blue,
    // TV only: percent the screen content is drawn at, one of [TV_CONTENT_SCALE_OPTIONS];
    // [TV_CONTENT_SCALE_AUTO] fits the content to the screen height.
    val tvContentScale: Int = TV_CONTENT_SCALE_AUTO,
    // Keep the display on (no screensaver / sleep) while the app is visible.
    // Null = device default: on for TV, off elsewhere.
    val keepScreenOn: Boolean? = null,

    // Playback
    val streamingMusicFormat: AudioFormat = defaultStreamingMusicFormat(),
    val streamingMusicQuality: AudioQuality = AudioQuality.Lossy(bitrate = 256_000),
    val enableMusicCaching: Boolean = true,
    val cacheFolder: String? = null,
    val cacheSizeLimitMB: Long = -1L,
    val enableEndlessPlayback: Boolean = true,
    val gaplessEnabled: Boolean = true,
    val crossfadeSeconds: Int = 5,
    val crossfadeCurve: org.spotube.spotube.features.player.CrossfadeCurve = org.spotube.spotube.features.player.CrossfadeCurve.EQUAL_POWER,
    val autoMixEnabled: Boolean = false,
    val autoMixBars: Int = 4,
    val playlistMixOverrides: Map<String, Boolean> = emptyMap(),
    val djMode: Boolean = false,
    val djName: String = "Livi",
    val djVoiceCharacter: DjVoiceCharacter = DjVoiceCharacter.NATURAL,
    val djVoiceGender: DjVoiceGender = DjVoiceGender.AUTO,
    val djVoiceAccent: DjVoiceAccent = DjVoiceAccent.SYSTEM,
    val djVoiceExpressiveness: DjVoiceExpressiveness = DjVoiceExpressiveness.NATURAL,
    val djVoiceSpeed: Float = 1f,
    // Solo preferencias locales; las claves se guardan en el almacen seguro nativo.
    val djAiEnabled: Boolean = false,
    val djAiProvider: String = "OPENROUTER",
    val djAiModels: Map<String, String> = emptyMap(),
    val djAiRequest: String = "",
    val playbackProxyServerPort: Int = 14769,

    // Remote Control (LAN)
    val allowRemoteControl: Boolean = false,
    val allowedRemoteDevices: List<String> = emptyList(),
    val remoteControlDeviceName: String = "",
    val remoteControlDeviceId: String = "",

    // Group Jam
    val jamParticipantName: String = "",
    /** Room server (https://github.com/team-spotube/jam-server); self-hosters point this at their own deployment. */
    val jamServerUrl: String = DEFAULT_JAM_SERVER_URL,
    val lastJamCode: String = "",
    /** Random per-install secret behind this device's jam identities (see `jamParticipantKey`). */
    val jamDeviceKey: String = "",

    // Downloads
    val overloadedDownloadFolder: String? = null, // When null, uses default music folder
    val localMediaFolders: List<String> = emptyList(),
    val downloadMusicFormat: AudioFormat = AudioFormat(
        codec = "aac",
        container = "mp4",
        qualities = listOf(
            AudioQuality.Lossy(bitrate = 44_000),
            AudioQuality.Lossy(bitrate = 96_000),
            AudioQuality.Lossy(bitrate = 128_000),
            AudioQuality.Lossy(bitrate = 256_000))
    ),
    val downloadMusicQuality: AudioQuality = AudioQuality.Lossy(bitrate = 256_000),

    // Desktop
    val minimizeToTray: Boolean = false,
    val discordRichPresence: Boolean = true,

    // Updates
    val autoCheckForUpdates: Boolean = true,
    val ignoredAppUpdateVersion: String? = null,
    val ignoredPluginUpdateVersions: Map<String, String> = emptyMap(),

    // Support
    val supportRemindersEnabled: Boolean = true,
    val lastSupportReminderEpochMs: Long = 0L)

/** The public Group Jam room server. */
const val DEFAULT_JAM_SERVER_URL = "https://jam.spotube.cc"

/** Whether [url] can be a room server address (http(s), or ws(s) for self-hosters). */
internal fun isJamServerUrl(url: String): Boolean =
    Regex("^(https?|wss?)://[^\\s/?#]+(/\\S*)?$", RegexOption.IGNORE_CASE).matches(url.trim())

/** opus/webm on every platform: all of their players (ExoPlayer, VLC) can play it. */
internal fun defaultStreamingMusicFormat(): AudioFormat = AudioFormat(
    codec = "opus",
    container = "webm",
    qualities = listOf(
        AudioQuality.Lossy(bitrate = 44_000),
        AudioQuality.Lossy(bitrate = 96_000),
        AudioQuality.Lossy(bitrate = 128_000),
        AudioQuality.Lossy(bitrate = 256_000)))
