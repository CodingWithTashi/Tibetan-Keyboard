package com.kharagedition.tibetankeyboard.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the unlock routing: tapping a PRO control must reach the paywall directly, signed in or
 * not. Requiring a login first cost 61% of upgrade intents (2,454 taps → 956 paywall views), and
 * Google Play does not need an account to take a payment.
 */
class UnlockRouterTest {

    @Test
    fun signedOutUser_goesStraightToPremium() {
        assertEquals(
            UnlockDestination.PREMIUM,
            UnlockRouter.destinationFor(isAuthenticated = false)
        )
    }

    @Test
    fun signedInFreeUser_goesStraightToPremium() {
        assertEquals(
            UnlockDestination.PREMIUM,
            UnlockRouter.destinationFor(isAuthenticated = true)
        )
    }
}
