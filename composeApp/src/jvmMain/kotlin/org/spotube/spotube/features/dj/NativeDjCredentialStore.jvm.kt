package org.spotube.spotube.features.dj

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** DPAPI en Windows; Keychain/Secret Service en macOS y Linux. No hay alternativa en texto plano. */
actual class NativeDjCredentialStore actual constructor(context: Any, namespace: String) : DjCredentialStore {
    private val directory = Path.of(context as String, "dj-credentials")
    private val service = "org.spotube.dj." + MessageDigest.getInstance("SHA-256").digest((directory.toString() + namespace).toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }
    private val windows = System.getProperty("os.name").startsWith("Windows", true)
    private val mac = System.getProperty("os.name").startsWith("Mac", true)
    private fun path(provider: DjAiProvider) = directory.resolve("${service}-${provider.name}.bin")

    actual override suspend fun read(provider: DjAiProvider): String? = withContext(Dispatchers.IO) {
        if (windows) {
            val path = path(provider)
            if (!Files.exists(path)) null else {
                val decoded = protect(Files.readAllBytes(path), false).toString(Charsets.UTF_8)
                val prefix = "$service/${provider.name}\n"
                check(decoded.startsWith(prefix)) { "Credential provider mismatch" }
                decoded.removePrefix(prefix)
            }
        } else {
            val command = if (mac) listOf("/usr/bin/security", "find-generic-password", "-s", service, "-a", provider.name, "-w")
                else listOf("secret-tool", "lookup", "application", service, "provider", provider.name)
            val result = runNative(command)
            if (result.first == 0) result.second.trimEnd('\r', '\n').takeIf { it.isNotEmpty() }
            else if ((mac && result.first == 44) || (!mac && result.first == 1 && result.second.isBlank())) null
            else error("Native credential storage unavailable")
        }
    }
    actual override suspend fun write(provider: DjAiProvider, key: String): Unit = withContext(Dispatchers.IO) {
        require(key.length in 1..1024 && key.none { it.isISOControl() })
        if (windows) {
            Files.createDirectories(directory)
            val plain = "$service/${provider.name}\n$key".toByteArray()
            val encrypted = try { protect(plain, true) } finally { plain.fill(0) }
            val temporary = Files.createTempFile(directory, "credential-", ".tmp")
            try {
                Files.write(temporary, encrypted)
                Files.move(temporary, path(provider), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
            Unit
        } else {
            // La clave pasa por stdin, nunca por argumentos visibles en la lista de procesos.
            val result = if (mac) runNative(listOf("/usr/bin/security", "-i"),
                "add-generic-password -U -s ${quoted(service)} -a ${quoted(provider.name)} -w ${quoted(key)}\n")
            else runNative(listOf("secret-tool", "store", "--label=Spotube DJ", "application", service, "provider", provider.name), key)
            check(result.first == 0) { "Native credential storage unavailable" }
            check(read(provider) == key) { "Native credential storage verification failed" }
        }
    }
    actual override suspend fun delete(provider: DjAiProvider) = withContext(Dispatchers.IO) {
        if (windows) { Files.deleteIfExists(path(provider)); Unit }
        else {
            val command = if (mac) listOf("/usr/bin/security", "delete-generic-password", "-s", service, "-a", provider.name)
                else listOf("secret-tool", "clear", "application", service, "provider", provider.name)
            val result = runNative(command)
            check(result.first == 0 || (mac && result.first == 44) || (!mac && result.first == 1))
        }
    }

    private fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun runNative(command: List<String>, input: String? = null): Pair<Int, String> {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { if (input != null) it.write(input) }
        if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Native credential storage timed out") }
        return process.exitValue() to process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    private fun protect(bytes: ByteArray, encrypt: Boolean): ByteArray {
        require(bytes.size in 1..16_384)
        val offset = if (Native.POINTER_SIZE == 8) 8L else 4L
        val size = offset + Native.POINTER_SIZE
        val library = NativeLibrary.getInstance("Crypt32")
        val free = NativeLibrary.getInstance("Kernel32").getFunction("LocalFree", Function.ALT_CONVENTION)
        Memory(bytes.size.toLong()).use { payload ->
            payload.write(0, bytes, 0, bytes.size)
            Memory(size).use { input -> Memory(size).use { output ->
                input.clear(); output.clear()
                input.setInt(0, bytes.size); input.setPointer(offset, payload)
                val function = library.getFunction(if (encrypt) "CryptProtectData" else "CryptUnprotectData", Function.ALT_CONVENTION)
                check(function.invokeInt(arrayOf(input, null, null, null, null, 1, output)) != 0) { "Native credential protection failed" }
                val pointer = output.getPointer(offset)
                try { return pointer.getByteArray(0, output.getInt(0).also { check(it in 1..16_384) }) }
                finally { free.invokePointer(arrayOf(pointer)); payload.clear() }
            } }
        }
    }
}
