package com.kharagedition.tibetankeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSizingTest {

    private fun portrait(heightDp: Int) = KeyboardSizing.keyHeightDp(heightDp, landscape = false)
    private fun landscape(heightDp: Int) = KeyboardSizing.keyHeightDp(heightDp, landscape = true)

    /** Bar and five rows as a share of the screen's height. */
    private fun share(heightDp: Int, keyHeightDp: Int) =
        KeyboardSizing.panelHeightDp(keyHeightDp) / heightDp.toFloat()

    @Test
    fun `a tall phone held upright keeps the declared rows`() {
        // Pixel 8 (914dp), a 393x851 and a 360x800 phone, and the shortest screen that fits them.
        listOf(914, 851, 800, 766).forEach { assertEquals(60, portrait(it)) }
        // Tablets and unfolded foldables too, either way up.
        assertEquals(60, portrait(1280))
        assertEquals(60, landscape(800))
        assertEquals(60, landscape(673))
    }

    @Test
    fun `rows never grow past the declared height`() {
        assertEquals(KeyboardSizing.DESIGN_KEY_HEIGHT_DP, portrait(5000))
        assertEquals(KeyboardSizing.DESIGN_KEY_HEIGHT_DP, landscape(5000))
    }

    @Test
    fun `a short phone gets shorter rows and keeps about half its screen`() {
        // 16:9 phones (411x731, 360x640), 320dp-wide ones (569, 533), and a Pixel 8 set to its
        // largest display size (711).
        listOf(731, 711, 640, 569, 533).forEach { height ->
            val key = portrait(height)
            assertTrue("$height: $key", key < 60)
            assertTrue("$height: ${share(height, key)}", share(height, key) <= 0.47f)
        }
        assertEquals(56, portrait(731))
        assertEquals(48, portrait(640))
        assertEquals(41, portrait(569))
    }

    @Test
    fun `a phone on its side leaves room for the field being typed in`() {
        // 60dp rows made the keyboard 360dp of a Pixel 8's 411: with the bars, all of it.
        assertEquals(37, landscape(411))
        assertTrue(share(411, landscape(411)) <= 0.60f)
        assertTrue(share(411, 60) > 0.85f)
    }

    @Test
    fun `rows stop shrinking at the smallest key that can be hit`() {
        assertEquals(KeyboardSizing.MIN_KEY_HEIGHT_DP, landscape(360))
        assertEquals(KeyboardSizing.MIN_KEY_HEIGHT_DP, landscape(240))
        assertEquals(KeyboardSizing.MIN_KEY_HEIGHT_DP, portrait(200))
    }

    @Test
    fun `the panel that replaces bar and keys is as tall as they are`() {
        // 48dp bar + 12dp padding + five rows; 360dp is the height it always had.
        assertEquals(360, KeyboardSizing.panelHeightDp(60))
        assertEquals(245, KeyboardSizing.panelHeightDp(37))
    }

    @Test
    fun `the emoji grid gives up what the rows gave up, down to a floor`() {
        assertEquals(200, KeyboardSizing.emojiGridHeightDp(60, searching = false))
        assertEquals(120, KeyboardSizing.emojiGridHeightDp(60, searching = true))
        assertEquals(170, KeyboardSizing.emojiGridHeightDp(54, searching = false))
        assertEquals(90, KeyboardSizing.emojiGridHeightDp(54, searching = true))
        // On its side: two rows of emoji, one while the search keyboard is up.
        assertEquals(88, KeyboardSizing.emojiGridHeightDp(37, searching = false))
        assertEquals(44, KeyboardSizing.emojiGridHeightDp(37, searching = true))
    }

    @Test
    fun `key labels follow the font size setting as far as a key has room`() {
        fun scale(system: Float, widthDp: Int, keyDp: Int) =
            KeyboardSizing.labelFontScale(system, screenWidthDp = widthDp, keyHeightDp = keyDp)
        // Default and "large" are followed in full on an ordinary phone held upright.
        assertEquals(1.0f, scale(1.0f, 411, 60), 0.001f)
        assertEquals(1.3f, scale(1.3f, 360, 48), 0.001f)
        // A smaller-than-default setting is the user's choice too.
        assertEquals(0.85f, scale(0.85f, 411, 60), 0.001f)
        // 200% is not followed: neighbouring letters ran into each other.
        assertEquals(1.3f, scale(2.0f, 411, 60), 0.001f)
        assertEquals(1.3f, scale(2.0f, 1280, 60), 0.001f)
        // Narrower keys and shorter rows hold less.
        assertEquals(1.3f * 320 / 360, scale(2.0f, 320, 60), 0.001f)
        assertEquals(1.0f, scale(1.3f, 640, 34), 0.001f)
    }

    @Test
    fun `compact means the rows had to shrink`() {
        assertFalse(KeyboardSizing.isCompact(60))
        assertTrue(KeyboardSizing.isCompact(59))
    }
}
