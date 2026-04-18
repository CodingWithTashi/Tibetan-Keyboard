package com.kharagedition.botok.third_party.cql

/**
 * CQL parser for replacing the content of Token.attributes.
 * From the CQL syntax, only the attribute names and the values
 * are taken into account.
 *
 * Original copyright notice:
 *
 * ---------------------------------------------------------------
 * PyNLPl - Corpus Query Language (CQL)
 *   by Maarten van Gompel
 *   Centre for Language Studies
 *   Radboud University Nijmegen
 *   http://proycon.github.com/folia
 *   http://www.github.com/proycon/pynlpl
 *   proycon AT anaproy DOT nl
 *
 * Parser and interpreter for a basic subset of the Corpus Query Language
 *
 *   Licensed under GPLv3
 *
 * ----------------------------------------------------------------
 *
 * This file is modified and redistributed here under APL2 with
 * written permission from the original author
 */
object CqlParser {

    /**
     * Parse a CQL query string
     *
     * @param query CQL query string
     * @param numerals If true, gives Int instead of String
     * @param booleans If true, gives Boolean instead of String
     * @return A list of maps, one per token slot, where keys == Token.attributes
     *         and values == content of the expected Token.attributes
     */
    fun parseCqlQuery(
        query: String?,
        numerals: Boolean = true,
        booleans: Boolean = true
    ): List<Map<String, Any?>>? {
        if (query == null) {
            return null
        }

        fun str2Int(string: String): Any {
            return try {
                string.toInt()
            } catch (e: NumberFormatException) {
                string
            }
        }

        fun str2Bool(string: String): Any? {
            return when (string) {
                "True" -> true
                "False" -> false
                "None" -> null
                else -> string
            }
        }

        fun cql2Pattern(tokenExpr: TokenExpression, numerals: Boolean, booleans: Boolean): Map<String, Any?> {
            val changes = mutableMapOf<String, Any?>()
            for (attrExpr in tokenExpr.attribExprs) {
                var value: Any? = attrExpr.valueExpr[0]
                if (numerals) {
                    value = str2Int(value as String)
                }
                if (booleans) {
                    value = str2Bool(value as String)
                }
                changes[attrExpr.attribute] = value
            }
            return changes
        }

        val parsed = Query(query)
        val pattern = mutableListOf<Map<String, Any?>>()

        for (tokenExpr in parsed.tokenExprs) {
            pattern.add(cql2Pattern(tokenExpr, numerals, booleans))
        }

        return pattern
    }

    /**
     * Applies in place the replacements found in the CQL query (token_changes)
     * The number of tokens in the list and the number of token slots in the query
     * must be even.
     *
     * @param tokens List of tokens (as maps)
     * @param tokenChanges CQL query
     */
    fun replaceTokenAttributes(tokens: List<Map<String, Any?>>, tokenChanges: String) {
        val changes = parseCqlQuery(tokenChanges) ?: return

        require(tokens.size == changes.size) {
            "Number of tokens (${tokens.size}) must match number of token slots in query (${changes.size})"
        }

        for (i in tokens.indices) {
            for ((attr, value) in changes[i]) {
                @Suppress("UNCHECKED_CAST")
                (tokens[i] as MutableMap<String, Any?>)[attr] = value ?: continue
            }
        }
    }
}
