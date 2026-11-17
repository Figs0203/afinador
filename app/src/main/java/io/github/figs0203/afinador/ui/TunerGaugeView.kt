package io.github.figs0203.afinador.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dark tuner gauge: an arc with tick marks, a needle for the cents deviation and
 * the detected note drawn in the middle.
 *
 * Everything is drawn in one view so the note, the needle and the scale always
 * line up, no matter the screen size.
 */
class TunerGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class State(
        val noteName: String = "",
        val octave: String = "",
        val cents: Double = 0.0,
        val frequency: Double = 0.0,
        val targetFrequency: Double = 0.0,
        val showTarget: Boolean = false,
        val active: Boolean = false
    )

    var state: State = State()
        set(value) {
            field = value
            invalidate()
        }

    var hintText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val colorAccent = context.getColor(io.github.figs0203.afinador.R.color.accent)
    private val colorWarn = 0xFFFFB454.toInt()
    private val colorError = 0xFFFF6B6B.toInt()
    private val colorMuted = context.getColor(io.github.figs0203.afinador.R.color.text_secondary)
    private val colorPrimary = context.getColor(io.github.figs0203.afinador.R.color.text_primary)
    private val colorTrack = 0xFF1B222A.toInt()
    private val colorTick = 0xFF2B3540.toInt()

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val octavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()

    /**
     * The gauge keeps a fixed aspect ratio so the arc always uses most of the
     * width; the height is capped by whatever space the layout offers.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (measuredWidth * 1.02f).toInt()
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> desiredHeight
            else -> min(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
        }
        setMeasuredDimension(measuredWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val centerX = width / 2f
        // The drawing is a fixed composition centred on the view, so it looks the
        // same whether the gauge is short or fills a tall screen.
        val radius = min(width * 0.44f, height * 0.43f)
        val centerY = height / 2f + radius * 0.17f
        val trackWidth = dp(9f)

        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        val active = state.active
        val accuracyColor = when {
            !active -> colorMuted
            abs(state.cents) <= IN_TUNE_CENTS -> colorAccent
            abs(state.cents) <= WARN_CENTS -> colorWarn
            else -> colorError
        }

        if (active) {
            glowPaint.shader = RadialGradient(
                centerX,
                centerY - radius * 0.15f,
                radius * 1.15f,
                intArrayOf(withAlpha(accuracyColor, 0.13f), withAlpha(accuracyColor, 0f)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(centerX, centerY - radius * 0.15f, radius * 1.15f, glowPaint)
            glowPaint.shader = null
        }

        // Arc track.
        arcPaint.color = colorTrack
        arcPaint.strokeWidth = trackWidth
        canvas.drawArc(arcRect, ARC_START, ARC_SWEEP, false, arcPaint)

        // In-tune band.
        val bandHalfAngle = (IN_TUNE_CENTS / FULL_SCALE_CENTS).toFloat() * ANGLE_HALF_SPAN
        arcPaint.color = if (active) withAlpha(colorAccent, 0.55f) else withAlpha(colorTick, 0.6f)
        arcPaint.strokeWidth = trackWidth
        canvas.drawArc(arcRect, CENTER_ANGLE - bandHalfAngle, bandHalfAngle * 2f, false, arcPaint)

        drawTicks(canvas, centerX, centerY, radius, active)
        drawDeviationLabels(canvas, centerX, centerY, radius)
        drawNeedle(canvas, centerX, centerY, radius, if (active) accuracyColor else colorTick)

        drawReadout(canvas, centerX, centerY, radius, accuracyColor)
    }

    private fun drawTicks(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, active: Boolean) {
        var step = -50
        while (step <= 50) {
            val angle = Math.toRadians((CENTER_ANGLE + step / FULL_SCALE_CENTS * ANGLE_HALF_SPAN).toDouble())
            val cosAngle = cos(angle).toFloat()
            val sinAngle = sin(angle).toFloat()
            val isMajor = step % 25 == 0
            val inner = radius + dp(6f)
            val outer = inner + if (isMajor) dp(9f) else dp(4f)

            tickPaint.color = when {
                step == 0 && active -> withAlpha(colorAccent, 0.9f)
                isMajor -> colorTick
                else -> withAlpha(colorTick, 0.55f)
            }
            tickPaint.strokeWidth = if (isMajor) dp(2f) else dp(1.4f)
            canvas.drawLine(
                centerX + cosAngle * inner,
                centerY + sinAngle * inner,
                centerX + cosAngle * outer,
                centerY + sinAngle * outer,
                tickPaint
            )
            step += 5
        }
    }

    private fun drawDeviationLabels(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        smallPaint.textSize = sp(12f)
        smallPaint.color = withAlpha(colorMuted, 0.75f)
        val distance = radius * 0.86f
        val flatAngle = Math.toRadians((CENTER_ANGLE - ANGLE_HALF_SPAN + 4.0).toDouble())
        val sharpAngle = Math.toRadians((CENTER_ANGLE + ANGLE_HALF_SPAN - 4.0).toDouble())
        drawCentered(
            canvas,
            "♭",
            centerX + cos(flatAngle).toFloat() * distance,
            centerY + sin(flatAngle).toFloat() * distance,
            smallPaint
        )
        drawCentered(
            canvas,
            "♯",
            centerX + cos(sharpAngle).toFloat() * distance,
            centerY + sin(sharpAngle).toFloat() * distance,
            smallPaint
        )
    }

    private fun drawNeedle(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, color: Int) {
        val clampedCents = state.cents.coerceIn(-FULL_SCALE_CENTS, FULL_SCALE_CENTS)
        val angle = Math.toRadians((CENTER_ANGLE + clampedCents / FULL_SCALE_CENTS * ANGLE_HALF_SPAN).toDouble())
        val cosAngle = cos(angle).toFloat()
        val sinAngle = sin(angle).toFloat()

        val inner = radius * 0.58f
        val outer = radius * 0.97f

        // Soft halo behind the needle.
        needlePaint.color = withAlpha(color, if (state.active) 0.18f else 0.10f)
        needlePaint.strokeWidth = dp(7f)
        canvas.drawLine(
            centerX + cosAngle * inner,
            centerY + sinAngle * inner,
            centerX + cosAngle * outer,
            centerY + sinAngle * outer,
            needlePaint
        )

        needlePaint.color = color
        needlePaint.strokeWidth = dp(2.5f)
        canvas.drawLine(
            centerX + cosAngle * inner,
            centerY + sinAngle * inner,
            centerX + cosAngle * outer,
            centerY + sinAngle * outer,
            needlePaint
        )
    }

    private fun drawReadout(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, accuracyColor: Int) {
        val active = state.active
        val noteCenterY = centerY + radius * 0.02f

        if (!active) {
            if (hintText.isNotEmpty()) {
                smallPaint.color = withAlpha(colorMuted, 0.95f)
                smallPaint.letterSpacing = 0f
                smallPaint.textSize = sp(14f)
                fitWidth(hintText, smallPaint, width * 0.80f, sp(10f))
                drawCentered(canvas, hintText, centerX, noteCenterY, smallPaint)
            }
            return
        }

        notePaint.textSize = radius * 0.68f
        notePaint.color = if (active) colorPrimary else withAlpha(colorPrimary, 0.35f)
        val letter = state.noteName.ifEmpty { "–" }

        octavePaint.textSize = radius * 0.24f
        octavePaint.color = if (active) accuracyColor else withAlpha(colorMuted, 0.5f)

        val letterWidth = notePaint.measureText(letter)
        val octave = if (active) state.octave else ""
        val octaveWidth = octavePaint.measureText(octave)
        val gap = dp(4f)
        val totalWidth = letterWidth + gap + octaveWidth
        val startX = centerX - totalWidth / 2f

        val metrics = notePaint.fontMetrics
        val baseline = noteCenterY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(letter, startX, baseline, notePaint)
        if (octave.isNotEmpty()) {
            val octaveMetrics = octavePaint.fontMetrics
            canvas.drawText(
                octave,
                startX + letterWidth + gap,
                baseline - (metrics.descent) + (octaveMetrics.descent),
                octavePaint
            )
        }

        if (active) {
            smallPaint.textSize = sp(13f)
            smallPaint.color = accuracyColor
            smallPaint.letterSpacing = 0.12f
            drawCentered(canvas, formatCents(state.cents), centerX, centerY + radius * 0.47f, smallPaint)
            smallPaint.letterSpacing = 0f

            smallPaint.textSize = sp(13f)
            smallPaint.color = withAlpha(colorMuted, 0.9f)
            val frequencyText = if (state.showTarget && state.targetFrequency > 0.0) {
                "${formatFrequency(state.frequency)} → ${formatFrequency(state.targetFrequency)} Hz"
            } else {
                "${formatFrequency(state.frequency)} Hz"
            }
            drawCentered(canvas, frequencyText, centerX, centerY + radius * 0.68f, smallPaint)
        }
    }

    /** Shrinks the text until it fits in [maxWidth], never below [minSize]. */
    private fun fitWidth(text: String, paint: Paint, maxWidth: Float, minSize: Float) {
        var size = paint.textSize
        val measured = paint.measureText(text)
        if (measured > maxWidth && measured > 0f) {
            size = (size * maxWidth / measured).coerceAtLeast(minSize)
            paint.textSize = size
        }
    }

    private fun formatCents(cents: Double): String {
        val rounded = cents.roundToInt()
        return when {
            rounded == 0 -> "0 ¢"
            rounded > 0 -> "+$rounded ¢"
            else -> "−${abs(rounded)} ¢"
        }
    }

    private fun formatFrequency(frequency: Double): String =
        if (frequency < 100.0) {
            String.format(Locale.US, "%.2f", frequency)
        } else {
            String.format(Locale.US, "%.1f", frequency)
        }

    private fun drawCentered(canvas: Canvas, text: String, x: Float, centerY: Float, paint: Paint) {
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, x - paint.measureText(text) / 2f, baseline, paint)
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * scaledDensity

    private fun withAlpha(color: Int, alpha: Float): Int {
        val safeAlpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        return (color and 0x00FFFFFF) or (safeAlpha shl 24)
    }

    companion object {
        /** Deviation considered in tune. */
        const val IN_TUNE_CENTS = 5.0

        /** Deviation at which the reading turns red. */
        const val WARN_CENTS = 20.0

        /** Half of the visible scale, in cents. */
        private const val FULL_SCALE_CENTS = 50.0

        private const val CENTER_ANGLE = 270f
        private const val ANGLE_HALF_SPAN = 65f
        private const val ARC_START = 205f
        private const val ARC_SWEEP = 130f
    }
}
