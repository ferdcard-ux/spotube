package org.spotube.spotube.features.dj

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumBottomNavigationItem
import org.spotube.spotube.core.ui.component.AdaptiveDialogBottomSheet
import spotube.composeapp.generated.resources.*

/** Entrada fija del DJ. El reproductor y su configuración se abren sobre la pantalla actual. */
@Composable
fun RowScope.DjNavigationControl(viewModel: DjViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MillenniumBottomNavigationItem(selected = state.enabled, onClick = viewModel::showPanel,
        contentDescription = stringResource(Res.string.dj_title, state.name),
        icon = { DjEmblem(state.enabled, state.speaking) }, label = {})
    if (state.panelVisible) {
        AdaptiveDialogBottomSheet(onDismiss = viewModel::hidePanel,
            dialogTitle = stringResource(Res.string.dj_title, state.name)) { DjCard(viewModel) }
    }
}
