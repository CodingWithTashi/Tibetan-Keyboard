package com.kharagedition.tibetankeyboard.subscription

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebPlansTest {

    @Test
    fun listsAnnualMonthlyLifetime_likeOurOwnPaywall() {
        assertEquals(listOf(Plan.ANNUAL, Plan.MONTHLY, Plan.LIFETIME), WebPlans.list().map { it.plan })
        assertEquals(listOf("\$rc_annual", "\$rc_monthly", "\$rc_lifetime"), WebPlans.list().map { it.packageId })
    }

    @Test
    fun pricesReadAsDollars_andAYearAlsoPerMonth() {
        val plans = WebPlans.list().associateBy { it.plan }
        assertEquals("\$9.99", plans.getValue(Plan.ANNUAL).price)
        assertEquals("\$0.83", plans.getValue(Plan.ANNUAL).pricePerMonth)
        assertEquals("\$1.99", plans.getValue(Plan.MONTHLY).price)
        assertNull(plans.getValue(Plan.MONTHLY).pricePerMonth)
        assertNull(plans.getValue(Plan.LIFETIME).pricePerMonth)
    }

    @Test
    fun onlySubscriptionsHaveATrial() {
        val plans = WebPlans.list().associateBy { it.plan }
        assertEquals(7, plans.getValue(Plan.ANNUAL).trialDays)
        assertEquals(7, plans.getValue(Plan.MONTHLY).trialDays)
        assertNull(plans.getValue(Plan.LIFETIME).trialDays)
    }

    @Test
    fun dashboardPrices_replaceOurs() {
        val plans = WebPlans.list(mapOf(Plan.MONTHLY to 0.99)).associateBy { it.plan }
        assertEquals("\$0.99", plans.getValue(Plan.MONTHLY).price)
        assertEquals("\$9.99", plans.getValue(Plan.ANNUAL).price)
    }

    @Test
    fun annualSaving_isRoundedDown() {
        // 9.99 a year is 0.8325 a month against 1.99: 58.2% cheaper.
        assertEquals(58, WebPlans.annualSavingPercent(WebPlans.list()))
    }

    @Test
    fun noSaving_noBadge() {
        assertNull(WebPlans.annualSavingPercent(WebPlans.list(mapOf(Plan.ANNUAL to 23.88))))
        assertNull(WebPlans.annualSavingPercent(WebPlans.list(mapOf(Plan.ANNUAL to 30.0))))
        assertNull(WebPlans.annualSavingPercent(WebPlans.list().filter { it.plan != Plan.MONTHLY }))
    }

    @Test
    fun eachVariantPreselectsItsPlan() {
        val plans = WebPlans.list()
        assertEquals(Plan.MONTHLY, WebPlans.preselected(plans, WebPaywallVariant.MONTHLY_FIRST)?.plan)
        assertEquals(Plan.ANNUAL, WebPlans.preselected(plans, WebPaywallVariant.ANNUAL_FIRST)?.plan)
        assertNull(WebPlans.preselected(emptyList(), WebPaywallVariant.ANNUAL_FIRST))
    }

    @Test
    fun experiment_splitsTheBucketsByThePercent() {
        val annualFirst = (0 until WebPaywallExperiment.BUCKETS).count {
            WebPaywallExperiment.variant(it) == WebPaywallVariant.ANNUAL_FIRST
        }
        assertEquals(WebPaywallExperiment.DEFAULT_ANNUAL_FIRST_PERCENT, annualFirst)
        assertEquals(WebPaywallVariant.ANNUAL_FIRST, WebPaywallExperiment.variant(bucket = 29, annualFirstPercent = 30))
        assertEquals(WebPaywallVariant.MONTHLY_FIRST, WebPaywallExperiment.variant(bucket = 30, annualFirstPercent = 30))
    }

    @Test
    fun experiment_endsWithEveryoneOnTheWinner() {
        (0 until WebPaywallExperiment.BUCKETS).forEach { bucket ->
            assertEquals(WebPaywallVariant.MONTHLY_FIRST, WebPaywallExperiment.variant(bucket, annualFirstPercent = 0))
            assertEquals(WebPaywallVariant.ANNUAL_FIRST, WebPaywallExperiment.variant(bucket, annualFirstPercent = 100))
        }
    }

    @Test
    fun experiment_toleratesAnyNumber() {
        assertEquals(WebPaywallExperiment.variant(5), WebPaywallExperiment.variant(105))
        assertEquals(WebPaywallExperiment.variant(95), WebPaywallExperiment.variant(-5))
        assertEquals(WebPaywallVariant.ANNUAL_FIRST, WebPaywallExperiment.variant(bucket = 99, annualFirstPercent = 250))
    }
}
