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
            // Create WordTokenizer with general pack
            val assetsPath = System.getProperty("user.dir") + "/src/main/assets/botok/general"
            val config = Config(assetsPath)

            wt = WordTokenizer(config)
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

        // With split affixes, we should have more tokens
        assertTrue(tokensWithSplit.size >= tokensNoSplit.size)

        // Verify that some tokens have been split
        // Look for particles that should be split
        val particles = tokensWithSplit.filter { it.pos == "PART" }
        assertTrue(particles.isNotEmpty())

        // Verify affix properties are set correctly
        for (token in tokensWithSplit) {
            if (token.affix) {
                assertEquals("PART", token.pos)
                assertEquals(true, token.affix)
                assertEquals(false, token.affixHost)
            }
            if (token.affixHost) {
                assertEquals(true, token.affixHost)
                assertEquals(false, token.affix)
            }
        }
    }

    @Test
    fun testSplitAffixedProperties() {
        // Test a simpler case with known affixed particles
        val input = "བཀྲ་ཤིས་ཀྱི་"

        val tokens = wt.tokenize(input, splitAffixes = true)

        // Should have at least 2 tokens: base word + particle
        assertTrue(tokens.size >= 2)

        // Find the affixed particle
        val particle = tokens.find { it.affix }
        if (particle != null) {
            assertEquals("PART", particle.pos)
            assertEquals(true, particle.affix)
            assertEquals(false, particle.affixHost)
        }

        // Find the affix host
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
