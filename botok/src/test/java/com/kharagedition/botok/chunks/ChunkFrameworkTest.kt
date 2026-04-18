package com.kharagedition.botok.chunks

import com.kharagedition.botok.ChunkMarkers
import com.kharagedition.botok.textunits.CharCategories
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Port of Python's tests/chunks/test_chunkframework.py
 *
 * Tests all public chunking methods of ChunkFramework.
 * CharCategories must be initialised with the CSV before any BoString is created.
 */
class ChunkFrameworkTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            val csvFile = File("src/main/assets/botok/resources/bo_uni_table.csv")
            val lines = csvFile.readLines(Charsets.UTF_8)
            CharCategories.init(lines)
        }
    }

    // -----------------------------------------------------------------------
    // chunk_bo_chars
    // -----------------------------------------------------------------------

    @Test fun `test bo nonbo`() {
        val string = "བཀྲ་་ཤིས་བདེ་ལེགས། 23PIEIUZLDVéjoldvép«»(\"«»%="
        val cb = ChunkFramework(string)
        val chunks = cb.chunkBoChars()
        val output = cb.getReadable(chunks)

        assertEquals(2, output.size)
        assertEquals("BO", output[0].first)
        assertEquals("བཀྲ་་ཤིས་བདེ་ལེགས། ", output[0].second)
        assertEquals("OTHER", output[1].first)
        assertEquals("23PIEIUZLDVéjoldvép«»(\"«»%=", output[1].second)
    }

    // -----------------------------------------------------------------------
    // chunk_punct
    // -----------------------------------------------------------------------

    @Test fun `test punct nonpunct`() {
        val string = "༆ བཀྲ་ཤིས་བདེ་ལེགས།། །།"
        val cb = ChunkFramework(string)
        val chunks = cb.chunkPunct()
        val output = cb.getReadable(chunks)

        assertEquals(3, output.size)
        assertEquals("PUNCT", output[0].first)
        assertEquals("༆ ", output[0].second)
        assertEquals("NON_PUNCT", output[1].first)
        assertEquals("བཀྲ་ཤིས་བདེ་ལེགས", output[1].second)
        assertEquals("PUNCT", output[2].first)
        assertEquals("།། །།", output[2].second)
    }

    // -----------------------------------------------------------------------
    // chunk_symbol
    // -----------------------------------------------------------------------

    @Test fun `test sym nonsym`() {
        val string = "བཀྲ་ཤིས་བདེ་ལེགས། ༪༫༝༜༛༚༇༆"
        val cb = ChunkFramework(string)
        val chunks = cb.chunkSymbol()
        val output = cb.getReadable(chunks)

        assertEquals(3, output.size)
        assertEquals("NON_SYM", output[0].first)
        assertEquals("བཀྲ་ཤིས་བདེ་ལེགས།", output[0].second)
        assertEquals("SYM", output[1].first)
        assertEquals(" ༪༫༝༜༛༚", output[1].second)
        assertEquals("NON_SYM", output[2].first)
        assertEquals("༇༆", output[2].second)
    }

    // -----------------------------------------------------------------------
    // chunk_number
    // -----------------------------------------------------------------------

    @Test fun `test num nonnum`() {
        val string = "བཀྲ་ཤིས་བདེ་ལེགས།  ༡༢༣༠༩༨"
        val cb = ChunkFramework(string)
        val chunks = cb.chunkNumber()
        val output = cb.getReadable(chunks)

        assertEquals(2, output.size)
        assertEquals("NON_NUM", output[0].first)
        assertEquals("བཀྲ་ཤིས་བདེ་ལེགས།", output[0].second)
        assertEquals("NUM", output[1].first)
        assertEquals("  ༡༢༣༠༩༨", output[1].second)
    }

    // -----------------------------------------------------------------------
    // chunk_spaces
    // -----------------------------------------------------------------------

    @Test fun `test space nonspace`() {
        val string = "བཀྲ་ཤིས་བདེ་ལེགས།   །བཀྲ་ཤིས་བདེ་ལེགས།"
        val cb = ChunkFramework(string)
        val chunks = cb.chunkSpaces()
        val output = cb.getReadable(chunks)

        assertEquals(3, output.size)
        assertEquals("NON_SPACE", output[0].first)
        assertEquals("བཀྲ་ཤིས་བདེ་ལེགས།", output[0].second)
        assertEquals("SPACE", output[1].first)
        assertEquals("   ", output[1].second)
        assertEquals("NON_SPACE", output[2].first)
        assertEquals("།བཀྲ་ཤིས་བདེ་ལེགས།", output[2].second)
    }

    // -----------------------------------------------------------------------
    // syllabify
    // -----------------------------------------------------------------------

    @Test fun `test text syllabify`() {
        val string = "བཀྲ་ཤིས་བདེ་ལེགས"
        val cb = ChunkFramework(string)
        val chunks = cb.syllabify()
        val output = cb.getReadable(chunks)

        assertEquals(4, output.size)
        assertEquals("TEXT", output[0].first); assertEquals("བཀྲ་", output[0].second)
        assertEquals("TEXT", output[1].first); assertEquals("ཤིས་", output[1].second)
        assertEquals("TEXT", output[2].first); assertEquals("བདེ་", output[2].second)
        assertEquals("TEXT", output[3].first); assertEquals("ལེགས", output[3].second)
    }

    // -----------------------------------------------------------------------
    // chunk_latin
    // -----------------------------------------------------------------------

    @Test fun `test latin`() {
        val string = "བཀྲ་ཤིས་བདེ་ལེགས This is a test."
        val cb = ChunkFramework(string)
        val chunks = cb.chunkLatin()
        val output = cb.getReadable(chunks)

        assertEquals(2, output.size)
        assertEquals("OTHER", output[0].first)
        assertEquals("བཀྲ་ཤིས་བདེ་ལེགས", output[0].second)
        assertEquals("LATIN", output[1].first)
        assertEquals(" This is a test.", output[1].second)
    }

    // -----------------------------------------------------------------------
    // chunk_cjk
    // -----------------------------------------------------------------------

    @Test fun `test cjk`() {
        val string = "བཀྲ་ཤིས་བདེ་ལེགས 这是  什么"
        val cb = ChunkFramework(string)
        val chunks = cb.chunkCjk()
        val output = cb.getReadable(chunks)

        assertEquals(2, output.size)
        assertEquals("OTHER", output[0].first)
        assertEquals("བཀྲ་ཤིས་བདེ་ལེགས", output[0].second)
        assertEquals("CJK", output[1].first)
        assertEquals(" 这是  什么", output[1].second)
    }

    // -----------------------------------------------------------------------
    // Full pipeline (mirrors test_full_example)
    // -----------------------------------------------------------------------

    @Test fun `test full example pipeline`() {
        val string = "༆ བཀྲ་ཤིས་བདེ་ལེགས།། །། 23PIEIUZLDVéjoldvép«»(\"«»%= ༪༫༝༜༛༚༇༆ ༡༢༣༠༩༨ " +
                     "This is a test. 这是  什么 กขฃคฅฆงจฉชซฌญฎฏฐฑฒณดตถทธ"
        val cb = ChunkFramework(string)

        // BO / OTHER
        var chunks = cb.chunkBoChars().toMutableList()
        cb.cleanChunks(chunks)
        var output = cb.getReadable(chunks)
        assertEquals(4, output.size)
        assertEquals("BO",    output[0].first)
        assertEquals("OTHER", output[1].first)
        assertEquals("BO",    output[2].first)
        assertEquals("OTHER", output[3].first)

        // BO / PUNCT
        cb.pipeChunk(chunks, cb::chunkPunct, ChunkMarkers.BO, ChunkMarkers.PUNCT)
        cb.cleanChunks(chunks)
        output = cb.getReadable(chunks)
        // Expected: PUNCT("༆ "), BO("བཀྲ་ཤིས་བདེ་ལེགས"), PUNCT("།། །། "),
        //           OTHER(...), BO("༪༫༝༜༛༚"), PUNCT("༇༆ "), BO("༡༢༣༠༩༨ "), OTHER(...)
        assertEquals(8, output.size)
        assertEquals("PUNCT",  output[0].first); assertEquals("༆ ",       output[0].second)
        assertEquals("BO",     output[1].first); assertEquals("བཀྲ་ཤིས་བདེ་ལེགས", output[1].second)
        assertEquals("PUNCT",  output[2].first); assertEquals("།། །། ",   output[2].second)
        assertEquals("OTHER",  output[3].first)
        assertEquals("BO",     output[4].first); assertEquals("༪༫༝༜༛༚",  output[4].second)
        assertEquals("PUNCT",  output[5].first); assertEquals("༇༆ ",      output[5].second)
        assertEquals("BO",     output[6].first); assertEquals("༡༢༣༠༩༨ ", output[6].second)
        assertEquals("OTHER",  output[7].first)

        // BO / NUM
        cb.pipeChunk(chunks, cb::chunkNumber, ChunkMarkers.BO, ChunkMarkers.NUM)
        cb.cleanChunks(chunks)
        output = cb.getReadable(chunks)
        assertEquals(8, output.size)
        assertEquals("NUM", output[6].first); assertEquals("༡༢༣༠༩༨ ", output[6].second)

        // BO / SYM
        cb.pipeChunk(chunks, cb::chunkSymbol, ChunkMarkers.BO, ChunkMarkers.SYM)
        cb.cleanChunks(chunks)
        output = cb.getReadable(chunks)
        assertEquals(8, output.size)
        assertEquals("SYM",  output[4].first); assertEquals("༪༫༝༜༛༚",  output[4].second)

        // TEXT (syllabify BO chunks)
        cb.pipeChunk(chunks, cb::syllabify, ChunkMarkers.BO, ChunkMarkers.TEXT)
        cb.cleanChunks(chunks)
        output = cb.getReadable(chunks)
        // BO("བཀྲ་ཤིས་བདེ་ལེགས") → 4 TEXT syllables; total increases by 3
        assertEquals(11, output.size)
        assertEquals("PUNCT", output[0].first)
        assertEquals("TEXT",  output[1].first); assertEquals("བཀྲ་", output[1].second)
        assertEquals("TEXT",  output[2].first); assertEquals("ཤིས་", output[2].second)
        assertEquals("TEXT",  output[3].first); assertEquals("བདེ་", output[3].second)
        assertEquals("TEXT",  output[4].first); assertEquals("ལེགས", output[4].second)
        assertEquals("PUNCT", output[5].first)

        // OTHER / CJK
        cb.pipeChunk(chunks, cb::chunkCjk, ChunkMarkers.OTHER, ChunkMarkers.CJK)
        cb.cleanChunks(chunks)
        output = cb.getReadable(chunks)
        val cjkEntry = output.find { it.first == "CJK" }
        assertNotNull(cjkEntry)
        assertEquals(" 这是  什么 ", cjkEntry!!.second)

        // OTHER / LATIN
        cb.pipeChunk(chunks, cb::chunkLatin, ChunkMarkers.OTHER, ChunkMarkers.LATIN)
        cb.cleanChunks(chunks)
        output = cb.getReadable(chunks)
        assertTrue(output.any { it.first == "LATIN" && it.second.contains("23PIEIU") })
        assertTrue(output.any { it.first == "LATIN" && it.second == "This is a test." })
    }

    // -----------------------------------------------------------------------
    // TokChunks.getSyls()
    // -----------------------------------------------------------------------

    @Test fun `TokChunks getSyls extracts clean syllables`() {
        val string = "བཀྲ་ཤིས་བདེ་ལེགས།"
        val tc = TokChunks(string)
        val syls = tc.getSyls()

        assertEquals(4, syls.size)
        assertEquals("བཀྲ", syls[0])
        assertEquals("ཤིས", syls[1])
        assertEquals("བདེ", syls[2])
        assertEquals("ལེགས", syls[3])
    }
}
