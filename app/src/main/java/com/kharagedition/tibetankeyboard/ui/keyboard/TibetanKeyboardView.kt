package com.kharagedition.tibetankeyboard.ui.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

/**
 * KeyboardView that paints the Enter/return key as a gold cap (the design's signature
 * accent) on top of whatever key background the current layout uses. Purely visual —
 * touch handling and key codes are untouched.
 */
class TibetanKeyboardView(context: Context?, attrs: AttributeSet?) : KeyboardView(context, attrs) {

    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val radius = 10f * density
    private val inset = 3f * density

    @SuppressLint("DrawAllocation")
    @Deprecated("Deprecated in Java")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kb = keyboard ?: return
        for (key in kb.keys) {
            val codes = key.codes ?: continue
            if (codes.isEmpty() || codes[0] != ENTER_CODE) continue

            val left = key.x + inset
            val top = key.y + inset
            val right = key.x + key.width - inset
            val bottom = key.y + key.height - inset

            goldPaint.shader = LinearGradient(left, top, left, bottom, GOLD_TOP, GOLD_BOTTOM, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, goldPaint)

            // Re-draw the return icon (super already drew the white one underneath the gold).
            key.icon?.let { icon ->
                val d = icon.mutate()
                val w = d.intrinsicWidth
                val h = d.intrinsicHeight
                val cx = key.x + key.width / 2
                val cy = key.y + key.height / 2
                d.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
                d.setTint(ESPRESSO)
                d.draw(canvas)
            }
        }
    }

    companion object {
        private const val ENTER_CODE = -4 // Keyboard.KEYCODE_DONE
        private val GOLD_TOP = Color.parseColor("#F2C264")
        private val GOLD_BOTTOM = Color.parseColor("#C8881F")
        private val ESPRESSO = Color.parseColor("#1C1305")
    }
}
