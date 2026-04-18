package com.kharagedition.botok.utils

/**
 * Port of botok/utils/lenient_normalization.py - Lenient Normalization
 *
 * Normalization of Old Tibetan shorthands and affix removal
 */
object LenientNormalization {
    // Map of consonants that need 'a' vowel after certain combinations
    private val NEEDS_A = mapOf(
        "ག" to setOf("ཅ", "ཉ", "ཏ", "ད", "ན", "ཙ", "ཞ", "ཟ", "ཡ", "ཤ", "ས"),
        "ད" to setOf("ཀ", "ག", "ང", "པ", "བ", "མ"),
        "བ" to setOf("ཀ", "ག", "ཅ", "ཏ", "ད", "ཙ", "ཞ", "ཟ", "ཤ", "ས"),
        "མ" to setOf("ཁ", "ག", "ང", "ཆ", "ཇ", "ཉ", "ཐ", "ད", "ན", "ཚ", "ཛ"),
        "འ" to setOf("ཁ", "ག", "ཆ", "ཇ", "ཐ", "ད", "ཕ", "བ", "ཚ", "ཛ"),
    )

    // Regex for removing usual suffixes
    private val REMOVE_AFFIXES_RE = Regex("([\u0f40-\u0fbc])(?:འིའོ|འིའམ|འིའང|འོའམ|འོའང|འིས|འི|འོ|འམ|འང|འས|འད|འར)$")

    // Regex for da drag (removing 'ད' after certain consonants)
    private val DA_DRAG_RE = Regex("([^གམ][ནལར])ད$")

    // Normalization of Old Tibetan shorthands
    // Rule 1: Split merged syllables for cases as དྲངསྟེ > དྲངས་ཏེ
    // ([ཀ-ྼ])སྟེ   -> $1ས་
    private val OLD_TIB_P1 = Regex("([ཀ-ྼ])སྟེ")

    // Rule 2: Split merged syllables for cases as གཅལྟོ > གཅལད་ཏོ
    // ([ཀ-ྼ][ནལར])ྟ([ེོ])", "$1་ཏ$2
    private val OLD_TIB_P2 = Regex("([ཀ-ྼ][ནལར])ྟ([ེོ])")

    // Rule 3: Split merged syllables for cases with genitive as གགྀ་ > གག་གྀ་, པགི་ > པག་གི་
    private val OLD_TIB_P3 = Regex("([ཀ-ྼ][ནལར][འིའོའམའང])")

    /**
     * Remove affixes from a syllable.
     *
     * Steps:
     * 1. Remove usual suffixes (འིས, འི, འོ, འམ, འང, འས, འད, འར)
     * 2. Add 'a' vowel (འ) when warranted by NEEDS_A rules
     * 3. Remove unnecessary 'a' suffix
     * 4. Handle special cases: འུར and འུས
     * 5. Apply da drag rule
     *
     * @param s Syllable to process
     * @return Syllable with affixes removed and normalized
     */
    fun removeAffixes(s: String): String {
        var result = s

        // Remove usual suffixes
        val originalLength = result.length
        result = REMOVE_AFFIXES_RE.replace(result, "$1")

        if (result.length != originalLength && result.length > 1) {
            // If a substitution has been made, make sure to add a suffix འ in relevant cases
            if (result.length >= 2) {
                val needsA = NEEDS_A[result[result.length - 2].toString()]
                val lastChar = result[result.length - 1].toString()
                if (needsA != null && lastChar in needsA) {
                    result += "འ"
                }
            }
        }

        // Remove འ suffix when not warranted
        if (result.length > 2 &&
            result.last() == 'འ' &&
            (result.length < 3 ||
             result[result.length - 3].toString() !in NEEDS_A ||
             result[result.length - 2].toString() !in NEEDS_A[result[result.length - 3].toString()]!!)) {
            result = result.dropLast(1)
        }

        // Handle special cases
        result = result.replace("འུར", "འུ")
        result = result.replace("འུས", "འུ")

        // Apply da drag rule
        result = DA_DRAG_RE.replace(result, "$1")

        return result
    }

    /**
     * Normalize Old Tibetan shorthands.
     *
     * Applies rules for splitting merged syllables that are common in Old Tibetan
     * texts but not in Classical Tibetan.
     *
     * @param text Old Tibetan text to normalize
     * @return Normalized text with merged syllables split
     */
    fun normalizeOldTibetan(text: String): String {
        var result = text

        // Rule 1: ([ཀ-ྼ])སྟེ -> $1ས་
        result = OLD_TIB_P1.replace(result, "$1ས་")

        // Rule 2: ([ཀ-ྼ][ནལར])ྟ([ེོ]) -> $1་ཏ$2
        result = OLD_TIB_P2.replace(result, "$1་ཏ$2")

        // Rule 3: Split merged syllables with genitive
        result = OLD_TIB_P3.replace(result, "$1")

        return result
    }

    /**
     * Run sanity checks for lenient normalization functions
     */
    fun runSanityChecks() {
        // Test removeAffixes with simple cases
        val test1 = removeAffixes("བཀྲིས")
        check(test1 == "བཀྲ") {
            "removeAffixes failed for བཀྲིས: got $test1, expected བཀྲ"
        }

        val test2 = removeAffixes("པའིའོ")
        check(test2 == "པ") {
            "removeAffixes failed for པའིའོ: got $test2, expected པ"
        }

        // Test da drag rule
        val test3 = removeAffixes("བཀུནད")
        check(test3 == "བཀུན") {
            "removeAffixes failed for བཀུནད: got $test3, expected བཀུན"
        }

        // Test Old Tibetan normalization
        val test4 = normalizeOldTibetan("དྲངསྟེ")
        check(test4 == "དྲངས་ཏེ") {
            "normalizeOldTibetan failed for དྲངསྟེ: got $test4, expected དྲངས་ཏེ"
        }

        val test5 = normalizeOldTibetan("གཅལྟོ")
        check(test5 == "གཅལ་ཏོ") {
            "normalizeOldTibetan failed for གཅལྟོ: got $test5, expected གཅལ་ཏོ"
        }
    }
}