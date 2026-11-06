package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceContent.WaveMode
import kotlin.math.PI
import kotlin.math.sin

/**
 * The voice panel's waveform: 36 gold bars.
 *  - [WaveMode.LIVE] scrolls the last 36 mic levels in from the right (newest brightest), so the
 *    user sees the phone hearing them.
 *  - [WaveMode.THINK] folds the bars into a slow travelling shimmer while the text is written.
 *  - [WaveMode.IDLE] is a row of faint dots.
 * With animations removed in the system settings the shimmer holds at a fixed shape.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var mode: WaveMode = WaveMode.IDLE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val levels = FloatArray(BARS)
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()

    /** Read once per attach, not per frame. */
    private var animate = true

    /** Forget the last recording's bars. */
    fun clear() {
        levels.fill(0f)
        invalidate()
    }

    /** Adds the newest mic level (0..1); the oldest scrolls off the left. */
    fun push(level: Float) {
        System.arraycopy(levels, 1, levels, 0, BARS - 1)
        levels[BARS - 1] = level.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animate = Motion.enabled(context)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), GOLD_200, GOLD_500, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val barWidth = BAR_DP * density
        val gap = (width - BARS * barWidth) / (BARS - 1)
        val minHeight = MIN_BAR_DP * density
        val maxHeight = height - 2 * density
        val phase = if (animate) SystemClock.uptimeMillis() / SHIMMER_MS * 2.0 * PI else 0.0

        for (i in 0 until BARS) {
            val (barHeight, alpha) = when (mode) {
                WaveMode.LIVE -> (minHeight + levels[i] * (maxHeight - minHeight)) to
                    (0.28f + 0.72f * i / (BARS - 1))
                WaveMode.THINK -> {
                    val wave = 0.5f + 0.5f * sin(phase - i * 0.35).toFloat()
                    (minHeight + wave * (maxHeight * 0.45f - minHeight)) to 0.75f
                }
                WaveMode.IDLE -> minHeight to 0.3f
            }
            val left = i * (barWidth + gap)
            val top = (height - barHeight) / 2f
            bar.set(left, top, left + barWidth, top + barHeight)
            paint.alpha = (alpha * 255).toInt()
            canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, paint)
        }
        if (mode == WaveMode.THINK && animate) postInvalidateOnAnimation()
    }

    private companion object {
        const val BARS = 36
        const val BAR_DP = 3f
        const val MIN_BAR_DP = 4f
        const val SHIMMER_MS = 1100.0
        const val GOLD_200 = 0xFFF7D899.toInt()
        const val GOLD_500 = 0xFFC8881F.toInt()
    }
}
