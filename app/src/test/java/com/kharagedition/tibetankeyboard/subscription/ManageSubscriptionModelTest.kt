package com.kharagedition.tibetankeyboard.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageSubscriptionModelTest {

    private val monthly = SubscriptionSnapshot(
        plan = PlanKind.MONTHLY,
        store = BillingStore.PLAY,
        productId = "monthly_premium_subscription",
        isActive = true,
        willRenew = true,
        inTrial = false,
        hasBillingIssue = false,
        expiresAtMs = 1_800_000_000_000L,
        price = "$1.89",
        managementUrl = null,
    )

    @Test
    fun planOf_readsProductIds() {
        assertEquals(PlanKind.MONTHLY, SubscriptionSnapshot.planOf("monthly_premium_subscription", 1L))
        assertEquals(PlanKind.ANNUAL, SubscriptionSnapshot.planOf("annual_premium_subscription", 1L))
        assertEquals(PlanKind.LIFETIME, SubscriptionSnapshot.planOf("lifetime_premium_unlock", null))
        assertEquals(PlanKind.ANNUAL, SubscriptionSnapshot.planOf("web_pro_annual", 1L))
        assertEquals(PlanKind.LIFETIME, SubscriptionSnapshot.planOf("some_unlock", null))
        assertEquals(PlanKind.OTHER, SubscriptionSnapshot.planOf(null, 1L))
    }

    @Test
    fun status_coversEachState() {
        assertEquals(SubscriptionStatus.ACTIVE, SubscriptionStatus.of(monthly))
        assertEquals(SubscriptionStatus.TRIAL, SubscriptionStatus.of(monthly.copy(inTrial = true)))
        assertEquals(SubscriptionStatus.ENDING, SubscriptionStatus.of(monthly.copy(willRenew = false)))
        assertEquals(SubscriptionStatus.ENDING, SubscriptionStatus.of(monthly.copy(willRenew = false, inTrial = true)))
        assertEquals(SubscriptionStatus.BILLING_ISSUE, SubscriptionStatus.of(monthly.copy(hasBillingIssue = true)))
        assertEquals(
            SubscriptionStatus.LIFETIME,
            SubscriptionStatus.of(monthly.copy(plan = PlanKind.LIFETIME, willRenew = false, expiresAtMs = null)),
        )
        assertEquals(SubscriptionStatus.GRANTED, SubscriptionStatus.of(monthly.copy(store = BillingStore.GRANTED)))
        assertEquals(SubscriptionStatus.INACTIVE, SubscriptionStatus.of(monthly.copy(isActive = false)))
    }

    @Test
    fun onlyRenewingSubscriptions_canBeCancelled() {
        assertTrue(ManageSubscriptionPolicy.canCancel(monthly))
        assertTrue(ManageSubscriptionPolicy.canCancel(monthly.copy(inTrial = true)))
        assertFalse(ManageSubscriptionPolicy.canCancel(monthly.copy(willRenew = false)))
        assertFalse(ManageSubscriptionPolicy.canCancel(monthly.copy(plan = PlanKind.LIFETIME, expiresAtMs = null)))
        assertFalse(ManageSubscriptionPolicy.canCancel(monthly.copy(store = BillingStore.GRANTED)))
    }

    @Test
    fun discount_goesToPayingMonthlyPlaySubscribers() {
        assertTrue(offer(CancelReason.TOO_EXPENSIVE))
        assertTrue(offer(CancelReason.NOT_USING))
        assertTrue(offer(CancelReason.OTHER))
    }

    @Test
    fun discount_isNeverShownWhereItCantHelpOrApply() {
        assertFalse(offer(CancelReason.BOUGHT_BY_MISTAKE))
        assertFalse(offer(null))
        assertFalse(offer(CancelReason.TOO_EXPENSIVE, s = monthly.copy(inTrial = true)))
        assertFalse(offer(CancelReason.TOO_EXPENSIVE, s = monthly.copy(plan = PlanKind.ANNUAL)))
        assertFalse(offer(CancelReason.TOO_EXPENSIVE, s = monthly.copy(store = BillingStore.WEB)))
        assertFalse(offer(CancelReason.TOO_EXPENSIVE, s = monthly.copy(hasBillingIssue = true)))
        assertFalse(offer(CancelReason.TOO_EXPENSIVE, available = false))
        assertFalse(offer(CancelReason.TOO_EXPENSIVE, accepted = true))
    }

    @Test
    fun offer_mustSitOnAnotherBasePlan_andNeverRepeat() {
        // Play can't switch offers within the base plan the user is already on.
        assertFalse(ManageSubscriptionPolicy.offerApplies("base-renew-plan", listOf("base-renew-plan")))
        assertTrue(ManageSubscriptionPolicy.offerApplies("base-renew-plan", listOf("retention-monthly")))
        // Already moved onto the retention plan: an offer elsewhere must not discount them again.
        assertFalse(ManageSubscriptionPolicy.offerApplies("retention-monthly", listOf("retention-monthly", "base-renew-plan")))
        assertFalse(ManageSubscriptionPolicy.offerApplies("base-renew-plan", emptyList()))
    }

    @Test
    fun steps_goForwardAndBackInOrder() {
        assertEquals(ManageStep.OFFER, ManageSubscriptionPolicy.stepAfterReason(showOffer = true))
        assertEquals(ManageStep.HANDOFF, ManageSubscriptionPolicy.stepAfterReason(showOffer = false))
        assertNull(ManageSubscriptionPolicy.previous(ManageStep.OVERVIEW, offerShown = false))
        assertEquals(ManageStep.OVERVIEW, ManageSubscriptionPolicy.previous(ManageStep.REASON, offerShown = false))
        assertEquals(ManageStep.REASON, ManageSubscriptionPolicy.previous(ManageStep.OFFER, offerShown = true))
        assertEquals(ManageStep.OFFER, ManageSubscriptionPolicy.previous(ManageStep.HANDOFF, offerShown = true))
        assertEquals(ManageStep.REASON, ManageSubscriptionPolicy.previous(ManageStep.HANDOFF, offerShown = false))
    }

    @Test
    fun managementUrl_pointsAtTheRightPlace() {
        val pkg = "com.kharagedition.tibetankeyboard"
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=monthly_premium_subscription&package=$pkg",
            ManageSubscriptionPolicy.managementUrl(monthly, pkg),
        )
        val web = monthly.copy(store = BillingStore.WEB, managementUrl = "https://billing.example/portal")
        assertEquals("https://billing.example/portal", ManageSubscriptionPolicy.managementUrl(web, pkg))
        assertEquals(ManageSubscriptionPolicy.PLAY_SUBSCRIPTIONS_URL, ManageSubscriptionPolicy.managementUrl(null, pkg))
    }

    @Test
    fun managementUrl_neverSendsACardSubscriptionToGooglePlay() {
        val pkg = "com.kharagedition.tibetankeyboard"
        val web = monthly.copy(store = BillingStore.WEB, productId = "web_pro_monthly", managementUrl = null)
        assertNull(ManageSubscriptionPolicy.managementUrl(web, pkg))
        // A grant's product id is not a Play product either: the list, not a page Play can't find.
        val granted = monthly.copy(store = BillingStore.GRANTED, productId = "rc_promo_pro_monthly")
        assertEquals(ManageSubscriptionPolicy.PLAY_SUBSCRIPTIONS_URL, ManageSubscriptionPolicy.managementUrl(granted, pkg))
    }

    @Test
    fun percentOff_roundsAndIgnoresNonDiscounts() {
        assertEquals(50, ManageSubscriptionPolicy.percentOff(945_000, 1_890_000))
        assertEquals(50, ManageSubscriptionPolicy.percentOff(950_000, 1_890_000))
        assertNull(ManageSubscriptionPolicy.percentOff(1_890_000, 1_890_000))
        assertNull(ManageSubscriptionPolicy.percentOff(100, 0))
    }

    private fun offer(
        reason: CancelReason?,
        s: SubscriptionSnapshot = monthly,
        available: Boolean = true,
        accepted: Boolean = false,
    ) = ManageSubscriptionPolicy.shouldOfferDiscount(reason, s, available, accepted)
}
