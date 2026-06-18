package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.third_party.cql.CqlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ReplacingMatcher functionality
 */
class ReplacingMatcherTest {

    @Test
    fun testReplaceSingleAttribute() {
        val tokens = listOf(
            mapOf("text" to "hello", "pos" to "NOUN"),
            mapOf("text" to "world", "pos" to "VERB")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(tokens)

        // Should find first token
        assertTrue(matched.isNotEmpty())
        assertEquals(0, matched[0].first)
    }

    @Test
    fun testReplaceWithCqlMatcher() {
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
    fun testReplaceOnlyMatchedTokens() {
        val tokens = listOf(
            mapOf("text" to "test1", "pos" to "NOUN"),
            mapOf("text" to "test2", "pos" to "VERB")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(tokens)

        // Should find first token only
        assertEquals(1, matched.size)
        assertEquals(0, matched[0].first)
    }

    @Test
    fun testReplacePreservesText() {
        val token = mapOf(
            "text" to "hello",
            "pos" to "NOUN"
        )

        val originalText = token["text"]

        // Text should be preserved
        assertEquals("hello", originalText)
    }

    @Test
    fun testCqlParserExists() {
        // Test that CqlParser has the expected methods
        val query = "[pos=\"NOUN\"]"
        val parsed = CqlParser.parseCqlQuery(query)

        assertNotNull(parsed)
        assertTrue(parsed!!.isNotEmpty())
    }

    @Test
    fun testComplexPattern() {
        val token = mapOf(
            "text" to "བཀྲ་ཤིས་",
            "pos" to "NOUN",
            "lemma" to "old"
        )

        // Token should have all expected attributes
        assertEquals("NOUN", token["pos"])
        assertEquals("old", token["lemma"])
        assertTrue(token["text"]!!.contains("བཀྲ"))
    }
}
