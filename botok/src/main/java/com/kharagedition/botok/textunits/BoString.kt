package com.kharagedition.botok.textunits

import com.kharagedition.botok.CharMarkers

/**
 * Port of botok/textunits/bostring.py — BoString
 *
 * Wraps a raw string and categorises every character into a [CharMarkers] value,
 * storing the result in [baseStructure] (char-index → category int).
 *
 * This is the foundational building block: all chunking and tokenization layers
 * read from [baseStructure] rather than re-examining the raw string characters.
 */
class BoString(
    val string: String,
    val ignoreChars: Set<Char> = emptySet()
) {
    val len: Int = string.length

    /**
     * Maps char-index → [CharMarkers] int value.
     * Equivalent to Python's `base_structure: dict[int, int]`.
     */
    val baseStructure: IntArray = IntArray(len)

    init {
        attributeBasicTypes()
    }

    // Port of Python's __attribute_basic_types()
    private fun attributeBasicTypes() {
        for (i in 0 until len) {
            val char = string[i]
            val cat = CharCategories.getCharCategory(char)
            nfcCheck(cat, i)
            baseStructure[i] = if (char in ignoreChars) {
                CharMarkers.TRANSPARENT  // ignore-listed chars treated as transparent spaces
            } else {
                cat
            }
        }
    }

    // Port of Python's __nfc_check()
    // Emits a warning (via Android Log or stderr in tests) when a non-expanded (NFC-only) char is seen.
    private fun nfcCheck(cat: Int, idx: Int) {
        if (cat == CharMarkers.NFC) {
            val sliceStart = maxOf(0, idx - 10)
            val sliceEnd = minOf(len, idx + 10)
            val context = string.substring(sliceStart, sliceEnd)
            nfcWarningListener?.invoke(
                "Beware of unexpected results: input string contains the non-expanded char " +
                        "\"${string[idx]}\", found in \"$context\"."
            )
        }
    }

    /**
     * Export the [baseStructure] slice as a map, optionally re-indexed from 0.
     * Port of Python's `export_groups(start_idx, slice_len, for_substring=True)`.
     *
     * @param startIdx   first character index
     * @param sliceLen   number of characters to export
     * @param forSubstring if true, keys start from 0; otherwise original indices are preserved
     */
    fun exportGroups(startIdx: Int, sliceLen: Int, forSubstring: Boolean = true): Map<Int, Int> {
        val result = LinkedHashMap<Int, Int>(sliceLen)
        for (n in 0 until sliceLen) {
            val i = startIdx + n
            result[if (forSubstring) n else i] = baseStructure[i]
        }
        return result
    }

    /**
     * Returns a human-readable map from char-index → category name.
     * Port of Python's `get_categories(struct=None)`.
     */
    fun getCategories(struct: Map<Int, Int>? = null): Map<Int, String> {
        val source = struct ?: baseStructure.withIndex().associate { (i, v) -> i to v }
        return source.mapValues { (_, v) -> CharMarkers.valueToName[v] ?: "UNKNOWN" }
    }

    companion object {
        /**
         * Optional listener for NFC warnings. In production, wire this to Android Log.w().
         * In tests, capture warnings by setting this to a recording lambda.
         * Defaults to stderr output.
         */
        var nfcWarningListener: ((String) -> Unit)? = { msg -> System.err.println("WARN: $msg") }
    }
}
