package org.spotube.spotube.features.dj

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest

/** Prueba aislada: nunca accede a las claves ni a la cola del usuario. */
object DjCredentialDeviceChecks {
    fun run(context: Context) {
        val namespace = "instrumentation-credentials-v1"
        val store = NativeDjCredentialStore(context, namespace)
        try {
            check(context.packageName.endsWith(".nightly"))
            runBlocking {
                try {
                    for (provider in DjAiProvider.entries) store.write(provider, "test-only-${provider.name}")
                    val restarted = NativeDjCredentialStore(context, namespace)
                    for (provider in DjAiProvider.entries) check(restarted.read(provider) == "test-only-${provider.name}")
                    restarted.delete(DjAiProvider.GEMINI)
                    check(restarted.read(DjAiProvider.GEMINI) == null)
                    check(restarted.read(DjAiProvider.OPENROUTER) == "test-only-OPENROUTER")
                    check(restarted.read(DjAiProvider.GROQ) == "test-only-GROQ")
                    val scopeId = MessageDigest.getInstance("SHA-256").digest(namespace.toByteArray())
                        .take(12).joinToString("") { "%02x".format(it) }
                    val directory = File(context.noBackupFilesDir, "dj-credentials-$scopeId")
                    val source = File(directory, "OPENROUTER.bin").readBytes()
                    check(!source.toString(Charsets.UTF_8).contains("test-only-OPENROUTER"))
                    File(directory, "GROQ.bin").writeBytes(source)
                    check(runCatching { restarted.read(DjAiProvider.GROQ) }.isFailure)
                    val tampered = source.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                    File(directory, "OPENROUTER.bin").writeBytes(tampered)
                    check(runCatching { restarted.read(DjAiProvider.OPENROUTER) }.isFailure)

                } finally { DjAiProvider.entries.forEach { store.delete(it) } }
            }

        } finally { /* El bloque interno elimina solo las credenciales ficticias. */ }
    }
}
