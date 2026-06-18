package com.kharagedition.tibetankeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the per-field layout hint that lets the Translate source field force
 * the keyboard onto the right script (round-trips through privateImeOptions).
 */
class KeyboardLayoutHintTest {

    @Test
    fun tibetanSource_roundTripsToTibetan() {
        val option = KeyboardLayoutHint.privateImeOption("bo")
        assertEquals(true, KeyboardLayoutHint.forcedTibetan(option))
    }

    @Test
    fun englishSource_roundTripsToQwerty() {
        val option = KeyboardLayoutHint.privateImeOption("en")
        assertEquals(false, KeyboardLayoutHint.forcedTibetan(option))
    }

    @Test
    fun nonTibetanSource_forcesQwerty() {
        // Chinese (or any non-Tibetan) source is typed in Latin, so → QWERTY.
        assertEquals(false, KeyboardLayoutHint.forcedTibetan(KeyboardLayoutHint.privateImeOption("zh-CN")))
    }

    @Test
    fun noHint_keepsUserChoice() {
        assertNull(KeyboardLayoutHint.forcedTibetan(null))
        assertNull(KeyboardLayoutHint.forcedTibetan(""))
        // A privateImeOptions string from some other app/IME feature, not ours.
        assertNull(KeyboardLayoutHint.forcedTibetan("com.example.other=flag"))
    }

    @Test
    fun hintAmongOtherOptions_isStillParsed() {
        // privateImeOptions is a comma-separated list; ours may not be first.
        val mixed = "com.example.other=flag,${KeyboardLayoutHint.privateImeOption("bo")}"
        assertEquals(true, KeyboardLayoutHint.forcedTibetan(mixed))
    }
}
