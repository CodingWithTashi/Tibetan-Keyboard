package com.kharagedition.botok.textunits

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Port of Python's tests/textunits/test_sylcomponents.py
 *
 * All assert statements from test_components() are ported exactly.
 */
class SylComponentsTest {

    companion object {
        private lateinit var sc: SylComponents

        @JvmStatic
        @BeforeClass
        fun setup() {
            val jsonFile = File("src/main/assets/botok/resources/SylComponents.json")
            val json = jsonFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
            sc = SylComponents(json)
        }
    }

    // -----------------------------------------------------------------------
    // A) getParts()
    // -----------------------------------------------------------------------

    @Test fun `getParts normal syllable returns root and suffix`() {
        // sc.get_parts("བཀྲིས") == ("བཀྲ", "ིས")
        assertEquals(SylComponents.SylParts.Single("བཀྲ", "ིས"), sc.getParts("བཀྲིས"))
    }

    @Test fun `getParts exception syllable returns syl and x`() {
        // sc.get_parts("མདྲོན") == ("མདྲོན", "x")
        assertEquals(SylComponents.SylParts.Single("མདྲོན", "x"), sc.getParts("མདྲོན"))
    }

    @Test fun `getParts invalid syllable returns null`() {
        // sc.get_parts("ཀཀ") is None
        assertNull(sc.getParts("ཀཀ"))
    }

    @Test fun `getParts single-char syllable ང`() {
        // ང is a valid root with no suffix
        val result = sc.getParts("ང")
        assertTrue("Expected Single", result is SylComponents.SylParts.Single)
        assertEquals("ང", (result as SylComponents.SylParts.Single).root)
        assertEquals("", result.suffix)
    }

    // -----------------------------------------------------------------------
    // B) getMingzhi()
    // -----------------------------------------------------------------------

    @Test fun `getMingzhi for བསྒྲུབས`() {
        // sc.get_mingzhi("བསྒྲུབས") == "ྒ"
        assertEquals("ྒ", sc.getMingzhi("བསྒྲུབས"))
    }

    @Test fun `getMingzhi for ཁྱེའུར`() {
        // sc.get_mingzhi("ཁྱེའུར") == "འ"
        assertEquals("འ", sc.getMingzhi("ཁྱེའུར"))
    }

    @Test fun `getMingzhi for dadrag ཀུནད returns ཀ`() {
        // sc.get_mingzhi("ཀུནད") == "ཀ"
        assertEquals("ཀ", sc.getMingzhi("ཀུནད"))
    }

    // -----------------------------------------------------------------------
    // C) normalizeDadrag()
    // -----------------------------------------------------------------------

    @Test fun `normalizeDadrag strips trailing da`() {
        // sc.normalize_dadrag("ཀུནད") == "ཀུན"
        assertEquals("ཀུན", sc.normalizeDadrag("ཀུནད"))
    }

    @Test fun `normalizeDadrag leaves normal syllable unchanged`() {
        assertEquals("བཀྲིས", sc.normalizeDadrag("བཀྲིས"))
    }

    // -----------------------------------------------------------------------
    // D) getInfo()
    // -----------------------------------------------------------------------

    @Test fun `getInfo dadrag for ཀུན`() {
        // sc.get_info("ཀུན") == "dadrag"
        assertEquals("dadrag", sc.getInfo("ཀུན"))
    }

    @Test fun `getInfo thame for དེའིའམ`() {
        // sc.get_info("དེའིའམ") == "thame"
        assertEquals("thame", sc.getInfo("དེའིའམ"))
    }

    @Test fun `getInfo thame for དེའི`() {
        // sc.get_info("དེའི") == "thame"
        assertEquals("thame", sc.getInfo("དེའི"))
    }

    @Test fun `getInfo thame for ང`() {
        // sc.get_info("ང") == "thame"
        assertEquals("thame", sc.getInfo("ང"))
    }

    @Test fun `getInfo returns syllable itself for རྒྱལ`() {
        // sc.get_info("རྒྱལ") == "རྒྱལ"
        assertEquals("རྒྱལ", sc.getInfo("རྒྱལ"))
    }

    // -----------------------------------------------------------------------
    // E) isThame()
    // -----------------------------------------------------------------------

    @Test fun `isThame false for ཀུན`() {
        // sc.is_thame("ཀུན") is False
        assertFalse(sc.isThame("ཀུན"))
    }

    @Test fun `isThame true for དེའིའམ`() {
        // sc.is_thame("དེའིའམ") is True
        assertTrue(sc.isThame("དེའིའམ"))
    }

    @Test fun `isThame true for དེའི`() {
        // sc.is_thame("དེའི") is True
        assertTrue(sc.isThame("དེའི"))
    }

    @Test fun `isThame true for ང`() {
        // sc.is_thame("ང") is True
        assertTrue(sc.isThame("ང"))
    }
}
