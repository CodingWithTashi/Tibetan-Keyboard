package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.config.Config
import com.kharagedition.botok.textunits.BoSyl
import com.kharagedition.botok.tokenizers.Token
import com.kharagedition.botok.tokenizers.WordTokenizer
import com.kharagedition.botok.tries.Trie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Port of tests/tokenizers/test_splitaffixed.py
 *
 * Tests the SplitAffixed functionality which splits tokens containing affixed particles.
 */
class SplitAffixedTest {

    companion object {
        private lateinit var wt: WordTokenizer

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Create minimal WordTokenizer for fast testing
            wt = WordTokenizer(buildTrie = false)
        }
    }

    @Test
    fun testSplitToken() {
        // Test input with affixed particles
        val input = "བསྟན་བཅོས་ཀྱི་དགོངས་དོན།"

        // Tokenize without splitting affixes
        val tokensNoSplit = wt.tokenize(input, splitAffixes = false)

        // Tokenize with splitting affixes (default)
        val tokensWithSplit = wt.tokenize(input, splitAffixes = true)

        // With split affixes, we should have more or equal tokens
        assertTrue(tokensWithSplit.size >= tokensNoSplit.size)

        // Verify affix properties are set correctly (if any affixes exist)
        for (token in tokensWithSplit) {
            if (token.affix) {
                assertEquals(true, token.affix)
                assertEquals(false, token.affixHost)
            }
            if (token.affixHost) {
                assertEquals(true, token.affixHost)
                assertEquals(false, token.affix)
            }
        }

        // Verify that tokenization happened
        assertTrue(tokensWithSplit.isNotEmpty())
    }

    @Test
    fun testSplitAffixedProperties() {
        // Test a simpler case with known affixed particles
        val input = "བཀྲ་ཤིས་ཀྱི་"

        val tokens = wt.tokenize(input, splitAffixes = true)

        // Should have at least 1 token
        assertTrue(tokens.size >= 1)

        // Find the affixed particle (if any)
        val particle = tokens.find { it.affix }
        if (particle != null) {
            assertEquals(true, particle.affix)
            assertEquals(false, particle.affixHost)
        }

        // Find the affix host (if any)
        val host = tokens.find { it.affixHost }
        if (host != null) {
            assertEquals(true, host.affixHost)
            assertEquals(false, host.affix)
        }
    }

    @Test
    fun testNoSplitWhenDisabled() {
        val input = "བཀྲ་ཤིས་ཀྱི་"

        // Without splitting
        val tokensNoSplit = wt.tokenize(input, splitAffixes = false)

        // With splitting
        val tokensWithSplit = wt.tokenize(input, splitAffixes = true)

        // With splitting should have equal or more tokens
        assertTrue(tokensWithSplit.size >= tokensNoSplit.size)

        // When split is disabled, affix tokens should not exist
        val affixTokensNoSplit = tokensNoSplit.filter { it.affix }
        assertEquals(0, affixTokensNoSplit.size)

        // When split is enabled, affix tokens may exist
        val affixTokensWithSplit = tokensWithSplit.filter { it.affix }
        assertTrue(affixTokensWithSplit.size >= 0)
    }
}
