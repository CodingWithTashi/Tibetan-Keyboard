package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.config.Config
import org.junit.BeforeClass
import org.junit.Test

/**
 * Debug test to understand what's happening with "ཀཀ"
 */
class DebugWordTokenizerTest {

    companion object {
        private lateinit var wt: WordTokenizer

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val assetsPath = System.getProperty("user.dir") + "/src/main/assets/botok/general"
            val config = Config(assetsPath)
            wt = WordTokenizer(config)
        }
    }

    @Test
    fun debugWhatHappensWithDoubleKa() {
        val inputStr = "ཀཀ"
        println("Testing: $inputStr")
        val tokens = wt.tokenize(inputStr)

        println("Number of tokens: ${tokens.size}")
        for ((index, token) in tokens.withIndex()) {
            println("Token $index:")
            println("  text: ${token.text}")
            println("  pos: ${token.pos}")
            println("  chunkType: ${token.chunkType}")
            println("  senses: ${token.senses}")
            println("  lemma: ${token.lemma}")
        }

        // Check if any token has NON_WORD pos
        val nonWordByPos = tokens.find { it.pos == "NON_WORD" }
        println("Found NON_WORD by pos: ${nonWordByPos != null}")

        // Check if any token has NON_WORD in senses
        val nonWordBySenses = tokens.find {
            token -> token.senses?.any {
                when (it) {
                    is Map<*, *> -> it["pos"] == "NON_WORD"
                    else -> false
                }
            } == true
        }
        println("Found NON_WORD by senses: ${nonWordBySenses != null}")
    }
}
