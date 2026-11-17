package io.github.figs0203.afinador.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import io.github.figs0203.afinador.dsp.PitchDetector
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Captures microphone audio on a dedicated thread and reports a pitch estimate
 * for every frame. Callbacks arrive on the audio thread.
 */
class AudioEngine(private val onReading: (Reading?) -> Unit) {

    data class Reading(
        val frequency: Double,
        val level: Double,
        val clarity: Double
    )

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ captureLoop() }, "tuner-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running.set(false)
        thread?.join(600)
        thread = null
    }

    private fun captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            running.set(false)
            onReading(null)
            return
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferSize, FRAME_SIZE * 4)
            )
        } catch (error: Exception) {
            running.set(false)
            onReading(null)
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            running.set(false)
            onReading(null)
            return
        }

        val shorts = ShortArray(FRAME_SIZE)
        val samples = FloatArray(FRAME_SIZE)

        try {
            recorder.startRecording()
            while (running.get()) {
                val read = recorder.read(shorts, 0, FRAME_SIZE)
                if (read <= 0) {
                    if (read < 0) break else continue
                }
                if (read < FRAME_SIZE) continue

                var energy = 0.0
                for (i in 0 until FRAME_SIZE) {
                    val value = shorts[i] / 32768f
                    samples[i] = value
                    energy += value.toDouble() * value
                }
                val rms = sqrt(energy / FRAME_SIZE)

                if (rms < NOISE_FLOOR) {
                    onReading(null)
                    continue
                }

                val result = PitchDetector.detect(samples, SAMPLE_RATE, MIN_FREQUENCY, MAX_FREQUENCY)
                onReading(result?.let { Reading(it.frequency, rms, it.clarity) })
            }
        } catch (error: Exception) {
            onReading(null)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            running.set(false)
        }
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val FRAME_SIZE = 4096
        const val MIN_FREQUENCY = 55.0
        const val MAX_FREQUENCY = 1600.0
        private const val NOISE_FLOOR = 0.0045
    }
}
