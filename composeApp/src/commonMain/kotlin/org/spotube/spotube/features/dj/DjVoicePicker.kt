package org.spotube.spotube.features.dj

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.spotube.millennium.composables.MillenniumButton
import org.spotube.spotube.core.i18n.getString
import org.spotube.spotube.modules.settings.DjSpeechEngine
import org.spotube.spotube.modules.settings.DjVoiceGender
import org.spotube.spotube.modules.settings.SettingsRepository
import org.spotube.spotube.modules.settings.components.SelectionSettingCard
import org.spotube.spotube.modules.settings.components.SettingCardItem
import spotube.composeapp.generated.resources.*

/** Una opción del selector de voz. [id] es lo que se guarda en `djVoiceNames` ("" = automática). */
sealed interface DjVoiceChoice {
    val id: String
    data object Auto : DjVoiceChoice { override val id = "" }
    data class Gemini(val voice: GeminiVoice) : DjVoiceChoice { override val id get() = voice.name }
    data class System(val voice: DjSystemVoice) : DjVoiceChoice { override val id get() = voice.id }
}

enum class DjVoicePreview { IDLE, PLAYING, FAILED }

data class DjVoicePickerUiState(
    val engine: DjSpeechEngine = DjSpeechEngine.SYSTEM,
    val options: List<DjVoiceChoice> = listOf(DjVoiceChoice.Auto),
    val selected: DjVoiceChoice = DjVoiceChoice.Auto,
    val preview: DjVoicePreview = DjVoicePreview.IDLE,
)

/** Lista las voces del motor elegido, guarda la elegida por motor y reproduce una muestra. */
class DjVoiceViewModel(
    private val settings: SettingsRepository,
    private val speech: NativeSpeech,
    private val gemini: GeminiDjVoice,
) : ViewModel() {
    private val systemVoices = MutableStateFlow<List<DjSystemVoice>>(emptyList())
    private val preview = MutableStateFlow(DjVoicePreview.IDLE)
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            systemVoices.value = try { speech.systemVoices(settings.userSettings.value.language.locale) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
        }
    }

    val uiState: StateFlow<DjVoicePickerUiState> = combine(settings.userSettings, systemVoices, preview) { prefs, installed, previewing ->
        val engine = prefs.djSpeechEngine
        val options = listOf(DjVoiceChoice.Auto) + when (engine) {
            DjSpeechEngine.GEMINI_CLOUD -> GEMINI_VOICES.map(DjVoiceChoice::Gemini)
            DjSpeechEngine.SYSTEM -> installed.map(DjVoiceChoice::System)
        }
        val saved = prefs.djVoiceNames[engine.name].orEmpty()
        DjVoicePickerUiState(engine, options, options.firstOrNull { it.id == saved } ?: DjVoiceChoice.Auto, previewing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DjVoicePickerUiState())

    fun select(choice: DjVoiceChoice) {
        viewModelScope.launch {
            settings.mutateSettings { it.copy(djVoiceNames = it.djVoiceNames + (it.djSpeechEngine.name to choice.id)) }
        }
    }

    fun preview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            preview.value = DjVoicePreview.PLAYING
            val prefs = settings.userSettings.value
            val name = normalizeDjName(prefs.djName)
            val text = getString(Res.string.dj_voice_preview_text, name)
            val options = prefs.djSpeechOptions()
            preview.value = try {
                withTimeout(60_000) {
                    if (prefs.djSpeechEngine == DjSpeechEngine.GEMINI_CLOUD) {
                        when (val result = gemini.synthesize(text, name, options)) {
                            is DjVoiceResult.Ready -> speech.play(result.clip)
                            is DjVoiceResult.Failed -> error("Gemini preview failed: ${result.reason}")
                        }
                    } else {
                        // La muestra suena igual que en un set: generada y con el mismo realce.
                        speech.synthesize(text, prefs.language.locale, options)?.let { speech.play(it) }
                            ?: speech.speak(text, prefs.language.locale, options)
                    }
                }
                DjVoicePreview.IDLE
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { DjVoicePreview.FAILED }
            finally { speech.stop() }
        }
    }

    override fun onCleared() { previewJob?.cancel(); speech.stop() }
}

private fun GeminiVoiceStyle.label(): StringResource = when (this) {
    GeminiVoiceStyle.BRIGHT -> Res.string.dj_voice_style_bright
    GeminiVoiceStyle.UPBEAT -> Res.string.dj_voice_style_upbeat
    GeminiVoiceStyle.INFORMATIVE -> Res.string.dj_voice_style_informative
    GeminiVoiceStyle.FIRM -> Res.string.dj_voice_style_firm
    GeminiVoiceStyle.EXCITABLE -> Res.string.dj_voice_style_excitable
    GeminiVoiceStyle.YOUTHFUL -> Res.string.dj_voice_style_youthful
    GeminiVoiceStyle.BREEZY -> Res.string.dj_voice_style_breezy
    GeminiVoiceStyle.EASY_GOING -> Res.string.dj_voice_style_easy_going
    GeminiVoiceStyle.BREATHY -> Res.string.dj_voice_style_breathy
    GeminiVoiceStyle.CLEAR -> Res.string.dj_voice_style_clear
    GeminiVoiceStyle.SMOOTH -> Res.string.dj_voice_style_smooth
    GeminiVoiceStyle.GRAVELLY -> Res.string.dj_voice_style_gravelly
    GeminiVoiceStyle.SOFT -> Res.string.dj_voice_style_soft
    GeminiVoiceStyle.EVEN -> Res.string.dj_voice_style_even
    GeminiVoiceStyle.MATURE -> Res.string.dj_voice_style_mature
    GeminiVoiceStyle.FORWARD -> Res.string.dj_voice_style_forward
    GeminiVoiceStyle.FRIENDLY -> Res.string.dj_voice_style_friendly
    GeminiVoiceStyle.CASUAL -> Res.string.dj_voice_style_casual
    GeminiVoiceStyle.GENTLE -> Res.string.dj_voice_style_gentle
    GeminiVoiceStyle.LIVELY -> Res.string.dj_voice_style_lively
    GeminiVoiceStyle.KNOWLEDGEABLE -> Res.string.dj_voice_style_knowledgeable
    GeminiVoiceStyle.WARM -> Res.string.dj_voice_style_warm
}

@Composable
private fun DjVoiceChoice.label(): String = when (this) {
    DjVoiceChoice.Auto -> stringResource(Res.string.dj_voice_auto_choice)
    is DjVoiceChoice.Gemini -> stringResource(Res.string.dj_voice_gemini_label, voice.name, stringResource(voice.style.label()),
        stringResource(if (voice.gender == DjVoiceGender.FEMALE) Res.string.dj_voice_female else Res.string.dj_voice_male))
    // Android nombra sus voces como «es-us-x-esf-local»: se muestra el idioma y el código corto.
    is DjVoiceChoice.System -> stringResource(Res.string.dj_voice_system_label, voice.languageTag,
        voice.id.substringAfter("-x-", voice.id).substringBefore('-'),
        stringResource(if (voice.needsNetwork) Res.string.dj_voice_online else Res.string.dj_voice_offline))
}

@Composable
fun DjVoicePicker(viewModel: DjVoiceViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = stringResource(Res.string.dj_voice_name_title)
    if (state.options.size > 1) {
        SelectionSettingCard(
            title = title,
            selectedOption = state.selected,
            options = state.options,
            optionLabel = { it.label() },
            onOptionSelected = viewModel::select)
    } else {
        SettingCardItem(title = title, subtitle = stringResource(Res.string.dj_voice_name_none))
    }
}

@Composable
fun DjVoicePreviewCard(viewModel: DjVoiceViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingCardItem(
        title = stringResource(Res.string.dj_voice_preview_title),
        subtitle = stringResource(when (state.preview) {
            DjVoicePreview.PLAYING -> Res.string.dj_voice_preview_playing
            DjVoicePreview.FAILED -> Res.string.dj_voice_preview_failed
            DjVoicePreview.IDLE -> if (state.engine == DjSpeechEngine.GEMINI_CLOUD) Res.string.dj_voice_preview_subtitle_gemini
                else Res.string.dj_voice_preview_subtitle
        }),
        trailingContent = {
            MillenniumButton(onClick = viewModel::preview, enabled = state.preview != DjVoicePreview.PLAYING) {
                Text(stringResource(Res.string.dj_voice_preview_button))
            }
        })
}
