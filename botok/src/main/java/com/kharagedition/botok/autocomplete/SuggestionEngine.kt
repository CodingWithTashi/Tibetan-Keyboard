package com.kharagedition.botok.autocomplete

/**
 * Fast Tibetan word suggestion engine.
 *
 * Words are loaded from TSV lines (format: form\tpos\tlemma\tsense\tfreq)
 * and stored in a sorted array for O(log N) prefix lookup via binary search.
 *
 * Call [addLines] for each TSV file, then [ready] once to sort and activate.
 * After [ready], [getSuggestions] is thread-safe and allocation-light.
 */
class SuggestionEngine {

    private val words = ArrayList<WordEntry>(35000)

    @Volatile
    var isReady = false
        private set

    private data class WordEntry(val form: String, val freq: Int)

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

    fun getSuggestions(prefix: String, max: Int = 4): List<String> {
        if (!isReady || prefix.isEmpty()) return emptyList()

        val direct = lookup(prefix, max)
        if (direct.isNotEmpty()) return direct

        // Fallback: when typing a new word after a tshek without a space, the prefix is
        // "previousWord་newPartial". Strip back to the text after the last tshek and retry.
        val lastTshek = prefix.lastIndexOf('་')
        if (lastTshek >= 0) {
            val tail = prefix.substring(lastTshek + 1)
            if (tail.isNotEmpty()) return lookup(tail, max)
        }
        return emptyList()
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
