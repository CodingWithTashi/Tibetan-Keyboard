package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.UpgradeSource
import com.kharagedition.tibetankeyboard.subscription.RemoteConfig
import com.kharagedition.tibetankeyboard.ui.keyboard.KeyboardUpsell.Action
import com.kharagedition.tibetankeyboard.ui.keyboard.KeyboardUpsell.Control
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the keyboard bar's upsell: a tap above the keys asks before it opens the paywall. */
class KeyboardUpsellTest {

    @Test
    fun byDefault_aLockedTapAsksFirst() {
        assertEquals(Action.ASK_FIRST, KeyboardUpsell.onLockedTap(RemoteConfig().keyboardUpsellConfirm))
    }

    @Test
    fun switchedOffInTheDashboard_theTapOpensThePaywallAsBefore() {
        val config = RemoteConfig.fromMetadata(mapOf(RemoteConfig.META_KEYBOARD_UPSELL_CONFIRM to false))

        assertEquals(Action.OPEN_PAYWALL, KeyboardUpsell.onLockedTap(config.keyboardUpsellConfirm))
    }

    @Test
    fun theSecondTapOfADoubleTap_isIgnored() {
        // Chat → "See plans", Translate → close, close → Translate: all the same spot on the bar.
        assertTrue(KeyboardUpsell.isStrayTap(msSinceBarSwapped = 0))
        assertTrue(KeyboardUpsell.isStrayTap(KeyboardUpsell.SWAP_GUARD_MS - 1))
        assertFalse("A tap after reading the question counts", KeyboardUpsell.isStrayTap(KeyboardUpsell.SWAP_GUARD_MS))
        // Never swapped yet (uptime minus zero), and a clock that stepped back, are not strays.
        assertFalse(KeyboardUpsell.isStrayTap(86_400_000L))
        assertFalse(KeyboardUpsell.isStrayTap(-5))
    }

    @Test
    fun salesKeepTheSourceTheyHadBeforeTheQuestion() {
        // upgrade_clicked reports these two sources; moving a control between them would break
        // the comparison with earlier releases.
        assertEquals(UpgradeSource.KEYBOARD, Control.PILL.source)
        assertEquals(UpgradeSource.KEYBOARD, Control.CHAT.source)
        assertEquals(UpgradeSource.KEYBOARD, Control.TRANSLATE.source)
        assertEquals(UpgradeSource.KEYBOARD_SUGGESTIONS, Control.LOCKED_SUGGESTION.source)
    }
}
