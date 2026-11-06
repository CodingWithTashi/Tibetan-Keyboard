package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What goes between what is already typed and what was spoken, and when Undo is safe. */
class VoiceInsertTest {

    private val spoken = "བཀྲ་ཤིས་བདེ་ལེགས།"

    @Test
    fun anEmptyFieldTakesTheTextAsIs() {
        assertEquals(spoken, VoiceInsert.join(null, spoken))
        assertEquals(spoken, VoiceInsert.join("", spoken))
    }

    @Test
    fun afterASpaceOrNewlineNothingIsAdded() {
        assertEquals(spoken, VoiceInsert.join("hello ", spoken))
        assertEquals(spoken, VoiceInsert.join("hello\n", spoken))
    }

    @Test
    fun afterATshegNothingIsAdded() {
        assertEquals(spoken, VoiceInsert.join("བཀྲ་", spoken))
    }

    @Test
    fun afterAShadASpaceSeparatesTheSentences() {
        assertEquals(" $spoken", VoiceInsert.join("བཀྲ་ཤིས།", spoken))
    }

    @Test
    fun afterLatinTextOrDigitsASpaceSeparates() {
        assertEquals(" $spoken", VoiceInsert.join("hello", spoken))
        assertEquals(" $spoken", VoiceInsert.join("call me at 5", spoken))
        assertEquals(" $spoken", VoiceInsert.join("hello!", spoken))
    }

    @Test
    fun afterATibetanDigitASpaceSeparates() {
        assertEquals(" $spoken", VoiceInsert.join("༡༢", spoken))
    }

    @Test
    fun afterABareTibetanSyllableATshegJoinsIt() {
        assertEquals("་$spoken", VoiceInsert.join("བཀྲ", spoken))
        assertEquals("A vowel sign ends a syllable too", "་$spoken", VoiceInsert.join("ཤི", spoken))
    }

    @Test
    fun textThatStartsWithItsOwnPunctuationNeedsNoSeparator() {
        assertEquals("།ཁྱེད་རང་", VoiceInsert.join("བཀྲ་ཤིས", "།ཁྱེད་རང་"))
        assertEquals("་ཤིས།", VoiceInsert.join("བཀྲ", "་ཤིས།"))
        assertEquals(" x", VoiceInsert.join("hello", " x"))
    }

    @Test
    fun onlyTheLastCharacterDecides() {
        assertEquals(" $spoken", VoiceInsert.join("བཀྲ་ཤིས་བདེ་ལེགས། ok", spoken))
        assertEquals(spoken, VoiceInsert.join("ok བཀྲ་", spoken))
    }

    @Test
    fun undoIsSafeWhileTheInsertedTextIsStillBeforeTheCursor() {
        val inserted = " $spoken"
        assertTrue(VoiceInsert.canUndo("hello$inserted", inserted))
        assertTrue(VoiceInsert.canUndo(inserted, inserted))
    }

    @Test
    fun undoRefusesOnceTheUserMovedOnOrEditedIt() {
        val inserted = " $spoken"
        assertFalse("something typed after it", VoiceInsert.canUndo("hello$inserted!", inserted))
        assertFalse("cursor moved elsewhere", VoiceInsert.canUndo("hello", inserted))
        assertFalse("edited in the middle", VoiceInsert.canUndo("hello བཀྲ་ཤིས་འབྲས། ", inserted))
    }

    @Test
    fun undoRefusesWhenTheEditorCannotSayWhatIsBeforeTheCursor() {
        assertFalse(VoiceInsert.canUndo(null, " $spoken"))
    }

    @Test
    fun undoRefusesToRemoveNothing() {
        assertFalse(VoiceInsert.canUndo("hello", ""))
    }

    @Test
    fun theStreakCountsTibetanCharactersOnly() {
        assertEquals(4, VoiceInsert.tibetanChars("བཀྲ་"))
        assertEquals("Spaces and Latin are not Tibetan", 3, VoiceInsert.tibetanChars(" ok བོད "))
        assertEquals(0, VoiceInsert.tibetanChars("hello 123"))
        assertEquals(0, VoiceInsert.tibetanChars(""))
    }
}
