package org.spotube.spotube.features.dj

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.millennium.composables.MillenniumButtonStyle
import org.spotube.spotube.core.ui.theming.AppTheme
import spotube.composeapp.generated.resources.*

data class DjMemoryUiState(
    val artists: String = "",
    val genres: String = "",
    val listened: Int = 0,
)

/** Muestra lo que el DJ aprendió del historial local y permite olvidarlo. */
class DjMemoryViewModel(private val history: DjHistory) : ViewModel() {
    val uiState: StateFlow<DjMemoryUiState> = history.entries.map { entries ->
        val profile = DjTasteProfile.from(entries)
        DjMemoryUiState(profile.topArtists.joinToString(), profile.topGenres.joinToString(), entries.size)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DjMemoryUiState())

    fun forget() { viewModelScope.launch { history.clear() } }
}

@Composable
fun DjMemoryConfiguration(viewModel: DjMemoryViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.dj_memory_learned_title))
        if (state.listened == 0) {
            Text(stringResource(Res.string.dj_memory_learned_empty), style = AppTheme.typography.bodySmall)
        } else {
            Text(stringResource(Res.string.dj_memory_learned_count, state.listened), style = AppTheme.typography.bodySmall)
            if (state.artists.isNotBlank()) Text(stringResource(Res.string.dj_memory_learned_artists, state.artists), style = AppTheme.typography.bodySmall)
            if (state.genres.isNotBlank()) Text(stringResource(Res.string.dj_memory_learned_genres, state.genres), style = AppTheme.typography.bodySmall)
            MillenniumButton(onClick = viewModel::forget, style = MillenniumButtonStyle.Secondary) {
                Text(stringResource(Res.string.dj_memory_forget))
            }
        }
    }
}
