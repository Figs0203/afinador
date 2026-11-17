package io.github.figs0203.afinador

import io.github.figs0203.afinador.dsp.PitchDetector
import io.github.figs0203.afinador.music.Music
import io.github.figs0203.afinador.music.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class PitchDetectorTest {

    private fun sine(
        frequency: Double,
        amplitude: Float = 0.6f,
        sampleRate: Int = 44100,
        size: Int = 4096
    ): FloatArray = FloatArray(size) { index ->
        (amplitude * sin(2.0 * PI * frequency * index / sampleRate)).toFloat()
    }

    private fun detectedFrequency(frequency: Double): Double {
        val result = PitchDetector.detect(sine(frequency), 44100)
        assertNotNull("no pitch detected for $frequency Hz", result)
        return result!!.frequency
    }

    private fun detectedCents(frequency: Double): Double {
        val note = Music.nearestNote(detectedFrequency(frequency))
        return note.cents
    }

    @Test
    fun detectsConcertA() {
        assertEquals(440.0, detectedFrequency(440.0), 0.5)
    }

    @Test
    fun detectsLowEString() {
        assertEquals(82.41, detectedFrequency(82.41), 0.6)
    }

    @Test
    fun detectsHighEString() {
        assertEquals(329.63, detectedFrequency(329.63), 0.6)
    }

    @Test
    fun detectsLowSevenStringB() {
        assertEquals(61.74, detectedFrequency(61.74), 0.6)
    }

    @Test
    fun detectsHighFrequencies() {
        assertEquals(1318.51, detectedFrequency(1318.51), 2.0)
    }

    @Test
    fun detectsQuietSignal() {
        val quiet = sine(196.0, amplitude = 0.02f)
        val result = PitchDetector.detect(quiet, 44100)
        assertNotNull("quiet signal should still be detected", result)
        assertEquals(196.0, result!!.frequency, 0.8)
    }

    @Test
    fun silenceYieldsNoPitch() {
        assertNull(PitchDetector.detect(FloatArray(4096), 44100))
    }

    @Test
    fun whiteNoiseYieldsNoPitch() {
        val random = Random(7)
        val noise = FloatArray(4096) { (random.nextFloat() - 0.5f) * 0.6f }
        assertNull(PitchDetector.detect(noise, 44100))
    }

    @Test
    fun measuresCentsWithinOneCent() {
        val sharp = 440.0 * 2.0.pow(25.0 / 1200.0)
        assertEquals(25.0, detectedCents(sharp), 1.0)

        val flat = 440.0 * 2.0.pow(-35.0 / 1200.0)
        assertEquals(-35.0, detectedCents(flat), 1.0)
    }

    @Test
    fun nearestNoteReportsOctaveAndDeviation() {
        val note = Music.nearestNote(444.0)
        assertEquals("A", note.name)
        assertEquals(4, note.octave)
        assertEquals(15.67, note.cents, 0.1)
    }

    @Test
    fun nearestNoteWorksOutsideGuitarStrings() {
        val note = Music.nearestNote(261.63)
        assertEquals("C", note.name)
        assertEquals(4, note.octave)
        assertEquals(0.0, note.cents, 0.2)
    }

    @Test
    fun standardTuningFindsClosestString() {
        val tuning = Tunings.STANDARD
        assertEquals(0, tuning.nearestStringIndex(82.4))
        assertEquals(1, tuning.nearestStringIndex(110.0))
        assertEquals(2, tuning.nearestStringIndex(146.8))
        assertEquals(3, tuning.nearestStringIndex(196.0))
        assertEquals(4, tuning.nearestStringIndex(246.9))
        assertEquals(5, tuning.nearestStringIndex(329.6))
    }

    @Test
    fun dropDTuningReordersTargets() {
        val dropD = Tunings.ALL.first { it.id == "drop_d" }
        assertEquals("D", Music.nameOf(dropD.strings.first()))
        assertEquals(0, dropD.nearestStringIndex(73.4))
    }
}
