package com.kharagedition.botok.utils

import java.io.BufferedReader
import java.io.StringReader

/**
 * Port of botok/utils/helpers.py - Helpers
 *
 * Utility functions for text processing and file handling
 */
object Helpers {
    /**
     * Remove comments from a file line by line
     *
     * @param file BufferedReader to read from
     * @return Sequence of non-empty, uncommented lines
     */
    fun decommentFile(file: BufferedReader): Sequence<String> = sequence {
        file.useLines { lines ->
            for (row in lines) {
                val raw = row.split("#")[0].trim()
                if (raw.isNotEmpty()) {
                    yield(raw)
                }
            }
        }
    }

    /**
     * Remove comments from a string
     *
     * @param content String content to process
     * @return Sequence of non-empty, uncommented lines
     */
    fun decommentContent(content: String): Sequence<String> = sequence {
        val reader = BufferedReader(StringReader(content))
        reader.useLines { lines ->
            for (row in lines) {
                val raw = row.split("#")[0].trim()
                if (raw.isNotEmpty()) {
                    yield(raw)
                }
            }
        }
    }

    /**
     * Read a TSV file and return as list of string arrays
     *
     * @param content TSV file content
     * @return List of string arrays, one per row
     */
    fun readTsv(content: String): List<Array<String>> {
        return content.lineSequence()
            .filter { it.isNotBlank() }
            .map { it.split("\t").toTypedArray() }
            .toList()
    }

    /**
     * Read a TSV file and return as list of lists
     *
     * @param content TSV file content
     * @return List of string lists, one per row
     */
    fun readTsvAsList(content: String): List<List<String>> {
        return content.lineSequence()
            .filter { it.isNotBlank() }
            .map { it.split("\t") }
            .toList()
    }
}