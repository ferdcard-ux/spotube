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

    actual suspend fun speak(text: String, language: String): Unit = withContext(Dispatchers.Main) {
        withTimeout(5_000) { ready.await() }
        val result = tts.setLanguage(if (language.isBlank()) Locale.getDefault() else Locale.forLanguageTag(language))
        check(result >= 0) { "TTS language unavailable" }
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
