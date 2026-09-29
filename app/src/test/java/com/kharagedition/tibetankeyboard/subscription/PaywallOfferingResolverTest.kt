package com.kharagedition.tibetankeyboard.subscription

import org.junit.Assert.assertEquals
import org.junit.Test

class PaywallOfferingResolverTest {

    private val proV2 = OfferingSummary("pro_v2", hasPaywall = false, packageCount = 3)

    @Test
    fun placementWithDashboardPaywall_isShownAsDashboard() {
        val placement = OfferingSummary("pro_trial_monthly", hasPaywall = true, packageCount = 3)
        assertEquals(PaywallChoice.Dashboard("pro_trial_monthly"), PaywallOfferingResolver.resolve(placement, proV2))
    }

    @Test
    fun placementWithoutPaywall_usesOurScreen_neverRevenueCatsGenericTemplate() {
        val placement = OfferingSummary("pro_trial_annual", hasPaywall = false, packageCount = 3)
        assertEquals(PaywallChoice.Custom("pro_trial_annual"), PaywallOfferingResolver.resolve(placement, proV2))
    }

    @Test
    fun legacySaleOffering_isNeverShown() {
        // Customers matching no targeting rule get `current`, which is still `sale`.
        val sale = OfferingSummary("sale", hasPaywall = true, packageCount = 1)
        assertEquals(PaywallChoice.Custom("pro_v2"), PaywallOfferingResolver.resolve(sale, proV2))
    }

    @Test
    fun noPlacements_fallsBackToProV2() {
        assertEquals(PaywallChoice.Custom("pro_v2"), PaywallOfferingResolver.resolve(null, proV2))
    }

    @Test
    fun ruleWithoutPlacements_usesTheCurrentOffering() {
        // "Everyone else" has no placements, so RevenueCat only sets `current`.
        val annual = OfferingSummary("pro_trial_annual", hasPaywall = true, packageCount = 3)
        assertEquals(
            PaywallChoice.Dashboard("pro_trial_annual"),
            PaywallOfferingResolver.resolve(placement = null, fallback = proV2, current = annual),
        )
    }

    @Test
    fun placementOffering_winsOverCurrent() {
        val monthly = OfferingSummary("pro_trial_monthly", hasPaywall = true, packageCount = 3)
        assertEquals(
            PaywallChoice.Custom("pro_v2"),
            PaywallOfferingResolver.resolve(placement = proV2, fallback = proV2, current = monthly),
        )
    }

    @Test
    fun currentStillSale_fallsBackToProV2() {
        // Customers matching no targeting rule (e.g. before 2.3.0 is recorded) get `sale`.
        val sale = OfferingSummary("sale", hasPaywall = true, packageCount = 1)
        assertEquals(
            PaywallChoice.Custom("pro_v2"),
            PaywallOfferingResolver.resolve(placement = null, fallback = proV2, current = sale),
        )
    }

    @Test
    fun legacyPlacement_fallsThroughToCurrent() {
        // Placements whose fallback is the legacy `sale` must not hide a targeting/experiment choice.
        val sale = OfferingSummary("sale", hasPaywall = true, packageCount = 1)
        val annual = OfferingSummary("pro_trial_annual", hasPaywall = true, packageCount = 3)
        assertEquals(
            PaywallChoice.Dashboard("pro_trial_annual"),
            PaywallOfferingResolver.resolve(placement = sale, fallback = proV2, current = annual),
        )
    }

    @Test
    fun emptyOfferings_areUnavailable() {
        val empty = OfferingSummary("pro_trial_annual", hasPaywall = true, packageCount = 0)
        val emptyFallback = proV2.copy(packageCount = 0)
        assertEquals(PaywallChoice.Unavailable, PaywallOfferingResolver.resolve(empty, emptyFallback))
        assertEquals(PaywallChoice.Unavailable, PaywallOfferingResolver.resolve(null, null))
    }
}
