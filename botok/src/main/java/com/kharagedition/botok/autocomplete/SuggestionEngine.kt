package com.kharagedition.botok.autocomplete

/**
 * Fast Tibetan word suggestion engine.
 *
 * Words are loaded from TSV lines (format: form\tpos\tlemma\tsense\tfreq)
 * and stored in a sorted array for O(log N) prefix lookup via binary search.
 *
 * Call [addLines] for each TSV file, then [ready] once to sort and activate.
 * After [ready], [suggest] is thread-safe and allocation-light.
 */
class SuggestionEngine {

    private val words = ArrayList<WordEntry>(35000)

    @Volatile
    var isReady = false
        private set

    private data class WordEntry(val form: String, val freq: Int)

    private companion object {
        /** The syllable separator, and its no-break form (used before a shad after nga). */
        val TSHEGS = charArrayOf('་', '༌')
    }

    fun addLines(lines: Sequence<String>) {
        for (line in lines) {
            val trimmed = line.trimStart('\uFEFF')
            if (trimmed.startsWith('#') || trimmed.isBlank()) continue
            val parts = trimmed.split('\t')
            val form = parts[0].trim()
            if (form.isEmpty()) continue
            val freq = parts.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
            words.add(WordEntry(form, freq))
        }
    }

    val wordCount: Int get() = words.size

    fun ready() {
        words.sortWith(compareBy { it.form })
        isReady = true
    }

    /**
     * Completions for a prefix, with [matched]: the part of the prefix the words complete — the
     * whole prefix, or the tail of it (starting after a tsheg) that found them. Whoever inserts a
     * chosen word must replace exactly [matched]; what precedes it is earlier words the user
     * already typed (Tibetan puts no spaces between words).
     */
    data class Suggestions(val words: List<String>, val matched: String) {
        companion object {
            val NONE = Suggestions(emptyList(), "")
        }
    }

    /**
     * Tries the whole prefix, then each tail that starts after a tsheg, longest first: a run has
     * no word boundaries, so "ང་བོད་ར" is first read as one word, then as the compound in
     * progress (བོད་ར → བོད་རིགས), then as a new word (ར → རང).
     */
    fun suggest(prefix: String, max: Int = 4): Suggestions {
        if (!isReady || prefix.isEmpty()) return Suggestions.NONE

        var start = 0
        while (start < prefix.length) {
            val tail = prefix.substring(start)
            val words = lookup(tail, max)
            if (words.isNotEmpty()) return Suggestions(words, tail)
            val tsheg = prefix.indexOfAny(TSHEGS, start)
            if (tsheg < 0) break
            start = tsheg + 1
        }
        return Suggestions.NONE
    }

    /** True if [word] is an exact dictionary entry (used for greedy word-boundary segmentation). */
    fun containsExact(word: String): Boolean {
        if (!isReady || word.isEmpty()) return false
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi).ushr(1)
            if (words[mid].form < word) lo = mid + 1 else hi = mid
        }
        return lo < words.size && words[lo].form == word
    }

    private fun lookup(prefix: String, max: Int): List<String> {
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi).ushr(1)
            if (words[mid].form < prefix) lo = mid + 1 else hi = mid
        }
        val seen = LinkedHashMap<String, WordEntry>(max * 10)
        var i = lo
        while (i < words.size && words[i].form.startsWith(prefix)) {
            val e = words[i]
            val existing = seen[e.form]
            if (existing == null || e.freq > existing.freq) seen[e.form] = e
            i++
            if (seen.size >= max * 20) break
        }
        if (seen.isEmpty()) return emptyList()
        return seen.values.sortedByDescending { it.freq }.take(max).map { it.form }
    }
}
