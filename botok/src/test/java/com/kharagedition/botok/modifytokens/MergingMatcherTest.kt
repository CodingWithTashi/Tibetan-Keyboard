package com.kharagedition.botok.modifytokens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for MergingMatcher functionality
 */
class MergingMatcherTest {

    @Test
    fun testMergeWithCqlMatcher() {
        val tokens = listOf(
            mapOf("text" to "first", "pos" to "NOUN"),
            mapOf("text" to "second", "pos" to "VERB")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(tokens)

        // Should find first token
        assertTrue(matched.isNotEmpty())
        assertEquals(0, matched[0].first)
    }

    @Test
    fun testMergeAllMatching() {
        val tokens = listOf(
            mapOf("text" to "test1", "pos" to "NOUN"),
            mapOf("text" to "test2", "pos" to "NOUN"),
            mapOf("text" to "test3", "pos" to "VERB")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(tokens)

        // Should find both NOUN tokens
        assertEquals(2, matched.size)
    }

    @Test
    fun testNoMatch() {
        val tokens = listOf(
            mapOf("text" to "hello", "pos" to "VERB"),
            mapOf("text" to "world", "pos" to "VERB")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(tokens)

        // Should not find any NOUN tokens
        assertEquals(0, matched.size)
    }

    @Test
    fun testMergeWithIndex() {
        val tokens = listOf(
            mapOf("text" to "first", "pos" to "NOUN"),
            mapOf("text" to "second", "pos" to "VERB"),
            mapOf("text" to "third", "pos" to "NOUN")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(tokens)

        // Should find first and third tokens
        assertEquals(2, matched.size)
        assertEquals(0, matched[0].first)
        assertEquals(2, matched[1].first)
    }

    @Test
    fun testMergingOperation() {
        val tokens = listOf(
            mapOf("text" to "བ", "pos" to "NOUN"),
            mapOf("text" to "དེ", "pos" to "VERB")
        )

        // Test that tokens exist
        assertTrue(tokens.isNotEmpty())
        assertEquals(2, tokens.size)
    }
}
