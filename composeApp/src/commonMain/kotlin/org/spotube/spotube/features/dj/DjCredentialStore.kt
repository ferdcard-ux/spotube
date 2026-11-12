package org.spotube.spotube.features.dj

/** Las claves nunca forman parte del JSON de ajustes ni de copias de seguridad de preferencias. */
interface DjCredentialStore {
    suspend fun read(provider: DjAiProvider): String?
    suspend fun write(provider: DjAiProvider, key: String)
    suspend fun delete(provider: DjAiProvider)
}

expect class NativeDjCredentialStore(context: Any, namespace: String) : DjCredentialStore {
    override suspend fun read(provider: DjAiProvider): String?
    override suspend fun write(provider: DjAiProvider, key: String)
    override suspend fun delete(provider: DjAiProvider)
}
