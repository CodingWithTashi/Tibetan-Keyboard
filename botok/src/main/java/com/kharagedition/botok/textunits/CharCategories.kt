package com.kharagedition.botok.textunits

import com.kharagedition.botok.CharMarkers

/**
 * Port of botok/textunits/charcategories.py
 *
 * Builds a lookup table from Tibetan Unicode characters to their [CharMarkers] category values
 * by parsing the `bo_uni_table.csv` resource.
 *
 * Usage:
 *   1. Call [init] once with the CSV lines before calling [getCharCategory].
 *   2. From an Android component, obtain lines via AssetLoader.readLines().
 *   3. From a JVM test, read the file directly.
 */
object CharCategories {

    // Maps every Tibetan-range char (U+0F00–U+0FFF) to its CharMarkers int value
    private val charToCategory = HashMap<Char, Int>(300)
    private var initialized = false

    // Transparent chars — defined here because they are tested before the Tibetan-range lookup.
    // Matches the `transparent` list in Python's bostring.py exactly.
    val TRANSPARENT_CHARS: Set<Char> = setOf(
        ' ',        // U+0020 SPACE
        '\u180E',   // MONGOLIAN VOWEL SEPARATOR
        '\u2000',   // EN QUAD
        '\u2001',   // EM QUAD
        '\u2002',   // EN SPACE
        '\u2003',   // EM SPACE
        '\u2004',   // THREE-PER-EM SPACE
        '\u2005',   // FOUR-PER-EM SPACE
        '\u2006',   // SIX-PER-EM SPACE
        '\u2007',   // FIGURE SPACE
        '\u2008',   // PUNCTUATION SPACE
        '\u2009',   // THIN SPACE
        '\u200A',   // HAIR SPACE
        '\u200B',   // ZERO WIDTH SPACE
        '\u202F',   // NARROW NO-BREAK SPACE
        '\u205F',   // MEDIUM MATHEMATICAL SPACE
        '\u3000',   // IDEOGRAPHIC SPACE
        '\uFEFF',   // ZERO WIDTH NO-BREAK SPACE (BOM)
        '\t',       // Tabulation
        '\n'        // Carriage return
    )

    /**
     * Initialise from parsed CSV lines (header line must already be stripped or will be skipped).
     * Matches Python:
     *   for row in list(csv.reader(...))[1:]:
     *       char = row[1].replace("—", "")
     *       cat  = c[row[2]].value
     */
    fun init(csvLines: List<String>) {
        charToCategory.clear()
        for (line in csvLines) {
            // Simple CSV split — the table has no quoted commas, so split on ',' is safe
            val parts = line.split(",")
            if (parts.size < 3) continue
            val rawChar = parts[1].replace("—", "")
            val categoryName = parts[2].trim()
            val categoryValue = charMarkerValueFor(categoryName) ?: continue
            for (ch in rawChar) {
                charToCategory[ch] = categoryValue
            }
        }
        initialized = true
    }

    /**
     * Returns the [CharMarkers] int value for the given character.
     * Port of Python's `get_char_category(char)`.
     */
    fun getCharCategory(char: Char): Int {
        check(initialized) { "CharCategories not initialised — call init() first." }

        if (char in TRANSPARENT_CHARS) return CharMarkers.TRANSPARENT

        // Tibetan Unicode block (U+0F00–U+0FFF)
        if (char in '\u0F00'..'\u0FFF') {
            return charToCategory[char]
                ?: throw IllegalArgumentException(
                    "Char '${char}' (U+${char.code.toString(16).uppercase()}) is in the Tibetan range but missing from the table."
                )
        }

        // CJK ranges (BMP portion of Python's check; supplementary plane omitted — negligible for keyboard use)
        if (char in '\u2E80'..'\uFAFF' || char in '\uFE30'..'\uFE4F') {
            return CharMarkers.CJK
        }

        // Latin range
        // 1. U+0020–U+036F: Basic Latin + Latin-1 + Extended A/B + IPA + Spacing Modifiers + Combining Diacritical
        // 2. U+1E00–U+20CF: Latin Extended Additional + Superscripts + Currency
        if (char in '\u0020'..'\u036F' || char in '\u1E00'..'\u20CF') {
            return CharMarkers.LATIN
        }

        return CharMarkers.OTHER
    }

    // --- helpers ------------------------------------------------------------

    private fun charMarkerValueFor(name: String): Int? = try {
        CharMarkers.nameToValue(name)
    } catch (e: NoSuchElementException) {
        null  // unknown category names are silently skipped
    }
}
