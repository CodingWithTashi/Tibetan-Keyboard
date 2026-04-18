package com.kharagedition.botok.third_party

/**
 * Port of botok/third_party/has_skrt_syl.py
 *
 * Sanskrit syllable detection using Paul Hackett's Visual Basic regexes.
 * Three regexes cover:
 *   regex1 — Sanskrit vowels, aspirated consonants (gh, dh, bh, dzh), hr/shr, special chars
 *   regex2 — Invalid superscript-subscript pairs not found in standard Tibetan
 *   regex3 — Tsa-phru mark (U+0F39) used in Chinese transliteration
 */
object HasSkrtSyl {

    private val TSEK = '\u0F0B'

    private val regex1 = Regex(
        "([ཀ-ཬཱ-྅ྐ-ྼ]{0,}[ཱཱཱིུ-ཹཻཽ-ྃ][ཀ-ཬཱ-྅ྐ-ྼ]{0,}" +
        "|[ཀ-ཬཱ-྅ྐ-ྼ]{0,}[གཌདབཛྒྜྡྦྫ][ྷ][ཀ-ཬཱ-྅ྐ-ྼ]{0,}" +
        "|[ཀ-ཬཱ-྅ྐ-ྼ]{0,}[ཤཧ][ྲ][ཀ-ཬཱ-྅ྐ-ྼ]{0,}" +
        "|[ཀ-ཬཱ-྅ྐ-ྼ]{0,}[གྷཊ-ཎདྷབྷཛྷཥཀྵ-ཬཱཱཱིུ-ཹཻཽ-ྃྒྷྚ-ྞྡྷྦྷྫྷྵྐྵ-ྼ][ཀ-ཬཱ-྅ྐ-ྼ]{0,})"
    )

    private val regex2 = Regex(
        "([ཀ-ཬཱ-྅ྐ-ྼ]{0,}[ཀཁགང-ཉཏ-དན-བམ-ཛཝ-ཡཤཧཨ][ྐ-ྫྷྮ-ྰྴ-ྼ][ཀ-ཬཱ-྅ྐ-ྼ]{0,})"
    )

    private val regex3 = Regex(
        "([ཀ-ཬཱ-྅ྐ-ྼ]{0,}[༹][ཀ-ཬཱ-྅ྐ-ྼ]{0,})"
    )

    /**
     * Port of Python's is_skrt(syl).
     * Returns true if the syllable matches any of the Sanskrit-indicator regexes.
     */
    fun isSkrt(syl: String): Boolean =
        regex1.containsMatchIn(syl) || regex2.containsMatchIn(syl) || regex3.containsMatchIn(syl)

    /**
     * Port of Python's has_skrt_syl(word).
     * Splits word on tsek (U+0F0B) and checks each syllable with isSkrt().
     */
    fun hasSkrtSyl(word: String): Boolean {
        val syls = word.trim(TSEK).split(TSEK)
        return syls.any { isSkrt(it) }
    }
}
