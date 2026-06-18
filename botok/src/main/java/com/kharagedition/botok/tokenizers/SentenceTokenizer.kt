package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.tokenizers.Token

/**
 * Port of botok/tokenizers/sentencetokenizer.py
 *
 * Sentence tokenizer for Tibetan text
 * Segments token lists into sentences based on ending particles,
 * clause boundaries, and other linguistic features
 */
class SentenceTokenizer {

    companion object {
        // Ending particles for Tibetan
        private val ENDING_PARTICLES = listOf(
            "\u0FC2", "\u0F43", "\u0F53", "\u0F54",
            "\u0F55", "\u0F56", "\u0F5C", "\u0F57",
            "\u0F58", "\u0F59", "\u0F5A", "\u0F5B",
            "\u0F5C", "\u0F5D", "\u0F5E", "\u0F5F"
        )

        // Ending words
        private val ENDING_WORDS = listOf("\u0F52", "\u0F5B")

        // Clause boundary particles
        private val CLAUSE_BOUNDARIES = listOf(
            "\u0F66", "\u0F51", "\u0F63", "\u0F68", "\u0F44"
        )

        // Dagdra particles
        private val DAGDRA = listOf("\u0F44", "\u0F56", "\u0F66")

        // Verb ending particles
        private val VERB_ENDING_PARTICLES = listOf(
            "\u0F4C", "\u0F74", "\u0F7E", "\u0F7F", "\u0F7C",
            "\u0F7D", "\u0F7E"
        )

        // Default threshold for paragraph detection
        private const val DEFAULT_THRESHOLD = 70
        private const val PARAGRAPH_MAX = 150
    }

    /**
     * Tokenize input text into sentences
     *
     * @param tokens List of Token objects to segment
     * @return List of sentences, each as a list of tokens
     */
    fun tokenize(tokens: List<Token>): List<List<Token>> {
        if (tokens.isEmpty()) return emptyList()

        val sentenceIndices = getSentenceIndices(tokens)

        val sentences = mutableListOf<List<Token>>()

        for (sentenceIndex in sentenceIndices) {
            val start = sentenceIndex["start"] as Int
            val end = sentenceIndex["end"] as Int
            val length = sentenceIndex["len"] as Int

            // Ensure end is within bounds
            val safeEnd = minOf(end, tokens.size - 1)

            val sentenceTokens = tokens.subList(start, safeEnd + 1)

            // Create normalized sentence text
            val normalizedSentence = getNormalizedSentence(sentenceTokens)

            sentences.add(sentenceTokens)
        }

        return sentences
    }

    /**
     * Get sentence indices from tokens
     *
     * @param tokens List of Token objects
     * @return List of sentence index maps
     */
    private fun getSentenceIndices(tokens: List<Token>): List<Map<String, Any>> {
        val sentenceIndices = mutableListOf<Map<String, Any>>()

        var previousEnd = 0

        // Find sentence end markers: ending particles followed by punctuation
        val sentenceEndIndex = extractChunks(
            { t1: Token, t2: Token -> isEndingPartNounPunct(t1, t2) },
            tokens,
            0,
            previousEnd
        )

        // Find clause boundaries followed by punctuation
        val clauseBoundaryIndex = extractChunks(
            { t1: Token, t2: Token -> isClauseBoundaryNounPunct(t1, t2) },
            tokens,
            0,
            previousEnd
        )

        // Find verbs followed by punctuation
        val verbIndex = extractChunks(
            { t1: Token, t2: Token -> isVerbNounPunct(t1, t2) },
            tokens,
            0,
            previousEnd
        )

        // Join sentences without verbs
        val verbJoinIndex = joinNoVerbSentences(verbIndex, tokens)

        return verbJoinIndex
    }

    /**
     * Get normalized sentence text from tokens
     *
     * @param tokens List of Token objects
     * @return Normalized sentence text
     */
    private fun getNormalizedSentence(tokens: List<Token>): String {
        val sentence = StringBuilder()

        for (token in tokens) {
            sentence.append(token.text)
            sentence.append(" ")
        }

        var normalizedSentence = sentence.toString().trim()

        // Apply normalization patterns
        // Remove extra spaces, normalize punctuation, etc.
        normalizedSentence = normalizedSentence.replace(Regex("\\s+"), " ")
        normalizedSentence = normalizedSentence.replace(Regex("\\s+\\s"), " ")

        return normalizedSentence
    }

    /**
     * Extract chunks based on test condition
     *
     * @param test Function to test condition
     * @param subtokens List of tokens
     * @param start Starting index
     * @param previousEnd Previous ending index
     * @return List of chunk index maps
     */
    private fun extractChunks(
        test: (Token, Token) -> Boolean,
        subtokens: List<Token>,
        start: Int,
        previousEnd: Int
    ): List<Map<String, Any>> {
        val chunks = mutableListOf<Map<String, Any>>()
        var currentPreviousEnd = previousEnd

        for ((n, token) in subtokens.withIndex()) {
            // Skip first iteration to avoid ArrayIndexOutOfBoundsException
            if (n == 0) continue

            if (test(subtokens[n - 1], token)) {
                chunks.add(
                    mapOf(
                        "start" to currentPreviousEnd,
                        "end" to start + n,
                        "len" to start + n - currentPreviousEnd
                    )
                )
                currentPreviousEnd = start + n + 1
            }
        }

        // Add all subtokens if no chunk was produced
        if (chunks.isEmpty()) {
            chunks.add(
                mapOf(
                    "start" to start,
                    "end" to start + subtokens.size - 1,
                    "len" to subtokens.size
                )
            )
        }

        return chunks
    }

    /**
     * Join sentences without verbs
     *
     * @param sentenceIndices Current sentence indices
     * @param tokens All tokens
     * @return Updated sentence indices without verb chunks
     */
    private fun joinNoVerbSentences(
        sentenceIndices: List<Map<String, Any>>,
        tokens: List<Token>
    ): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        val threshold = 4

        var i = 0
        while (i < sentenceIndices.size) {
            val start = sentenceIndices[i]["start"] as Int
            val end = sentenceIndices[i]["end"] as Int
            val length = sentenceIndices[i]["len"] as Int

            var noVerb = true

            // Check if any verb tokens in the sentence
            for (token in tokens.subList(start, end + 1)) {
                if (token.pos == "VERB" && !hasLastSyllable(token, DAGDRA)) {
                    noVerb = false
                    break
                }
            }

            if (length > threshold) {
                result.add(sentenceIndices[i])
                i += 1
                continue
            }

            result.add(sentenceIndices[i])
            i += 1
        }

        return result
    }

    /**
     * Check if token has last syllable matching pattern
     *
     * @param token Token to check
     * @param patterns List of patterns to match against
     * @return True if last syllable matches any pattern
     */
    private fun hasLastSyllable(token: Token, patterns: List<String>): Boolean {
        if (token.syls.isEmpty()) {
            return false
        }

        val lastSyl = token.syls.last().joinToString("")

        return patterns.any { pattern ->
            lastSyl.contains(pattern)
        }
    }

    /**
     * Test if first token is ending particle and second is noun
     */
    private fun isEndingPartNounPunct(token1: Token, token2: Token): Boolean {
        return isEndingParticle(token1) && token2.chunkType == "PUNCT"
    }

    /**
     * Test if first token has clause boundary and second is noun
     */
    private fun isClauseBoundaryNounPunct(token1: Token, token2: Token): Boolean {
        return hasLastSyllable(token1, CLAUSE_BOUNDARIES) && token2.chunkType == "PUNCT"
    }

    /**
     * Test if first token is verb and second is punctuation
     */
    private fun isVerbNounPunct(token1: Token, token2: Token): Boolean {
        return token1.pos == "VERB" && !hasLastSyllable(token1, DAGDRA) && token2.chunkType == "PUNCT"
    }

    /**
     * Check if token is an ending particle
     */
    private fun isEndingParticle(token: Token): Boolean {
        return token.pos == "PART" && hasLastSyllable(token, ENDING_PARTICLES)
    }

    /**
     * Test method for sentence tokenizer
     */
    fun testSentenceTokenizer(): Boolean {
        val tokens = listOf(
            Token().apply {
                text = "ཀྱམ་"
                pos = "NOUN"
                chunkType = "TEXT"
                sylsIdx = listOf(listOf(0, 1, 2))
            },
            Token().apply {
                text = "ངོ་"
                pos = "NOUN"
                chunkType = "TEXT"
                sylsIdx = listOf(listOf(0, 1, 2))
            },
            Token().apply {
                text = "\u0F66"
                pos = "PUNCT"
                chunkType = "PUNCT"
                sylsIdx = listOf(listOf(0))
            },
            Token().apply {
                text = "ཀྱམ"
                pos = "NOUN"
                chunkType = "TEXT"
                sylsIdx = listOf(listOf(0, 1, 2))
            }
        )

        val sentences = tokenize(tokens)

        // Should create at least 1 sentence with proper segmentation
        return sentences.isNotEmpty() &&
               sentences.all { sentence -> sentence.isNotEmpty() }
    }
}
