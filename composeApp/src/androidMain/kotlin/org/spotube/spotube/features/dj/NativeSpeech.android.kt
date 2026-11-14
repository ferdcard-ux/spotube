package org.spotube.spotube.features.dj

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import android.os.Bundle
import android.media.audiofx.LoudnessEnhancer
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class NativeSpeech actual constructor(context: Any) {
    private val applicationContext = (context as Context).applicationContext
    private val ready = CompletableDeferred<Unit>()
    private val clipStopped = AtomicBoolean(false)
    /** Un solo uso del motor TTS a la vez (comparte un único listener). */
    private val engineLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var clipTrack: AudioTrack? = null
    private val tts = TextToSpeech(applicationContext, { status ->
        if (status == TextToSpeech.SUCCESS) ready.complete(Unit)
        else ready.completeExceptionally(IllegalStateException("TTS initialization failed"))
    }, installedEngine(applicationContext))

    /** Ajusta idioma, voz, velocidad y tono del motor según [options]. Requiere el motor listo. */
    private fun configure(language: String, options: DjSpeechOptions) {
        val requestedLocale = when (options.accent) {
            org.spotube.spotube.modules.settings.DjVoiceAccent.COLOMBIA -> Locale("es", "CO")
            org.spotube.spotube.modules.settings.DjVoiceAccent.MEXICO -> Locale("es", "MX")
            org.spotube.spotube.modules.settings.DjVoiceAccent.SPAIN -> Locale("es", "ES")
            org.spotube.spotube.modules.settings.DjVoiceAccent.ARGENTINA -> Locale("es", "AR")
            else -> if (language.isBlank()) Locale.getDefault() else Locale.forLanguageTag(language)
        }
        val locale = sequenceOf(
            requestedLocale,
            tts.voices.orEmpty().firstOrNull { it.locale.language == requestedLocale.language }?.locale,
            Locale("es", "US"), Locale("es", "ES"), Locale.getDefault()
        ).filterNotNull().distinct().firstOrNull { tts.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: error("TTS language unavailable")
        val characterRate = when (options.character) {
            org.spotube.spotube.modules.settings.DjVoiceCharacter.CALM -> .90f
            org.spotube.spotube.modules.settings.DjVoiceCharacter.ENERGETIC -> 1.08f
            else -> 1f
        }
        tts.setSpeechRate((options.speed * characterRate).coerceIn(.5f, 1.8f))
        val expressionPitch = when (options.expressiveness) {
            org.spotube.spotube.modules.settings.DjVoiceExpressiveness.SOFT -> .92f
            org.spotube.spotube.modules.settings.DjVoiceExpressiveness.EXPRESSIVE -> 1.08f
            else -> 1f
        }
        val genderPitch = when (options.gender) {
            org.spotube.spotube.modules.settings.DjVoiceGender.MALE -> .84f
            org.spotube.spotube.modules.settings.DjVoiceGender.FEMALE -> 1.13f
            else -> 1f
        }
        tts.setPitch((expressionPitch * genderPitch).coerceIn(.5f, 2f))
        // Una voz elegida a mano gana; si ya no está instalada, se vuelve a la elección automática.
        val chosen = options.voiceName.takeIf { it.isNotBlank() && options.engine == org.spotube.spotube.modules.settings.DjSpeechEngine.SYSTEM }
            ?.let { name -> tts.voices.orEmpty().firstOrNull { it.name == name } }
        if (chosen != null) {
            tts.setVoice(chosen)
            // El tono de género sólo aproximaba un género que el usuario ya eligió con la voz.
            tts.setPitch(expressionPitch.coerceIn(.5f, 2f))
        } else tts.voices.orEmpty().filter { it.locale.language == locale.language }
            .maxWithOrNull(compareBy<android.speech.tts.Voice> { genderScore(it, options.gender) }.thenBy { it.quality })
            ?.let(tts::setVoice)
    }

    /**
     * Dice [text] directamente (empieza al instante). El DJ normalmente usa [synthesize] por
     * adelantado, que suena más fuerte; esto es el último recurso.
     */
    actual suspend fun speak(text: String, language: String, options: DjSpeechOptions): Unit = withContext(Dispatchers.Main) {
        engineLock.withLock {
        withTimeout(5_000) { ready.await() }
        configure(language, options)
        suspendCancellableCoroutine { continuation ->
            val id = UUID.randomUUID().toString()
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
    }

    /**
     * Genera el audio de [text] con la voz del sistema para reproducirlo después con realce de
     * sonoridad (la voz sintetizada suena más baja que la música). null si el motor no puede.
     */
    actual suspend fun synthesize(text: String, language: String, options: DjSpeechOptions): DjVoiceClip? =
        withContext(Dispatchers.Main) {
            engineLock.withLock {
                runCatching { withTimeout(5_000) { ready.await() } }.getOrNull() ?: return@withLock null
                configure(language, options)
                val wav = File(applicationContext.cacheDir, "dj-tts-${UUID.randomUUID()}.wav")
                val started = android.os.SystemClock.elapsedRealtime()
                try {
                    val clip = if (synthesizeToFile(text, wav)) withContext(Dispatchers.IO) { readPcm16Wav(wav) } else null
                    Log.i(TAG, "System voice synthesized ${text.length} chars in ${android.os.SystemClock.elapsedRealtime() - started} ms " +
                        "(${clip?.durationMs ?: 0} ms of audio)")
                    clip?.let { DjVoiceClip(it.pcm16, it.sampleRate, boost = true) }
                } finally {
                    wav.delete()
                }
            }
        }

    /** Genera el audio de [text] en [file]; false si el motor falla o no responde a tiempo. */
    private suspend fun synthesizeToFile(text: String, file: File): Boolean = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(true)
                }
                @Deprecated("Native callback")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && continuation.isActive) continuation.resume(false)
                }
            })
            continuation.invokeOnCancellation { tts.stop() }
            if (tts.synthesizeToFile(text, Bundle(), file, id) == TextToSpeech.ERROR && continuation.isActive)
                continuation.resume(false)
        }
    } ?: false

    /** Lee un WAV PCM de 16 bits mono (lo que generan los motores TTS); null si es otro formato. */
    private fun readPcm16Wav(file: File): DjVoiceClip? {
        val bytes = file.takeIf { it.isFile }?.readBytes() ?: return null
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        fun int16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
        fun int32(at: Int) = int16(at) or (int16(at + 2) shl 16)
        var offset = 12
        var sampleRate = 0
        var mono16 = false
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = int32(offset + 4)
            val body = offset + 8
            if (id == "fmt " && body + 16 <= bytes.size) {
                mono16 = int16(body) == 1 && int16(body + 2) == 1 && int16(body + 14) == 16
                sampleRate = int32(body + 4)
            } else if (id == "data") {
                if (!mono16 || sampleRate <= 0) return null
                val end = minOf(bytes.size, body + size).let { it - (it - body) % 2 }
                return DjVoiceClip(bytes.copyOfRange(body, end), sampleRate)
            }
            offset = body + size + (size and 1)
        }
        return null
    }

    actual suspend fun play(clip: DjVoiceClip): Unit = playPcm(clip, if (clip.boost) SYSTEM_VOICE_GAIN_MB else 0)

    /** Reproduce PCM de 16 bits; [gainMb] > 0 aplica un realce de sonoridad con limitador. */
    private suspend fun playPcm(clip: DjVoiceClip, gainMb: Int): Unit = withContext(Dispatchers.IO) {
        clipStopped.set(false)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(clip.sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(32 * 1024)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        // stop() corta también esta reproducción.
        clipTrack = track
        val enhancer = if (gainMb > 0) runCatching {
            LoudnessEnhancer(track.audioSessionId).apply { setTargetGain(gainMb); enabled = true }
        }.getOrNull() else null
        try {
            track.play()
            var offset = 0
            while (offset < clip.pcm16.size) {
                if (clipStopped.get() || !isActive) throw CancellationException("DJ voice playback stopped")
                val written = track.write(clip.pcm16, offset, minOf(16 * 1024, clip.pcm16.size - offset))
                if (written <= 0) {
                    if (clipStopped.get()) throw CancellationException("DJ voice playback stopped")
                    error("DJ voice playback failed ($written)")
                }
                offset += written
            }
            val frames = clip.pcm16.size / 2L
            while (track.playbackHeadPosition.toLong() < frames) {
                if (clipStopped.get() || !isActive) throw CancellationException("DJ voice playback stopped")
                delay(10)
            }
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        } finally {
            if (clipTrack === track) clipTrack = null
            enhancer?.release()
            if (track.state == AudioTrack.STATE_INITIALIZED) track.release()
        }
    }

    actual suspend fun systemVoices(language: String): List<DjSystemVoice> = withContext(Dispatchers.Main) {
        withTimeout(5_000) { ready.await() }
        val wanted = language.substringBefore('-').substringBefore('_').ifBlank { "es" }
        tts.voices.orEmpty()
            .filter { it.locale.language == wanted || it.locale.language == "es" }
            // Las voces sin datos descargados fallan al hablar: no se ofrecen.
            .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty() }
            .sortedWith(compareBy<android.speech.tts.Voice> { it.locale.toLanguageTag() }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
            .map { DjSystemVoice(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired) }
    }

    actual fun stop() {
        tts.stop()
        clipStopped.set(true)
        clipTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    track.stop()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Unable to stop DJ voice playback", e)
                }
            }
        }
    }

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

    private companion object {
        const val TAG = "NativeSpeech"
        /** +6 dB: la voz del sistema quedaba a ~70 % del volumen percibido de la música. */
        const val SYSTEM_VOICE_GAIN_MB = 600
        const val SYNTHESIS_TIMEOUT_MS = 20_000L
    }
}
