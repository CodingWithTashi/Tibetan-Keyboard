package com.kharagedition.botok.utils

/**
 * Port of botok/utils/standard_tibetan.py - Standard Tibetan Syllable Validation
 *
 * Standard-Tibetan syllable validation and stack splitting.
 *
 * This is a Kotlin port of isStandardTibetan / nextStackBreak logic
 * from the BDRC lucene-bo library.
 *
 * A syllable is *standard* if its onset (leading consonant cluster) matches one
 * of the entries in _ONSET_SET and the remainder matches a vowel+coda from
 * _VOWEL_CODA_SET (or there is no remainder at all, which is valid).
 *
 * Non-standard syllables are typically Sanskrit transliterations that use
 * additional vowel marks (e.g., U+0F71 ā-chen) not found in Classical Tibetan.
 */
object StandardTibetan {
    // ---------------------------------------------------------------------------
    // Valid onsets (consonant clusters that can begin a standard Tibetan syllable)
    // ---------------------------------------------------------------------------
    private val ONSET_SET = setOf(
        // ka family
        "ཀ", "ཀྱ", "ཀྲ", "ཀླ",
        "དཀ", "དཀྱ", "དཀྲ",
        "བཀ", "བཀྱ", "བཀྲ", "བཀླ",
        "རྐ", "རྐྱ", "ལྐ",
        "སྐ", "སྐྱ", "སྐྲ",
        "བརྐ", "བརྐྱ", "བསྐ", "བསྐྱ", "བསྐྲ",
        // kha family
        "ཁ", "ཁྱ", "ཁྲ",
        "མཁ", "མཁྱ", "མཁྲ",
        "འཁ", "འཁྱ", "འཁྲ",
        // ga family
        "ག", "གྱ", "གྲ", "གླ",
        "དག", "དགྱ", "དགྲ",
        "བག", "བགྱ", "བགྲ",
        "མག", "མགྱ", "མགྲ",
        "འག", "འགྱ", "འགྲ",
        "རྒ", "རྒྱ", "ལྒ",
        "སྒ", "སྒྱ", "སྒྲ",
        "བརྒ", "བརྒྱ", "བསྒ", "བསྒྱ", "བསྒྲ",
        // nga family
        "ང",
        "དང", "མང",
        "རྔ", "ལྔ", "སྔ", "བརྔ", "བསྔ",
        // ca family
        "ཅ", "གཅ", "བཅ", "ལྕ",
        // cha family
        "ཆ", "མཆ", "འཆ",
        // ja family
        "ཇ", "མཇ", "འཇ", "རྗ", "ལྗ", "བརྗ",
        // nya family
        "ཉ", "གཉ", "མཉ", "རྙ", "སྙ", "བརྙ", "བསྙ",
        // ta family
        "ཏ", "གཏ", "བཏ",
        "རྟ", "ལྟ", "སྟ",
        "བརྟ", "བལྟ", "བསྟ",
        // tha family
        "ཐ", "མཐ", "འཐ",
        // da family
        "ད", "དྲ",
        "གད", "བད", "མད",
        "འད", "འདྲ",
        "རྡ", "ལྡ", "སྡ",
        "བརྡ", "བལྡ", "བསྡ",
        // na family
        "ན", "གན", "མན", "རྣ", "སྣ", "བརྣ", "བསྣ",
        // pa family
        "པ", "པྱ", "པྲ",
        "དཔ", "དཔྱ", "དཔྲ",
        "ལྤ",
        "སྤ", "སྤྱ", "སྤྲ",
        // pha family
        "ཕ", "ཕྱ", "ཕྲ",
        "འཕ", "འཕྱ", "འཕྲ",
        // ba family
        "བ", "བྱ", "བྲ", "བླ",
        "དབ", "དབྱ", "དབྲ",
        "འབ", "འབྱ", "འབྲ",
        "རྦ", "ལྦ",
        "སྦ", "སྦྱ", "སྦྲ",
        // ma family
        "མ", "མྱ",
        "དམ", "དམྱ",
        "རྨ", "རྨྱ",
        "སྨ", "སྨྱ",
        // tsa family
        "ཙ", "གཙ", "བཙ", "རྩ", "སྩ", "བརྩ", "བསྩ",
        // tsha family
        "ཚ", "མཚ", "འཚ",
        // dza family
        "ཛ", "མཛ", "འཛ", "རྫ", "བརྫ",
        // wa
        "ཝ",
        // zha family
        "ཞ", "གཞ", "བཞ",
        // za family
        "ཟ", "ཟླ", "གཟ", "བཟ", "བཟླ",
        // 'a
        "འ",
        // ya family
        "ཡ", "གཡ",
        // ra family
        "ར", "རླ", "བརླ",
        // la
        "ལ",
        // sha family
        "ཤ", "གཤ", "བཤ",
        // sa family
        "ས", "སྲ", "སླ", "གས", "བས", "བསྲ", "བསླ",
        // ha family
        "ཧ", "ཧྲ", "ལྷ",
        // a (vowel carrier)
        "ཨ",
        // additional / extended clusters
        "བགླ",
        "མྲ", "སྨྲ",
        "ཏྲ", "ཐྲ",
        "སྣྲ",
        // wa-zur clusters
        "ཀྭ", "བཀྭ", "ཁྭ", "གྭ", "གྲྭ",
        "བཅྭ", "ཉྭ",
        "ཏྭ", "ཐྭ", "དྭ", "དྲྭ",
        "ཕྱྭ", "མྭ",
        "ཙྭ", "རྩྭ", "ཚྭ", "ཛྭ",
        "ཞྭ", "ཟྭ",
        "རྭ", "ལྭ", "ལྷྭ", "ཤྭ",
        "སྟྭ", "སྭ", "བསྭ",
        "ཧྭ",
    )

    // ---------------------------------------------------------------------------
    // Valid vowel + final consonant (coda) suffixes
    // ---------------------------------------------------------------------------
    private val VOWEL_CODA_SET = setOf(
        // no vowel / no coda
        "",
        // coda only (a-vowel implicit)
        "འ", "ག", "གས", "ང", "ངས", "ད", "ན", "བ", "བས", "མ", "མས", "ལ",
        // a-vowel + particle
        "འི", "འིའོ", "འོ", "འང", "འམ", "ར", "ས",
        // i vowel
        "ི",
        "ིག", "ིགས", "ིང", "ིངས", "ིད", "ིན", "ིབ", "ིབས", "ིམ", "ིམས", "ིལ",
        "ིའི", "ིའིའོ", "ིའོ", "ིའང", "ིའམ", "ིར", "ིས",
        // u vowel
        "ུ",
        "ུག", "ུགས", "ུང", "ུངས", "ུད", "ུན", "ུབ", "ུབས", "ུམ", "ུམས", "ུལ",
        "ུའི", "ུའིའོ", "ུའོ", "ུའང", "ུའམ", "ུར", "ུས",
        // e vowel
        "ེ",
        "ེག", "ེགས", "ེང", "ེངས", "ེད", "ེན", "ེབ", "ེབས", "ེམ", "ེམས", "ེལ",
        "ེའི", "ེའིའོ", "ེའོ", "ེའང", "ེའམ", "ེར", "ེས",
        // o vowel
        "ོ",
        "ོག", "ོགས", "ོང", "ོངས", "ོད", "ོན", "ོབ", "ོབས", "ོམ", "ོམས", "ོལ",
        "ོའི", "ོའིའོ", "ོའོ", "ོའང", "ོའམ", "ོར", "ོས",
        // 'u (contracted u, from འུ)
        "འུ", "འུའི", "འུའིའོ", "འུའོ", "འུའང", "འུའམ", "འུར", "འུས",
        // i + 'u combinations
        "ིའུ", "ིའུའི", "ིའུའིའོ", "ིའུའོ", "ིའུའང", "ིའུའམ", "ིའུར", "ིའུས",
        // u + 'u combinations
        "ུའུ", "ུའུའི", "ུའུའིའོ", "ུའུའོ", "ུའུའང", "ུའུའམ", "ུའུར", "ུའུས",
        // e + 'u combinations
        "ེའུ", "ེའུའི", "ེའུའིའོ", "ེའུའོ", "ེའུའང", "ེའུའམ", "ེའུར", "ེའུས",
        // o + 'u combinations
        "ོའུ", "ོའུའི", "ོའུའིའོ", "ོའུའོ", "ོའུའང", "ོའུའམ", "ོའུར", "ོའུས",
    )

    private val MAX_ONSET_LEN = ONSET_SET.maxOfOrNull { it.length } ?: 4
    private val MAX_VCODA_LEN = VOWEL_CODA_SET.maxOfOrNull { it.length } ?: 7

    /**
     * Return end-position of longest prefix of text[start:] that is
     * in candidates, or -1 if no prefix matches.
     *
     * @param text Full text
     * @param start Starting position
     * @param candidates Set of candidate strings
     * @param maxLen Maximum length to check
     * @return End position of longest matching prefix, or -1 if none matches
     */
    private fun findLongestPrefix(text: String, start: Int, candidates: Set<String>, maxLen: Int): Int {
        val end = text.length
        for (length in minOf(maxLen, end - start) downTo 1) {
            if (text.substring(start, start + length) in candidates) {
                return start + length
            }
        }
        return -1
    }

    /**
     * Return true if syllable (without trailing tsheg) is formed
     * according to Standard Tibetan orthographic rules.
     *
     * The algorithm matches Java isStandardTibetan in CommonHelpers:
     * 1. The onset (leading consonant cluster) must be in ONSET_SET.
     * 2. The remainder must be in VOWEL_CODA_SET (including empty string).
     *
     * @param syllable Tibetan syllable to validate (without tsheg)
     * @return true if the syllable is standard Tibetan
     */
    fun isStandardTibetan(syllable: String): Boolean {
        if (syllable.isEmpty()) return false

        val onsetEnd = findLongestPrefix(syllable, 0, ONSET_SET, MAX_ONSET_LEN)
        if (onsetEnd == -1) return false

        if (onsetEnd == syllable.length) return true

        val codaEnd = findLongestPrefix(syllable, onsetEnd, VOWEL_CODA_SET, MAX_VCODA_LEN)
        return codaEnd == syllable.length
    }

    /**
     * Return true for code-points that are part of current stack
     * (i.e., combining / subjoined marks), not a new base consonant.
     *
     * Matches Java keepinstack helper in CommonHelpers.
     *
     * @param cp Unicode code point
     * @return true if the character should be kept in the current stack
     */
    private fun keepInStack(cp: Int): Boolean {
        return (cp in 0x0F71..0x0F87) || (cp in 0x0F8D..0x0FBC) || cp == 0x0F39
    }

    /**
     * Split a Tibetan syllable (without trailing tsheg) into its constituent
     * stacks (akṣaras).
     *
     * Each stack is a base consonant followed by its subjoined consonants and
     * vowel marks. For standard Tibetan syllables this returns a list with a
     * single element; for Sanskrit transliterations it may return multiple.
     *
     * Example:
     *   splitIntoStacks("ཨཱཪྱ")  → ["ཨཱ", "ཪྱ"]
     *
     * @param syllable Tibetan syllable to split (without tsheg)
     * @return List of constituent stacks
     */
    fun splitIntoStacks(syllable: String): List<String> {
        val stacks = mutableListOf<String>()
        var i = 0
        val n = syllable.length

        while (i < n) {
            var j = i + 1
            while (j < n && keepInStack(syllable[j].code)) {
                j++
            }
            stacks.add(syllable.substring(i, j))
            i = j
        }

        return stacks
    }
}