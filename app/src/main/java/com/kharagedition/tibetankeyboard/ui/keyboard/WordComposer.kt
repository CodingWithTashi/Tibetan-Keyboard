package com.kharagedition.tibetankeyboard.ui.keyboard

/**
 * The word being typed, and what a suggestion tap and the keys around it do to the editor.
 *
 * Pure (no Android) so the rules are unit-tested; the IME applies the returned [Edit]s to its
 * InputConnection and asks the engine for suggestions on [composing].
 *
 * How Tibetan is typed: syllables are joined with a tsheg (་) and words are not separated at all,
 * so a run of text only ends at a shad (།), a space or a newline. The word in progress is
 * everything typed since the last run end or the last accepted suggestion. The engine matches it
 * whole (བོད་ཡ → བོད་ཡུལ) or, failing that, a tail of it starting after a tsheg (ང་བོད་ར →
 * བོད་རིགས, ང་བོད་པ → པད་མ), and an accepted word replaces exactly the part that was matched,
 * never the whole run.
 *
 * Accepting a word also writes the tsheg the user would type next (བོ → བོད་). That tsheg stays
 * provisional until the next key: a typed tsheg is swallowed (never ་་) and changes nothing, a
 * shad takes the tsheg's place (བོད་ + ། → བོད།) except after nga, where the tsheg is kept so ང།
 * cannot be read as ད (རང་།), and any other key makes it an ordinary tsheg.
 *
 * Every decision is checked against the text actually before the cursor: state the editor no
 * longer confirms (the user tapped elsewhere, the app changed the text, a callback was lost) is
 * dropped, so a tap can never delete characters that are not the ones it completes.
 */
class WordComposer {

    /** What the user has typed since the last run end or accepted suggestion. */
    var composing: String = ""
        private set

    /** The word + tsheg just accepted, while that tsheg is still provisional. */
    private var provisional: String = ""

    /** An editor change: delete [deleteBefore] chars before the cursor, then insert [text]. */
    data class Edit(
        val deleteBefore: Int = 0,
        val text: String = "",
        /**
         * Typed text that is now final, for the Journey word stats: the run a boundary key just
         * closed, or the syllables an accepted word leaves in place before it. Empty otherwise.
         */
        val finishedChunk: String = "",
    )

    val hasState: Boolean get() = composing.isNotEmpty() || provisional.isNotEmpty()

    /** How much editor text before the cursor [sync] needs to check this state. */
    val lookback: Int get() = maxOf(composing.length, provisional.length) + LOOKBACK_MARGIN

    fun reset() {
        composing = ""
        provisional = ""
    }

    /**
     * Drops whatever [before] (the text before the cursor; null when the editor cannot say) no
     * longer confirms. Returns true when the word in progress was dropped, so the strip can be
     * cleared.
     */
    fun sync(before: CharSequence?): Boolean {
        val hadWord = composing.isNotEmpty()
        if (before == null || !before.endsWith(composing)) composing = ""
        if (provisional.isNotEmpty() && (before == null || !before.endsWith(provisional))) provisional = ""
        return hadWord && composing.isEmpty()
    }

    /** The user typed [c]; returns what to write, which may differ from [c] after an accepted word. */
    fun onChar(c: Char, before: CharSequence?): Edit {
        sync(before)
        var deleteBefore = 0
        if (provisional.isNotEmpty()) {
            // The tsheg is already there: nothing to write, and it stays provisional.
            if (isTsheg(c)) return Edit()
            val accepted = provisional.dropLast(1)
            provisional = ""
            if (isShad(c) && !endsWithNga(accepted)) deleteBefore = 1
        }
        if (endsRun(c)) {
            val chunk = composing
            composing = ""
            return Edit(deleteBefore, c.toString(), chunk)
        }
        // An editor that cannot report its text gets no suggestions: a replacement could never
        // be checked against what is really before the cursor.
        if (before != null) composing += c
        return Edit(deleteBefore, c.toString())
    }

    /** The user is deleting the char before the cursor. */
    fun onDelete(before: CharSequence?) {
        sync(before)
        provisional = ""
        if (composing.isNotEmpty()) composing = composing.dropLast(1)
    }

    /**
     * The user accepted [word], which the engine offered for [matched] (the end of the prefix it
     * completes). [after] is the text after the cursor (null when the editor cannot say), so no
     * tsheg is written onto one that is already there or in front of a shad.
     */
    fun onAccept(word: String, matched: String, before: CharSequence?, after: CharSequence?): Edit {
        sync(before)
        val deleteBefore =
            if (matched.isNotEmpty() && before != null && before.endsWith(matched)) matched.length else 0
        val text = if (wantsTsheg(word, after)) word + TSHEG else word
        // Whatever the word does not replace stays in the editor as typed words.
        val kept = composing.dropLast(deleteBefore)
        composing = ""
        provisional = if (text.length > word.length) text else ""
        return Edit(deleteBefore, text, kept)
    }

    private fun wantsTsheg(word: String, after: CharSequence?): Boolean {
        if (word.isEmpty() || !isLetterOrVowel(word.last())) return false
        // Unknown text after the cursor: a missing tsheg is cheaper than a doubled one.
        if (after == null) return false
        val next = after.firstOrNull() ?: return true
        return when {
            isTsheg(next) -> false
            isShad(next) -> endsWithNga(word)
            else -> true
        }
    }

    companion object {
        const val TSHEG = '་'
        private const val TSHEG_NO_BREAK = '༌'
        private const val NGA = 'ང'
        private const val SUBJOINED_NGA = 'ྔ'
        private const val LOOKBACK_MARGIN = 8

        fun isTsheg(c: Char): Boolean = c == TSHEG || c == TSHEG_NO_BREAK

        /** Shad (།), nyis shad (༎) and the other clause-ending marks, plus gter tsheg (༔). */
        fun isShad(c: Char): Boolean = c in '།'..'༒' || c == '༔'

        /** A key that ends the run the word in progress belongs to. */
        fun endsRun(c: Char): Boolean = isShad(c) || c == ' ' || c == '\n'

        /**
         * True when the syllable's last letter is nga (ང, or subjoined ྔ in a stack), vowel signs
         * aside. Such a syllable keeps its tsheg before a shad.
         */
        fun endsWithNga(syllable: CharSequence): Boolean {
            var i = syllable.length - 1
            while (i >= 0 && isVowelOrMark(syllable[i])) i--
            return i >= 0 && (syllable[i] == NGA || syllable[i] == SUBJOINED_NGA)
        }

        /** Vowel signs and the marks that sit on a syllable's last letter. */
        private fun isVowelOrMark(c: Char): Boolean = c in 'ཱ'..'྄' || c == '྆' || c == '྇'

        private fun isLetterOrVowel(c: Char): Boolean =
            c in 'ཀ'..'ཬ' || c in 'ྐ'..'ྼ' || isVowelOrMark(c)
    }
}
