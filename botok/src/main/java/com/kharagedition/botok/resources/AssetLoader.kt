package com.kharagedition.botok.resources

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Opens files from the `assets/botok/` directory.
 * All botok data lives under this prefix to avoid collisions with the host app's assets.
 */
internal object AssetLoader {

    private const val BOTOK_ASSET_PREFIX = "botok"

    /** Open a raw [InputStream] for the given asset path relative to `assets/botok/`. */
    fun open(context: Context, path: String): InputStream =
        context.assets.open("$BOTOK_ASSET_PREFIX/$path")

    /** Read the entire asset file into a [String]. */
    fun readText(context: Context, path: String): String =
        open(context, path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /**
     * Read a TSV/CSV asset line by line.
     * Strips the UTF-8 BOM if present (Python files often have the BOM from `utf-8-sig`).
     * Lines starting with `#` are skipped (comment lines).
     * Empty lines are skipped.
     */
    fun readLines(context: Context, path: String): List<String> {
        val reader = BufferedReader(InputStreamReader(open(context, path), Charsets.UTF_8))
        return reader.use { br ->
            val lines = mutableListOf<String>()
            var first = true
            var line: String?
            while (br.readLine().also { line = it } != null) {
                var l = line!!
                // Strip UTF-8 BOM on first line (matches Python's 'utf-8-sig')
                if (first) {
                    if (l.startsWith("\uFEFF")) l = l.substring(1)
                    first = false
                }
                // Strip inline comments: take only the portion before '#'
                val commentIdx = l.indexOf('#')
                if (commentIdx >= 0) l = l.substring(0, commentIdx)
                l = l.trim()
                if (l.isNotEmpty()) lines.add(l)
            }
            lines
        }
    }

    /**
     * List asset file paths directly under `assets/botok/<dir>` recursively.
     * Returns paths relative to `assets/botok/` (e.g. "general/dictionary/words/tsikchen.tsv").
     *
     * Used by [com.kharagedition.botok.config.Config] to discover TSV files in a dialect pack.
     */
    fun listFiles(context: Context, dir: String): List<String> {
        val results = mutableListOf<String>()
        listFilesRecursive(context, "$BOTOK_ASSET_PREFIX/$dir", dir, results)
        return results
    }

    private fun listFilesRecursive(
        context: Context,
        fullAssetPath: String,
        relativePath: String,
        results: MutableList<String>
    ) {
        val entries = context.assets.list(fullAssetPath) ?: return
        for (entry in entries) {
            val entryFull = "$fullAssetPath/$entry"
            val entryRel = "$relativePath/$entry"
            val children = context.assets.list(entryFull)
            if (children != null && children.isNotEmpty()) {
                // It's a directory — recurse
                listFilesRecursive(context, entryFull, entryRel, results)
            } else {
                // It's a file
                results.add(entryRel)
            }
        }
    }
}
