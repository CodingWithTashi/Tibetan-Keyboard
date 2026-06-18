package com.kharagedition.botok.third_party.cql

import com.kharagedition.botok.third_party.fsa.State

/**
 * Token Expression for CQL parsing
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
class TokenExpression(
    val attribExprs: List<AttributeExpression> = emptyList(),
    val interval: Pair<Int, Int>? = null
) {

    val size: Int
        get() = attribExprs.size

    operator fun get(index: Int): AttributeExpression {
        return attribExprs[index]
    }

    /**
     * Returns an initial state for an NFA
     */
    fun nfa(nextState: State): State {
        if (interval != null) {
            val (minInterval, maxInterval) = interval
            var nextStatex2 = nextState

            for (i in 0 until maxInterval) {
                val state = State(transitions = mutableListOf(Triple(this, this::match, nextStatex2)))

                if (i + 1 > minInterval) {
                    if (nextState !== nextStatex2) {
                        state.transitions.add(Triple(this, this::match, nextState))
                    }
                    if (maxInterval == MAX_INTERVAL) {
                        state.epsilon.add(state)
                        break
                    }
                }
                nextStatex2 = state
            }
            return nextStatex2
        } else {
            return State(transitions = mutableListOf(Triple(this, this::match, nextState)))
        }
    }

    /**
     * Match a token against this expression
     */
    fun match(value: Any): Boolean {
        for (attribExpr in attribExprs) {
            val annotType = attribExpr.attribute

            val negate = when (attribExpr.operator) {
                "!=" -> true
                "=" -> false
                else -> throw Exception("Unexpected operator ${attribExpr.operator}")
            }

            val regex = if (attribExpr.valueExpr.size > 1) {
                Regex("^(${attribExpr.valueExpr.values.joinToString("|")})$")
            } else {
                Regex("^${attribExpr.valueExpr[0]}$")
            }

            // Get the attribute value from the token
            val attrValue = when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    (value as Map<String, String>)[annotType]
                }
                else -> null
            }

            var match = attrValue?.let { regex.matches(it) } ?: false

            if (negate) {
                match = !match
            }

            if (!match) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val MAX_INTERVAL = 99

        /**
         * Parse a token expression from a string
         * Expects syntax: [attribute1="value1" & attribute2="value2" (& ...)]
         * or simply: "value"
         */
        fun parse(s: String, i: Int): Pair<TokenExpression, Int> {
            val attribExprs = mutableListOf<AttributeExpression>()
            var index = i

            // Skip whitespace
            while (index < s.length && s[index] == ' ') {
                index++
            }

            when {
                s[index] == '"' -> {
                    // Simple value expression
                    val (attribExpr, newIndex) = AttributeExpression.parse(s, index)
                    attribExprs.add(attribExpr)
                    index = newIndex
                }
                s[index] == '[' -> {
                    // Complex token expression
                    index++ // Skip '['

                    while (true) {
                        // Skip whitespace
                        while (index < s.length && s[index] == ' ') {
                            index++
                        }

                        when (s[index]) {
                            '&' -> {
                                val (attribExpr, newIndex) = AttributeExpression.parse(s, index + 1)
                                attribExprs.add(attribExpr)
                                index = newIndex
                            }
                            ']' -> {
                                index++
                                break
                            }
                            else -> {
                                if (attribExprs.isEmpty()) {
                                    val (attribExpr, newIndex) = AttributeExpression.parse(s, index)
                                    attribExprs.add(attribExpr)
                                    index = newIndex
                                } else {
                                    throw SyntaxError(
                                        "Unexpected char whilst parsing token expression, position $index: ${s[index]}"
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    throw SyntaxError(
                        "Expected token expression starting with either \" or [, got: ${s[index]}"
                    )
                }
            }

            // Parse interval if present
            val interval: Pair<Int, Int>? = if (index >= s.length) {
                null
            } else when (s[index]) {
                '{' -> {
                    // Interval expression
                    val intervalStart = index
                    var intervalEnd = -1
                    for (j in (index + 1) until s.length) {
                        if (s[j] == '}') {
                            intervalEnd = j
                            break
                        }
                    }

                    require(intervalEnd != -1) { "Interval expression started but no end-brace found" }

                    val intervalStr = s.substring(intervalStart + 1, intervalEnd)
                    index = intervalEnd + 1

                    when {
                        "," in intervalStr -> {
                            val parts = intervalStr.split(",")
                            require(parts.size == 2) { "Invalid interval: $intervalStr" }
                            Pair(parts[0].toInt(), parts[1].toInt())
                        }
                        "-" in intervalStr -> {
                            val parts = intervalStr.split("-")
                            require(parts.size == 2) { "Invalid interval: $intervalStr" }
                            Pair(parts[0].toInt(), parts[1].toInt())
                        }
                        else -> {
                            val intervalValue = intervalStr.toInt()
                            Pair(intervalValue, intervalValue)
                        }
                    }
                }
                '?' -> {
                    index++
                    Pair(0, 1)
                }
                '+' -> {
                    index++
                    Pair(1, MAX_INTERVAL)
                }
                '*' -> {
                    index++
                    Pair(0, MAX_INTERVAL)
                }
                else -> null
            }

            return Pair(TokenExpression(attribExprs, interval), index)
        }
    }

    override fun toString(): String {
        return "TokenExpression(attribExprs=$attribExprs, interval=$interval)"
    }
}
