package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A transcript belongs to the field it was spoken into, and nowhere else. */
class VoiceFieldTest {

    @Test
    fun theSameFieldMatches() {
        val a = VoiceField.of("com.whatsapp", 42)
        val b = VoiceField.of("com.whatsapp", 42)
        assertTrue(a!!.matches(b))
    }

    @Test
    fun anotherFieldInTheSameAppDoesNotMatch() {
        assertFalse(VoiceField.of("com.whatsapp", 42)!!.matches(VoiceField.of("com.whatsapp", 43)))
    }

    @Test
    fun theSameFieldIdInAnotherAppDoesNotMatch() {
        assertFalse(VoiceField.of("com.whatsapp", 1)!!.matches(VoiceField.of("org.telegram", 1)))
    }

    @Test
    fun anUnknownAppIsNeverAField() {
        assertNull(VoiceField.of(null, 5))
        assertNull(VoiceField.of("", 5))
    }

    @Test
    fun noFieldMatchesNothing() {
        assertFalse(VoiceField.of("com.whatsapp", 1)!!.matches(null))
    }

    @Test
    fun appsThatSetNoFieldIdAreToldApartByPackageOnly() {
        // EditorInfo.fieldId is 0 when the app never set it.
        assertTrue(VoiceField.of("com.notes", 0)!!.matches(VoiceField.of("com.notes", 0)))
        assertFalse(VoiceField.of("com.notes", 0)!!.matches(VoiceField.of("com.mail", 0)))
    }
}
