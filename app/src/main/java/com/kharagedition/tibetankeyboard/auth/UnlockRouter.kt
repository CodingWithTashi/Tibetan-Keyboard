package com.kharagedition.tibetankeyboard.auth

/** Where a user tapping a PRO control should be sent. */
enum class UnlockDestination {
    /** No longer used by the unlock flow; kept for the "sign in to use AI" prompt. */
    LOGIN_THEN_PREMIUM,

    /** Go straight to the premium paywall, signed in or not. */
    PREMIUM,
}

/**
 * Pure routing decision for the "unlock PRO" flow, kept out of Activity/IME plumbing so the
 * conversion-critical branch is unit-testable. Everyone goes straight to the paywall — Play needs
 * no account to take a payment, and sign-in is asked for afterwards.
 */
object UnlockRouter {
    @Suppress("UNUSED_PARAMETER")
    fun destinationFor(isAuthenticated: Boolean): UnlockDestination = UnlockDestination.PREMIUM
}
