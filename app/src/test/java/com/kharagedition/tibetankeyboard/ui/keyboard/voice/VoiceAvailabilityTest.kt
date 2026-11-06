package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voice typing must never be offered in a field that holds a secret. */
class VoiceAvailabilityTest {

    private val text = InputType.TYPE_CLASS_TEXT

    @Test
    fun anOrdinaryTextFieldHasTheMic() {
        assertTrue(VoiceAvailability.allowed(text, null))
        assertTrue(VoiceAvailability.allowed(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE, null))
        assertTrue(VoiceAvailability.allowed(text or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, null))
    }

    @Test
    fun passwordFieldsHaveNoMic() {
        assertFalse(VoiceAvailability.allowed(text or InputType.TYPE_TEXT_VARIATION_PASSWORD, null))
        assertFalse(VoiceAvailability.allowed(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, null))
        assertFalse(VoiceAvailability.allowed(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, null))
    }

    @Test
    fun numberPasswordFieldsHaveNoMic() {
        assertFalse(VoiceAvailability.allowed(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, null))
    }

    @Test
    fun aPasswordFlagCombinedWithOtherFlagsStillCounts() {
        val inputType = text or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertFalse(VoiceAvailability.allowed(inputType, null))
    }

    @Test
    fun anOrdinaryNumberFieldKeepsTheMic() {
        assertTrue(VoiceAvailability.allowed(InputType.TYPE_CLASS_NUMBER, null))
    }

    @Test
    fun anAppCanSwitchTheMicOffForItsField() {
        assertFalse(VoiceAvailability.allowed(text, "noMicrophoneKey"))
        assertFalse(VoiceAvailability.allowed(text, "nm"))
        assertFalse(VoiceAvailability.allowed(text, "com.kharagedition.tibetankeyboard.forceLayout=bo,nm"))
        assertFalse(VoiceAvailability.allowed(text, " noMicrophoneKey , other"))
    }

    @Test
    fun otherPrivateOptionsLeaveTheMicOn() {
        assertTrue(VoiceAvailability.allowed(text, "com.kharagedition.tibetankeyboard.forceLayout=bo"))
        assertTrue("A word that merely contains nm is not the option", VoiceAvailability.allowed(text, "unmute"))
        assertTrue(VoiceAvailability.allowed(text, ""))
    }
}
