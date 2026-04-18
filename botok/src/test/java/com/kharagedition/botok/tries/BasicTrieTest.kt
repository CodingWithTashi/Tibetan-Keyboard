package com.kharagedition.botok.tries

import org.junit.Assert.*
import org.junit.Test

/**
 * Port of Python's tests/tries/test_basictrie.py
 *
 * In Python, BasicTrie.add() takes an iterable of any type — strings iterate char-by-char,
 * lists iterate element-by-element. In Kotlin, we use List<String> for both cases:
 * single-char strings for the English-word tests, syllable strings for Tibetan.
 */
class BasicTrieTest {

    private fun chars(word: String): List<String> = word.map { it.toString() }

    @Test fun `test trie add and has_word`() {
        val trie = BasicTrie()
        val words = "hello goo good goodbye help gerald gold tea ted team to too tom stan standard money"
        for (w in words.split(" ")) {
            trie.add(chars(w))
        }
        assertEquals(
            mapOf("exists" to true, "data" to mapOf("_" to mutableMapOf<String, Any?>())),
            trie.hasWord(chars("goodbye"))
        )
    }

    @Test fun `test add_data with pos`() {
        val trie = BasicTrie()
        trie.add(chars("goodbye"))
        trie.addData(chars("goodbye"), mapOf("pos" to "NOUN"))
        val result = trie.hasWord(chars("goodbye"))
        assertEquals(true, result["exists"])
        @Suppress("UNCHECKED_CAST")
        val data = result["data"] as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val senses = data["senses"] as List<Map<String, Any?>>
        assertEquals(1, senses.size)
        assertEquals("NOUN", senses[0]["pos"])
    }

    @Test fun `test add_data empty dict does not replace`() {
        val trie = BasicTrie()
        trie.add(chars("goodbye"))
        trie.addData(chars("goodbye"), mapOf("pos" to "NOUN"))
        trie.addData(chars("goodbye"), emptyMap())  // adding empty dict — same as Python
        val result = trie.hasWord(chars("goodbye"))
        @Suppress("UNCHECKED_CAST")
        val data = result["data"] as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val senses = data["senses"] as List<Map<String, Any?>>
        // An empty map is "different" from {"pos": "NOUN"} by isDiffMeaning → gets appended
        // BUT Python's add_meaning for empty m1 iterates zero keys → isDiffMeaning returns false
        // → empty dict is NOT appended. Let's verify this matches Python behaviour.
        // Python: for k, v in {}.items(): → loop body never runs → is_diff = False → returns False
        // So: empty dict is not added (returns False from add_meaning).
        assertEquals(1, senses.size)
        assertEquals("NOUN", senses[0]["pos"])
    }

    @Test fun `test add_data overwrites with different meaning`() {
        val trie = BasicTrie()
        trie.add(chars("goodbye"))
        trie.addData(chars("goodbye"), mapOf("pos" to "NOUN"))
        trie.addData(chars("goodbye"), mapOf("pos" to "VERB", "lemma" to "goodbye"))
        val result = trie.hasWord(chars("goodbye"))
        @Suppress("UNCHECKED_CAST")
        val data = result["data"] as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val senses = data["senses"] as List<Map<String, Any?>>
        assertEquals(2, senses.size)
        assertEquals("NOUN", senses[0]["pos"])
        assertEquals("VERB", senses[1]["pos"])
        assertEquals("goodbye", senses[1]["lemma"])
    }

    @Test fun `test deactivate`() {
        val trie = BasicTrie()
        trie.add(chars("goodbye"))
        trie.addData(chars("goodbye"), mapOf("pos" to "NOUN"))
        trie.addData(chars("goodbye"), mapOf("pos" to "VERB", "lemma" to "goodbye"))

        trie.deactivate(chars("goodbye"))
        val result = trie.hasWord(chars("goodbye"))
        assertEquals(false, result["exists"])
        // data is still accessible
        @Suppress("UNCHECKED_CAST")
        val senses = (result["data"] as MutableMap<String, Any?>)["senses"] as List<*>
        assertEquals(2, senses.size)
    }

    @Test fun `test reactivate`() {
        val trie = BasicTrie()
        trie.add(chars("goodbye"))
        trie.addData(chars("goodbye"), mapOf("pos" to "NOUN"))
        trie.addData(chars("goodbye"), mapOf("pos" to "VERB", "lemma" to "goodbye"))
        trie.deactivate(chars("goodbye"))
        trie.deactivate(chars("goodbye"), rev = true)

        val result = trie.hasWord(chars("goodbye"))
        assertEquals(true, result["exists"])
    }

    @Test fun `test walk`() {
        val trie = BasicTrie()
        trie.add(chars("goodbye"))
        trie.addData(chars("goodbye"), mapOf("pos" to "NOUN"))
        trie.addData(chars("goodbye"), mapOf("pos" to "VERB", "lemma" to "goodbye"))

        var currentNode: Node? = null
        for (char in "goodbye") {
            currentNode = trie.walk(char.toString(), currentNode)
        }

        assertNotNull(currentNode)
        assertEquals("e", currentNode!!.label)
        assertEquals(true, currentNode.leaf)
        @Suppress("UNCHECKED_CAST")
        val senses = (currentNode.data["senses"] as List<Map<String, Any?>>)
        assertEquals(2, senses.size)
    }

    @Test fun `test has_word non-existent returns exists false`() {
        val trie = BasicTrie()
        trie.add(chars("hello"))
        val result = trie.hasWord(chars("world"))
        assertEquals(false, result["exists"])
    }

    @Test fun `test has_word prefix-only returns exists false`() {
        val trie = BasicTrie()
        trie.add(chars("hello"))
        // "hell" is a prefix but not a complete word
        val result = trie.hasWord(chars("hell"))
        assertEquals(false, result["exists"])
    }
}
