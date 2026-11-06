package com.kharagedition.tibetankeyboard.ui.keyboard.voice

/**
 * What a dictation adds to the field, and when taking it back is safe. Pure, like
 * `WordComposer`: both decisions read only the text before the cursor.
 */
object VoiceInsert {

    private const val TSHEG = '་'
    private const val SHAD = '།'
    private val TIBETAN_BLOCK = 'ༀ'..'࿿'

    /**
     * [text] with the separator it needs after [textBeforeCursor]: none at the start, after a
     * space or after a tsheg; a tsheg to finish a bare Tibetan syllable; a space after anything
     * else (a shad, Latin text, digits). Text that opens with its own punctuation is left as is.
     */
    fun join(textBeforeCursor: CharSequence?, text: String): String {
        if (textBeforeCursor.isNullOrEmpty() || text.isEmpty()) return text
        val first = text.first()
        if (first.isWhitespace() || first == TSHEG || first == SHAD) return text
        return separatorAfter(textBeforeCursor.last()) + text
    }

    /**
     * Whether [inserted] is still exactly what ends the text before the cursor, so Undo removes
     * it and nothing else. False once the user typed after it, moved the cursor or edited it,
     * and when the editor cannot say what is there.
     */
    fun canUndo(textBeforeCursor: CharSequence?, inserted: String): Boolean =
        inserted.isNotEmpty() && textBeforeCursor?.endsWith(inserted) == true

    /** How many Tibetan-script characters [text] holds: what a dictation adds to the typing streak. */
    fun tibetanChars(text: String): Int = text.count { it in TIBETAN_BLOCK }

    private fun separatorAfter(last: Char): String = when {
        last.isWhitespace() || last == TSHEG -> ""
        isTibetanSyllablePart(last) -> TSHEG.toString()
        else -> " "
    }

    /** A consonant, vowel sign or subjoined letter: the syllable is not yet closed by a tsheg. */
    private fun isTibetanSyllablePart(c: Char): Boolean =
        c in 'ཀ'..'ཬ' || c in 'ཱ'..'྄' || c in 'ྐ'..'ྼ'
}
