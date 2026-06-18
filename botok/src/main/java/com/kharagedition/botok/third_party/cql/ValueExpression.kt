package com.kharagedition.botok.third_party.cql

/**
 * Value Expression for CQL parsing
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
class ValueExpression(val values: List<String>) {

    val size: Int
        get() = values.size

    operator fun get(index: Int): String {
        return values[index]
    }

    override fun toString(): String {
        return "ValueExpression(${values.joinToString("|")})"
    }

    companion object {
        /**
         * Parse a value expression from a string
         * Expects syntax: "value1|value2|value3"
         */
        fun parse(s: String, i: Int): Pair<ValueExpression, Int> {
            require(s[i] == '"') { "Expected opening quote at position $i" }

            var values = ""
            var index = i + 1

            while (!(s[index] == '"' && s[index - 1] != '\\')) {
                values += s[index]
                index++
            }

            val valueList = values.split("|")
            return Pair(ValueExpression(valueList), index + 1)
        }
    }
}
