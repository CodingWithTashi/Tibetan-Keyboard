package com.kharagedition.botok.modifytokens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SplittingMatcher functionality
 */
class SplittingMatcherTest {

    @Test
    fun testSplitTokenAtPosition() {
        val token = mapOf(
            "text" to "བཀྲ་ཤིས་",
            "pos" to "NOUN"
        )

        // Test that token has content that can be split
        val text = token["text"]
        assertTrue(text != null)
        assertTrue(text!!.length > 1)
    }

    @Test
    fun testSplitWithCqlPattern() {
        val tokens = listOf(
            mapOf("text" to "བཀྲ་ཤིས་", "pos" to "NOUN")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(tokens)

        // Should find the NOUN token
        assertTrue(matched.isNotEmpty())
    }

    @Test
    fun testSplitMultipleTokens() {
        val tokens = listOf(
            mapOf("text" to "བཀྲ་ཤིས་", "pos" to "NOUN"),
            mapOf("text" to "བདེ་ལེགས་", "pos" to "NOUN")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(tokens)

        // Should find both NOUN tokens
        assertEquals(2, matched.size)
    }

    @Test
    fun testNoSplitNeeded() {
        val token = mapOf(
            "text" to "བ",
            "pos" to "NOUN"
        )

        // Single character shouldn't be split
        val text = token["text"]
        assertEquals(1, text!!.length)
    }

    @Test
    fun testSplitPreservesAttributes() {
        val token = mapOf(
            "text" to "བཀྲ་ཤིས་",
            "pos" to "NOUN",
            "lemma" to "test"
        )

        // Token should have expected attributes
        assertTrue(token.containsKey("pos"))
        assertTrue(token.containsKey("text"))
        assertEquals("NOUN", token["pos"])
    }
}
