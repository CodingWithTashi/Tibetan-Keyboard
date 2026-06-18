package com.kharagedition.botok.utils

/**
 * Port of botok/utils/unicode_normalization.py
 *
 * Provides Tibetan Unicode normalization:
 * - Replaces deprecated / discouraged composed characters with their canonical decompositions
 * - Reorders combining marks within syllable stacks to a canonical order
 * - Handles special cases (ra-subscript 0F6A, invalid start sequences)
 */
object UnicodeNormalization {

    // ---------------------------------------------------------------------------
    // Cats — mirrors Python's Cats enum (used only inside unicode_reorder)
    // ---------------------------------------------------------------------------
    private object Cats {
        const val Other = 0
        const val Base = 1
        const val Subscript = 2
        const val BottomVowel = 3
        const val BottomMark = 4
        const val TopVowel = 5
        const val TopMark = 6
        const val RightMark = 7
    }

    /**
     * CATEGORIES — mirrors Python's CATEGORIES tuple (indexed from 0x0F00).
     * Each int value corresponds to a Cats constant.
     *
     * Build once as a static IntArray so index lookup is O(1).
     * Port of Python's list-concatenation construction.
     */
    private val CATEGORIES: IntArray = buildCategories()

    private fun buildCategories(): IntArray {
        val list = mutableListOf<Int>()
        list += Cats.Other                          // 0F00
        list += Cats.Base                           // 0F01
        list += List(22) { Cats.Other }             // 0F02-0F17
        list += List(2) { Cats.BottomVowel }        // 0F18-0F19
        list += List(6) { Cats.Other }              // 0F1A-0F1F
        list += List(20) { Cats.Base }              // 0F20-0F33 (numbers)
        list += Cats.Other                          // 0F34
        list += Cats.BottomMark                     // 0F35
        list += Cats.Other                          // 0F36
        list += Cats.BottomMark                     // 0F37
        list += Cats.Other                          // 0F38
        list += Cats.Subscript                      // 0F39
        list += List(4) { Cats.Other }              // 0F3A-0F3D
        list += Cats.RightMark                      // 0F3E
        list += Cats.Other                          // 0F3F
        list += List(45) { Cats.Base }              // 0F40-0F6C
        list += List(4) { Cats.Other }              // 0F6D-0F70
        list += Cats.BottomVowel                    // 0F71
        list += Cats.TopVowel                       // 0F72
        list += Cats.TopVowel                       // 0F73
        list += List(2) { Cats.BottomVowel }        // 0F74-0F75
        list += List(8) { Cats.TopVowel }           // 0F76-0F7D
        list += Cats.TopMark                        // 0F7E
        list += Cats.RightMark                      // 0F7F
        list += List(2) { Cats.TopVowel }           // 0F80-0F81
        list += List(2) { Cats.TopMark }            // 0F82-0F83
        list += Cats.BottomMark                     // 0F84
        list += Cats.Other                          // 0F85
        list += List(2) { Cats.TopMark }            // 0F86-0F87
        list += List(2) { Cats.Base }               // 0F88-0F89
        list += Cats.Base                           // 0F8A
        list += Cats.Other                          // 0F8B
        list += Cats.Base                           // 0F8C
        list += List(48) { Cats.Subscript }         // 0F8D-0FBC
        return list.toIntArray()
    }

    /** Returns the Cats value for a single character (U+0F00–U+0FBC → table; else Other). */
    private fun charcat(c: Char): Int {
        val o = c.code
        return if (o in 0x0F00..0x0FBC) CATEGORIES[o - 0x0F00] else Cats.Other
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Reorder combining marks within Tibetan syllable stacks to canonical order.
     * Port of Python's `unicode_reorder(txt)`.
     *
     * @return Pair(reordered string, isValid)
     */
    fun unicodeReorder(txt: String): Pair<String, Boolean> {
        val charcats = txt.map { charcat(it) }
        val res = StringBuilder(txt.length)
        var i = 0
        var valid = true

        while (i < charcats.size) {
            val c = charcats[i]
            if (c != Cats.Base) {
                if (c > Cats.Base) valid = false
                res.append(txt[i])
                i++
                continue
            }
            // Scan for end of this syllable stack component
            var j = i + 1
            while (j < charcats.size && charcats[j] > Cats.Base) j++

            // Sort indices in this stack by (category, original position) — matches Python's key
            val range = (i until j).toList()
            val sorted = range.sortedWith(compareBy({ charcats[it] }, { it }))
            for (n in sorted) res.append(txt[n])
            i = j
        }
        return Pair(res.toString(), valid)
    }

    /**
     * Normalize a Tibetan Unicode string.
     * Port of Python's `normalize_unicode(s, form="nfd")`.
     *
     * Performs:
     * 1. Replace discouraged/deprecated composed characters
     * 2. Optionally decompose (form="nfd") or compose (form="nfc") the main stack chars
     * 3. Expand U+0F00 (OM syllable)
     * 4. Reorder combining marks
     * 5. Replace ra-subscript (0F6A) with ra (0F62) where not before certain subscripts
     * 6. Fix syllables starting with vowel/subscript
     */
    fun normalizeUnicode(s: String, form: String = "nfd"): String {
        var result = s

        // 1. Replace discouraged/deprecated characters (both forms)
        result = result.replace("\u0F73", "\u0F71\u0F72")  // discouraged
        result = result.replace("\u0F75", "\u0F71\u0F74")  // discouraged
        result = result.replace("\u0F77", "\u0FB2\u0F71\u0F80")  // deprecated
        result = result.replace("\u0F79", "\u0FB3\u0F71\u0F80")  // deprecated
        result = result.replace("\u0F81", "\u0F71\u0F80")  // discouraged

        // 2. NFD / NFC composed character substitutions
        if (form == "nfd") {
            result = result.replace("\u0F43", "\u0F42\u0FB7")
            result = result.replace("\u0F4D", "\u0F4C\u0FB7")
            result = result.replace("\u0F52", "\u0F51\u0FB7")
            result = result.replace("\u0F57", "\u0F56\u0FB7")
            result = result.replace("\u0F5C", "\u0F5B\u0FB7")
            result = result.replace("\u0F69", "\u0F40\u0FB5")
            result = result.replace("\u0F76", "\u0FB2\u0F80")
            result = result.replace("\u0F78", "\u0FB3\u0F80")
            result = result.replace("\u0F93", "\u0F92\u0FB7")
            result = result.replace("\u0F9D", "\u0F9C\u0FB7")
            result = result.replace("\u0FA2", "\u0FA1\u0FB7")
            result = result.replace("\u0FA7", "\u0FA6\u0FB7")
            result = result.replace("\u0FAC", "\u0FAB\u0FB7")
            result = result.replace("\u0FB9", "\u0F90\u0FB5")
        } else {
            result = result.replace("\u0F42\u0FB7", "\u0F43")
            result = result.replace("\u0F4C\u0FB7", "\u0F4D")
            result = result.replace("\u0F51\u0FB7", "\u0F52")
            result = result.replace("\u0F56\u0FB7", "\u0F57")
            result = result.replace("\u0F5B\u0FB7", "\u0F5C")
            result = result.replace("\u0F40\u0FB5", "\u0F69")
            result = result.replace("\u0FB2\u0F80", "\u0F76")
            result = result.replace("\u0FB3\u0F80", "\u0F78")
            result = result.replace("\u0F92\u0FB7", "\u0F93")
            result = result.replace("\u0F9C\u0FB7", "\u0F9D")
            result = result.replace("\u0FA1\u0FB7", "\u0FA2")
            result = result.replace("\u0FA6\u0FB7", "\u0FA7")
            result = result.replace("\u0FAB\u0FB7", "\u0FAC")
            result = result.replace("\u0F90\u0FB5", "\u0FB9")
        }

        // 3. Expand U+0F00 (OM syllable — not a composed char in Unicode by spec, but treated as one)
        result = result.replace("\u0F00", "\u0F68\u0F7C\u0F7E")

        // 4. Reorder combining marks
        result = unicodeReorder(result).first

        // 5. Ra-subscript fix:
        // U+0F6A should become U+0F62 (ར) when NOT followed by one of the permitted subscripts.
        // Python uses a negative lookahead regex; we replicate with a char-by-char scan.
        result = fixRaSubscript(result)

        // 6. Fix invalid start sequences
        result = normalizeInvalidStartString(result)

        return result
    }

    /**
     * Replace U+0F6A with U+0F62 (ར) when it is NOT immediately followed by
     * one of the permitted subjoined consonants.
     *
     * Permitted subscripts (Python regex group): U+0F90–U+0F97, U+0F9A–U+0FAC, U+0FAE–U+0FAF, U+0FB4–U+0FBC
     * Port of Python's: re.sub("\u0f6a(?![\u0f90-\u0f97\u0f9a-\u0fac\u0fae\u0faf\u0fb4-\u0fbc])", "ར", s)
     */
    private fun fixRaSubscript(s: String): String {
        if (!s.contains('\u0F6A')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\u0F6A') {
                val next = s.getOrNull(i + 1)
                sb.append(if (next == null || !isPermittedRaSubscript(next)) '\u0F62' else '\u0F6A')
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun isPermittedRaSubscript(c: Char): Boolean {
        val code = c.code
        return (code in 0x0F90..0x0F97)
                || (code in 0x0F9A..0x0FAC)
                || (code in 0x0FAE..0x0FAF)
                || (code in 0x0FB4..0x0FBC)
    }

    /**
     * Returns true if the character is a Tibetan vowel mark (U+0F71–U+0F84).
     * Port of Python's `is_vowel(char)`.
     */
    fun isVowel(char: Char): Boolean {
        val code = char.code
        return code in 0x0F71..0x0F84
    }

    /**
     * Returns true if the character is a subjoined consonant (U+0F90–U+0FBC).
     * Port of Python's `is_suffix(char)`.
     */
    fun isSuffix(char: Char): Boolean {
        val code = char.code
        return code in 0x0F90..0x0FBC
    }

    /**
     * Fix a string that starts with a vowel or subscript.
     * Port of Python's `normalize_invalid_start_string(s)`.
     */
    fun normalizeInvalidStartString(s: String): String {
        if (s.length < 2) return s
        // If starts with a vowel and second char is not a vowel/subscript → swap
        if (isVowel(s[0]) && !isVowel(s[1]) && !isSuffix(s[1])) {
            return s[1] + s[0].toString() + (if (s.length > 2) s.substring(2) else "")
        }
        // If starts with a subscript → drop it
        if (isSuffix(s[0])) {
            return s.substring(1)
        }
        return s
    }
}
