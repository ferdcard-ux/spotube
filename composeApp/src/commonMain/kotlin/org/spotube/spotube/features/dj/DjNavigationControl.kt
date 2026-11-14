package org.spotube.spotube.features.dj

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumBottomNavigationItem
import spotube.composeapp.generated.resources.*

/** Acción fija de reproducción; toda la configuración permanece en Ajustes. */
@Composable
fun RowScope.DjNavigationControl(viewModel: DjViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val row = this
    // El distintivo del motor va en la esquina superior izquierda del botón, sin tapar el emblema.
    Box(Modifier.weight(1f)) {
        with(row) {
            MillenniumBottomNavigationItem(selected = state.enabled, onClick = viewModel::startNewSet,
                modifier = Modifier.fillMaxWidth(),
                contentDescription = stringResource(when {
                    !state.enabled -> Res.string.dj_enable_in_settings
                    state.loading -> Res.string.dj_loading
                    state.speaking -> Res.string.dj_speaking
                    state.playing -> Res.string.dj_set_next
                    else -> Res.string.dj_start
                }, state.name),
                icon = { DjEmblem(state.playing || state.loading, state.speaking) }, label = {})
        }
        if (state.enabled) DjVoiceBadge(state.activeVoice, Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}
