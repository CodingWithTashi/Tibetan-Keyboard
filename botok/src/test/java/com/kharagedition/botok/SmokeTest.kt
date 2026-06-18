package com.kharagedition.botok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 0 smoke tests.
 * These run on the JVM (not Android device) so we read assets directly from the filesystem
 * using the known project path, since there's no Android Context in unit tests.
 *
 * In Phases 1+ the instrumented tests will use real Context.
 */
class SmokeTest {

    private val assetsRoot = File("src/main/assets/botok")

    @Test
    fun `assets root directory exists`() {
        assertTrue("assets/botok/ must exist", assetsRoot.isDirectory)
    }

    @Test
    fun `bo_uni_table csv is present and non-empty`() {
        val f = File(assetsRoot, "resources/bo_uni_table.csv")
        assertTrue("bo_uni_table.csv missing", f.exists())
        val lines = f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertTrue("bo_uni_table.csv should have > 100 entries", lines.size > 100)
    }

    @Test
    fun `SylComponents json is present and non-empty`() {
        val f = File(assetsRoot, "resources/SylComponents.json")
        assertTrue("SylComponents.json missing", f.exists())
        val content = f.readText(Charsets.UTF_8)
        assertTrue("SylComponents.json should have 'roots' key", content.contains("\"roots\""))
        assertTrue("SylComponents.json should have 'suffixes' key", content.contains("\"suffixes\""))
    }

    @Test
    fun `particles tsv is present`() {
        val f = File(assetsRoot, "resources/particles.tsv")
        assertTrue("particles.tsv missing", f.exists())
        assertTrue("particles.tsv should be non-empty", f.length() > 0)
    }

    @Test
    fun `general dialect pack tsikchen tsv is present and has 31060 entries`() {
        val f = File(assetsRoot, "general/dictionary/words/tsikchen.tsv")
        assertTrue("tsikchen.tsv missing", f.exists())
        val dataLines = f.readLines(Charsets.UTF_8).filter {
            val trimmed = it.removePrefix("\uFEFF").trimStart()
            trimmed.isNotBlank() && !trimmed.startsWith("#")
        }
        // Should have ~31,060 lexicon entries
        assertTrue("tsikchen.tsv should have > 30000 entries, got ${dataLines.size}", dataLines.size > 30_000)
    }

    @Test
    fun `general dialect pack directory structure is correct`() {
        val required = listOf(
            "general/dictionary/words/tsikchen.tsv",
            "general/dictionary/words/exceptions.tsv",
            "general/dictionary/words/ancient.tsv",
            "general/dictionary/words/dagdra.tsv",
            "general/dictionary/words/uncompound_lexicon.tsv",
            "general/dictionary/words_non_inflected/particles.tsv",
            "general/dictionary/rules/rdr_basis.tsv"
        )
        for (path in required) {
            val f = File(assetsRoot, path)
            assertTrue("Missing required asset: $path", f.exists())
        }
    }

    // ------------------------------------------------------------------
    // BotokVars sanity checks
    // ------------------------------------------------------------------

    @Test
    fun `CharMarkers values match Python IntEnum`() {
        assertEquals(1, CharMarkers.CONS)
        assertEquals(4, CharMarkers.TSEK)
        assertEquals(14, CharMarkers.SKRT_LONG_VOW)
        assertEquals(18, CharMarkers.TRANSPARENT)
        assertEquals(19, CharMarkers.NFC)
    }

    @Test
    fun `ChunkMarkers values match Python IntEnum`() {
        assertEquals(100, ChunkMarkers.BO)
        assertEquals(104, ChunkMarkers.TEXT)
        assertEquals(105, ChunkMarkers.PUNCT)
        assertEquals(111, ChunkMarkers.NUM)
        assertEquals(112, ChunkMarkers.NON_NUM)
    }

    @Test
    fun `WordMarkers values match Python IntEnum`() {
        assertEquals(1000, WordMarkers.WORD)
        assertEquals(1001, WordMarkers.NO_POS)
        assertEquals(1002, WordMarkers.NON_WORD)
    }

    @Test
    fun `Tibetan Unicode constants are correct`() {
        assertEquals("\u0F0B", TSEK)
        assertEquals("\u0F7F", NAMCHE)
        assertEquals("\u0F0D", SHAD)
        assertEquals("\u0F60", AA)
    }

    @Test
    fun `DAGDRA list matches Python`() {
        assertEquals(listOf("པ་", "པོ་", "བ་", "བོ་"), DAGDRA)
    }
}
