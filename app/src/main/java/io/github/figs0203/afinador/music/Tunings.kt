package io.github.figs0203.afinador.music

import kotlin.math.abs

/**
 * A guitar tuning expressed as MIDI note numbers, ordered from the lowest
 * (thickest) string to the highest (thinnest).
 */
data class Tuning(
    val id: String,
    val name: String,
    val strings: List<Int>
) {
    /** Index of the string the played frequency is closest to, in cents. */
    fun nearestStringIndex(frequency: Double): Int {
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        strings.forEachIndexed { index, midi ->
            val distance = abs(Music.centsBetween(frequency, Music.frequencyOf(midi.toDouble())))
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    /** Human readable string notes, e.g. "E A D G B E". */
    fun stringNames(): String = strings.joinToString(" ") { Music.nameOf(it) }
}

object Tunings {

    /** Standard six string tuning, used as the default. */
    val STANDARD = Tuning("standard", "Estándar", listOf(40, 45, 50, 55, 59, 64))

    val ALL: List<Tuning> = listOf(
        STANDARD,
        Tuning("drop_d", "Drop D", listOf(38, 45, 50, 55, 59, 64)),
        Tuning("half_step", "½ tono abajo", listOf(39, 44, 49, 54, 58, 63)),
        Tuning("d_standard", "D estándar", listOf(38, 43, 48, 53, 57, 62)),
        Tuning("drop_c", "Drop C", listOf(36, 43, 48, 53, 57, 62)),
        Tuning("open_g", "Open G", listOf(38, 43, 50, 55, 59, 62)),
        Tuning("open_d", "Open D", listOf(38, 45, 50, 54, 57, 62)),
        Tuning("open_e", "Open E", listOf(40, 47, 52, 56, 59, 64)),
        Tuning("dadgad", "DADGAD", listOf(38, 45, 50, 55, 57, 62))
    )
}
