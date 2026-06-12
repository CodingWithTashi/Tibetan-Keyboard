package com.kharagedition.tibetankeyboard.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the unlock routing: a free user tapping a PRO control must reach the paywall, going via
 * login first only when signed out.
 */
class UnlockRouterTest {

    @Test
    fun signedOutUser_goesToLoginThenPremium() {
        assertEquals(
            UnlockDestination.LOGIN_THEN_PREMIUM,
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
