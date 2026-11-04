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

package org.spotube.spotube.modules.qrlogin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.millennium.composables.MillenniumButtonStyle
import org.spotube.millennium.composables.MillenniumText
import org.spotube.spotube.core.ui.component.ApplicationMainBar
import org.spotube.spotube.core.ui.theming.AppTheme
import org.spotube.spotube.modules.plugin.MetadataAuthViewModel
import spotube.composeapp.generated.resources.Res
import spotube.composeapp.generated.resources.qr_login_note
import spotube.composeapp.generated.resources.qr_login_plugin_missing
import spotube.composeapp.generated.resources.qr_login_received
import spotube.composeapp.generated.resources.qr_login_restart_required
import spotube.composeapp.generated.resources.qr_login_starting
import spotube.composeapp.generated.resources.qr_login_step_1
import spotube.composeapp.generated.resources.qr_login_step_2
import spotube.composeapp.generated.resources.qr_login_step_3
import spotube.composeapp.generated.resources.qr_login_title
import spotube.composeapp.generated.resources.qr_login_unavailable
import spotube.composeapp.generated.resources.qr_login_use_web
import spotube.composeapp.generated.resources.qr_login_waiting

/**
 * Sign-in screen shown on TV: a QR the phone scans to send its session, plus the usual web login
 * as a fallback.
 */
@Composable
fun QrLoginScreen(
    viewModel: QrLoginReceiveViewModel = koinViewModel(),
    authViewModel: MetadataAuthViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ApplicationMainBar(
            backButton = true,
            title = { MillenniumText(stringResource(Res.string.qr_login_title)) }
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val modules = state.qrModules
                if (modules != null && state.status == QrLoginReceiveStatus.Waiting) {
                    QrCode(modules, Modifier.fillMaxSize())
                }
            }

            Column(
                modifier = Modifier.weight(1f).widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MillenniumText(stringResource(Res.string.qr_login_step_1))
                MillenniumText(stringResource(Res.string.qr_login_step_2))
                MillenniumText(stringResource(Res.string.qr_login_step_3))
                MillenniumText(
                    stringResource(Res.string.qr_login_note),
                    color = AppTheme.colors.onSurfaceVariant
                )
                MillenniumText(
                    when (state.status) {
                        QrLoginReceiveStatus.Starting -> stringResource(Res.string.qr_login_starting)
                        QrLoginReceiveStatus.Waiting ->
                            stringResource(Res.string.qr_login_waiting, state.address.orEmpty())
                        QrLoginReceiveStatus.Received ->
                            stringResource(Res.string.qr_login_received, state.senderName.orEmpty())
                        QrLoginReceiveStatus.RestartRequired ->
                            stringResource(Res.string.qr_login_restart_required, state.senderName.orEmpty())
                        QrLoginReceiveStatus.Unavailable -> stringResource(Res.string.qr_login_unavailable)
                        QrLoginReceiveStatus.PluginMissing ->
                            stringResource(Res.string.qr_login_plugin_missing, state.senderName.orEmpty())
                    },
                    color = AppTheme.colors.primary
                )
                MillenniumButton(
                    onClick = authViewModel::login,
                    style = MillenniumButtonStyle.Secondary
                ) {
                    MillenniumText(stringResource(Res.string.qr_login_use_web))
                }
            }
        }
    }
}

@Composable
private fun QrCode(modules: List<BooleanArray>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val count = modules.size
        if (count == 0) return@Canvas
        val cell = size.minDimension / count
        modules.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                if (dark) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        // Slight overlap avoids hairline gaps between modules.
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}
