package com.kharagedition.tibetankeyboard.billing

/** How this user can pay for PRO. */
enum class BillingRoute {
    /** Google Play Billing, the normal path. */
    PLAY,

    /** Google Play, in a country where most cards fail (Nepal): Play plus a card-help note. */
    PLAY_DEGRADED,

    /**
     * Card checkout outside Play. Only where Play Billing is not available: Google's Payments
     * policy FAQ exempts those countries from the Play-billing requirement. Never offered in a
     * country Play can sell to.
     */
    WEB_CHECKOUT,
}

/** Everything known about this user's ability to pay through Play. SDK-free for unit tests. */
data class BillingSignals(
    /** Play Billing country (ISO-3166 alpha-2), null until Play has answered. */
    val storefront: String?,
    /** SIM mobile country code, 0 when unknown. 402 is Bhutan. */
    val simMcc: Int,
    /** RevenueCat error code from the last failed offerings load, if any. */
    val offeringsErrorCode: String?,
    /** RevenueCat error code of the last purchase that failed for a non-transient reason. */
    val lastHardFailureCode: String?,
    val lastHardFailureAtMs: Long,
    val nowMs: Long,
    /**
     * Countries treated as "Play can't sell here". Set from the RevenueCat dashboard so that if
     * Google opens Play billing in one of them, it can be removed without a release — from then on
     * the policy requires Play there.
     */
    val webCheckoutCountries: Set<String> = BillingAvailability.PLAY_UNAVAILABLE_COUNTRIES,
)

/**
 * Decides [BillingRoute]. Bhutan is not a Google Play buyer country (no base plan has a Bhutan
 * price and every purchase attempt there fails), so Bhutanese users would otherwise be shown a buy
 * button that can never work.
 *
 * The check is deliberately conservative because it is also a policy line: a user whose Play
 * account can buy must use Play, even if they are in Bhutan.
 */
object BillingAvailability {
    /**
     * Countries where Google Play Billing is not available (Google's paid-app buyer list omits
     * them). Default for [BillingSignals.webCheckoutCountries].
     */
    val PLAY_UNAVAILABLE_COUNTRIES = setOf("BT")

    /** Play charges USD here and domestic cards/wallets can't pay it. */
    val DEGRADED_COUNTRIES = setOf("NP")

    const val BHUTAN_MCC = 402

    /** How long a hard failure keeps the user on web checkout before Play is tried again. */
    const val HARD_FAILURE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    // RevenueCat PurchasesErrorCode names that mean "Play can't sell to this account" — not
    // a flaky network or a cancelled sheet.
    private val HARD_FAILURE_CODES = setOf(
        "PurchaseNotAllowedError",
        "ProductNotAvailableForPurchaseError",
    )

    /** Offerings failing this way means Play returned none of our products at all. */
    private const val NO_PRODUCTS_CODE = "ConfigurationError"

    fun isHardFailure(code: String?): Boolean = code in HARD_FAILURE_CODES

    /**
     * Whether PRO can be bought at all on [route]. False only on card checkout before its link is
     * configured: then upsells (onboarding paywall, locked suggestions) would lead nowhere, so they
     * must stay off rather than annoy people who can't pay.
     */
    fun canSell(route: BillingRoute, webCheckoutConfigured: Boolean): Boolean =
        route != BillingRoute.WEB_CHECKOUT || webCheckoutConfigured

    fun route(s: BillingSignals): BillingRoute = decide(s).route

    /**
     * The route and, for card checkout, why — decided together so the logged reason always
     * matches the route actually taken.
     */
    fun decide(s: BillingSignals): BillingDecision {
        val storefront = s.storefront?.uppercase()
        if (storefront != null && storefront in s.webCheckoutCountries) {
            return BillingDecision(BillingRoute.WEB_CHECKOUT, "storefront_$storefront")
        }
        // Unknown storefront: only a Bhutanese SIM plus proof that Play can't sell counts.
        // A known sellable storefront (e.g. an Indian Play account used in Bhutan) stays on Play.
        if (storefront == null && s.simMcc == BHUTAN_MCC && "BT" in s.webCheckoutCountries) {
            playCannotSellReason(s)?.let { return BillingDecision(BillingRoute.WEB_CHECKOUT, it) }
        }
        if (storefront in DEGRADED_COUNTRIES) return BillingDecision(BillingRoute.PLAY_DEGRADED, null)
        return BillingDecision(BillingRoute.PLAY, null)
    }

    /** Why Play can't sell to this account, or null if nothing proves it. */
    private fun playCannotSellReason(s: BillingSignals): String? {
        if (s.offeringsErrorCode == NO_PRODUCTS_CODE) return "no_products"
        val recent = s.nowMs - s.lastHardFailureAtMs in 0..HARD_FAILURE_WINDOW_MS
        return if (recent && isHardFailure(s.lastHardFailureCode)) "hard_failure" else null
    }
}

/**
 * [route] for this user; [reason] (only for [BillingRoute.WEB_CHECKOUT]) is the short label for
 * `AppAnalytics.logBillingUnavailable`.
 */
data class BillingDecision(val route: BillingRoute, val reason: String?)
