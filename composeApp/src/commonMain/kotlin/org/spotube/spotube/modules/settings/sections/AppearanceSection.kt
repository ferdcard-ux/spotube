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

package org.spotube.spotube.modules.settings.sections

import org.spotube.spotube.resources.iconsax.IconsaxMirrorScreenRegular
import org.spotube.spotube.PlatformType
import org.spotube.spotube.getPlatform
import org.spotube.millennium.composables.MillenniumDialogHeader
import org.spotube.millennium.composables.MillenniumDialogTitle
import org.spotube.millennium.composables.MillenniumDialogClose
import org.spotube.millennium.composables.MillenniumDialogBody
import org.spotube.millennium.composables.MillenniumDialogFooter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.spotube.spotube.LocalIsTv
import org.spotube.spotube.core.ui.component.TvDialogFocus
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.spotube.spotube.core.ui.theming.AppTheme
import org.spotube.spotube.core.ui.theming.isLightAppearance
import org.spotube.spotube.modules.settings.SettingsViewModel
import org.spotube.spotube.modules.settings.Theme
import org.spotube.spotube.modules.settings.TV_CONTENT_SCALE_AUTO
import org.spotube.spotube.modules.settings.TV_CONTENT_SCALE_OPTIONS
import org.spotube.spotube.isTelevision
import org.spotube.spotube.resources.iconsax.FluentMaximize
import org.spotube.spotube.modules.settings.UserSettings
import org.spotube.spotube.modules.settings.components.SelectionSettingCard
import org.spotube.spotube.modules.settings.components.SwitchSettingCard
import org.spotube.spotube.modules.settings.components.SettingCardItem
import org.spotube.spotube.resources.iconsax.Iconsax
import org.spotube.spotube.resources.iconsax.IconsaxColorSwatch
import org.spotube.spotube.resources.iconsax.IconsaxMagic
import org.jetbrains.compose.resources.stringResource
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.millennium.composables.MillenniumButtonStyle
import org.spotube.millennium.composables.MillenniumDialog
import org.spotube.millennium.composables.MillenniumSeparator
import org.spotube.millennium.composables.MillenniumText
import org.spotube.millennium.theme.MillenniumColorSchemes
import org.spotube.millennium.theme.MillenniumPalette
import org.spotube.millennium.theme.MillenniumTheme
import org.spotube.millennium.theme.MillenniumThemeConfig
import org.spotube.millennium.theme.resolve
import spotube.composeapp.generated.resources.*

internal fun LazyListScope.appearanceSection(
    settings: UserSettings,
    settingsViewModel: SettingsViewModel,
    includeHeader: Boolean = true) {
    if (includeHeader) {
        settingsSectionHeader(Res.string.settings_section_appearance)
    }
    settingsSectionCard(
        items = listOf<@Composable () -> Unit>(
            {
                SelectionSettingCard(
                    title = stringResource(Res.string.settings_theme_title),
                    subtitle = stringResource(
                        Res.string.settings_theme_subtitle_current,
                        settings.theme.displayLabel()
                    ),
                    icon = {
                        SettingsItemIcon(Iconsax.IconsaxColorSwatch, stringResource(Res.string.settings_theme_title))
                    },
                    selectedOption = settings.theme,
                    options = Theme.entries,
                    optionLabel = { it.displayLabel() },
                    onOptionSelected = { theme ->
                        settingsViewModel.updateSettings {
                            copy(theme = theme)
                        }
                    }
                )
            },
            {
                AppearanceSettingCard(
                    selectedPalette = settings.palette,
                    icon = {
                        SettingsItemIcon(
                            Iconsax.IconsaxMagic,
                            stringResource(Res.string.settings_appearance_title)
                        )
                    },
                    onSaved = { palette ->
                        settingsViewModel.updateSettings {
                            copy(palette = palette)
                        }
                    })
            }) + if (getPlatform().type == PlatformType.Android) {
            listOf<@Composable () -> Unit>({
                SwitchSettingCard(
                    title = stringResource(Res.string.settings_keep_screen_on_title),
                    subtitle = stringResource(Res.string.settings_keep_screen_on_subtitle),
                    icon = {
                        SettingsItemIcon(
                            Iconsax.IconsaxMirrorScreenRegular,
                            stringResource(Res.string.settings_keep_screen_on_title)
                        )
                    },
                    checked = settings.keepScreenOn ?: isTelevision(),
                    onCheckedChange = { enabled ->
                        settingsViewModel.updateSettings { copy(keepScreenOn = enabled) }
                    }
                )
            })
        } else {
            emptyList()
        } + if (isTelevision()) {
            listOf<@Composable () -> Unit>({
                SelectionSettingCard(
                    title = stringResource(Res.string.settings_tv_scale_title),
                    subtitle = stringResource(
                        Res.string.settings_tv_scale_subtitle_current,
                        tvContentScaleLabel(settings.tvContentScale)
                    ),
                    icon = {
                        SettingsItemIcon(
                            Iconsax.FluentMaximize,
                            stringResource(Res.string.settings_tv_scale_title)
                        )
                    },
                    selectedOption = settings.tvContentScale,
                    options = TV_CONTENT_SCALE_OPTIONS,
                    optionLabel = { tvContentScaleLabel(it) },
                    onOptionSelected = { scale ->
                        settingsViewModel.updateSettings {
                            copy(tvContentScale = scale)
                        }
                    }
                )
            })
        } else {
            emptyList()
        }
    )
}

@Composable
private fun tvContentScaleLabel(scale: Int): String =
    if (scale == TV_CONTENT_SCALE_AUTO) {
        stringResource(Res.string.settings_tv_scale_auto)
    } else {
        stringResource(Res.string.settings_tv_scale_percent, scale)
    }

@Composable
private fun Theme.displayLabel(): String {
    return when (this) {
        Theme.LIGHT -> stringResource(Res.string.settings_theme_light)
        Theme.DARK -> stringResource(Res.string.settings_theme_dark)
        Theme.SYSTEM -> stringResource(Res.string.settings_theme_system)
    }
}

@Composable
private fun MillenniumPalette.displayLabel(): String = when (this) {
    MillenniumPalette.Blue -> stringResource(Res.string.settings_palette_blue)
    MillenniumPalette.Orange -> stringResource(Res.string.settings_palette_orange)
    MillenniumPalette.Lime -> stringResource(Res.string.settings_palette_lime)
}

@Composable
private fun AppearanceSettingCard(
    selectedPalette: MillenniumPalette,
    icon: (@Composable () -> Unit)? = null,
    onSaved: (MillenniumPalette) -> Unit) {
    var isDialogOpen by remember { mutableStateOf(false) }

    SettingCardItem(
        title = stringResource(Res.string.settings_appearance_title),
        subtitle = stringResource(
            Res.string.settings_appearance_subtitle_palette,
            selectedPalette.displayLabel()),
        icon = icon,
        trailingContent = {
            PaletteTrailingSwatch(selectedPalette)
        },
        onClick = { isDialogOpen = true })

    if (isDialogOpen) {
        var draftPalette by remember(selectedPalette, isDialogOpen) {
            mutableStateOf(selectedPalette)
        }

        // TV: start D-pad focus on the current palette, otherwise it stays on the screen behind.
        val swatchRequester = remember { FocusRequester() }
        if (LocalIsTv.current) {
            LaunchedEffect(Unit) {
                // The dialog focuses its close button as it opens; take focus after that.
                repeat(20) { withFrameNanos { } }
                runCatching { swatchRequester.requestFocus(FocusDirection.Enter) }
            }
        }
        MillenniumDialog(visible = true, onDismissRequest = { isDialogOpen = false }) {
            // TV: this dialog draws in the screen's own window, so keep D-pad focus inside it.
            TvDialogFocus {
                MillenniumDialogHeader {
                    MillenniumDialogTitle(stringResource(Res.string.settings_palette_dialog_title))
                    MillenniumDialogClose()
                }
                MillenniumDialogBody {
                MillenniumText(
                    stringResource(Res.string.settings_palette_dialog_description),
                    color = AppTheme.colors.onSurfaceVariant)
                MillenniumText(
                    stringResource(Res.string.settings_palette_label),
                    color = AppTheme.colors.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MillenniumPalette.entries.forEach { palette ->
                        PaletteSwatch(
                            palette = palette,
                            label = palette.displayLabel(),
                            selected = palette == draftPalette,
                            onClick = { draftPalette = palette },
                            focusRequester = if (palette == selectedPalette) swatchRequester else null)
                    }
                }

                PalettePreview(draftPalette)
                }
                MillenniumDialogFooter {
                    MillenniumButton(
                        onClick = { isDialogOpen = false },
                        style = MillenniumButtonStyle.Secondary) {
                        MillenniumText(stringResource(Res.string.common_cancel))
                    }
                    MillenniumButton(
                        onClick = {
                            onSaved(draftPalette)
                            isDialogOpen = false
                        }) {
                        MillenniumText(stringResource(Res.string.common_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: MillenniumPalette,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null) {
    val dark = !isLightAppearance()
    val scheme = remember(palette, dark) { MillenniumColorSchemes().resolve(palette, dark = dark) }
    val isTv = LocalIsTv.current
    // TV: a swatch has the accent colour itself, so an accent ring would vanish on it. Draw a
    // contrasting ring with a gap around the focused swatch instead.
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .then(
                if (isTv) {
                    Modifier
                        .border(
                            width = if (focused) 3.dp else 0.dp,
                            color = if (focused) AppTheme.colors.onSurface else Color.Transparent,
                            shape = CircleShape)
                        .padding(6.dp)
                } else {
                    Modifier
                })
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .size(40.dp)
            .clip(CircleShape)
            .background(scheme.accent)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) AppTheme.colors.onSurface else AppTheme.colors.outlineVariant,
                shape = CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label })
}

@Composable
private fun PaletteTrailingSwatch(palette: MillenniumPalette) {
    val dark = !isLightAppearance()
    val scheme = remember(palette, dark) { MillenniumColorSchemes().resolve(palette, dark = dark) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 46.dp, height = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.accent.copy(alpha = 0.35f)))
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(scheme.accent))
    }
}

@Composable
private fun PalettePreview(palette: MillenniumPalette) {
    val dark = !isLightAppearance()
    MillenniumTheme(dark = dark, config = MillenniumThemeConfig(palette = palette)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MillenniumTheme.colors.surface)
                .border(1.dp, MillenniumTheme.colors.border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MillenniumText(
                stringResource(Res.string.settings_preview_label),
                color = MillenniumTheme.colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MillenniumButton(onClick = {}) {
                    MillenniumText(stringResource(Res.string.settings_preview_button))
                }
                MillenniumButton(onClick = {}, style = MillenniumButtonStyle.Secondary) {
                    MillenniumText(stringResource(Res.string.settings_preview_secondary))
                }
            }
        }
    }
}
