package com.kharagedition.botok.modifytokens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import com.kharagedition.botok.third_party.cql.Query
import com.kharagedition.botok.third_party.cql.CqlParser
import com.kharagedition.botok.tokenizers.Token

/**
 * Port of tests/modifytokens/test_matchers.py
 *
 * Tests for CQL matcher and token modification operations
 */
class MatchersTest {

    @Test
    fun testCqlQuery() {
        val query = "[text=\"ན\"] []"
        val q = Query(query)
        assertTrue(q.tokenExprs.isNotEmpty())
    }

    @Test
    fun testCqlParser() {
        val query = "[pos=\"NOUN\" & lemma=\"test\"]"
        val parsed = CqlParser.parseCqlQuery(query)

        assertNotNull(parsed)
        assertTrue(parsed!!.isNotEmpty())
        assertEquals("NOUN", parsed[0]["pos"])
        assertEquals("test", parsed[0]["lemma"])
    }

    @Test
    fun testReplaceTokenAttributes() {
        val tokens = mutableListOf(
            mutableMapOf("text" to "hello", "pos" to "NOUN"),
            mutableMapOf("text" to "world", "pos" to "NOUN")
        )

        val tokenChanges = "[pos=\"VERB\"]"
        CqlParser.replaceTokenAttributes(listOf(tokens[0]), tokenChanges)

        assertEquals("VERB", tokens[0]["pos"])
        assertEquals("NOUN", tokens[1]["pos"]) // Should remain unchanged
    }

    @Test
    fun testAdjustTokensRuleParsing() {
        val ruleParts = listOf(
            "[pos=\"NOUN\"]",
            "1",
            "=",
            "[pos=\"VERB\"]"
        )

        val rule = AdjustTokens.parseRule(ruleParts)

        assertEquals("[pos=\"NOUN\"]", rule.matchCql)
        assertEquals(1, rule.matchIdx)
        assertEquals(com.kharagedition.botok.modifytokens.AdjustTokens.Operation.REPLACE, rule.operation)
        assertEquals("[pos=\"VERB\"]", rule.replaceCql)
    }

    @Test
    fun testSplitRuleParsing() {
        val ruleParts = listOf(
            "[pos=\"NOUN\"]",
            "1-2",
            ":",
            "[pos=\"VERB\"]"
        )

        val rule = AdjustTokens.parseRule(ruleParts)

        assertEquals("[pos=\"NOUN\"]", rule.matchCql)
        assertEquals(1, rule.matchIdx)
        assertEquals(2, rule.splitIdx)
        assertEquals(com.kharagedition.botok.modifytokens.AdjustTokens.Operation.SPLIT, rule.operation)
        assertEquals(com.kharagedition.botok.modifytokens.AdjustTokens.SplitMode.SYLLABLE, rule.splitMode)
    }

    @Test
    fun testLastToken() {
        val token1 = mutableMapOf("pos" to "NOUN", "text_cleaned" to "word1")
        val token2 = mutableMapOf("pos" to "VERB", "text_cleaned" to "word2")

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val slices = matcher.match(listOf(token1, token2))
        assertEquals(listOf(Pair(0, 0)), slices)

        val matcher2 = CqlMatcher("[pos=\"VERB\"]")
        val slices2 = matcher2.match(listOf(token1, token2))
        assertEquals(listOf(Pair(1, 1)), slices2)
    }

    @Test
    fun testBasicCQLMatching() {
        val test = listOf(
            mapOf("text" to "hello", "text_cleaned" to "hello", "pos" to "NOUN"),
            mapOf("text" to "world", "text_cleaned" to "world", "pos" to "NOUN"),
            mapOf("text" to "foo", "text_cleaned" to "foo", "pos" to "VERB")
        )

        val matcher = CqlMatcher("[pos=\"NOUN\"]")
        val matched = matcher.match(test)

        assertEquals(2, matched.size)
        assertEquals(Pair(0, 0), matched[0])
        assertEquals(Pair(1, 1), matched[1])
    }
}
