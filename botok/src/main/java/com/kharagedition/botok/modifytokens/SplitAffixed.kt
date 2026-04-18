package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.tokenizers.Token

/**
 * Port of botok/modifytokens/splitaffixed.py — split_affixed
 *
 * Splits in place the tokens containing affixed particles.
 * Tokens have to be Token objects produced by Tokenize.
 */
object SplitAffixed {

    /**
     * Splits tokens containing affixed particles in place.
     * @param tokens list of Token objects (modified in place)
     */
    fun splitAffixed(tokens: MutableList<Token>) {
        var t = 0
        while (t < tokens.size) {
            val token = tokens[t]

            // Check that splitting is possible (affixation attribute exists)
            // and that there is no meaning that has "affixed: False".
            // ie, check that the inflected form can't be the affixed form of a word
            // and the unaffixed form of another word
            val hasAffixation = token.affixation.isNotEmpty()
            val hasNonAffixedSense = token.senses?.any { sense ->
                "affixed" in sense && sense["affixed"] == false
            } ?: false

            if (hasAffixation && !hasNonAffixedSense) {
                // Split token containing the affixed particle
                val lenToRemove = token.affixation["len"] as? Int ?: 0
                val splitIdx = token.sylsIdx?.last()?.takeLast(lenToRemove)?.first() ?: 0

                val ts = TokenSplit(token, splitIdx, null)
                val (token1, token2) = ts.split()

                // Manually set the properties for token1 (affix host)
                token1.affixHost = true
                token1.affix = false

                // Manually set the properties for token2 (affix)
                token2.pos = "PART"
                token2.affix = true
                token2.affixHost = false
                token2.skrt = false
                token2.freq = null

                if (token2.senses == null) {
                    token2.senses = mutableListOf()
                }

                // Replace the original token with the two new ones
                tokens[t] = token1
                tokens.add(t + 1, token2)

                t += 1 // Increment once more to account for the newly split token
            }
            t += 1
        }
    }
}
