package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.UpgradeSource

/**
 * What a free user's tap on a locked PRO control in the keyboard bar does.
 *
 * The bar sits right above the top key row, and its paywall opens full-screen over the app being
 * typed in. It was the largest source of paywall opens (new users in Bhutan, week to 2026-10-03:
 * six in ten opens with a recorded source, 2.7 per user who opened one), and a tap there is as
 * likely a slip as a wish to buy. So the bar asks first: the tap swaps the bar for one line and
 * a "See plans" button, and only that button opens the paywall. Typing on, the close button or
 * a few seconds put the bar back.
 */
object KeyboardUpsell {

    /** A locked control in the bar; [source] is the `UpgradeSource` its sale is attributed to. */
    enum class Control(val source: String) {
        PILL(UpgradeSource.KEYBOARD),
        CHAT(UpgradeSource.KEYBOARD),
        TRANSLATE(UpgradeSource.KEYBOARD),
        /** The Autocomplete icon, shown once today's free suggestions are used. */
        AUTOCOMPLETE(UpgradeSource.KEYBOARD_SUGGESTIONS),
        LOCKED_SUGGESTION(UpgradeSource.KEYBOARD_SUGGESTIONS),
    }

    enum class Action { ASK_FIRST, OPEN_PAYWALL }

    /** How long the question stays in the bar when nothing is tapped. */
    const val PROMPT_TIMEOUT_MS = 6_000L

    /**
     * The question's buttons sit where the bar's icons are: "See plans" over Chat and
     * Autocomplete, close over Translate. The second tap of a double tap would therefore land on
     * whatever has just replaced the first tap's target, opening the paywall after all, or
     * closing the question unread, or asking again after it was closed. Taps this soon after the
     * bar swapped under the finger are ignored.
     */
    const val SWAP_GUARD_MS = 500L

    fun isStrayTap(msSinceBarSwapped: Long): Boolean = msSinceBarSwapped in 0 until SWAP_GUARD_MS

    /** [confirmFirst] is the dashboard switch `RemoteConfig.keyboardUpsellConfirm`. */
    fun onLockedTap(confirmFirst: Boolean): Action =
        if (confirmFirst) Action.ASK_FIRST else Action.OPEN_PAYWALL
}
