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
            state.failure?.let { failure ->
                Text(stringResource(when (failure) {
                    DjAiFailure.CONFIGURATION -> Res.string.dj_ai_error_configuration
                    DjAiFailure.NO_CANDIDATES -> Res.string.dj_ai_error_candidates
                    DjAiFailure.FREE_MODEL_REQUIRED -> Res.string.dj_ai_error_free_model
                    DjAiFailure.AUTHENTICATION -> Res.string.dj_ai_error_authentication
                    DjAiFailure.PERMISSION -> Res.string.dj_ai_error_permission
                    DjAiFailure.ACCOUNT_LIMIT -> Res.string.dj_ai_error_account
                    DjAiFailure.RATE_LIMIT -> Res.string.dj_ai_error_rate_limit
                    DjAiFailure.MODEL_UNAVAILABLE -> Res.string.dj_ai_error_model
                    DjAiFailure.REQUEST_REJECTED -> Res.string.dj_ai_error_request
                    DjAiFailure.PROVIDER_UNAVAILABLE -> Res.string.dj_ai_error_provider
                    DjAiFailure.TIMEOUT -> Res.string.dj_ai_error_timeout
                    DjAiFailure.NETWORK -> Res.string.dj_ai_error_network
                    DjAiFailure.EMPTY_RESPONSE -> Res.string.dj_ai_error_empty
                    DjAiFailure.OUTPUT_LIMIT -> Res.string.dj_ai_error_output_limit
                    DjAiFailure.INVALID_RESPONSE -> Res.string.dj_ai_error_response
                    DjAiFailure.INVALID_PLAN -> Res.string.dj_ai_error_plan
                }))
            }
            state.httpStatus?.let { Text(stringResource(Res.string.dj_ai_http_status, it)) }
            MillenniumButton(onClick = viewModel::retry, enabled = state.canRetry) {
                Text(stringResource(Res.string.dj_ai_retry))
            }
            Text(stringResource(Res.string.dj_ai_retry_hint))
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
