package org.spotube.spotube.features.dj

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.artist.MetadataArtist
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack
import org.spotube.spotube.core.audioplayer.QueueCollectionEntry
import kotlin.test.*

class DjContinuityTest {
    private fun track(id: String, artist: String) = MetadataTrack(
        id = id, title = "Song $id", durationMs = 180_000, trackNumber = 1, discNumber = 1,
        artists = listOf(MetadataArtist.Basic(id = "artist-$artist", name = artist, thumbnails = emptyList(), externalUri = null)),
        thumbnails = emptyList(), explicit = false, popularity = 0, isrcCode = null, externalUri = null, album = null)

    private fun configured() = DjAiSession().apply { key("test-only-not-a-real-secret"); enable(true) }
    private fun reply(content: String) = DjAiReply(200, buildJsonObject {
        putJsonArray("choices") { add(buildJsonObject {
            put("finish_reason", "stop")
            putJsonObject("message") { put("content", content) }
        }) }
    }.toString())

    @Test fun bridgedSetEndsWithoutAnotherGreeting() {
        val lifecycle = DjSetLifecycle()
        val set = QueueCollectionEntry.DjSet("next")
        lifecycle.begin(set.id, "intro", "outro", introduced = true)
        assertFalse(lifecycle.introduction(set))
        assertTrue(lifecycle.ending(set))
    }

    @Test fun bridgePromptLinksBothBlocksWithoutSayingGoodbye() = runTest {
        var prompt = ""
        val planner = DjAiPlanner(configured(), DjAiTransport { _, sent ->
            prompt = sent; reply("""{"bridge":"Qué bloque tan bueno. Ahora seguimos con Soda Stereo y Café Tacvba."}""")
        })
        val bridge = planner.bridge("Livi", listOf(track("1", "Queen")), listOf(track("2", "Soda Stereo")), DjTasteContext(personality = "Tutéame"))
        assertEquals("Qué bloque tan bueno. Ahora seguimos con Soda Stereo y Café Tacvba.", bridge)
        assertTrue("Song 1 — Queen" in prompt && "Song 2 — Soda Stereo" in prompt)
        assertTrue("No te despidas" in prompt)
        assertTrue("Tutéame" in prompt)
    }

    @Test fun suggestionsAreCleanedAndOnePerArtist() = runTest {
        val planner = DjAiPlanner(configured(), DjAiTransport { _, _ ->
            reply("""{"suggestions":[{"artist":"Soda Stereo","title":"De música ligera"},{"artist":"soda stereo","title":"Persiana americana"},{"artist":"","title":"x"},{"artist":"Zoé","title":"Labios rotos"}]}""")
        })
        assertEquals(listOf("Soda Stereo" to "De música ligera", "Zoé" to "Labios rotos"),
            planner.suggest(10, DjTasteContext(), emptyList()))
    }

    @Test fun nothingIsSentWhenAiIsOff() = runTest {
        var calls = 0
        val planner = DjAiPlanner(DjAiSession(), DjAiTransport { _, _ -> calls++; reply("{}") })
        assertNull(planner.bridge("Livi", emptyList(), emptyList(), DjTasteContext()))
        assertTrue(planner.suggest(5, DjTasteContext(), emptyList()).isEmpty())
        assertEquals(0, calls)
    }

    @Test fun tasteProfileRanksArtistsGenresAndKeepsTheirIds() {
        val history = listOf(
            ListeningEntry("1", "a", listOf("Zoé"), "rock", artistIds = listOf("z")),
            ListeningEntry("2", "b", listOf("Zoé"), "rock", artistIds = listOf("z")),
            ListeningEntry("3", "c", listOf("Bad Bunny"), "urbano", artistIds = listOf("b")),
        )
        val profile = DjTasteProfile.from(history)
        assertEquals(listOf("Zoé", "Bad Bunny"), profile.topArtists)
        assertEquals(listOf("rock", "urbano"), profile.topGenres)
        assertEquals(listOf("z", "b"), profile.topArtistIds)
        assertTrue("Zoé" in profile.describe())
    }

    @Test fun fallbackLinesNeverSoundLikeAFarewell() {
        repeat(4) { variant ->
            val outro = DjDialogueSkill.fallbackOutro("Livi", variant).lowercase()
            assertFalse("gracias por escuchar" in outro || "vuelve" in outro)
            assertTrue("Zoé" in DjDialogueSkill.fallbackBridge(listOf("Zoé", "Queen"), variant))
        }
    }

    @Test fun greetingFromAiIsKeptWithoutNameOrOutroAndCarriesTheMoment() = runTest {
        var prompt = ""
        val planner = DjAiPlanner(configured(), DjAiTransport { _, sent ->
            prompt = sent; reply("""{"intro":"¡Viernes de madrugada y esto apenas empieza! Arrancamos con Zoé."}""")
        })
        val taste = DjTasteContext(moment = "viernes 14 de noviembre de 2026, 4:40 (madrugada)", personality = "sarcástico")
        val (intro, outro) = assertNotNull(planner.dialogue("Livi", listOf(track("1", "Zoé")), taste))
        assertTrue(intro.startsWith("¡Viernes"))
        assertNull(outro)
        assertTrue("viernes 14 de noviembre" in prompt && "sarcástico" in prompt && "Song 1 — Zoé" in prompt)
    }

    @Test fun fallbackGreetingsVaryAndUseTheSetArtists() {
        val friday = kotlinx.datetime.LocalDateTime(2026, 11, 14, 21, 30)
        val greetings = (0 until 20).map { DjDialogueSkill.fallbackIntro("Livi", listOf("Zoé", "Queen"), friday, kotlin.random.Random(it)) }
        assertTrue(greetings.distinct().size > 5)
        assertTrue(greetings.all { "DJ Livi" in it && ("Zoé" in it) })
        assertTrue(greetings.any { "Buenas noches" in it })
    }

    @Test fun longLinesAreCappedAtFortyWordsOnASentenceEnd() {
        val first = (1..25).joinToString(" ") { "uno" } + "."
        val second = (1..25).joinToString(" ") { "dos" } + "."
        val capped = assertNotNull("$first $second".cleanDjDialogue())
        assertEquals(first, capped)
        assertTrue(capped.split(' ').size <= 40)
    }

    @Test fun promptsAddressOneListenerAndHonourConciseMode() {
        val full = DjDialogueSkill.prompt("Livi", listOf("Song — Zoé"), "", DjTasteContext(recentLines = listOf("Hola, hola")))
        assertTrue("singular" in full && "prohibido \"gente\"" in full && "30 a 40 palabras" in full)
        assertTrue("Hola, hola" in full)
        val concise = DjDialogueSkill.prompt("Livi", listOf("Song — Zoé"), "", DjTasteContext(concise = true))
        assertTrue("10 a 15 palabras" in concise && "30 a 40" !in concise)
        val greeting = DjDialogueSkill.fallbackIntro("Livi", listOf("Zoé"), kotlinx.datetime.LocalDateTime(2026, 11, 14, 9, 0),
            kotlin.random.Random(3), concise = true)
        assertTrue(greeting.split(' ').size <= 15 && "Zoé" in greeting)
    }
}
