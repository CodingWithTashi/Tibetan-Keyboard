package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.third_party.cql.Query

/**
 * Splitting Matcher for splitting tokens based on CQL queries
 */
class SplittingMatcher(
    query: String,
    private val replaceIdx: Int,
    private val splitIdx: Int,
    private val tokenList: List<Map<String, Any>>,
    private val tokenChanges: Map<String, Any>? = null
) {
    private val matcher: Query = Query(query)
    private val span: Int = matcher.size - 1

    /**
     * Split tokens that match the CQL query
     * @param mode Can be either "char" or "syl"
     * @return A new list of tokens with splits applied
     */
    fun splitOnMatches(mode: String = "char"): List<Map<String, Any>> {
        val splitList = mutableListOf<Map<String, Any>>()
        var i = 0

        while (i < tokenList.size) {
            if (matches(i)) {
                // Find the index of the token to split
                val idx = i + replaceIdx

                // Add new tokens that precede the one to split
                for (r in i until idx) {
                    splitList.add(tokenList[r])
                    i++
                }

                // Split the token and add them to the new list
                val splitTokens = split(tokenList[idx], mode)
                splitList.addAll(splitTokens)
            } else {
                splitList.add(tokenList[i])
            }

            i++
        }

        return splitList
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

    private fun split(token: Map<String, Any>, mode: String): List<Map<String, Any>> {
        // Simple split implementation
        val text = token["text"] as? String ?: ""

        // Validate split index
        if (splitIdx < 0 || splitIdx > text.length) {
            return listOf(token) // Return original if split index is invalid
        }

        // Split the text
        val firstText = text.substring(0, splitIdx)
        val secondText = text.substring(splitIdx)

        // Create two new tokens
        val firstToken = mutableMapOf<String, Any>()
        val secondToken = mutableMapOf<String, Any>()

        // Copy original attributes
        firstToken.putAll(token)
        secondToken.putAll(token)

        // Update text and length
        firstToken["text"] = firstText
        firstToken["len"] = firstText.length

        secondToken["text"] = secondText
        secondToken["len"] = secondText.length

        // Apply token changes if provided
        tokenChanges?.let {
            firstToken.putAll(it)
            secondToken.putAll(it)
        }

        return listOf(firstToken, secondToken)
    }
}
