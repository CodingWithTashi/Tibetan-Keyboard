package com.kharagedition.tibetankeyboard.ui.keyboard

import android.content.Context
import android.hardware.display.DisplayManager
import android.inputmethodservice.Keyboard
import android.util.DisplayMetrics
import android.view.Display
import androidx.annotation.XmlRes

/**
 * The whole screen the keyboard is on, bars included. `Resources.displayMetrics` leaves the
 * system bars out before Android 15 and counts them from it, so the same phone would get
 * different keys depending on its Android version.
 */
private fun wholeScreen(context: Context): DisplayMetrics {
    val screen = DisplayMetrics()
    val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
        .getDisplay(Display.DEFAULT_DISPLAY)
    // Deprecated in favour of WindowMetrics, which needs a window: the keyboard service asks
    // before it has one.
    @Suppress("DEPRECATION")
    display.getRealMetrics(screen)
    return screen
}

/** [KeyboardSizing]'s row height, in dp, for the screen the keyboard is on. */
fun keyHeightDpFor(context: Context): Int {
    val screen = wholeScreen(context)
    return KeyboardSizing.keyHeightDp(
        screenHeightDp = (screen.heightPixels / screen.density).toInt(),
        landscape = screen.widthPixels > screen.heightPixels,
    )
}

/** [KeyboardSizing]'s font scale for key labels on the screen the keyboard is on. */
fun keyLabelFontScaleFor(context: Context): Float {
    val screen = wholeScreen(context)
    return KeyboardSizing.labelFontScale(
        systemFontScale = context.resources.configuration.fontScale,
        screenWidthDp = (screen.widthPixels / screen.density).toInt(),
        keyHeightDp = keyHeightDpFor(context),
    )
}

/**
 * A key layout whose rows are as tall as this screen allows (see [KeyboardSizing]) instead of
 * the height its XML declares, when that is shorter. Only the rows' height and position change:
 * every key keeps its codes, label, width and order, and a layout that already fits is left
 * exactly as the platform loaded it.
 */
@Suppress("DEPRECATION")
class SizedKeyboard(context: Context, @XmlRes layout: Int) : Keyboard(context, layout) {

    private val sizedHeight: Int

    init {
        val declared = keyHeight
        // Truncated like the platform truncates the XML's own dp, so 60dp is "no change".
        val wanted = (keyHeightDpFor(context) * context.resources.displayMetrics.density).toInt()
        if (wanted in 1 until declared) {
            // Rows sit at multiples of the declared height, so each lands on a multiple of the
            // new one and the rows stay flush.
            for (key in keys) {
                key.y = key.y * wanted / declared
                key.height = key.height * wanted / declared
            }
            sizedHeight = super.getHeight() * wanted / declared
        } else {
            sizedHeight = super.getHeight()
        }
    }

    /** What the view measures itself by, and the grid touches are looked up in. */
    override fun getHeight(): Int = sizedHeight
}
