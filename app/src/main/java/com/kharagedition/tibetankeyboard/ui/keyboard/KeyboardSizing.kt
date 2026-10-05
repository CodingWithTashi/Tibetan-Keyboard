package com.kharagedition.tibetankeyboard.ui.keyboard

/**
 * How tall the keyboard is on a given screen.
 *
 * The key layouts (`res/xml`) declare 60dp rows. Five of them plus the bar are 360dp: right for
 * a tall phone held upright, but well over half of a 640dp-high phone once its navigation bar
 * is added, and nearly the whole screen of any phone turned on its side, where the keyboard
 * covered the field being typed in. So the rows shrink, and only shrink, until the keyboard
 * leaves the app its share of the screen. Held upright, a screen from 766dp high (a 360x800
 * phone, a Pixel 8) keeps the declared 60dp.
 *
 * Heights here are of the whole screen, system bars included; see [keyHeightDpFor].
 *
 * Pure, so the rule is unit-tested; [SizedKeyboard] applies it to the key layouts and the panels
 * that take their place ask for the matching heights.
 */
object KeyboardSizing {

    /** The row height the key layouts declare. Rows are never taller. */
    const val DESIGN_KEY_HEIGHT_DP = 60

    /** Rows are never shorter: under this a key is hard to hit, however short the screen. */
    const val MIN_KEY_HEIGHT_DP = 34

    /**
     * Rows of the tallest layouts (the number row and four more). The four-row layouts get the
     * same key height, so keys do not change size when the user switches between layouts.
     */
    const val ROWS = 5

    /** Ours and not a key row: the 48dp bar above the keys and the 12dp of padding around them. */
    const val CHROME_DP = 60

    /**
     * The most of the screen's height the bar and five rows may take, held upright. The
     * navigation bar under them comes on top of it: 48dp more on a three-button phone.
     */
    const val MAX_SHARE_PORTRAIT = 0.47f

    /** On its side a screen is short and every row is wide, so the keyboard may take more. */
    const val MAX_SHARE_LANDSCAPE = 0.60f

    /** The largest font size setting key labels follow in full (Android's "large" is 1.3). */
    const val MAX_LABEL_FONT_SCALE = 1.3f

    /** From this width a key is wide enough for a label at [MAX_LABEL_FONT_SCALE]. */
    private const val LABEL_FULL_WIDTH_DP = 360f

    /** The emoji grid on a screen with room for the declared rows, and while its search is open. */
    private const val EMOJI_GRID_DP = 200
    private const val EMOJI_GRID_SEARCHING_DP = 120

    /** One row of emoji; with search open the grid may go down to it, otherwise to two. */
    private const val EMOJI_ROW_DP = 44

    /** Row height, in dp, for a screen [screenHeightDp] high. */
    fun keyHeightDp(screenHeightDp: Int, landscape: Boolean): Int {
        val share = if (landscape) MAX_SHARE_LANDSCAPE else MAX_SHARE_PORTRAIT
        val fits = ((screenHeightDp * share - CHROME_DP) / ROWS).toInt()
        return fits.coerceIn(MIN_KEY_HEIGHT_DP, DESIGN_KEY_HEIGHT_DP)
    }

    /**
     * Height of a panel that replaces both the bar and the keys (the AI translate panel): what
     * they take together, so opening it does not move the top edge of the keyboard.
     */
    fun panelHeightDp(keyHeightDp: Int): Int = CHROME_DP + ROWS * keyHeightDp

    /** Height of the emoji grid: it gives up what the five key rows gave up, down to a floor. */
    fun emojiGridHeightDp(keyHeightDp: Int, searching: Boolean): Int {
        val given = ROWS * (DESIGN_KEY_HEIGHT_DP - keyHeightDp)
        return if (searching) (EMOJI_GRID_SEARCHING_DP - given).coerceAtLeast(EMOJI_ROW_DP)
        else (EMOJI_GRID_DP - given).coerceAtLeast(2 * EMOJI_ROW_DP)
    }

    /**
     * The font scale key labels (and the bar above them) are drawn at. They follow the system's
     * font size setting only as far as a key has room: at 200% the letters of neighbouring keys
     * ran into each other. Up to [MAX_LABEL_FONT_SCALE] on a screen 360dp wide or more, less on
     * a narrower one (its keys are narrower) and where the rows are short.
     */
    fun labelFontScale(systemFontScale: Float, screenWidthDp: Int, keyHeightDp: Int): Float {
        val widthAllows = MAX_LABEL_FONT_SCALE * minOf(1f, screenWidthDp / LABEL_FULL_WIDTH_DP)
        val heightAllows = keyHeightDp / MIN_KEY_HEIGHT_DP.toFloat()
        return minOf(systemFontScale, widthAllows, heightAllows)
    }

    /** True when the rows had to shrink: there is no height to spare on this screen. */
    fun isCompact(keyHeightDp: Int): Boolean = keyHeightDp < DESIGN_KEY_HEIGHT_DP
}
