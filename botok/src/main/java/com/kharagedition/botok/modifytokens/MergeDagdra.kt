package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.DAGDRA
import com.kharagedition.botok.TSEK
import com.kharagedition.botok.tokenizers.Token

/**
 * Port of botok/modifytokens/mergedagdra.py — MergeDagdra
 *
 * Merges pa/po/ba/bo tokens with the preceding token in a token list.
 */
class MergeDagdra {

    /**
     * Merges tokens containing either pa/po/ba/bo.
     * @param tokens list of Token objects (modified in place)
     */
    fun merge(tokens: MutableList<Token>) {
        when (tokens.size) {
            0, 1 -> {
                // No merging needed
            }
            2 -> {
                val token0 = tokens[0]
                val token1 = tokens[1]

                val cleanWord = if (!token1.textCleaned.endsWith(TSEK)) {
                    token1.textCleaned + TSEK
                } else {
                    token1.textCleaned
                }

                if (cleanWord in DAGDRA) {
                    // Split token containing the affixed particle
                    val merged = mergeWithPreviousToken(token0, token1)
                    tokens.removeAt(1)
                    tokens[0] = merged
                }
            }
            else -> {
                var t = 0
                while (t <= tokens.size - 1) {
                    if (t + 1 > tokens.size - 1) {
                        break
                    }

                    val token0 = tokens[t]
                    val token1 = tokens[t + 1]

                    val cleanWord = if (!token1.textCleaned.endsWith(TSEK)) {
                        token1.textCleaned + TSEK
                    } else {
                        token1.textCleaned
                    }

                    if (token0.chunkType == "TEXT" &&
                        token1.chunkType == "TEXT" &&
                        cleanWord in DAGDRA) {
                        // Split token containing the affixed particle
                        val merged = mergeWithPreviousToken(token0, token1)

                        // Replace the original two tokens with the merged one
                        tokens[t] = merged
                        tokens.removeAt(t + 1)
                    }
                    t += 1
                }
            }
        }
    }

    private fun mergeWithPreviousToken(token0: Token, token1: Token): Token {
        val merged = TokenMerge(token0, token1).merge()
        merged.hasMergedDagdra = true
        merged.lemma = merged.textCleaned
        return merged
    }
}
