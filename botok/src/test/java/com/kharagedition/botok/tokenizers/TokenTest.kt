package com.kharagedition.botok.tokenizers

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Port of tests/tokenizers/test_token.py
 */
class TokenTest {

    @Test
    fun testToken() {
        val t = Token()
        t.text = "test"

        // Token supports access to attributes in two ways
        // (required for CQL found in third_party/cql.py)
        assertEquals(t.text, t["text"])
        assertEquals(t.customData, t["_"])

        // Setting existing attributes like dicts is supported
        val attrs = mapOf("pos" to "NOUN", "freq" to 123, "len" to 4)
        for ((k, v) in attrs) {
            t[k] = v
        }

        val expected = """
            |text: "test"
            |pos: NOUN
            |freq: 123
            |start: 0
            |len: 4
            |
            |
        """.trimMargin().trimEnd() + "\n\n"

        assertEquals(expected, t.toString())

        // Raises an error when trying to add a new attribute
        try {
            t["non_attr"] = "test"
            fail("Expected AttributeError to be thrown")
        } catch (e: AttributeError) {
            // Expected exception
        }
    }
}
