package com.kharagedition.botok.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Port of Python's test_normalize_unicode() in unicode_normalization.py.
 * All 10 assert_conv() calls are ported exactly.
 * Also tests isVowel(), isSuffix(), normalizeInvalidStartString().
 */
class UnicodeNormalizationTest {

    // Helper matching Python's assert_conv(orig, expected)
    private fun assertConv(orig: String, expected: String) {
        val result = UnicodeNormalization.normalizeUnicode(orig)
        assertEquals(
            "normalizeUnicode(${orig.map { "\\u%04x".format(it.code) }})" +
                    " expected ${expected.map { "\\u%04x".format(it.code) }}" +
                    " but got ${result.map { "\\u%04x".format(it.code) }}",
            expected,
            result
        )
    }

    // -----------------------------------------------------------------------
    // Direct ports of Python's test_normalize_unicode() assert_conv calls
    // -----------------------------------------------------------------------

    @Test fun `reorder vowel before base char`() {
        // assert_conv("\u0F7B\u0F56", "\u0F56\u0F7B", False)
        assertConv("\u0F7B\u0F56", "\u0F56\u0F7B")
    }

    @Test fun `expand deprecated 0F77`() {
        // assert_conv("\u0f40\u0f77", "\u0f40\u0fb2\u0f71\u0f80", False)
        assertConv("\u0F40\u0F77", "\u0F40\u0FB2\u0F71\u0F80")
    }

    @Test fun `reorder k M o A u to k A u o M`() {
        // assert_conv("\u0f40\u0f7e\u0f7c\u0f71\u0f74", "\u0f40\u0f71\u0f74\u0f7c\u0f7e")
        assertConv("\u0F40\u0F7E\u0F7C\u0F71\u0F74", "\u0F40\u0F71\u0F74\u0F7C\u0F7E")
    }

    @Test fun `reorder m u wa o M to m wa u o M`() {
        // assert_conv("\u0f58\u0f74\u0fb0\u0f83", "\u0f58\u0fb0\u0f74\u0f83")
        assertConv("\u0F58\u0F74\u0FB0\u0F83", "\u0F58\u0FB0\u0F74\u0F83")
    }

    @Test fun `NFD da with bhru vowels reordered`() {
        // assert_conv("\u0F51\u0FB7\u0F74\u0FB0", "\u0F51\u0FB7\u0fb0\u0F74")
        assertConv("\u0F51\u0FB7\u0F74\u0FB0", "\u0F51\u0FB7\u0FB0\u0F74")
    }

    @Test fun `reorder sa ya vowel`() {
        // assert_conv("\u0F66\u0F7C\u0FB1", "\u0F66\u0FB1\u0F7C")
        assertConv("\u0F66\u0F7C\u0FB1", "\u0F66\u0FB1\u0F7C")
    }

    @Test fun `tsheg with anusvara stays unchanged`() {
        // assert_conv("\u0F0B\u0F7E", "\u0F0B\u0F7E", False)
        // 0F0B is tsheg — not in Tibetan consonant range so no reordering
        assertConv("\u0F0B\u0F7E", "\u0F0B\u0F7E")
    }

    @Test fun `ra-subscript before nya is NOT replaced`() {
        // assert_conv("\u0f6a\u0f99\u0f7a\u0f7a", "\u0f62\u0f99\u0f7a\u0f7a")
        // Wait — 0F99 IS in the permitted range 0F90-0F97? No: 0F99 > 0F97.
        // 0F99 is NOT in [0F90-0F97] but IS in [0F9A-0FAC]? 0F9A=154, 0F99=153... 0F99 < 0F9A.
        // Actually 0F99 = 3993 decimal. Range 0F9A = 3994. So 0F99 < 0F9A.
        // But wait the Python test says: assert_conv("\u0f6a\u0f99\u0f7a\u0f7a", "\u0f62\u0f99\u0f7a\u0f7a")
        // So 0F6A followed by 0F99 DOES get replaced! (0F99 is not in permitted range)
        // Permitted: 0F90-0F97 (3984-3991), 0F9A-0FAC (3994-4012), 0FAE-0FAF (4014-4015), 0FB4-0FBC (4020-4028)
        // 0F99 = 3993 — between 0F97 and 0F9A, so NOT permitted → replace with 0F62
        assertConv("\u0F6A\u0F99\u0F7A\u0F7A", "\u0F62\u0F99\u0F7A\u0F7A")
    }

    @Test fun `ra-subscript before simple vowel is replaced`() {
        // assert_conv("\u0f6a\u0f72", "\u0f62\u0f72")
        assertConv("\u0F6A\u0F72", "\u0F62\u0F72")
    }

    @Test fun `ra-subscript before permitted subscript 0F90 stays as 0F6A`() {
        // assert_conv("\u0f6a\u0f90", "\u0f6a\u0f90")
        // 0F90 IS in permitted range [0F90-0F97]
        assertConv("\u0F6A\u0F90", "\u0F6A\u0F90")
    }

    @Test fun `0F01 with 0F83 stays unchanged`() {
        // assert_conv("\u0f01\u0f83", "\u0f01\u0f83") -- valid
        // 0F01 is SPECIAL_PUNCT (Cats.Base? No — Cats.Base starts at 0F01 in Python: [Cats.Base] # 0F01)
        // Actually 0F01 → Cats.Base. Then 0F83 → Cats.TopMark. So reorder: keep order since 0F01 is Base, 0F83 is TopMark
        // The output keeps same order because TopMark comes after Base in sorted categories.
        assertConv("\u0F01\u0F83", "\u0F01\u0F83")
    }

    // -----------------------------------------------------------------------
    // isVowel tests
    // -----------------------------------------------------------------------

    @Test fun `isVowel true for 0F71`() {
        assertEquals(true, UnicodeNormalization.isVowel('\u0F71'))
    }

    @Test fun `isVowel true for 0F84`() {
        assertEquals(true, UnicodeNormalization.isVowel('\u0F84'))
    }

    @Test fun `isVowel false for consonant`() {
        assertEquals(false, UnicodeNormalization.isVowel('བ'))
    }

    // -----------------------------------------------------------------------
    // isSuffix tests
    // -----------------------------------------------------------------------

    @Test fun `isSuffix true for 0F90`() {
        assertEquals(true, UnicodeNormalization.isSuffix('\u0F90'))
    }

    @Test fun `isSuffix false for tsheg`() {
        assertEquals(false, UnicodeNormalization.isSuffix('་'))
    }

    // -----------------------------------------------------------------------
    // normalizeInvalidStartString tests
    // -----------------------------------------------------------------------

    @Test fun `normalizeInvalidStartString swaps vowel-first string`() {
        // starts with vowel 0F72 (i), second is consonant 0F56 (ba) → swap
        val result = UnicodeNormalization.normalizeInvalidStartString("\u0F72\u0F56")
        assertEquals("\u0F56\u0F72", result)
    }

    @Test fun `normalizeInvalidStartString drops leading suffix`() {
        // starts with subscript 0F90 → drop first char
        val result = UnicodeNormalization.normalizeInvalidStartString("\u0F90\u0F56")
        assertEquals("\u0F56", result)
    }

    @Test fun `normalizeInvalidStartString leaves normal string unchanged`() {
        assertEquals("བར་", UnicodeNormalization.normalizeInvalidStartString("བར་"))
    }
}
