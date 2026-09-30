package com.kharagedition.botok.autocomplete

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prefix lookup on a small in-memory dictionary, and above all `matched`: the part of the prefix
 * a suggestion completes, which is what the keyboard replaces when one is tapped.
 */
class SuggestionEngineTest {

    private val engine = SuggestionEngine().apply {
        addLines(
            """
            # form	pos	lemma	sense	freq
            བོད	NOUN	བོད		900
            བོད་ཡུལ	NOUN	བོད་ཡུལ		300
            བོད་རིགས	NOUN	བོད་རིགས		200
            བོགས	NOUN	བོགས		100
            རང	PRON	རང		800
            རགས་པ	ADJ	རགས་པ		50
            པད་མ	NOUN	པད་མ		400
            """.trimIndent().lineSequence()
        )
        ready()
    }

    @Test
    fun directHit_completesTheWholePrefix() {
        val s = engine.suggest("བོ")

        assertEquals(listOf("བོད", "བོད་ཡུལ", "བོད་རིགས", "བོགས"), s.words)
        assertEquals("བོ", s.matched)
    }

    @Test
    fun multiSyllablePrefix_isMatchedWhole() {
        val s = engine.suggest("བོད་ར")

        assertEquals(listOf("བོད་རིགས"), s.words)
        assertEquals("བོད་ར", s.matched)
    }

    @Test
    fun fallback_matchesTheLastSyllable_whenNothingLongerDoes() {
        // Nothing starts with ང་བོད་པ or བོད་པ: the words complete just the པ.
        val s = engine.suggest("ང་བོད་པ")

        assertEquals(listOf("པད་མ"), s.words)
        assertEquals("པ", s.matched)
    }

    @Test
    fun compoundInProgress_isFoundAfterEarlierWords() {
        // ང་བོད་ར is not a word, but the run's tail བོད་ར is a compound being typed.
        val s = engine.suggest("ང་བོད་ར")

        assertEquals(listOf("བོད་རིགས"), s.words)
        assertEquals("བོད་ར", s.matched)
    }

    @Test
    fun fallback_alsoSplitsAtTheNoBreakTsheg() {
        val s = engine.suggest("བོད༌ར")

        assertEquals(listOf("རང", "རགས་པ"), s.words)
        assertEquals("ར", s.matched)
    }

    @Test
    fun aTshegAfterAWord_offersItsContinuations() {
        val s = engine.suggest("བོད་")

        assertEquals(listOf("བོད་ཡུལ", "བོད་རིགས"), s.words)
        assertEquals("བོད་", s.matched)
    }

    @Test
    fun noMatch_matchesNothing() {
        assertEquals(SuggestionEngine.Suggestions.NONE, engine.suggest("ཀ"))
        // Nothing continues བོགས, and there is no syllable after its tsheg to fall back to.
        assertEquals(SuggestionEngine.Suggestions.NONE, engine.suggest("བོགས་"))
        assertEquals(SuggestionEngine.Suggestions.NONE, engine.suggest(""))
    }

    @Test
    fun resultsAreCapped_byFrequency() {
        assertEquals(listOf("བོད", "བོད་ཡུལ"), engine.suggest("བོ", max = 2).words)
    }

    @Test
    fun notReady_suggestsNothing() {
        val cold = SuggestionEngine().apply { addLines(sequenceOf("བོད\tNOUN\tབོད\t\t1")) }

        assertEquals(SuggestionEngine.Suggestions.NONE, cold.suggest("བོ"))
    }
}
