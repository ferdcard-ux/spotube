package org.spotube.spotube.features.dj

import org.spotube.spotube.modules.settings.*

data class DjSpeechOptions(
    val character: DjVoiceCharacter, val gender: DjVoiceGender, val accent: DjVoiceAccent,
    val expressiveness: DjVoiceExpressiveness, val speed: Float,
    val engine: DjSpeechEngine)

/** Espera el final real de la voz. La cancelación debe detener la síntesis nativa. */
expect class NativeSpeech(context: Any) {
    suspend fun speak(text: String, language: String, options: DjSpeechOptions)
    fun stop()
}
