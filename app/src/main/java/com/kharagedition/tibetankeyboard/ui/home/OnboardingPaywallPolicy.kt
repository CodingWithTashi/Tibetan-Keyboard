package com.kharagedition.tibetankeyboard.ui.home

/** Whether Home should open the paywall by itself, and which one. */
enum class OnboardingPaywall { NONE, FIRST, DAY3, EXISTING }

/**
 * The soft upsell, shown only once keyboard setup is complete: once right after it, once more on
 * day 3 or later, and just once for people who set the keyboard up before this paywall existed.
 * Never for PRO users, never when PRO status is still unknown (it starts as "not premium" until
 * RevenueCat answers), and never more than those times.
 */
object OnboardingPaywallPolicy {
    const val DAY3_AFTER_DAYS = 2L

    /**
     * @param setupWasAlreadyDone setup completed on an earlier app version, before this paywall shipped.
     * @param isPremium null while unknown.
     * @param enabled remote kill switch (RevenueCat offering metadata), and PRO sellable here.
     * @param firstShownDay local epoch day of the first showing, null if never shown.
     */
    fun decide(
        setupWasAlreadyDone: Boolean,
        isPremium: Boolean?,
        enabled: Boolean,
        firstShownDay: Long?,
        day3Shown: Boolean,
        today: Long,
    ): OnboardingPaywall {
        if (!enabled || isPremium != false) return OnboardingPaywall.NONE
        if (firstShownDay == null) {
            return if (setupWasAlreadyDone) OnboardingPaywall.EXISTING else OnboardingPaywall.FIRST
        }
        // Existing users get it once; new users once more, on day 3.
        if (setupWasAlreadyDone || day3Shown) return OnboardingPaywall.NONE
        val elapsed = today - firstShownDay
        // A clock moved backwards reads as negative; never treat that as "day 3".
        return if (elapsed >= DAY3_AFTER_DAYS) OnboardingPaywall.DAY3 else OnboardingPaywall.NONE
    }
}
