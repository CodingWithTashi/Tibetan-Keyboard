package com.kharagedition.tibetankeyboard.subscription

/** The facts about an offering the resolver needs — kept free of SDK types so it is unit-testable. */
data class OfferingSummary(
    val id: String,
    val hasPaywall: Boolean,
    val packageCount: Int,
)

/** Which paywall to render. */
sealed interface PaywallChoice {
    /** A paywall designed in the RevenueCat dashboard, attached to offering [offeringId]. */
    data class Dashboard(val offeringId: String) : PaywallChoice

    /** Our own Compose paywall, listing the packages of offering [offeringId]. */
    data class Custom(val offeringId: String) : PaywallChoice

    /** Nothing sellable could be loaded. */
    data object Unavailable : PaywallChoice
}

/**
 * Picks what the paywall shows for one placement.
 *
 * - The placement's offering, else RevenueCat's `current` one. A targeting rule or experiment
 *   variant without placements sends no placement mapping, so the SDK returns no placement
 *   offering; `current` is then the one it chose. (The SDK also returns null for a placement
 *   mapped to "No offering", so that setting can't hide the paywall; use the kill switches.)
 * - The legacy offerings ([BillingCatalog.LEGACY_OFFERINGS]) are never used: old builds pick
 *   their first package blindly, and its only package is mislabelled.
 * - An offering without a dashboard paywall is shown with our own screen. Handing it to
 *   RevenueCat's `Paywall()` would silently render a generic template instead.
 * - With nothing usable, fall back to [FALLBACK_OFFERING_ID] on our own screen.
 */
object PaywallOfferingResolver {
    const val FALLBACK_OFFERING_ID = BillingCatalog.OFFERING_PRO_V2

    fun resolve(
        placement: OfferingSummary?,
        fallback: OfferingSummary?,
        current: OfferingSummary? = null,
    ): PaywallChoice {
        val usable = listOfNotNull(placement, current).firstOrNull(::isUsable)
        if (usable != null) {
            return if (usable.hasPaywall) PaywallChoice.Dashboard(usable.id) else PaywallChoice.Custom(usable.id)
        }
        val backup = fallback?.takeIf(::isUsable)
        return if (backup != null) PaywallChoice.Custom(backup.id) else PaywallChoice.Unavailable
    }

    private fun isUsable(o: OfferingSummary) = o.id !in BillingCatalog.LEGACY_OFFERINGS && o.packageCount > 0
}
