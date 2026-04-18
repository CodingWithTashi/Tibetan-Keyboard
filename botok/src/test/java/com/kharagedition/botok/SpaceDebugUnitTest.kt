package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.chunks.TokChunks
import com.kharagedition.botok.config.Config
import com.kharagedition.botok.textunits.BoSyl
import com.kharagedition.botok.tries.Trie
import org.junit.BeforeClass
import org.junit.Test

/**
 * Debug test for spacesAsPunct issue
 */
class SpaceDebugUnitTest {

    companion object {
        private lateinit var tok: Tokenize
        private lateinit var trie: Trie

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val assetsPath = System.getProperty("user.dir") + "/src/main/assets/botok/general"
            val config = Config(assetsPath)
            val profile = "general"

            trie = Trie(
                BoSyl(),
                profile,
                config.dictionary,
                config.adjustments
            )

            tok = Tokenize(trie)
        }
    }

    @Test
    fun debugSpaceChunks() {
        val input = "བ ཀྲ་ཤིས་"
        println("Input: '$input'")
        println()

        // Create chunks with spaceAsPunct=true
        val preproc = TokChunks(input, spaceAsPunct = true)
        preproc.serveSylsToTrie()

        println("Chunks (${preproc.chunks!!.size} total):")
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

        // Tokenize
        val tokens = tok.tokenize(preproc)

        println("Tokens (${tokens.size} total):")
        for ((i, token) in tokens.withIndex()) {
            println("  [$i] text='${token.text}', chunkType='${token.chunkType}', len=${token.len}")
        }
        println()

        // Print what we expect
        println("Expected:")
        println("  [0] text='བ'")
        println("  [1] text=' '")
        println("  [2] text='ཀྲ་ཤིས་'")
    }
}
