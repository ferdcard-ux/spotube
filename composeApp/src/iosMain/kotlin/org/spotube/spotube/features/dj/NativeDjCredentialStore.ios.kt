@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.spotube.spotube.features.dj

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.*
import platform.Security.*

/** Keychain del dispositivo; no sincroniza las claves con iCloud. */
actual class NativeDjCredentialStore actual constructor(context: Any, namespace: String) : DjCredentialStore {
    private val service = "org.spotube.dj.$namespace"
    private fun <T> query(provider: DjAiProvider, action: (CFMutableDictionaryRef) -> T): T {
        val dictionary = CFDictionaryCreateMutable(null, 0, null, null) ?: error("Keychain unavailable")
        val serviceValue = CFStringCreateWithCString(null, service, kCFStringEncodingUTF8)!!
        val account = CFStringCreateWithCString(null, provider.name, kCFStringEncodingUTF8)!!
        try {
            CFDictionarySetValue(dictionary, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dictionary, kSecAttrService, serviceValue)
            CFDictionarySetValue(dictionary, kSecAttrAccount, account)
            return action(dictionary)
        } finally { CFRelease(account); CFRelease(serviceValue); CFRelease(dictionary) }
    }
    actual override suspend fun read(provider: DjAiProvider): String? = withContext(Dispatchers.Default) {
        query(provider) { dictionary ->
            CFDictionarySetValue(dictionary, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(dictionary, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(dictionary, result.ptr)
                if (status == errSecItemNotFound) null else {
                    check(status == errSecSuccess) { "Keychain unavailable" }
                    val data = result.value!!.reinterpret<__CFData>()
                    try {
                        val length = CFDataGetLength(data).toInt().also { check(it in 1..16_384) }
                        CFDataGetBytePtr(data)!!.readBytes(length).decodeToString()
                    } finally { CFRelease(data) }
                }
            }
        }
    }
    actual override suspend fun write(provider: DjAiProvider, key: String): Unit = withContext(Dispatchers.Default) {
        require(key.length in 1..1024)
        val bytes = key.encodeToByteArray()
        try {
            bytes.usePinned { pinned ->
                val data = CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())!!
                try {
                    query(provider) { dictionary ->
                        val attributes = CFDictionaryCreateMutable(null, 0, null, null)!!
                        try {
                            CFDictionarySetValue(attributes, kSecValueData, data)
                            val updated = SecItemUpdate(dictionary, attributes)
                            if (updated == errSecItemNotFound) {
                                CFDictionarySetValue(dictionary, kSecValueData, data)
                                CFDictionarySetValue(dictionary, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                                check(SecItemAdd(dictionary, null) == errSecSuccess) { "Keychain unavailable" }
                            } else check(updated == errSecSuccess) { "Keychain unavailable" }
                        } finally { CFRelease(attributes) }
                    }
                } finally { CFRelease(data) }
            }
        } finally { bytes.fill(0) }
    }
    actual override suspend fun delete(provider: DjAiProvider): Unit = withContext(Dispatchers.Default) {
        query(provider) { dictionary ->
            val status = SecItemDelete(dictionary)
            check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain unavailable" }
        }
    }
}
