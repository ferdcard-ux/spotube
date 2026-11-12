package org.spotube.spotube.features.dj

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import org.spotube.millennium.composables.MillenniumSwitch
import org.spotube.spotube.core.ui.theming.AppTheme
import org.spotube.spotube.core.ui.TvTextFieldGate
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.millennium.composables.MillenniumButtonStyle
import spotube.composeapp.generated.resources.*

@Composable
fun DjAiConfiguration(viewModel: DjAiViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.dj_ai_title))
            MillenniumSwitch(checked = state.enabled, onCheckedChange = viewModel::enable, contentSpacing = 0.dp)
        }
        Text(stringResource(Res.string.dj_ai_disclosure))
        if (state.enabled) {
            DjAiProvider.entries.forEach { provider ->
                MillenniumButton(onClick = { viewModel.provider(provider) },
                    style = if (state.provider == provider) MillenniumButtonStyle.Primary else MillenniumButtonStyle.Secondary) {
                    Text(stringResource(when (provider) {
                        DjAiProvider.OPENROUTER -> Res.string.dj_ai_openrouter
                        DjAiProvider.GEMINI -> Res.string.dj_ai_gemini
                        DjAiProvider.GROQ -> Res.string.dj_ai_groq
                    }))
                }
            }
            DjAiTextField(state.model, viewModel::model, stringResource(Res.string.dj_ai_model))
            DjAiTextField(state.apiKey, viewModel::key, stringResource(Res.string.dj_ai_key), password = true)
            DjAiTextField(state.request, viewModel::request, stringResource(Res.string.dj_ai_request))
            Text(stringResource(Res.string.dj_ai_session_only))
            Text(stringResource(when (state.status) {
                DjAiStatus.IDLE -> Res.string.dj_ai_idle
                DjAiStatus.PLANNING -> Res.string.dj_ai_planning
                DjAiStatus.READY -> Res.string.dj_ai_ready
                DjAiStatus.LOCAL_FALLBACK -> Res.string.dj_ai_fallback
            }))
            MillenniumButton(onClick = viewModel::clear, style = MillenniumButtonStyle.Secondary) {
                Text(stringResource(Res.string.dj_ai_clear))
            }
        }
    }
}

@Composable
private fun DjAiTextField(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)
        TvTextFieldGate { fieldModifier ->
            BasicTextField(value = value, onValueChange = onChange, singleLine = true,
                modifier = fieldModifier.fillMaxWidth().border(1.dp, AppTheme.colors.outline, RoundedCornerShape(12.dp))
                    .padding(14.dp).semantics { contentDescription = label },
                textStyle = AppTheme.typography.bodyMedium.copy(color = AppTheme.colors.onSurface),
                cursorBrush = SolidColor(AppTheme.colors.primary),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default)
        }
    }
}
