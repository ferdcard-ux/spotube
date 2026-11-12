package org.spotube.spotube.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.composeunstyled.LocalTextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.spotube.millennium.composables.*

/** Aro metálico dibujado por Compose: funciona en cualquier densidad sin bitmaps. */
@Composable
fun ChromeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    style: MillenniumIconButtonStyle = MillenniumIconButtonStyle.Quiet,
    content: @Composable () -> Unit,
) {
    val highlighted = style == MillenniumIconButtonStyle.Primary || style == MillenniumIconButtonStyle.Secondary
    val ring = if (highlighted) listOf(Color(0xFF31DD78), Color(0xFFB0FFD1), Color(0xFF12A759), Color(0xFF31DD78))
        else listOf(Color(0xFF5A5A60), Color(0xFFF4F4F6), Color(0xFF343439), Color(0xFFD0D0D6), Color(0xFF5A5A60))
    MillenniumIconButton(onClick = onClick, enabled = enabled, loading = loading,
        style = MillenniumIconButtonStyle.Quiet,
        modifier = modifier.size(if (style == MillenniumIconButtonStyle.Primary) 60.dp else 48.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient(listOf(Color(0xFF19191D), Color(0xFF626268), Color(0xFF1B1B20), Color(0xFF45454C), Color(0xFF19191D))))
            .border(if (highlighted) 3.dp else 1.5.dp, Brush.sweepGradient(ring), CircleShape),
        content = {
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(color = Color.White)) { content() }
        })
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
