package com.kharagedition.tibetankeyboard.subscription

import java.net.URLEncoder

/** Which PRO plan the user holds. */
enum class PlanKind { MONTHLY, ANNUAL, LIFETIME, OTHER }

/** Where PRO was bought, which decides where it is managed. */
enum class BillingStore { PLAY, WEB, GRANTED, OTHER }

/** The facts about a user's PRO the manage screen needs — SDK-free so the rules are unit-testable. */
data class SubscriptionSnapshot(
    val plan: PlanKind,
    val store: BillingStore,
    val productId: String?,
    val isActive: Boolean,
    val willRenew: Boolean,
    val inTrial: Boolean,
    val hasBillingIssue: Boolean,
    /** Null for lifetime and for grants without an end. */
    val expiresAtMs: Long?,
    /** Current price as the store formats it, e.g. "$1.89"; null when unknown. */
    val price: String?,
    val managementUrl: String?,
    /** Play base plan the subscription is on, e.g. `base-renew-plan`. */
    val basePlanId: String? = null,
) {
    companion object {
        /** Maps a store product id (`monthly_premium_subscription`, `web_pro_annual`, …) to a plan. */
        fun planOf(productId: String?, expiresAtMs: Long?): PlanKind {
            val id = productId?.lowercase().orEmpty()
            return when {
                "lifetime" in id -> PlanKind.LIFETIME
                "annual" in id || "year" in id -> PlanKind.ANNUAL
                "monthly" in id || "month" in id -> PlanKind.MONTHLY
                expiresAtMs == null && id.isNotEmpty() -> PlanKind.LIFETIME
                else -> PlanKind.OTHER
            }
        }
    }
}

/** What the plan card says about the subscription. */
enum class SubscriptionStatus {
    /** Renews automatically. */
    ACTIVE,

    /** In a free trial that will convert. */
    TRIAL,

    /** Cancelled: PRO stays until the period ends, then stops. */
    ENDING,

    /** The store couldn't charge the last renewal; PRO is at risk. */
    BILLING_ISSUE,

    /** Bought once, never renews. */
    LIFETIME,

    /** Given by the developer (promotional grant), nothing to manage. */
    GRANTED,

    /** No active PRO. */
    INACTIVE;

    companion object {
        fun of(s: SubscriptionSnapshot): SubscriptionStatus = when {
            !s.isActive -> INACTIVE
            s.plan == PlanKind.LIFETIME -> LIFETIME
            s.store == BillingStore.GRANTED -> GRANTED
            s.hasBillingIssue -> BILLING_ISSUE
            !s.willRenew -> ENDING
            s.inTrial -> TRIAL
            else -> ACTIVE
        }
    }
}

/** Why a subscriber is cancelling; [key] is the analytics value. */
enum class CancelReason(val key: String) {
    TOO_EXPENSIVE("too_expensive"),
    NOT_USING("not_using"),
    MISSING_FEATURE("missing_feature"),
    BOUGHT_BY_MISTAKE("bought_by_mistake"),
    OTHER("other"),
}

/** The flow's steps, in order. */
enum class ManageStep { OVERVIEW, REASON, OFFER, HANDOFF }

object ManageSubscriptionPolicy {

    /** Whether the user can start a cancellation here (anything that renews and isn't a grant). */
    fun canCancel(s: SubscriptionSnapshot): Boolean = when (SubscriptionStatus.of(s)) {
        SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL, SubscriptionStatus.BILLING_ISSUE -> true
        else -> false
    }

    /**
     * Whether to show the half-price retention offer. Only to paying monthly Play subscribers
     * (the Play offer exists only on that base plan), never during a trial (nothing paid yet),
     * never to someone who bought by mistake (they want a refund, not a discount), and only once.
     */
    fun shouldOfferDiscount(
        reason: CancelReason?,
        s: SubscriptionSnapshot,
        offerAvailable: Boolean,
        alreadyAccepted: Boolean,
    ): Boolean = reason != null &&
        reason != CancelReason.BOUGHT_BY_MISTAKE &&
        offerAvailable &&
        !alreadyAccepted &&
        s.store == BillingStore.PLAY &&
        s.plan == PlanKind.MONTHLY &&
        SubscriptionStatus.of(s) == SubscriptionStatus.ACTIVE

    /**
     * Whether the retention offer, present on [offerBasePlanIds], can be given to someone on
     * [currentBasePlanId]. Play only moves a subscription onto an offer of ANOTHER base plan: it
     * rejects switching offers within the same one (tested: "Server error"), and a plain re-purchase
     * fails with mismatched account identifiers. Someone already on a base plan that carries the
     * offer has had it (or can't take it), so they never see it again.
     */
    fun offerApplies(currentBasePlanId: String?, offerBasePlanIds: Collection<String>): Boolean =
        offerBasePlanIds.isNotEmpty() && currentBasePlanId !in offerBasePlanIds

    /** Where Continue on the reason step leads. */
    fun stepAfterReason(showOffer: Boolean): ManageStep = if (showOffer) ManageStep.OFFER else ManageStep.HANDOFF

    /** Where Back leads from [step]; null means leave the screen. */
    fun previous(step: ManageStep, offerShown: Boolean): ManageStep? = when (step) {
        ManageStep.OVERVIEW -> null
        ManageStep.REASON -> ManageStep.OVERVIEW
        ManageStep.OFFER -> ManageStep.REASON
        ManageStep.HANDOFF -> if (offerShown) ManageStep.OFFER else ManageStep.REASON
    }

    const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
    const val PLAY_ORDER_HISTORY_URL = "https://play.google.com/store/account/orderhistory"

    /**
     * Where to manage [s]: a card subscription's billing portal, a Play subscription's own page
     * (Google's recommended deep link), else Play's subscription list. Null for a card subscription
     * without a portal link: Google Play has never heard of that product, so its page would be a
     * dead end, and the caller sends the user to support instead.
     */
    fun managementUrl(s: SubscriptionSnapshot?, packageName: String): String? = when {
        s?.store == BillingStore.WEB -> s.managementUrl
        s?.store == BillingStore.PLAY && s.productId != null ->
            "$PLAY_SUBSCRIPTIONS_URL?sku=${URLEncoder.encode(s.productId, "UTF-8")}&package=$packageName"
        else -> PLAY_SUBSCRIPTIONS_URL
    }

    /** Whole-number discount of [discountedMicros] against [fullMicros], e.g. 50; null if none. */
    fun percentOff(discountedMicros: Long, fullMicros: Long): Int? {
        if (fullMicros <= 0 || discountedMicros >= fullMicros) return null
        return Math.round(100.0 - discountedMicros * 100.0 / fullMicros).toInt().takeIf { it > 0 }
    }
}
