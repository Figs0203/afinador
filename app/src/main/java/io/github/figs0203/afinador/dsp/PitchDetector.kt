package io.github.figs0203.afinador.dsp

import kotlin.math.pow

/**
 * Monophonic pitch detection using the YIN algorithm (de Cheveigné & Kawahara, 2002).
 *
 * YIN is used instead of a plain FFT peak because it is far more robust for
 * plucked strings: it estimates the period directly, ignores the missing
 * fundamental on low strings and gives sub-sample accuracy thanks to parabolic
 * interpolation, which is what makes the cents readout trustworthy.
 *
 * This class is pure Kotlin (no Android dependencies) so it can be covered by
 * JVM unit tests with synthetic tones.
 */
object PitchDetector {

    class Result(val frequency: Double, val clarity: Double)

    /**
     * @param samples normalised audio in [-1, 1]
     * @param sampleRate sample rate in Hz
     * @param minFrequency lowest frequency of interest
     * @param maxFrequency highest frequency of interest
     */
    fun detect(
        samples: FloatArray,
        sampleRate: Int,
        minFrequency: Double = 55.0,
        maxFrequency: Double = 1600.0,
        threshold: Double = 0.12
    ): Result? {
        val n = samples.size
        val tauMax = minOf(n / 2, (sampleRate / minFrequency).toInt() + 2)
        val tauMin = maxOf(2, (sampleRate / maxFrequency).toInt())
        if (tauMax <= tauMin + 2) return null

        // The integration window is fixed so every tau uses the same sample
        // count; otherwise the normalised difference would be biased.
        val window = n - tauMax

        val difference = DoubleArray(tauMax + 1)
        var index = 0
        while (index <= tauMax) {
            if (index > 0) {
                var sum = 0.0
                for (i in 0 until window) {
                    val delta = samples[i] - samples[i + index]
                    sum += delta * delta
                }
                difference[index] = sum
            }
            index++
        }

        // Cumulative mean normalised difference function.
        val normalised = DoubleArray(tauMax + 1)
        normalised[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..tauMax) {
            runningSum += difference[tau]
            normalised[tau] = if (runningSum <= 0.0) 1.0 else difference[tau] * tau / runningSum
        }

        var tauEstimate = -1
        var tau = tauMin
        while (tau <= tauMax) {
            if (normalised[tau] < threshold) {
                // Advance to the bottom of the dip for a stable period.
                while (tau + 1 <= tauMax && normalised[tau + 1] < normalised[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }

        if (tauEstimate < 0) {
            // No dip below the threshold: accept the global minimum only when it
            // is still reasonably periodic.
            var best = tauMin
            for (t in tauMin + 1..tauMax) {
                if (normalised[t] < normalised[best]) best = t
            }
            if (normalised[best] > 0.35) return null
            tauEstimate = best
        }

        var refinedTau = tauEstimate.toDouble()
        if (tauEstimate in 1 until tauMax) {
            val previous = normalised[tauEstimate - 1]
            val current = normalised[tauEstimate]
            val next = normalised[tauEstimate + 1]
            val denominator = 2.0 * (2.0 * current - next - previous)
            if (denominator != 0.0) {
                refinedTau += (next - previous) / denominator
            }
        }
        if (refinedTau <= 0.0) return null

        val frequency = sampleRate / refinedTau
        if (frequency < minFrequency || frequency > maxFrequency) return null

        val clarity = (1.0 - normalised[tauEstimate]).coerceIn(0.0, 1.0)
        return Result(frequency, clarity)
    }
}
