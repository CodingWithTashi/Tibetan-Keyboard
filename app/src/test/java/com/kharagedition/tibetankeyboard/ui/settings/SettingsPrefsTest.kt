package com.kharagedition.tibetankeyboard.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the PRO theme gate. Enforcing it only in the Settings picker let a lapsed subscriber keep
 * a premium theme forever, because the IME reads the preference store directly and never re-checked
 * the entitlement.
 */
class SettingsPrefsTest {

    @Test
    fun freeUser_fallsBackToTheFreeDefaults() {
        assertEquals(
            SettingsPrefs.DEFAULT_COLOR,
            SettingsPrefs.effectiveColor(SettingsPrefs.COLOR_BLACK, isPremium = false)
        )
        assertEquals(
            SettingsPrefs.DEFAULT_STYLE,
            SettingsPrefs.effectiveStyle(SettingsPrefs.STYLE_CLASSIC, isPremium = false)
        )
    }

    @Test
    fun subscriber_keepsTheirChoice() {
        assertEquals(
            SettingsPrefs.COLOR_BLACK,
            SettingsPrefs.effectiveColor(SettingsPrefs.COLOR_BLACK, isPremium = true)
        )
        assertEquals(
            SettingsPrefs.STYLE_CLASSIC,
            SettingsPrefs.effectiveStyle(SettingsPrefs.STYLE_CLASSIC, isPremium = true)
        )
    }

    @Test
    fun freeChoicesAreNeverTouched() {
        assertEquals(
            SettingsPrefs.COLOR_BROWN,
            SettingsPrefs.effectiveColor(SettingsPrefs.COLOR_BROWN, isPremium = false)
        )
        assertEquals(
            SettingsPrefs.STYLE_BORDERLESS,
            SettingsPrefs.effectiveStyle(SettingsPrefs.STYLE_BORDERLESS, isPremium = false)
        )
    }
}
