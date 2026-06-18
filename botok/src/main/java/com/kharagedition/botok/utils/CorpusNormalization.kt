package com.kharagedition.botok.utils

/**
 * Port of botok/utils/corpus_normalization.py - Corpus Normalization
 *
 * General-purpose Unicode normalization for Tibetan text corpus processing
 */
object CorpusNormalization {
    // Normalize all line breaks to '\n'
    private val LINEBREAKS_RE = Regex("\\r\\n?|\\u0085|\\u2028|\\u2029")

    // Zero-width and invisible characters to remove (includes BOM everywhere)
    private val ZERO_WIDTH_STRIP = setOf(
        "\u200B",  // ZERO WIDTH SPACE
        "\u2060",  // WORD JOINER
        "\uFEFF",  // ZERO WIDTH NO-BREAK SPACE / BOM (remove even mid-text)
        "\u180E",  // MONGOLIAN VOWEL SEPARATOR (deprecated)
        "\u034F",  // COMBINING GRAPHEME JOINER
    )

    // Map all Unicode spaces (and horizontal ASCII whitespace) to ASCII space
    private val UNICODE_SPACES = listOf(
        "\u00A0",  // NO-BREAK SPACE
        "\u1680", "\u2000", "\u2001", "\u2002", "\u2003", "\u2004",
        "\u2005", "\u2006", "\u2007", "\u2008", "\u2009", "\u200A",
        "\u202F", "\u205F", "\u3000",  // narrow, medium, ideographic spaces
        "\t", "\u000B", "\u000C"           // TAB, VT, FF
    )

    private val SPACE_TO_ASCII = UNICODE_SPACES.associateWith { " " }

    // Tibetan-specific regex patterns
    private val TSHEG_OR_SPACE_RE = Regex("(\u0F0B| )")
    private val MULTI_TSHEG_RE = Regex("\\u0F0B{2,}")
    private val LETTER_BEFORE_NL_RE = Regex("([\\u0F40-\\u0FBC])\\n")
    private val GA_SHA_KA_NL_RE = Regex("([གཤཀ][\\u0F71-\\u0F84]?)\\n")

    // U+0FD2 is excluded (NYIS TSHEG → converted to U+0F0B earlier).
    // U+0FD5-U+0FD8 are svasti/auspicious signs, structurally identical to yig-mgo.
    private val YIG_MGO_START = "\u0F01-\u0F07\u0F09\u0F0A\u0FD0\u0FD1\u0FD3-\u0FD8"
    private val PUNCT = "\u0F0D-\u0F14"
    private val LETTER = "\u0F40-\u0FBC"

    // Compiled patterns for normalize_for_perplexity
    private val VOWEL = "\u0F71-\u0F84"
    private val YIG_MGO_RE = Regex("[$YIG_MGO_START]+[$PUNCT]*")
    private val DIGIT_RUN_RE = Regex("[0-9\u0F20-\u0F33][0-9\u0F20-\u0F33, ]*")
    private val NON_TIBETAN_RE = Regex("[^\u0F00-\u0FFF D]")
    private val PUNCT_OR_SPACE_RE = Regex("[$PUNCT ]+")
    private val MULTI_SPACE_RE = Regex(" {2,}")
    private val BRACKET_RE = Regex("([\u0F3C\u0F3D])")

    // Case affixes: always start with འ (U+0F60) followed by a specific letter.
    // Longest alternative (འིས) must precede prefix it shares (འི).
    private val AFFIX_RE = Regex("^([$LETTER]+)(འིས|འི|འོ|འམ|འང|འས|འད|འར)$")

    /**
     * Normalize spaces in text.
     *
     * Steps:
     *   1. Collapse multiple newlines to one.
     *   2. Remove spaces next to newlines.
     *   3. Collapse multiple spaces to one.
     *   4. Apply Tibetan-specific space normalization rules.
     *
     * Tibetan-specific rules:
     *   - Remove space after tsheg (U+0F0B, U+0F0C, U+0FD2) if followed by
     *     initial letter (U+0F40-U+0F6C) or shad (U+0F0D-U+0F11)
     *   - Remove space between final letter (U+0F40-U+0FBC) and tsheg
     *
     * @param text Input text
     * @param collapseInternalSpaces Whether to collapse multiple spaces
     * @param tibetanSpecific Whether to apply Tibetan-specific rules
     * @return Normalized text
     */
    fun normalizeSpaces(
        text: String,
        collapseInternalSpaces: Boolean = true,
        tibetanSpecific: Boolean = true
    ): String {
        if (text.isEmpty()) return ""

        var s = text

        // 0) Map Unicode line endings to '\n', Unicode spaces/tabs to ASCII space
        s = LINEBREAKS_RE.replace(s, "\n")
        s = ZERO_WIDTH_STRIP.fold(s) { acc, char -> acc.replace(char, "") }
        s = SPACE_TO_ASCII.entries.fold(s) { acc, (from, to) -> acc.replace(from, to) }

        // 1) Collapse multiple newlines
        s = s.replace(Regex("\\n{2,}"), "\n")

        // 2) Remove spaces next to newlines
        s = s.replace(Regex("[ ]+\\n"), "\n")
        s = s.replace(Regex("\\n[ ]+"), "\n")

        // 3) Collapse space runs
        if (collapseInternalSpaces) {
            s = s.replace(Regex(" {2,}"), " ")
        }

        // 4) Tibetan-specific space normalization
        if (tibetanSpecific) {
            // Remove space after tsheg if followed by initial letter or shad
            s = s.replace(Regex("([\\u0f0b\\u0f0c\\u0fd2]) +([\\u0f40-\\u0f6c\\u0f0d-\\u0f11])"), "$1$2")
            // Remove space between final letter and tsheg
            s = s.replace(Regex("([\\u0f40-\\u0fbc]) +([\\u0f0b\\u0f0c\\u0fd2])"), "$1$2")
        }

        return s
    }

    /**
     * General-purpose Unicode normalization.
     *
     * Steps:
     *   1. Normalize to NFC.
     *   2. Convert all line breaks to '\n'.
     *   3. Remove zero-width / invisible characters (incl. all BOMs).
     *   4. Map Unicode spaces and tabs to plain ASCII space.
     *   5. Optionally remove control characters (except newline).
     *   6. Normalize spaces (including Tibetan-specific rules).
     *   7. Apply Tibetan Unicode normalization.
     *
     * Keeps ZWJ/ZWNJ (joiners) intact.
     *
     * @param text Input text
     * @param stripControl Whether to remove control characters
     * @param collapseInternalSpaces Whether to collapse multiple spaces
     * @return Normalized text
     */
    fun normalizeCorpus(
        text: String,
        stripControl: Boolean = true,
        collapseInternalSpaces: Boolean = true
    ): String {
        if (text.isEmpty()) return ""

        // 1) NFC normalization
        var s = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)

        // 5) Optionally strip control characters (but keep newline)
        if (stripControl) {
            s = s.filter { ch -> ch == '\n' || Character.getType(ch.code) != Character.CONTROL.toInt() }
        }

        // 6) Normalize spaces
        s = normalizeSpaces(s, collapseInternalSpaces)

        // 7) Tibetan Unicode normalization
        s = UnicodeNormalization.normalizeUnicode(s)
        // no graphical distinction between 0f0b and 0f0c
        s = s.replace("\u0f0c", "\u0f0b")
        // double shad is just two shad
        s = s.replace("\u0f0e", "\u0f0d\u0f0d")

        return s
    }

    /**
     * Merge a multi-line Tibetan string into a single continuous line.
     *
     * Designed for word-wrapped or page-OCR'd text where newlines are
     * typographic artefacts rather than sentence boundaries.
     *
     * Steps:
     *   1. Normalize line-break sequences (CRLF, CR, NEL, LS, PS) to LF.
     *   2. Remove ASCII spaces / tabs immediately before or after each LF.
     *   3. Fold runs of two or more tshegs (U+0F0B) that sit at the very end
     *      or very beginning of a line down to a single tsheg.
     *   4. When a Tibetan letter (U+0F40-U+0FBC) ends a line with no trailing
     *      tsheg, insert a space between it and LF so that syllable
     *      boundary is preserved after LF is removed.
     *   5. Remove all remaining LF characters.
     *
     * @param text Multi-line text to merge
     * @return Single-line text
     */
    fun mergeLines(text: String): String {
        // 1) Normalize line endings
        var s = LINEBREAKS_RE.replace(text, "\n")

        // 2) Remove spaces/tabs around newlines
        s = s.replace(Regex("[ \\t]+\\n"), "\n")
        s = s.replace(Regex("\\n[ \\t]+"), "\n")

        // 3) Fold tsheg runs at line boundaries to one tsheg
        s = s.replace(Regex("\\u0F0B{2,}(?=\\n)"), "\u0F0B")
        s = s.replace(Regex("(?<=\\n)\\u0F0B{2,}"), "\u0F0B")

        // 4) Letter at line-end without trailing tsheg → insert space
        s = LETTER_BEFORE_NL_RE.replace(s, "$1 \n")

        // 5) Drop all newlines
        s = s.replace("\n", "")

        return s
    }

    /**
     * Recursively split Tibetan case affixes from a syllable body.
     *
     * Affixes (འི, འོ, འམ, འང, འིས, འར, འད, འས) always start with འ
     * followed by a specific letter. The function peels off one affix per
     * call and recurses on the remaining stem, joining parts with spaces.
     *
     * Special case: a syllable ending in ``འུར`` has its final ``ར`` split
     * off as a separate token (the ``འུ`` oblique marker stays with the
     * stem).
     *
     * Examples:
     *   _splitSyllableAffixes("རྒྱལའི")  → "རྒྱལ འི"
     *   _splitSyllableAffixes("པའིའོ")   → "པ འི འོ"
     *   _splitSyllableAffixes("བཀའུར")   → "བཀའུ ར"
     *
     * @param syllable Syllable to split
     * @return Syllable with affixes separated by spaces
     */
    private fun splitSyllableAffixes(syllable: String): String {
        // *འུར: split off the allative ར, keep the oblique འུ with the stem
        if (syllable.endsWith("\u0F60\u0F74\u0F62") && syllable.length > 3) {
            return splitSyllableAffixes(syllable.dropLast(1)) + " \u0F62"
        }

        val match = AFFIX_RE.find(syllable)
        if (match == null) {
            return syllable
        }

        val stem = match.groupValues[1]
        val affix = match.groupValues[2]
        return splitSyllableAffixes(stem) + " " + affix
    }

    /**
     * Apply splitSyllableAffixes to every syllable token in text.
     *
     * Tokens are delimited by tshegs (U+0F0B) or spaces; only tokens that
     * contain at least one Tibetan letter are processed.
     *
     * @param text Text to process
     * @return Text with affixes split
     */
    private fun applyAffixSplits(text: String): String {
        val parts = TSHEG_OR_SPACE_RE.split(text).toMutableList()
        for (i in parts.indices step 2) {
            val part = parts[i]
            if (part.any { it.code in 0x0F40..0x0FBC }) {
                parts[i] = splitSyllableAffixes(part)
            }
        }
        return parts.joinToString("")
    }

    /**
     * Process non-standard (Sanskrit) syllables at tsheg-delimited level.
     *
     * Tshegs (U+0F0B) are syllable separators within a sentence; spaces
     * mark sentence/clause boundaries. The function walks each tsheg-delimited
     * piece and, for non-standard syllables:
     *
     * * spaceSskt: expands syllable into its constituent stacks,
     *   separated by spaces.
     * * foldSskt: accumulates consecutive non-standard syllables and
     *   replaces whole run with placeholder "S".
     *
     * @param text Text to process
     * @param spaceSskt Whether to space Sanskrit syllables
     * @param foldSskt Whether to fold Sanskrit syllables
     * @return Processed text
     */
    private fun processSskt(text: String, spaceSskt: Boolean, foldSskt: Boolean): String {
        val parts = TSHEG_OR_SPACE_RE.split(text)
        // parts alternates: [content₀, delim₀, content₁, delim₁, …, contentₙ]
        val out = mutableListOf<String>()
        var inSsktRun = false

        fun flushSskt() {
            if (inSsktRun) {
                out.add(" S")
                inSsktRun = false
            }
        }

        val n = parts.size
        for (i in 0 until n step 2) {
            val content = parts[i]
            val delim = if (i + 1 < n) parts[i + 1] else ""

            if (content.isEmpty()) {
                if (delim == " " && foldSskt) {
                    flushSskt()
                }
                if (delim.isNotEmpty()) {
                    out.add(delim)
                }
                continue
            }

            val hasTibetan = content.any { it.code in 0x0F40..0x0FBC }

            if (!hasTibetan) {
                if (foldSskt) {
                    flushSskt()
                }
                out.add(content)
                if (delim.isNotEmpty()) {
                    out.add(delim)
                }
                continue
            }

            val std = StandardTibetan.isStandardTibetan(content)

            if (std) {
                if (foldSskt && inSsktRun) {
                    flushSskt()
                    out.add(" ")
                }
                out.add(content)
                if (delim.isNotEmpty()) {
                    out.add(delim)
                }
            } else {
                if (foldSskt) {
                    inSsktRun = true
                    if (delim == " ") {
                        flushSskt()
                        out.add(delim)
                    }
                } else if (spaceSskt) {
                    val stacks = StandardTibetan.splitIntoStacks(content)
                    out.add(stacks.joinToString(" "))
                    if (delim.isNotEmpty()) {
                        out.add(delim)  // tsheg → space in step 10; space stays
                    }
                } else {
                    out.add(content)
                    if (delim.isNotEmpty()) {
                        out.add(delim)
                    }
                }
            }
        }

        if (foldSskt) {
            flushSskt()
        }

        var result = out.joinToString("")
        return MULTI_SPACE_RE.replace(result, " ")
    }

    /**
     * Normalize Tibetan text for perplexity calculation.
     *
     * Every sentence boundary — whether marked by Tibetan punctuation
     * (U+0F0D–U+0F14) or a plain space in source — is rendered as a shad
     * token (" ། ") surrounded by spaces. Syllables within a sentence are
     * separated by plain spaces (tshegs are removed). The result uses space
     * as the sole token delimiter and "།" as an explicit sentence-boundary
     * marker.
     *
     * @param text Input text
     * @param spaceSskt If true, non-standard syllables are split into stacks
     * @param foldSskt If true, consecutive non-standard syllables are collapsed to "S"
     * @return Normalized text for perplexity calculation
     */
    fun normalizeForPerplexity(
        text: String,
        spaceSskt: Boolean = true,
        foldSskt: Boolean = false
    ): String {
        var text = normalizeCorpus(text)

        // 1) NYIS TSHEG → TSHEG, then collapse runs of TSHEG to one
        text = text.replace("\u0FD2", "\u0F0B")
        text = MULTI_TSHEG_RE.replace(text, "\u0F0B")

        // 2) Remove honorific particles U+0F35 / U+0F37 and TSA-PHRU (U+0F39)
        text = text.filter { it !in setOf('\u0F35', '\u0F37', '\u0F39') }

        // 3) Normalize nasalization marks to RJES SU NGA RO (U+0F7E)
        text = text.replace("\u0F82", "\u0F7E").replace("\u0F83", "\u0F7E")

        // 3.5) Typographic shad: ག/ཤ/ཀ (+ optional vowel) at line end → add ། before \n
        text = GA_SHA_KA_NL_RE.replace(text, "$1\u0F0D\n")

        // 4) Any remaining Tibetan letter before \n → letter + tsheg + \n
        text = LETTER_BEFORE_NL_RE.replace(text, "$1\u0F0B\n")

        // 5) Drop all newlines
        text = text.replace("\n", "")

        // 6) Drop yig-mgo / svasti opening marks (+ optional trailing punctuation)
        text = YIG_MGO_RE.replace(text, "")

        // 7) Replace digit runs (Tibetan + ASCII, with commas) → placeholder D
        text = DIGIT_RUN_RE.replace(text, "D")

        // 8) Strip characters outside Tibetan block (keep D placeholder and spaces)
        text = NON_TIBETAN_RE.replace(text, " ")

        // 9) Any run of punctuation and/or spaces → shad token surrounded by spaces
        text = PUNCT_OR_SPACE_RE.replace(text, " \u0F0D ")

        // 9b) Surround Tibetan brackets with spaces
        text = BRACKET_RE.replace(text, " $1 ")

        // 9c) Split case affixes from syllable bodies
        text = applyAffixSplits(text)

        // 9d) Sanskrit syllable handling
        if (spaceSskt || foldSskt) {
            text = processSskt(text, spaceSskt, foldSskt)
        }

        // 10) Replace all tshegs with spaces; space is now sole token delimiter
        text = text.replace("\u0F0B", " ")
        text = MULTI_SPACE_RE.replace(text, " ")

        return text.trim()
    }

    /**
     * Run sanity checks for normalization functions
     */
    fun runSanityChecks() {
        // normalize_spaces: collapse newlines/spaces and trim around newlines
        val normalizeSpacesBasic = normalizeSpaces("a\n\n b  \n c")
        check(normalizeSpacesBasic == "a\nb\nc") {
            "normalize_spaces basic spacing failed: $normalizeSpacesBasic != a\\nb\\nc"
        }

        // normalize_spaces: Tibetan-specific spacing around tsheg and finals
        val tibetanSample = "\u0f0b \u0f40 \u0f66 \u0f0b"  // tsheg, initial, final, tsheg
        val normalizeSpacesTibetan = normalizeSpaces(tibetanSample)
        check(normalizeSpacesTibetan == "\u0f0b\u0f40 \u0f66\u0f0b") {
            "normalize_spaces tibetan spacing failed"
        }

        // normalize_corpus: line breaks, zero-width strip, space mapping, control strip,
        // Tibetan Unicode tweaks (0f0c→0f0b, 0f0e→double shad)
        val corpusSample = "a\u00a0\u200b b\r\nc\u0f0c\u0f0e\u0001"
        val normalizeCorpusFull = normalizeCorpus(corpusSample)
        check(normalizeCorpusFull == "a b\nc\u0f0b\u0f0d\u0f0d") {
            "normalize_corpus full pipeline failed: $normalizeCorpusFull"
        }
    }
}