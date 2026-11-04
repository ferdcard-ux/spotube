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

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import org.koin.core.component.KoinComponent
import androidx.lifecycle.viewModelScope
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.spotube.spotube.core.db.Database
import org.spotube.spotube.core.di.injectLogger
import org.spotube.spotube.core.profile.restartApplication
import org.spotube.spotube.core.qrlogin.QR_LOGIN_MAX_PAYLOAD_BYTES
import org.spotube.spotube.core.qrlogin.QrLoginLink
import org.spotube.spotube.core.qrlogin.QrLoginPayload
import org.spotube.spotube.core.qrlogin.QrLoginPlatform
import org.spotube.spotube.core.qrlogin.toBase64Url
import org.spotube.spotube.core.server.startServerWithPortFallback
import org.spotube.spotube.modules.plugin.PluginManager
import org.spotube.spotube.modules.plugin.PluginManagerStates
import spotube.composeapp.generated.resources.Res
import spotube.composeapp.generated.resources.qr_login_page_body
import spotube.composeapp.generated.resources.qr_login_page_open
import spotube.composeapp.generated.resources.qr_login_page_title

enum class QrLoginReceiveStatus {
    /** Starting the listener. */
    Starting,

    /** QR on screen, waiting for the phone. */
    Waiting,

    /** Session stored; the app is restarting. */
    Received,

    /** Session stored but this platform cannot restart itself. */
    RestartRequired,

    /** Not on a local network, or the platform has no QR login. */
    Unavailable,

    /** The phone sent a session for a plugin this device does not have. */
    PluginMissing
}

data class QrLoginReceiveUiState(
    val status: QrLoginReceiveStatus = QrLoginReceiveStatus.Starting,
    val qrModules: List<BooleanArray>? = null,
    /** Address shown under the QR, without the secret key. */
    val address: String? = null,
    val senderName: String? = null
)

/**
 * TV side of the QR login: while the screen is open, listens on the LAN for one encrypted
 * session from a phone (see `core/qrlogin/QrLogin.kt`), stores it and restarts the app.
 */
class QrLoginReceiveViewModel(
    private val database: Database,
    private val pluginManager: PluginManager
) : ViewModel(), KoinComponent {
    private val logger by injectLogger<QrLoginReceiveViewModel>()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(QrLoginReceiveUiState())
    val uiState: StateFlow<QrLoginReceiveUiState> = _uiState.asStateFlow()

    private var server: EmbeddedServer<*, *>? = null
    private val sessionId = QrLoginPlatform.randomBytes(16).toBase64Url()
    private val key = QrLoginPlatform.randomBytes(32)

    /** Plain flag: one session per QR. Guarded by the server's single POST route. */
    @kotlin.concurrent.Volatile
    private var consumed = false

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val ip = if (QrLoginPlatform.isSupported) QrLoginPlatform.lanIpv4Address() else null
        if (ip == null) {
            _uiState.value = QrLoginReceiveUiState(status = QrLoginReceiveStatus.Unavailable)
            return
        }
        val pageTitle = getString(Res.string.qr_login_page_title)
        val pageBody = getString(Res.string.qr_login_page_body)
        val pageOpen = getString(Res.string.qr_login_page_open)

        val bound = runCatching {
            withContext(Dispatchers.IO) {
                startServerWithPortFallback(host = "0.0.0.0", preferredPort = 0) {
                    routing {
                        get("/qr/{session}") {
                            if (call.parameters["session"] != sessionId) {
                                call.respondText("", status = HttpStatusCode.NotFound)
                                return@get
                            }
                            call.respondText(
                                landingPage(pageTitle, pageBody, pageOpen),
                                ContentType.Text.Html.withParameter("charset", "utf-8")
                            )
                        }
                        post("/qr/{session}") {
                            val status = if (call.parameters["session"] != sessionId || consumed) {
                                HttpStatusCode.NotFound
                            } else {
                                val body = call.receiveChannel()
                                    .readRemaining(QR_LOGIN_MAX_PAYLOAD_BYTES.toLong() + 1)
                                    .readByteArray()
                                if (body.size > QR_LOGIN_MAX_PAYLOAD_BYTES) {
                                    HttpStatusCode.PayloadTooLarge
                                } else {
                                    accept(body)
                                }
                            }
                            call.respondText(status.description, status = status)
                        }
                    }
                }
            }
        }.getOrElse {
            logger.e(it) { "QR login listener failed to start" }
            _uiState.value = QrLoginReceiveUiState(status = QrLoginReceiveStatus.Unavailable)
            return
        }
        server = bound.engine

        val link = QrLoginLink(ip, bound.port, sessionId, key.toBase64Url())
        // The key goes in the fragment: browsers never send it to the server.
        val qrText = "http://$ip:${bound.port}/qr/$sessionId#k=${link.key}"
        _uiState.value = QrLoginReceiveUiState(
            status = QrLoginReceiveStatus.Waiting,
            qrModules = QrLoginPlatform.qrMatrix(qrText),
            address = "$ip:${bound.port}"
        )
    }

    /** Validates, stores and applies a session. Runs on the server's thread. */
    private fun accept(body: ByteArray): HttpStatusCode {
        val plain = QrLoginPlatform.decrypt(key, sessionId.encodeToByteArray(), body)
            ?: return HttpStatusCode.Forbidden
        val payload = runCatching { json.decodeFromString<QrLoginPayload>(plain.decodeToString()) }
            .getOrNull() ?: return HttpStatusCode.BadRequest
        if (payload.entries.isEmpty()) return HttpStatusCode.BadRequest

        val installed = (pluginManager.state.value as? PluginManagerStates.Data)
            ?.plugins?.any { it.id == payload.pluginId } == true
        if (!installed) {
            _uiState.update { it.copy(status = QrLoginReceiveStatus.PluginMissing, senderName = payload.sender) }
            return HttpStatusCode.Conflict
        }

        consumed = true
        runBlocking {
            database.pluginsDataStore.edit { prefs ->
                payload.entries.forEach { (entryKey, value) ->
                    prefs[stringPreferencesKey("${payload.pluginId}:$entryKey")] = value
                }
            }
            // Make sure the write reached disk before the process restarts.
            database.pluginsDataStore.data.first()
        }
        logger.i { "QR login: stored ${payload.entries.size} entries for plugin ${payload.pluginId}" }
        _uiState.update { it.copy(status = QrLoginReceiveStatus.Received, senderName = payload.sender) }

        viewModelScope.launch {
            // Let the phone get its answer and the user read the confirmation.
            delay(2_000)
            stopServer()
            if (!restartApplication()) {
                _uiState.update { it.copy(status = QrLoginReceiveStatus.RestartRequired) }
            }
        }
        return HttpStatusCode.OK
    }

    private fun stopServer() {
        val running = server ?: return
        server = null
        runCatching { running.stop(gracePeriodMillis = 0, timeoutMillis = 500) }
    }

    override fun onCleared() {
        stopServer()
        super.onCleared()
    }

    private fun landingPage(title: String, body: String, open: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        // Hands the link (with the key from the fragment) to the Spotube app on the phone.
        return """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>${esc(title)}</title>
            <style>body{font-family:sans-serif;background:#111;color:#eee;margin:0;padding:32px;text-align:center}
            a{display:inline-block;margin-top:24px;padding:14px 28px;border-radius:24px;background:#84cc16;color:#111;text-decoration:none;font-weight:bold}</style>
            </head><body><h2>${esc(title)}</h2><p>${esc(body)}</p><a id="open" href="#">${esc(open)}</a>
            <script>
            (function(){
              var k=(location.hash.match(/k=([A-Za-z0-9_-]+)/)||[])[1]; if(!k) return;
              var q='h='+location.hostname+'&p='+location.port+'&s=$sessionId&k='+k;
              var app='spotube://qr-login?'+q;
              var intent='intent://qr-login?'+q+'#Intent;scheme=spotube;end';
              var target=/Android/i.test(navigator.userAgent)?intent:app;
              document.getElementById('open').href=target;
              location.href=target;
            })();
            </script></body></html>
        """.trimIndent()
    }
}
