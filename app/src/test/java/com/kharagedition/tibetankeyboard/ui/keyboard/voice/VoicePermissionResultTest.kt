package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permission screen and the keyboard share a process but not a window: with the permission
 * already refused for good, Android shows no dialog, so the keyboard never restarts and cannot
 * notice. The screen must tell it.
 */
class VoicePermissionResultTest {

    @After
    fun reset() {
        VoicePermissionResult.listener = null
        VoicePermissionResult.report(granted = true, blocked = false)
    }

    @Test
    fun theKeyboardIsToldTheAnswer() {
        val heard = mutableListOf<Pair<Boolean, Boolean>>()
        VoicePermissionResult.listener = { granted, blocked -> heard += granted to blocked }

        VoicePermissionResult.report(granted = false, blocked = true)
        VoicePermissionResult.report(granted = true, blocked = false)

        assertEquals(listOf(false to true, true to false), heard)
    }

    @Test
    fun theLastRefusalIsRemembered() {
        VoicePermissionResult.report(granted = false, blocked = true)
        assertTrue(VoicePermissionResult.blocked)

        VoicePermissionResult.report(granted = true, blocked = false)
        assertFalse(VoicePermissionResult.blocked)
    }

    @Test
    fun reportingWithNobodyListeningIsHarmless() {
        VoicePermissionResult.listener = null
        VoicePermissionResult.report(granted = false, blocked = false)
        assertFalse(VoicePermissionResult.blocked)
    }
}
