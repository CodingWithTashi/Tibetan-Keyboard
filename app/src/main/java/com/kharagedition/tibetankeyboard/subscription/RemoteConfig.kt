package com.kharagedition.tibetankeyboard.subscription

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
) {
    companion object {
        const val META_FREE_SUGGESTIONS = "free_suggestions_per_day"
        const val META_ONBOARDING_ENABLED = "onboarding_paywall_enabled"
        const val META_WEB_CHECKOUT_COUNTRIES = "web_checkout_countries"

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
        )

        /** A JSON array (["BT"]) or a comma list ("BT,XX"); an empty one turns card checkout off. */
        private fun parseCountries(value: Any?): Set<String>? = when (value) {
            is List<*> -> value.mapNotNull { (it as? String)?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }.toSet()
            is String -> value.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
            else -> null
        }
    }
}
