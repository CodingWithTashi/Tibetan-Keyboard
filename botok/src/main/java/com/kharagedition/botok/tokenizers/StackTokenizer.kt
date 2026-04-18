package com.kharagedition.botok.tokenizers

/**
 * Port of botok/tokenizers/stacktokenizer.py
 *
 * Stack-based tokenizer for Tibetan text using regex patterns
 * Extracts Tibetan character stacks (like "ཀྱམ་") from text
 */
class StackTokenizer {

    companion object {
        // Regex pattern for Tibetan character stacks
        // Matches sequences like: ཀྱམ་, ཀྱམ, ཀྱམ་པ, etc.
        private val STACK_PARTS = Regex(
            "[\\u0f7f\\u0f18\\u0f19\\u0f35\\u0f37\\u0f71-\\u0f7e\\u0f80-\\u0f84\\u0f86\\u0f87\\u0f8d-\\u0fbc][\\u0f18\\u0f19\\u0f35\\u0f37\\u0f71-\\u0f7e\\u0f80-\\u0f84\\u0f86\\u0f87\\u0f8d-\\u0fbc]*|" +
                "[\\u0f18\\u0f19\\u0f35\\u0f37\\u0f71-\\u0f7e\\u0f80-\\u0f84\\u0f86\\u0f87\\u0f8d-\\u0fbc]*"
        )

        private val COMMON_PARTICLES = Regex("[\\u0f7e\\u0f80\\u0f84\\u0f86\\u0f87\\u0f8d]")
    }

    /**
     * Tokenize text into Tibetan character stacks
     *
     * @param text Input text to tokenize
     * @return List of stack strings found in text
     */
    fun tokenize(text: String): List<String> {
        val stacks = tokenizeInStacks(text)

        // Remove common particles from stack results
        val result = stacks.map { stack ->
            COMMON_PARTICLES.replace(stack, "")
        }

        return result
    }

    /**
     * Extract all Tibetan character stacks from text
     *
     * @param text Input text to parse
     * @return List of stack strings
     */
    private fun tokenizeInStacks(text: String): List<String> {
        val matches = STACK_PARTS.findAll(text)
        return matches.map { it.value }.toList()
    }

    /**
     * Test method for stack tokenizer
     */
    fun testStackTokenizer(): Boolean {
        val input = "ཀྱམ་ཀྱམ་པ ཀྱམ ཀྱམ་པ"

        val tokenizer = StackTokenizer()
        val result = tokenizer.tokenize(input)

        val expected = listOf("ཀྱམ་", "ཀྱམ", "ཀྱམ་པ")

        // Return whether result matches expected
        return result == expected
    }
}
