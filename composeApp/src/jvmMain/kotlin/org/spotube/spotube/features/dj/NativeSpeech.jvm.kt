package org.spotube.spotube.features.dj

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Base64

actual class NativeSpeech actual constructor(context: Any) {
    private var process: Process? = null

    actual suspend fun speak(text: String, language: String, options: DjSpeechOptions) = withContext(Dispatchers.IO) {
        stop()
        val os = System.getProperty("os.name").lowercase()
        // El texto viaja por stdin: nunca se interpola en un comando del sistema.
        val command = when {
            os.contains("win") -> {
                val script = "[Console]::InputEncoding = [System.Text.Encoding]::UTF8; Add-Type -AssemblyName System.Speech; " +
                    "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "\$s.Rate = ${((options.speed - 1f) * 10).toInt().coerceIn(-10, 10)}; \$s.Speak([Console]::In.ReadToEnd()); \$s.Dispose()"
                listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand",
                    Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE)))
            }
            os.contains("mac") -> listOf("/usr/bin/say")
            else -> listOf("spd-say", "--wait", "--pipe-mode")
        }
        val builder = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        val child = builder.start()
        process = child
        try {
            child.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            while (child.isAlive) delay(40)
            check(child.exitValue() == 0) { "Native speech failed" }
        } finally {
            child.destroyForcibly()
            if (process === child) process = null
        }
    }

    @Volatile private var line: javax.sound.sampled.SourceDataLine? = null

    actual suspend fun play(clip: DjVoiceClip) = withContext(Dispatchers.IO) {
        stop()
        val format = javax.sound.sampled.AudioFormat(clip.sampleRate.toFloat(), 16, 1, true, false)
        val output = javax.sound.sampled.AudioSystem.getSourceDataLine(format)
        line = output
        try {
            output.open(format)
            output.start()
            var offset = 0
            while (offset < clip.pcm16.size) {
                ensureActive()
                if (line !== output) throw kotlinx.coroutines.CancellationException("DJ voice playback stopped")
                val count = minOf(8 * 1024, clip.pcm16.size - offset)
                offset += output.write(clip.pcm16, offset, count).coerceAtLeast(1)
            }
            output.drain()
        } finally {
            output.close()
            if (line === output) line = null
        }
    }

    /** La voz del sistema operativo se lanza como proceso externo: no se puede generar por adelantado. */
    actual suspend fun synthesize(text: String, language: String, options: DjSpeechOptions): DjVoiceClip? = null

    /** El escritorio usa la voz predeterminada del sistema operativo; no se ofrece lista. */
    actual suspend fun systemVoices(language: String): List<DjSystemVoice> = emptyList()

    actual fun stop() {
        process?.destroyForcibly(); process = null
        line?.let { line = null; it.stop(); it.flush() }
    }
}
