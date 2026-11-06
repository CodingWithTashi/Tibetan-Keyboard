package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceContent.OrbMode
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The big gold mic of the voice panel, and everything that moves around it.
 *
 *  - READY breathes softly to invite a tap.
 *  - LISTENING swells two glow rings with the voice and sends a ripple out every second.
 *  - The last ten seconds fill a gold ring around it ([setEnding]).
 *  - BUSY is a dark orb with a gold arc circling it while the text is written.
 *  - CANCEL turns red while a hold is slid left past the cancel line.
 *
 * The view is square and as large as the controls row; the orb sits in the middle and the rings
 * stay inside the view. It owns one gesture, push to talk: recording starts the instant the finger
 * lands (waiting to tell a tap from a hold would cost the first syllable), ends when it lifts, and
 * a slide to the left cancels. What those mean is the [Listener]'s business. With animations
 * removed in the system settings it holds still.
 */
class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** Only TalkBack's double tap: it cannot hold, so it starts and stops with a click. */
        fun onTap()
        /** The finger landed: start recording. */
        fun onHoldStart()
        fun onSlide(armed: Boolean)
        /** [cancelled] is true when the system took the touch away (a call, a gesture), not the user. */
        fun onHoldEnd(cancelled: Boolean)
    }

    var listener: Listener? = null

    /** The orb's diameter in dp; the view itself is as large as the controls row. */
    var orbDiameterDp = 80f
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcBounds = RectF()

    /** Read once per attach, not per frame: a settings lookup 60 times a second is waste. */
    private var animate = true

    // Gradients are made once, at unit radius, and scaled onto the canvas: building a shader per
    // frame would churn the garbage collector inside the keyboard's process.
    private val glowShader = RadialGradient(
        0f, 0f, 1f, intArrayOf(withAlpha(GOLD_300, 0.38f), withAlpha(GOLD_300, 0f)),
        floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP,
    )
    private val discShaders = OrbMode.values().associateWith { mode ->
        when (mode) {
            OrbMode.BUSY -> RadialGradient(0f, -0.3f, 1.4f, BROWN_600, BROWN_700, Shader.TileMode.CLAMP)
            OrbMode.CANCEL -> LinearGradient(-1f, -1f, 1f, 1f, MAROON_SOFT, MAROON, Shader.TileMode.CLAMP)
            else -> LinearGradient(
                -1f, -1f, 1f, 1f, intArrayOf(GOLD_300, GOLD_400, GOLD_500),
                floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP,
            )
        }
    }
    private val arcShader = SweepGradient(
        0f, 0f, intArrayOf(withAlpha(GOLD_300, 0f), withAlpha(GOLD_300, 0.15f), GOLD_300, withAlpha(GOLD_300, 0f)),
        floatArrayOf(0f, 0.33f, 0.92f, 1f),
    )

    private var mode = OrbMode.READY
    private var held = false
    private var pressedDown = false
    private var ending = 0f
    private var targetLevel = 0f
    private var level = 0f
    private var orbScale = 1f
    private var scaleAnimator: ValueAnimator? = null

    private val micIcon = tinted(R.drawable.ic_voice_mic, INK)
    private val sparkleIcon = tinted(R.drawable.ic_voice_sparkle, GOLD_300)
    private val closeIcon = tinted(R.drawable.ic_close, CREAM)

    // --- state from the panel ---

    /** [mode] and whether a hold is in progress ([held]); the orb animates to its new look. */
    fun render(mode: OrbMode, held: Boolean) {
        if (this.mode == mode && this.held == held) return
        this.mode = mode
        this.held = held
        if (mode != OrbMode.LISTENING && mode != OrbMode.CANCEL) {
            targetLevel = 0f
            level = 0f
        }
        animateScale()
        invalidate()
    }

    /** The microphone's level, 0..1, at about 20 Hz while listening. */
    fun setLevel(value: Float) {
        targetLevel = value.coerceIn(0f, 1f)
        invalidate()
    }

    /** How much of the gold ring around the orb is drawn, 0..1: the last ten seconds. */
    fun setEnding(progress: Float) {
        if (ending == progress) return
        ending = progress
        invalidate()
    }

    // --- drawing ---

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f
        val orbRadius = orbDiameterDp * density / 2f * orbScale
        val now = SystemClock.uptimeMillis()

        level += (targetLevel - level) * LEVEL_SMOOTHING

        when (mode) {
            OrbMode.READY -> drawGlow(canvas, cx, cy, orbRadius, outer, now)
            OrbMode.LISTENING -> drawListening(canvas, cx, cy, orbRadius, outer, now)
            OrbMode.BUSY, OrbMode.CANCEL -> Unit
        }
        if (ending > 0f && mode == OrbMode.LISTENING) drawEndingRing(canvas, cx, cy, outer)

        drawDisc(canvas, cx, cy, orbRadius)
        drawIcon(canvas, cx, cy, orbRadius)
        if (mode == OrbMode.BUSY) drawBusyArc(canvas, cx, cy, orbRadius, now)

        val moving = mode == OrbMode.LISTENING || mode == OrbMode.BUSY || mode == OrbMode.READY
        if (animate && moving) postInvalidateOnAnimation()
    }

    /** READY: a soft glow that slowly swells and settles. */
    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, orbRadius: Float, outer: Float, now: Long) {
        val breath = if (animate) 0.5f + 0.5f * sin(now / BREATH_MS * 2.0 * PI).toFloat() else 0.5f
        val radius = min(outer, orbRadius * (1.3f + 0.2f * breath))
        paint.style = Paint.Style.FILL
        paint.shader = glowShader
        paint.alpha = ((0.58f + 0.42f * breath) * 255).toInt()
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        paint.shader = null
        paint.alpha = 255
    }

    /** LISTENING: two rings that swell with the voice, and a ripple every second. */
    private fun drawListening(canvas: Canvas, cx: Float, cy: Float, orbRadius: Float, outer: Float, now: Long) {
        val room = outer - orbRadius
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(GOLD_300, 0.14f)
        canvas.drawCircle(cx, cy, orbRadius + room * (0.4f + 0.6f * level), paint)
        paint.color = withAlpha(GOLD_300, 0.22f)
        canvas.drawCircle(cx, cy, orbRadius + room * (0.25f + 0.45f * level), paint)

        if (!animate) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        for (offset in longArrayOf(0L, RIPPLE_MS / 2)) {
            val progress = ((now + offset) % RIPPLE_MS) / RIPPLE_MS.toFloat()
            paint.color = withAlpha(GOLD_300, (1f - progress) * 0.55f)
            canvas.drawCircle(cx, cy, orbRadius + room * progress, paint)
        }
    }

    /** The last ten seconds: a gold ring fills clockwise from the top. */
    private fun drawEndingRing(canvas: Canvas, cx: Float, cy: Float, outer: Float) {
        val stroke = 3f * density
        val radius = outer - stroke
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = withAlpha(GOLD_300, 0.16f)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = GOLD_300
        canvas.drawArc(arcBounds, -90f, 360f * ending, false, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawDisc(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        // A shader is drawn at the paint's alpha, and the ripple left it faint: reset it, or the
        // orb comes out dull.
        paint.alpha = 255
        paint.shader = discShaders.getValue(mode)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        paint.shader = null

        // A fine inner highlight along the top edge gives the disc its raised, lit look.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = withAlpha(0xFFFFFFFF.toInt(), if (mode == OrbMode.BUSY) 0.10f else 0.35f)
        arcBounds.set(cx - radius + density, cy - radius + density, cx + radius - density, cy + radius - density)
        canvas.drawArc(arcBounds, 200f, 140f, false, paint)
    }

    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val half = radius * 0.42f
        when (mode) {
            OrbMode.READY -> micIcon.draw(canvas, cx, cy, half)
            OrbMode.BUSY -> sparkleIcon.draw(canvas, cx, cy, half)
            OrbMode.CANCEL -> closeIcon.draw(canvas, cx, cy, half)
            // A finger held down is already saying "let go to send": the mic stays, a stop square
            // would be a button nobody needs to press.
            OrbMode.LISTENING -> if (held) micIcon.draw(canvas, cx, cy, half) else {
                // Tap mode (TalkBack's double tap): a rounded square says "tap to finish".
                val side = radius * 0.42f
                paint.style = Paint.Style.FILL
                paint.color = INK
                arcBounds.set(cx - side, cy - side, cx + side, cy + side)
                canvas.drawRoundRect(arcBounds, side * 0.3f, side * 0.3f, paint)
            }
        }
    }

    private fun android.graphics.drawable.Drawable.draw(canvas: Canvas, cx: Float, cy: Float, half: Float) {
        setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        draw(canvas)
    }

    /** BUSY: a gold arc circles the dark orb. */
    private fun drawBusyArc(canvas: Canvas, cx: Float, cy: Float, radius: Float, now: Long) {
        val arcRadius = radius + 6f * density
        val rotation = if (animate) (now % ARC_MS) / ARC_MS.toFloat() * 360f else 0f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * density / arcRadius // drawn at unit radius, then scaled up
        paint.strokeCap = Paint.Cap.ROUND
        paint.alpha = 255 // see drawDisc: the disc's highlight left it faint
        paint.shader = arcShader
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation)
        canvas.scale(arcRadius, arcRadius)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        paint.shader = null
        paint.strokeCap = Paint.Cap.BUTT
    }

    // --- press feel ---

    /** Pressed sinks to 92%; a hold grows to 110%; released it springs back a little past full size. */
    private fun animateScale() {
        val target = when {
            pressedDown && !held -> 0.92f
            held -> 1.1f
            mode == OrbMode.LISTENING -> 1.03f
            else -> 1f
        }
        scaleAnimator?.cancel()
        if (!animate) {
            orbScale = target
            invalidate()
            return
        }
        val settling = target >= orbScale
        scaleAnimator = ValueAnimator.ofFloat(orbScale, target).apply {
            duration = if (settling) 340L else 90L
            interpolator = if (settling) OvershootInterpolator(2f) else DecelerateInterpolator()
            addUpdateListener {
                orbScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // --- gestures ---

    private var downX = 0f
    private var holdStarted = false
    private var slideArmed = false

    init {
        // TalkBack only offers (and announces) a click on a view that says it can be clicked.
        isClickable = true
        isFocusable = true
    }

    // A finger's press is the whole gesture (hold to talk), so it must not also call performClick():
    // that would start a second, tap-style recording on release. TalkBack reaches onTap through
    // performClick() below instead.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                pressedDown = true
                parent?.requestDisallowInterceptTouchEvent(true)
                animateScale()
                holdStarted = true
                slideArmed = false
                listener?.onHoldStart()
            }
            MotionEvent.ACTION_MOVE -> if (holdStarted) {
                val armed = VoicePolicy.cancelArmed((event.rawX - downX) / density)
                if (armed != slideArmed) {
                    slideArmed = armed
                    listener?.onSlide(armed)
                }
            }
            MotionEvent.ACTION_UP -> {
                pressedDown = false
                animateScale()
                if (holdStarted) {
                    holdStarted = false
                    listener?.onHoldEnd(cancelled = false)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedDown = false
                animateScale()
                if (holdStarted) {
                    holdStarted = false
                    listener?.onHoldEnd(cancelled = true)
                }
            }
        }
        return true
    }

    /** TalkBack's double tap (a finger never gets here: its press is the whole gesture). */
    override fun performClick(): Boolean {
        super.performClick()
        listener?.onTap()
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animate = Motion.enabled(context)
    }

    override fun onDetachedFromWindow() {
        scaleAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    // --- helpers ---

    private fun tinted(res: Int, color: Int): android.graphics.drawable.Drawable =
        DrawableCompat.wrap(ContextCompat.getDrawable(context, res)!!.mutate()).also {
            DrawableCompat.setTint(it, color)
        }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private companion object {
        const val LEVEL_SMOOTHING = 0.35f
        const val BREATH_MS = 2600.0
        const val RIPPLE_MS = 2000L
        const val ARC_MS = 1100L

        const val GOLD_300 = 0xFFF2C264.toInt()
        const val GOLD_400 = 0xFFE3A53A.toInt()
        const val GOLD_500 = 0xFFC8881F.toInt()
        const val INK = 0xFF3A2606.toInt()
        const val CREAM = 0xFFFCF5E8.toInt()
        const val BROWN_600 = 0xFF4D3812.toInt()
        const val BROWN_700 = 0xFF3C2B0E.toInt()
        const val MAROON = 0xFF8E2E27.toInt()
        const val MAROON_SOFT = 0xFFC9584C.toInt()
    }
}
