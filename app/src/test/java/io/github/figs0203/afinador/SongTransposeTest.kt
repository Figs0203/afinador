package io.github.figs0203.afinador

import io.github.figs0203.afinador.songbook.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/** The key chosen for a song has to survive a re-import of the same page. */
class SongTransposeTest {

    private fun song(transpose: Int) = Song(
        id = "1",
        title = "Too Much To Ask",
        artist = "Arctic Monkeys",
        source = "Ultimate Guitar",
        url = "https://tabs.ultimate-guitar.com/tab/x",
        text = "Am C",
        savedAt = 0L,
        transposeSemitones = transpose
    )

    @Test
    fun keepsPreviousTranspose() {
        assertEquals(3, song(0).keepingTransposeOf(song(3)).transposeSemitones)
    }

    @Test
    fun firstImportKeepsItsOwnValue() {
        assertEquals(0, song(0).keepingTransposeOf(null).transposeSemitones)
    }
}
