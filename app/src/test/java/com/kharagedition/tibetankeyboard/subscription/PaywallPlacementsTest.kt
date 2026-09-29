package com.kharagedition.tibetankeyboard.subscription

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.UpgradeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PaywallPlacementsTest {

    @Test
    fun everyKnownSource_hasItsOwnPlacement() {
        // A new UpgradeSource that falls through to DEFAULT loses its per-gate targeting and
        // attribution in RevenueCat; this fails until it is given a placement.
        val known = UpgradeSource.ALL - UpgradeSource.UNKNOWN
        known.forEach { source ->
            assertNotEquals("$source falls back to the default placement", PaywallPlacements.DEFAULT, PaywallPlacements.forSource(source))
        }
        assertEquals("placements must be distinct", known.size, known.map(PaywallPlacements::forSource).toSet().size)
    }

    @Test
    fun onboardingShowings_keepTheirOwnPlacements() {
        assertEquals(PaywallPlacements.ONBOARDING, PaywallPlacements.forSource(UpgradeSource.ONBOARDING))
        assertEquals(PaywallPlacements.ONBOARDING_DAY3, PaywallPlacements.forSource(UpgradeSource.ONBOARDING_DAY3))
        assertEquals(PaywallPlacements.ONBOARDING_EXISTING, PaywallPlacements.forSource(UpgradeSource.ONBOARDING_EXISTING))
    }

    @Test
    fun unknownSource_usesDefault() {
        assertEquals(PaywallPlacements.DEFAULT, PaywallPlacements.forSource("something_new"))
    }
}
