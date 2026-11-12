package org.spotube.spotube.features.player

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import org.spotube.spotube.core.audioplayer.*
import org.spotube.spotube.core.db.Database
import org.spotube.spotube.features.dj.DjService
import org.spotube.spotube.features.dj.NativeSpeech
import org.spotube.spotube.modules.settings.SettingsRepository
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Pruebas en hardware sin runner ni bibliotecas de terceros adicionales. */
class PlaybackDeviceInstrumentation : Instrumentation() {
    private var selected: String? = null
    private val results = JSONArray()
    private val koin get() = GlobalContext.get()
    private val player get() = koin.get<AudioPlayerInterface>() as TransitionAudioPlayer
    private val settings get() = koin.get<SettingsRepository>()
    private lateinit var firstFile: File
    private lateinit var secondFile: File

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        selected = arguments?.getString("case")
        start()
    }

    override fun onStart() {
        val summary = Bundle()
        var passed = true
        try {
            check(targetContext.packageName.endsWith(".nightly")) {
                "Las pruebas de dispositivo solo se ejecutan en la variante Nightly"
            }
            startActivitySync(Intent().setClassName(targetContext.packageName,
                "org.spotube.spotube.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            runBlocking {
                val original = settings.getCurrentSettings()
                val queue = koin.get<AudioPlayerQueue>()
                check(queue.getQueue().isEmpty()) { "Vacía la cola de Nightly antes de ejecutar las pruebas" }
                val database = koin.get<Database>()
                val historyKey = stringPreferencesKey("dj_listening_history")
                val originalHistory = database.audioPlayerQueueDataStore.data.first()[historyKey]
                val originalVolume = player.volumeFlow.value
                firstFile = fixture("transition-device-a.wav", 220.0)
                secondFile = fixture("transition-device-b.wav", 330.0)
                try {
                    for ((name, test) in listOf<Pair<String, suspend () -> Unit>>(
                        "gapless" to { gapless() }, "crossfade" to { crossfade() },
                        "automix" to { automix() }, "dj" to { dj() })) {
                        if (selected != null && selected != name) continue
                        val started = SystemClock.elapsedRealtime()
                        val result = JSONObject().put("case", name)
                        try {
                            withTimeout(45_000) { test() }
                            result.put("passed", true)
                        } catch (error: Throwable) {
                            passed = false
                            result.put("passed", false).put("error", error.stackTraceToString())
                        } finally {
                            player.stop()
                        }
                        result.put("durationMs", SystemClock.elapsedRealtime() - started)
                        results.put(result)
                        File(targetContext.filesDir, "playback-device-results.json").writeText(results.toString(2))
                        sendStatus(if (result.optBoolean("passed")) 0 else -1,
                            Bundle().apply { putString("stream", result.toString() + "\n") })
                    }
                } finally {
                    player.stop()
                    player.playlistContext = false
                    queue.clear()
                    player.setVolume(originalVolume)
                    settings.updateSettings(original)
                    database.audioPlayerQueueDataStore.edit { preferences ->
                        if (originalHistory == null) preferences.remove(historyKey)
                        else preferences[historyKey] = originalHistory
                    }
                    firstFile.delete()
                    secondFile.delete()
                }
            }
        } catch (error: Throwable) {
            passed = false
            summary.putString("error", error.stackTraceToString())
        }
        summary.putString("stream", results.toString(2))
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, summary)
    }

    private suspend fun configure(crossfade: Int, autoMix: Boolean = false, dj: Boolean = false) {
        player.stop()
        val next = settings.getCurrentSettings().copy(gaplessEnabled = true,
            crossfadeSeconds = crossfade, autoMixEnabled = autoMix, djMode = dj,
            enableEndlessPlayback = false)
        settings.updateSettings(next)
        withTimeout(5_000) { settings.userSettings.first { it == next } }
        koin.get<DjService>().uiState.first { it.enabled == dj }
        player.setVolume(0.15f)
    }

    private fun track(file: File, bpm: Float? = null) = MediaItem(
        title = if (file == firstFile) "Prueba de audio A" else "Prueba de audio B",
        artist = "SpoTube", album = "Pruebas en dispositivo", duration = 24.seconds,
        coverURL = "", url = file.absolutePath, bpm = bpm, beatOffsetMs = 0)

    private fun standby(): AudioPlayerInterface? = TransitionAudioPlayer::class.java
        .getDeclaredField("standby").apply { isAccessible = true }.get(player) as? AudioPlayerInterface

    private suspend fun awaitDeck(): AudioPlayerInterface = withTimeout(5_000) {
        while (standby() == null) delay(20)
        standby()!!.also { deck -> deck.playerStateFlow.first {
            it == PlayerState.READY || it == PlayerState.PAUSED
        } }
    }

    private suspend fun gapless() {
        configure(0)
        val first = track(firstFile)
        val second = track(secondFile)
        player.load(listOf(first, second), true, 0)
        player.playerStateFlow.first { it == PlayerState.PLAYING }
        player.seekTo(19.seconds)
        val buffered = awaitDeck()
        check(buffered.currentMediaItemFlow.value == second) { "La siguiente canción no se precargó" }
        check(buffered.positionFlow.value == 0.seconds) { "El búfer avanzó antes de reproducirse" }
        check(buffered.playerStateFlow.value != PlayerState.PLAYING) { "Precarga audible" }
        player.currentMediaItemFlow.first { it == second }
        check(player.activeDeck.value === buffered) { "La transición recargó otro motor" }
        check(buffered.positionFlow.value < 500.milliseconds) { "La siguiente no comenzó desde cero" }
        buffered.playerStateFlow.first { it == PlayerState.PLAYING }
    }

    private suspend fun crossfade() {
        configure(6)
        val first = track(firstFile)
        val second = track(secondFile)
        player.load(listOf(first, second), true, 0)
        player.playerStateFlow.first { it == PlayerState.PLAYING }
        val outgoing = player.activeDeck.value
        player.seekTo(17.seconds)
        val incoming = awaitDeck()
        incoming.playerStateFlow.first { it == PlayerState.PLAYING }
        incoming.positionFlow.first { it > 700.milliseconds }
        check(outgoing.playerStateFlow.value == PlayerState.PLAYING) { "Arrancar B pausó A" }
        val a = outgoing.volumeFlow.value
        val b = incoming.volumeFlow.value
        check(a > 0f && b > 0f && a < 0.15f && b < 0.15f) { "No hay fundido simultáneo: A=$a B=$b" }
        check(abs(a * a + b * b - 0.15f * 0.15f) < 0.002f) { "Potencia de mezcla incorrecta" }
        // Los mismos comandos que usa la notificación deben controlar los dos motores.
        uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
        try {
            val sessions = targetContext.getSystemService(MediaSessionManager::class.java)
            val controller = withTimeout(5_000) {
                var current = sessions.getActiveSessions(null).firstOrNull { it.packageName == targetContext.packageName }
                while (current == null) {
                    delay(50)
                    current = sessions.getActiveSessions(null).firstOrNull { it.packageName == targetContext.packageName }
                }
                current
            }
            controller.transportControls.pause()
            outgoing.playerStateFlow.first { it == PlayerState.PAUSED }
            incoming.playerStateFlow.first { it == PlayerState.PAUSED }
            delay(300)
            val aPosition = outgoing.positionFlow.value
            val bPosition = incoming.positionFlow.value
            delay(350)
            // Se admite la actualización final del reloj nativo tras recibir la pausa.
            check((outgoing.positionFlow.value - aPosition).absoluteValue <= 50.milliseconds &&
                (incoming.positionFlow.value - bPosition).absoluteValue <= 50.milliseconds) {
                "Pausa: A=$aPosition -> ${outgoing.positionFlow.value} (${outgoing.playerStateFlow.value}), " +
                    "B=$bPosition -> ${incoming.positionFlow.value} (${incoming.playerStateFlow.value})"
            }
            val settledA = outgoing.positionFlow.value
            val settledB = incoming.positionFlow.value
            delay(500)
            check((outgoing.positionFlow.value - settledA).absoluteValue <= 5.milliseconds &&
                (incoming.positionFlow.value - settledB).absoluteValue <= 5.milliseconds &&
                outgoing.playerStateFlow.value == PlayerState.PAUSED &&
                incoming.playerStateFlow.value == PlayerState.PAUSED) { "Los relojes no se estabilizaron en pausa" }
            controller.transportControls.play()
            incoming.playerStateFlow.first { it == PlayerState.PLAYING }
            controller.transportControls.skipToNext()
            player.currentMediaItemFlow.first { it == second }
            player.playerStateFlow.first { it == PlayerState.PLAYING }
            controller.transportControls.seekTo(5_000)
            player.positionFlow.first { it >= 5.seconds && it < 6.seconds }
            check(player.currentMediaItemFlow.value == second) { "Buscar cambió de canción" }
            controller.transportControls.skipToPrevious()
            player.currentMediaItemFlow.first { it == first }
        } finally { uiAutomation.dropShellPermissionIdentity() }
    }

    private suspend fun automix() {
        configure(2, autoMix = true)
        player.playlistContext = true
        player.load(listOf(track(firstFile, 105f), track(secondFile, 100f)), true, 0)
        player.playerStateFlow.first { it == PlayerState.PLAYING }
        player.seekTo(15.seconds)
        val incoming = awaitDeck()
        incoming.playerStateFlow.first { it == PlayerState.PLAYING }
        check(abs(incoming.playbackSpeedFlow.value - 1.05f) < 0.001f) { "Tempo no igualado" }
        check(player.activeDeck.value.positionFlow.value < 19.seconds) { "No se aplicó la ventana de ocho segundos" }
        player.stop()
        player.load(listOf(track(firstFile, 100f), track(secondFile, 120f)), true, 0)
        player.playerStateFlow.first { it == PlayerState.PLAYING }
        player.seekTo(21.seconds)
        val fallback = awaitDeck()
        fallback.playerStateFlow.first { it == PlayerState.PLAYING }
        check(fallback.playbackSpeedFlow.value == 1f) { "Se alteró el tempo con BPM incompatibles" }
        check(player.activeDeck.value.positionFlow.value >= 22.seconds) { "No se respetó el fundido configurado" }
    }

    private suspend fun dj() {
        configure(6, dj = true)
        // Se comprueba el motor instalado y después el flujo real de presentación de DjService.
        withTimeout(15_000) { koin.get<NativeSpeech>().speak("Prueba de voz de DJ Livi.", "es") }
        val service = koin.get<DjService>()
        player.load(listOf(track(firstFile)), true, 0)
        service.uiState.first { it.speaking }
        check(player.activeDeck.value.playerStateFlow.value != PlayerState.PLAYING) {
            "La música empezó antes de terminar la voz"
        }
        check(!service.uiState.value.script.isNullOrBlank()) { "Guion vacío" }
        service.uiState.first { !it.speaking }
        player.playerStateFlow.first { it == PlayerState.PLAYING }
        // Una segunda presentación permite comprobar la cancelación desde pause.
        player.load(listOf(track(secondFile)), true, 0)
        service.uiState.first { it.speaking }
        player.pause()
        service.uiState.first { !it.speaking }
        delay(300)
        check(player.activeDeck.value.playerStateFlow.value != PlayerState.PLAYING) { "La voz reanudó la música tras pausa" }
        player.play()
        player.playerStateFlow.first { it == PlayerState.PLAYING }
    }

    private fun fixture(name: String, frequency: Double): File {
        val samples = 44_100 * 24
        val bytes = samples * 2
        val wav = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(44_100).putInt(88_200)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes)
        repeat(samples) { index ->
            wav.putShort((sin(2 * PI * frequency * index / 44_100) * 4_000).toInt().toShort())
        }
        return File(targetContext.filesDir, name).apply { writeBytes(wav.array()) }
    }
}
