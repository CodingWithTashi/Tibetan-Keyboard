package com.kharagedition.tibetankeyboard.ui.keyboard

import com.kharagedition.botok.autocomplete.SuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * The strip and the tap together, on the real dictionary: the engine's `matched` feeds the
 * composer exactly as the IME wires them. Replays the keystrokes of the reported recording
 * (pick བོད, type ར, pick རང) and checks both the chips shown and the text that results.
 *
 * Reads the botok assets from the sibling module like botok's own SmokeTest does; skipped when
 * they are not on disk. Asserts on the words that matter, not on the dictionary's exact ranking.
 */
class SuggestionFlowTest {

    /** A text field with the cursor at the end. */
    private class Editor(var text: String = "") {
        fun apply(edit: WordComposer.Edit) {
            text = text.dropLast(edit.deleteBefore) + edit.text
        }
    }

    private val composer = WordComposer()
    private val editor = Editor()

    /** The chips the strip would show for the word in progress (PRO width, four chips). */
    private fun strip(): SuggestionEngine.Suggestions = engine.suggest(composer.composing, ProStripState.PRO_SUGGESTIONS)

    private fun type(keys: String) {
        for (c in keys) editor.apply(composer.onChar(c, editor.text))
    }

    private fun tap(word: String) {
        val shown = strip()
        assertTrue("'$word' must be on the strip, got ${shown.words}", word in shown.words)
        editor.apply(composer.onAccept(word, shown.matched, editor.text, after = ""))
    }

    @Test
    fun theRecordedFlow_endsWithBothWordsAndTheirTshegs() {
        type("བ")
        assertTrue(strip().words.isNotEmpty())

        type("ོ")
        assertTrue("བོད" in strip().words)

        tap("བོད")
        assertEquals("བོད་", editor.text)
        assertEquals("nothing to suggest right after a pick", 0, strip().words.size)

        type("་")
        assertEquals("the habitual tsheg is swallowed", "བོད་", editor.text)

        type("ར")
        assertTrue("རང" in strip().words)
        assertEquals("ར", strip().matched)

        tap("རང")
        assertEquals("བོད་རང་", editor.text)

        type("།")
        assertEquals("after nga the tsheg stays before the shad", "བོད་རང་།", editor.text)
    }

    @Test
    fun handTypedRun_tapReplacesOnlyWhatTheStripMatched() {
        val run = "ང་བོད་པ"
        type(run)
        val shown = strip()
        assertTrue(shown.words.isNotEmpty())
        // What the chips complete is the run itself or a tail of it starting after a tsheg.
        assertTrue(run.endsWith(shown.matched))
        val cut = run.length - shown.matched.length
        assertTrue(cut == 0 || run[cut - 1] == WordComposer.TSHEG)

        tap(shown.words.first())

        assertEquals(run.dropLast(shown.matched.length) + shown.words.first() + "་", editor.text)
    }

    @Test
    fun compoundTypedAfterAnotherWord_isOffered_andReplacedWhole() {
        type("ང་བོད་ར")
        val shown = strip()
        assertTrue("བོད་རིགས" in shown.words)
        assertEquals("བོད་ར", shown.matched)

        tap("བོད་རིགས")

        assertEquals("ང་བོད་རིགས་", editor.text)
    }

    @Test
    fun handTypedCompound_tapReplacesTheWholeCompound() {
        type("བོད་ར")
        assertEquals("བོད་ར", strip().matched)

        tap("བོད་རིགས")

        assertEquals("བོད་རིགས་", editor.text)
    }

    companion object {
        private val assets = File("../botok/src/main/assets/botok/general/dictionary")
        private lateinit var engine: SuggestionEngine

        @BeforeClass
        @JvmStatic
        fun loadDictionary() {
            assumeTrue("botok dictionary assets not found at $assets", assets.isDirectory)
            engine = SuggestionEngine()
            for (name in listOf("words/tsikchen.tsv", "words/uncompound_lexicon.tsv", "words_non_inflected/particles.tsv")) {
                File(assets, name).bufferedReader(Charsets.UTF_8).useLines { engine.addLines(it) }
            }
            engine.ready()
        }
    }
}
