package com.kharagedition.botok.third_party.fsa

/**
 * Non-deterministic finite state automaton.
 * Can be used to model DFAs as well if your state transitions are not ambiguous and epsilon is empty.
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
class Nfa(val initialState: State) {

    private fun add(state: State, states: MutableSet<State>) {
        /**
         * Add state and recursively add epsilon transitions
         */
        if (state in states) {
            return
        }
        states.add(state)
        for (eps in state.epsilon) {
            add(eps, states)
        }
    }

    fun run(sequence: List<Any>, mustMatchAll: Boolean = false, debug: Boolean = false): Sequence<Int> = sequence {
        /**
         * Run the NFA on the given sequence
         */
        val currentStates = mutableSetOf<State>()
        add(initialState, currentStates)

        if (debug) {
            println("Starting run, current states: $currentStates")
        }

        sequence.forEachIndexed { offset, value ->
            if (currentStates.isEmpty()) {
                return@forEachIndexed
            }

            if (debug) {
                println("Value: $value")
            }

            val nextStates = mutableSetOf<State>()
            for (state in currentStates) {
                for ((matchItem, matchFunction, transState) in state.transitions) {
                    if (matchFunction(value)) {
                        transState.transitioned = Pair(state, matchItem)
                        add(transState, nextStates)
                    }
                }
            }

            currentStates.clear()
            currentStates.addAll(nextStates)

            if (debug) {
                println("Current states: $currentStates")
            }

            if (!mustMatchAll) {
                for (s in currentStates) {
                    if (s.final) {
                        if (debug) {
                            println("Final state reached")
                        }
                        yield(offset + 1)
                    }
                }
            }
        }

        if (mustMatchAll) {
            val offset = sequence.size
            for (s in currentStates) {
                if (s.final) {
                    if (debug) {
                        println("Final state reached")
                    }
                    yield(offset)
                }
            }
        }
    }

    fun match(sequence: List<Any>): Boolean {
        /**
         * Check if the entire sequence matches
         */
        return run(sequence, mustMatchAll = true).firstOrNull() == sequence.size
    }

    fun find(sequence: List<Any>, debug: Boolean = false): Sequence<List<Any>> = sequence {
        /**
         * Find all matches in the sequence
         */
        val l = sequence.size
        for (i in 0 until l) {
            for (length in run(sequence.slice(i until l), mustMatchAll = false, debug = debug)) {
                yield(sequence.slice(i until i + length))
            }
        }
    }

    override fun toString(): String {
        return "Nfa(initial=$initialState)"
    }
}
