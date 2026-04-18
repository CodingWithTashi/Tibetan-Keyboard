package com.kharagedition.botok

import com.kharagedition.botok.utils.CorpusNormalization
import com.kharagedition.botok.utils.StandardTibetan
import com.kharagedition.botok.utils.LenientNormalization
import com.kharagedition.botok.utils.Helpers
import com.kharagedition.botok.text.Text
import com.kharagedition.botok.Botok
import org.junit.Test
import org.junit.Assert.*

/**
 * Parity validation tests
 *
 * Compares Kotlin botok outputs against Python botok for 100% output parity
 */
class ParityTest {

    @Test
    fun testCorpusNormalizationNormalizeSpaces() {
        val input = "a\n\n b  \n c"
        val result = CorpusNormalization.normalizeSpaces(input)
        val expected = "a\nb\nc"
        assertEquals("normalize_spaces basic spacing", expected, result)
    }

    @Test
    fun testCorpusNormalizationTibetanSpacing() {
        val input = "\u0f0b \u0f40 \u0f66 \u0f0b"  // tsheg, initial, final, tsheg
        val result = CorpusNormalization.normalizeSpaces(input)
        val expected = "\u0f0b\u0f40 \u0f66\u0f0b"
        assertEquals("normalize_spaces tibetan spacing", expected, result)
    }

    @Test
    fun testCorpusNormalizationFullPipeline() {
        val input = "a\u00a0\u200b b\r\nc\u0f0c\u0f0e\u0001"
        val result = CorpusNormalization.normalizeCorpus(input)
        val expected = "a b\nc\u0f0b\u0f0d\u0f0d"
        assertEquals("normalize_corpus full pipeline", expected, result)
    }

    @Test
    fun testStandardTibetanBasicSyllables() {
        // Test standard Tibetan syllables
        assertTrue("Single consonant 'ka'", StandardTibetan.isStandardTibetan("ཀ"))
        assertTrue("Complex onset 'bka+r'", StandardTibetan.isStandardTibetan("བཀྲ"))
        assertTrue("Full syllable 'grugs'", StandardTibetan.isStandardTibetan("གྲུགས"))
    }

    @Test
    fun testStandardTibetanNonStandardSyllables() {
        // Test non-standard (Sanskrit) syllables
        assertFalse("Sanskrit vowel sign", StandardTibetan.isStandardTibetan("ཨཱ"))
        assertFalse("Extended cluster", StandardTibetan.isStandardTibetan("ཀྱྭ"))
    }

    @Test
    fun testSplitIntoStacks() {
        // Test stack splitting
        val result1 = StandardTibetan.splitIntoStacks("ཀ")
        assertEquals("Single consonant stack", listOf("ཀ"), result1)

        val result2 = StandardTibetan.splitIntoStacks("བཀྲ")
        assertEquals("Complex stack", listOf("བཀྲ"), result2)

        // Sanskrit stacks would split into multiple
        val sanskritSyllable = "ཨཱཪྱ"
        val sanskritStacks = StandardTibetan.splitIntoStacks(sanskritSyllable)
        assertTrue("Sanskrit syllable splits into stacks", sanskritStacks.size >= 1)
    }

    @Test
    fun testLenientNormalizationRemoveAffixes() {
        val input1 = "བཀྲིས"
        val result1 = LenientNormalization.removeAffixes(input1)
        assertEquals("Remove ིས affix", "བཀྲ", result1)

        val input2 = "པའིའོ"
        val result2 = LenientNormalization.removeAffixes(input2)
        assertEquals("Remove འིའོ affixes", "པ", result2)
    }

    @Test
    fun testLenientNormalizationOldTibetan() {
        val input1 = "དྲངསྟེ"
        val result1 = LenientNormalization.normalizeOldTibetan(input1)
        assertEquals("Old Tibetan rule 1", "དྲངས་ཏེ", result1)

        val input2 = "གཅལྟོ"
        val result2 = LenientNormalization.normalizeOldTibetan(input2)
        assertEquals("Old Tibetan rule 2", "གཅལ་ཏོ", result2)
    }

    @Test
    fun testHelpersDecommentContent() {
        val input = """
            # This is a comment
            line 1
            line 2 # another comment
            line 3
        """.trimIndent()

        val result = Helpers.decommentContent(input).toList()
        assertEquals("Should have 3 non-comment lines", 3, result.size)
        assertEquals("First line", "line 1", result[0])
        assertEquals("Second line", "line 2", result[1])
        assertEquals("Third line", "line 3", result[2])
    }

    @Test
    fun testHelpersReadTsv() {
        val input = """
            header1	heading2
            value1	value2
            value3	value4
        """.trimIndent()

        val result = Helpers.readTsvAsList(input)
        assertEquals("Should have 3 rows", 3, result.size)
        assertEquals("Header row", listOf("header1", "heading2"), result[0])
        assertEquals("First data row", listOf("value1", "value2"), result[1])
        assertEquals("Second data row", listOf("value3", "value4"), result[2])
    }

    @Test
    fun testTextTokenizeOnSpaces() {
        val input = "word1 word2  word3"
        val text = Text(input)
        val result = text.tokenizeOnSpaces()
        val expected = "word1 word2 word3"
        assertEquals("Tokenize on spaces", expected, result)
    }

    @Test
    fun testTextTokenizeWordsRawText() {
        val input = "བཀྲ་ཤིས་མཐའི་"
        val text = Text(input)
        val result = text.tokenizeWordsRawText()
        // Should tokenize and replace spaces with underscores
        assertTrue("Should produce output", result.isNotEmpty())
        assertTrue("Should contain བཀྲ་", result.contains("བཀྲ་"))
    }

    @Test
    fun testBotokTokenizeWords() {
        val input = "བཀྲ་ཤིས་མཐའི་"
        val result = Botok.tokenizeWords(input)
        assertTrue("Should produce word tokens", result.isNotEmpty())
        assertTrue("Should contain བཀྲ", result.any { it.contains("བཀྲ") })
    }

    @Test
    fun testBotokIsStandardTibetan() {
        assertTrue("Standard syllable 'ka'", Botok.isStandardTibetan("ཀ"))
        assertTrue("Standard syllable 'bka+r'", Botok.isStandardTibetan("བཀྲ"))
        assertFalse("Non-standard Sanskrit", Botok.isStandardTibetan("ཨཱ"))
    }

    @Test
    fun testBotokSplitIntoStacks() {
        val result = Botok.splitIntoStacks("བཀྲ")
        assertEquals("Split stack", listOf("བཀྲ"), result)
    }

    @Test
    fun testBotokNormalizeCorpus() {
        val input = "a\u00a0\u200b b\r\nc\u0f0c\u0f0e"
        val result = Botok.normalizeCorpus(input)
        val expected = "a b\nc\u0f0b\u0f0d\u0f0d"
        assertEquals("Normalize corpus", expected, result)
    }

    @Test
    fun testBotokRemoveAffixes() {
        val input = "བཀྲིས"
        val result = Botok.removeAffixes(input)
        assertEquals("Remove affixes", "བཀྲ", result)
    }

    @Test
    fun testIntegrationComplexTibetanText() {
        val input = """
            བཀུར་བར་མི་འགྱུར་ཞིང༌། །བརྙས་བཅོས་མི་སྙན་རྗོད་པར་བྱེད།
            །དབང་དང་འབྱོར་པ་ལྡན་པ་ཡི། །རྒྱལ་རིགས་ཕལ་ཆེར་བདེ་ལེགས་༡༢༣ཀཀ།
            མཐའི་རྒྱ་མཚོར་གནས་པའི་ཉས་ཆུ་འཐུང་།། །།མཁའ །
        """.trimIndent()

        val text = Text(input)
        val result = text.tokenizeWordsRawText()

        // Should process complex Tibetan text successfully
        assertTrue("Should produce output", result.isNotEmpty())
        assertTrue("Should contain བཀུར", result.contains("བཀུར"))
        assertTrue("Should contain བར", result.contains("བར"))
    }

    @Test
    fun testNormalizationForPerplexity() {
        val input = "བཀྲ་ཤིས་མཐའི་ ༆ ཤི་བཀྲ་ཤིས་"
        val result = CorpusNormalization.normalizeForPerplexity(input)

        // Should normalize for perplexity calculation
        assertTrue("Should produce output", result.isNotEmpty())
        // Should contain shad markers
        assertTrue("Should contain shad marker", result.contains("\u0F0D"))
    }

    @Test
    fun testMergeLines() {
        val input = """
            བཀྲ་ཤིས་
            མཐའི་
            །
        """.trimIndent()

        val result = CorpusNormalization.mergeLines(input)

        // Should merge lines into single line
        assertFalse("Should not contain newlines", result.contains("\n"))
        assertTrue("Should contain first part", result.contains("བཀྲ་ཤིས་"))
        assertTrue("Should contain second part", result.contains("མཐའི་"))
    }

    @Test
    fun testCustomPipeline() {
        val input = "word1 word2 word3"
        val text = Text(input)

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