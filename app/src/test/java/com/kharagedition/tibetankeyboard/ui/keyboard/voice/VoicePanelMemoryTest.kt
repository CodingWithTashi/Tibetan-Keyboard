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
        assertFalse(VoicePanelMemory().restoreOn(chat, allowed = true))
    }

    @Test
    fun anOpenPanelComesBackOnTheSameFieldEveryTime() {
        val memory = VoicePanelMemory()
        memory.opened(chat)

        assertTrue(memory.restoreOn(chat, allowed = true))
        assertTrue("through a second restart too", memory.restoreOn(chat, allowed = true))
    }

    @Test
    fun movingToAnotherFieldForgetsThePanel() {
        val memory = VoicePanelMemory()
        memory.opened(chat)

        assertFalse(memory.restoreOn(other, allowed = true))
        assertFalse("and coming back does not bring it back", memory.restoreOn(chat, allowed = true))
    }

    @Test
    fun aRestartForAnUnknownTargetForgetsItToo() {
        val memory = VoicePanelMemory()
        memory.opened(chat)

        assertFalse(memory.restoreOn(null, allowed = true))
        assertFalse(memory.restoreOn(chat, allowed = true))
    }

    @Test
    fun closingThePanelForgetsIt() {
        val memory = VoicePanelMemory()
        memory.opened(chat)
        memory.closed()

        assertFalse(memory.restoreOn(chat, allowed = true))
    }

    @Test
    fun aPanelOpenedOnAnUnknownFieldIsNeverRestored() {
        val memory = VoicePanelMemory()
        memory.opened(null)

        assertFalse(memory.restoreOn(chat, allowed = true))
        assertFalse(memory.restoreOn(null, allowed = true))
    }

    @Test
    fun openingAgainOnAnotherFieldReplacesTheOldOne() {
        val memory = VoicePanelMemory()
        memory.opened(chat)
        memory.opened(other)

        assertFalse(memory.restoreOn(chat, allowed = true))
    }

    @Test
    fun aFieldThatMustNotBeDictatedIntoNeverGetsThePanelBack() {
        // Many apps leave the field id unset, so a password field of the same app looks like the
        // field the panel was opened on: availability decides, not the field's identity.
        val memory = VoicePanelMemory()
        memory.opened(chat)

        assertFalse(memory.restoreOn(chat, allowed = false))
        assertFalse("and it is forgotten, not just hidden", memory.restoreOn(chat, allowed = true))
    }
}
