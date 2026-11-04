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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.millennium.composables.MillenniumButtonStyle
import org.spotube.millennium.composables.MillenniumDialog
import org.spotube.millennium.composables.MillenniumDialogBody
import org.spotube.millennium.composables.MillenniumDialogClose
import org.spotube.millennium.composables.MillenniumDialogFooter
import org.spotube.millennium.composables.MillenniumDialogHeader
import org.spotube.millennium.composables.MillenniumDialogTitle
import org.spotube.millennium.composables.MillenniumText
import org.spotube.spotube.core.ui.component.TvDialogFocus
import spotube.composeapp.generated.resources.Res
import spotube.composeapp.generated.resources.common_cancel
import spotube.composeapp.generated.resources.common_close
import spotube.composeapp.generated.resources.qr_login_send_action
import spotube.composeapp.generated.resources.qr_login_send_confirm
import spotube.composeapp.generated.resources.qr_login_send_failed
import spotube.composeapp.generated.resources.qr_login_send_not_signed_in
import spotube.composeapp.generated.resources.qr_login_send_sending
import spotube.composeapp.generated.resources.qr_login_send_sent
import spotube.composeapp.generated.resources.qr_login_send_title
import spotube.composeapp.generated.resources.qr_login_send_unsupported

/**
 * Shows the "send your session to the TV?" dialog when a QR login link was opened on this device.
 * Composed once at the app root.
 */
@Composable
fun QrLoginSendHost(viewModel: QrLoginSendViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val status = state.status ?: return

    MillenniumDialog(visible = true, onDismissRequest = viewModel::dismiss) {
        TvDialogFocus {
            MillenniumDialogHeader {
                MillenniumDialogTitle(stringResource(Res.string.qr_login_send_title))
                MillenniumDialogClose()
            }
            MillenniumDialogBody {
                MillenniumText(
                    when (status) {
                        QrLoginSendStatus.Confirm -> stringResource(
                            Res.string.qr_login_send_confirm,
                            state.pluginName.orEmpty(),
                            state.target.orEmpty()
                        )
                        QrLoginSendStatus.Sending -> stringResource(Res.string.qr_login_send_sending)
                        QrLoginSendStatus.Sent -> stringResource(Res.string.qr_login_send_sent)
                        QrLoginSendStatus.NotSignedIn -> stringResource(Res.string.qr_login_send_not_signed_in)
                        QrLoginSendStatus.Failed -> stringResource(Res.string.qr_login_send_failed)
                        QrLoginSendStatus.Unsupported -> stringResource(Res.string.qr_login_send_unsupported)
                    }
                )
            }
            MillenniumDialogFooter {
                if (status == QrLoginSendStatus.Confirm || status == QrLoginSendStatus.Sending) {
                    MillenniumButton(
                        onClick = viewModel::dismiss,
                        style = MillenniumButtonStyle.Secondary
                    ) {
                        MillenniumText(stringResource(Res.string.common_cancel))
                    }
                    MillenniumButton(
                        onClick = viewModel::confirm,
                        enabled = status == QrLoginSendStatus.Confirm
                    ) {
                        MillenniumText(stringResource(Res.string.qr_login_send_action))
                    }
                } else {
                    MillenniumButton(onClick = viewModel::dismiss) {
                        MillenniumText(stringResource(Res.string.common_close))
                    }
                }
            }
        }
    }
}
