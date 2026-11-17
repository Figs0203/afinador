package io.github.figs0203.afinador.music

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Equal-tempered music math referenced to A4.
 *
 * MIDI note 69 is A4. The conversion is intentionally done in log space so that
 * cents deviations stay accurate for low notes (where a few Hz means a lot).
 */
object Music {

    const val A4_FREQUENCY = 440.0

    private const val LN2 = 0.6931471805599453

    private val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    /** A note rounded to the nearest tempered pitch, with its deviation in cents. */
    data class Note(
        val midi: Int,
        val name: String,
        val octave: Int,
        val cents: Double,
        val frequency: Double
    )

    fun frequencyOf(midi: Double): Double = A4_FREQUENCY * 2.0.pow((midi - 69.0) / 12.0)

    fun midiOf(frequency: Double): Double = 69.0 + 12.0 * ln(frequency / A4_FREQUENCY) / LN2

    /** Signed distance in cents from [reference] to [frequency]. Positive means sharp. */
    fun centsBetween(frequency: Double, reference: Double): Double =
        1200.0 * ln(frequency / reference) / LN2

    fun nameOf(midi: Int): String = NOTE_NAMES[((midi % 12) + 12) % 12]

    fun octaveOf(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    fun label(midi: Int): String = nameOf(midi) + octaveOf(midi)

    fun nearestNote(frequency: Double): Note {
        val exact = midiOf(frequency)
        val midi = exact.roundToInt()
        return Note(
            midi = midi,
            name = nameOf(midi),
            octave = octaveOf(midi),
            cents = (exact - midi) * 100.0,
            frequency = frequencyOf(midi.toDouble())
        )
    }
}
