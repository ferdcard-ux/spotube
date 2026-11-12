package org.spotube.spotube.features.dj

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM autenticado; la clave maestra permanece no exportable en Android Keystore. */
actual class NativeDjCredentialStore actual constructor(context: Any, namespace: String) : DjCredentialStore {
    private val app = (context as Context).applicationContext
    private val scopeId = MessageDigest.getInstance("SHA-256").digest(namespace.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    private val alias = "${app.packageName}.dj.credentials.$scopeId"
    private val directory = File(app.noBackupFilesDir, "dj-credentials-$scopeId")
    private fun file(provider: DjAiProvider) = AtomicFile(File(directory, "${provider.name}.bin"))
    private fun master(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    actual override suspend fun read(provider: DjAiProvider): String? = withContext(Dispatchers.IO) {
        val stored = file(provider)
        if (!stored.baseFile.exists()) return@withContext null
        val bytes = stored.openRead().use { it.readBytes() }
        check(bytes.size in 30..16_384 && bytes[0] == 1.toByte()) { "Invalid credential envelope" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, master(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(provider.name.toByteArray())
        cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8)
    }
    actual override suspend fun write(provider: DjAiProvider, key: String) = withContext(Dispatchers.IO) {
        require(key.length in 1..1024)
        check(directory.exists() || directory.mkdirs())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, master())
        cipher.updateAAD(provider.name.toByteArray())
        val plain = key.toByteArray()
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val stored = file(provider)
        val output = stored.startWrite()
        try {
            output.write(byteArrayOf(1) + cipher.iv + encrypted)
            stored.finishWrite(output)
        } catch (error: Throwable) { stored.failWrite(output); throw error }
    }
    actual override suspend fun delete(provider: DjAiProvider) = withContext(Dispatchers.IO) { file(provider).delete() }
}
