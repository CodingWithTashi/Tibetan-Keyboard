package com.kharagedition.botok.tries

import com.kharagedition.botok.chunks.TokChunks
import com.kharagedition.botok.config.Config
import com.kharagedition.botok.textunits.BoSyl
import com.kharagedition.botok.textunits.CharCategories
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * Port of Python's tests/tries/test_trie.py
 *
 * Tests build the trie from the bundled assets at src/main/assets/botok/.
 * CharCategories must be initialised before any BoString (and therefore TokChunks) is created.
 */
class TrieTest {

    companion object {
        private lateinit var boSyl: BoSyl
        private lateinit var config: Config

        @JvmStatic
        @BeforeClass
        fun setup() {
            val csvFile = File("src/main/assets/botok/resources/bo_uni_table.csv")
            CharCategories.init(csvFile.readLines(Charsets.UTF_8))

            val jsonFile = File("src/main/assets/botok/resources/SylComponents.json")
            boSyl = BoSyl(jsonFile.readText(Charsets.UTF_8))

            config = Config("src/main/assets/botok/general")
        }

        private fun syls(string: String): List<String> = TokChunks(string).getSyls()
    }

    @Test fun `test inflect_n_modify_trie adds inflected forms`() {
        val trie = Trie(boSyl, "test", emptyMap(), emptyMap())

        // Without inflection: affixed form not found
        trie.add(syls("གྲུབ་མཐའ་"), mapOf("pos" to "NOUN"))
        val r1 = trie.hasWord(syls("གྲུབ་མཐའི་"))
        assertEquals(false, r1["exists"])

        // With inflection: affixed form is found
        trie.inflectNModifyTrie("གྲུབ་མཐའ་")
        val r2 = trie.hasWord(syls("གྲུབ་མཐའི་"))
        assertEquals(true, r2["exists"])

        @Suppress("UNCHECKED_CAST")
        val affixation = (r2["data"] as MutableMap<String, Any?>)["affixation"] as Map<String, Any?>
        assertEquals(2, affixation["len"])
        assertEquals("gi", affixation["type"])
        assertEquals(true, affixation["aa"])
    }

    @Test fun `test inflect_n_modify_trie with skrt=true`() {
        val trie = Trie(boSyl, "test", emptyMap(), emptyMap())
        trie.inflectNModifyTrie("ཀ་ར་", skrt = true)

        val result = trie.hasWord(syls("ཀ་རར་"))
        assertEquals(true, result["exists"])

        @Suppress("UNCHECKED_CAST")
        val data = result["data"] as MutableMap<String, Any?>
        assertEquals(true, data["skrt"])

        @Suppress("UNCHECKED_CAST")
        val affixation = data["affixation"] as Map<String, Any?>
        assertEquals(1, affixation["len"])
        assertEquals("la", affixation["type"])
        assertEquals(false, affixation["aa"])
    }

    @Test fun `test inflect_n_add_data attaches freq`() {
        val trie = Trie(boSyl, "test", emptyMap(), emptyMap())
        trie.inflectNModifyTrie("གྲུབ་མཐའ་")
        trie.inflectNAddData("གྲུབ་མཐའ་\t\t\t\t532")

        val result = trie.hasWord(syls("གྲུབ་མཐའི་"))
        assertEquals(true, result["exists"])

        @Suppress("UNCHECKED_CAST")
        val senses = (result["data"] as MutableMap<String, Any?>)["senses"] as List<Map<String, Any?>>
        assertTrue(senses.isNotEmpty())
        val sense = senses.first()
        assertEquals(532, sense["freq"])
        assertEquals(true, sense["affixed"])
    }

    @Test fun `test deactivate base form`() {
        val trie = Trie(boSyl, "test", emptyMap(), emptyMap())
        trie.inflectNModifyTrie("ཀ་ར་", skrt = true)

        val r1 = trie.hasWord(syls("ཀ་ར་"))
        assertEquals(true, r1["exists"])

        trie.deactivate(syls("ཀ་ར་"))
        val r2 = trie.hasWord(syls("ཀ་ར་"))
        assertEquals(false, r2["exists"])
    }

    @Ignore("Integration test: builds full 31k-word trie from real assets — too slow for unit tests")
    @Test fun `test build from general dictionary`() {
        // Build a trie from the actual bundled lexicon and spot-check a few words
        val trie = Trie(boSyl, "general", config.dictionary, config.adjustments)

        // "ཀ" is in tsikchen.tsv as a DET
        val r = trie.hasWord(listOf("ཀ"))
        assertEquals(true, r["exists"])
    }

    @Test fun `test get_inflected caching`() {
        val trie = Trie(boSyl, "test", emptyMap(), emptyMap())
        val inflected1 = trie.getInflected("གྲུབ་མཐའ་")
        val inflected2 = trie.getInflected("གྲུབ་མཐའ་")
        assertNotNull(inflected1)
        // Should be the same list object (from tmpInflected cache)
        assertSame(inflected1, inflected2)
    }

    @Test fun `test non-tibetan word returns null`() {
        val trie = Trie(boSyl, "test", emptyMap(), emptyMap())
        val result = trie.getInflected("hello")
        assertNull(result)
    }
}
