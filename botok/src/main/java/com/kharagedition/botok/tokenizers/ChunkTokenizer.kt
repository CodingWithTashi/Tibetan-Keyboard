package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.chunkValues
import com.kharagedition.botok.chunks.Chunks
import com.kharagedition.botok.textunits.CharCategories
import java.io.File

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
            val startIdx = chunk.second
            val length = chunk.third
            val endIdx = minOf(startIdx + length, string.length)

            com.kharagedition.botok.tokenizers.Token().apply {
                this.text = string.substring(startIdx, endIdx)
                this.start = startIdx
                this.len = length
                this.chunkType = getChunkTypeName(chunk.first)
            }
        }

        return tokens
    }

    private fun getChunkTypeName(marker: Int): String = chunkValues[marker] ?: "UNKNOWN"

    /**
     * Test method for chunk tokenizer
     */
    fun testChunkTokenizer(): Boolean {
        val csvFile = File("src/main/assets/botok/resources/bo_uni_table.csv")
        if (csvFile.exists()) CharCategories.init(csvFile.readLines(Charsets.UTF_8))

        val input = "Hello World"
        val tokenizer = ChunkTokenizer(input)
        val tokens = tokenizer.tokenize()

        // Should tokenize "Hello World" into words
        return tokens.isNotEmpty() &&
               tokens.any { it.text.contains("Hello") } &&
               tokens.any { it.text.contains("World") }
    }
}
