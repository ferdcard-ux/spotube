package org.spotube.spotube.features.dj

/** Espera el final real de la voz. La cancelación debe detener la síntesis nativa. */
expect class NativeSpeech(context: Any) {
    suspend fun speak(text: String, language: String)
    fun stop()
}
