package com.kharagedition.botok.chunks

import com.kharagedition.botok.CharMarkers
import com.kharagedition.botok.ChunkMarkers
import com.kharagedition.botok.NO_SHAD_CONS
import com.kharagedition.botok.VOWELS
import com.kharagedition.botok.textunits.BoString

/**
 * Port of botok/chunks/chunkframework.py
 *
 * Provides fine-grained chunking methods for Tibetan text.
 * Each public method signature is (start, end, yes) → List<Triple<Int,Int,Int>>
 * so they can be passed directly to pipeChunk().
 */
open class ChunkFramework(
    string: String,
    ignoreChars: Set<Char>? = null
) : ChunkFrameworkBase(BoString(string, ignoreChars ?: emptySet())) {

    // ------------------------------------------------------------------
    // chunk_bo_chars — Tibetan Unicode vs everything else
    // ------------------------------------------------------------------

    fun chunkBoChars(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.BO
    ): List<Triple<Int, Int, Int>> =
        chunkUsing(::isBoUnicode, start, end, yes, ChunkMarkers.OTHER)

    private fun isBoUnicode(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] != CharMarkers.OTHER &&
        bs.baseStructure[charIdx] != CharMarkers.LATIN &&
        bs.baseStructure[charIdx] != CharMarkers.CJK

    // ------------------------------------------------------------------
    // chunk_latin — Latin vs everything else
    // ------------------------------------------------------------------

    fun chunkLatin(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.LATIN
    ): List<Triple<Int, Int, Int>> =
        chunkUsing(::isLatin, start, end, yes, ChunkMarkers.OTHER)

    private fun isLatin(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] == CharMarkers.LATIN ||
        bs.baseStructure[charIdx] == CharMarkers.TRANSPARENT

    // ------------------------------------------------------------------
    // chunk_cjk — CJK vs everything else
    // ------------------------------------------------------------------

    fun chunkCjk(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.CJK
    ): List<Triple<Int, Int, Int>> =
        chunkUsing(::isCjk, start, end, yes, ChunkMarkers.OTHER)

    private fun isCjk(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] == CharMarkers.CJK ||
        bs.baseStructure[charIdx] == CharMarkers.TRANSPARENT

    // ------------------------------------------------------------------
    // chunk_punct — punctuation vs non-punctuation
    // ------------------------------------------------------------------

    fun chunkPunct(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.PUNCT
    ): List<Triple<Int, Int, Int>> =
        chunkUsing(::isPunct, start, end, yes, ChunkMarkers.NON_PUNCT)

    private fun isPunct(charIdx: Int): Boolean {
        // If a tsek or a space is right after a non-text character
        if (charIdx > 0) {
            val prev = bs.baseStructure[charIdx - 1]
            if (prev == CharMarkers.SYMBOL     ||
                prev == CharMarkers.NUMERAL    ||
                prev == CharMarkers.OTHER      ||
                prev == CharMarkers.NORMAL_PUNCT ||
                prev == CharMarkers.SPECIAL_PUNCT ||
                prev == CharMarkers.TSEK       ||
                prev == CharMarkers.TRANSPARENT) {
                val curr = bs.baseStructure[charIdx]
                if (curr == CharMarkers.TSEK        ||
                    curr == CharMarkers.TRANSPARENT ||
                    curr == CharMarkers.NORMAL_PUNCT) {
                    return true
                }
            }
        }
        return bs.baseStructure[charIdx] == CharMarkers.NORMAL_PUNCT  ||
               bs.baseStructure[charIdx] == CharMarkers.SPECIAL_PUNCT ||
               bs.baseStructure[charIdx] == CharMarkers.TRANSPARENT
    }

    // ------------------------------------------------------------------
    // chunk_symbol — symbols vs non-symbols
    // ------------------------------------------------------------------

    fun chunkSymbol(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.SYM
    ): List<Triple<Int, Int, Int>> =
        chunkUsing(::isSym, start, end, yes, ChunkMarkers.NON_SYM)

    private fun isSym(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] == CharMarkers.SYMBOL      ||
        bs.baseStructure[charIdx] == CharMarkers.TRANSPARENT ||
        bs.baseStructure[charIdx] == CharMarkers.NFC

    // ------------------------------------------------------------------
    // chunk_number — numerals vs non-numerals
    // ------------------------------------------------------------------

    fun chunkNumber(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.NUM
    ): List<Triple<Int, Int, Int>> =
        chunkUsing(::isNum, start, end, yes, ChunkMarkers.NON_NUM)

    private fun isNum(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] == CharMarkers.NUMERAL     ||
        bs.baseStructure[charIdx] == CharMarkers.TRANSPARENT

    // ------------------------------------------------------------------
    // chunk_spaces — spaces vs non-spaces
    // ------------------------------------------------------------------

    fun chunkSpaces(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.SPACE
    ): List<Triple<Int, Int, Int>> =
        chunkUsing(::isTransparent, start, end, yes, ChunkMarkers.NON_SPACE)

    private fun isTransparent(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] == CharMarkers.TRANSPARENT

    // ------------------------------------------------------------------
    // syllabify — split Tibetan text into syllables on tsek/long-skrt-vowel
    // ------------------------------------------------------------------

    /**
     * Port of Python's syllabify().
     * Each returned chunk is (TEXT, start, length), tsek included at the end of each syllable.
     */
    fun syllabify(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.TEXT
    ): List<Triple<Int, Int, Int>> {
        val indices = chunk(start, end, ::isTsekOrLongSkrtVowel).toMutableList()

        // Attach trailing tsek/long-vowel to the preceding syllable body
        for (num in indices.indices) {
            val i = indices[num]
            if (i.first && num - 1 >= 0 && !indices[num - 1].first) {
                val prev = indices[num - 1]
                indices[num - 1] = Triple(prev.first, prev.second, prev.third + i.third)
            }
        }

        // Keep only non-separator groups → each is a syllable
        return indices.filter { !it.first }.map { Triple(yes, it.second, it.third) }
    }

    private fun isTsekOrLongSkrtVowel(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] == CharMarkers.TSEK ||
        bs.baseStructure[charIdx] == CharMarkers.SKRT_LONG_VOW

    // ------------------------------------------------------------------
    // adjust_syls — split syllable chunks that contain an internal space
    // ------------------------------------------------------------------

    /**
     * Port of Python's adjust_syls().
     *
     * Returns empty list (no change) if there is nothing to adjust,
     * or a replacement list of TEXT chunks when a space-separated split is found.
     *
     * Uses Int? internally to model Python's mixed True/False/None/int markers:
     *   TRANSPARENT_MARK → Python's True  (space, removed by filter)
     *   CONTENT_MARK     → Python's False (syllable body, converted to yes)
     *   null             → Python's None  (merged away, removed by filter)
     *   other int        → actual ChunkMarkers value (TEXT, PUNCT…)
     */
    fun adjustSyls(
        start: Int = 0,
        end: Int = bs.len,
        yes: Int = ChunkMarkers.TEXT
    ): List<Triple<Int, Int, Int>> {
        val TRANSPARENT_MARK = Int.MAX_VALUE     // Python's True
        val CONTENT_MARK     = Int.MAX_VALUE - 1 // Python's False

        val rawChunks = chunk(start, end, ::isTransparent)
        val indices = rawChunks.map { (matched, s, l) ->
            Triple<Int?, Int, Int>(if (matched) TRANSPARENT_MARK else CONTENT_MARK, s, l)
        }.toMutableList()

        for (num in indices.indices) {
            val entry = indices[num]
            when {
                // Interior transparent (space) chunk
                indices.size - 1 > num && num > 0 && entry.first == TRANSPARENT_MARK -> {
                    val prev = indices[num - 1]
                    val text = bs.string.substring(prev.second, prev.second + prev.third)
                    val shouldSplit =
                        (text.length >= 2 &&
                         text.last().toString() in VOWELS &&
                         text[text.length - 2].toString() in NO_SHAD_CONS) ||
                        (text.length >= 1 &&
                         text.last().toString() in NO_SHAD_CONS)

                    if (shouldSplit) {
                        // Merge space into preceding syllable (split at space)
                        indices[num - 1] = Triple(yes, prev.second, prev.third + entry.third)
                    } else {
                        // Merge preceding + space + following into one chunk
                        val next = indices[num + 1]
                        indices[num - 1] = Triple(
                            prev.first, prev.second, prev.third + entry.third + next.third
                        )
                        indices[num + 1] = Triple(null, next.second, next.third)
                    }
                }
                // Non-transparent content → becomes TEXT
                entry.first == CONTENT_MARK -> {
                    indices[num] = Triple(yes, entry.second, entry.third)
                }
                // Leading or trailing transparent → becomes PUNCT
                (num == 0 || num == indices.size - 1) && entry.first == TRANSPARENT_MARK -> {
                    indices[num] = Triple(ChunkMarkers.PUNCT, entry.second, entry.third)
                }
            }
        }

        // Filter: remove transparent sentinels and nulls; these correspond to
        // Python's "i[0] is not True and i[0] is not None"
        val result = indices
            .filter { it.first != TRANSPARENT_MARK && it.first != null }
            .map { Triple(it.first!!, it.second, it.third) }

        // Return empty list if only one element (caller keeps original chunk)
        return if (result.size > 1) result else emptyList()
    }
}
