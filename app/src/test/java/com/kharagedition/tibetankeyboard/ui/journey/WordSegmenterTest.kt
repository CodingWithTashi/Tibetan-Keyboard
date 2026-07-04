package com.kharagedition.tibetankeyboard.ui.journey

import com.kharagedition.botok.autocomplete.SuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class WordSegmenterTest {

    private fun engineOf(vararg forms: String): SuggestionEngine {
        val engine = SuggestionEngine()
        engine.addLines(forms.asSequence().map { "$it\t\t\t\t1" })
        engine.ready()
        return engine
    }

    @Test
    fun `splits a multi-word chunk into its dictionary words`() {
        // "ཀ་དག" (2 syllables) + "ང" (1 syllable) typed back-to-back with no space —
        // exactly the case the old space-shad-only chunking miscounted as a single "word".
        val engine = engineOf("ཀ་དག", "ང")
        assertEquals(listOf("ཀ་དག", "ང"), WordSegmenter.segment("ཀ་དག་ང", engine))
    }

    @Test
    fun `prefers the longest dictionary match over single syllables`() {
        val engine = engineOf("ཀ", "དག", "ཀ་དག")
        assertEquals(listOf("ཀ་དག"), WordSegmenter.segment("ཀ་དག", engine))
    }

    @Test
    fun `falls back to single syllables when nothing matches`() {
        val engine = engineOf("སོ་སོ")
        assertEquals(listOf("ཀ", "ཁ", "ག"), WordSegmenter.segment("ཀ་ཁ་ག", engine))
    }

    @Test
    fun `dictionary not ready yet falls back to the whole chunk as one word`() {
        val notReady = SuggestionEngine()
        assertEquals(listOf("ཀ་དག་ང"), WordSegmenter.segment("ཀ་དག་ང", notReady))
        assertEquals(listOf("ཀ་དག་ང"), WordSegmenter.segment("ཀ་དག་ང", null))
    }

    @Test
    fun `empty chunk segments to no words`() {
        assertEquals(emptyList<String>(), WordSegmenter.segment("", engineOf("ཀ")))
    }
}
