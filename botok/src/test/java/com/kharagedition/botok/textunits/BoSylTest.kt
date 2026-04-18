package com.kharagedition.botok.textunits

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Port of Python's tests/textunits/test_bosyl.py
 *
 * All assert statements from test_bosyl() are ported exactly.
 */
class BoSylTest {

    companion object {
        private lateinit var bs: BoSyl

        @JvmStatic
        @BeforeClass
        fun setup() {
            val jsonFile = File("src/main/assets/botok/resources/SylComponents.json")
            val json = jsonFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
            bs = BoSyl(json)
        }
    }

    // -----------------------------------------------------------------------
    // isThame() vs isAffixable()
    // -----------------------------------------------------------------------

    @Test fun `ཀུན is not thame and not affixable`() {
        assertFalse(bs.isThame("ཀུན"))
        assertFalse(bs.isAffixable("ཀུན"))
    }

    @Test fun `དེའིའམ is thame but not affixable (already affixed)`() {
        assertTrue(bs.isThame("དེའིའམ"))
        assertFalse(bs.isAffixable("དེའིའམ"))
    }

    @Test fun `དེའི is thame but not affixable (already affixed)`() {
        assertTrue(bs.isThame("དེའི"))
        assertFalse(bs.isAffixable("དེའི"))
    }

    @Test fun `ང is both thame and affixable`() {
        assertTrue(bs.isThame("ང"))
        assertTrue(bs.isAffixable("ང"))
    }

    // -----------------------------------------------------------------------
    // getAllAffixed() — ང (aa=false)
    // -----------------------------------------------------------------------

    @Test fun `getAllAffixed for ང produces 11 forms with aa false`() {
        val affixed = bs.getAllAffixed("ང")
        assertNotNull(affixed)
        assertEquals(
            listOf(
                "ངར"    to BoSyl.AffixMetadata(1, "la",     false),
                "ངས"    to BoSyl.AffixMetadata(1, "gis",    false),
                "ངའི"  to BoSyl.AffixMetadata(2, "gi",     false),
                "ངའམ"  to BoSyl.AffixMetadata(2, "am",     false),
                "ངའང"  to BoSyl.AffixMetadata(2, "ang",    false),
                "ངའོ"  to BoSyl.AffixMetadata(2, "o",      false),
                "ངའིའོ" to BoSyl.AffixMetadata(4, "gi+o",  false),
                "ངའིའམ" to BoSyl.AffixMetadata(4, "gi+am", false),
                "ངའིའང" to BoSyl.AffixMetadata(4, "gi+ang",false),
                "ངའོའམ" to BoSyl.AffixMetadata(4, "o+am",  false),
                "ངའོའང" to BoSyl.AffixMetadata(4, "o+ang", false)
            ),
            affixed
        )
    }

    // -----------------------------------------------------------------------
    // getAllAffixed() — མཐའ (aa=true, trailing འ stripped)
    // -----------------------------------------------------------------------

    @Test fun `getAllAffixed for མཐའ produces 11 forms with aa true`() {
        val affixed = bs.getAllAffixed("མཐའ")
        assertNotNull(affixed)
        assertEquals(
            listOf(
                "མཐར"    to BoSyl.AffixMetadata(1, "la",     true),
                "མཐས"    to BoSyl.AffixMetadata(1, "gis",    true),
                "མཐའི"  to BoSyl.AffixMetadata(2, "gi",     true),
                "མཐའམ"  to BoSyl.AffixMetadata(2, "am",     true),
                "མཐའང"  to BoSyl.AffixMetadata(2, "ang",    true),
                "མཐའོ"  to BoSyl.AffixMetadata(2, "o",      true),
                "མཐའིའོ" to BoSyl.AffixMetadata(4, "gi+o",  true),
                "མཐའིའམ" to BoSyl.AffixMetadata(4, "gi+am", true),
                "མཐའིའང" to BoSyl.AffixMetadata(4, "gi+ang",true),
                "མཐའོའམ" to BoSyl.AffixMetadata(4, "o+am",  true),
                "མཐའོའང" to BoSyl.AffixMetadata(4, "o+ang", true)
            ),
            affixed
        )
    }

    // -----------------------------------------------------------------------
    // getAllAffixed() — non-affixable syllable
    // -----------------------------------------------------------------------

    @Test fun `getAllAffixed for ཀུན returns null`() {
        assertNull(bs.getAllAffixed("ཀུན"))
    }
}
