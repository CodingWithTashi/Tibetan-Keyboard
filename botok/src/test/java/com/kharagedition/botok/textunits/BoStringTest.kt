package com.kharagedition.botok.textunits

import com.kharagedition.botok.CharMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Port of Python's tests/textunits/test_bostring.py
 *
 * Input string: "བཀྲ་ཤིས་ ༡༢༣ tr  就到 郊外玩བདེ་ལེགས།"
 *
 * Index reference (same as Python test):
 *   0  = བ  (CONS)
 *   1  = ཀ  (CONS)
 *   2  = ྲ  (SUB_CONS)
 *   3  = ་  (TSEK)
 *   4  = ཤ  (CONS)
 *   5  = ི  (VOW)
 *   6  = ས  (CONS)
 *   7  = ་  (TSEK)
 *   8  = ' ' (TRANSPARENT)
 *   9  = ༡  (NUMERAL)
 *   10 = ༢  (NUMERAL)
 *   11 = ༣  (NUMERAL)
 *   12 = ' ' (TRANSPARENT)
 *   13 = t  (LATIN)
 *   14 = r  (LATIN)
 *   15 = ' ' (TRANSPARENT)
 *   16 = ' ' (TRANSPARENT)
 *   17 = 就 (CJK)
 *   ...
 */
class BoStringTest {

    companion object {
        private val BO_STR = "བཀྲ་ཤིས་ ༡༢༣ tr  就到 郊外玩བདེ་ལེགས།"

        @JvmStatic
        @BeforeClass
        fun setup() {
            val csvFile = File("src/main/assets/botok/resources/bo_uni_table.csv")
            val lines = csvFile.readLines(Charsets.UTF_8)
                .drop(1)
                .map { it.removePrefix("\uFEFF") }
                .filter { it.isNotBlank() }
            CharCategories.init(lines)

            // Silence NFC warnings during tests so output stays clean
            BoString.nfcWarningListener = null
        }
    }

    // -- test_string() port --------------------------------------------------

    @Test fun `idx0 Ba is CONS`() {
        val bs = BoString(BO_STR)
        assertEquals('བ', BO_STR[0])
        assertEquals(CharMarkers.CONS, bs.baseStructure[0])
    }

    @Test fun `idx2 ra-sub is SUB_CONS`() {
        val bs = BoString(BO_STR)
        assertEquals('ྲ', BO_STR[2])
        assertEquals(CharMarkers.SUB_CONS, bs.baseStructure[2])
    }

    @Test fun `idx7 tsheg is TSEK`() {
        val bs = BoString(BO_STR)
        assertEquals('་', BO_STR[7])
        assertEquals(CharMarkers.TSEK, bs.baseStructure[7])
    }

    @Test fun `idx9 tibetan digit 1 is NUMERAL`() {
        val bs = BoString(BO_STR)
        assertEquals('༡', BO_STR[9])
        assertEquals(CharMarkers.NUMERAL, bs.baseStructure[9])
    }

    @Test fun `idx13 latin t is LATIN`() {
        val bs = BoString(BO_STR)
        assertEquals('t', BO_STR[13])
        assertEquals(CharMarkers.LATIN, bs.baseStructure[13])
    }

    @Test fun `idx17 CJK char is CJK`() {
        val bs = BoString(BO_STR)
        assertEquals('就', BO_STR[17])
        assertEquals(CharMarkers.CJK, bs.baseStructure[17])
    }

    // -- test_warning() port -------------------------------------------------

    @Test fun `NFC char triggers warning callback`() {
        val warnings = mutableListOf<String>()
        BoString.nfcWarningListener = { msg -> warnings.add(msg) }
        try {
            BoString("ༀ་པ་ཊུ་")
            assertEquals("Should have emitted exactly 1 NFC warning", 1, warnings.size)
            assertTrue(
                "Warning message should mention the NFC char ༀ",
                warnings[0].contains("ༀ")
            )
        } finally {
            BoString.nfcWarningListener = null
        }
    }

    // -- exportGroups() tests ------------------------------------------------

    @Test fun `exportGroups with forSubstring=true re-indexes from 0`() {
        val bs = BoString(BO_STR)
        // Python: bs.export_groups(2, 5) → {0:1, 1:2, 2:4, 3:1, 4:3}
        // chars at 2..6: ྲ(SUB_CONS=2), ་(TSEK=4), ཤ(CONS=1), ི(VOW=3), ས(CONS=1)
        val groups = bs.exportGroups(startIdx = 2, sliceLen = 5, forSubstring = true)
        assertEquals(mapOf(0 to CharMarkers.SUB_CONS, 1 to CharMarkers.TSEK,
            2 to CharMarkers.CONS, 3 to CharMarkers.VOW, 4 to CharMarkers.CONS), groups)
    }

    @Test fun `exportGroups with forSubstring=false preserves original indices`() {
        val bs = BoString(BO_STR)
        // Python: bs.export_groups(2, 5, for_substring=False) → {2:1,3:4,4:1,5:3,6:1}  (wait - index 4=ཤ=CONS)
        val groups = bs.exportGroups(startIdx = 2, sliceLen = 5, forSubstring = false)
        assertEquals(
            mapOf(2 to CharMarkers.SUB_CONS, 3 to CharMarkers.TSEK,
                4 to CharMarkers.CONS, 5 to CharMarkers.VOW, 6 to CharMarkers.CONS),
            groups
        )
    }

    // -- getCategories() tests -----------------------------------------------

    @Test fun `getCategories returns human-readable names`() {
        val bs = BoString("བར་")
        val cats = bs.getCategories()
        assertEquals("CONS", cats[0])   // བ
        assertEquals("CONS", cats[1])   // ར
        assertEquals("TSEK", cats[2])   // ་
    }

    // -- ignoreChars tests ---------------------------------------------------

    @Test fun `ignored chars are classified as TRANSPARENT`() {
        val ignore = setOf('་')
        val bs = BoString("བར་", ignoreChars = ignore)
        // Tsheg is in ignoreChars → should become TRANSPARENT
        assertEquals(CharMarkers.TRANSPARENT, bs.baseStructure[2])
        // Regular cons should still be CONS
        assertEquals(CharMarkers.CONS, bs.baseStructure[0])
    }

    // -- baseStructure length matches string length --------------------------

    @Test fun `baseStructure length equals string length`() {
        val bs = BoString(BO_STR)
        assertEquals(BO_STR.length, bs.baseStructure.size)
        assertEquals(BO_STR.length, bs.len)
    }
}
