package org.spotube.spotube.features.dj

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class NativeSpeech actual constructor(context: Any) {
    private val ready = CompletableDeferred<Unit>()
    private val tts = TextToSpeech((context as Context).applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) ready.complete(Unit)
        else ready.completeExceptionally(IllegalStateException("TTS initialization failed"))
    }

    actual suspend fun speak(text: String, language: String, options: DjSpeechOptions): Unit = withContext(Dispatchers.Main) {
        withTimeout(5_000) { ready.await() }
        val locale = when (options.accent) {
            org.spotube.spotube.modules.settings.DjVoiceAccent.COLOMBIA -> Locale("es", "CO")
            org.spotube.spotube.modules.settings.DjVoiceAccent.MEXICO -> Locale("es", "MX")
            org.spotube.spotube.modules.settings.DjVoiceAccent.SPAIN -> Locale("es", "ES")
            org.spotube.spotube.modules.settings.DjVoiceAccent.ARGENTINA -> Locale("es", "AR")
            else -> if (language.isBlank()) Locale.getDefault() else Locale.forLanguageTag(language)
        }
        val result = tts.setLanguage(locale)
        check(result >= 0) { "TTS language unavailable" }
        val characterRate = when (options.character) { org.spotube.spotube.modules.settings.DjVoiceCharacter.CALM -> .90f; org.spotube.spotube.modules.settings.DjVoiceCharacter.ENERGETIC -> 1.08f; else -> 1f }
        tts.setSpeechRate((options.speed * characterRate).coerceIn(.5f, 1.8f))
        val pitch = when (options.expressiveness) { org.spotube.spotube.modules.settings.DjVoiceExpressiveness.SOFT -> .92f; org.spotube.spotube.modules.settings.DjVoiceExpressiveness.EXPRESSIVE -> 1.08f; else -> 1f }
        tts.setPitch(pitch)
        val genderHint = options.gender.name.lowercase()
        tts.voices?.filter { it.locale.language == locale.language }?.sortedByDescending { it.quality }?.firstOrNull { voice ->
            options.gender == org.spotube.spotube.modules.settings.DjVoiceGender.AUTO || voice.name.lowercase().contains(genderHint)
        }?.let(tts::setVoice)
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
}
