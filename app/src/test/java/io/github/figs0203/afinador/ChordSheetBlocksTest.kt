package io.github.figs0203.afinador

import io.github.figs0203.afinador.songbook.ChordSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordSheetBlocksTest {

    private fun blocksOf(text: String) = ChordSheet.blocks(ChordSheet.render(text, 0))

    @Test
    fun `prose stays together and tab keeps its lines`() {
        val text = """
            [Intro]

            e|-------0--------|
            B|-------1--------|
            G|-------0--------|
            D|---2---2---2----|
            A|---2---2---2----|
            E|---0---0---0----|

            The smiles as she walked in the room
        """.trimIndent()

        val blocks = blocksOf(text)
        assertEquals(3, blocks.size)
        assertFalse(blocks[0].isTab)
        assertTrue(blocks[1].isTab)
        assertEquals(6, blocks[1].lines.size)
        assertFalse(blocks[2].isTab)
        assertTrue(blocks[2].text.contains("smiles"))
    }

    @Test
    fun `tab split in two halves with a blank line stays in one block`() {
        val text = """
            e|--1--8--|
            B|--3--10-|
            --------|
            --------|
        """.trimIndent()

        val blocks = blocksOf(text)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].isTab)
        assertEquals(4, blocks[0].lines.size)
    }

    @Test
    fun `a wrapped lyric keeps its own chords above every row`() {
        val text = """
                  Bb                C
            This is no declaration, I just thought I'd let you know goodbye
        """.trimIndent()

        val wrapped = ChordSheet.wrap(ChordSheet.render(text, 0), 26)

        // Every chord row is followed by lyrics, never by another chord row.
        wrapped.forEachIndexed { index, line ->
            if (line.isChordLine) assertFalse(wrapped[index + 1].isChordLine)
        }
        assertEquals("Bb                C", wrapped[0].text.trim())
        assertTrue(wrapped[1].text.trim().startsWith("This is no declaration"))
        assertTrue(wrapped.any { !it.isChordLine && it.text.contains("thought") })
        assertTrue(wrapped.last().text.contains("goodbye"))
    }

    @Test
    fun `wrapping never splits a chord name`() {
        val text = """
                Bb   Cmaj7
            aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        """.trimIndent()

        val wrapped = ChordSheet.wrap(ChordSheet.render(text, 0), 12)
        val names = wrapped.filter { it.isChordLine }.map { it.text.trim() }
        assertTrue(names.contains("Bb"))
        assertTrue(names.contains("Cmaj7"))
        assertTrue(wrapped.all { it.text.length <= 12 })
    }

    @Test
    fun `short lines are left untouched`() {
        val text = "C        G\nHello there my friend"
        val wrapped = ChordSheet.wrap(ChordSheet.render(text, 0), 40)
        assertEquals(2, wrapped.size)
        assertEquals("C        G", wrapped[0].text)
        assertEquals("Hello there my friend", wrapped[1].text)
    }

    @Test
    fun `chord line is never mistaken for tablature`() {
        assertFalse(ChordSheet.isTabLine("Em                B7"))
        assertFalse(ChordSheet.isTabLine("| Em | G | D |"))
        assertTrue(ChordSheet.isTabLine("e|--0--2--3--|"))
        assertTrue(ChordSheet.isTabLine("-----------------------|"))
    }
}
