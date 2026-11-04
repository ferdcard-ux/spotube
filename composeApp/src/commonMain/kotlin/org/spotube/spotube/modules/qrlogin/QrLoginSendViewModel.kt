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

import androidx.lifecycle.ViewModel
import org.koin.core.component.KoinComponent
import androidx.lifecycle.viewModelScope
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.spotube.spotube.core.db.Database
import org.spotube.spotube.core.di.injectLogger
import org.spotube.spotube.core.network.HttpClientFactory
import org.spotube.spotube.core.qrlogin.QrLoginInbox
import org.spotube.spotube.core.qrlogin.QrLoginPayload
import org.spotube.spotube.core.qrlogin.QrLoginPlatform
import org.spotube.spotube.modules.plugin.PluginAbility
import org.spotube.spotube.modules.plugin.PluginManager
import org.spotube.spotube.modules.plugin.PluginManagerStates
import kotlin.time.Duration.Companion.seconds

enum class QrLoginSendStatus { Confirm, Sending, Sent, NotSignedIn, Failed, Unsupported }

data class QrLoginSendUiState(
    /** Null: nothing to answer, the dialog is hidden. */
    val status: QrLoginSendStatus? = null,
    /** The TV's address, shown so the user knows where the session goes. */
    val target: String? = null,
    val pluginName: String? = null
)

/**
 * Phone side of the QR login: when a `spotube://qr-login` link arrives, asks the user, then sends
 * the selected metadata plugin's stored session to the TV, encrypted with the QR's key.
 */
class QrLoginSendViewModel(
    private val inbox: QrLoginInbox,
    private val database: Database,
    private val pluginManager: PluginManager,
    private val httpClientFactory: HttpClientFactory
) : ViewModel(), KoinComponent {
    private val logger by injectLogger<QrLoginSendViewModel>()
    private val status = MutableStateFlow<QrLoginSendStatus?>(null)

    val uiState: StateFlow<QrLoginSendUiState> = combine(
        inbox.pending,
        status,
        pluginManager.state
    ) { link, current, managerState ->
        val plugin = (managerState as? PluginManagerStates.Data)?.selectedPlugins?.get(PluginAbility.METADATA)
        QrLoginSendUiState(
            status = when {
                link == null -> null
                !QrLoginPlatform.isSupported -> QrLoginSendStatus.Unsupported
                else -> current ?: QrLoginSendStatus.Confirm
            },
            target = link?.host,
            pluginName = plugin?.name
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QrLoginSendUiState())

    fun confirm() {
        val link = inbox.pending.value ?: return
        if (status.value == QrLoginSendStatus.Sending) return
        status.value = QrLoginSendStatus.Sending
        viewModelScope.launch {
            status.value = runCatching { send(link) }.getOrElse {
                logger.e(it) { "QR login: sending the session failed" }
                QrLoginSendStatus.Failed
            }
        }
    }

    private suspend fun send(link: org.spotube.spotube.core.qrlogin.QrLoginLink): QrLoginSendStatus {
        val key = link.keyBytes() ?: return QrLoginSendStatus.Failed
        val plugin = (pluginManager.state.value as? PluginManagerStates.Data)
            ?.selectedPlugins?.get(PluginAbility.METADATA) ?: return QrLoginSendStatus.NotSignedIn

        val prefix = "${plugin.id}:"
        val entries = database.pluginsDataStore.data.first().asMap()
            .mapNotNull { (prefKey, value) ->
                val name = prefKey.name
                if (name.startsWith(prefix) && value is String) name.removePrefix(prefix) to value else null
            }
            .toMap()
        if (entries.isEmpty()) return QrLoginSendStatus.NotSignedIn

        val payload = QrLoginPayload(
            pluginId = plugin.id,
            pluginName = plugin.name,
            entries = entries,
            sender = QrLoginPlatform.deviceName()
        )
        val body = QrLoginPlatform.encrypt(
            key,
            link.sessionId.encodeToByteArray(),
            Json.encodeToString(QrLoginPayload.serializer(), payload).encodeToByteArray()
        )
        val response: HttpResponse = withTimeout(15.seconds) {
            httpClientFactory.default.post(link.postUrl) {
                contentType(ContentType.Application.OctetStream)
                setBody(body)
            }
        }
        return if (response.status.isSuccess()) QrLoginSendStatus.Sent else QrLoginSendStatus.Failed
    }

    fun dismiss() {
        status.value = null
        inbox.clear()
    }
}
