package com.kharagedition.tibetankeyboard.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the post-login routing: logging in from the PRO upsell must forward to the paywall,
 * a normal login must go to chat.
 */
class PostLoginDestinationTest {

    @Test
    fun launchedFromProUpsell_routesToPaywall() {
        assertEquals(
            PostLoginDestination.PREMIUM,
            postLoginDestination(openPremiumAfterLogin = true)
        )
    }

    @Test
    fun normalLogin_routesToChat() {
        assertEquals(
            PostLoginDestination.CHAT,
            postLoginDestination(openPremiumAfterLogin = false)
        )
    }
}
