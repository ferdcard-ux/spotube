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

package org.spotube.spotube.modules.profiles

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.spotube.spotube.core.navigation.NavigationCommands
import org.spotube.spotube.core.navigation.Routes
import org.spotube.spotube.core.profile.ProfileStore
import org.spotube.spotube.core.profile.restartApplication

data class ProfileItemUi(
    val id: String,
    /** Null for the main profile, which the screen shows with its localized name. */
    val name: String?,
    val initial: String?,
    val isActive: Boolean,
    val canDelete: Boolean
)

enum class ProfileEditorMode { Add, Rename }

data class ProfileEditorUi(
    val mode: ProfileEditorMode,
    val profileId: String?,
    val canSave: Boolean
)

data class ProfilesUiState(
    val profiles: List<ProfileItemUi> = emptyList(),
    /** The profile in use, for the profile buttons (rail, sidebar, Home header). */
    val activeProfile: ProfileItemUi? = null,
    val editor: ProfileEditorUi? = null,
    /** Profile waiting for delete confirmation, with the name to show. */
    val pendingDelete: ProfileItemUi? = null,
    /** The switch was saved but this platform cannot restart itself: ask the user to reopen. */
    val restartRequired: Boolean = false
)

private data class ProfilesUiFlags(
    val editorMode: ProfileEditorMode? = null,
    val editorProfileId: String? = null,
    val pendingDeleteId: String? = null,
    val restartRequired: Boolean = false
)

class ProfilesViewModel(
    private val store: ProfileStore,
    private val navigationCommands: NavigationCommands
) : ViewModel() {
    /** Text of the add / rename dialog's name field. */
    val nameField = TextFieldState()

    private val flags = MutableStateFlow(ProfilesUiFlags())

    val uiState: StateFlow<ProfilesUiState> = combine(
        store.snapshot,
        flags,
        snapshotFlow { nameField.text.toString() }
    ) { snapshot, f, name ->
        val items = snapshot.profiles.map { profile ->
            ProfileItemUi(
                id = profile.id,
                name = profile.name,
                initial = profile.name?.trim()?.firstOrNull()?.uppercase(),
                isActive = profile.id == snapshot.activeId,
                // Any profile but the active one, as long as another remains.
                canDelete = profile.id != snapshot.activeId && snapshot.profiles.size > 1
            )
        }
        ProfilesUiState(
            profiles = items,
            activeProfile = items.firstOrNull { it.isActive },
            editor = f.editorMode?.let { mode ->
                ProfileEditorUi(
                    mode = mode,
                    profileId = f.editorProfileId,
                    canSave = name.isNotBlank()
                )
            },
            pendingDelete = f.pendingDeleteId?.let { id -> items.firstOrNull { it.id == id } },
            restartRequired = f.restartRequired
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    fun switchTo(id: String) {
        if (store.snapshot.value.activeId == id) return
        store.setActive(id)
        if (!restartApplication()) {
            flags.value = flags.value.copy(restartRequired = true)
        }
    }

    fun startAdd() {
        nameField.clearText()
        flags.value = flags.value.copy(editorMode = ProfileEditorMode.Add, editorProfileId = null)
    }

    fun startRename(id: String) {
        val profile = store.snapshot.value.profiles.firstOrNull { it.id == id } ?: return
        nameField.setTextAndPlaceCursorAtEnd(profile.name.orEmpty())
        flags.value = flags.value.copy(editorMode = ProfileEditorMode.Rename, editorProfileId = id)
    }

    fun saveEditor() {
        val name = nameField.text.toString().trim()
        val f = flags.value
        if (name.isEmpty() || f.editorMode == null) return
        when (f.editorMode) {
            ProfileEditorMode.Add -> store.add(name)
            ProfileEditorMode.Rename -> f.editorProfileId?.let { store.rename(it, name) }
        }
        closeEditor()
    }

    fun closeEditor() {
        flags.value = flags.value.copy(editorMode = null, editorProfileId = null)
    }

    fun requestDelete(id: String) {
        flags.value = flags.value.copy(pendingDeleteId = id)
    }

    fun confirmDelete() {
        flags.value.pendingDeleteId?.let(store::remove)
        cancelDelete()
    }

    fun cancelDelete() {
        flags.value = flags.value.copy(pendingDeleteId = null)
    }

    /** The active profile's accounts are the metadata plugins' logins. */
    fun openAccounts() {
        navigationCommands.navigateTo(Routes.Plugins)
    }

    fun dismissRestartNotice() {
        flags.value = flags.value.copy(restartRequired = false)
    }
}
