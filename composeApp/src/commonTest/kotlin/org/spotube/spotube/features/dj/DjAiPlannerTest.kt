package org.spotube.spotube.features.dj

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack
import org.spotube.plugin_interfaces.plugin_apis.metadata.album.MetadataAlbum
import org.spotube.plugin_interfaces.plugin_apis.metadata.album.MetadataAlbumType

class DjAiPlannerTest {
    private val allowed = setOf("a", "b", "c", "d", "e")

    private fun track(id: String) = MetadataTrack(
        id = id, title = "Test $id", durationMs = 180_000, trackNumber = 1, discNumber = 1,
        artists = emptyList(), thumbnails = emptyList(), explicit = false, popularity = 0, isrcCode = null, externalUri = null,
        album = MetadataAlbum.Detailed(releaseDate = "2026", genres = emptyList(), trackCount = 1,
            id = "album", title = "Test album", description = null, thumbnails = emptyList(),
            albumType = MetadataAlbumType.Album, artists = emptyList(), externalUri = null))

    private fun configured() = DjAiSession().apply { key("test-only-not-a-real-secret"); enable(true) }
    private fun validReply() = reply("""{"track_ids":["d","a","c","b"]}""")
    private fun reply(content: String?, finish: String = "stop") = DjAiReply(200, buildJsonObject {
        putJsonArray("choices") { add(buildJsonObject {
            put("finish_reason", finish)
            putJsonObject("message") { put("content", content?.let(::JsonPrimitive) ?: JsonNull) }
        }) }
    }.toString())

    @Test fun httpFailuresAndEmbeddedErrorsHaveSpecificDiagnostics() {
        for ((code, failure) in listOf(401 to DjAiFailure.AUTHENTICATION, 402 to DjAiFailure.ACCOUNT_LIMIT,
            403 to DjAiFailure.PERMISSION, 429 to DjAiFailure.RATE_LIMIT, 404 to DjAiFailure.MODEL_UNAVAILABLE,
            400 to DjAiFailure.REQUEST_REJECTED, 503 to DjAiFailure.PROVIDER_UNAVAILABLE, 504 to DjAiFailure.TIMEOUT)) {
            val decoded = decodeDjAiReply(DjAiReply(code, ""), DjAiProvider.OPENROUTER, allowed, 4)
            assertEquals(failure, decoded.failure)
            assertEquals(code, decoded.httpStatus)
        }
        val embedded = decodeDjAiReply(DjAiReply(200, """{"error":{"code":429,"message":"not displayed"}}"""),
            DjAiProvider.OPENROUTER, allowed, 4)
        assertEquals(DjAiFailure.RATE_LIMIT, embedded.failure)
        assertEquals(429, embedded.httpStatus)
    }

    @Test fun emptyTruncatedAndMalformedRepliesAreDifferent() {
        assertEquals(DjAiFailure.EMPTY_RESPONSE, decodeDjAiReply(reply(null), DjAiProvider.OPENROUTER, allowed, 4).failure)
        assertEquals(DjAiFailure.OUTPUT_LIMIT, decodeDjAiReply(reply(null, "length"), DjAiProvider.OPENROUTER, allowed, 4).failure)
        assertEquals(DjAiFailure.INVALID_RESPONSE, decodeDjAiReply(DjAiReply(200, "not json"), DjAiProvider.OPENROUTER, allowed, 4).failure)
        assertEquals(DjAiFailure.INVALID_PLAN, decodeDjAiReply(reply("""{"track_ids":["invented"]}"""), DjAiProvider.OPENROUTER, allowed, 4).failure)
        assertEquals(listOf("d", "a", "c", "b"), decodeDjAiReply(validReply(), DjAiProvider.OPENROUTER, allowed, 4).ids)
    }

    @Test fun acceptsGeminiEnvelopeWithoutReadingProviderMessages() {
        val body = buildJsonObject {
            putJsonArray("candidates") { add(buildJsonObject {
                put("finishReason", "STOP")
                putJsonObject("content") { putJsonArray("parts") { add(buildJsonObject {
                    put("text", """{"track_ids":["d","a","c","b"]}""")
                }) } }
            }) }
        }.toString()
        assertEquals(listOf("d", "a", "c", "b"), decodeDjAiReply(DjAiReply(200, body), DjAiProvider.GEMINI, allowed, 4).ids)
    }

    @Test fun incompleteOrPaidConfigurationNeverSendsARequest() = runTest {
        val session = DjAiSession().apply { enable(true) }
        var calls = 0
        val planner = DjAiPlanner(session, DjAiTransport { _, _ -> calls++; validReply() })
        val candidates = allowed.map(::track)
        assertNull(planner.order(candidates))
        assertEquals(DjAiFailure.CONFIGURATION, session.uiState.value.failure)
        session.key("test-only-not-a-real-secret")
        session.model("a-paid-model")
        assertNull(planner.order(candidates))
        assertEquals(DjAiFailure.FREE_MODEL_REQUIRED, session.uiState.value.failure)
        assertEquals(0, calls)
    }

    @Test fun aSlowValidResponseCanFinishBeforeTheNewDeadline() = runTest {
        val session = configured()
        val planner = DjAiPlanner(session, DjAiTransport { _, _ -> delay(12_000); validReply() })
        assertEquals(listOf("d", "a", "c", "b"), planner.order(allowed.map(::track))?.map { it.id })
        assertEquals(DjAiStatus.READY, session.uiState.value.status)
        assertNull(session.uiState.value.failure)
    }

    @Test fun deadlineFallsBackOnceWithoutAutomaticRetries() = runTest {
        val session = configured()
        var calls = 0
        val planner = DjAiPlanner(session, DjAiTransport { _, _ -> calls++; delay(31_000); validReply() })
        assertNull(planner.order(allowed.map(::track)))
        assertEquals(DjAiFailure.TIMEOUT, session.uiState.value.failure)
        assertEquals(DjAiStatus.LOCAL_FALLBACK, session.uiState.value.status)
        assertEquals(1, calls)
    }

    @Test fun editingAndReturningToTheSameConfigurationStillInvalidatesTheOldRequest() = runTest {
        val session = configured()
        val planner = DjAiPlanner(session, DjAiTransport { _, _ ->
            session.enable(false); session.enable(true); validReply()
        })
        assertNull(planner.order(allowed.map(::track)))
        assertEquals(DjAiStatus.IDLE, session.uiState.value.status)
    }

    @Test fun previousRequestsCannotOverwriteTheNewestStatus() {
        val session = configured()
        val old = session.beginRequest()
        val current = session.beginRequest()
        assertFalse(session.complete(old, DjAiStatus.LOCAL_FALLBACK, DjAiFailure.NETWORK))
        assertEquals(DjAiStatus.PLANNING, session.uiState.value.status)
        assertTrue(session.complete(current, DjAiStatus.READY))
        assertEquals(DjAiStatus.READY, session.uiState.value.status)
    }

    @Test fun networkExceptionsNeverExposeTheirMessagesOrKeys() = runTest {
        val session = configured()
        val planner = DjAiPlanner(session, DjAiTransport { _, _ -> error("private exception message") })
        assertNull(planner.order(allowed.map(::track)))
        assertEquals(DjAiFailure.NETWORK, session.uiState.value.failure)
        assertFalse(session.uiState.value.toString().contains("private exception message"))
        assertFalse(session.uiState.value.toString().contains("test-only-not-a-real-secret"))
    }

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
