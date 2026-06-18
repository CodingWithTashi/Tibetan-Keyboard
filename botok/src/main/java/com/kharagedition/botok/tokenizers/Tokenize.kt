package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.CharMarkers
import com.kharagedition.botok.chunkValues
import com.kharagedition.botok.chunks.TokChunks
import com.kharagedition.botok.textunits.BoString
import com.kharagedition.botok.third_party.HasSkrtSyl
import com.kharagedition.botok.tries.Node
import com.kharagedition.botok.tries.Trie

/**
 * Port of botok/tokenizers/tokenize.py — Tokenize
 *
 * Maximum-match tokenizer using a Trie to find the longest matching word.
 * Handles OOV (out-of-vocabulary) syllables by falling back to non-word tokens.
 */
class Tokenize(val trie: Trie) {

    private var preProcessed: TokChunks? = null

    /**
     * Main tokenization method.
     * Port of Python's tokenize(pre_processed, debug=False).
     *
     * @param preProcessed TokChunks object with served syllables
     * @param debug if true, prints debug information
     * @return list of Token objects
     */
    fun tokenize(preProcessed: TokChunks, debug: Boolean = false): List<Token> {
        this.preProcessed = preProcessed
        val tokens = mutableListOf<Token>()

        var cIdx = 0
        while (cIdx < (preProcessed.chunks?.size ?: 0)) {
            var walker = cIdx
            val syls = mutableListOf<Int>()
            val maxMatch = mutableListOf<List<Int>>()
            val matchData = mutableMapOf<Int, MutableMap<String, Any?>>()
            var currentNode: Node? = null
            var foundMaxMatch = false

            while (true) {
                val curSyl = preProcessed.chunks!![walker].first

                if (curSyl != null) {
                    // CHUNK IS SYLLABLE
                    val syl = curSyl.joinToString("") { idx ->
                        preProcessed.boString.string[idx].toString()
                    }
                    currentNode = trie.walk(syl, currentNode)

                    if (currentNode != null) {
                        syls.add(walker)
                        if (currentNode.isMatch()) {
                            matchData[walker] = currentNode.data.toMutableMap()
                            maxMatch.add(syls.toList())

                            // Check if the matched is last
                            if (walker + 1 == (preProcessed.chunks?.size ?: 0)) {
                                foundMaxMatch = true
                            }
                        } else {
                            if (walker + 1 == (preProcessed.chunks?.size ?: 0)) {
                                if (maxMatch.isNotEmpty()) {
                                    foundMaxMatch = true
                                } else {
                                    // OOV syllables are turned into independent tokens
                                    cIdx = addFoundWordOrNonWord(
                                        walker, matchData, syls, tokens
                                    )
                                    break
                                }
                            }
                        }
                    } else {
                        // CAN'T CONTINUE WALKING
                        if (maxMatch.isNotEmpty()) {
                            foundMaxMatch = true
                        } else {
                            // Check if syllables is NO_POS or Non-word
                            if (syls.isNotEmpty()) {
                                cIdx = addFoundWordOrNonWord(
                                    walker, matchData, syls, tokens
                                )
                                break
                            } else {
                                // Syllable is not in the dictionary (Trie)
                                val nonWord = listOf(walker)
                                tokens.add(
                                    chunksToToken(nonWord, emptyMap(), ttype = "NON_WORD")
                                )
                                cIdx += 1
                                break
                            }
                        }
                    }
                } else {
                    // CHUNK IS NON-SYLLABLE
                    if (maxMatch.isNotEmpty()) {
                        // Non-syllable terminates the match — emit the matched token now.
                        // We cannot rely on the foundMaxMatch block below because that block
                        // is only reached from the syllable branch (curSyl != null).
                        addFoundWordOrNonWord(
                            cIdx + maxMatch.last().size - 1,
                            matchData,
                            maxMatch.last(),
                            tokens
                        )
                        cIdx = walker  // walker points at the non-syllable; process it next
                        break
                    } else if (syls.isNotEmpty()) {
                        // Check for any syllables left, which are to be turned into independent tokens
                        cIdx = addFoundWordOrNonWord(
                            walker, matchData, syls, tokens
                        )
                        if (syls.size == 1) {
                            cIdx += 1
                        }
                        break
                    } else {
                        // Non-syllables are turned into independent tokens
                        tokens.add(
                            chunksToToken(listOf(cIdx), emptyMap())
                        )
                        cIdx += 1
                        break
                    }
                }

                if (foundMaxMatch) {
                    addFoundWordOrNonWord(
                        cIdx + maxMatch.last().size - 1,
                        matchData,
                        maxMatch.last(),
                        tokens
                    )

                    // Check if the next chunk (walker) is a non-syllable
                    // If so, don't increment cIdx - we want to process it next
                    val nextChunkIsNonSyllable = (walker < (preProcessed.chunks?.size ?: 0) &&
                        preProcessed.chunks!![walker].first == null)

                    if (nextChunkIsNonSyllable) {
                        // Keep cIdx at the current position so we process the non-syllable chunk next
                        // But we need to move past the matched chunks
                        cIdx = walker
                    } else {
                        cIdx += maxMatch.last().size
                    }
                    break
                }

                walker += 1
            }
        }

        this.preProcessed = null
        return tokens
    }

    /**
     * Add a found word or non-word token to the list.
     * Port of Python's add_found_word_or_non_word().
     *
     * @param cIdx current chunk index
     * @param matchData map of matched positions to data
     * @param syls list of syllable indices
     * @param tokens list to add the token to
     * @return updated cIdx for next iteration
     */
    private fun addFoundWordOrNonWord(
        cIdx: Int,
        matchData: Map<Int, MutableMap<String, Any?>>,
        syls: List<Int>,
        tokens: MutableList<Token>,
        hasDecremented: Boolean = false
    ): Int {
        var newCIdx = cIdx

        // There is a match
        if (cIdx in matchData) {
            val data = matchData[cIdx]!!
            val ttype = if (
                "senses" !in data ||
                data["senses"]!!.let { it is List<*> && (it as List<*>).none { it is Map<*, *> && "pos" in it } }
            ) {
                "NO_POS"
            } else {
                null
            }
            tokens.add(chunksToToken(syls, data, ttype))
        } else if (matchData.values.any { it.isNotEmpty() }) {
            val nonMaxIdx = matchData.keys.maxOrNull()!!
            val nonMaxSyls = mutableListOf<Int>()
            for (syl in syls) {
                if (syl <= nonMaxIdx) {
                    nonMaxSyls.add(syl)
                }
            }
            val data = matchData[nonMaxIdx]!!
            val ttype = if (
                "senses" !in data ||
                data["senses"]!!.let { it is List<*> && (it as List<*>).none { it is Map<*, *> && "pos" in it } }
            ) {
                "NO_POS"
            } else {
                null
            }
            tokens.add(chunksToToken(nonMaxSyls, data, ttype))
            newCIdx = nonMaxIdx
        } else {
            // Add first syl in syls as NO_POS (partial trie match, not a complete word)
            tokens.add(chunksToToken(listOf(syls[0]), emptyMap(), ttype = "NO_POS"))

            if (syls.size == 1) {
                // Single syllable: advance past it so the outer loop doesn't spin
                newCIdx = syls[0] + 1
            } else {
                // Multiple syllables tried, none formed a complete word.
                // Backtrack: emit syls[0] and retry from syls[1].
                // Python: if syls and syls[1:]: c_idx -= len(syls[1:]) - 1
                newCIdx -= syls.size - 2

                val currentChunkIsNonSyllable = (newCIdx < (preProcessed?.chunks?.size ?: 0) &&
                    preProcessed?.chunks?.get(newCIdx)?.first == null)

                if (hasDecremented || currentChunkIsNonSyllable) {
                    newCIdx -= 1
                }
            }
        }

        return newCIdx
    }

    /**
     * Convert chunk indices to a Token object.
     * Port of Python's chunks_to_token().
     *
     * @param syls list of syllable chunk indices
     * @param data data from trie match
     * @param ttype optional token type override
     * @return Token object
     */
    private fun chunksToToken(
        syls: List<Int>,
        data: Map<String, Any?>,
        ttype: String? = null
    ): Token {
        val chunks = preProcessed?.chunks ?: throw IllegalStateException("preProcessed is null")

        return when (syls.size) {
            1 -> {
                val chunk = chunks[syls[0]]
                val tokenSyls = listOf(chunk.first)
                val tokenType = chunk.second.first
                val tokenStart = chunk.second.second
                val tokenLength = chunk.second.third
                val sylStartEnd = listOf(
                    mapOf("start" to chunk.second.second, "end" to chunk.second.second + chunk.second.third)
                )

                val processedData = if (ttype != null) {
                    val mutableData = data.toMutableMap()
                    if ("senses" !in mutableData) {
                        mutableData["senses"] = mutableListOf(mapOf("pos" to ttype))
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val sensesList = mutableData["senses"] as MutableList<Map<String, Any?>>
                        val mutableSensesList = mutableListOf<MutableMap<String, Any?>>()
                        for (m in sensesList) {
                            val mutableM = m.toMutableMap()
                            if ("pos" !in mutableM) {
                                mutableM["pos"] = ttype
                            }
                            mutableSensesList.add(mutableM)
                        }
                        mutableData["senses"] = mutableSensesList
                    }
                    mutableData
                } else {
                    data.toMutableMap()
                }

                createToken(
                    tokenType, tokenStart, tokenLength, tokenSyls, sylStartEnd, processedData
                )
            }

            else -> {
                val tokenSyls = syls.map { chunks[it].first }
                val tokenType = chunks[syls.last()].second.first
                val tokenStart = chunks[syls.first()].second.second
                var tokenLength = 0
                val sylStartEnd = mutableListOf<Map<String, Int>>()

                for (i in syls) {
                    val chunk = chunks[i]
                    tokenLength += chunk.second.third
                    sylStartEnd.add(mapOf("start" to chunk.second.second, "end" to chunk.second.second + chunk.second.third))
                }

                val processedData = if (ttype != null) {
                    val mutableData = data.toMutableMap()
                    if ("senses" !in mutableData) {
                        mutableData["senses"] = mutableListOf(mapOf("pos" to ttype))
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val sensesList = mutableData["senses"] as MutableList<Map<String, Any?>>
                        val mutableSensesList = mutableListOf<MutableMap<String, Any?>>()
                        for (m in sensesList) {
                            val mutableM = m.toMutableMap()
                            if ("pos" !in mutableM) {
                                mutableM["pos"] = ttype
                            }
                            mutableSensesList.add(mutableM)
                        }
                        mutableData["senses"] = mutableSensesList
                    }
                    mutableData
                } else {
                    data.toMutableMap()
                }

                createToken(
                    tokenType, tokenStart, tokenLength, tokenSyls, sylStartEnd, processedData
                )
            }
        }
    }

    /**
     * Create a Token from chunk information.
     * Port of Python's create_token().
     *
     * @param ttype token type marker
     * @param start start index in input string
     * @param length length of the substring
     * @param syls syllable representation from TokChunks
     * @param sylStartEnd start/end indices for each syllable
     * @param data data from trie match
     * @return Token object
     */
    private fun createToken(
        ttype: Int,
        start: Int,
        length: Int,
        syls: List<List<Int>?>,
        sylStartEnd: List<Map<String, Int>>,
        data: Map<String, Any?>
    ): Token {
        val token = Token()

        // Get the raw text from chunk boundaries
        var rawText = preProcessed!!.boString.string.substring(start, start + length)

        // Trim trailing spaces that may have been merged by mergeSkippablePunct
        // But only if this is not a pure space/punctuation token (i.e., has syllables)
        if (syls != listOf(null) && syls.any { it != null && it.isNotEmpty() }) {
            rawText = rawText.trimEnd()
        }

        token.text = rawText
        token.chunkType = chunkValues[ttype]
        token.start = start
        token.len = rawText.length

        if (syls != listOf(null)) {
            token.sylsIdx = syls.map { syl ->
                syl?.map { it - start } ?: emptyList()
            }
            token.sylsStartEnd = sylStartEnd.map { map ->
                mapOf("start" to (map["start"]!! - start), "end" to (map["end"]!! - start))
            }
        }

        val charGroups = preProcessed!!.boString.exportGroups(start, rawText.length, forSubstring = true)
        token.charTypes = charGroups.keys.sorted().map { idx ->
            CharMarkers.valueToName[charGroups[idx]] ?: "UNKNOWN"
        }

        // Copy data to token
        for ((key, value) in data) {
            when (key) {
                "affixation" -> token.affixation = value as? Map<String, Any?> ?: emptyMap()
                "senses" -> {
                    @Suppress("UNCHECKED_CAST")
                    token.senses = value as? MutableList<Map<String, Any?>> ?: mutableListOf()
                }
                "form_freq" -> token.formFreq = value as? Int
                "skrt" -> token.skrt = value as? Boolean ?: false
                "affix_host" -> token.affixHost = value as? Boolean ?: false
                else -> {
                    // Store in custom data
                    if (key == "_") {
                        @Suppress("UNCHECKED_CAST")
                        token.customData.putAll(value as? Map<String, Any?> ?: emptyMap())
                    }
                }
            }
        }

        // Extract POS, lemma, sense, and freq from senses
        // Prefer the sense with the highest freq if multiple exist
        if (token.senses != null && token.senses!!.isNotEmpty()) {
            // Find the sense with the highest freq (if any have freq)
            val bestSense = token.senses!!.maxByOrNull { sense ->
                (sense["freq"] as? Int) ?: 0
            } ?: token.senses!![0]

            if (token.pos.isEmpty() && "pos" in bestSense) {
                token.pos = bestSense["pos"] as? String ?: ""
            }
            if (token.lemma.isEmpty() && "lemma" in bestSense) {
                token.lemma = bestSense["lemma"] as? String ?: ""
            }
            if (token.sense.isEmpty() && "sense" in bestSense) {
                token.sense = bestSense["sense"] as? String ?: ""
            }
            if (token.freq == null && "freq" in bestSense) {
                token.freq = bestSense["freq"] as? Int
            }
        }

        // Check if Sanskrit
        if (!token.skrt) {
            token.skrt = isSanskrit(charGroups, token.text)
        }

        return token
    }

    /**
     * Check if a word is Sanskrit.
     * Port of Python's is_sanskrit().
     *
     * @param charGroups character groups from BoString
     * @param word the word text
     * @return true if Sanskrit
     */
    private fun isSanskrit(charGroups: Map<Int, Int>, word: String): Boolean {
        return hasSkrtChar(charGroups) || HasSkrtSyl.hasSkrtSyl(word)
    }

    /**
     * Check if character groups contain Sanskrit markers.
     * Port of Python's _has_skrt_char().
     */
    private fun hasSkrtChar(charGroups: Map<Int, Int>): Boolean {
        return charGroups.values.any {
            it == CharMarkers.SKRT_VOW ||
            it == CharMarkers.SKRT_CONS ||
            it == CharMarkers.SKRT_SUB_CONS
        }
    }

    /**
     * Debug output helper.
     * Port of Python's debug().
     */
    private fun debug(debug: Boolean, toPrint: String) {
        if (debug) {
            println(toPrint)
        }
    }
}
