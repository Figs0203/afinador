package io.github.figs0203.afinador.songbook

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Reads an Ultimate Guitar playlist, such as the link the site gives when you
 * press "Compartir" on one of your lists
 * (`https://www.ultimate-guitar.com/playlist/shared/…`).
 *
 * The playlist page is rendered by the site's own JavaScript, but it also ships
 * the whole list as JSON in a `data-content` attribute. That copy carries the
 * name of the list, and the title, artist and link of every tab, so the list can
 * be read without a browser and without an account.
 */
object PlaylistImporter {

    data class Entry(val title: String, val artist: String, val url: String)

    data class Playlist(val name: String, val entries: List<Entry>)

    private val DATA_CONTENT = Regex("(?is)data-content=\"([^\"]{2,8000000})\"")

    /** A playlist is never worth downloading thousands of tabs for. */
    private const val MAX_ENTRIES = 300

    /** Playlists are only accepted from Ultimate Guitar, where the JSON exists. */
    fun isPlaylistUrl(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        if (!host.contains("ultimate-guitar")) return false
        val path = runCatching { URL(url).path.lowercase() }.getOrNull().orEmpty()
        return path.contains("/playlist")
    }

    fun importFromUrl(url: String): Playlist = parse(SongImporter.fetch(url))

    fun parse(html: String): Playlist {
        DATA_CONTENT.findAll(html).forEach { match ->
            val decoded = SongImporter.unescapeEntities(match.groupValues[1])
            if (!decoded.trimStart().startsWith("{")) return@forEach
            val root = runCatching { JSONObject(decoded) }.getOrNull() ?: return@forEach
            val songbook = findSongbook(root) ?: return@forEach
            val entries = entriesOf(songbook)
            if (entries.isNotEmpty()) {
                return Playlist(songbook.optString("name").trim(), entries)
            }
        }

        throw SongImporter.ImportException(
            "No se encontró la lista en la página. " +
                "Comprueba que el enlace esté compartido públicamente."
        )
    }

    /**
     * Walks the page JSON looking for the object that holds the tabs, so a
     * change in the surrounding structure does not break the import.
     */
    private fun findSongbook(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                val tabs = node.optJSONArray("tabs")
                val first = tabs?.optJSONObject(0)
                if (first != null &&
                    (first.has("tab") || first.has("tab_url") || first.has("url"))
                ) {
                    return node
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findSongbook(node.opt(keys.next()))?.let { return it }
                }
            }

            is JSONArray -> for (index in 0 until node.length()) {
                findSongbook(node.opt(index))?.let { return it }
            }
        }
        return null
    }

    private fun entriesOf(songbook: JSONObject): List<Entry> {
        val tabs = songbook.optJSONArray("tabs") ?: return emptyList()
        val entries = mutableListOf<Entry>()
        val seen = mutableSetOf<String>()

        for (index in 0 until tabs.length()) {
            if (entries.size >= MAX_ENTRIES) break
            val item = tabs.optJSONObject(index) ?: continue
            val tab = item.optJSONObject("tab") ?: item

            val link = tab.optString("tab_url").ifBlank { tab.optString("url") }.trim()
            if (link.isEmpty() || !seen.add(link)) continue

            var title = tab.optString("localized_song_name").ifBlank { tab.optString("song_name") }
            var artist = tab.optString("localized_artist_name").ifBlank { tab.optString("artist_name") }
            if (title.isBlank() || artist.isBlank()) {
                val fromUrl = SongImporter.fromUrl(link)
                if (title.isBlank()) title = fromUrl.first
                if (artist.isBlank()) artist = fromUrl.second
            }

            entries.add(Entry(title.trim(), artist.trim(), link))
        }
        return entries
    }
}
