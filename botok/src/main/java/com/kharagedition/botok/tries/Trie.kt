package com.kharagedition.botok.tries

import com.kharagedition.botok.NAMCHE
import com.kharagedition.botok.NO_POS
import com.kharagedition.botok.TSEK
import com.kharagedition.botok.chunks.TokChunks
import com.kharagedition.botok.textunits.BoSyl
import com.kharagedition.botok.textunits.CharCategories
import java.io.File
import java.nio.charset.Charset

/**
 * Port of botok/tries/trie.py — Trie class.
 *
 * Extends BasicTrie with Tibetan-specific functionality:
 *  - Loads lexicon from TSV files
 *  - Inflects every word with BoSyl.getAllAffixed() and adds all forms to the trie
 *  - Attaches POS/lemma/sense/freq data to each inflected form
 *
 * Note: Python uses pickle for serialization. This port builds the trie fresh every
 * time (acceptable for tests; production can add Kotlinx Serialization caching).
 */
class Trie(
    private val boSyl: BoSyl,
    val profile: String,
    private val mainData: Map<String, List<String>>,
    private val customData: Map<String, List<String>>
) : BasicTrie() {

    /** Cache: avoid re-inflecting the same word string during a single build. */
    private val tmpInflected = mutableMapOf<String, List<Pair<List<String>, Map<String, Any?>?>>?>()

    init {
        // Ensure CharCategories is initialized (required for TokChunks)
        initializeCharCategories()

        head.data["_"] = mutableMapOf<String, Any?>("version" to "0.9.0")
        populateTrie(mainData)
        populateTrie(customData)
        tmpInflected.clear()
    }

    /**
     * Initialize CharCategories from the CSV file if not already initialized.
     * This is needed because getInflected() creates TokChunks which requires CharCategories.
     */
    private fun initializeCharCategories() {
        // Check if already initialized by trying to get a category
        try {
            CharCategories.getCharCategory('ཀ')
            return // Already initialized
        } catch (e: IllegalStateException) {
            // Not initialized, proceed to initialize
        }

        // Load and initialize from CSV file
        val csvFile = File("src/main/assets/botok/resources/bo_uni_table.csv")
        if (csvFile.exists()) {
            val lines = csvFile.readLines(Charsets.UTF_8)
                .drop(1)  // skip header
                .map { it.removePrefix("\uFEFF") }  // strip BOM
                .filter { it.isNotBlank() }
            CharCategories.init(lines)
        }
    }

    // ------------------------------------------------------------------
    // _populate_trie — two-pass file loading
    // ------------------------------------------------------------------

    /**
     * Port of Python's _populate_trie(files).
     *
     * Pass 1: categories starting with "lexica_" (none in current general pack — kept for parity)
     * Pass 2: all other non-"rules" categories ("words", "words_non_inflected", "words_skrt", "remove")
     */
    private fun populateTrie(files: Map<String, List<String>>) {
        // Pass 1 — lexica categories (none in general pack, kept for parity)
        for ((category, filePaths) in files) {
            if (!category.startsWith("lexica")) continue
            for (path in filePaths) addOneFile(path, category)
        }

        // Pass 2 — non-lexica, non-rules categories
        for ((category, filePaths) in files) {
            if (category.startsWith("lexica") || category.startsWith("rules")) continue
            for (path in filePaths) addOneFile(path, category)
        }
    }

    private fun addOneFile(filePath: String, category: String) {
        val lines = readLinesFromFile(filePath)
        for (line in lines) {
            val word = line.split("\t", limit = 2)[0]
            when (category) {
                "words" -> {
                    inflectNModifyTrie(word)
                    inflectNAddData(line)
                }
                "words_non_inflected" -> {
                    addNonInflectible(word)
                    inflectNAddData(line)
                }
                "words_skrt" -> {
                    inflectNModifyTrie(word, skrt = true)
                    inflectNAddData(line)
                }
                "remove" -> {
                    inflectNModifyTrie(line, deactivate = true)
                }
                else -> throw IllegalArgumentException(
                    "'category' is: '$category'. Valid: words, words_skrt, words_non_inflected, remove"
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // add_non_inflectible
    // ------------------------------------------------------------------

    /** Port of Python's add_non_inflectible(). Adds the base form only (no affixation). */
    fun addNonInflectible(word: String) {
        val syls = TokChunks(word).getSyls()
        if (syls.isEmpty()) return
        add(syls)
    }

    // ------------------------------------------------------------------
    // inflect_n_modify_trie
    // ------------------------------------------------------------------

    /**
     * Port of Python's inflect_n_modify_trie(word, deactivate=False, skrt=False).
     * Adds (or deactivates) all affixed forms of word.
     */
    fun inflectNModifyTrie(word: String, deactivate: Boolean = false, skrt: Boolean = false) {
        val inflected = getInflected(word) ?: return
        for ((infl, data) in inflected) {
            if (deactivate) {
                deactivate(infl)
            } else {
                val addData: Map<String, Any?>? = when {
                    skrt && data == null -> mapOf("skrt" to true)
                    skrt && data != null -> data + mapOf("skrt" to true)
                    else -> data
                }
                add(infl, addData)
            }
        }
    }

    // ------------------------------------------------------------------
    // inflect_n_add_data
    // ------------------------------------------------------------------

    /**
     * Port of Python's inflect_n_add_data(line).
     * Parses a TSV line and attaches sense data to all inflected forms.
     */
    fun inflectNAddData(line: String) {
        val (form, pos, lemma, sense, freqStr) = parseLine(line)
        val freq: Int? = freqStr?.toIntOrNull()
        val lemmaJoined: String? = if (lemma != null) {
            val syls = TokChunks(lemma).getSyls()
            if (syls.isNotEmpty()) joinSyls(syls) else null
        } else null

        val inflected = getInflected(form ?: return) ?: return

        for ((infl, affixData) in inflected) {
            val affixed = affixData != null
            val data = buildMap<String, Any?> {
                if (lemmaJoined != null) put("lemma", lemmaJoined)
                if (pos != null) put("pos", pos)
                if (freq != null) put("freq", freq)
                if (sense != null) put("sense", sense)
                put("affixed", affixed)
            }
            addData(infl, data)
        }
    }

    // ------------------------------------------------------------------
    // _get_inflected
    // ------------------------------------------------------------------

    /**
     * Port of Python's _get_inflected(word).
     *
     * Returns a list of (syllableList, affixationData?) pairs:
     *   - First element: the base form (data = null → affixed = false)
     *   - Remaining: each affixed form with its affixation metadata
     *
     * Returns null if word has no Tibetan syllables.
     */
    fun getInflected(word: String): List<Pair<List<String>, Map<String, Any?>?>>? {
        tmpInflected[word]?.let { return it }

        val syls = TokChunks(word).getSyls()
        if (syls.isEmpty()) {
            tmpInflected[word] = null
            return null
        }

        val result = mutableListOf<Pair<List<String>, Map<String, Any?>?>>()
        result.add(Pair(syls, null))  // base form

        val affixed = boSyl.getAllAffixed(syls.last())
        if (affixed != null) {
            for ((inflSyl, meta) in affixed) {
                val inflWord = syls.dropLast(1) + listOf(inflSyl)
                val affixData: Map<String, Any?> = mapOf(
                    "affixation" to mapOf("len" to meta.len, "type" to meta.type, "aa" to meta.aa)
                )
                result.add(Pair(inflWord, affixData))
            }
        }

        tmpInflected[word] = result
        return result
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Port of Python's __join_syls().
     * Joins syllables with TSEK, except those already ending in NAMCHE (visarga).
     */
    private fun joinSyls(syls: List<String>): String =
        syls.joinToString("") { syl ->
            if (syl.endsWith(NAMCHE)) syl else syl + TSEK
        }

    /**
     * Port of Python's __parse_line().
     * Returns [form, pos, lemma, sense, freq] with null for missing/empty fields.
     */
    private fun parseLine(line: String): Array<String?> {
        val fields = arrayOfNulls<String>(5)
        val sep = when {
            '\t' in line -> '\t'
            ',' in line -> ','
            else -> {
                fields[0] = line.trim()
                fields[2] = NO_POS
                return fields
            }
        }
        val cells = line.split(sep)
        for ((num, cell) in cells.withIndex()) {
            if (num >= 5) break
            fields[num] = cell.trim().ifEmpty { null }
        }
        return fields
    }

    /**
     * Reads a TSV file from the filesystem, stripping BOM, comments, and empty lines.
     * Used by JVM tests; Android production should use AssetLoader.readLines() instead.
     */
    private fun readLinesFromFile(filePath: String): List<String> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()
        val result = mutableListOf<String>()
        var first = true
        file.forEachLine(Charsets.UTF_8) { rawLine ->
            var line = rawLine
            if (first) {
                if (line.startsWith("\uFEFF")) line = line.substring(1)
                first = false
            }
            val commentIdx = line.indexOf('#')
            if (commentIdx >= 0) line = line.substring(0, commentIdx)
            line = line.trim()
            if (line.isNotEmpty()) result.add(line)
        }
        return result
    }
}
