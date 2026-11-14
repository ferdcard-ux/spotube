package org.spotube.spotube.features.dj

import org.spotube.spotube.modules.settings.*

data class DjSpeechOptions(
    val character: DjVoiceCharacter, val gender: DjVoiceGender, val accent: DjVoiceAccent,
    val expressiveness: DjVoiceExpressiveness, val speed: Float,
    val engine: DjSpeechEngine,
    /** Voz concreta elegida para [engine]; vacío = elegir automáticamente por género y carácter. */
    val voiceName: String = "")

/** Espera el final real de la voz. La cancelación debe detener la síntesis nativa. */
expect class NativeSpeech(context: Any) {
    suspend fun speak(text: String, language: String, options: DjSpeechOptions)
    /** Reproduce audio ya sintetizado (voz en la nube) y espera a que termine. */
    suspend fun play(clip: DjVoiceClip)
    /** Genera por adelantado el audio de la voz del sistema; null donde la plataforma no lo permite. */
    suspend fun synthesize(text: String, language: String, options: DjSpeechOptions): DjVoiceClip?
    /** Voces del sistema instaladas para [language] (o todas las españolas si está vacío). */
    suspend fun systemVoices(language: String): List<DjSystemVoice>
    fun stop()
}
