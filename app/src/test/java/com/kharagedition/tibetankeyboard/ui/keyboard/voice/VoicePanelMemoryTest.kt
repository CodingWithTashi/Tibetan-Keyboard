package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard rebuilds its views whenever an app restarts input (and twice around the system's
 * permission dialog). The voice panel must survive that on the same field, whatever state it is
 * in, but never follow the user to another field.
 */
class VoicePanelMemoryTest {

    private val chat = VoiceField.of("com.whatsapp", 7)!!
    private val other = VoiceField.of("org.telegram", 3)!!

    @Test
    fun withNoPanelOpenNothingIsRestored() {
        assertFalse(VoicePanelMemory().restoreOn(chat))
    }

    @Test
    fun anOpenPanelComesBackOnTheSameFieldEveryTime() {
        val memory = VoicePanelMemory()
        memory.opened(chat)

        assertTrue(memory.restoreOn(chat))
        assertTrue("through a second restart too", memory.restoreOn(chat))
    }

    @Test
    fun movingToAnotherFieldForgetsThePanel() {
        val memory = VoicePanelMemory()
        memory.opened(chat)

        assertFalse(memory.restoreOn(other))
        assertFalse("and coming back does not bring it back", memory.restoreOn(chat))
    }

    @Test
    fun aRestartForAnUnknownTargetForgetsItToo() {
        val memory = VoicePanelMemory()
        memory.opened(chat)

        assertFalse(memory.restoreOn(null))
        assertFalse(memory.restoreOn(chat))
    }

    @Test
    fun closingThePanelForgetsIt() {
        val memory = VoicePanelMemory()
        memory.opened(chat)
        memory.closed()

        assertFalse(memory.restoreOn(chat))
    }

    @Test
    fun aPanelOpenedOnAnUnknownFieldIsNeverRestored() {
        val memory = VoicePanelMemory()
        memory.opened(null)

        assertFalse(memory.restoreOn(chat))
        assertFalse(memory.restoreOn(null))
    }

    @Test
    fun openingAgainOnAnotherFieldReplacesTheOldOne() {
        val memory = VoicePanelMemory()
        memory.opened(chat)
        memory.opened(other)

        assertFalse(memory.restoreOn(chat))
    }
}
