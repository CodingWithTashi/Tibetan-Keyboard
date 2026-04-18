package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.chunks.Chunks

/**
 * Port of botok/tokenizers/chunktokenizer.py
 *
 * Chunk-based tokenizer that wraps the TokChunks functionality
 * Performs tokenization at the chunk level
 */
class ChunkTokenizer(private val string: String) {

    /**
     * Tokenize the input string using chunk processing
     *
     * @return List of token objects
     */
    fun tokenize(): List<com.kharagedition.botok.tokenizers.Token> {
        val chunks = Chunks(string)

        // Make chunks using the Chunks class
        val tokenChunks = chunks.makeChunks()

        // Convert to readable format
        val tokens = tokenChunks.map { chunk ->
            com.kharagedition.botok.tokenizers.Token().apply {
                this.text = string.substring(chunk.first, chunk.first + chunk.second)
                this.start = chunk.first
                this.len = chunk.second
                this.chunkType = chunk.third.toString()
            }
        }

        return tokens
    }

    /**
     * Test method for chunk tokenizer
     */
    fun testChunkTokenizer(): Boolean {
        val input = "Hello World"

        val tokenizer = ChunkTokenizer(input)
        val tokens = tokenizer.tokenize()

        return tokens.size == 2 &&
               tokens[0].text == "Hello" &&
               tokens[1].text == "World"
    }
}
