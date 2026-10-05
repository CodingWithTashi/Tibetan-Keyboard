package com.kharagedition.tibetankeyboard.ui.home

import com.kharagedition.tibetankeyboard.analytics.AppAnalytics.PickerTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards setup step 1: the keyboard being switched on in the system settings is noticed while
 * the user is away, Home comes back by itself only where Android allows it, and the picker opens
 * by itself only for someone who has just switched the keyboard on.
 */
class EnableTripTest {

    private val android14 = 34
    private val android15 = 35
    private val keyboardOff = EnableTrip.Outcome(enabledVia = null, openPicker = null)

    private fun trip(sdk: Int = android14) = EnableTrip(sdk).apply { start() }

    @Test
    fun homeComesBackByItself_onlyBelowAndroid15() {
        assertTrue(EnableTrip(24).bringsHomeBack)
        assertTrue(EnableTrip(android14).bringsHomeBack)
        // From Android 15 the system can refuse to let an app cover a system screen in its own
        // task; trying would only leave the settings screen stuck on top of Home.
        assertFalse(EnableTrip(android15).bringsHomeBack)
        assertFalse(EnableTrip(36).bringsHomeBack)
    }

    @Test
    fun withoutATrip_homeDoesNothingExtra() {
        val trip = EnableTrip(android14)

        assertFalse(trip.pending)
        assertFalse(trip.sawSwitchedOn(enabled = true))
        // Every ordinary resume of a set-up or half-set-up user lands here: no picker popping up,
        // and the keyboard counts as switched on some other way (an older version, the settings).
        assertEquals(
            EnableTrip.Outcome(EnabledVia.OTHER, openPicker = null),
            trip.finish(enabled = true, selected = false),
        )
        assertEquals(keyboardOff, trip.finish(enabled = false, selected = false))
    }

    @Test
    fun comingBackWithoutSwitchingItOn_changesNothing() {
        val trip = trip()

        assertTrue(trip.pending)
        assertFalse(trip.sawSwitchedOn(enabled = false))
        assertEquals(keyboardOff, trip.finish(enabled = false, selected = false))
        assertFalse(trip.pending)
    }

    @Test
    fun switchedOn_isNoticedOnce_whileTheUserIsStillAway() {
        val trip = trip()

        assertFalse(trip.sawSwitchedOn(enabled = false))
        assertTrue(trip.sawSwitchedOn(enabled = true))
        assertFalse("The poll keeps running", trip.sawSwitchedOn(enabled = true))
    }

    @Test
    fun broughtBack_thePickerOpensByItself() {
        val trip = trip()
        trip.sawSwitchedOn(enabled = true)
        trip.broughtBack()

        assertEquals(
            EnableTrip.Outcome(EnabledVia.SETUP, openPicker = PickerTrigger.AUTO_RETURN),
            trip.finish(enabled = true, selected = false),
        )
    }

    @Test
    fun systemRefusedToBringHomeBack_countsAsTheUsersOwnReturn() {
        // Home asked, but its intent never arrived: however soon the user comes back, it was them.
        val trip = trip()
        trip.sawSwitchedOn(enabled = true)

        assertEquals(
            EnableTrip.Outcome(EnabledVia.SETUP, openPicker = PickerTrigger.MANUAL_RETURN),
            trip.finish(enabled = true, selected = false),
        )
    }

    @Test
    fun backBeforeThePollNoticed_stillCountsAsSetup_andOpensThePicker() {
        val trip = trip(android15)

        assertEquals(
            EnableTrip.Outcome(EnabledVia.SETUP, openPicker = PickerTrigger.MANUAL_RETURN),
            trip.finish(enabled = true, selected = false),
        )
    }

    @Test
    fun alreadyTheDefaultKeyboard_needsNoPicker() {
        val trip = trip()
        trip.sawSwitchedOn(enabled = true)
        trip.broughtBack()

        assertEquals(
            EnableTrip.Outcome(EnabledVia.SETUP, openPicker = null),
            trip.finish(enabled = true, selected = true),
        )
    }

    @Test
    fun aFinishedTrip_doesNotLinger() {
        val trip = trip()
        trip.finish(enabled = true, selected = false)

        assertFalse(trip.sawSwitchedOn(enabled = true))
        trip.broughtBack()
        assertEquals(
            EnableTrip.Outcome(EnabledVia.OTHER, openPicker = null),
            trip.finish(enabled = true, selected = false),
        )
    }

    @Test
    fun tappingEnableAgain_forgetsWhatTheLastTripSaw() {
        // No finish() in between: only start() can clear the first trip's "switched on" and
        // "brought back".
        val trip = trip()
        trip.sawSwitchedOn(enabled = true)
        trip.broughtBack()

        trip.start()
        assertTrue("Noticed afresh", trip.sawSwitchedOn(enabled = true))
        assertEquals(
            EnableTrip.Outcome(EnabledVia.SETUP, openPicker = PickerTrigger.MANUAL_RETURN),
            trip.finish(enabled = true, selected = false),
        )
    }

    @Test
    fun aTripRestoredAfterTheProcessDied_stillOpensThePicker() {
        val trip = EnableTrip(android14)
        trip.restore()

        assertTrue(trip.pending)
        assertEquals(
            EnableTrip.Outcome(EnabledVia.SETUP, openPicker = PickerTrigger.MANUAL_RETURN),
            trip.finish(enabled = true, selected = false),
        )
    }

    @Test
    fun restoringOverALiveTrip_keepsWhatItAlreadyKnows() {
        // A rotation hands the saved state to a Home whose trip never went away.
        val trip = trip()
        trip.sawSwitchedOn(enabled = true)
        trip.broughtBack()

        trip.restore()
        assertFalse("Not noticed a second time", trip.sawSwitchedOn(enabled = true))
        assertEquals(PickerTrigger.AUTO_RETURN, trip.finish(enabled = true, selected = false).openPicker)
    }
}
