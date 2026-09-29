package com.kharagedition.tibetankeyboard.subscription

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.Plan
import com.kharagedition.tibetankeyboard.billing.BillingAvailability

/**
 * Dashboard switches, read from the metadata of [BillingCatalog.OFFERING_PRO_V2] so they change
 * without a release. Missing or malformed values fall back to the defaults.
 */
data class RemoteConfig(
    val freeSuggestionsPerDay: Int = SuggestionQuota.DEFAULT_DAILY_LIMIT,
    val onboardingPaywallEnabled: Boolean = true,
    /** Where card checkout replaces Play; see [BillingAvailability]. */
    val webCheckoutCountries: Set<String> = BillingAvailability.PLAY_UNAVAILABLE_COUNTRIES,
    /** Card checkout prices in USD by [Plan], after a price change in the dashboard. */
    val webPrices: Map<String, Double> = emptyMap(),
    /** Share of installs whose card checkout paywall pre-selects annual; see [WebPaywallExperiment]. */
    val webAnnualFirstPercent: Int = WebPaywallExperiment.DEFAULT_ANNUAL_FIRST_PERCENT,
) {
    companion object {
        const val META_FREE_SUGGESTIONS = "free_suggestions_per_day"
        const val META_ONBOARDING_ENABLED = "onboarding_paywall_enabled"
        const val META_WEB_CHECKOUT_COUNTRIES = "web_checkout_countries"
        const val META_WEB_PRICES = "web_prices"
        const val META_WEB_ANNUAL_FIRST_PERCENT = "web_annual_first_percent"

        private val PRICED_PLANS = setOf(Plan.MONTHLY, Plan.ANNUAL, Plan.LIFETIME)

        fun fromMetadata(meta: Map<String, Any?>): RemoteConfig = RemoteConfig(
            freeSuggestionsPerDay = (meta[META_FREE_SUGGESTIONS] as? Number)?.toInt()
                ?.takeIf { it >= 0 } ?: SuggestionQuota.DEFAULT_DAILY_LIMIT,
            onboardingPaywallEnabled = meta[META_ONBOARDING_ENABLED] as? Boolean ?: true,
            // The dashboard can only REMOVE countries (e.g. if Google opens Play billing in
            // Bhutan). Adding one — or a typo like "IN" — would put card checkout in a Play-billing
            // country, which Google's Payments policy forbids.
            webCheckoutCountries = parseCountries(meta[META_WEB_CHECKOUT_COUNTRIES])
                ?.intersect(BillingAvailability.PLAY_UNAVAILABLE_COUNTRIES)
                ?: BillingAvailability.PLAY_UNAVAILABLE_COUNTRIES,
            webPrices = parsePrices(meta[META_WEB_PRICES]),
            webAnnualFirstPercent = (meta[META_WEB_ANNUAL_FIRST_PERCENT] as? Number)?.toInt()
                ?.takeIf { it in 0..100 } ?: WebPaywallExperiment.DEFAULT_ANNUAL_FIRST_PERCENT,
        )

        /** `{"monthly": 1.99, "annual": 9.99}`; anything that isn't a known plan and a price is skipped. */
        private fun parsePrices(value: Any?): Map<String, Double> = (value as? Map<*, *>).orEmpty()
            .mapNotNull { (key, price) ->
                val plan = (key as? String)?.takeIf { it in PRICED_PLANS }
                val usd = (price as? Number)?.toDouble()?.takeIf { it > 0 }
                if (plan != null && usd != null) plan to usd else null
            }
            .toMap()

        /** A JSON array (["BT"]) or a comma list ("BT,XX"); an empty one turns card checkout off. */
        private fun parseCountries(value: Any?): Set<String>? = when (value) {
            is List<*> -> value.mapNotNull { (it as? String)?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }.toSet()
            is String -> value.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
            else -> null
        }
    }
}
