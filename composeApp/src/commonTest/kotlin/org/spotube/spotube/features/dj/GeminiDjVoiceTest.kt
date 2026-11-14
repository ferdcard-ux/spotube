package org.spotube.spotube.features.dj

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.spotube.spotube.modules.settings.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

@OptIn(ExperimentalEncodingApi::class)
class GeminiDjVoiceTest {
    private val options = DjSpeechOptions(DjVoiceCharacter.ENERGETIC, DjVoiceGender.MALE, DjVoiceAccent.COLOMBIA,
        DjVoiceExpressiveness.EXPRESSIVE, 1f, DjSpeechEngine.GEMINI_CLOUD)

    private fun audioReply(pcm: ByteArray, rate: Int = 24_000) = DjAiReply(200, buildJsonObject {
        putJsonArray("candidates") { add(buildJsonObject { putJsonObject("content") { putJsonArray("parts") {
            add(buildJsonObject { putJsonObject("inlineData") {
                put("mimeType", "audio/L16;codec=pcm;rate=$rate"); put("data", Base64.encode(pcm))
            } })
        } } }) }
    }.toString())

    @Test fun requestAsksForAudioWithTheChosenVoiceAndKeepsTheScript() {
        val body = Json.parseToJsonElement(geminiVoiceRequest("Soy DJ Livi.", "Livi", options)).jsonObject
        val config = body["generationConfig"]!!.jsonObject
        assertEquals("AUDIO", config["responseModalities"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("Fenrir", config["speechConfig"]!!.jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!
            .jsonObject["voiceName"]!!.jsonPrimitive.content)
        val prompt = body["contents"]!!.jsonArray.single().jsonObject["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(prompt.endsWith("Transcripción: Soy DJ Livi."))
        assertTrue("colombiano" in prompt)
    }

    @Test fun decodesPcmAndSampleRate() = runTest {
        val voice = GeminiDjVoice({ "test-only-key" }, { _, _, _ -> audioReply(ByteArray(48_000), 24_000) })
        val clip = (voice.synthesize("Hola", "Livi", options) as DjVoiceResult.Ready).clip
        assertEquals(24_000, clip.sampleRate)
        assertEquals(1_000, clip.durationMs)
        assertEquals(44 + 48_000, clip.toWav().size)
    }

    @Test fun missingKeySendsNothing() = runTest {
        var calls = 0
        val voice = GeminiDjVoice({ "" }, { _, _, _ -> calls++; audioReply(ByteArray(4)) })
        assertEquals(DjVoiceResult.Failed(DjVoiceFailure.NO_KEY), voice.synthesize("Hola", "Livi", options))
        assertEquals(0, calls)
    }

    @Test fun unknownModelFallsBackToTheNextName() = runTest {
        val models = mutableListOf<String>()
        val voice = GeminiDjVoice({ "test-only-key" }, { model, _, _ ->
            models += model; if (models.size == 1) DjAiReply(404, "") else audioReply(ByteArray(4))
        })
        assertIs<DjVoiceResult.Ready>(voice.synthesize("Hola", "Livi", options))
        assertEquals(GeminiDjVoice.MODELS, models)
    }

    @Test fun rateLimitCoolsDownWithoutRetrying() = runTest {
        var calls = 0
        var clock = 0L
        val voice = GeminiDjVoice({ "test-only-key" }, { _, _, _ -> calls++; DjAiReply(429, "") }, now = { clock })
        assertEquals(DjVoiceResult.Failed(DjVoiceFailure.RATE_LIMIT), voice.synthesize("Hola", "Livi", options))
        assertEquals(DjVoiceResult.Failed(DjVoiceFailure.RATE_LIMIT), voice.synthesize("Hola", "Livi", options))
        assertEquals(1, calls)
        clock += 11 * 60_000L
        voice.synthesize("Hola", "Livi", options)
        assertEquals(2, calls)
    }

    private fun voiceOf(body: String) = Json.parseToJsonElement(body).jsonObject["generationConfig"]!!.jsonObject["speechConfig"]!!
        .jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!.jsonObject["voiceName"]!!.jsonPrimitive.content

    @Test fun chosenVoiceWinsAndUnknownNamesFallBackToAutomatic() {
        assertEquals("Kore", voiceOf(geminiVoiceRequest("Hola", "Livi", options.copy(voiceName = "Kore"))))
        assertEquals("Fenrir", voiceOf(geminiVoiceRequest("Hola", "Livi", options.copy(voiceName = "NoExiste"))))
        assertEquals(30, GEMINI_VOICES.map { it.name }.distinct().size)
    }

    @Test fun responseWithoutAudioIsUnavailable() {
        assertNull(decodeGeminiAudio("""{"candidates":[{"content":{"parts":[{"text":"hola"}]}}]}"""))
    }
}
