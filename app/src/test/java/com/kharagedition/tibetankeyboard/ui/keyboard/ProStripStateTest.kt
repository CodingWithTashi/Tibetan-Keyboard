package com.kharagedition.tibetankeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the free-vs-PRO presentation of the keyboard's PRO strip — the conversion-critical UI.
 */
class ProStripStateTest {

    @Test
    fun freeUser_showsUpsell_andDimsLockedIcons() {
        val s = ProStripState.forPremium(isPremium = false)

        assertTrue("PRO pill must be shown to free users", s.pillVisible)
        assertTrue("Autocomplete upsell must be shown to free users", s.autocompleteVisible)
        assertEquals(ProStripState.LOCKED_ALPHA, s.chatAlpha, 0f)
        assertEquals(ProStripState.LOCKED_ALPHA, s.translateAlpha, 0f)
        assertEquals(ProStripState.LOCKED_ALPHA, s.autocompleteAlpha, 0f)
    }

    @Test
    fun premiumUser_hidesUpsell_andEnablesFeatures() {
        val s = ProStripState.forPremium(isPremium = true)

        assertFalse("PRO pill must be hidden for PRO users", s.pillVisible)
        assertFalse("Autocomplete chip must be hidden for PRO users", s.autocompleteVisible)
        assertEquals(ProStripState.ACTIVE_ALPHA, s.chatAlpha, 0f)
        assertEquals(ProStripState.ACTIVE_ALPHA, s.translateAlpha, 0f)
    }
}
