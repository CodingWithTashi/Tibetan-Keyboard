package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.AA
import com.kharagedition.botok.TSEK

/**
 * Port of botok/tokenizers/token.py
 *
 * Token represents a single tokenized unit with rich metadata.
 * Supports both property-style (token.text) and map-style (token["text"]) access.
 */
class Token {
    var text: String = ""
    var charTypes: List<String> = emptyList()
    var hasMergedDagdra: Boolean? = null
    var lemma: String = ""
    var sense: String = ""
    var chunkType: String? = null
    var start: Int = 0
    var len: Int? = null
    var sylsIdx: List<List<Int>>? = null
    var sylsStartEnd: List<Map<String, Int>>? = null
    var pos: String = ""
    var affixation: Map<String, Any?> = emptyMap()
    var senses: MutableList<Map<String, Any?>>? = null
    var affix: Boolean = false
    var affixHost: Boolean = false
    var formFreq: Int? = null
    var freq: Int? = null
    var skrt: Boolean = false
    val customData: MutableMap<String, Any?> = mutableMapOf() // corresponds to Token._ in Python

    /**
     * Syllables extracted from text using sylsIdx.
     * Returns list of syllables, each being a list of characters.
     */
    val syls: List<List<String>>
        get() {
            return sylsIdx?.map { sylIdx ->
                sylIdx.map { text[it].toString() }
            } ?: emptyList()
        }

    /**
     * Syllables as joined strings (for debugging)
     */
    val sylsJoined: List<String>
        get() = syls.map { it.joinToString("") }

    /**
     * Text with TSEK appended to every syllable except those hosting affixes.
     */
    val textCleaned: String
        get() {
            if (syls.isEmpty()) return ""

            val cleaned = sylsJoined.joinToString(TSEK)
            return if (affixHost && !affix) {
                cleaned
            } else {
                cleaned + TSEK
            }
        }

    /**
     * Text with affixes removed (stripped from end if affixation present).
     * Restores AA vowel if it was removed by affixation.
     */
    val textUnaffixed: String
        get() {
            var unaffixed = if (syls.isNotEmpty()) {
                sylsJoined.joinToString(TSEK)
            } else {
                ""
            }

            // Remove affix if present
            if (affixation.isNotEmpty() &&
                !affix &&
                "len" in affixation) {
                val hasAffixedSense = senses?.any { it["affixed"] == true } ?: false
                if (hasAffixedSense) {
                    val lenToRemove = affixation["len"] as Int
                    unaffixed = unaffixed.dropLast(lenToRemove)

                    // Restore AA vowel if needed
                    if (unaffixed.isNotEmpty() &&
                        "aa" in affixation &&
                        affixation["aa"] == true) {
                        unaffixed += AA
                    }
                }
            }

            return when {
                affixation.isNotEmpty() && affixHost && !affix -> unaffixed
                unaffixed.isNotEmpty() -> unaffixed + TSEK
                else -> ""
            }
        }

    /**
     * Map-style access: token["text"] equivalent to token.text
     */
    operator fun get(attr: String): Any? {
        return when (attr) {
            "text" -> text
            "char_types" -> charTypes
            "has_merged_dagdra" -> hasMergedDagdra
            "lemma" -> lemma
            "sense" -> sense
            "chunk_type" -> chunkType
            "start" -> start
            "len" -> len
            "syls_idx" -> sylsIdx
            "syls_start_end" -> sylsStartEnd
            "pos" -> pos
            "affixation" -> affixation
            "senses" -> senses
            "affix" -> affix
            "affix_host" -> affixHost
            "form_freq" -> formFreq
            "freq" -> freq
            "skrt" -> skrt
            "_" -> customData
            else -> throw AttributeError("Token does not have attribute: $attr")
        }
    }

    /**
     * Map-style setting: token["pos"] = "NOUN"
     * Enforces that only existing attributes can be set (except for "_" which accepts dict updates)
     */
    operator fun set(key: String, value: Any?) {
        when (key) {
            "text" -> text = value as String
            "char_types" -> charTypes = value as List<String>
            "has_merged_dagdra" -> hasMergedDagdra = value as Boolean?
            "lemma" -> lemma = value as String
            "sense" -> sense = value as String
            "chunk_type" -> chunkType = value as String?
            "start" -> start = value as Int
            "len" -> len = value as Int?
            "syls_idx" -> sylsIdx = value as List<List<Int>>?
            "syls_start_end" -> sylsStartEnd = value as List<Map<String, Int>>?
            "pos" -> pos = value as String
            "affixation" -> affixation = value as Map<String, Any?>
            "senses" -> {
                @Suppress("UNCHECKED_CAST")
                senses = value as MutableList<Map<String, Any?>>?
            }
            "affix" -> affix = value as Boolean
            "affix_host" -> affixHost = value as Boolean
            "form_freq" -> formFreq = value as Int?
            "freq" -> freq = value as Int?
            "skrt" -> skrt = value as Boolean
            "_" -> {
                if (value !is Map<*, *>) {
                    throw TypeError("Only dicts are accepted for Token._")
                }
                @Suppress("UNCHECKED_CAST")
                customData.putAll(value as Map<String, Any?>)
            }
            else -> throw AttributeError("Token objects don't have $key as attribute")
        }
    }

    override fun toString(): String {
        val out = StringBuilder()
        out.appendLine("text: \"$text\"")

        if (textCleaned.isNotEmpty()) {
            out.appendLine("text_cleaned: \"$textCleaned\"")
        }
        if (textUnaffixed.isNotEmpty()) {
            out.appendLine("text_unaffixed: \"$textUnaffixed\"")
        }
        if (syls.isNotEmpty()) {
            val sylsStr = syls.joinToString("\", \"") { it.joinToString("") }
            out.appendLine("syls: [\"$sylsStr\"]")
        }
        if (pos.isNotEmpty()) {
            out.appendLine("pos: $pos")
        }
        if (lemma.isNotEmpty()) {
            out.appendLine("lemma: $lemma")
        }
        if (sense.isNotEmpty()) {
            out.appendLine("sense: $sense")
        }
        if (senses != null && senses!!.isNotEmpty()) {
            val sensesStr = senses!!.joinToString(" | ") { m ->
                m.entries.joinToString(", ") { (k, v) -> "$k: $v" }
            }
            out.appendLine("senses: | $sensesStr |")
        }
        if (charTypes.isNotEmpty()) {
            out.appendLine("char_types: |${charTypes.joinToString("|")}|")
        }
        if (chunkType != null) {
            out.appendLine("chunk_type: $chunkType")
        }
        if (formFreq != null) {
            out.appendLine("form_freq: $formFreq")
        }
        if (freq != null) {
            out.appendLine("freq: $freq")
        }
        if (skrt) {
            out.appendLine("skrt: $skrt")
        }
        if (affix) {
            out.appendLine("affix: $affix")
        }
        if (affixHost) {
            out.appendLine("affix_host: $affixHost")
        }
        if (hasMergedDagdra != null) {
            out.appendLine("has_merged_dagdra: $hasMergedDagdra")
        }
        if (sylsIdx != null) {
            out.appendLine("syls_idx: $sylsIdx")
        }
        if (sylsStartEnd != null) {
            out.appendLine("syls_start_end: $sylsStartEnd")
        }
        out.appendLine("start: $start")
        out.appendLine("len: $len")

        if (customData.isNotEmpty()) {
            out.appendLine()
            for ((k, v) in customData) {
                out.appendLine("_$k: $v")
            }
        }
        out.appendLine()

        return out.toString().trimEnd() + "\n\n"
    }
}

// Custom exceptions to match Python behavior
class AttributeError(message: String) : Exception(message)
class TypeError(message: String) : Exception(message)
