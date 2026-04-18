package com.kharagedition.botok.textunits

import com.kharagedition.botok.CharMarkers
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Tests for CharCategories — port of Python's charcategories.py logic.
 * Reads bo_uni_table.csv from the local assets folder (JVM test, no Android Context).
 */
class CharCategoriesTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            val csvFile = File("src/main/assets/botok/resources/bo_uni_table.csv")
            val lines = csvFile.readLines(Charsets.UTF_8)
                .drop(1)  // skip header
                .map { it.removePrefix("\uFEFF") }  // strip BOM
                .filter { it.isNotBlank() }
            CharCategories.init(lines)
        }
    }

    // -- Tibetan consonants --------------------------------------------------

    @Test fun `tibetan consonant Ba is CONS`() {
        assertEquals(CharMarkers.CONS, CharCategories.getCharCategory('བ'))
    }

    @Test fun `tibetan sub-consonant ra is SUB_CONS`() {
        assertEquals(CharMarkers.SUB_CONS, CharCategories.getCharCategory('ྲ'))
    }

    @Test fun `tibetan vowel i is VOW`() {
        assertEquals(CharMarkers.VOW, CharCategories.getCharCategory('ི'))
    }

    @Test fun `tsheg is TSEK`() {
        assertEquals(CharMarkers.TSEK, CharCategories.getCharCategory('་'))
    }

    // -- Numerals ------------------------------------------------------------

    @Test fun `tibetan digit 1 is NUMERAL`() {
        assertEquals(CharMarkers.NUMERAL, CharCategories.getCharCategory('༡'))
    }

    @Test fun `tibetan digit 9 is NUMERAL`() {
        assertEquals(CharMarkers.NUMERAL, CharCategories.getCharCategory('༩'))
    }

    // -- Punctuation ---------------------------------------------------------

    @Test fun `shad is NORMAL_PUNCT`() {
        assertEquals(CharMarkers.NORMAL_PUNCT, CharCategories.getCharCategory('།'))
    }

    @Test fun `special punct gter yig is SPECIAL_PUNCT`() {
        assertEquals(CharMarkers.SPECIAL_PUNCT, CharCategories.getCharCategory('༁'))
    }

    // -- Non-Tibetan scripts -------------------------------------------------

    @Test fun `latin t is LATIN`() {
        assertEquals(CharMarkers.LATIN, CharCategories.getCharCategory('t'))
    }

    @Test fun `latin r is LATIN`() {
        assertEquals(CharMarkers.LATIN, CharCategories.getCharCategory('r'))
    }

    @Test fun `CJK char is CJK`() {
        assertEquals(CharMarkers.CJK, CharCategories.getCharCategory('就'))
    }

    @Test fun `CJK char 2 is CJK`() {
        assertEquals(CharMarkers.CJK, CharCategories.getCharCategory('郊'))
    }

    // -- Transparent (spaces) -----------------------------------------------

    @Test fun `space is TRANSPARENT`() {
        assertEquals(CharMarkers.TRANSPARENT, CharCategories.getCharCategory(' '))
    }

    @Test fun `tab is TRANSPARENT`() {
        assertEquals(CharMarkers.TRANSPARENT, CharCategories.getCharCategory('\t'))
    }

    @Test fun `newline is TRANSPARENT`() {
        assertEquals(CharMarkers.TRANSPARENT, CharCategories.getCharCategory('\n'))
    }

    // -- NFC -----------------------------------------------------------------

    @Test fun `NFC OM char is NFC category`() {
        // U+0F00 is ༀ (Tibetan OM syllable) — marked NFC in table
        assertEquals(CharMarkers.NFC, CharCategories.getCharCategory('\u0F00'))
    }

    // -- OTHER ---------------------------------------------------------------

    @Test fun `arbitrary other char is OTHER`() {
        // Thai script — not Tibetan, not Latin, not CJK
        assertEquals(CharMarkers.OTHER, CharCategories.getCharCategory('ก'))
    }
}
