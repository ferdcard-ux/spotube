package org.spotube.spotube.features.dj

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.spotube.spotube.core.network.HttpClientFactory

internal data class DjAiReply(val status: Int, val body: String)
internal fun interface DjAiTransport {
    suspend fun send(config: DjAiUiState, prompt: String): DjAiReply
}

/** Usa el cliente compartido y nunca registra cabeceras, cuerpos ni excepciones con claves. */
internal class KtorDjAiTransport(private val clients: HttpClientFactory) : DjAiTransport {
    override suspend fun send(config: DjAiUiState, prompt: String): DjAiReply {
        val gemini = config.provider == DjAiProvider.GEMINI
        val endpoint = when (config.provider) {
            DjAiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            DjAiProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
            DjAiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/${config.model.encodeURLPathPart()}:generateContent"
        }
        val body = if (gemini) buildJsonObject {
            putJsonArray("contents") { add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) } }) }
            putJsonObject("generationConfig") { put("maxOutputTokens", 2048) }
        } else buildJsonObject {
            put("model", config.model)
            // Los modelos con razonamiento (gpt-oss en Groq) gastan tokens antes de responder.
            put("max_tokens", 2048)
            if (config.provider == DjAiProvider.GROQ && config.model.contains("gpt-oss")) put("reasoning_effort", "low")
            if (config.provider == DjAiProvider.OPENROUTER) {
                // El router gratuito filtra por compatibilidad con JSON cuando se solicita este formato.
                putJsonObject("response_format") { put("type", "json_object") }
            }
            putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", prompt) }) }
        }
        val response = clients.default.post(endpoint) {
            contentType(ContentType.Application.Json)
            if (gemini) header("x-goog-api-key", config.apiKey) else bearerAuth(config.apiKey)
            setBody(body.toString())
        }
        // Los errores HTTP se clasifican por número: nunca se muestra el mensaje remoto sin filtrar.
        return DjAiReply(response.status.value, if (response.status.isSuccess()) response.bodyAsText() else "")
    }
}

internal data class DjAiDecoded(val ids: List<String>? = null, val failure: DjAiFailure? = null, val httpStatus: Int? = null)

internal fun djAiHttpFailure(code: Int): DjAiFailure = when (code) {
    401 -> DjAiFailure.AUTHENTICATION
    402 -> DjAiFailure.ACCOUNT_LIMIT
    403 -> DjAiFailure.PERMISSION
    404 -> DjAiFailure.MODEL_UNAVAILABLE
    408, 504 -> DjAiFailure.TIMEOUT
    429 -> DjAiFailure.RATE_LIMIT
    400, 422 -> DjAiFailure.REQUEST_REJECTED
    else -> DjAiFailure.PROVIDER_UNAVAILABLE
}

/** Algunos proveedores devuelven un error en un cuerpo HTTP 200; también se comprueba aquí. */
internal fun decodeDjAiReply(reply: DjAiReply, provider: DjAiProvider, allowed: Set<String>, count: Int): DjAiDecoded {
    if (reply.status !in 200..299) return DjAiDecoded(failure = djAiHttpFailure(reply.status), httpStatus = reply.status)
    if (reply.body.length > 65_536) return DjAiDecoded(failure = DjAiFailure.INVALID_RESPONSE)
    return runCatching {
        val parsed = Json.parseToJsonElement(reply.body).jsonObject
        parsed["error"]?.jsonObject?.let { error ->
            val code = error["code"]?.jsonPrimitive?.intOrNull
            return DjAiDecoded(failure = code?.let(::djAiHttpFailure) ?: DjAiFailure.PROVIDER_UNAVAILABLE, httpStatus = code)
        }
        val gemini = provider == DjAiProvider.GEMINI
        val candidate = parsed[if (gemini) "candidates" else "choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val finish = candidate?.get(if (gemini) "finishReason" else "finish_reason")?.jsonPrimitive?.contentOrNull
        val text = if (gemini) candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("")
        else candidate?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        if (finish == "length" || finish == "MAX_TOKENS") return DjAiDecoded(failure = DjAiFailure.OUTPUT_LIMIT)
        if (text.isNullOrBlank()) return DjAiDecoded(failure = DjAiFailure.EMPTY_RESPONSE)
        val ids = validateDjPlan(text, allowed, count)
        if (ids == null) DjAiDecoded(failure = DjAiFailure.INVALID_PLAN) else DjAiDecoded(ids = ids)
    }.getOrElse { DjAiDecoded(failure = DjAiFailure.INVALID_RESPONSE) }
}
