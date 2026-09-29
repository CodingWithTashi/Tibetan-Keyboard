package com.kharagedition.tibetankeyboard.subscription

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.Plan

/** A plan as the pre-selection rule sees it. [plan] is one of [Plan]. */
data class PlanSummary(val id: String, val plan: String, val hasFreeTrial: Boolean)

/**
 * Which plan our own paywall pre-selects. (Dashboard paywalls choose their own default, set per
 * country through Targeting.)
 *
 * India (storefront `IN`) pre-selects monthly: INR 60 with a free trial is what Indian users have
 * always bought, while annual-first sold nothing there. Everywhere else prefers annual, and a plan
 * with a free trial over one without — people start trials, they rarely pay up front.
 */
object DefaultPlanPolicy {
    fun pick(plans: List<PlanSummary>, storefront: String?): PlanSummary? {
        if (plans.isEmpty()) return null
        if (storefront.equals("IN", ignoreCase = true)) {
            plans.firstOrNull { it.plan == Plan.MONTHLY }?.let { return it }
        }
        return plans.firstOrNull { it.plan == Plan.ANNUAL && it.hasFreeTrial }
            ?: plans.firstOrNull { it.hasFreeTrial && it.plan != Plan.LIFETIME }
            ?: plans.firstOrNull { it.plan == Plan.ANNUAL }
            ?: plans.first()
    }
}
