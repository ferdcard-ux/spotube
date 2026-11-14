package org.spotube.spotube.features.dj

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import org.spotube.spotube.core.network.HttpClientFactory
import org.spotube.spotube.modules.settings.DjVoiceAccent
import org.spotube.spotube.modules.settings.DjVoiceCharacter
import org.spotube.spotube.modules.settings.DjVoiceExpressiveness
import org.spotube.spotube.modules.settings.DjVoiceGender
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Audio ya sintetizado: PCM de 16 bits, little endian, mono. */
class DjVoiceClip(val pcm16: ByteArray, val sampleRate: Int, val boost: Boolean = false) {
    val durationMs: Long get() = pcm16.size / 2L * 1000L / sampleRate.coerceAtLeast(1)

    /** El mismo audio con cabecera WAV, para reproductores que no aceptan PCM sin formato. */
    fun toWav(): ByteArray {
        val out = ByteArray(44 + pcm16.size)
        fun text(at: Int, value: String) = value.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
        fun int32(at: Int, value: Int) { for (i in 0..3) out[at + i] = (value shr (8 * i)).toByte() }
        fun int16(at: Int, value: Int) { for (i in 0..1) out[at + i] = (value shr (8 * i)).toByte() }
        text(0, "RIFF"); int32(4, 36 + pcm16.size); text(8, "WAVE")
        text(12, "fmt "); int32(16, 16); int16(20, 1); int16(22, 1)
        int32(24, sampleRate); int32(28, sampleRate * 2); int16(32, 2); int16(34, 16)
        text(36, "data"); int32(40, pcm16.size)
        pcm16.copyInto(out, 44)
        return out
    }
}

enum class DjVoiceFailure { NO_KEY, RATE_LIMIT, AUTHENTICATION, UNAVAILABLE }

sealed interface DjVoiceResult {
    class Ready(val clip: DjVoiceClip) : DjVoiceResult
    data class Failed(val reason: DjVoiceFailure) : DjVoiceResult
}

internal fun interface DjVoiceTransport {
    /** Devuelve el código HTTP y, sólo si tuvo éxito, el cuerpo. */
    suspend fun send(model: String, apiKey: String, body: String): DjAiReply
}

internal class KtorDjVoiceTransport(clients: HttpClientFactory) : DjVoiceTransport {
    // Generar el audio tarda más que el tiempo de espera de lectura del cliente compartido.
    private val client by lazy {
        clients.create {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = GeminiDjVoice.REQUEST_TIMEOUT_MS
                socketTimeoutMillis = GeminiDjVoice.REQUEST_TIMEOUT_MS
                connectTimeoutMillis = 15_000
            }
        }
    }

    override suspend fun send(model: String, apiKey: String, body: String): DjAiReply {
        val response = client.post(
            "https://generativelanguage.googleapis.com/v1beta/models/${model.encodeURLPathPart()}:generateContent"
        ) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", apiKey)
            setBody(body)
        }
        return DjAiReply(response.status.value, if (response.status.isSuccess()) response.bodyAsText() else "")
    }
}

/**
 * Voz del DJ con Gemini TTS. Usa la clave de Gemini que el usuario guardó en la sección de IA
 * (nunca se registra) y sólo envía el texto que el DJ va a decir.
 */
class GeminiDjVoice internal constructor(
    private val keys: suspend () -> String,
    private val transport: DjVoiceTransport,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    constructor(session: DjAiSession, clients: HttpClientFactory) :
        this({ session.storedKey(DjAiProvider.GEMINI) }, KtorDjVoiceTransport(clients))

    /** Tras un 429 no se insiste: la cuota gratuita es pequeña y cada intento la consume. */
    private var coolDownUntil = 0L
    private val log = co.touchlab.kermit.Logger.withTag("GeminiVoice")

    suspend fun synthesize(text: String, name: String, options: DjSpeechOptions): DjVoiceResult {
        val key = keys().trim()
        if (key.isEmpty()) return DjVoiceResult.Failed(DjVoiceFailure.NO_KEY)
        if (now() < coolDownUntil) return DjVoiceResult.Failed(DjVoiceFailure.RATE_LIMIT)
        val body = geminiVoiceRequest(text, name, options)
        for (model in MODELS) {
            val started = now()
            val reply = try {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) { transport.send(model, key, body) }
                    ?: run {
                        log.w { "$model: timed out after $REQUEST_TIMEOUT_MS ms (${text.length} chars)" }
                        return DjVoiceResult.Failed(DjVoiceFailure.UNAVAILABLE)
                    }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                log.w { "$model: request failed (${e::class.simpleName})" }
                return DjVoiceResult.Failed(DjVoiceFailure.UNAVAILABLE)
            }
            // Sólo el código y la duración: nunca la clave ni el texto.
            log.i { "$model: HTTP ${reply.status} in ${now() - started} ms (${text.length} chars)" }
            when (reply.status) {
                in 200..299 -> return decodeGeminiAudio(reply.body)
                    ?.let(DjVoiceResult::Ready)
                    ?: run { log.w { "$model: reply without audio" }; DjVoiceResult.Failed(DjVoiceFailure.UNAVAILABLE) }
                // Un modelo retirado o renombrado: se prueba el siguiente nombre conocido.
                404 -> continue
                429 -> {
                    coolDownUntil = now() + 10.minutes.inWholeMilliseconds
                    return DjVoiceResult.Failed(DjVoiceFailure.RATE_LIMIT)
                }
                401, 403 -> return DjVoiceResult.Failed(DjVoiceFailure.AUTHENTICATION)
                else -> return DjVoiceResult.Failed(DjVoiceFailure.UNAVAILABLE)
            }
        }
        return DjVoiceResult.Failed(DjVoiceFailure.UNAVAILABLE)
    }

    internal companion object {
        val MODELS = listOf("gemini-2.5-flash-preview-tts", "gemini-2.5-flash-tts")
        const val REQUEST_TIMEOUT_MS = 45_000L
        const val DEFAULT_SAMPLE_RATE = 24_000
    }
}

/** Voces preconstruidas de Gemini elegidas por género y carácter. */
internal fun geminiVoiceName(gender: DjVoiceGender, character: DjVoiceCharacter): String = when (gender) {
    DjVoiceGender.MALE -> when (character) {
        DjVoiceCharacter.ENERGETIC -> "Fenrir"
        DjVoiceCharacter.WARM -> "Achird"
        DjVoiceCharacter.CALM -> "Charon"
        DjVoiceCharacter.NATURAL -> "Puck"
    }
    DjVoiceGender.FEMALE -> when (character) {
        DjVoiceCharacter.ENERGETIC -> "Laomedeia"
        DjVoiceCharacter.WARM -> "Sulafat"
        DjVoiceCharacter.CALM -> "Aoede"
        DjVoiceCharacter.NATURAL -> "Zephyr"
    }
    DjVoiceGender.AUTO, DjVoiceGender.NEUTRAL -> when (character) {
        DjVoiceCharacter.ENERGETIC -> "Fenrir"
        DjVoiceCharacter.WARM -> "Sulafat"
        DjVoiceCharacter.CALM -> "Aoede"
        DjVoiceCharacter.NATURAL -> "Puck"
    }
}

/** Notas de dirección: Gemini interpreta el estilo y sólo lee la transcripción. */
internal fun geminiVoicePrompt(text: String, name: String, options: DjSpeechOptions): String {
    val character = when (options.character) {
        DjVoiceCharacter.NATURAL -> "cercano y natural, como quien conversa con un amigo"
        DjVoiceCharacter.WARM -> "cálido y acogedor, con una sonrisa en la voz"
        DjVoiceCharacter.ENERGETIC -> "con mucha energía y entusiasmo, como un locutor de radio animando la fiesta"
        DjVoiceCharacter.CALM -> "sereno y relajado, como un programa nocturno de radio"
    }
    val expression = when (options.expressiveness) {
        DjVoiceExpressiveness.NATURAL -> "Entonación viva y fluida, sin exagerar."
        DjVoiceExpressiveness.SOFT -> "Entonación suave, volumen contenido."
        DjVoiceExpressiveness.EXPRESSIVE -> "Muy expresivo: varía el ritmo, enfatiza las palabras clave y transmite emoción."
    }
    val accent = when (options.accent) {
        DjVoiceAccent.COLOMBIA -> "Español con acento colombiano."
        DjVoiceAccent.MEXICO -> "Español con acento mexicano."
        DjVoiceAccent.SPAIN -> "Español con acento de España."
        DjVoiceAccent.ARGENTINA -> "Español con acento rioplatense."
        DjVoiceAccent.SYSTEM -> "Pronunciación natural del idioma del texto."
    }
    val pace = when {
        options.speed >= 1.15f -> "Ritmo ágil."
        options.speed <= .9f -> "Ritmo pausado."
        else -> "Ritmo natural."
    }
    return buildString {
        appendLine("Eres DJ ${name.filterNot(Char::isISOControl)}, presentador de un programa musical.")
        appendLine("Lee en voz alta únicamente la transcripción, sin añadir ni omitir palabras.")
        appendLine("Estilo: $character. $expression $accent $pace")
        append("Transcripción: ")
        append(text.filterNot(Char::isISOControl))
    }
}

internal fun geminiVoiceRequest(text: String, name: String, options: DjSpeechOptions): String = buildJsonObject {
    putJsonArray("contents") {
        add(buildJsonObject {
            putJsonArray("parts") { add(buildJsonObject { put("text", geminiVoicePrompt(text, name, options)) }) }
        })
    }
    putJsonObject("generationConfig") {
        putJsonArray("responseModalities") { add("AUDIO") }
        putJsonObject("speechConfig") {
            putJsonObject("voiceConfig") {
                putJsonObject("prebuiltVoiceConfig") {
                    put("voiceName", options.voiceName.takeIf { name -> GEMINI_VOICES.any { it.name == name } }
                        ?: geminiVoiceName(options.gender, options.character))
                }
            }
        }
    }
}.toString()

/** Extrae el PCM (`audio/L16;rate=24000`) de la respuesta; null si no trae audio válido. */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeGeminiAudio(body: String): DjVoiceClip? = runCatching {
    val inline = Json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("content")?.jsonObject?.get("parts")?.jsonArray
        ?.firstNotNullOfOrNull { it.jsonObject["inlineData"]?.jsonObject }
        ?: return null
    val mime = inline["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull()
        ?: GeminiDjVoice.DEFAULT_SAMPLE_RATE
    val data = inline["data"]?.jsonPrimitive?.contentOrNull ?: return null
    val pcm = Base64.decode(data)
    if (pcm.size < 2) return null
    // Un tamaño impar dejaría la última muestra a medias.
    DjVoiceClip(if (pcm.size % 2 == 0) pcm else pcm.copyOf(pcm.size - 1), rate)
}.getOrNull()
