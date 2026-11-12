package org.spotube.spotube.features.dj

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.spotube.core.ui.theming.AppTheme
import spotube.composeapp.generated.resources.*

class DjViewModel(private val service: DjService) : ViewModel() {
    val uiState = service.uiState
    fun startNewSet() = service.startNewSet()
    fun dismissMessage() = service.dismissMessage()
}

@Composable
fun DjCard(viewModel: DjViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.loading && !state.speaking && state.message == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        .background(AppTheme.colors.surface, RoundedCornerShape(20.dp))
        .border(1.dp, AppTheme.colors.primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.loading) Text(stringResource(Res.string.dj_loading))
        state.message?.let { Text(it) }
        if (state.speaking) {
            Text(stringResource(Res.string.dj_speaking, state.name))
            state.script?.let { Text(it) }
            SpeakingWave()
        }
        if (state.message != null) MillenniumButton(onClick = viewModel::dismissMessage) {
            Text(stringResource(Res.string.dj_status_dismiss))
        }
    }
}

@Composable
private fun SpeakingWave() {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(0f, 6.283185f,
        infiniteRepeatable(tween(800, easing = LinearEasing)))
    val color = AppTheme.colors.primary
    Canvas(Modifier.width(140.dp).height(28.dp)) {
        repeat(20) { bar ->
            val x = size.width * bar / 20
            val height = (0.2f + kotlin.math.abs(kotlin.math.sin(phase + bar * 0.7f)) * 0.8f) * size.height / 2
            drawLine(color, Offset(x, size.height / 2 - height),
                Offset(x, size.height / 2 + height), strokeWidth = 3.dp.toPx())
        }
    }
}
