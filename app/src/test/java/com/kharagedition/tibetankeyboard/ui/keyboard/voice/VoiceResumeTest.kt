package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After "Allow microphone" the keyboard restarts for the permission dialog before it comes back
 * to the user's field. Only the field the user was on may spend the "listen now" that was armed.
 */
class VoiceResumeTest {

    private val chat = VoiceField.of("com.whatsapp", 7)!!
    private val dialog = VoiceField.of("com.google.android.permissioncontroller", 0)!!

    @Test
    fun withNothingArmedNothingResumes() {
        assertFalse(VoiceResume().check(chat, nowMs = 0))
    }

    @Test
    fun backOnTheSameFieldResumesOnce() {
        val resume = VoiceResume()
        resume.arm(chat, nowMs = 1_000)

        assertTrue(resume.check(chat, nowMs = 5_000))
        assertFalse("it is spent", resume.check(chat, nowMs = 5_100))
    }

    @Test
    fun theKeyboardRestartingForThePermissionDialogDoesNotSpendIt() {
        val resume = VoiceResume()
        resume.arm(chat, nowMs = 1_000)

        assertFalse(resume.check(dialog, nowMs = 1_200))
        assertTrue("still armed when the user is back", resume.check(chat, nowMs = 6_000))
    }

    @Test
    fun anUnknownFieldDoesNotSpendItEither() {
        val resume = VoiceResume()
        resume.arm(chat, nowMs = 1_000)

        assertFalse(resume.check(null, nowMs = 2_000))
        assertTrue(resume.check(chat, nowMs = 3_000))
    }

    @Test
    fun anotherFieldInTheSameAppDoesNotResume() {
        val resume = VoiceResume()
        resume.arm(chat, nowMs = 1_000)

        assertFalse(resume.check(VoiceField.of("com.whatsapp", 8), nowMs = 2_000))
    }

    @Test
    fun itLapsesAfterAMinute() {
        val resume = VoiceResume()
        resume.arm(chat, nowMs = 0)
        assertTrue("exactly a minute is still in time", VoiceResume().also { it.arm(chat, 0) }.check(chat, VoiceResume.WINDOW_MS))

        assertFalse(resume.check(chat, nowMs = VoiceResume.WINDOW_MS + 1))
        assertFalse("and it is gone, not just late", resume.check(chat, nowMs = VoiceResume.WINDOW_MS + 2))
    }

    @Test
    fun armingAgainReplacesTheOldField() {
        val resume = VoiceResume()
        resume.arm(chat, nowMs = 0)
        resume.arm(VoiceField.of("org.telegram", 3)!!, nowMs = 10)

        assertFalse(resume.check(chat, nowMs = 20))
    }

    @Test
    fun disarmingCancelsIt() {
        val resume = VoiceResume()
        resume.arm(chat, nowMs = 0)
        resume.disarm()

        assertFalse(resume.check(chat, nowMs = 1))
    }
}
