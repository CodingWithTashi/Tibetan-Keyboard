package com.kharagedition.botok.tries

/**
 * Port of botok/tries/basictrie.py — Node class.
 *
 * Each node in the trie:
 *  - label    : the syllable string stored at this node (null for the head)
 *  - leaf     : true if this node represents a complete word
 *  - data     : mutable map; always contains "_" → mutable sub-map for user data.
 *               Additional keys (affixation, senses, skrt, form_freq) are added by Trie.
 *  - children : insertion-ordered map of syllable → child Node.
 *               LinkedHashMap preserves insertion order, matching Python 3.7+ dict semantics
 *               so that trie walks are deterministic.
 */
class Node(
    var label: String? = null,
    var leaf: Boolean = false,
    val data: MutableMap<String, Any?> = mutableMapOf("_" to mutableMapOf<String, Any?>())
) {
    val children: LinkedHashMap<String, Node> = LinkedHashMap()

    /** Port of Python's add_child(). */
    fun addChild(key: String, leaf: Boolean = false) {
        children[key] = Node(key, leaf)
    }

    /** Port of Python's can_walk(). */
    fun canWalk(): Boolean = children.isNotEmpty()

    /** Port of Python's is_match(). */
    fun isMatch(): Boolean = leaf

    operator fun get(key: String): Node = children.getValue(key)
}
