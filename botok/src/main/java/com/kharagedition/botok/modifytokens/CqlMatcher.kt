package com.kharagedition.botok.modifytokens

import com.kharagedition.botok.third_party.cql.Query

/**
 * CQL Matcher for matching token lists against CQL queries
 *
 * Creates a matcher object to be later executed against a list of tokens
 *
 * @param query CQL compliant query string
 */
class CqlMatcher(query: String) {

    private val query: Query = Query(query)

    /**
     * Runs CQL Query on a slice of the list of tokens for every index in the list
     *
     * @param tokensList Output of tokenizer (list of token maps)
     * @return A list of matching slices of tokensList
     *         Each match is a pair with (beginning index, end index)
     */
    fun match(tokensList: List<Map<String, String>>): List<Pair<Int, Int>> {
        val sliceLen = query.size - 1
        val matches = mutableListOf<Pair<Int, Int>>()

        for (i in tokensList.indices) {
            if (i + sliceLen <= tokensList.size) {
                val slice = tokensList.slice(i until i + sliceLen + 1)
                if (query(slice).isNotEmpty()) {
                    matches.add(Pair(i, i + sliceLen))
                }
            }
        }

        return matches
    }
}
