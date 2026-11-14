package org.spotube.spotube.features.dj

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.Foundation.NSData
import platform.Foundation.create

actual class NativeSpeech actual constructor(context: Any) {
    private val synthesizer = AVSpeechSynthesizer()
    private var player: AVAudioPlayer? = null

    actual suspend fun speak(text: String, language: String, options: DjSpeechOptions): Unit = withContext(Dispatchers.Main) {
        stop()
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        val chosen = options.voiceName.takeIf { it.isNotBlank() }?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
        if (chosen != null) utterance.voice = chosen
        else if (language.isNotBlank()) utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(language)
        utterance.rate = (AVSpeechUtteranceDefaultSpeechRate * options.speed.coerceIn(.5f, 1.8f))
        try {
            synthesizer.speakUtterance(utterance)
            // Se cede el hilo principal para que AVFoundation inicie la síntesis.
            delay(100)
            while (synthesizer.speaking) delay(40)
        } finally { stop() }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun play(clip: DjVoiceClip): Unit = withContext(Dispatchers.Main) {
        stop()
        val wav = clip.toWav()
        val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
        val audio = AVAudioPlayer(data = data, error = null)
        player = audio
        try {
            check(audio.play()) { "DJ voice playback failed" }
            delay(100)
            while (audio.playing) delay(40)
        } finally { stop() }
    }

    actual suspend fun synthesize(text: String, language: String, options: DjSpeechOptions): DjVoiceClip? = null

    actual suspend fun systemVoices(language: String): List<DjSystemVoice> {
        val wanted = language.substringBefore('-').ifBlank { "es" }
        return AVSpeechSynthesisVoice.speechVoices().filterIsInstance<AVSpeechSynthesisVoice>()
            .filter { it.language.startsWith(wanted) || it.language.startsWith("es") }
            .map { DjSystemVoice(it.identifier, it.language, needsNetwork = false) }
            .sortedBy { it.languageTag }
    }

    actual fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
        player?.stop()
        player = null
    }
}
