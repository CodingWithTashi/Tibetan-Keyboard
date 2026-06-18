package com.kharagedition.botok.chunks

import com.kharagedition.botok.CharMarkers
import com.kharagedition.botok.ChunkMarkers
import com.kharagedition.botok.textunits.BoString

/**
 * Port of botok/chunks/chunks.py — Chunks and TokChunks classes.
 */

// ---------------------------------------------------------------------------
// Chunks — full chunking pipeline
// ---------------------------------------------------------------------------

/**
 * Runs the complete Tibetan chunking pipeline.
 * Port of Python's Chunks class.
 *
 * Pipeline (mirrors Python make_chunks):
 *   chunk_bo_chars → chunk_punct → chunk_symbol → chunk_number →
 *   [merge_skippable_punct] → syllabify → adjust_syls →
 *   chunk_cjk → chunk_latin → [merge_skippable_punct]
 */
open class Chunks(
    string: String,
    ignoreChars: Set<Char>? = null
) : ChunkFramework(string, ignoreChars) {

    /**
     * Port of Python's make_chunks(indices=True, gen=False, space_as_punct=False).
     *
     * Returns raw (marker, start, length) triples.
     * Note: Python's `indices=True` returns RAW chunks (not substrings).
     */
    fun makeChunks(spaceAsPunct: Boolean = false): MutableList<Triple<Int, Int, Int>> {
        val chunks = chunkBoChars().toMutableList()

        if (spaceAsPunct) {
            pipeChunk(chunks, ::chunkSpaces, ChunkMarkers.BO, ChunkMarkers.PUNCT)
        }

        pipeChunk(chunks, ::chunkPunct,   ChunkMarkers.BO, ChunkMarkers.PUNCT)
        pipeChunk(chunks, ::chunkSymbol,  ChunkMarkers.BO, ChunkMarkers.SYM)
        pipeChunk(chunks, ::chunkNumber,  ChunkMarkers.BO, ChunkMarkers.NUM)

        if (!spaceAsPunct) {
            mergeSkippablePunct(chunks)
        }

        pipeChunk(chunks, ::syllabify,   ChunkMarkers.BO,   ChunkMarkers.TEXT)
        pipeChunk(chunks, ::adjustSyls,  ChunkMarkers.TEXT, ChunkMarkers.TEXT)
        pipeChunk(chunks, ::chunkCjk,    ChunkMarkers.OTHER, ChunkMarkers.CJK)
        pipeChunk(chunks, ::chunkLatin,  ChunkMarkers.OTHER, ChunkMarkers.LATIN)

        if (!spaceAsPunct) {
            mergeSkippablePunct(chunks)
        }

        return chunks
    }
}

// ---------------------------------------------------------------------------
// TokChunks — syllable identification for the trie
// ---------------------------------------------------------------------------

/**
 * Port of Python's TokChunks class.
 *
 * Wraps each chunk from makeChunks() into a Pair:
 *   - List<Int>? : cleaned char indices for TEXT chunks (null for non-text)
 *   - Triple<Int,Int,Int> : the raw chunk
 */
class TokChunks(
    string: String,
    ignoreChars: Set<Char>? = null,
    val spaceAsPunct: Boolean = false
) : Chunks(string, ignoreChars) {

    var chunks: List<Pair<List<Int>?, Triple<Int, Int, Int>>>? = null

    /**
     * Public accessor for the BoString instance (needed by Tokenize)
     */
    val boString: BoString
        get() = bs

    /**
     * Port of Python's serve_syls_to_trie().
     * Attaches cleaned syllable indices to each TEXT chunk.
     */
    fun serveSylsToTrie(): List<Pair<List<Int>?, Triple<Int, Int, Int>>> {
        val result = mutableListOf<Pair<List<Int>?, Triple<Int, Int, Int>>>()
        for (chunk in makeChunks(spaceAsPunct = spaceAsPunct)) {
            if (chunk.first == ChunkMarkers.TEXT) {
                val syl = getTextChars(chunk.second, chunk.second + chunk.third)
                result.add(Pair(syl, chunk))
            } else {
                result.add(Pair(null, chunk))
            }
        }
        chunks = result
        return result
    }

    /**
     * Port of Python's get_syls().
     * Returns cleaned syllable strings (no tsek/space).
     */
    fun getSyls(): List<String> {
        val syls = mutableListOf<String>()
        for (chunk in makeChunks(spaceAsPunct = spaceAsPunct)) {
            if (chunk.first == ChunkMarkers.TEXT) {
                val charIdxs = getTextChars(chunk.second, chunk.second + chunk.third)
                syls.add(charIdxs.joinToString("") { bs.string[it].toString() })
            }
        }
        return syls
    }

    /**
     * Port of Python's __get_text_chars().
     * Returns char indices of the cleaned syllable (no tsek/space/long-skrt-vowel).
     */
    private fun getTextChars(startIdx: Int, endIdx: Int): List<Int> =
        (startIdx until endIdx).filter { isSylText(it) }

    /**
     * Port of Python's __is_syl_text().
     * True if the character counts as part of the cleaned syllable.
     * Note: Python's expression is:
     *   (not TSEK and not TRANSPARENT and not SKRT_LONG_VOW) or SKRT_LONG_VOW
     * which simplifies to: not TSEK and not TRANSPARENT.
     */
    private fun isSylText(charIdx: Int): Boolean {
        val cat = bs.baseStructure[charIdx]
        return cat != CharMarkers.TSEK && cat != CharMarkers.TRANSPARENT
    }
}
