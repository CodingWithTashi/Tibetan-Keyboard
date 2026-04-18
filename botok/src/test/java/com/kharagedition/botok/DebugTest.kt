package com.kharagedition.botok

import com.kharagedition.botok.chunks.TokChunks

/**
 * Quick debug test to see what chunks are created
 */
fun main() {
    val input = "བཀྲ་ཤིས་ abc བདེ་"
    println("Input: '$input'")
    println("Input length: ${input.length}")
    println("Input bytes: ${input.toByteArray().size}")
    println()

    val preproc = TokChunks(input)
    val chunks = preproc.serveSylsToTrie()

    println("Total chunks: ${chunks.size}")
    println()

    for ((i, chunk) in chunks.withIndex()) {
        val (syl, triple) = chunk
        val (marker, start, length) = triple
        val text = input.substring(start, start + length)
        val markerName = when(marker) {
            1 -> "BO"
            2 -> "PUNCT"
            3 -> "SYM"
            4 -> "NUM"
            5 -> "TEXT"
            6 -> "CJK"
            7 -> "LATIN"
            8 -> "OTHER"
            else -> "UNKNOWN"
        }

        println("Chunk $i:")
        println("  Marker: $marker ($markerName)")
        println("  Start: $start")
        println("  Length: $length")
        println("  Text: '$text'")
        println("  Syl indices: $syl")
        println()
    }
}
