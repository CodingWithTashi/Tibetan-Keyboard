package com.kharagedition.tibetankeyboard.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPaywallPolicyTest {

    private val today = 20_000L

    private fun decide(
        alreadyDone: Boolean = false,
        isPremium: Boolean? = false,
        enabled: Boolean = true,
        firstShownDay: Long? = null,
        day3Shown: Boolean = false,
        now: Long = today,
    ) = OnboardingPaywallPolicy.decide(alreadyDone, isPremium, enabled, firstShownDay, day3Shown, now)

    @Test
    fun rightAfterSetup_showsFirst() {
        assertEquals(OnboardingPaywall.FIRST, decide())
    }

    @Test
    fun premiumOrUnknown_showsNothing() {
        assertEquals(OnboardingPaywall.NONE, decide(isPremium = true))
        // Premium starts as "false" until RevenueCat answers; unknown must never show a paywall.
        assertEquals(OnboardingPaywall.NONE, decide(isPremium = null))
    }

    @Test
    fun killSwitch_showsNothing() {
        assertEquals(OnboardingPaywall.NONE, decide(enabled = false))
    }

    @Test
    fun existingUsers_getTheOneTimeVariant_andNeverDay3() {
        assertEquals(OnboardingPaywall.EXISTING, decide(alreadyDone = true))
        assertEquals(OnboardingPaywall.NONE, decide(alreadyDone = true, firstShownDay = today - 2))
        assertEquals(OnboardingPaywall.NONE, decide(alreadyDone = true, firstShownDay = today - 30))
    }

    @Test
    fun day3_showsOnceTwoDaysLater() {
        assertEquals(OnboardingPaywall.NONE, decide(firstShownDay = today))
        assertEquals(OnboardingPaywall.NONE, decide(firstShownDay = today - 1))
        assertEquals(OnboardingPaywall.DAY3, decide(firstShownDay = today - 2))
        assertEquals(OnboardingPaywall.DAY3, decide(firstShownDay = today - 30))
        assertEquals(OnboardingPaywall.NONE, decide(firstShownDay = today - 2, day3Shown = true))
    }

    @Test
    fun clockMovedBackwards_showsNothing() {
        assertEquals(OnboardingPaywall.NONE, decide(firstShownDay = today + 5))
    }
}
