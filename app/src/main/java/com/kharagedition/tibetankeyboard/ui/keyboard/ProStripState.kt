package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.tibetankeyboard.subscription.SuggestionMode

/**
 * Pure (Android-free) UI state for the keyboard's PRO feature strip, derived from the user's
 * entitlement. Kept as plain data so the free-vs-PRO presentation rules are unit-testable
 * without inflating the IME view.
 *
 *  - Free users: gold upsell pill visible; Chat + Translate dimmed (locked). While today's free
 *    suggestions last, real suggestions fill the strip; once they run out the suggestions turn
 *    into padlocked chips. Any locked control routes to the unlock flow.
 *  - PRO users: the pill is hidden (autocomplete just works while typing); Chat + Translate are
 *    full opacity and functional.
 *
 * The mic is not here: voice typing is open to everyone, so it is never locked or hidden by
 * entitlement (see `VoiceAvailability` for when a field may not have it).
 */
data class ProStripState(
    val pillVisible: Boolean,
    val chatAlpha: Float,
    val translateAlpha: Float,
    /** How many suggestion chips fit next to the controls that are showing. */
    val maxSuggestions: Int,
) {
    companion object {
        const val ACTIVE_ALPHA = 1f
        const val LOCKED_ALPHA = 0.5f

        /** PRO has the whole strip; free users share it with the upsell pill. */
        const val PRO_SUGGESTIONS = 4
        const val FREE_SUGGESTIONS = 3

        /** Locked chips also carry a padlock, so they are wider and fewer. */
        const val LOCKED_SUGGESTIONS = 2

        fun forUser(isPremium: Boolean, mode: SuggestionMode): ProStripState = ProStripState(
            pillVisible = !isPremium,
            chatAlpha = if (isPremium) ACTIVE_ALPHA else LOCKED_ALPHA,
            translateAlpha = if (isPremium) ACTIVE_ALPHA else LOCKED_ALPHA,
            maxSuggestions = when {
                isPremium -> PRO_SUGGESTIONS
                mode == SuggestionMode.LOCKED -> LOCKED_SUGGESTIONS
                else -> FREE_SUGGESTIONS
            },
        )
    }
}
