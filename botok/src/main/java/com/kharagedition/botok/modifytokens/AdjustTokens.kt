package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.tokenizers.Token
import com.kharagedition.botok.resources.AssetLoader
import java.io.BufferedReader
import java.io.StringReader

/**
 * Port of botok/modifytokens/adjusttokens.py — AdjustTokens
 *
 * Syntax for the .tsv adjustment rules
 * ===================================
 * - each rule should be as follows: "<matchcql>\t<index>\t<operation>\t<replacecql>"
 * - comments with # and empty lines are allowed
 * - CQL rules: "<text>" can be used without specifying that there is "text_cleaned="
 * - Index format: either "<matching_index>" or "<matching_index>-<splitting-index>"
 * - Adjustment format:
 *         - "+" for merge
 *         - ":" for split (default: syllable mode)
 *         - "::" for split in character mode
 *         - "=" for replace
 * - Constraint: "<matching_index>-<splitting-index>" is only allowed if adjustment is ":" or "::"
 */
class AdjustTokens(
    private val mainRules: List<String>? = null,
    private val customRules: List<String>? = null
) {

    private val rules: MutableList<Rule> = mutableListOf()

    init {
        parseRules()
    }

    /**
     * Apply adjustment rules to the token list.
     * @param tokenList list of Token objects
     * @return modified token list
     */
    fun adjust(tokenList: List<Token>): List<Token> {
        var workingList = tokenList.map { it.toMutableMap() }

        for (rule in rules) {
            when (rule.operation) {
                Operation.SPLIT -> {
                    if (rule.matchIdx <= noTokenMatched(rule.matchCql)) {
                        val sm = SplittingMatcher(
                            rule.matchCql,
                            rule.matchIdx,
                            rule.splitIdx ?: 1,
                            workingList,
                            rule.replaceCql.toAttrMap()
                        )
                        val mode = if (rule.splitMode == SplitMode.CHARACTER) "char" else "syl"
                        workingList = sm.splitOnMatches(mode = mode).map { it.toMutableMap() }
                    } else {
                        println("[ERROR]: No token to split with token number ${rule.matchIdx} found in rule $rule")
                    }
                }
                Operation.MERGE -> {
                    if (rule.matchIdx < noTokenMatched(rule.matchCql)) {
                        val mm = MergingMatcher(
                            rule.matchCql,
                            rule.matchIdx,
                            workingList,
                            rule.replaceCql.toAttrMap()
                        )
                        workingList = mm.mergeOnMatches().map { it.toMutableMap() }
                    } else {
                        println("[ERROR]: No token to merge with token number ${rule.matchIdx} found in rule $rule")
                    }
                }
                Operation.REPLACE -> {
                    val rm = ReplacingMatcher(
                        rule.matchCql,
                        rule.matchIdx,
                        workingList,
                        rule.replaceCql
                    )
                    rm.replaceOnMatches()
                }
            }
        }

        return workingList.map { mapToToken(it) }
    }

    private fun noTokenMatched(matchCql: String): Int {
        // Count the number of token expressions in the CQL query
        val matchedTokens = matchCql.split(Regex("(\\[.+?\\])")).filter { it.isNotBlank() && it != " " }
        return matchedTokens.size
    }

    private fun parseRules() {
        val allRules = mutableListOf<String>()
        customRules?.let { allRules.addAll(it) }
        mainRules?.let { allRules.addAll(it) }

        // Sort rules before applying them
        allRules.sort()

        for (ruleContent in allRules) {
            val reader = BufferedReader(StringReader(ruleContent))
            val lines = decommentFile(reader)

            for (line in lines) {
                if (line.isBlank()) continue

                val parts = line.split("\t")
                if (parts.size == 4) {
                    val rule = parseRule(parts)
                    rules.add(rule)
                }
            }
        }
    }

    private fun decommentFile(reader: BufferedReader): List<String> {
        val lines = mutableListOf<String>()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                lines.add(trimmed)
            }
        }

        return lines
    }

    data class Rule(
        val matchCql: String,
        val matchIdx: Int,
        val splitIdx: Int? = null,
        val operation: Operation,
        val splitMode: SplitMode? = null,
        val replaceCql: String
    )

    enum class Operation { SPLIT, MERGE, REPLACE }
    enum class SplitMode { SYLLABLE, CHARACTER }

    companion object {
        fun parseRule(rule: List<String>): Rule {
            val idxSep = "-"

            // Sanity checks
            require(rule.size == 4) { "There can't be more than three columns per rule." }
            require(rule[1].isNotEmpty()) { "There needs to be an index for every rule." }

            require(!(idxSep in rule[1] && rule[2] !in listOf(":", "::"))) {
                "The double index is only intended for split adjustments."
            }

            require(rule[2] in listOf("+", "=", ":", "::")) {
                "The supported operations are either of [\"+\", \"=\", \"::\"]."
            }

            // Parse
            val matchIdx: Int
            val splitIdx: Int?
            val operation: Operation
            val splitMode: SplitMode?
            val replaceCql = rule[3]

            if (idxSep in rule[1]) {
                val parts = rule[1].split("-")
                matchIdx = parts[0].toInt()
                splitIdx = parts[1].toInt()
            } else {
                matchIdx = rule[1].toInt()
                splitIdx = null
            }

            when (rule[2]) {
                "=" -> {
                    operation = Operation.REPLACE
                    splitMode = null
                }
                "+" -> {
                    operation = Operation.MERGE
                    splitMode = null
                }
                ":" -> {
                    operation = Operation.SPLIT
                    splitMode = SplitMode.SYLLABLE
                }
                "::" -> {
                    operation = Operation.SPLIT
                    splitMode = SplitMode.CHARACTER
                }
                else -> throw IllegalArgumentException("Unknown operation: ${rule[2]}")
            }

            return Rule(
                matchCql = rule[0],
                matchIdx = matchIdx,
                splitIdx = splitIdx,
                operation = operation,
                splitMode = splitMode,
                replaceCql = replaceCql
            )
        }
    }
}

/**
 * Helper functions for token conversion
 */
private fun String.toAttrMap(): Map<String, Any> {
    // Simple parsing for attribute replacement
    // Format: [attr1="value1" & attr2="value2"]
    val result = mutableMapOf<String, Any>()

    // Remove brackets and split by &
    val content = this.removeSurrounding("[", "]")
    val parts = content.split("&")

    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.isNotEmpty()) {
            val eqIndex = trimmed.indexOf("=")
            if (eqIndex != -1) {
                val key = trimmed.substring(0, eqIndex).trim()
                val value = trimmed.substring(eqIndex + 1).trim().removeSurrounding("\"")
                result[key] = value
            }
        }
    }

    return result
}

private fun Token.toMutableMap(): MutableMap<String, Any> {
    return mutableMapOf(
        "text" to this.text,
        "pos" to this.pos,
        "lemma" to this.lemma,
        "sense" to this.sense,
        "affix" to this.affix,
        "affixHost" to this.affixHost,
        "chunkType" to (this.chunkType ?: ""),
        "start" to this.start,
        "len" to (this.len ?: 0),
        "skrt" to this.skrt,
        "hasMergedDagdra" to (this.hasMergedDagdra ?: false)
    )
}

private fun mapToToken(map: Map<String, Any>): Token {
    val token = Token()
    token.text = map["text"] as? String ?: ""
    token.pos = map["pos"] as? String ?: ""
    token.lemma = map["lemma"] as? String ?: ""
    token.sense = map["sense"] as? String ?: ""
    token.affix = map["affix"] as? Boolean ?: false
    token.affixHost = map["affixHost"] as? Boolean ?: false
    token.chunkType = map["chunkType"] as? String ?: ""
    token.start = map["start"] as? Int ?: 0
    token.len = map["len"] as? Int
    token.skrt = map["skrt"] as? Boolean ?: false
    token.hasMergedDagdra = map["hasMergedDagdra"] as? Boolean
    return token
}
