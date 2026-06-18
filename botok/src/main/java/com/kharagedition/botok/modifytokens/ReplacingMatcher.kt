package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.third_party.cql.CqlParser
import com.kharagedition.botok.third_party.cql.Query

/**
 * Replacing Matcher for replacing token attributes based on CQL queries
 */
class ReplacingMatcher(
    query: String,
    private val replaceIdx: Int,
    private val tokenList: List<Map<String, Any>>,
    private val tokenChanges: String
) {
    private val matcher: Query = Query(query)
    private val span: Int = matcher.size - 1

    /**
     * Replace attributes in tokens that match the CQL query
     * Modifies tokenList in place
     */
    fun replaceOnMatches() {
        var i = 0

        while (i < tokenList.size) {
            if (matches(i)) {
                // Find the index of the token to replace
                val idx = i + replaceIdx

                // Replace the attributes in the token
                val token = tokenList[idx] as MutableMap<String, Any?>
                val changes = CqlParser.parseCqlQuery(tokenChanges)
                if (changes != null && changes.isNotEmpty()) {
                    for ((attr, value) in changes[0]) {
                        token[attr] = value
                    }
                }
            }

            i++
        }
    }

    private fun matches(i: Int): Boolean {
        return i + span <= tokenList.size && matcher(
            tokenList.slice(i until i + span + 1).map { token ->
                token.mapValues { (_, value) ->
                    when (value) {
                        is String -> value
                        else -> value.toString()
                    }
                }
            }
        ).isNotEmpty()
    }
}
