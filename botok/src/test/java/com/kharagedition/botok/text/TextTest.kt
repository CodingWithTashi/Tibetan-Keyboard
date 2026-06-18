package com.kharagedition.botok.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kharagedition.botok.tokenizers.WordTokenizer
import com.kharagedition.botok.config.Config
import com.kharagedition.botok.text.Text

/**
 * Port of tests for Phase 10 - Text class
 *
 * Tests for the high-level Text API
 */
class TextTest {

    @Test
    fun testTokenizeOnSpaces() {
        val text = Text("Hello World")

        val result = text.tokenizeOnSpaces()

        assertEquals("Hello World", result)
    }

    @Test
    fun testTokenizeWordsRawText() {
        val text = Text("Hello World Test")

        val result = text.tokenizeWordsRawText()

        // Should tokenize and return space-separated words
        assertTrue("Result should not be empty", result.isNotEmpty())
        assertTrue("Result should contain Hello", result.contains("Hello"))
    }

    @Test
    fun testTextWithTibetanContent() {
        val text = Text("བཀྲ་ཤིས་མཐའི་")

        val result = text.tokenizeWordsRawText()

        // Should process Tibetan text
        assertTrue("Should process Tibetan text", result.isNotEmpty())
    }

    @Test
    fun testCustomPipeline() {
        val text = Text("Test input")

        val result = text.customPipeline(
            "basic_cleanup",
            "space_tok",
            "dummy",
            "plaintext"
        )

        // Should process through custom pipeline
        assertTrue("Should return string", result is String)
        assertTrue("Should not be empty", (result as String).isNotEmpty())
    }
}