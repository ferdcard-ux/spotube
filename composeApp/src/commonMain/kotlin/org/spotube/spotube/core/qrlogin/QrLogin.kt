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

package org.spotube.spotube.core.qrlogin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.spotube.spotube.core.deeplink.ExternalUriHandler
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * QR login: a signed-in phone hands its plugin session (account) to a TV.
 *
 * 1. The TV starts a short-lived HTTP listener on the LAN and shows a QR with
 *    `http://<tv-ip>:<port>/qr/<session>#k=<key>`. The key is random, travels only inside the QR
 *    (the URL fragment is never sent to the server) and is used once.
 * 2. Any camera app opens that URL on the phone; the TV's page hands it to the Spotube app as
 *    `spotube://qr-login?...` (see [QrLoginLink]).
 * 3. After the user confirms on the phone, the app encrypts the metadata plugin's stored session
 *    with AES-256-GCM (key from the QR, session id as associated data) and POSTs it to the TV.
 * 4. The TV decrypts it, stores the entries for the same plugin and restarts, so the plugin loads
 *    the session. No password ever crosses the network.
 */

const val QR_LOGIN_SCHEME = "spotube"
const val QR_LOGIN_HOST = "qr-login"

/** Largest accepted payload: plugin sessions are a few KB. */
const val QR_LOGIN_MAX_PAYLOAD_BYTES = 64 * 1024

/** Where the phone sends the session and with which key. */
data class QrLoginLink(
    val host: String,
    val port: Int,
    val sessionId: String,
    /** Base64url (no padding) AES-256 key. */
    val key: String
) {
    fun toAppUri(): String = "$QR_LOGIN_SCHEME://$QR_LOGIN_HOST?h=$host&p=$port&s=$sessionId&k=$key"

    val postUrl: String get() = "http://$host:$port/qr/$sessionId"

    @OptIn(ExperimentalEncodingApi::class)
    fun keyBytes(): ByteArray? = runCatching {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(key)
    }.getOrNull()?.takeIf { it.size == 32 }

    companion object {
        private val SAFE_HOST = Regex("^[0-9A-Za-z.:\\-\\[\\]]{1,64}$")
        private val SAFE_TOKEN = Regex("^[0-9A-Za-z_\\-]{16,64}$")

        /** Parses `spotube://qr-login?h=…&p=…&s=…&k=…`; null for anything else or unsafe values. */
        fun parse(uri: String): QrLoginLink? {
            val prefix = "$QR_LOGIN_SCHEME://$QR_LOGIN_HOST"
            if (!uri.startsWith(prefix, ignoreCase = true)) return null
            val query = uri.substringAfter('?', "").substringBefore('#')
            val params = query.split('&').mapNotNull { part ->
                val name = part.substringBefore('=', "")
                if (name.isEmpty()) null else name to part.substringAfter('=')
            }.toMap()
            val host = params["h"]?.takeIf { SAFE_HOST.matches(it) } ?: return null
            val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val session = params["s"]?.takeIf { SAFE_TOKEN.matches(it) } ?: return null
            val key = params["k"]?.takeIf { SAFE_TOKEN.matches(it) } ?: return null
            return QrLoginLink(host, port, session, key).takeIf { it.keyBytes() != null }
        }
    }
}

/** What the phone sends: every stored entry of one plugin, keys without the plugin prefix. */
@Serializable
data class QrLoginPayload(
    val version: Int = 1,
    val pluginId: String,
    val pluginName: String? = null,
    val entries: Map<String, String>,
    val sender: String
)

/** Platform pieces (crypto, network address, QR encoding). Unsupported platforms return false/null. */
expect object QrLoginPlatform {
    val isSupported: Boolean
    fun randomBytes(size: Int): ByteArray

    /** AES-256-GCM; returns nonce (12 bytes) followed by ciphertext and tag. */
    fun encrypt(key: ByteArray, associatedData: ByteArray, plain: ByteArray): ByteArray

    /** Inverse of [encrypt]; null when the key, data or associated data do not match. */
    fun decrypt(key: ByteArray, associatedData: ByteArray, data: ByteArray): ByteArray?

    /** This device's private-network IPv4 address, or null when not on a LAN. */
    fun lanIpv4Address(): String?

    /** QR modules (true = dark), row by row, or null when encoding is not available. */
    fun qrMatrix(text: String): List<BooleanArray>?

    fun deviceName(): String
}

@OptIn(ExperimentalEncodingApi::class)
internal fun ByteArray.toBase64Url(): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(this)

/**
 * Receives `spotube://qr-login` links opened on this device (from the TV's QR page) and keeps
 * the pending one until the user answers. Other links keep the old behaviour (ignored).
 */
class QrLoginInbox {
    private val _pending = MutableStateFlow<QrLoginLink?>(null)
    val pending: StateFlow<QrLoginLink?> = _pending.asStateFlow()

    init {
        ExternalUriHandler.listener = { uri ->
            QrLoginLink.parse(uri)?.let { _pending.value = it }
        }
    }

    fun clear() {
        _pending.value = null
    }
}
