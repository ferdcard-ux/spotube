package org.spotube.spotube.features.dj

import kotlin.test.*

class DjAiPlannerTest {
    private val allowed = setOf("a", "b", "c", "d", "e")

    @Test fun acceptsOnlyACompleteKnownSelection() {
        assertEquals(listOf("d", "a", "c", "b"), validateDjPlan(
            """{"track_ids":["d","a","c","b"]}""", allowed, 4))
        assertNull(validateDjPlan("""{"track_ids":["a","b","c","invented"]}""", allowed, 4))
        assertNull(validateDjPlan("""{"track_ids":["a","b","c","c"]}""", allowed, 4))
        assertNull(validateDjPlan("""{"track_ids":["a","b"]}""", allowed, 4))
        assertNull(validateDjPlan("""{"track_ids":["a","b","c","d","e"]}""", allowed, 4))
    }

    @Test fun handlesMalformedAndOversizedReplies() {
        assertNull(validateDjPlan("not json", allowed, 4))
        assertNull(validateDjPlan("""{"track_ids":{}}""", allowed, 4))
        assertNull(validateDjPlan(" ".repeat(20_000), allowed, 4))
        assertEquals(listOf("a"), validateDjPlan("```json\n{\"track_ids\":[\"a\"]}\n```", allowed, 1))
    }

    @Test fun switchingProvidersNeverReusesTheOtherProvidersKey() {
        val session = DjAiSession()
        session.key("test-only-not-a-real-secret")
        session.enable(true)
        session.provider(DjAiProvider.GEMINI)
        assertEquals("", session.uiState.value.apiKey)
        assertEquals("", session.uiState.value.model)
        session.clear()
        assertFalse(session.uiState.value.enabled)
        assertEquals("", session.uiState.value.apiKey)
    }
}
