package org.spotube.spotube.features.dj

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumBottomNavigationItem
import spotube.composeapp.generated.resources.*

/** Acción fija de reproducción; toda la configuración permanece en Ajustes. */
@Composable
fun RowScope.DjNavigationControl(viewModel: DjViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MillenniumBottomNavigationItem(selected = state.enabled, onClick = viewModel::startNewSet,
        contentDescription = stringResource(when {
            !state.enabled -> Res.string.dj_enable_in_settings
            state.loading -> Res.string.dj_loading
            state.speaking -> Res.string.dj_speaking
            state.playing -> Res.string.dj_set_next
            else -> Res.string.dj_start
        }, state.name),
        icon = { DjEmblem(state.playing || state.loading, state.speaking) }, label = {})
}
