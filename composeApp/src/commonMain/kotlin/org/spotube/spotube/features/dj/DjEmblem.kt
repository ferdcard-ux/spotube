package org.spotube.spotube.features.dj

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.spotube.millennium.composables.MillenniumIcon
import org.spotube.spotube.resources.iconsax.Iconsax
import org.spotube.spotube.resources.iconsax.IconsaxMagic
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

/** Distintivo con el motor que está hablando: IA (Gemini) o voz del teléfono. */
@Composable
fun DjVoiceBadge(voice: DjActiveVoice, modifier: Modifier = Modifier) {
    val color = AppTheme.colors.primary
    val description = stringResource(when (voice) {
        DjActiveVoice.GEMINI -> Res.string.dj_voice_badge_gemini
        DjActiveVoice.DEVICE -> Res.string.dj_voice_badge_device
    })
    Box(modifier.size(18.dp).clip(CircleShape).background(AppTheme.colors.surface)
        .border(1.dp, color, CircleShape).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        when (voice) {
            DjActiveVoice.GEMINI -> MillenniumIcon(Iconsax.IconsaxMagic, contentDescription = null, tint = color,
                modifier = Modifier.size(12.dp))
            // Teléfono dibujado: el paquete de iconos no incluye uno.
            DjActiveVoice.DEVICE -> Canvas(Modifier.size(width = 7.dp, height = 11.dp)) {
                val stroke = 1.2.dp.toPx()
                drawRoundRect(color, cornerRadius = CornerRadius(1.5.dp.toPx()), style = Stroke(stroke))
                drawLine(color, Offset(size.width * 0.35f, size.height * 0.82f), Offset(size.width * 0.65f, size.height * 0.82f),
                    strokeWidth = stroke)
            }
        }
    }
}
