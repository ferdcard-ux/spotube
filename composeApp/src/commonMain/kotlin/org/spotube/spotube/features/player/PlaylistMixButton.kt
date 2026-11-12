package org.spotube.spotube.features.player

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import compose.icons.FeatherIcons
import compose.icons.feathericons.Sliders
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.spotube.millennium.composables.MillenniumIcon
import spotube.composeapp.generated.resources.*

@Composable
fun PlaylistMixButton(playlistId: String,
    viewModel: PlaylistMixViewModel = koinViewModel(key = "mix:$playlistId", parameters = { parametersOf(playlistId) })) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MixCapsuleButton(onClick = viewModel::toggle, enabled = state.available, selected = state.enabled) {
        MillenniumIcon(FeatherIcons.Sliders, contentDescription = null)
        Text(stringResource(when { state.playing -> Res.string.mix_action_playing; state.enabled -> Res.string.mix_action_enabled; else -> Res.string.mix_action }))
    }
}
