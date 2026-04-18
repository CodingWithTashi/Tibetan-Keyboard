package com.kharagedition.botok

import com.kharagedition.botok.chunks.TokChunks
import com.kharagedition.botok.config.Config
import com.kharagedition.botok.textunits.BoSyl
import com.kharagedition.botok.textunits.BoString
import com.kharagedition.botok.tokenizers.Tokenize
import com.kharagedition.botok.tokenizers.WordTokenizer
import com.kharagedition.botok.tries.Trie

/**
 * Debug test for spacesAsPunct issue
 */
fun main() {
    println("=== Testing spacesAsPunct ===")

    val input = "བ ཀྲ་ཤིས་"
    println("Input: '$input'")
    println()

    // Create chunks with spaceAsPunct=true
    val preproc = TokChunks(input, spaceAsPunct = true)
    preproc.serveSylsToTrie()

    println("Chunks:")
    for ((i, chunk) in preproc.chunks!!.withIndex()) {
        val (syl, triple) = chunk
        val (marker, start, length) = triple
        val text = preproc.boString.string.substring(start, start + length)
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
        println("  [$i] marker=$markerName, start=$start, len=$length, text='$text', syl=$syl")
    }
    println()

    // Create tokenizer
    val assetsPath = System.getProperty("user.dir") + "/src/main/assets/botok/general"
    val config = Config(assetsPath)
    val trie = Trie(BoSyl(), "general", config.dictionary, config.adjustments)
    val tok = Tokenize(trie)

    // Tokenize
    val tokens = tok.tokenize(preproc)

    println("Tokens:")
    for ((i, token) in tokens.withIndex()) {
        println("  [$i] text='${token.text}', chunkType='${token.chunkType}', len=${token.len}")
    }
    println()

    // Now test with WordTokenizer
    println("=== Testing with WordTokenizer ===")
    val wt = WordTokenizer()
    val wtTokens = wt.tokenize(input, spacesAsPunct = true)

    println("WordTokenizer Tokens:")
    for ((i, token) in wtTokens.withIndex()) {
        println("  [$i] text='${token.text}', chunkType='${token.chunkType}', len=${token.len}")
    }
}
