package org.spotube.spotube.features.dj

import kotlin.test.*
import kotlinx.coroutines.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DjCredentialSessionTest {
    private class Storage : DjCredentialStore {
        val values = mutableMapOf<DjAiProvider, String>()
        var fail = false
        var failRead = false
        override suspend fun read(provider: DjAiProvider): String? { check(!failRead); return values[provider] }
        override suspend fun write(provider: DjAiProvider, key: String) {
            check(!fail); values[provider] = key
        }
        override suspend fun delete(provider: DjAiProvider) { check(!fail); values.remove(provider) }
    }
    @Test fun switchingAndRestartingPreserveIndependentProviderKeys() = runTest {
        val storage = Storage()
        val session = DjAiSession(storage, scope = backgroundScope)
        runCurrent()
        session.key("fake-openrouter")
        session.model("test-model:free")
        session.provider(DjAiProvider.GEMINI)
        assertEquals("", session.uiState.value.apiKey)
        session.key("fake-gemini")
        session.provider(DjAiProvider.GROQ)
        session.key("fake-groq")
        session.provider(DjAiProvider.OPENROUTER)
        assertEquals("fake-openrouter", session.uiState.value.apiKey)
        assertEquals("test-model:free", session.uiState.value.model)
        session.persist()
        assertTrue(session.uiState.value.storageSaved)
        val restarted = DjAiSession(storage, scope = backgroundScope)
        runCurrent()
        for ((provider, expected) in mapOf(DjAiProvider.OPENROUTER to "fake-openrouter",
            DjAiProvider.GEMINI to "fake-gemini", DjAiProvider.GROQ to "fake-groq")) {
            restarted.provider(provider)
            assertEquals(expected, restarted.uiState.value.apiKey)
        }
        restarted.clear()
        restarted.persist()
        assertNull(storage.values[DjAiProvider.GROQ])
        assertEquals("fake-openrouter", storage.values[DjAiProvider.OPENROUTER])
        assertEquals("fake-gemini", storage.values[DjAiProvider.GEMINI])
    }
    @Test fun storageFailureDoesNotClaimSuccessOrDiscardOtherProviders() = runTest {
        val storage = Storage().apply { values[DjAiProvider.GEMINI] = "fake-existing" }
        val session = DjAiSession(storage, scope = backgroundScope)
        runCurrent()
        session.key("fake-new")
        storage.fail = true
        session.persist()
        assertTrue(session.uiState.value.storageError)
        assertFalse(session.uiState.value.storageSaved)
        assertEquals("fake-existing", storage.values[DjAiProvider.GEMINI])
        storage.fail = false
        session.persist()
        assertTrue(session.uiState.value.storageSaved)
        assertEquals("fake-new", storage.values[DjAiProvider.OPENROUTER])
    }
    @Test fun inaccessibleKeysAreNeverErasedAndCanBeRecoveredOnSave() = runTest {
        val storage = Storage().apply {
            values[DjAiProvider.OPENROUTER] = "fake-existing"
            failRead = true
        }
        val session = DjAiSession(storage, scope = backgroundScope)
        runCurrent()
        assertTrue(session.uiState.value.storageError)
        session.enable(true)
        session.persist()
        assertFalse(session.uiState.value.storageSaved)
        assertEquals("fake-existing", storage.values[DjAiProvider.OPENROUTER])
        storage.failRead = false
        session.persist()
        assertEquals("fake-existing", session.uiState.value.apiKey)
        assertTrue(session.uiState.value.storageSaved)
        assertFalse(session.uiState.value.storageError)
    }
}
