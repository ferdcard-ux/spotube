package org.spotube.spotube.features.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
