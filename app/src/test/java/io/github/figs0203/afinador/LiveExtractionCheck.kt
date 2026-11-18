package io.github.figs0203.afinador

import io.github.figs0203.afinador.songbook.ChordSheet
import io.github.figs0203.afinador.songbook.PlaylistImporter
import io.github.figs0203.afinador.songbook.SongImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the importer against pages downloaded from the live sites, not against
 * hand-written HTML. It is opt-in: without samples under `build/live-samples` the
 * tests are skipped, so the normal suite stays offline and fast.
 *
 * ```
 * mkdir -p build/live-samples
 * curl -sL --compressed -A "Mozilla/5.0 (Linux; Android 13) Chrome/122 Mobile" \
 *   -o build/live-samples/ug-chords.html \
 *   "https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-27596"
 * curl -sL --compressed -A "Mozilla/5.0 (Linux; Android 13) Chrome/122 Mobile" \
 *   -o build/live-samples/ug-playlist.html \
 *   "https://www.ultimate-guitar.com/playlist/shared/vy6zwyg6w3zx"
 * ./gradlew :app:testDebugUnitTest --tests "*LiveExtractionCheck"
 * ```
 */
class LiveExtractionCheck {

    private fun samplesDir(): File? = listOf(
        File("build/live-samples"),
        File("../build/live-samples"),
        File(System.getProperty("user.dir"), "build/live-samples"),
        File(System.getProperty("user.dir"), "../build/live-samples")
    ).firstOrNull { it.isDirectory }

    private fun sample(name: String): String? =
        samplesDir()?.let { File(it, name) }?.takeIf { it.isFile }?.readText(Charsets.UTF_8)

    private val ugUrl = "https://tabs.ultimate-guitar.com/tab/oasis/wonderwall-chords-27596"

    @Test
    fun ultimateGuitarPageStillParses() {
        val html = sample("ug-chords.html")
        assumeTrue("no live sample; see the class comment", html != null)

        val imported = SongImporter.extract(html!!, ugUrl)
        val lines = imported.text.lines()
        val chordLines = lines.count { ChordSheet.isChordLine(it) }

        assertTrue("title should be found", imported.title.isNotBlank())
        assertTrue("artist should be found", imported.artist.isNotBlank())
        assertTrue("expected many chord lines, got $chordLines", chordLines >= 10)
        assertTrue("expected tablature lines", lines.any { it.contains("-|") })
        assertFalse("ChordPro markup leaked", imported.text.contains("[ch]"))
        assertFalse("navigation leaked", imported.text.contains("Ultimate-Guitar.Com"))

        // Transposition must move chords, keep the first chord's column and leave tabs alone.
        val chords = ChordSheet.render(imported.text, 2).first { it.isChordLine }
        val original = ChordSheet.render(imported.text, 0).first { it.isChordLine }
        assertTrue("chords did not change", chords.text != original.text)
        assertTrue(
            "chord columns shifted: '${original.text}' -> '${chords.text}'",
            chords.text.indexOfFirst { !it.isWhitespace() } == original.text.indexOfFirst { !it.isWhitespace() }
        )
        val tabBefore = lines.first { it.contains("-|") }
        val tabAfter = ChordSheet.render(imported.text, 2).map { it.text }.first { it.contains("-|") }
        assertEquals("tablature was rewritten", tabBefore, tabAfter)
    }

    @Test
    fun jsonFallbackMatchesThePreBlock() {
        val html = sample("ug-chords.html")
        assumeTrue("no live sample; see the class comment", html != null)

        val fromPre = SongImporter.extract(html!!, ugUrl)
        val withoutPre = html.replace(Regex("(?is)<pre[^>]*>.*?</pre>"), "")
        val fromJson = SongImporter.extract(withoutPre, ugUrl)

        // On a page with no <pre>, the tab can only come from the embedded JSON copy.
        assertEquals(fromPre.text, fromJson.text)
        assertEquals(fromPre.title, fromJson.title)
    }

    @Test
    fun ultimateGuitarPlaylistStillReads() {
        val html = sample("ug-playlist.html")
        assumeTrue("no live sample; see the class comment", html != null)

        val playlist = PlaylistImporter.parse(html!!)
        val urls = playlist.entries.map { it.url }

        assertTrue("the name of the list should be found", playlist.name.isNotBlank())
        assertTrue("expected many songs, got ${urls.size}", urls.size >= 10)
        assertEquals("links must not repeat", urls.size, urls.distinct().size)
        assertTrue(
            "every entry must point at the site",
            urls.all { it.startsWith("https://tabs.ultimate-guitar.com/") }
        )
        // Personal tabs of the author stay in the list but cannot be downloaded
        // without their account; the import counts them as failures.
        assertTrue(
            "most entries must be public tab pages",
            urls.count { it.startsWith("https://tabs.ultimate-guitar.com/tab/") } >= urls.size - 2
        )
        assertTrue(
            "titles and artists must be readable",
            playlist.entries.all { it.title.isNotBlank() && it.artist.isNotBlank() }
        )
        assertTrue(
            "Wonderwall / Oasis is part of this sample",
            playlist.entries.any {
                it.title.equals("Wonderwall", true) && it.artist.equals("Oasis", true)
            }
        )
    }

    @Test
    fun cifraClubTextStillTransposesCleanly() {
        val text = sample("cifraclub.txt")
        assumeTrue("no live sample; see the class comment", text != null)

        val normalized = text!!.replace("\r\n", "\n")
        val base = ChordSheet.render(normalized, 0)
        val shifted = ChordSheet.render(normalized, 2)

        assertTrue("expected chord lines", shifted.count { it.isChordLine } >= 3)
        assertTrue("expected tablature lines", base.count { it.text.contains("-|") } >= 5)
        assertEquals(
            "tablature must not be rewritten",
            0,
            base.indices.count { base[it].text.contains("-|") && base[it].text != shifted[it].text }
        )
        assertTrue("lyrics were touched", base.indices.none {
            !base[it].isChordLine && base[it].text != shifted[it].text
        })
        assertFalse("encoding broke", normalized.contains('\uFFFD'))
    }
}
