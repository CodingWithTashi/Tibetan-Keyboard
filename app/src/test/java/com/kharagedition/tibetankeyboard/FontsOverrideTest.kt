package com.kharagedition.tibetankeyboard

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the crash that took crash-free users to 69.5%: writing null into a static [android.graphics.Typeface]
 * field poisons it for the whole process, and the IME shares its process with every Compose screen.
 * A font that failed to load must leave the system default alone.
 */
class FontsOverrideTest {

    @Test
    fun nullTypeface_isNeverWrittenToTheStaticField() {
        assertFalse(FontsOverride.shouldReplace(null))
    }
}
