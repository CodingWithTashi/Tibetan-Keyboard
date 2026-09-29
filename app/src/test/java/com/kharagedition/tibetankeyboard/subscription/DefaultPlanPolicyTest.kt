package com.kharagedition.tibetankeyboard.subscription

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultPlanPolicyTest {

    private val annualTrial = PlanSummary("\$rc_annual", Plan.ANNUAL, hasFreeTrial = true)
    private val annualNoTrial = annualTrial.copy(hasFreeTrial = false)
    private val monthlyTrial = PlanSummary("\$rc_monthly", Plan.MONTHLY, hasFreeTrial = true)
    private val lifetime = PlanSummary("\$rc_lifetime", Plan.LIFETIME, hasFreeTrial = false)

    @Test
    fun india_preselectsMonthly() {
        assertEquals(monthlyTrial, DefaultPlanPolicy.pick(listOf(annualTrial, monthlyTrial, lifetime), "IN"))
    }

    @Test
    fun elsewhere_preselectsAnnualWithTrial() {
        assertEquals(annualTrial, DefaultPlanPolicy.pick(listOf(annualTrial, monthlyTrial, lifetime), "US"))
        assertEquals(annualTrial, DefaultPlanPolicy.pick(listOf(annualTrial, monthlyTrial, lifetime), null))
    }

    @Test
    fun annualWithoutTrial_losesToMonthlyWithTrial() {
        // The 2.2.14 bug: annual had no trial, so pre-selecting it showed a full charge up front.
        assertEquals(monthlyTrial, DefaultPlanPolicy.pick(listOf(annualNoTrial, monthlyTrial, lifetime), "US"))
    }

    @Test
    fun noTrialsAnywhere_prefersAnnual() {
        val monthlyNoTrial = monthlyTrial.copy(hasFreeTrial = false)
        assertEquals(annualNoTrial, DefaultPlanPolicy.pick(listOf(monthlyNoTrial, annualNoTrial), "US"))
    }

    @Test
    fun indiaWithoutMonthly_fallsBackToTheUsualRule() {
        assertEquals(annualTrial, DefaultPlanPolicy.pick(listOf(annualTrial, lifetime), "IN"))
    }

    @Test
    fun noPlans_pickNothing() {
        assertNull(DefaultPlanPolicy.pick(emptyList(), "IN"))
    }
}
