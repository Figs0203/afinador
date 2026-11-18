package io.github.figs0203.afinador.songbook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A saved chord sheet. [text] holds the plain text of the song only: the app
 * never stores the downloaded page, ads or scripts, just the tablature.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val source: String,
    val url: String?,
    val text: String,
    val savedAt: Long,
    val transposeSemitones: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("source", source)
        put("url", url ?: JSONObject.NULL)
        put("text", text)
        put("savedAt", savedAt)
        put("transpose", transposeSemitones)
    }

    companion object {
        fun fromJson(json: JSONObject): Song = Song(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = json.optString("title"),
            artist = json.optString("artist"),
            source = json.optString("source"),
            url = if (json.isNull("url")) null else json.optString("url").ifBlank { null },
            text = json.optString("text"),
            savedAt = json.optLong("savedAt"),
            transposeSemitones = json.optInt("transpose").coerceIn(-11, 11)
        )
    }
}

/**
 * Local songbook persisted as a small JSON file in the app's private storage.
 * Newest song first; a corrupt or missing file simply yields an empty library.
 */
class SongStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val lock = Any()

    fun all(): List<Song> = synchronized(lock) { read() }

    fun get(id: String): Song? = all().firstOrNull { it.id == id }

    fun add(song: Song) = synchronized(lock) {
        val songs = read().toMutableList()
        songs.removeAll { it.id == song.id || (song.url != null && it.url == song.url) }
        songs.add(0, song)
        write(songs)
    }

    /**
     * Adds a whole playlist at once, keeping its order and skipping songs that
     * were already saved (same link), so re-importing a list is harmless.
     */
    fun addAll(songs: List<Song>) = synchronized(lock) {
        if (songs.isEmpty()) return@synchronized
        val ids = songs.map { it.id }.toSet()
        val urls = songs.mapNotNull { it.url }.toSet()
        val kept = read().filterNot { it.id in ids || (it.url != null && it.url in urls) }
        write(songs + kept)
    }

    fun delete(id: String) = synchronized(lock) {
        write(read().filterNot { it.id == id })
    }

    fun updateTranspose(id: String, semitones: Int) = synchronized(lock) {
        write(read().map { if (it.id == id) it.copy(transposeSemitones = semitones) else it })
    }

    private fun read(): List<Song> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                runCatching { Song.fromJson(array.getJSONObject(index)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun write(songs: List<Song>) {
        runCatching {
            val array = JSONArray()
            songs.forEach { array.put(it.toJson()) }
            file.writeText(array.toString())
        }
    }

    private companion object {
        const val FILE_NAME = "songbook.json"
    }
}
