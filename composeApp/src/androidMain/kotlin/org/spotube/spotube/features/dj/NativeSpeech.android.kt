package org.spotube.spotube.features.dj

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class NativeSpeech actual constructor(context: Any) {
    private val applicationContext = (context as Context).applicationContext
    private val ready = CompletableDeferred<Unit>()
    private val tts = TextToSpeech(applicationContext, { status ->
        if (status == TextToSpeech.SUCCESS) ready.complete(Unit)
        else ready.completeExceptionally(IllegalStateException("TTS initialization failed"))
    }, installedEngine(applicationContext))

    actual suspend fun speak(text: String, language: String, options: DjSpeechOptions): Unit = withContext(Dispatchers.Main) {
        withTimeout(5_000) { ready.await() }
        val requestedLocale = when (options.accent) {
            org.spotube.spotube.modules.settings.DjVoiceAccent.COLOMBIA -> Locale("es", "CO")
            org.spotube.spotube.modules.settings.DjVoiceAccent.MEXICO -> Locale("es", "MX")
            org.spotube.spotube.modules.settings.DjVoiceAccent.SPAIN -> Locale("es", "ES")
            org.spotube.spotube.modules.settings.DjVoiceAccent.ARGENTINA -> Locale("es", "AR")
            else -> if (language.isBlank()) Locale.getDefault() else Locale.forLanguageTag(language)
        }
        // Algunos motores no exponen el idioma genérico ("es"), sólo voces regionales.
        // Probamos una voz instalada del idioma antes de declarar que la síntesis no existe.
        val locale = sequenceOf(
            requestedLocale,
            tts.voices.orEmpty().firstOrNull { it.locale.language == requestedLocale.language }?.locale,
            Locale("es", "US"), Locale("es", "ES"), Locale.getDefault()
        ).filterNotNull().distinct().firstOrNull { tts.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: error("TTS language unavailable")
        val characterRate = when (options.character) { org.spotube.spotube.modules.settings.DjVoiceCharacter.CALM -> .90f; org.spotube.spotube.modules.settings.DjVoiceCharacter.ENERGETIC -> 1.08f; else -> 1f }
        tts.setSpeechRate((options.speed * characterRate).coerceIn(.5f, 1.8f))
        val expressionPitch = when (options.expressiveness) { org.spotube.spotube.modules.settings.DjVoiceExpressiveness.SOFT -> .92f; org.spotube.spotube.modules.settings.DjVoiceExpressiveness.EXPRESSIVE -> 1.08f; else -> 1f }
        // Si el motor no marca género en sus voces, el tono todavía produce una diferencia audible.
        val genderPitch = when (options.gender) { org.spotube.spotube.modules.settings.DjVoiceGender.MALE -> .84f; org.spotube.spotube.modules.settings.DjVoiceGender.FEMALE -> 1.13f; else -> 1f }
        tts.setPitch((expressionPitch * genderPitch).coerceIn(.5f, 2f))
        tts.voices.orEmpty().filter { it.locale.language == locale.language }
            .maxWithOrNull(compareBy<android.speech.tts.Voice> { genderScore(it, options.gender) }.thenBy { it.quality })
            ?.let(tts::setVoice)
        suspendCancellableCoroutine { continuation ->
            val id = java.util.UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                }
                @Deprecated("Native callback")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive)
                        continuation.resumeWithException(IllegalStateException("TTS failed"))
                }
            })
            continuation.invokeOnCancellation { tts.stop() }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR && continuation.isActive)
                continuation.resumeWithException(IllegalStateException("TTS failed"))
        }
    }
    actual fun stop() { tts.stop() }

    private fun genderScore(voice: android.speech.tts.Voice, requested: org.spotube.spotube.modules.settings.DjVoiceGender): Int {
        if (requested == org.spotube.spotube.modules.settings.DjVoiceGender.AUTO) return 0
        val descriptor = (voice.name + " " + voice.features.orEmpty().joinToString()).lowercase()
        val female = listOf("female", "woman", "femen").any(descriptor::contains)
        val male = !female && listOf("male", "man", "masc").any(descriptor::contains)
        return when (requested) {
            org.spotube.spotube.modules.settings.DjVoiceGender.FEMALE -> if (female) 2 else 0
            org.spotube.spotube.modules.settings.DjVoiceGender.MALE -> if (male) 2 else 0
            else -> 0
        }
    }

    /** En algunos Android el motor está instalado pero no marcado como predeterminado. */
    private fun installedEngine(context: Context): String? = context.packageManager
        .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
        .firstOrNull()?.serviceInfo?.packageName
}
