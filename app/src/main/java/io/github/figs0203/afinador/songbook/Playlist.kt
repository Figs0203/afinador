package io.github.figs0203.afinador.songbook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A user-made list (playlist) of songs already saved in the songbook. It only
 * stores song ids, so renaming or deleting a song keeps the list consistent
 * (missing songs are simply skipped when rendering).
 */
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String>,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("songIds", JSONArray(songIds))
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Playlist = Playlist(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.optString("name"),
            songIds = json.optJSONArray("songIds")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            } ?: emptyList(),
            createdAt = json.optLong("createdAt")
        )
    }
}

/**
 * Local playlists persisted as a small JSON file in the app's private storage.
 * Newest playlist first.
 */
class PlaylistStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val lock = Any()

    fun all(): List<Playlist> = synchronized(lock) { read() }

    fun get(id: String): Playlist? = all().firstOrNull { it.id == id }

    fun create(name: String): Playlist = synchronized(lock) {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { DEFAULT_NAME },
            songIds = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        val playlists = read().toMutableList()
        playlists.add(0, playlist)
        write(playlists)
        playlist
    }

    fun rename(id: String, name: String) = synchronized(lock) {
        write(read().map {
            if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it
        })
    }

    fun delete(id: String) = synchronized(lock) {
        write(read().filterNot { it.id == id })
    }

    fun addSong(playlistId: String, songId: String) = synchronized(lock) {
        write(read().map { playlist ->
            if (playlist.id != playlistId) playlist
            else if (songId in playlist.songIds) playlist
            else playlist.copy(songIds = playlist.songIds + songId)
        })
    }

    fun removeSong(playlistId: String, songId: String) = synchronized(lock) {
        write(read().map { playlist ->
            if (playlist.id != playlistId) playlist
            else playlist.copy(songIds = playlist.songIds.filterNot { it == songId })
        })
    }

    private fun read(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                runCatching { Playlist.fromJson(array.getJSONObject(index)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun write(playlists: List<Playlist>) {
        runCatching {
            val array = JSONArray()
            playlists.forEach { array.put(it.toJson()) }
            file.writeText(array.toString())
        }
    }

    private companion object {
        const val FILE_NAME = "playlists.json"
        const val DEFAULT_NAME = "Nueva lista"
    }
}