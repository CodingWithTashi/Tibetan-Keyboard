package com.kharagedition.botok.tokenizers

/**
 * Port of botok/tokenizers/paragraphtokenizer.py
 *
 * Paragraph tokenizer for Tibetan text
 * Groups sentences into paragraphs based on word count thresholds
 */
class ParagraphTokenizer(private val threshold: Int = DEFAULT_THRESHOLD) {

    companion object {
        private const val DEFAULT_THRESHOLD = 70
        private const val PARAGRAPH_MAX = 150
    }

    /**
     * Tokenize input text into paragraphs
     *
     * @param tokens List of Token objects to segment
     * @return List of paragraphs, each as a list of tokens
     */
    fun tokenize(tokens: List<com.kharagedition.botok.tokenizers.Token>): List<List<Token>> {
        if (tokens.isEmpty()) return emptyList()

        val sentences = getSentenceIndices(tokens)

        val paragraphs = mutableListOf<List<Token>>()
        var i = 0

        while (i < sentences.size) {
            val sentence = sentences[i]

            if (sentence.isNotEmpty()) {
                val start = sentence.first().start
                val length: Int = sentence.fold(0) { acc, token -> acc + (token.text.length) }

                if (i > 0 && length < threshold && (start + length) < PARAGRAPH_MAX) {
                    // Join small sentences to form a paragraph
                    val lastParagraphIndex = paragraphs.size - 1
                    val lastParagraph = paragraphs[lastParagraphIndex].toMutableList()
                    lastParagraph.addAll(sentence)
                    paragraphs[lastParagraphIndex] = lastParagraph
                    i += 1
                } else {
                    // Keep as separate paragraph
                    paragraphs.add(sentence)
                    i += 1
                }
            } else {
                i += 1
            }
        }

        return paragraphs
    }

    /**
     * Get sentence indices from tokens
     * This is a simplified version - delegates to SentenceTokenizer
     *
     * @param tokens List of Token objects
     * @return List of sentences as token lists
     */
    private fun getSentenceIndices(tokens: List<Token>): List<List<Token>> {
        val sentenceTokenizer = SentenceTokenizer()
        return sentenceTokenizer.tokenize(tokens)
    }

    /**
     * Test method for paragraph tokenizer
     */
    fun testParagraphTokenizer(): Boolean {
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

        val paragraphs = tokenize(tokens)

        // No Tibetan sentence boundaries → treated as one sentence → one paragraph
        return paragraphs.isNotEmpty() &&
               paragraphs.flatMap { it }.size == tokens.size
    }
}
