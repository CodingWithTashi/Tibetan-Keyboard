package com.kharagedition.tibetankeyboard.subscription

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.Plan

/**
 * RevenueCat and Google Play identifiers the app depends on. They must match the dashboards;
 * change them here, never inline.
 */
object BillingCatalog {
    /** The one entitlement every PRO product unlocks. */
    const val ENTITLEMENT_PRO = "pro"

    /** Our own paywall's offering, and the carrier of the dashboard switches ([RemoteConfig]). */
    const val OFFERING_PRO_V2 = "pro_v2"

    /**
     * Builds before 2.2.14 buy `current.availablePackages.first()` of this offering, and its only
     * package is mislabelled; it is never shown by this app and must never gain packages.
     */
    val LEGACY_OFFERINGS = setOf("sale")

    /**
     * Play retention offer (50% off for 3 months), tagged `rc-ignore-offer` so it is never a
     * package's default option. Only applied where [ManageSubscriptionPolicy.offerApplies].
     */
    const val RETENTION_OFFER_ID = "retention-50off-3m"

    /** How our Compose paywall is named in RevenueCat's paywall-impression analytics. */
    const val CUSTOM_PAYWALL_ID = "in_app_compose"

    /**
     * The card checkout's plans (offering `pro_web_bt`, RevenueCat Billing, USD), in our paywall's
     * order. Play can't return these products, so their prices are repeated here: change them
     * together with the web products in the dashboard.
     */
    val WEB_PLANS = listOf(
        WebPlan(packageId = "\$rc_annual", plan = Plan.ANNUAL, priceUsd = 9.99, trialDays = 7),
        WebPlan(packageId = "\$rc_monthly", plan = Plan.MONTHLY, priceUsd = 1.99, trialDays = 7),
        WebPlan(packageId = "\$rc_lifetime", plan = Plan.LIFETIME, priceUsd = 19.99),
    )
}
