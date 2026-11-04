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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.millennium.composables.MillenniumButtonStyle
import org.spotube.millennium.composables.MillenniumDialog
import org.spotube.millennium.composables.MillenniumDialogBody
import org.spotube.millennium.composables.MillenniumDialogClose
import org.spotube.millennium.composables.MillenniumDialogHeader
import org.spotube.millennium.composables.MillenniumDialogTitle
import org.spotube.millennium.composables.MillenniumDialogFooter
import org.spotube.millennium.composables.MillenniumIcon
import org.spotube.millennium.composables.MillenniumIconButton
import org.spotube.millennium.composables.MillenniumIconButtonStyle
import org.spotube.millennium.composables.MillenniumText
import org.spotube.millennium.composables.MillenniumTextField
import org.spotube.spotube.core.ui.TvTextFieldGate
import org.spotube.spotube.core.ui.component.ApplicationMainBar
import org.spotube.spotube.core.ui.component.TvDialogFocus
import org.spotube.spotube.core.ui.theming.AppTheme
import org.spotube.spotube.core.ui.tvFocusRing
import org.spotube.spotube.resources.iconsax.Iconsax
import org.spotube.spotube.resources.iconsax.IconsaxAdd
import org.spotube.spotube.resources.iconsax.IconsaxEdit
import org.spotube.spotube.resources.iconsax.IconsaxTrash
import org.spotube.spotube.resources.iconsax.User
import spotube.composeapp.generated.resources.Res
import spotube.composeapp.generated.resources.common_cancel
import spotube.composeapp.generated.resources.common_save
import spotube.composeapp.generated.resources.profiles_accounts
import spotube.composeapp.generated.resources.profiles_active
import spotube.composeapp.generated.resources.profiles_add
import spotube.composeapp.generated.resources.profiles_delete
import spotube.composeapp.generated.resources.profiles_delete_confirm
import spotube.composeapp.generated.resources.profiles_description
import spotube.composeapp.generated.resources.profiles_main_name
import spotube.composeapp.generated.resources.profiles_name_label
import spotube.composeapp.generated.resources.profiles_rename
import spotube.composeapp.generated.resources.profiles_restart_required
import spotube.composeapp.generated.resources.profiles_switch_hint
import spotube.composeapp.generated.resources.profiles_title

/** Local profiles: switch, add, rename and remove them, and open the active one's accounts. */
@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ApplicationMainBar(
            backButton = true,
            title = { MillenniumText(stringResource(Res.string.profiles_title)) }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MillenniumText(
                    stringResource(Res.string.profiles_description),
                    color = AppTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(state.profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    onSwitch = { viewModel.switchTo(profile.id) },
                    onRename = { viewModel.startRename(profile.id) },
                    onDelete = { viewModel.requestDelete(profile.id) }
                )
            }
            item {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MillenniumButton(onClick = viewModel::startAdd) {
                        MillenniumIcon(imageVector = Iconsax.IconsaxAdd, contentDescription = null)
                        MillenniumText(stringResource(Res.string.profiles_add))
                    }
                    MillenniumButton(
                        onClick = viewModel::openAccounts,
                        style = MillenniumButtonStyle.Secondary
                    ) {
                        MillenniumText(stringResource(Res.string.profiles_accounts))
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        MillenniumDialog(
            visible = true,
            onDismissRequest = viewModel::closeEditor
        ) {
            TvDialogFocus {
                MillenniumDialogHeader {
                    MillenniumDialogTitle(
                        stringResource(
                            if (editor.mode == ProfileEditorMode.Add) Res.string.profiles_add
                            else Res.string.profiles_rename
                        )
                    )
                    MillenniumDialogClose()
                }
                MillenniumDialogBody {
                    TvTextFieldGate(modifier = Modifier.fillMaxWidth()) { fieldModifier ->
                        MillenniumTextField(
                            state = viewModel.nameField,
                            label = stringResource(Res.string.profiles_name_label),
                            modifier = fieldModifier.fillMaxWidth(),
                            placeholder = stringResource(Res.string.profiles_name_label)
                        )
                    }
                }
                MillenniumDialogFooter {
                    MillenniumButton(
                        onClick = viewModel::closeEditor,
                        style = MillenniumButtonStyle.Secondary
                    ) {
                        MillenniumText(stringResource(Res.string.common_cancel))
                    }
                    MillenniumButton(onClick = viewModel::saveEditor, enabled = editor.canSave) {
                        MillenniumText(stringResource(Res.string.common_save))
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { profile ->
        MillenniumDialog(
            visible = true,
            onDismissRequest = viewModel::cancelDelete
        ) {
            TvDialogFocus {
                MillenniumDialogHeader {
                    MillenniumDialogTitle(stringResource(Res.string.profiles_delete))
                    MillenniumDialogClose()
                }
                MillenniumDialogBody {
                    MillenniumText(
                        stringResource(
                            Res.string.profiles_delete_confirm,
                            profile.name ?: stringResource(Res.string.profiles_main_name)
                        )
                    )
                }
                MillenniumDialogFooter {
                    MillenniumButton(
                        onClick = viewModel::cancelDelete,
                        style = MillenniumButtonStyle.Secondary
                    ) {
                        MillenniumText(stringResource(Res.string.common_cancel))
                    }
                    MillenniumButton(onClick = viewModel::confirmDelete) {
                        MillenniumText(stringResource(Res.string.profiles_delete))
                    }
                }
            }
        }
    }

    if (state.restartRequired) {
        MillenniumDialog(
            visible = true,
            onDismissRequest = viewModel::dismissRestartNotice
        ) {
            TvDialogFocus {
                MillenniumDialogHeader {
                    MillenniumDialogTitle(stringResource(Res.string.profiles_title))
                    MillenniumDialogClose()
                }
                MillenniumDialogBody {
                    MillenniumText(stringResource(Res.string.profiles_restart_required))
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileItemUi,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val name = profile.name ?: stringResource(Res.string.profiles_main_name)
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The switch target and its buttons are siblings, so the D-pad reaches each of them.
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(AppTheme.shapes.medium)
                .background(
                    if (profile.isActive) AppTheme.colors.primaryContainer
                    else AppTheme.colors.surfaceVariant
                )
                .tvFocusRing(AppTheme.shapes.medium)
                .clickable(onClick = onSwitch)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.primary),
                contentAlignment = Alignment.Center
            ) {
                val initial = profile.initial ?: name.firstOrNull()?.uppercase()
                if (initial != null) {
                    MillenniumText(initial, color = AppTheme.colors.onPrimary)
                } else {
                    MillenniumIcon(imageVector = Iconsax.User, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                MillenniumText(name)
                MillenniumText(
                    stringResource(
                        if (profile.isActive) Res.string.profiles_active else Res.string.profiles_switch_hint
                    ),
                    color = AppTheme.colors.onSurfaceVariant
                )
            }
        }
        run {
            MillenniumIconButton(onClick = onRename, style = MillenniumIconButtonStyle.Quiet) {
                MillenniumIcon(
                    imageVector = Iconsax.IconsaxEdit,
                    contentDescription = stringResource(Res.string.profiles_rename)
                )
            }
        }
        if (profile.canDelete) {
            MillenniumIconButton(onClick = onDelete, style = MillenniumIconButtonStyle.Quiet) {
                MillenniumIcon(
                    imageVector = Iconsax.IconsaxTrash,
                    contentDescription = stringResource(Res.string.profiles_delete)
                )
            }
        }
    }
}
