package org.spotube.spotube.features.dj

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack
import org.spotube.spotube.core.db.Database

@Serializable
data class ListeningEntry(val trackId: String, val title: String, val artists: List<String>, val genre: String?,
                          val streaming: Boolean = true, val artistIds: List<String> = emptyList())

/** Un set ya reproducido: evita repetir sus canciones y, por un tiempo, sus artistas. */
@Serializable
data class PlayedDjSet(val trackIds: List<String>, val artists: List<String>, val artistNames: List<String> = emptyList())

/**
 * Memoria local del DJ (por perfil): lo escuchado y los sets que ya sonaron. Nunca sale del
 * dispositivo salvo el resumen de gustos que [DjTasteProfile] envía a la IA si la memoria está activa.
 */
class DjHistory(private val database: Database) {
    private val key = stringPreferencesKey("dj_listening_history")
    private val setsKey = stringPreferencesKey("dj_played_sets")
    private val linesKey = stringPreferencesKey("dj_recent_lines")
    private val json = Json { ignoreUnknownKeys = true }

    private fun decodeEntries(raw: String?) =
        raw?.let { runCatching { json.decodeFromString<List<ListeningEntry>>(it) }.getOrNull() }.orEmpty()
    private fun decodeSets(raw: String?) =
        raw?.let { runCatching { json.decodeFromString<List<PlayedDjSet>>(it) }.getOrNull() }.orEmpty()

    val entries: Flow<List<ListeningEntry>> = database.audioPlayerQueueDataStore.data.map { decodeEntries(it[key]) }

    suspend fun recent(): List<ListeningEntry> = entries.first()
    suspend fun recentSets(): List<PlayedDjSet> = decodeSets(database.audioPlayerQueueDataStore.data.first()[setsKey])

    suspend fun record(entry: ListeningEntry) {
        database.audioPlayerQueueDataStore.edit { prefs ->
            // Conserva repeticiones: son evidencia de preferencias, no duplicados accidentales.
            prefs[key] = json.encodeToString((listOf(entry) + decodeEntries(prefs[key])).take(HISTORY_SIZE))
        }
    }

    suspend fun recordSet(tracks: List<MetadataTrack>) {
        val set = PlayedDjSet(tracks.map { it.id }, tracks.map(::artistKey).distinct(),
            tracks.mapNotNull { it.artists.firstOrNull()?.name }.distinct())
        database.audioPlayerQueueDataStore.edit { prefs ->
            prefs[setsKey] = json.encodeToString((listOf(set) + decodeSets(prefs[setsKey])).take(PLAYED_SETS))
        }
    }

    /** Las últimas frases del DJ (también entre sesiones), para que no repita saludos. */
    suspend fun recentLines(): List<String> = database.audioPlayerQueueDataStore.data.first()[linesKey]
        ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    suspend fun recordLine(line: String) {
        database.audioPlayerQueueDataStore.edit { prefs ->
            val previous = prefs[linesKey]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
            prefs[linesKey] = json.encodeToString((listOf(line) + previous.filter { it != line }).take(RECENT_LINES))
        }
    }

    /** «Olvidar lo aprendido»: borra lo escuchado, los sets y las frases, no los ajustes. */
    suspend fun clear() {
        database.audioPlayerQueueDataStore.edit { prefs -> prefs.remove(key); prefs.remove(setsKey); prefs.remove(linesKey) }
    }

    private companion object {
        const val HISTORY_SIZE = 200
        const val PLAYED_SETS = 20
        const val RECENT_LINES = 5
    }
}

internal fun artistKey(track: MetadataTrack) =
    track.artists.firstOrNull()?.id?.ifBlank { null } ?: track.artists.firstOrNull()?.name?.lowercase().orEmpty()

/** Resumen de gustos calculado sólo con el historial local. */
data class DjTasteProfile(
    val topArtists: List<String>,
    val topGenres: List<String>,
    /** Identificadores de los artistas favoritos que el proveedor conoce, para buscar afines. */
    val topArtistIds: List<String>,
) {
    val isEmpty get() = topArtists.isEmpty() && topGenres.isEmpty()

    fun describe(): String = buildList {
        if (topArtists.isNotEmpty()) add("Artistas que más escucha: ${topArtists.joinToString()}")
        if (topGenres.isNotEmpty()) add("Géneros frecuentes: ${topGenres.joinToString()}")
    }.joinToString(". ")

    companion object {
        fun from(history: List<ListeningEntry>): DjTasteProfile {
            val artists = history.flatMap { it.artists }.filter(String::isNotBlank)
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            val genres = history.mapNotNull { it.genre?.takeIf(String::isNotBlank) }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            val ids = history.flatMap { entry -> entry.artistIds.zip(entry.artists) }
                .filter { (id, name) -> id.isNotBlank() && name in artists.take(TOP_ARTISTS) }
                .groupingBy { it.first }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            return DjTasteProfile(artists.take(TOP_ARTISTS), genres.take(TOP_GENRES), ids.take(TOP_ARTISTS))
        }

        private const val TOP_ARTISTS = 10
        private const val TOP_GENRES = 5
    }
}

/** Lo que la IA recibe para elegir canciones y escribir los diálogos. */
data class DjTasteContext(
    /** Resumen de gustos y notas del usuario; vacío si la memoria está desactivada. */
    val memory: String = "",
    /** Cómo habla el DJ y cómo trata al usuario. */
    val personality: String = "",
    /** Artistas de los últimos sets: no deben repetirse todavía. */
    val avoidArtists: List<String> = emptyList(),
    /** Fecha, hora y parte del día del oyente, en palabras (ver [DjMoment]). */
    val moment: String = "",
    /** «Menos comentarios»: intervenciones mínimas (guía de saludos, sección 6). */
    val concise: Boolean = false,
    /** Últimas frases dichas por el DJ, para no repetirlas. */
    val recentLines: List<String> = emptyList(),
)
