package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.third_party.cql.Query

/**
 * Merging Matcher for merging tokens based on CQL queries
 */
class MergingMatcher(
    query: String,
    private val replaceIdx: Int,
    private val tokenList: List<Map<String, Any>>,
    private val tokenChanges: Map<String, Any>? = null
) {
    private val matcher: Query = Query(query)
    private val span: Int = matcher.size - 1

    /**
     * Merge tokens that match the CQL query
     * @return A new list of tokens with merges applied
     */
    fun mergeOnMatches(): List<Map<String, Any>> {
        val mergedList = mutableListOf<Map<String, Any>>()
        var i = 0

        while (i < tokenList.size) {
            if (matches(i)) {
                // Find the index of the token to merge
                val idx = i + replaceIdx

                // Add new tokens that precede the one to merge
                for (r in i until idx) {
                    mergedList.add(tokenList[r])
                    i++
                }

                // Merge the token and add it to the new list
                val mergedToken = merge(tokenList[idx], tokenList[idx + 1])
                mergedList.add(mergedToken)
                i++ // Skip the second token that was merged
            } else {
                mergedList.add(tokenList[i])
            }

            i++
        }

        return mergedList
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

    private fun merge(token1: Map<String, Any>, token2: Map<String, Any>): Map<String, Any> {
        // Simple merge implementation - combine text and update attributes
        val merged = mutableMapOf<String, Any>()

        // Start with first token's attributes
        merged.putAll(token1)

        // Merge text content
        val text1 = token1["text"] as? String ?: ""
        val text2 = token2["text"] as? String ?: ""
        merged["text"] = text1 + text2

        // Update length
        val len1 = token1["len"] as? Int ?: 0
        val len2 = token2["len"] as? Int ?: 0
        merged["len"] = len1 + len2

        // Apply token changes if provided
        tokenChanges?.let { merged.putAll(it) }

        return merged
    }
}
