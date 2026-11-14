package org.spotube.spotube.features.dj

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.spotube.spotube.modules.settings.SettingsRepository

/** Cada proveedor conserva su clave; ninguna clave entra en el JSON de ajustes ni en registros. */
class DjAiSession(
    private val credentials: DjCredentialStore? = null,
    private val settings: SettingsRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val state = MutableStateFlow(DjAiUiState(storageReady = credentials == null))
    val uiState = state.asStateFlow()
    private val keys = mutableMapOf<DjAiProvider, String>()
    private val models = mutableMapOf<DjAiProvider, String>()
    private val dirty = mutableSetOf<DjAiProvider>()
    private val unreadable = mutableSetOf<DjAiProvider>()
    private val persistence = Mutex()
    private var saveJob: Job? = null

    init {
        if (credentials != null) scope.launch {
            var failed = false
            val prefs = try { settings?.getCurrentSettings() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true; null }
            DjAiProvider.entries.forEach { provider ->
                try { keys[provider] = credentials.read(provider).orEmpty() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true; unreadable += provider }
                models[provider] = prefs?.djAiModels?.get(provider.name) ?: defaultModel(provider)
            }
            val provider = DjAiProvider.entries.firstOrNull { it.name == prefs?.djAiProvider } ?: DjAiProvider.OPENROUTER
            state.value = DjAiUiState(storageReady = true, storageSaved = !failed,
                storageError = failed, enabled = prefs?.djAiEnabled ?: false, provider = provider,
                apiKey = keys[provider].orEmpty(), model = models[provider] ?: defaultModel(provider),
                request = prefs?.djAiRequest.orEmpty())
        }
    }

    private fun defaultModel(provider: DjAiProvider) = if (provider == DjAiProvider.OPENROUTER) "openrouter/free" else ""
    private fun edit(transform: DjAiUiState.() -> DjAiUiState) {
        if (!state.value.storageReady) return
        state.update { current -> current.transform().copy(revision = current.revision + 1,
            status = DjAiStatus.IDLE, failure = null, httpStatus = null, storageSaved = false) }
        saveJob?.cancel()
        if (credentials != null) saveJob = scope.launch { delay(400); persist() }
    }
    fun enable(enabled: Boolean) { edit { copy(enabled = enabled) } }
    fun provider(provider: DjAiProvider) {
        if (!state.value.storageReady || state.value.provider == provider) return
        edit { copy(provider = provider, model = models[provider] ?: defaultModel(provider), apiKey = keys[provider].orEmpty()) }
    }
    fun model(value: String) {
        if (!state.value.storageReady) return
        models[state.value.provider] = value.trim().take(160)
        edit { copy(model = models[provider].orEmpty()) }
    }
    fun key(value: String) {
        if (!state.value.storageReady) return
        keys[state.value.provider] = value.filterNot { it.isISOControl() }.trim().take(1024)
        dirty += state.value.provider
        edit { copy(apiKey = keys[provider].orEmpty()) }
    }
    fun request(value: String) { edit { copy(request = value.take(300)) } }
    /** Borrar afecta exclusivamente a la credencial seleccionada. */
    fun clear() = key("")
    fun save() {
        saveJob?.cancel()
        saveJob = scope.launch { persist() }
    }
    internal suspend fun persist() = persistence.withLock {
        val store = credentials ?: return@withLock
        try {
            // Captura por proveedor: una escritura lenta nunca marca como guardada una edición posterior.
            val snapshot = state.value
            val pending = dirty.associateWith { keys[it].orEmpty() }
            val savedModels = models.mapKeys { it.key.name }.toMap()
            // Reintenta lecturas fallidas sin sustituir una edición que el usuario esté escribiendo.
            for (provider in unreadable.toList().filterNot { it in dirty }) {
                try {
                    val restored = store.read(provider).orEmpty()
                    if (provider !in dirty) {
                        keys[provider] = restored
                        unreadable.remove(provider)
                        if (state.value.provider == provider) state.update { it.copy(apiKey = restored) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Conserva el error; nunca sobreescribe una clave ilegible. */ }
            }
            for ((provider, value) in pending) {
                if (value.isEmpty()) store.delete(provider) else store.write(provider, value)
                unreadable.remove(provider)
                if (keys[provider].orEmpty() == value) dirty.remove(provider)
            }
            settings?.mutateSettings { it.copy(djAiEnabled = snapshot.enabled, djAiProvider = snapshot.provider.name,
                djAiModels = savedModels, djAiRequest = snapshot.request) }
            state.update { it.copy(storageSaved = dirty.isEmpty() && unreadable.isEmpty() &&
                snapshot.revision == it.revision, storageError = unreadable.isNotEmpty()) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state.update { it.copy(storageSaved = false, storageError = true) } }
    }
    /** Clave guardada de un proveedor, aunque no sea el seleccionado (la voz Gemini reutiliza la suya). */
    internal fun storedKey(provider: DjAiProvider): String = keys[provider].orEmpty()

    /** Cerrar limpia RAM; no elimina las credenciales persistidas. */
    fun close() { scope.cancel(); keys.clear(); state.value = DjAiUiState() }
    internal fun beginRequest(): DjAiUiState = state.updateAndGet {
        it.copy(status = DjAiStatus.PLANNING, failure = null, httpStatus = null, requestId = it.requestId + 1)
    }
    internal fun complete(attempt: DjAiUiState, status: DjAiStatus, failure: DjAiFailure? = null, httpStatus: Int? = null): Boolean {
        var accepted = false
        state.update { current ->
            accepted = current.enabled && current.revision == attempt.revision && current.requestId == attempt.requestId
            if (accepted) current.copy(status = status, failure = failure, httpStatus = httpStatus) else current
        }
        return accepted
    }
}
