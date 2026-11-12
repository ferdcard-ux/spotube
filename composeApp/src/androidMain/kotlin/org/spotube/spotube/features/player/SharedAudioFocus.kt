package org.spotube.spotube.features.player

import android.content.Context
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer

/** Un único foco para ambos motores: arrancar B no debe pausar A durante el fundido. */
@Suppress("DEPRECATION")
internal object SharedAudioFocus {
    private var manager: AudioManager? = null
    private var held = false
    private val playing = mutableSetOf<ExoPlayer>()
    private var interrupted = emptySet<ExoPlayer>()
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                held = true
                interrupted.forEach { if (it in playing) it.play() }
                interrupted = emptySet()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                interrupted = playing.filter { it.playWhenReady }.toSet()
                interrupted.forEach { it.pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                playing.forEach { it.pause() }
                playing.clear()
                interrupted = emptySet()
                held = false
            }
        }
    }

    fun acquire(context: Context, player: ExoPlayer): Boolean {
        val audioManager = manager ?: (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .also { manager = it }
        if (!held) {
            held = audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (held) playing.add(player)
        return held
    }

    fun release(player: ExoPlayer) {
        playing.remove(player)
        interrupted = interrupted - player
        if (playing.isEmpty() && held) {
            manager?.abandonAudioFocus(listener)
            held = false
        }
    }
}
