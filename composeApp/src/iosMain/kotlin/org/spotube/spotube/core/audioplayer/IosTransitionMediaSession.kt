package org.spotube.spotube.core.audioplayer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Una sesión de iOS controla ambos motores, incluidos pausa y cambio desde la pantalla bloqueada. */
internal class IosTransitionMediaSession(private val player: AudioPlayerInterface) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val session = IosMediaSession()
    private var active = false

    init {
        session.configure(
            onPlay = { scope.launch { player.play() } },
            onPause = { scope.launch { player.pause() } },
            onSeek = { position -> scope.launch { player.seekTo(position.seconds) } },
            onNext = { scope.launch { player.skipToNext() } },
            onPrevious = { scope.launch { player.skipToPrevious() } })
        scope.launch {
            combine(player.currentMediaItemFlow, player.positionFlow, player.durationFlow,
                player.playerStateFlow) { item, position, duration, state ->
                if (state == PlayerState.PLAYING && !active) {
                    session.activate()
                    active = true
                } else if (state == PlayerState.IDLE && active) {
                    session.deactivate()
                    active = false
                }
                session.update(item, position.inWholeMilliseconds / 1_000.0,
                    duration.inWholeMilliseconds / 1_000.0, state == PlayerState.PLAYING)
            }.collect { }
        }
    }

    fun close() {
        scope.cancel()
        session.clear()
        if (active) session.deactivate()
    }
}
