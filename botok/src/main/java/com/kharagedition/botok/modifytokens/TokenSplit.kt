package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.tokenizers.Token

/**
 * Port of botok/modifytokens/tokensplit.py — TokenSplit
 *
 * Splits a token into two at a given index.
 * The affected attributes are:
 *   - token.text : the string is split at the index
 *   - token.char_types : redistributed
 *   - token.start : second token only
 *   - token.len : length of new content
 *   - token.syls_idx : syls are redistributed and split if necessary
 *   - token.syls_start_end : redistributed and split if necessary
 */
class TokenSplit(
    private val token: Token,
    private val splitIdx: Int,
    private val tokenChanges: String? = null
) {

    var first: Token? = null
        private set
    var second: Token? = null
        private set
    private var idx = splitIdx

    /**
     * Split the token into two.
     * @param mode can be "syl" or "char" to split on syllable or character index
     */
    fun split(mode: String = "char"): Pair<Token, Token> {
        if (mode != "char" && mode != "syl") {
            throw SyntaxError("splitting mode should either be 'syl' or 'char'.")
        }

        // In syllable-mode, if there is only one syllable, return the word without splitting
        if (mode == "syl" && token.syls.size == 1) {
            return Pair(token, Token()) // Return token and empty token as placeholder
        }

        splitOnIdx(mode)
        replaceAttrs()

        return Pair(first!!, second!!)
    }

    private fun replaceAttrs() {
        if (tokenChanges != null) {
            // Placeholder for CQL-based attribute replacement (Phase 7)
            val tokens = listOfNotNull(first, second)
            // replaceTokenAttributes(tokens, tokenChanges)
            if (tokens.size >= 2) {
                first = tokens[0]
                second = tokens[1]
            }
        }
    }

    private fun splitOnIdx(mode: String) {
        first = deepCopyToken(token)
        second = deepCopyToken(token)

        if (mode == "syl") {
            val sylStartEnd = token.sylsStartEnd
            if (sylStartEnd != null && sylStartEnd.isNotEmpty()) {
                idx = sylStartEnd[idx - 1]["end"]!!
            }
        }

        splitContents()
        splitIndices()
        splitSylsIdx()
        splitSylsStartEnd(mode)
        splitCharTypes()
        splitAffixation()
    }

    private fun splitContents() {
        val text = first!!.text
        first!!.text = text.substring(0, idx)
        second!!.text = text.substring(idx)
    }

    private fun splitCharTypes() {
        val charTypes = first!!.charTypes
        first!!.charTypes = charTypes.take(idx)
        second!!.charTypes = charTypes.drop(idx)
    }

    private fun splitIndices() {
        first!!.len = first!!.text.length
        second!!.len = second!!.text.length
        second!!.start = second!!.start + idx
    }

    private fun splitSylsStartEnd(mode: String) {
        val sylsStartEnd = token.sylsStartEnd
        if (sylsStartEnd.isNullOrEmpty()) {
            return
        }

        var toSplitIdx = 0
        for ((num, s) in sylsStartEnd.withIndex()) {
            if (idx in s["start"]!!..s["end"]!!) {
                toSplitIdx = num
                break
            }
        }

        val start = sylsStartEnd.take(toSplitIdx).toMutableList()
        val end = sylsStartEnd.drop(toSplitIdx + 1).toMutableList()
        val toSplit = sylsStartEnd[toSplitIdx]

        if (mode == "char") {
            start.add(mapOf("start" to toSplit["start"]!!, "end" to idx))
            end.add(mapOf("start" to idx, "end" to toSplit["end"]!!))
        } else if (mode == "syl") {
            start.add(toSplit)
        }

        if (mode == "char") {
            start.add(mapOf("start" to toSplit["start"]!!, "end" to idx))
            end.add(mapOf("start" to idx, "end" to toSplit["end"]!!))
        } else if (mode == "syl") {
            start.add(toSplit)
        }

        first!!.sylsStartEnd = start
        second!!.sylsStartEnd = end
    }

    private fun splitSylsIdx() {
        val syls = first!!.sylsIdx
        first!!.sylsIdx = mutableListOf()
        second!!.sylsIdx = mutableListOf()

        if (syls != null) {
            for (syl in syls) {
                if (syl.last() < idx) {
                    (first!!.sylsIdx as MutableList).add(syl)
                } else {
                    // Separate the syl in two
                    val part1 = mutableListOf<Int>()
                    val part2 = mutableListOf<Int>()
                    for (i in syl) {
                        if (i < idx) {
                            part1.add(i)
                        } else {
                            part2.add(i - idx)
                        }
                    }

                    // Add them if non-empty
                    if (part1.isNotEmpty()) {
                        (first!!.sylsIdx as MutableList).add(part1)
                    }
                    if (part2.isNotEmpty()) {
                        (second!!.sylsIdx as MutableList).add(part2)
                    }
                }
            }
        }
    }

    private fun splitAffixation() {
        if (token.affixation.isNotEmpty()) {
            val firstAffixation = first!!.affixation.toMutableMap()
            val secondAffixation = second!!.affixation.toMutableMap()

            firstAffixation.remove("len")
            firstAffixation.remove("type")
            secondAffixation.remove("aa")

            first!!.affixation = firstAffixation
            second!!.affixation = secondAffixation
        }
    }

    private fun deepCopyToken(token: Token): Token {
        val copy = Token()
        copy.text = token.text
        copy.charTypes = token.charTypes.toList()
        copy.hasMergedDagdra = token.hasMergedDagdra
        copy.lemma = token.lemma
        copy.sense = token.sense
        copy.chunkType = token.chunkType
        copy.start = token.start
        copy.len = token.len
        copy.sylsIdx = token.sylsIdx?.map { it.toList() }
        copy.sylsStartEnd = token.sylsStartEnd?.map { it.toMap() }
        copy.pos = token.pos
        copy.affixation = token.affixation.toMap()
        copy.senses = token.senses?.map { it.toMap() }?.toMutableList()
        copy.affix = token.affix
        copy.affixHost = token.affixHost
        copy.formFreq = token.formFreq
        copy.freq = token.freq
        copy.skrt = token.skrt
        copy.customData.putAll(token.customData)
        return copy
    }
}

class SyntaxError(message: String) : Exception(message)
