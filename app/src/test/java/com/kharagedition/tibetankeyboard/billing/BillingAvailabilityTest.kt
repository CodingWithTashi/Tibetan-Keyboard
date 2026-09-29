package com.kharagedition.tibetankeyboard.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The card checkout is a policy line: allowed only where Google Play Billing isn't available.
 * These cases pin that line down.
 */
class BillingAvailabilityTest {

    private val now = 1_800_000_000_000L
    private fun signals(
        storefront: String? = null,
        mcc: Int = 0,
        offeringsError: String? = null,
        failure: String? = null,
        failureAt: Long = 0L,
    ) = BillingSignals(storefront, mcc, offeringsError, failure, failureAt, now)

    @Test
    fun bhutanStorefront_getsWebCheckout() {
        assertEquals(BillingRoute.WEB_CHECKOUT, BillingAvailability.route(signals(storefront = "BT")))
        assertEquals(BillingRoute.WEB_CHECKOUT, BillingAvailability.route(signals(storefront = "bt")))
    }

    @Test
    fun sellableStorefront_staysOnPlay_evenWithABhutaneseSim() {
        // An Indian Play account used in Bhutan can pay through Play, so it must.
        assertEquals(BillingRoute.PLAY, BillingAvailability.route(signals(storefront = "IN", mcc = 402)))
        assertEquals(
            BillingRoute.PLAY,
            BillingAvailability.route(signals(storefront = "IN", mcc = 402, offeringsError = "ConfigurationError")),
        )
    }

    @Test
    fun unknownStorefront_bhutaneseSim_andNoProducts_getsWebCheckout() {
        assertEquals(
            BillingRoute.WEB_CHECKOUT,
            BillingAvailability.route(signals(mcc = 402, offeringsError = "ConfigurationError")),
        )
    }

    @Test
    fun unknownStorefront_bhutaneseSim_andRecentHardFailure_getsWebCheckout() {
        assertEquals(
            BillingRoute.WEB_CHECKOUT,
            BillingAvailability.route(
                signals(mcc = 402, failure = "ProductNotAvailableForPurchaseError", failureAt = now - 60_000),
            ),
        )
    }

    @Test
    fun bhutaneseSimAlone_isNotEnough() {
        assertEquals(BillingRoute.PLAY, BillingAvailability.route(signals(mcc = 402)))
    }

    @Test
    fun transientOrStaleFailures_dontCount() {
        assertEquals(
            BillingRoute.PLAY,
            BillingAvailability.route(signals(mcc = 402, failure = "NetworkError", failureAt = now - 60_000)),
        )
        val eightDaysAgo = now - 8L * 24 * 60 * 60 * 1000
        assertEquals(
            BillingRoute.PLAY,
            BillingAvailability.route(signals(mcc = 402, failure = "PurchaseNotAllowedError", failureAt = eightDaysAgo)),
        )
    }

    @Test
    fun hardFailureOutsideBhutan_staysOnPlay() {
        assertEquals(
            BillingRoute.PLAY,
            BillingAvailability.route(signals(failure = "PurchaseNotAllowedError", failureAt = now - 60_000)),
        )
    }

    @Test
    fun bhutanRemovedFromTheDashboardList_goesBackToPlay() {
        // If Google opens Play billing in Bhutan, the policy requires Play there from that day.
        val noWebCountries = signals(storefront = "BT").copy(webCheckoutCountries = emptySet())
        assertEquals(BillingRoute.PLAY, BillingAvailability.route(noWebCountries))
        val simOnly = signals(mcc = 402, offeringsError = "ConfigurationError").copy(webCheckoutCountries = emptySet())
        assertEquals(BillingRoute.PLAY, BillingAvailability.route(simOnly))
    }

    @Test
    fun nepal_staysOnPlay_withCardHelp() {
        assertEquals(BillingRoute.PLAY_DEGRADED, BillingAvailability.route(signals(storefront = "NP")))
    }

    @Test
    fun upsellsStayOff_onlyWhereNothingCanBeSold() {
        assertEquals(true, BillingAvailability.canSell(BillingRoute.PLAY, webCheckoutConfigured = false))
        assertEquals(true, BillingAvailability.canSell(BillingRoute.PLAY_DEGRADED, webCheckoutConfigured = false))
        assertEquals(true, BillingAvailability.canSell(BillingRoute.WEB_CHECKOUT, webCheckoutConfigured = true))
        assertEquals(false, BillingAvailability.canSell(BillingRoute.WEB_CHECKOUT, webCheckoutConfigured = false))
    }

    @Test
    fun reason_matchesTheRouteTaken() {
        assertEquals("storefront_BT", BillingAvailability.decide(signals(storefront = "bt")).reason)
        assertEquals("no_products", BillingAvailability.decide(signals(mcc = 402, offeringsError = "ConfigurationError")).reason)
        assertEquals(
            "hard_failure",
            BillingAvailability.decide(signals(mcc = 402, failure = "PurchaseNotAllowedError", failureAt = now - 60_000)).reason,
        )
        // A stale failure keeps the user on Play, so there is nothing to report.
        val eightDaysAgo = now - 8L * 24 * 60 * 60 * 1000
        assertEquals(null, BillingAvailability.decide(signals(mcc = 402, failure = "PurchaseNotAllowedError", failureAt = eightDaysAgo)).reason)
        assertEquals(null, BillingAvailability.decide(signals(storefront = "IN")).reason)
    }

    @Test
    fun onlyNonTransientCodes_areHardFailures() {
        listOf("PurchaseNotAllowedError", "ProductNotAvailableForPurchaseError").forEach {
            assertEquals(true, BillingAvailability.isHardFailure(it))
        }
        listOf("StoreProblemError", "NetworkError", "PurchaseCancelledError", null).forEach {
            assertEquals(false, BillingAvailability.isHardFailure(it))
        }
    }
}
