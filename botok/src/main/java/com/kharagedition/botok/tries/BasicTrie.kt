package com.kharagedition.botok.tries

/**
 * Port of botok/tries/basictrie.py — BasicTrie class.
 *
 * Words are represented as List<String> where each element is one syllable (or one character
 * when used with non-Tibetan tests). Trie nodes are keyed by those strings.
 */
open class BasicTrie {

    val head = Node()

    operator fun get(key: String): Node = head.children.getValue(key)

    // ------------------------------------------------------------------
    // add — insert a word into the trie
    // ------------------------------------------------------------------

    /**
     * Port of Python's add(word, data=None).
     *
     * word : each element is one trie-key step (syllable string or single character).
     * data : optional dict merged into the leaf node's data map.
     */
    fun add(word: List<String>, data: Map<String, Any?>? = null) {
        var currentNode = head
        var wordFinished = true
        var i = 0

        // Walk as far as the word exists in the trie
        for (idx in word.indices) {
            i = idx
            if (word[idx] in currentNode.children) {
                currentNode = currentNode.children[word[idx]]!!
            } else {
                wordFinished = false
                break
            }
        }

        // Add missing nodes starting from the break-point (same index i, not i+1, matching Python)
        if (!wordFinished) {
            while (i < word.size) {
                currentNode.addChild(word[i])
                currentNode = currentNode.children[word[i]]!!
                i++
            }
        }

        currentNode.leaf = true

        if (data != null && data.isNotEmpty()) {
            currentNode.data.putAll(data)
        }
    }

    // ------------------------------------------------------------------
    // walk — single-step external trie walk (used by Tokenize)
    // ------------------------------------------------------------------

    /**
     * Port of Python's walk(char, current_node=None).
     * Returns the next node if char exists as a child, or null.
     */
    fun walk(char: String, currentNode: Node? = null): Node? {
        val node = currentNode ?: head
        return node.children[char]
    }

    // ------------------------------------------------------------------
    // has_word — check word existence and return its data
    // ------------------------------------------------------------------

    /**
     * Port of Python's has_word(word).
     * Returns {"exists": Boolean, "data": MutableMap<String, Any?>}.
     */
    fun hasWord(word: List<String>): Map<String, Any?> {
        if (word.isEmpty()) throw IllegalArgumentException("\"word\" must be non-null string")

        var currentNode = head
        var exists = true

        for (syl in word) {
            if (syl in currentNode.children) {
                currentNode = currentNode.children[syl]!!
            } else {
                exists = false
                break
            }
        }

        // Reached a prefix but not a leaf → not a complete word
        if (exists && !currentNode.leaf) {
            exists = false
        }

        // Python always returns data regardless of exists
        return mapOf("exists" to exists, "data" to currentNode.data)
    }

    // ------------------------------------------------------------------
    // add_data — attach metadata to an existing leaf
    // ------------------------------------------------------------------

    /**
     * Port of Python's add_data(word, data).
     *
     * data : a Map<String, Any?> representing one sense entry.
     *        (The Python interface also accepts int for form_freq, but that path
     *         is unused in practice; we keep the dict-only version for type safety.)
     *
     * Returns true if data was added, false if the word is not in the trie.
     */
    fun addData(word: List<String>, data: Map<String, Any?>): Boolean {
        if (word.isEmpty()) throw IllegalArgumentException("\"word\" must be non-null string")

        var currentNode = head
        for (syl in word) {
            currentNode = currentNode.children[syl] ?: return false
        }

        if (!currentNode.leaf) return false

        @Suppress("UNCHECKED_CAST")
        if ("senses" !in currentNode.data) {
            currentNode.data["senses"] = mutableListOf<Map<String, Any?>>()
        }
        val senses = currentNode.data["senses"] as MutableList<Map<String, Any?>>
        return addMeaning(senses, data)
    }

    // ------------------------------------------------------------------
    // add_meaning / is_diff_meaning
    // ------------------------------------------------------------------

    /**
     * Port of Python's add_meaning(meanings, meaning).
     * Appends meaning only if it differs from all existing meanings.
     */
    fun addMeaning(meanings: MutableList<Map<String, Any?>>, meaning: Map<String, Any?>): Boolean {
        if (meanings.isNotEmpty()) {
            for (m in meanings) {
                if (isDiffMeaning(meaning, m)) {
                    meanings.add(meaning)
                    return true
                }
            }
            return false
        } else {
            meanings.add(meaning)
            return true
        }
    }

    /** Port of Python's is_diff_meaning(m1, m2). */
    fun isDiffMeaning(m1: Map<String, Any?>, m2: Map<String, Any?>): Boolean {
        for ((k, v) in m1) {
            if (k !in m2 || m2[k] != v) return true
        }
        return false
    }

    // ------------------------------------------------------------------
    // deactivate — set leaf = false (or true for re-activation)
    // ------------------------------------------------------------------

    /**
     * Port of Python's deactivate(word, rev=False).
     * Sets leaf to false (deactivate) or true (re-activate when rev=true).
     * Returns true if the word was found, false otherwise.
     */
    fun deactivate(word: List<String>, rev: Boolean = false): Boolean {
        var currentNode = head
        for (syl in word) {
            currentNode = currentNode.children[syl] ?: return false
        }
        // Python checks isinstance(data, dict) which is always True for our MutableMap
        currentNode.leaf = rev
        return true
    }
}
