package com.kharagedition.tibetankeyboard.ui.keyboard

/**
 * Pure (Android-free) UI state for the keyboard's PRO feature strip, derived from the user's
 * entitlement. Kept as plain data so the free-vs-PRO presentation rules are unit-testable
 * without inflating the IME view.
 *
 *  - Free users: gold upsell pill + Autocomplete chip are visible; all feature icons are dimmed
 *    (locked). Tapping any control routes to the unlock flow.
 *  - PRO users: the pill and Autocomplete chip are hidden (autocomplete just works while typing);
 *    Chat + Translate are full opacity and functional.
 */
data class ProStripState(
    val pillVisible: Boolean,
    val autocompleteVisible: Boolean,
    val chatAlpha: Float,
    val translateAlpha: Float,
    val autocompleteAlpha: Float,
) {
    companion object {
        const val ACTIVE_ALPHA = 1f
        const val LOCKED_ALPHA = 0.5f

        fun forPremium(isPremium: Boolean): ProStripState = ProStripState(
            pillVisible = !isPremium,
            autocompleteVisible = !isPremium,
            chatAlpha = if (isPremium) ACTIVE_ALPHA else LOCKED_ALPHA,
            translateAlpha = if (isPremium) ACTIVE_ALPHA else LOCKED_ALPHA,
            // Autocomplete is only ever shown to free users, so it always reads as locked.
            autocompleteAlpha = LOCKED_ALPHA,
        )
    }
}
