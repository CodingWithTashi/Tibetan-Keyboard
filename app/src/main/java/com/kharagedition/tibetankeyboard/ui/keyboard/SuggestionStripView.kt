package com.kharagedition.tibetankeyboard.ui.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Even-width suggestion strip — 4 chips always fill the full keyboard width.
 * Extends LinearLayout directly (no HorizontalScrollView) so weight distribution works.
 */
class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onSuggestionClick: ((String) -> Unit)? = null
    private var themeColor = Color.parseColor("#FF704C04")

    init {
        orientation = HORIZONTAL
        setBackgroundColor(themeColor)
    }

    fun setThemeColor(colorInt: Int) {
        themeColor = colorInt
        setBackgroundColor(colorInt)
    }

    fun setSuggestions(suggestions: List<String>) {
        removeAllViews()
        suggestions.forEachIndexed { index, word ->
            if (index > 0) addView(makeDivider())
            addView(makeChip(word))
        }
    }

    private fun makeChip(word: String): TextView = TextView(context).apply {
        text = word
        textSize = 15f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setSingleLine(true)
        // weight=1 so all chips share width equally
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        background = ripple(Color.argb(60, 255, 255, 255))
        isClickable = true
        isFocusable = true
        setOnClickListener { onSuggestionClick?.invoke(word) }
    }

    private fun makeDivider(): View = View(context).apply {
        setBackgroundColor(Color.argb(60, 255, 255, 255))
        layoutParams = LayoutParams(dp(1), dp(18)).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun ripple(color: Int) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            RippleDrawable(ColorStateList.valueOf(color), ColorDrawable(Color.TRANSPARENT), null)
        else
            ColorDrawable(Color.TRANSPARENT)

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
