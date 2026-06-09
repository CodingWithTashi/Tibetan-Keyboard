package com.kharagedition.botok.tokenizers

/**
 * Port of botok/tokenizers/stacktokenizer.py
 *
 * Stack-based tokenizer for Tibetan text using regex patterns
 * Extracts Tibetan character stacks (like "ཀྱམ་") from text
 */
class StackTokenizer {

    companion object {
        // Matches a Tibetan base consonant (U+0F40-U+0F6C) followed by any combining marks
        // (subjoined consonants U+0F90-U+0FAD, vowel signs U+0F71-U+0F84, other marks).
        // This correctly captures stacks like ཀྱ, མ, པ, etc.
        private val STACK_RE = Regex("[\u0F40-\u0F6C][\u0F71-\u0F84\u0F86-\u0F8C\u0F90-\u0FAD\u0FB1-\u0FB3\u0FB7]*")

        // Syllable boundary: tsheg or whitespace
        private val SYLLABLE_SEP = Regex("[\u0F0B\\s]+")
    }

    /**
     * Tokenize text into Tibetan syllable strings (one per tsheg-separated token).
     * Each returned string is a non-empty syllable such as "ཀྱམ", "པ", "མཐ", etc.
     */
    fun tokenize(text: String): List<String> =
        text.split(SYLLABLE_SEP).filter { it.isNotEmpty() }

    /**
     * Split a syllable into its constituent stacks (base consonant + combining marks).
     * E.g. "བཀྲ" → ["བ", "ཀྲ"]
     */
    fun splitSyllableIntoStacks(syllable: String): List<String> =
        STACK_RE.findAll(syllable).map { it.value }.toList()

    /**
     * Test method for stack tokenizer
     */
    fun testStackTokenizer(): Boolean {
        val result = StackTokenizer().tokenize("ཀྱམ་ཀྱམ་པ ཀྱམ ཀྱམ་པ")
        return result.isNotEmpty() && result.any { it.contains("ཀྱམ") }
    }
}
