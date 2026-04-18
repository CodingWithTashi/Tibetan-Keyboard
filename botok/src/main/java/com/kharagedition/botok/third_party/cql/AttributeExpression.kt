package com.kharagedition.botok.third_party.cql

/**
 * Attribute Expression for CQL parsing
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
class AttributeExpression(
    val attribute: String,
    val operator: String,
    val valueExpr: ValueExpression
) {

    companion object {
        private val OPERATORS = setOf("=", "!=")
        private const val DEFAULT_FIELD = "text_cleaned"

        /**
         * Parse an attribute expression from a string
         * Expects syntax: attribute="value" or "value" (using default field)
         */
        fun parse(s: String, i: Int): Pair<AttributeExpression, Int> {
            var index = i

            // Skip whitespace
            while (index < s.length && s[index] == ' ') {
                index++
            }

            val (attribute, operator) = if (s[index] == '"') {
                // No attribute and no operator, use defaults
                Pair(DEFAULT_FIELD, "=")
            } else {
                // Parse attribute name
                val attrBuilder = StringBuilder()
                val forbiddenChars = setOf(' ', '!', '>', '<', '=')
                while (index < s.length && s[index] !in forbiddenChars) {
                    attrBuilder.append(s[index])
                    index++
                }

                val attr = attrBuilder.toString()
                require(attr.isNotEmpty()) { "Expected attribute name, none found" }

                // Parse operator
                val opBuilder = StringBuilder()
                val operatorChars = setOf(' ', '!', '>', '<', '=')
                while (index < s.length && s[index] in operatorChars) {
                    if (s[index] != ' ') {
                        opBuilder.append(s[index])
                    }
                    index++
                }

                val op = opBuilder.toString()
                require(op in OPERATORS) { "Expected operator, got '$op'" }

                Pair(attr, op)
            }

            require(index < s.length && s[index] == '"') {
                val errorMsg = if (index < s.length) "got ${s[index]}" else "got EOF"
                "Expected start of value expression (double quote) at position $index, $errorMsg"
            }

            val (valueExpr, newIndex) = ValueExpression.parse(s, index)
            return Pair(AttributeExpression(attribute, operator, valueExpr), newIndex)
        }
    }

    override fun toString(): String {
        return "AttributeExpression($attribute$operator\"${valueExpr.values.joinToString("|")}\")"
    }
}
