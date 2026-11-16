package org.spotube.spotube.features.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.Serializable

@Serializable
enum class CrossfadeCurve { EQUAL_POWER, LINEAR, SMOOTH }

fun crossfadeGains(progress: Float, curve: CrossfadeCurve): Pair<Float, Float> {
    val x = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    return when (curve) {
        CrossfadeCurve.EQUAL_POWER -> {
            val angle = x * PI / 2
            cos(angle).toFloat() to sin(angle).toFloat()
        }
        CrossfadeCurve.LINEAR -> (1f - x) to x
        CrossfadeCurve.SMOOTH -> {
            val incoming = sin(x * PI / 2).toFloat().let { it * it }
            (1f - incoming) to incoming
        }
    }
}
