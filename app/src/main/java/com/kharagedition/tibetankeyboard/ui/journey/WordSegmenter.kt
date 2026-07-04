package com.kharagedition.tibetankeyboard.ui.journey

import com.kharagedition.botok.autocomplete.SuggestionEngine

/**
 * Splits a space/shad-delimited chunk of typed Tibetan into real dictionary words, instead of
 * treating the whole chunk as one "word" (Tibetan has no spaces between words — only tsheg
 * between syllables — so an uninterrupted chunk is really a whole clause).
 *
 * Greedy longest-match over syllables using [dictionary] — the same word list the autocomplete
 * suggestion strip already loads from assets, so this needs no extra dictionary loading and no
 * heavyweight trie construction on the typing thread. It's a simplified stand-in for full Botok
 * tokenization (no POS/affix disambiguation), but grounded in the real dictionary rather than a
 * syntactic heuristic.
 */
object WordSegmenter {

    private const val TSHEK = '་'

    /** Longest compound entries in the dictionary run to about this many syllables. */
    private const val MAX_SPAN = 6

    fun segment(chunk: String, dictionary: SuggestionEngine?): List<String> {
        val syllables = chunk.split(TSHEK).filter { it.isNotEmpty() }
        if (syllables.isEmpty()) return emptyList()
        if (dictionary == null || !dictionary.isReady) return listOf(chunk)

        val words = mutableListOf<String>()
        var i = 0
        while (i < syllables.size) {
            val maxLen = minOf(MAX_SPAN, syllables.size - i)
            var matchLen = 1
            for (len in maxLen downTo 2) {
                val candidate = syllables.subList(i, i + len).joinToString(TSHEK.toString())
                if (dictionary.containsExact(candidate)) {
                    matchLen = len
                    break
                }
            }
            words.add(syllables.subList(i, i + matchLen).joinToString(TSHEK.toString()))
            i += matchLen
        }
        return words
    }
}
