package com.kharagedition.botok.third_party.cql

import com.kharagedition.botok.third_party.fsa.Nfa
import com.kharagedition.botok.third_party.fsa.State

/**
 * Query for CQL parsing
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
class Query(s: String) {
    val tokenExprs: List<TokenExpression>

    init {
        val exprs = mutableListOf<TokenExpression>()
        var i = 0
        val l = s.length

        while (i < l) {
            if (s[i] == ' ') {
                i++
            } else {
                val (tokenExpr, newIndex) = TokenExpression.parse(s, i)
                exprs.add(tokenExpr)
                i = newIndex
            }
        }

        tokenExprs = exprs
    }

    val size: Int
        get() = tokenExprs.size

    operator fun get(index: Int): TokenExpression {
        return tokenExprs[index]
    }

    /**
     * Convert the expression into an NFA
     */
    fun nfa(): Nfa {
        val finalState = State(final = true)
        var nextState: State = finalState

        for (tokenExpr in tokenExprs.reversed()) {
            val state = tokenExpr.nfa(nextState)
            nextState = state
        }

        return Nfa(nextState)
    }

    /**
     * Execute the CQL expression
     * @param tokens List of tokens/annotations using keyword arguments: word, pos, lemma, etc
     * @param debug Enable debug output
     * @return List of matching token slices
     */
    operator fun invoke(tokens: List<Map<String, String>>, debug: Boolean = false): List<List<Any>> {
        require(tokens.isNotEmpty()) {
            "Pass a list of tokens/annotation using keyword arguments! (word,pos,lemma, or others)"
        }

        // Convert the expression into an NFA
        val nfa = nfa()

        if (debug) {
            println("NFA: $nfa")
        }

        return nfa.find(tokens, debug).toList()
    }

    override fun toString(): String {
        return "Query(tokenExprs=$tokenExprs)"
    }
}
