package org.spotube.spotube.features.dj

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.AVFAudio.*

actual class NativeSpeech actual constructor(context: Any) {
    private val synthesizer = AVSpeechSynthesizer()

    actual suspend fun speak(text: String, language: String, options: DjSpeechOptions): Unit = withContext(Dispatchers.Main) {
        stop()
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        if (language.isNotBlank()) utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(language)
        utterance.rate = (AVSpeechUtteranceDefaultSpeechRate * options.speed.coerceIn(.5f, 1.8f))
        try {
            synthesizer.speakUtterance(utterance)
            // Se cede el hilo principal para que AVFoundation inicie la síntesis.
            delay(100)
            while (synthesizer.speaking) delay(40)
        } finally { stop() }
    }
    actual fun stop() { synthesizer.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate) }
}
