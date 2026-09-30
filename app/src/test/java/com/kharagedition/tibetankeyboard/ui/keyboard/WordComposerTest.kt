package com.kharagedition.tibetankeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suggestion-tap rules: what a tap replaces, the tsheg it writes, and what the keys after it
 * do. Driven through a tiny editor so each case reads like the typing it describes.
 */
class WordComposerTest {

    /** A text field with the cursor at the end, applying the composer's edits. */
    private class Editor(var text: String = "") {
        fun apply(edit: WordComposer.Edit): Editor {
            text = text.dropLast(edit.deleteBefore) + edit.text
            return this
        }
    }

    private val composer = WordComposer()
    private val editor = Editor()

    private fun type(keys: String) {
        for (c in keys) editor.apply(composer.onChar(c, editor.text))
    }

    private fun accept(word: String, matched: String = "") =
        editor.apply(composer.onAccept(word, matched, editor.text, after = ""))

    private fun backspace() {
        composer.onDelete(editor.text)
        editor.text = editor.text.dropLast(1)
    }

    @Test
    fun acceptedWord_replacesWhatItCompletes_andWritesTheTsheg() {
        type("བོ")
        assertEquals("བོ", composer.composing)

        accept("བོད", matched = "བོ")

        assertEquals("བོད་", editor.text)
        assertEquals("", composer.composing)
    }

    @Test
    fun theReportedFlow_secondTapKeepsTheTsheg() {
        // Pick བོད, type ར, pick རང: used to give བོདརང (the tsheg deleted with the ར).
        type("བོ")
        accept("བོད", matched = "བོ")
        type("ར")
        assertEquals("ར", composer.composing)

        accept("རང", matched = "ར")

        assertEquals("བོད་རང་", editor.text)
    }

    @Test
    fun typedTshegAfterAcceptedWord_isSwallowed_andChangesNothing() {
        accept("བོད")
        type("་")
        assertEquals("one tsheg, not ་་", "བོད་", editor.text)

        type("།")
        assertEquals("the shad still takes the tsheg's place", "བོད།", editor.text)
    }

    @Test
    fun acceptedWord_reportsTheSyllablesItKeeps_forStats() {
        type("ང་བོད་པ")
        val tail = composer.onAccept("པད་མ", "པ", editor.text, "")
        assertEquals("ང་བོད་", tail.finishedChunk)

        val e = Editor()
        val fresh = WordComposer()
        for (c in "བོད་ར") e.apply(fresh.onChar(c, e.text))
        val whole = fresh.onAccept("བོད་རིགས", "བོད་ར", e.text, "")
        assertEquals("", whole.finishedChunk)
    }

    @Test
    fun fallbackMatch_replacesOnlyTheLastSyllable() {
        // The engine found nothing for the whole run and matched the syllable after the last tsheg.
        type("ང་བོད་པ")

        accept("པད་མ", matched = "པ")

        assertEquals("ང་བོད་པད་མ་", editor.text)
    }

    @Test
    fun directMatch_replacesTheWholeRun() {
        type("བོད་ར")

        accept("བོད་རིགས", matched = "བོད་ར")

        assertEquals("བོད་རིགས་", editor.text)
    }

    @Test
    fun shadAfterAcceptedWord_takesTheTshegsPlace() {
        type("བོ")
        accept("བོད", matched = "བོ")

        type("།")

        assertEquals("བོད།", editor.text)
        assertEquals("", composer.composing)
    }

    @Test
    fun shadAfterNga_keepsTheTsheg() {
        // ང། would read as ད, so the tsheg stays: after a bare nga, nga with a vowel, and a stack.
        for (word in listOf("རང", "ངོ", "ལྔ")) {
            val e = Editor()
            val c = WordComposer()
            e.apply(c.onAccept(word, "", e.text, ""))
            e.apply(c.onChar('།', e.text))
            assertEquals("$word་།", e.text)
        }
    }

    @Test
    fun otherClauseMarks_countAsShad() {
        for (mark in listOf('༎', '༔', '༑')) {
            val e = Editor()
            val c = WordComposer()
            e.apply(c.onAccept("བོད", "", e.text, ""))
            e.apply(c.onChar(mark, e.text))
            assertEquals("བོད$mark", e.text)
        }
    }

    @Test
    fun anyOtherKey_makesTheTshegOrdinary() {
        accept("བོད")
        type("ཀ")
        backspace()
        type("།")

        assertEquals("the tsheg is no longer provisional", "བོད་།", editor.text)
    }

    @Test
    fun backspaceAfterAccept_deletesJustTheTsheg() {
        accept("བོད")
        backspace()
        assertEquals("བོད", editor.text)

        type("།")
        assertEquals("བོད།", editor.text)
    }

    @Test
    fun backspace_shortensTheWord() {
        type("བོད")
        backspace()
        assertEquals("བོ", composer.composing)

        backspace()
        backspace()
        backspace()
        assertEquals("", composer.composing)
    }

    @Test
    fun noTsheg_whenTheWordAlreadyEndsWithOne() {
        accept("བོང་བུ་")
        assertEquals("བོང་བུ་", editor.text)
    }

    @Test
    fun noTsheg_whenOneFollowsTheCursor_orTheEditorCannotSay() {
        assertEquals(WordComposer.Edit(0, "བོད"), WordComposer().onAccept("བོད", "", "", after = "་ཡུལ"))
        assertEquals(WordComposer.Edit(0, "བོད"), WordComposer().onAccept("བོད", "", "", after = null))
        assertEquals(WordComposer.Edit(0, "བོད་"), WordComposer().onAccept("བོད", "", "", after = ""))
    }

    @Test
    fun beforeAShad_onlyNgaGetsATsheg() {
        assertEquals(WordComposer.Edit(0, "བོད"), WordComposer().onAccept("བོད", "", "", after = "།"))
        assertEquals(WordComposer.Edit(0, "རང་"), WordComposer().onAccept("རང", "", "", after = "།"))
    }

    @Test
    fun boundaryKeys_endTheRun_andReportItForStats() {
        type("བོད་ཡིག")

        val edit = composer.onChar('།', editor.text)

        assertEquals("བོད་ཡིག", edit.finishedChunk)
        assertEquals("", composer.composing)
        for (c in listOf('།', '༎', '༔', ' ', '\n')) assertTrue("$c ends a run", WordComposer.endsRun(c))
        assertFalse("a tsheg joins syllables of one word", WordComposer.endsRun('་'))
    }

    @Test
    fun cursorMovedElsewhere_dropsTheWord_soATapCannotDeleteThere() {
        type("བོ")

        // The text before the cursor no longer ends with what was typed.
        assertTrue(composer.sync("ཁྱེད་རང"))
        assertEquals("", composer.composing)

        // A tap on a stale chip inserts, but deletes nothing.
        assertEquals(WordComposer.Edit(0, "བོད་"), composer.onAccept("བོད", "བོ", "ཁྱེད་རང", ""))
    }

    @Test
    fun ownEditsEchoedBack_keepTheWord() {
        type("བོ")

        assertFalse(composer.sync("ཁྱེད་རང་བོ"))

        assertEquals("བོ", composer.composing)
    }

    @Test
    fun cursorMovedAfterAccept_makesTheTshegOrdinary() {
        accept("བོད")
        composer.sync("ཁྱེད་རང")

        val edit = composer.onChar('།', "ཁྱེད་རང")

        assertEquals(WordComposer.Edit(0, "།"), edit)
    }

    @Test
    fun editorThatCannotReportText_keepsNoState() {
        assertEquals(WordComposer.Edit(0, "བ"), composer.onChar('བ', before = null))
        assertEquals("", composer.composing)
        assertFalse(composer.hasState)
    }

    @Test
    fun lookback_coversTheStateBeingChecked() {
        type("བོད་ཡི")
        assertTrue(composer.lookback >= "བོད་ཡི".length)

        accept("བོད་ཡིག", matched = "བོད་ཡི")
        assertTrue(composer.lookback >= "བོད་ཡིག་".length)
    }

    @Test
    fun endsWithNga_ignoresVowelSigns() {
        assertTrue(WordComposer.endsWithNga("ང"))
        assertTrue(WordComposer.endsWithNga("སྣང"))
        assertTrue(WordComposer.endsWithNga("ངོ"))
        assertTrue(WordComposer.endsWithNga("ལྔ"))
        assertFalse(WordComposer.endsWithNga("བོད"))
        assertFalse(WordComposer.endsWithNga("ངག"))
        assertFalse(WordComposer.endsWithNga(""))
    }
}
