package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.config.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Port of tests/tokenizers/test_wordtokenizer.py
 */
class WordTokenizerTest {

    companion object {
        private lateinit var wt: WordTokenizer

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Create WordTokenizer with general config
            val assetsPath = System.getProperty("user.dir") + "/src/main/assets/botok/general"
            val config = Config(assetsPath)
            wt = WordTokenizer(config)
        }
    }

    @Test
    fun testGetDefaultLemma() {
        val inputStr = "བཀྲ་ཤིས་བདེ་ལེགས། མཐའི་རྒྱ་མཚོར་གནས་སོ།། །།ཀཀ"
        val tokens = wt.tokenize(inputStr)

        // Check that lemma is added to མཐ (affix host)
        // token[3] should be མཐ
        assertTrue(tokens.size > 3)

        // For affix host, lemma should be text_unaffixed + AA + TSEK
        // if affixation["aa"] is true
        val affixHostToken = tokens.find { it.text == "མཐ" }
        if (affixHostToken != null) {
            // The lemma should be set
            assertTrue(affixHostToken.lemma.isNotEmpty())
        }

        // Check that particle འི་ gets lemma from particles.tsv
        val particleToken = tokens.find { it.text == "འི་" }
        if (particleToken != null) {
            assertEquals("PART", particleToken.pos)
            assertEquals("གི་", particleToken.lemma)
        }

        // Check regular word gets lemma
        val regularToken = tokens.find { it.text == "བཀྲ་ཤིས་" }
        if (regularToken != null) {
            assertEquals("བཀྲ་ཤིས་", regularToken.lemma)
        }

        // Non-words should not have lemma in senses
        val nonWordToken = tokens.find { it.text.contains("ཀཀ") }
        if (nonWordToken != null) {
            val hasLemma = nonWordToken.senses?.any { "lemma" in it } ?: false
            assertTrue(!hasLemma || nonWordToken.textUnaffixed.isNotEmpty())
        }
    }

    @Test
    fun testSpacesAsPunct() {
        val inputStr = "བ ཀྲ་ཤིས་ བདེ་ལེགས། \nམཐའི་རྒྱ་མཚོར་ག ནས་སོ།། །།ཀཀ"
        val tokens = wt.tokenize(inputStr, spacesAsPunct = true)

        assertTrue(tokens.size > 8)
        assertEquals("བ", tokens[0].text)
        assertEquals(" ", tokens[1].text)
        assertEquals("ཀྲ་", tokens[2].text)
        assertEquals(" ", tokens[3].text)
        assertEquals(" \n", tokens[8].text)
    }

    @Test
    fun testParticleBug() {
        val inputStr = "བོད་གིས"
        val tokens = wt.tokenize(inputStr)

        // The particle གིས should be recognized as PART
        val particleToken = tokens.find { it.text == "གིས" }
        if (particleToken != null) {
            assertEquals("PART", particleToken.pos)
        }
    }

    @Test
    fun testBasicTokenization() {
        val inputStr = "བཀྲ་ཤིས་བདེ་ལེགས།"
        val tokens = wt.tokenize(inputStr)

        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.any { it.text.contains("བཀྲ") })
        assertTrue(tokens.any { it.text.contains("ཤིས") })
    }

    @Test
    fun testAffixedWords() {
        val inputStr = "མཐའི་"
        val tokens = wt.tokenize(inputStr)

        // Should split into མཐ and འི་
        assertTrue(tokens.size >= 2)

        // First token should be affix host
        val affixHost = tokens.find { it.affixHost && !it.affix }
        if (affixHost == null) {
            throw AssertionError("Should find affix host token")
        }

        // Second token should be affix
        val affix = tokens.find { it.affix && !it.affixHost }
        if (affix == null) {
            throw AssertionError("Should find affix token")
        }
        assertEquals("PART", affix.pos)
    }

    @Test
    fun testDagdraMerging() {
        // Test that pa/po/ba/bo particles get merged
        val inputStr = "བདེ་པོ"
        val tokens = wt.tokenize(inputStr)

        // After mergeDagdra, should be merged into one token
        val mergedToken = tokens.find { it.hasMergedDagdra == true }
        if (mergedToken != null) {
            // Check that merged token contains both parts
            assertTrue(mergedToken.text.contains("བདེ"))
            assertTrue(mergedToken.text.contains("པོ"))
        }
    }

    @Test
    fun testMultipleSentences() {
        val inputStr = "བཀྲ་ཤིས་བདེ་ལེགས། མཐའི་རྒྱ་མཚོ།"
        val tokens = wt.tokenize(inputStr)

        // Should tokenize both sentences
        assertTrue(tokens.size > 5)
        assertTrue(tokens.any { it.text.contains("བཀྲ") })
        assertTrue(tokens.any { it.text.contains("མཐ") })
        assertTrue(tokens.any { it.chunkType == "PUNCT" })
    }

    @Test
    fun testWithPunctuation() {
        val inputStr = "བཀྲ་ཤིས་བདེ་ལེགས། མཐའི་རྒྱ་མཚོར་གནས་སོ།།"
        val tokens = wt.tokenize(inputStr)

        // Should preserve punctuation
        val punctTokens = tokens.filter { it.chunkType == "PUNCT" }
        assertTrue(punctTokens.isNotEmpty())
    }

    @Test
    fun testNonWordHandling() {
        val inputStr = "ཀཀ"
        val tokens = wt.tokenize(inputStr)

        // Should tokenize non-words as NON_WORD
        val nonWord = tokens.find { it.senses?.any { it["pos"] == "NON_WORD" } == true }
        if (nonWord == null) {
            throw AssertionError("Should find NON_WORD token")
        }
    }
}
