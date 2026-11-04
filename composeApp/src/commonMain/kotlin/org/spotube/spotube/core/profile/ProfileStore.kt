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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.spotube.spotube.core.paths.Paths
import kotlin.random.Random

/** One local user profile. [name] is null for the built-in main profile (shown localized). */
@Serializable
data class UserProfile(
    val id: String,
    val name: String? = null
)

@Serializable
data class ProfilesSnapshot(
    val profiles: List<UserProfile> = listOf(UserProfile(ProfileStore.MAIN_PROFILE_ID)),
    val activeId: String = ProfileStore.MAIN_PROFILE_ID
)

/**
 * Local profiles: each one keeps its own plugin sessions (accounts), queue and blacklist, while
 * settings, installed plugins and caches stay shared by the device. One profile is active at a
 * time; switching writes the new active id and restarts the app, so every singleton (database,
 * plugins, player) is rebuilt for it.
 *
 * The main profile uses the data directory itself, so installs from before profiles existed keep
 * their data without any migration. Other profiles live in `profiles/<id>/`.
 *
 * Stored as a small JSON file read synchronously, because [org.spotube.spotube.core.db.Database]
 * needs the active profile's directory while it is being constructed.
 */
class ProfileStore(private val paths: Paths) {
    private val baseDir = paths.getApplicationDataDirPath()
    private val file = "$baseDir/$FILE_NAME".toPath()

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<ProfilesSnapshot> = _snapshot.asStateFlow()

    /** Directory holding the active profile's own data stores. */
    val activeDataDir: String = dataDirOf(_snapshot.value.activeId)

    fun dataDirOf(id: String): String {
        if (id == MAIN_PROFILE_ID) return baseDir
        val dir = "$baseDir/$PROFILES_DIR/$id"
        runCatching { FileSystem.SYSTEM.createDirectories(dir.toPath()) }
        return dir
    }

    fun add(name: String): UserProfile {
        val profile = UserProfile(id = newId(), name = name.trim())
        write(_snapshot.value.copy(profiles = _snapshot.value.profiles + profile))
        return profile
    }

    fun rename(id: String, name: String) {
        write(
            _snapshot.value.copy(
                profiles = _snapshot.value.profiles.map {
                    if (it.id == id) it.copy(name = name.trim()) else it
                }
            )
        )
    }

    /**
     * Removes a profile and its own data. The active profile and the last remaining one cannot be
     * removed. The main profile's data lives in the shared data directory, so only its own files
     * ([PROFILE_FILES]) are deleted there; shared settings, plugins and caches stay.
     */
    fun remove(id: String) {
        val current = _snapshot.value
        if (id == current.activeId || current.profiles.size <= 1) return
        if (current.profiles.none { it.id == id }) return
        write(current.copy(profiles = current.profiles.filterNot { it.id == id }))
        runCatching {
            if (id == MAIN_PROFILE_ID) {
                PROFILE_FILES.forEach { name -> FileSystem.SYSTEM.delete("$baseDir/$name".toPath()) }
            } else {
                FileSystem.SYSTEM.deleteRecursively("$baseDir/$PROFILES_DIR/$id".toPath())
            }
        }
    }

    /** Marks [id] as active. Takes effect after the app restarts. */
    fun setActive(id: String) {
        val current = _snapshot.value
        if (current.profiles.none { it.id == id }) return
        write(current.copy(activeId = id))
    }

    private fun newId(): String {
        val existing = _snapshot.value.profiles.map { it.id }.toSet()
        while (true) {
            val id = "p" + Random.nextLong(0, Long.MAX_VALUE).toString(36)
            if (id !in existing) return id
        }
    }

    private fun read(): ProfilesSnapshot = runCatching {
        if (!FileSystem.SYSTEM.exists(file)) return@runCatching ProfilesSnapshot()
        val text = FileSystem.SYSTEM.read(file) { readUtf8() }
        val decoded = json.decodeFromString<ProfilesSnapshot>(text)
        // Always keep at least one profile and a valid active id. The main profile may have
        // been removed (its data directory is still the shared one).
        val profiles = decoded.profiles.ifEmpty { listOf(UserProfile(MAIN_PROFILE_ID)) }
        ProfilesSnapshot(
            profiles = profiles,
            activeId = decoded.activeId.takeIf { id -> profiles.any { it.id == id } }
                ?: profiles.first().id
        )
    }.getOrDefault(ProfilesSnapshot())

    private fun write(snapshot: ProfilesSnapshot) {
        runCatching {
            FileSystem.SYSTEM.createDirectories(baseDir.toPath())
            FileSystem.SYSTEM.write(file) { writeUtf8(json.encodeToString(ProfilesSnapshot.serializer(), snapshot)) }
        }
        _snapshot.value = snapshot
    }

    companion object {
        const val MAIN_PROFILE_ID = "main"
        private const val FILE_NAME = "spotube_profiles.json"
        private const val PROFILES_DIR = "profiles"

        /** Per-profile data stores (see [org.spotube.spotube.core.db.Database]). */
        val PROFILE_FILES = listOf(
            "spotube_plugins.preferences_pb",
            "spotube_audio_player_queue.preferences_pb",
            "spotube_blacklist.preferences_pb"
        )
        private val json = Json { ignoreUnknownKeys = true }
    }
}
