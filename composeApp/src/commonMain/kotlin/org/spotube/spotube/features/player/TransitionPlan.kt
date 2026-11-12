package org.spotube.spotube.features.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.Serializable

@Serializable
enum class CrossfadeCurve { EQUAL_POWER, LINEAR, SMOOTH }

/** Las curvas mantienen A al inicio y B al final; suave conserva amplitud, no potencia. */
fun crossfadeGains(progress: Float, curve: CrossfadeCurve): Pair<Float, Float> {
    val x = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    return when (curve) {
        CrossfadeCurve.EQUAL_POWER -> equalPower(x)
        CrossfadeCurve.LINEAR -> (1f - x) to x
        CrossfadeCurve.SMOOTH -> {
            val incoming = sin(x * PI / 2).toFloat().let { it * it }
            (1f - incoming) to incoming
        }
    }
}

/** Duración musical en 4/4. Sin rejilla válida se conserva el fundido de ocho segundos. */
fun mixDurationMillis(bpm: Float?, currentBeatOffsetMs: Long?, nextBeatOffsetMs: Long?, bars: Int): Long? {
    if (bpm == null || !bpm.isFinite() || bpm <= 0f ||
        currentBeatOffsetMs == null || currentBeatOffsetMs < 0 ||
        nextBeatOffsetMs == null || nextBeatOffsetMs < 0) return null
    val validBars = bars.takeIf { it in listOf(2, 4, 8) } ?: 4
    return (validBars * 4 * 60_000.0 / bpm).toLong().coerceAtLeast(1)
}

/** El reloj de la pista congela esta recuperación durante pausa o buffering. */
fun restoredTempoRatio(initialRatio: Float, elapsedMs: Long, originalBpm: Float): Float {
    if (!initialRatio.isFinite() || initialRatio <= 0f || !originalBpm.isFinite() || originalBpm <= 0f) return 1f
    val durationMs = 16 * 4 * 60_000.0 / originalBpm
    val progress = (elapsedMs / durationMs).coerceIn(0.0, 1.0).toFloat()
    return initialRatio + (1f - initialRatio) * progress
}

/** Curva de potencia constante; conserva el volumen maestro fuera de la mezcla. */
fun equalPower(progress: Float): Pair<Float, Float> {
    val angle = progress.coerceIn(0f, 1f) * PI / 2
    return cos(angle).toFloat() to sin(angle).toFloat()
}

data class TransitionPlan(val seconds: Int, val tempoRatio: Float = 1f, val beatMatching: Boolean = false) {
    companion object {
        fun create(crossfade: Int, autoMix: Boolean, playlist: Boolean,
                   currentBpm: Float?, nextBpm: Float?): TransitionPlan {
            val compatible = autoMix && playlist && currentBpm != null && nextBpm != null &&
                currentBpm.isFinite() && nextBpm.isFinite() && currentBpm > 0 && nextBpm > 0 &&
                kotlin.math.abs(currentBpm - nextBpm) < 10
            return if (compatible) TransitionPlan(8, currentBpm / nextBpm, beatMatching = true)
            else TransitionPlan(crossfade.coerceIn(0, 12))
        }
    }
}
