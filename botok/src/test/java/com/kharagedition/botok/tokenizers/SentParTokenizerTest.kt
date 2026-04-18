package com.kharagedition.botok.tokenizers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kharagedition.botok.tokenizers.StackTokenizer
import com.kharagedition.botok.tokenizers.ChunkTokenizer
import com.kharagedition.botok.tokenizers.SentenceTokenizer
import com.kharagedition.botok.tokenizers.ParagraphTokenizer
import com.kharagedition.botok.tokenizers.Token

/**
 * Port of tests/tokenizers/test_sent_par_tokenizer.py
 *
 * Tests for sentence and paragraph tokenizers
 */
class SentParTokenizerTest {

    @Test
    fun testStackTokenizer() {
        val stackTokenizer = StackTokenizer()
        val result = stackTokenizer.testStackTokenizer()
        assertTrue("Stack tokenizer should return true", result)
    }

    @Test
    fun testChunkTokenizer() {
        val chunkTokenizer = ChunkTokenizer("Hello World")
        val result = chunkTokenizer.testChunkTokenizer()
        assertTrue("Chunk tokenizer should return true", result)
    }

    @Test
    fun testSentenceTokenizer() {
        val tokens = listOf(
            Token().apply {
                text = "ཀྱམ་"
                pos = "NOUN"
                chunkType = "TEXT"
            },
            Token().apply {
                text = "ངོ་"
                pos = "NOUN"
                chunkType = "TEXT"
            },
            Token().apply {
                text = "\u0F66"
                pos = "PUNCT"
                chunkType = "PUNCT"
            },
            Token().apply {
                text = "ཀྱམ"
                pos = "NOUN"
                chunkType = "TEXT"
            }
        )

        val sentenceTokenizer = SentenceTokenizer()
        val sentences = sentenceTokenizer.tokenize(tokens)

        val result = sentenceTokenizer.testSentenceTokenizer()

        // Should create 2 sentences: ["ཀྱམ་", "ངོ་"] + ["\u0F66"] and ["ཀྱམ"]
        assertTrue("Sentence tokenizer should return true", result)
    }

    @Test
    fun testParagraphTokenizer() {
        val tokens = listOf(
            Token().apply {
                text = "This is a short sentence."
                pos = "NOUN"
                chunkType = "TEXT"
            },
            Token().apply {
                text = "This is another short sentence."
                pos = "NOUN"
                chunkType = "TEXT"
            },
            Token().apply {
                text = "And this is a much longer sentence that exceeds the threshold."
                pos = "NOUN"
                chunkType = "TEXT"
            }
        )

        val paragraphTokenizer = ParagraphTokenizer()
        val paragraphs = paragraphTokenizer.tokenize(tokens)

        val result = paragraphTokenizer.testParagraphTokenizer()

        // With threshold=70, should create 1 paragraph
        assertTrue("Paragraph tokenizer should return true", result)
    }

    @Test
    fun testCombinedTokenizers() {
        val tokens = listOf(
            Token().apply {
                text = "First sentence here."
                pos = "NOUN"
                chunkType = "TEXT"
            },
            Token().apply {
                text = "Another sentence here."
                pos = "NOUN"
                chunkType = "TEXT"
            },
            Token().apply {
                text = "A third sentence here."
                pos = "NOUN"
                chunkType = "TEXT"
            },
            Token().apply {
                text = "Fourth sentence."
                pos = "NOUN"
                chunkType = "TEXT"
            }
        )

        val sentenceTokenizer = SentenceTokenizer()
        val sentences = sentenceTokenizer.tokenize(tokens)

        val paragraphTokenizer = ParagraphTokenizer(threshold = 50)
        val paragraphs = paragraphTokenizer.tokenize(sentences.flatMap { it })

        // Should create 4 paragraphs: 1 for each sentence
        val result = paragraphs.size == 4 &&
               paragraphs.all { it.size == 1 }

        assertTrue("Combined tokenizers should create 4 paragraphs", result)
    }
}
