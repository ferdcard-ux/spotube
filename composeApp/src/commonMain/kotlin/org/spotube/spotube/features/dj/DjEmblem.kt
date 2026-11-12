package org.spotube.spotube.features.dj

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import org.jetbrains.compose.resources.stringResource
import org.spotube.spotube.core.ui.theming.AppTheme
import spotube.composeapp.generated.resources.*

/** Ondas concéntricas nativas; el control conserva su texto accesible en la barra. */
@Composable
fun DjEmblem(active: Boolean, speaking: Boolean) {
    val animation = rememberInfiniteTransition()
    val pulse by animation.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
    val color = AppTheme.colors.primary
    Box(Modifier.requiredSize(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(3) { ring ->
                drawCircle(color.copy(alpha = if (active || speaking) pulse else 0.55f),
                    radius = size.minDimension * (0.32f + ring * 0.08f), style = Stroke(1.5.dp.toPx()))
            }
        }
        Text(stringResource(Res.string.dj_emblem_text), style = AppTheme.typography.labelSmall)
    }
}
