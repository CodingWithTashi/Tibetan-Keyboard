package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.chunks.TokChunks
import com.kharagedition.botok.config.Config
import com.kharagedition.botok.textunits.BoSyl
import com.kharagedition.botok.tries.Trie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Port of tests/tokenizers/test_tokenize.py
 */
class TokenizeTest {

    companion object {
        private lateinit var tok: Tokenize
        private lateinit var trie: Trie

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Create minimal trie for fast testing
            trie = Trie(
                BoSyl(),
                "test",
                emptyMap(),
                emptyMap()
            )

            // Add test words to trie
            trie.inflectNModifyTrie("བཀྲ་ཤིས་")
            trie.inflectNAddData("བཀྲ་ཤིས་\tNOUN\t\tབཀྲ་ཤིས་\t17500")
            trie.inflectNModifyTrie("མཐའ་")
            trie.inflectNAddData("མཐའ་\tNOUN")

            tok = Tokenize(trie)
        }
    }

    @Test
    fun testTokenize() {
        val inStr = "མཐའི་བཀྲ་ཤིས། ཀཀ abc མཐའི་རྒྱ་མཚོ་"
        val preproc = TokChunks(inStr)
        preproc.serveSylsToTrie()
        val tokens = tok.tokenize(preproc)

        // Expected token 1 (བཀྲ་ཤིས)
        val expected = """
            |text: "བཀྲ་ཤིས"
            |text_cleaned: "བཀྲ་ཤིས་"
            |text_unaffixed: "བཀྲ་ཤིས་"
            |syls: ["བཀྲ", "ཤིས"]
            |senses: | pos: NOUN, freq: 17500, sense: བཀྲ་ཤིས་, affixed: False |
            |char_types: |CONS|CONS|SUB_CONS|TSEK|CONS|VOW|CONS|
            |chunk_type: TEXT
            |syls_idx: [[0, 1, 2], [4, 5, 6]]
            |syls_start_end: [{'start': 0, 'end': 4}, {'start': 4, 'end': 7}]
            |start: 5
            |len: 7
            |
            |
        """.trimMargin().trimEnd() + "\n\n"

        // Check token 1
        assertEquals("བཀྲ་ཤིས", tokens[1].text)
        assertEquals("བཀྲ་ཤིས་", tokens[1].textCleaned)
        assertEquals("བཀྲ་ཤིས་", tokens[1].textUnaffixed)
        assertEquals(listOf(listOf("བ", "ཀ", "ྲ"), listOf("ཤ", "ི", "ས")), tokens[1].syls)
        assertEquals("NOUN", tokens[1].pos)
        assertEquals(17500, tokens[1].freq)
        assertEquals("བཀྲ་ཤིས་", tokens[1].sense)
        assertEquals("TEXT", tokens[1].chunkType)
        assertEquals(5, tokens[1].start)
        assertEquals(7, tokens[1].len)

        // Check token 2 (punctuation)
        assertEquals("། ", tokens[2].text)
        assertEquals("PUNCT", tokens[2].chunkType)
    }

    @Test
    fun testNonMax2() {
        val preproc = TokChunks("བཀྲ་ཤིས་བདེ་བཀྲ་")
        preproc.serveSylsToTrie()
        val tokens = tok.tokenize(preproc)

        assertEquals("བཀྲ་ཤིས་", tokens[0].text)
        assertEquals("NOUN", tokens[0].senses?.get(0)?.get("pos"))
        assertEquals("བདེ་", tokens[1].text)
        assertEquals("NON_WORD", tokens[1].senses?.get(0)?.get("pos"))
        assertEquals("བཀྲ་", tokens[2].text)
        assertEquals("NO_POS", tokens[2].senses?.get(0)?.get("pos"))
    }

    @Test
    fun testNonMaxEndOfString() {
        val preproc = TokChunks("བཀྲ་ཤིས་བདེ་")
        preproc.serveSylsToTrie()
        val tokens = tok.tokenize(preproc)

        assertEquals("བཀྲ་ཤིས་", tokens[0].text)
        assertEquals("བདེ་", tokens[1].text)
        assertEquals(2, tokens.size)
    }

    @Test
    fun testMaxMatch() {
        // Test that max-match finds longest possible match
        val preproc = TokChunks("བཀྲ་ཤིས་")
        preproc.serveSylsToTrie()
        val tokens = tok.tokenize(preproc)

        assertEquals(1, tokens.size)
        assertEquals("བཀྲ་ཤིས་", tokens[0].text)
    }

    @Test
    fun testNonTibetan() {
        val preproc = TokChunks("abc")
        preproc.serveSylsToTrie()
        val tokens = tok.tokenize(preproc)

        assertTrue(tokens.isNotEmpty())
        assertEquals("abc", tokens[0].text)
    }

    @Test
    fun testMixedContent() {
        val preproc = TokChunks("བཀྲ་ཤིས་ abc བདེ་", spaceAsPunct = true)
        preproc.serveSylsToTrie()
        val tokens = tok.tokenize(preproc)

        assertTrue(tokens.size >= 3)
        assertEquals("བཀྲ་ཤིས་", tokens[0].text)
        assertEquals(" ", tokens[1].text)
        assertEquals("abc", tokens[2].text)
    }
}
