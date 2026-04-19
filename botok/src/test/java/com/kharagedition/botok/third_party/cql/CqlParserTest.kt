package com.kharagedition.botok.third_party.cql

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for CQL Parser functionality
 */
class CqlParserTest {

    @Test
    fun testParseSimpleQuery() {
        val query = "[pos=\"NOUN\"]"
        val parsed = CqlParser.parseCqlQuery(query)

        assertNotNull(parsed)
        assertTrue(parsed!!.isNotEmpty())
        assertEquals("NOUN", parsed[0]["pos"])
    }

    @Test
    fun testParseQueryWithMultipleAttributes() {
        val query = "[pos=\"NOUN\" & lemma=\"test\"]"
        val parsed = CqlParser.parseCqlQuery(query)

        assertNotNull(parsed)
        assertTrue(parsed!!.isNotEmpty())
        assertEquals("NOUN", parsed[0]["pos"])
        assertEquals("test", parsed[0]["lemma"])
    }

    @Test
    fun testParseComplexQuery() {
        val query = "[text=\"བཀྲ\" & pos=\"NOUN\"]"
        val parsed = CqlParser.parseCqlQuery(query)

        assertNotNull(parsed)
        assertTrue(parsed!!.isNotEmpty())
        assertEquals("བཀྲ", parsed[0]["text"])
        assertEquals("NOUN", parsed[0]["pos"])
    }


    @Test
    fun testParseQueryWithNegation() {
        val query = "[pos!=\"NOUN\"]"
        val parsed = CqlParser.parseCqlQuery(query)

        assertNotNull(parsed)
        assertTrue(parsed!!.isNotEmpty())
    }
}
