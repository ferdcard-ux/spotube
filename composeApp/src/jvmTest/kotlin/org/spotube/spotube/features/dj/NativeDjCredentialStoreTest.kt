package org.spotube.spotube.features.dj

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class NativeDjCredentialStoreTest {
    @Test fun windowsDpapiSurvivesRecreationAndRejectsTampering() = runBlocking {
        if (!System.getProperty("os.name").startsWith("Windows", true)) return@runBlocking
        val directory = Files.createTempDirectory("spotube-dpapi-test-")
        val store = NativeDjCredentialStore(directory.toString(), "unit-test")
        try {
            store.write(DjAiProvider.OPENROUTER, "fake-openrouter-key")
            store.write(DjAiProvider.GEMINI, "fake-gemini-key")
            val restarted = NativeDjCredentialStore(directory.toString(), "unit-test")
            assertEquals("fake-openrouter-key", restarted.read(DjAiProvider.OPENROUTER))
            assertEquals("fake-gemini-key", restarted.read(DjAiProvider.GEMINI))
            val files = Files.list(directory.resolve("dj-credentials")).use { it.iterator().asSequence().toList() }
            files.forEach { assertFalse(Files.readString(it, Charsets.ISO_8859_1).contains("fake-")) }
            val openrouter = files.first { it.fileName.toString().contains("OPENROUTER") }
            val gemini = files.first { it.fileName.toString().contains("GEMINI") }
            val encrypted = Files.readAllBytes(openrouter)
            Files.write(gemini, encrypted)
            assertTrue(runCatching { restarted.read(DjAiProvider.GEMINI) }.isFailure)
            Files.write(openrouter, encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
            assertTrue(runCatching { restarted.read(DjAiProvider.OPENROUTER) }.isFailure)
        } finally {
            DjAiProvider.entries.forEach { store.delete(it) }
            Files.deleteIfExists(directory.resolve("dj-credentials"))
            Files.deleteIfExists(directory)
        }
    }
}
