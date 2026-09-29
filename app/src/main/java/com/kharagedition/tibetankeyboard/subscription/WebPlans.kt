package com.kharagedition.tibetankeyboard.subscription

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.Plan
import java.util.Locale

/**
 * One plan of the card checkout ([WebCheckout]), as the in-app picker shows it. Google Play can't
 * return these products, so the price is ours to state ([BillingCatalog.WEB_PLANS], or the
 * dashboard's [RemoteConfig.webPrices]). The hosted checkout charges what the dashboard says and
 * shows it before any payment.
 */
data class WebPlan(
    /** Package id in the card checkout offering; the link opens that plan's checkout directly. */
    val packageId: String,
    /** One of [Plan]. */
    val plan: String,
    val priceUsd: Double,
    /** Free trial for customers who never bought anything; null for a one-time purchase. */
    val trialDays: Int? = null,
) {
    val price: String get() = usd(priceUsd)

    /** What a year costs per month; null for the other plans. */
    val pricePerMonth: String? get() = if (plan == Plan.ANNUAL) usd(priceUsd / MONTHS_PER_YEAR) else null

    private fun usd(amount: Double) = String.format(Locale.US, "$%.2f", amount)
}

object WebPlans {
    /** The plans in our paywall's order, at the dashboard's [prices] (keyed by [Plan]) where given. */
    fun list(prices: Map<String, Double> = emptyMap()): List<WebPlan> =
        BillingCatalog.WEB_PLANS.map { plan -> prices[plan.plan]?.let { plan.copy(priceUsd = it) } ?: plan }

    /** How much cheaper a year is than twelve months, in whole percent; null when it isn't. */
    fun annualSavingPercent(plans: List<WebPlan>): Int? {
        val monthly = plans.firstOrNull { it.plan == Plan.MONTHLY }?.priceUsd?.takeIf { it > 0 } ?: return null
        val annual = plans.firstOrNull { it.plan == Plan.ANNUAL }?.priceUsd ?: return null
        // Rounded down: the badge must never promise more than the prices give.
        return (100 - annual / MONTHS_PER_YEAR / monthly * 100).toInt().takeIf { it > 0 }
    }

    /** The plan [variant] pre-selects. */
    fun preselected(plans: List<WebPlan>, variant: WebPaywallVariant): WebPlan? =
        plans.firstOrNull { it.plan == variant.defaultPlan } ?: plans.firstOrNull()
}

/**
 * The A/B test of the card checkout paywall: which plan it pre-selects. RevenueCat's experiments
 * can't run it (they assign offerings of Google Play products, and Play sells none where this
 * paywall shows), so the app assigns the variant itself and reports it with every paywall event.
 */
enum class WebPaywallVariant(val label: String, val defaultPlan: String) {
    /** Control: monthly first, as in India, the closest market by purchasing power. */
    MONTHLY_FIRST("monthly_first", Plan.MONTHLY),
    ANNUAL_FIRST("annual_first", Plan.ANNUAL),
}

object WebPaywallExperiment {
    const val BUCKETS = 100
    const val DEFAULT_ANNUAL_FIRST_PERCENT = 50

    /**
     * [bucket] is this install's fixed number below [BUCKETS]; the lowest [annualFirstPercent] of
     * them get [WebPaywallVariant.ANNUAL_FIRST]. 0 or 100 ends the test: everyone gets the winner.
     */
    fun variant(bucket: Int, annualFirstPercent: Int = DEFAULT_ANNUAL_FIRST_PERCENT): WebPaywallVariant =
        if (bucket.mod(BUCKETS) < annualFirstPercent.coerceIn(0, 100)) {
            WebPaywallVariant.ANNUAL_FIRST
        } else {
            WebPaywallVariant.MONTHLY_FIRST
        }
}

private const val MONTHS_PER_YEAR = 12
