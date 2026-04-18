package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.tokenizers.Token

/**
 * Port of botok/modifytokens/tokenmerge.py — TokenMerge
 *
 * Merges two tokens into one, combining their texts, indices, and syllable information.
 */
class TokenMerge(
    private val token1: Token,
    private val token2: Token,
    private val tokenChanges: String? = null
) {

    private val merged: Token = deepCopyToken(token1)

    fun merge(): Token {
        mergeAttrs()
        replaceAttrs()
        return merged
    }

    private fun replaceAttrs() {
        // Replaces attributes based on CQL query (simplified for Phase 5)
        // Full implementation in Phase 7 with CQL parser
        if (tokenChanges != null) {
            // Placeholder for CQL-based attribute replacement
        }
    }

    private fun mergeAttrs() {
        mergeTexts()
        mergeIndices()
        mergeSylsIdx()
        mergeSylsStartEnd()
        deleteLemma()
    }

    private fun mergeTexts() {
        merged.text += token2.text
    }

    private fun mergeIndices() {
        merged.len = (merged.len ?: 0) + (token2.len ?: 0)
    }

    private fun mergeSylsStartEnd() {
        if (merged.sylsStartEnd.isNullOrEmpty() ||
            token1.sylsStartEnd.isNullOrEmpty() ||
            token2.sylsStartEnd.isNullOrEmpty()) {
            return
        }

        val mergedStartEnd = merged.sylsStartEnd!!.toMutableList()
        val t1StartEnd = token1.sylsStartEnd!!
        val t2StartEnd = token2.sylsStartEnd!!

        // token1 is a host syllable and token2 its affixed syllable
        if (token1.affixHost && !token1.affix &&
            !token2.affixHost && token2.affix) {
            // Merge the last syllable of token1 with first of token2
            mergedStartEnd[mergedStartEnd.size - 1] = mapOf(
                "start" to t1StartEnd.last()["start"]!!,
                "end" to t2StartEnd[0]["end"]!!
            )
            (mergedStartEnd as MutableList).addAll(t2StartEnd.drop(1))
        } else {
            (mergedStartEnd as MutableList).addAll(t2StartEnd)
        }

        merged.sylsStartEnd = mergedStartEnd
    }

    private fun mergeSylsIdx() {
        if (merged.sylsIdx == null) {
            merged.sylsIdx = mutableListOf()
        }
        if (merged.syls.isNullOrEmpty()) {
            // syls is a computed property, so we don't need to set it directly
        }

        val mergedSylsIdx = merged.sylsIdx!!.toMutableList()
        var firstSyl = true

        if (token2.sylsIdx != null) {
            for (syl in token2.sylsIdx!!) {
                if (syl.isNotEmpty()) {
                    val token1Len = token1.len ?: 0
                    val newSyl = syl.map { it + token1Len }

                    // token1 is a host syllable and token2 its affixed syllable
                    if (firstSyl &&
                        token1.affixHost && !token1.affix &&
                        !token2.affixHost && token2.affix) {
                        // Merge the last syllable
                        mergedSylsIdx[mergedSylsIdx.size - 1] =
                            mergedSylsIdx.last().plus(newSyl)
                        merged.affix = true
                        firstSyl = false
                    } else {
                        mergedSylsIdx.add(newSyl)
                    }
                }
            }
        }

        merged.sylsIdx = mergedSylsIdx
    }

    private fun deleteLemma() {
        // Simply deletes any lemma in merged since the lemma of the merged token can't be guessed
        if (token1.lemma.isNotEmpty()) {
            merged.lemma = ""
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
