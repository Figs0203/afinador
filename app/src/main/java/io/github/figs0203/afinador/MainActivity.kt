package io.github.figs0203.afinador

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.figs0203.afinador.audio.AudioEngine
import io.github.figs0203.afinador.music.Music
import io.github.figs0203.afinador.music.Tuning
import io.github.figs0203.afinador.music.Tunings
import io.github.figs0203.afinador.songbook.SongLibraryActivity
import io.github.figs0203.afinador.songbook.SongStore
import io.github.figs0203.afinador.ui.TunerGaugeView
import kotlin.math.abs
import kotlin.math.pow

class MainActivity : Activity() {

    private enum class Mode { CHROMATIC, GUITAR }

    private lateinit var root: View
    private lateinit var gauge: TunerGaugeView
    private lateinit var modeChromatic: TextView
    private lateinit var modeGuitar: TextView
    private lateinit var tuningScroll: HorizontalScrollView
    private lateinit var tuningGroup: LinearLayout
    private lateinit var stringRow: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var songbookSummary: TextView

    private val store by lazy { SongStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val recentFrequencies = ArrayDeque<Double>()
    private val tuningChips = mutableListOf<Pair<Tuning, TextView>>()
    private val stringChips = mutableListOf<TextView>()

    private var engine: AudioEngine? = null
    private var mode = Mode.CHROMATIC
    private var tuning: Tuning = Tunings.STANDARD
    private var smoothedFrequency = 0.0
    private var silentFrames = 0
    private var activeStringIndex = -1

    private val density by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = findViewById(R.id.root)
        gauge = findViewById(R.id.gauge)
        modeChromatic = findViewById(R.id.modeChromatic)
        modeGuitar = findViewById(R.id.modeGuitar)
        tuningScroll = findViewById(R.id.tuningScroll)
        tuningGroup = findViewById(R.id.tuningGroup)
        stringRow = findViewById(R.id.stringRow)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        songbookSummary = findViewById(R.id.songbookSummary)

        applyWindowInsets()
        setupModeButtons()
        buildTuningChips()
        buildStringChips()
        applyModeUi()

        gauge.hintText = getString(R.string.hint_idle)
        gauge.setOnClickListener { onGaugeTapped() }
        findViewById<View>(R.id.openSongbook).setOnClickListener {
            startActivity(Intent(this, SongLibraryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateSongbookSummary()
        if (hasMicrophonePermission()) {
            startListening()
        } else {
            setStatus(getString(R.string.status_no_mic), R.color.text_secondary)
            gauge.hintText = getString(R.string.hint_need_permission)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        }
    }

    override fun onPause() {
        stopListening()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode != REQUEST_MICROPHONE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            setStatus(getString(R.string.status_mic_blocked), R.color.text_secondary)
            gauge.hintText = getString(R.string.hint_enable_permission)
        }
    }

    // ---------------------------------------------------------------- audio

    private fun startListening() {
        if (engine != null) return
        gauge.hintText = getString(R.string.hint_idle)
        engine = AudioEngine { reading ->
            handler.post { render(reading) }
        }.also { it.start() }
        setStatus(getString(R.string.status_listening), R.color.accent)
    }

    private fun stopListening() {
        engine?.stop()
        engine = null
        handler.removeCallbacksAndMessages(null)
        smoothedFrequency = 0.0
        recentFrequencies.clear()
        silentFrames = 0
        activeStringIndex = -1
        gauge.state = TunerGaugeView.State()
    }

    private fun render(reading: AudioEngine.Reading?) {
        if (reading == null) {
            silentFrames++
            if (silentFrames >= 2) {
                smoothedFrequency = 0.0
                recentFrequencies.clear()
                if (activeStringIndex != -1) {
                    activeStringIndex = -1
                    styleStringChips(-1)
                }
                gauge.state = TunerGaugeView.State()
                setStatus(getString(R.string.status_waiting), R.color.text_secondary)
            }
            return
        }

        silentFrames = 0
        setStatus(getString(R.string.status_listening), R.color.accent)

        val frequency = stabilize(reading.frequency)
        if (mode == Mode.CHROMATIC) showChromatic(frequency) else showGuitar(frequency)
    }

    /**
     * Median of the last few detections plus a light exponential average:
     * removes single frame octave jumps without making the needle sluggish.
     */
    private fun stabilize(frequency: Double): Double {
        if (recentFrequencies.isNotEmpty()) {
            val median = medianOf(recentFrequencies)
            if (abs(Music.centsBetween(frequency, median)) > 250.0) {
                recentFrequencies.clear()
                smoothedFrequency = 0.0
            }
        }
        recentFrequencies.addLast(frequency)
        while (recentFrequencies.size > 5) recentFrequencies.removeFirst()

        val median = medianOf(recentFrequencies)
        smoothedFrequency = if (smoothedFrequency <= 0.0) {
            median
        } else {
            smoothedFrequency * 0.35 + median * 0.65
        }
        return smoothedFrequency
    }

    private fun medianOf(values: Collection<Double>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
    }

    private fun showChromatic(frequency: Double) {
        val note = Music.nearestNote(frequency)
        gauge.state = TunerGaugeView.State(
            noteName = note.name,
            octave = note.octave.toString(),
            cents = note.cents,
            frequency = frequency,
            targetFrequency = note.frequency,
            showTarget = false,
            active = true
        )
    }

    private fun showGuitar(frequency: Double) {
        val index = tuning.nearestStringIndex(frequency)
        val targetMidi = tuning.strings[index]
        val targetFrequency = Music.frequencyOf(targetMidi.toDouble())

        if (index != activeStringIndex) {
            activeStringIndex = index
            styleStringChips(index)
        }

        gauge.state = TunerGaugeView.State(
            noteName = Music.nameOf(targetMidi),
            octave = Music.octaveOf(targetMidi).toString(),
            cents = Music.centsBetween(frequency, targetFrequency),
            frequency = frequency,
            targetFrequency = targetFrequency,
            showTarget = true,
            active = true
        )
    }

    // ------------------------------------------------------------------- ui

    /** The songbook is a main feature, so the header shows how much is saved. */
    private fun updateSongbookSummary() {
        val count = store.all().size
        songbookSummary.text = if (count == 0) {
            getString(R.string.songbook_header_empty)
        } else {
            resources.getQuantityString(R.plurals.songbook_count, count, count)
        }
    }

    private fun setupModeButtons() {
        modeChromatic.setOnClickListener {
            mode = Mode.CHROMATIC
            applyModeUi()
        }
        modeGuitar.setOnClickListener {
            mode = Mode.GUITAR
            applyModeUi()
        }
    }

    private fun applyModeUi() {
        val chromatic = mode == Mode.CHROMATIC
        modeChromatic.setBackgroundResource(if (chromatic) R.drawable.segment_selected else 0)
        modeGuitar.setBackgroundResource(if (chromatic) 0 else R.drawable.segment_selected)
        modeChromatic.setTextColor(getColor(if (chromatic) R.color.accent else R.color.text_secondary))
        modeGuitar.setTextColor(getColor(if (chromatic) R.color.text_secondary else R.color.accent))

        tuningScroll.visibility = if (chromatic) View.GONE else View.VISIBLE
        stringRow.visibility = if (chromatic) View.GONE else View.VISIBLE

        smoothedFrequency = 0.0
        recentFrequencies.clear()
        activeStringIndex = -1
        styleStringChips(-1)
        gauge.state = TunerGaugeView.State()
    }

    private fun buildTuningChips() {
        tuningGroup.removeAllViews()
        tuningChips.clear()

        Tunings.ALL.forEach { candidate ->
            val chip = TextView(this).apply {
                text = candidate.name
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(9), dp(15), dp(9))
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.tuning_chip_description, candidate.name, candidate.stringNames())
                setOnClickListener { selectTuning(candidate) }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            tuningGroup.addView(chip, params)
            tuningChips.add(candidate to chip)
        }
        styleTuningChips()
    }

    private fun selectTuning(candidate: Tuning) {
        if (candidate.id == tuning.id) return
        tuning = candidate
        styleTuningChips()
        buildStringChips()
        smoothedFrequency = 0.0
        recentFrequencies.clear()
        activeStringIndex = -1
        gauge.state = TunerGaugeView.State()
    }

    private fun styleTuningChips() {
        tuningChips.forEach { (candidate, chip) ->
            val selected = candidate.id == tuning.id
            chip.setBackgroundResource(if (selected) R.drawable.chip_selected else R.drawable.chip_normal)
            chip.setTextColor(getColor(if (selected) R.color.accent else R.color.text_secondary))
        }
    }

    private fun buildStringChips() {
        stringRow.removeAllViews()
        stringChips.clear()

        tuning.strings.forEach { midi ->
            val chip = TextView(this).apply {
                text = Music.label(midi)
                textSize = 13f
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.chip_normal)
                setTextColor(getColor(R.color.text_secondary))
                contentDescription = getString(R.string.string_chip_description, Music.label(midi))
            }
            val params = LinearLayout.LayoutParams(0, dp(38)).apply {
                weight = 1f
                marginEnd = dp(6)
            }
            stringRow.addView(chip, params)
            stringChips.add(chip)
        }
        // Remove the trailing margin so the row is visually even.
        stringChips.lastOrNull()?.let { last ->
            (last.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
            last.requestLayout()
        }
    }

    private fun styleStringChips(index: Int) {
        if (stringChips.isEmpty()) return
        stringChips.forEachIndexed { chipIndex, chip ->
            val selected = chipIndex == index
            chip.setBackgroundResource(if (selected) R.drawable.chip_selected else R.drawable.chip_normal)
            chip.setTextColor(getColor(if (selected) R.color.accent else R.color.text_secondary))
            chip.alpha = if (index == -1 || selected) 1f else 0.55f
        }
    }

    private fun setStatus(text: String, colorRes: Int) {
        if (statusText.text.toString() == text) return
        statusText.text = text
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(colorRes))
    }

    private fun applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top
                bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(dp(20), top + dp(14), dp(20), bottom + dp(10))
            insets
        }
        root.requestApplyInsets()
    }

    private fun onGaugeTapped() {
        if (hasMicrophonePermission()) return
        val canAsk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        if (canAsk) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        } else {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private const val REQUEST_MICROPHONE = 1001
    }
}
