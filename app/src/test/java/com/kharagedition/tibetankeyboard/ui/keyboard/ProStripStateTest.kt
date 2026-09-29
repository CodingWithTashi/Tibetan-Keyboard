package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.tibetankeyboard.subscription.SuggestionMode
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
        val s = ProStripState.forUser(isPremium = false, mode = SuggestionMode.LOCKED)

        assertTrue("PRO pill must be shown to free users", s.pillVisible)
        assertTrue("Autocomplete upsell must be shown to free users", s.autocompleteVisible)
        assertEquals(ProStripState.LOCKED_ALPHA, s.chatAlpha, 0f)
        assertEquals(ProStripState.LOCKED_ALPHA, s.translateAlpha, 0f)
        assertEquals(ProStripState.LOCKED_ALPHA, s.autocompleteAlpha, 0f)
    }

    @Test
    fun premiumUser_hidesUpsell_andEnablesFeatures() {
        // Whatever the quota says, PRO never sees locks or the Autocomplete upsell.
        for (mode in SuggestionMode.entries) {
            val s = ProStripState.forUser(isPremium = true, mode = mode)

            assertFalse("PRO pill must be hidden for PRO users", s.pillVisible)
            assertFalse("Autocomplete chip must be hidden for PRO users", s.autocompleteVisible)
            assertEquals(ProStripState.ACTIVE_ALPHA, s.chatAlpha, 0f)
            assertEquals(ProStripState.ACTIVE_ALPHA, s.translateAlpha, 0f)
            assertEquals(ProStripState.PRO_SUGGESTIONS, s.maxSuggestions)
        }
    }

    @Test
    fun freeUserWithSuggestionsLeft_getsLiveChips_withoutTheAutocompleteUpsell() {
        val s = ProStripState.forUser(isPremium = false, mode = SuggestionMode.LIVE)

        assertTrue("PRO pill stays for free users", s.pillVisible)
        assertFalse("Suggestions are showing, so the Autocomplete upsell is redundant", s.autocompleteVisible)
        assertEquals(ProStripState.FREE_SUGGESTIONS, s.maxSuggestions)
    }

    @Test
    fun freeUserOutOfSuggestions_seesTheLockedAutocompleteChip() {
        val s = ProStripState.forUser(isPremium = false, mode = SuggestionMode.LOCKED)

        assertTrue(s.autocompleteVisible)
        assertEquals(ProStripState.LOCKED_ALPHA, s.autocompleteAlpha, 0f)
        assertEquals("Padlocked chips need the room", ProStripState.LOCKED_SUGGESTIONS, s.maxSuggestions)
    }
}
