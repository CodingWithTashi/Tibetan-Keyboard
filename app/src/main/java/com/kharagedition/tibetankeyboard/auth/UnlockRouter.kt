package com.kharagedition.tibetankeyboard.auth

/** Where a free user tapping a PRO control should be sent. */
enum class UnlockDestination {
    /** Not signed in: go to login first (login then forwards to the paywall). */
    LOGIN_THEN_PREMIUM,

    /** Signed in but not subscribed: go straight to the premium paywall. */
    PREMIUM,
}

/**
 * Pure routing decision for the "unlock PRO" flow, separated from Activity/IME plumbing so the
 * conversion-critical branch is unit-testable.
 */
object UnlockRouter {
    fun destinationFor(isAuthenticated: Boolean): UnlockDestination =
        if (isAuthenticated) UnlockDestination.PREMIUM else UnlockDestination.LOGIN_THEN_PREMIUM
}
