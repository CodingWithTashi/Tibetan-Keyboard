package com.kharagedition.botok.config

import java.io.File

/**
 * Port of botok/config.py — Config class (filesystem variant for JVM/tests).
 *
 * Scans a dialect pack directory (e.g. `src/main/assets/botok/general/`) and
 * produces two path maps:
 *   dictionary   : category → list of absolute TSV file paths
 *   adjustments  : category → list of absolute TSV file paths
 *
 * Categories are the subdirectory names under `dictionary/` or `adjustments/`:
 *   e.g. "words", "words_non_inflected", "words_skrt", "rules", "remove"
 *
 * This class uses java.io.File and is suitable for JVM unit tests.
 * For Android production use, construct the path maps from AssetLoader instead.
 */
class Config(val packPath: String) {

    val profile: String = packPath.substringAfterLast("/")
    lateinit var dictionary: Map<String, List<String>>
    lateinit var adjustments: Map<String, List<String>>

    init {
        dictionary   = getPackComponent("dictionary")
        adjustments  = getPackComponent("adjustments")
    }

    init {
        dictionary   = getPackComponent("dictionary")
        adjustments  = getPackComponent("adjustments")
    }

    /**
     * Port of Python's _get_pack_component().
     * Returns a map of category (subfolder name) → sorted list of .tsv file paths.
     */
    private fun getPackComponent(name: String): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        val compDir = File("$packPath/$name")
        if (!compDir.isDirectory) return result

        for (subDir in compDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
            val category = subDir.name
            val files = subDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".tsv") }
                .map { it.absolutePath }
                .toList()
            if (files.isNotEmpty()) {
                result.getOrPut(category) { mutableListOf() }.addAll(files)
            }
        }
        return result
    }
}
