package com.kharagedition.botok.chunks

import com.kharagedition.botok.ChunkMarkers
import com.kharagedition.botok.chunkValues
import com.kharagedition.botok.textunits.BoString

/**
 * Port of botok/chunks/chunkframeworkbase.py
 *
 * All chunks are represented as Triple<Int, Int, Int> = (marker, startIndex, length).
 * Internal chunking uses Triple<Boolean, Int, Int> = (matched, startIndex, length).
 */
open class ChunkFrameworkBase(protected val bs: BoString) {

    // ------------------------------------------------------------------
    // Core chunking primitive
    // ------------------------------------------------------------------

    /**
     * Port of Python's chunk(start_idx, end_idx, condition_func).
     * Groups consecutive chars that pass/fail the test into (matched, start, length) triples.
     */
    protected fun chunk(
        startIdx: Int,
        endIdx: Int,
        conditionFunc: (Int) -> Boolean
    ): MutableList<Triple<Boolean, Int, Int>> {
        val chunked = mutableListOf<Triple<Boolean, Int, Int>>()
        var start = startIdx
        var length = 0
        var initialized = false
        var prevState = false
        var currentState = false

        for (i in startIdx until endIdx) {
            currentState = conditionFunc(i)
            if (!initialized) {
                prevState = currentState
                initialized = true
            }
            if (currentState == prevState) {
                length++
            } else {
                chunked.add(Triple(prevState, start, length))
                prevState = currentState
                start += length
                length = 1
            }
        }
        // Final element (port of Python's trailing-element logic)
        if (length != 0) {
            if (currentState == prevState && start + length < endIdx) {
                length++
            }
            chunked.add(Triple(prevState, start, length))
        }
        return chunked
    }

    // ------------------------------------------------------------------
    // chunk_using — converts bool chunks to int-marker chunks
    // ------------------------------------------------------------------

    protected fun chunkUsing(
        conditionFunc: (Int) -> Boolean,
        start: Int,
        end: Int,
        yes: Int,
        no: Int
    ): List<Triple<Int, Int, Int>> {
        return chunk(start, end, conditionFunc).map { (matched, s, l) ->
            if (matched) Triple(yes, s, l) else Triple(no, s, l)
        }
    }

    // ------------------------------------------------------------------
    // pipe_chunk — re-chunks in place
    // ------------------------------------------------------------------

    /**
     * Port of Python's pipe_chunk().
     * Re-chunks matching chunks with a new chunking function in place.
     *
     * @param pipedChunkFunc (start, end, yes) → new chunks
     */
    fun pipeChunk(
        chunks: MutableList<Triple<Int, Int, Int>>,
        pipedChunkFunc: (Int, Int, Int) -> List<Triple<Int, Int, Int>>,
        toChunkMarker: Int,
        yes: Int
    ): MutableList<Triple<Int, Int, Int>> {
        var i = 0
        while (i < chunks.size) {
            val chunk = chunks[i]
            if (chunk.first == toChunkMarker) {
                val new = pipedChunkFunc(chunk.second, chunk.second + chunk.third, yes)
                if (new.isNotEmpty()) {
                    chunks.removeAt(i)
                    for ((j, nChunk) in new.withIndex()) {
                        if (nChunk.first != yes) {
                            chunks.add(i + j, Triple(chunk.first, nChunk.second, nChunk.third))
                        } else {
                            chunks.add(i + j, nChunk)
                        }
                    }
                    // Python's enumerate auto-increments past inserted elements the same way
                    // (first inserted element is at i, so i++ moves to second → same as Python)
                }
            }
            i++
        }
        return chunks
    }

    // ------------------------------------------------------------------
    // merge_chunks — generic merger driven by a condition lambda
    // ------------------------------------------------------------------

    /**
     * Port of Python's merge_chunks().
     * Merges adjacent chunks when mergeConditionFunc returns true.
     */
    protected fun mergeChunks(
        chunks: MutableList<Triple<Int, Int, Int>>,
        mergeConditionFunc: (previous: Triple<Int, Int, Int>, current: Triple<Int, Int, Int>) -> Boolean
    ): MutableList<Triple<Int, Int, Int>> {
        var num = 0
        while (num <= chunks.size - 1) {
            val current = chunks[num]
            if (num - 1 >= 0) {
                val previous = chunks[num - 1]
                if (mergeConditionFunc(previous, current)) {
                    // Python's "while not previous" loop is dead code (tuples are always truthy)
                    chunks[num - 1] = Triple(previous.first, previous.second, previous.third + current.third)
                    chunks.removeAt(num)
                    num--
                }
            }
            num++
        }
        return chunks
    }

    // ------------------------------------------------------------------
    // merge_condition — all chars in chunk must satisfy test
    // ------------------------------------------------------------------

    protected fun mergeCondition(
        chunk: Triple<Int, Int, Int>,
        conditionFunc: (Int) -> Boolean
    ): Boolean {
        val start = chunk.second
        val end = chunk.second + chunk.third
        return (start until end).all { conditionFunc(it) }
    }

    // ------------------------------------------------------------------
    // clean_chunks / merge_spaces / merge_similar_chunks
    // ------------------------------------------------------------------

    fun cleanChunks(chunks: MutableList<Triple<Int, Int, Int>>): MutableList<Triple<Int, Int, Int>> {
        val merged = mergeSpaces(chunks)
        return mergeSimilarChunks(merged)
    }

    fun mergeSpaces(chunks: MutableList<Triple<Int, Int, Int>>): MutableList<Triple<Int, Int, Int>> =
        mergeChunks(chunks) { _, current -> mergeCondition(current, ::isSpace) }

    private fun isSpace(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] == com.kharagedition.botok.CharMarkers.TRANSPARENT

    fun mergeSimilarChunks(chunks: MutableList<Triple<Int, Int, Int>>): MutableList<Triple<Int, Int, Int>> =
        mergeChunks(chunks) { previous, current ->
            previous.first != ChunkMarkers.TEXT &&
            current.first  != ChunkMarkers.TEXT &&
            previous.first == current.first
        }

    // ------------------------------------------------------------------
    // merge_skippable_punct
    // ------------------------------------------------------------------

    fun mergeSkippablePunct(chunks: MutableList<Triple<Int, Int, Int>>): MutableList<Triple<Int, Int, Int>> {
        var i = 0
        while (i <= chunks.size - 1) {
            val current = chunks[i]
            // First element: merge into the next chunk
            if (i == 0 && chunks.size - 1 >= 1) {
                val toDel = (current.second until current.second + current.third).all { isSkippablePunct(it) }
                if (toDel) {
                    chunks[i + 1] = Triple(
                        chunks[i + 1].first,
                        current.second,
                        chunks[i + 1].second + chunks[i + 1].third
                    )
                    chunks.removeAt(i)
                    i--
                }
            }
            // Remaining: merge into the previous chunk
            if (i - 1 >= 0) {
                val cur = chunks[i]
                val toDel = (cur.second until cur.second + cur.third).all { isSkippablePunct(it) }
                if (toDel) {
                    val prev = chunks[i - 1]
                    chunks[i - 1] = Triple(prev.first, prev.second, cur.third + prev.third)
                    chunks.removeAt(i)
                    i--
                }
            }
            i++
        }
        return mergeSimilarChunks(chunks)
    }

    private fun isSkippablePunct(charIdx: Int): Boolean =
        bs.baseStructure[charIdx] == com.kharagedition.botok.CharMarkers.TSEK || isSpace(charIdx)

    // ------------------------------------------------------------------
    // Output helpers
    // ------------------------------------------------------------------

    /**
     * Replace int markers with human-readable names.
     * Port of Python's get_markers().
     */
    fun getMarkers(chunks: List<Triple<Int, Int, Int>>): List<Triple<String, Int, Int>> =
        chunks.map { (marker, start, length) ->
            Triple(chunkValues[marker] ?: "?", start, length)
        }

    /**
     * Replace indices with substrings (int marker version).
     * Port of Python's get_chunked() when called with raw int-marker chunks.
     */
    fun getChunked(chunks: List<Triple<Int, Int, Int>>): List<Pair<Int, String>> =
        chunks.map { (marker, start, length) ->
            Pair(marker, bs.string.substring(start, start + length))
        }

    /**
     * Return (name, substring) pairs.
     * Port of Python's get_readable().
     */
    fun getReadable(chunks: List<Triple<Int, Int, Int>>): List<Pair<String, String>> =
        chunks.map { (marker, start, length) ->
            Pair(chunkValues[marker] ?: "?", bs.string.substring(start, start + length))
        }
}