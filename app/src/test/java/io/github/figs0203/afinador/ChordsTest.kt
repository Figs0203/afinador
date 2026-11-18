package io.github.figs0203.afinador

import io.github.figs0203.afinador.music.Chords
import io.github.figs0203.afinador.songbook.ChordSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordsTest {

    @Test
    fun transposesSimpleChords() {
        assertEquals("D", Chords.transpose("C", 2))
        assertEquals("Bm", Chords.transpose("Am", 2))
        assertEquals("C", Chords.transpose("D", -2))
    }

    @Test
    fun wrapsAroundTheOctave() {
        assertEquals("C", Chords.transpose("B", 1))
        assertEquals("B", Chords.transpose("C", -1))
        assertEquals("C", Chords.transpose("C", 12))
    }

    @Test
    fun keepsFlatSpellingWhenTheChordHadAFlat() {
        assertEquals("C", Chords.transpose("Bb", 2))
        assertEquals("D", Chords.transpose("C#", 1))
        assertEquals("F#m", Chords.transpose("F#m", 0))
        assertEquals("Bb", Chords.transpose("C", -2))
    }

    @Test
    fun keepsQualitySuffixes() {
        assertEquals("Em7", Chords.transpose("F#m7", -2))
        assertEquals("A7", Chords.transpose("G7", 2))
        assertEquals("Bmaj7", Chords.transpose("Amaj7", 2))
        assertEquals("Dsus4", Chords.transpose("Csus4", 2))
        assertEquals("Cm7b5", Chords.transpose("Bm7b5", 1))
    }

    @Test
    fun transposesSlashChordsOnBothSides() {
        assertEquals("D/F#", Chords.transpose("C/E", 2))
        assertEquals("A/C#", Chords.transpose("G/B", 2))
    }

    @Test
    fun keepsBracketsAroundAChord() {
        assertEquals("(D#)", Chords.transpose("(C)", 3))
    }

    @Test
    fun leavesNonChordsAlone() {
        assertEquals("N.C.", Chords.transpose("N.C.", 2))
        assertEquals("Capo 2", Chords.transpose("Capo 2", 2))
        assertEquals("Dios", Chords.transpose("Dios", 2))
        assertEquals("", Chords.transpose("", 2))
    }

    @Test
    fun recognisesCommonChordShapes() {
        listOf("C", "Am", "F#m7b5", "Bb/D", "Cadd9", "G6/9", "D/F#", "Cmaj7", "(Em)").forEach {
            assertTrue("$it should be a chord", Chords.isChord(it))
        }
    }

    @Test
    fun rejectsWordsThatOnlyStartWithANoteName() {
        listOf("Sol", "Dios", "Coro", "Bien", "Estoy", "And", "Amor", "Fa").forEach {
            assertFalse("$it should not be a chord", Chords.isChord(it))
        }
    }
}

class ChordSheetTest {

    @Test
    fun detectsChordLines() {
        assertTrue(ChordSheet.isChordLine("C  G  Am  F"))
        assertTrue(ChordSheet.isChordLine("| G | D | Em | C |"))
        assertTrue(ChordSheet.isChordLine("Intro: G D Em C"))
        assertTrue(ChordSheet.isChordLine("         Bb      C"))
    }

    @Test
    fun doesNotDetectLyricsTabOrTuningNotes() {
        assertFalse(ChordSheet.isChordLine("Me despierto sin saber"))
        assertFalse(ChordSheet.isChordLine("e|---3---2---0---|"))
        assertFalse(ChordSheet.isChordLine("Tuning: E A D G B E"))
        assertFalse(ChordSheet.isChordLine("Afinación: E A D G B E"))
        assertFalse(ChordSheet.isChordLine(""))
    }

    @Test
    fun transposesOnlyTheChords() {
        val rendered = ChordSheet.render("C  G\nHoy no me levanto", 2)
        assertEquals("D  A", rendered[0].text)
        assertEquals(true, rendered[0].isChordLine)
        assertEquals("Hoy no me levanto", rendered[1].text)
        assertEquals(false, rendered[1].isChordLine)
    }

    @Test
    fun keepsColumnsAlignedWhenChordsChangeWidth() {
        assertEquals("D     A", ChordSheet.transposeLine("C     G", 2))
        assertEquals("C     G", ChordSheet.transposeLine("D     A", -2))
    }

    @Test
    fun keepsSectionLabels() {
        assertEquals("Intro: A E F#m D", ChordSheet.transposeLine("Intro: G D Em C", 2))
    }

    @Test
    fun leavesTablatureUntouched() {
        val tab = "e|---3---2---0---|"
        assertEquals(tab, ChordSheet.transposeLine(tab, 2))
    }
}
