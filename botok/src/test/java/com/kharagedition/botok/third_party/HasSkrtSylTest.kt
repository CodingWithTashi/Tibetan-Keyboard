package com.kharagedition.botok.third_party

import org.junit.Assert.*
import org.junit.Test

/**
 * Basic tests for Sanskrit syllable detection.
 *
 * Port coverage of Python's third_party/has_skrt_syl.py behavior.
 * Note: No dedicated test file exists in Python tests; these are derived from
 * direct Python execution verification.
 */
class HasSkrtSylTest {

    @Test fun `isSkrt recognizes Sanskrit vowel sign ཽ`() {
        assertTrue(HasSkrtSyl.isSkrt("ཽ"))
    }

    @Test fun `isSkrt recognizes Sanskrit vowel sign ཱ`() {
        assertTrue(HasSkrtSyl.isSkrt("ཱ"))
    }

    @Test fun `isSkrt rejects normal Tibetan vowel ་`() {
        assertFalse(HasSkrtSyl.isSkrt("ོ"))
    }

    @Test fun `isSkrt recognizes Sanskrit vowel combination ་ཽ`() {
        assertTrue(HasSkrtSyl.isSkrt("ོཽ"))
    }

    @Test fun `isSkrt rejects normal Tibetan syllable ཧ`() {
        assertFalse(HasSkrtSyl.isSkrt("ཧ"))
    }

    @Test fun `hasSkrtSyl rejects pure Tibetan word`() {
        assertFalse(HasSkrtSyl.hasSkrtSyl("བོད"))
    }

    @Test fun `hasSkrtSyl detects Sanskrit in mixed word`() {
        assertTrue(HasSkrtSyl.hasSkrtSyl("ཨཽ་"))
    }

    @Test fun `hasSkrtSyl handles empty string`() {
        assertFalse(HasSkrtSyl.hasSkrtSyl(""))
    }

    @Test fun `hasSkrtSyl handles word with tsek only`() {
        assertFalse(HasSkrtSyl.hasSkrtSyl("\u0F0B"))
    }
}
