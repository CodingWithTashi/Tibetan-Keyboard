package com.kharagedition.botok.third_party.fsa

/**
 * Finite State Automaton State
 *
 * Original copyright notice:
 *
 * ---------------------------------------------------------------
 * PyNLPl - Finite State Automata
 *   by Maarten van Gompel
 *   Centre for Language Studies
 *   Radboud University Nijmegen
 *   http://proycon.github.com/folia
 *   http://www.github.com/proycon/pynlpl
 *   proycon AT anaproy DOT nl
 *
 * Partially based/inspired on code by Xiayun Sun (https://github.com/xysun/regex)
 *
 *   Licensed under GPLv3
 *
 * ----------------------------------------------------------------
 *
 * This file is modified and redistributed here under APL2 with
 * written permission from the original author
 */
class State(
    val epsilon: MutableList<State> = mutableListOf(),
    val transitions: MutableList<Triple<Any, (Any) -> Boolean, State>> = mutableListOf(),
    var final: Boolean = false
) {
    /**
     * Will be a tuple (state, matchitem) indicating how this state was reached
     */
    var transitioned: Pair<State, Any>? = null

    override fun toString(): String {
        return "State(final=$final, epsilon=${epsilon.size}, transitions=${transitions.size})"
    }
}
