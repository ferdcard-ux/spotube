package org.spotube.spotube.features.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.composeunstyled.LocalTextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.spotube.millennium.composables.*

/**
 * Aro metálico dibujado por Compose: funciona en cualquier densidad sin bitmaps.
 *
 * Responde al toque (se hunde y se ilumina mientras se presiona), muestra el foco del D-pad y,
 * con [active], un aro verde que refleja el estado (aleatorio, repetición, reproduciendo…).
 */
@Composable
fun ChromeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    active: Boolean = false,
    style: MillenniumIconButtonStyle = MillenniumIconButtonStyle.Quiet,
    /** Diámetro; por defecto 60 dp (principal) o 48 dp. */
    size: androidx.compose.ui.unit.Dp? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val highlighted = active || style == MillenniumIconButtonStyle.Primary || style == MillenniumIconButtonStyle.Secondary
    val ring = if (highlighted) listOf(Color(0xFF31DD78), Color(0xFFB0FFD1), Color(0xFF12A759), Color(0xFF31DD78))
        else listOf(Color(0xFF5A5A60), Color(0xFFF4F4F6), Color(0xFF343439), Color(0xFFD0D0D6), Color(0xFF5A5A60))
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
    val glow by animateFloatAsState(if (pressed) 1f else if (active) 0.35f else 0f, tween(120))
    val glowColor = if (highlighted) Color(0xFF31DD78) else Color.White
    Box(
        modifier = modifier
            .size(size ?: if (style == MillenniumIconButtonStyle.Primary) 60.dp else 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape)
            .background(Brush.sweepGradient(listOf(Color(0xFF19191D), Color(0xFF626268), Color(0xFF1B1B20), Color(0xFF45454C), Color(0xFF19191D))))
            .drawWithContent {
                drawContent()
                if (glow > 0f) drawCircle(glowColor.copy(alpha = 0.28f * glow))
            }
            .border(if (focused) 3.dp else if (highlighted) 3.dp else 1.5.dp,
                if (focused) SolidColor(Color.White) else Brush.sweepGradient(ring), CircleShape)
            // Mientras carga sigue siendo enfocable (D-pad de TV) pero ignora los toques.
            .clickable(interactionSource = interaction, indication = null,
                enabled = enabled, role = Role.Button, onClick = { if (!loading) onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) MillenniumSpokeSpinner(size = minOf(24.dp, (size ?: 48.dp) * 0.6f))
        else CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(color = Color.White)) { content() }
    }
}

/** Cápsula de Mix inspirada en el aro cromado de la referencia, con estado seleccionable. */
@Composable
fun MixCapsuleButton(onClick: () -> Unit, enabled: Boolean, selected: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    MillenniumButton(onClick = onClick, enabled = enabled,
        style = MillenniumButtonStyle.Secondary,
        modifier = Modifier.clip(shape).border(2.dp,
            Brush.linearGradient(if (selected) listOf(Color(0xFF31DD78), Color.White, Color(0xFF15944F))
                else listOf(Color(0xFF77777F), Color.White, Color(0xFF333339))), shape).padding(3.dp),
        content = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() } })
}
