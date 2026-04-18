package com.kharagedition.botok.tokenizers

import com.kharagedition.botok.AA
import com.kharagedition.botok.TSEK
import com.kharagedition.botok.chunks.TokChunks
import com.kharagedition.botok.config.Config
import com.kharagedition.botok.modifytokens.AdjustTokens
import com.kharagedition.botok.modifytokens.MergeDagdra
import com.kharagedition.botok.modifytokens.SplitAffixed
import com.kharagedition.botok.textunits.BoSyl
import com.kharagedition.botok.textunits.SylComponents
import com.kharagedition.botok.tries.Trie
import java.io.BufferedReader
import java.io.File

/**
 * Port of botok/tokenizers/wordtokenizer.py — WordTokenizer
 *
 * Convenience class to tokenize a given string.
 * Orchestrates the full tokenization pipeline:
 *   TokChunks → Tokenize → splitAffixed → getDefaultLemma → chooseDefaultEntry → MergeDagdra → AdjustTokens
 */
class WordTokenizer(
    configParam: Config? = null,
    ignoreCharsParam: Set<Char>? = null,
    buildTrie: Boolean = false
) {

    private val config: Config = configParam ?: Config(getDefaultPackPath())
    private val ignoreChars: Set<Char> = ignoreCharsParam ?: emptySet()
    private val tok: Tokenize
    private val adj: AdjustTokens
    private val partLemmas: Map<String, String>

    init {
        // Build trie with BoSyl instance, config dictionary and adjustments
        val boSyl = BoSyl()  // Create instance using no-arg constructor
        val trie = Trie(
            boSyl,
            config.packPath.substringAfterLast("/"),  // profile is the last part of path
            config.dictionary,
            config.adjustments
        )

        this.tok = Tokenize(trie)

        // AdjustTokens with rules (Phase 7: full implementation)
        this.adj = AdjustTokens(
            mainRules = config.dictionary["rules"]?.map { it.toString() },
            customRules = config.adjustments["rules"]?.map { it.toString() }
        )

        // Load particle lemmas
        this.partLemmas = getPartLemmas()
    }

    /**
     * Main tokenization method.
     * Port of Python's tokenize(string, split_affixes=True, spaces_as_punct=False, debug=False).
     *
     * @param string to be tokenized
     * @param splitAffixes separates the affixed particles into separate tokens if true
     * @param spacesAsPunct treats spaces as punctuation if true
     * @param debug prints debug info while parsing
     * @return list of Token objects
     */
    fun tokenize(
        string: String,
        splitAffixes: Boolean = true,
        spacesAsPunct: Boolean = false,
        debug: Boolean = false
    ): List<Token> {
        // Preprocess: create chunks and serve syllables to trie
        val preprocessed = TokChunks(string, ignoreChars, spacesAsPunct)
        preprocessed.serveSylsToTrie()

        // Tokenize using max-match algorithm
        var tokens = tok.tokenize(preprocessed, debug = debug)

        // Split affixed particles if requested
        if (splitAffixes) {
            val mutableTokens = tokens.toMutableList()
            SplitAffixed.splitAffixed(mutableTokens)
            tokens = mutableTokens
        }

        // Get default lemmas for tokens
        getDefaultLemma(tokens)

        // Choose default entry (POS, lemma, freq, sense) from senses
        chooseDefaultEntry(tokens)

        // Merge pa/po/ba/bo tokens with previous ones
        MergeDagdra().merge(tokens.toMutableList())

        // Apply adjustment rules (Phase 7: full CQL-based rules)
        tokens = adj.adjust(tokens)

        return tokens
    }

    /**
     * Add default lemmas to tokens.
     * Port of Python's _get_default_lemma().
     */
    private fun getDefaultLemma(tokenList: List<Token>) {
        for (t in tokenList) {
            // Pass any token that is not a word
            if (t.textUnaffixed.isEmpty()) {
                continue
            }

            val lemma = when {
                // Affix particle
                t.affix && !t.affixHost -> {
                    val part = t.sylsJoined.joinToString("") { syl -> syl }
                    val partLemma = partLemmas[part] ?: part
                    partLemma + TSEK
                }
                // Affix host (needs AA if required)
                !t.affix && t.affixHost -> {
                    val hasAa = t.affixation["aa"] as? Boolean ?: false
                    if (hasAa) {
                        t.textUnaffixed + AA + TSEK
                    } else {
                        t.textUnaffixed + TSEK
                    }
                }
                // Regular word
                else -> {
                    if (t.textUnaffixed.endsWith(TSEK)) {
                        t.textUnaffixed
                    } else {
                        t.textUnaffixed + TSEK
                    }
                }
            }

            // Add lemma to senses
            val needsLemmaUpdate = t.senses?.any { m ->
                "lemma" !in m && ("pos" in m && m["pos"] != "NON_WORD")
            } ?: false

            if (needsLemmaUpdate) {
                t.senses = t.senses?.map { m ->
                    if ("lemma" !in m && ("pos" in m && m["pos"] != "NON_WORD")) {
                        val mutableM = m.toMutableMap()
                        mutableM["lemma"] = lemma
                        mutableM
                    } else {
                        m
                    }
                }?.toMutableList()
            }

            if (t.senses.isNullOrEmpty()) {
                t.senses = mutableListOf(mapOf("lemma" to lemma))
            }
        }
    }

    /**
     * Choose default entry (POS, lemma, freq, sense) from senses.
     * Port of Python's _choose_default_entry().
     */
    private fun chooseDefaultEntry(tokenList: List<Token>) {
        for (t in tokenList) {
            if (t.senses.isNullOrEmpty()) {
                continue
            }

            // Categorize all meanings in three groups
            val affixed = mutableListOf<Map<String, Any?>>()
            val nonAffixed = mutableListOf<Map<String, Any?>>()
            val no = mutableListOf<Map<String, Any?>>()

            for (m in t.senses!!) {
                if ("affixed" in m) {
                    if (m["affixed"] == true) {
                        affixed.add(m)
                    } else {
                        nonAffixed.add(m)
                    }
                } else {
                    no.add(m)
                }
            }

            // Decide what meaning to use as default
            // Order of preference: non_affixed, no, affixed
            // Take the one with the highest amount of attrs
            when {
                nonAffixed.isNotEmpty() -> chooseAndApply(nonAffixed, t)
                no.isNotEmpty() -> chooseAndApply(no, t)
                affixed.isNotEmpty() -> chooseAndApply(affixed, t)
                else -> throw IllegalStateException("This should never happen.")
            }
        }
    }

    /**
     * Choose the meaning with most attributes and apply to token.
     * Only sets fields that are not already set (preserves manual overrides like splitAffixed).
     */
    private fun chooseAndApply(senses: List<Map<String, Any?>>, token: Token) {
        val sorted = senses.sortedByDescending { it.size }
        val default = sorted[0]

        // Only set fields that are not already set
        // This preserves manual overrides from splitAffixed
        for (attr in listOf("pos", "lemma", "freq", "sense")) {
            if (attr in default) {
                when (attr) {
                    "pos" -> if (token.pos.isEmpty()) token.pos = default[attr] as String
                    "lemma" -> if (token.lemma.isEmpty()) token.lemma = default[attr] as String
                    "freq" -> if (token.freq == null) token.freq = default[attr] as? Int
                    "sense" -> if (token.sense.isEmpty()) token.sense = default[attr] as String
                }
            }
        }
    }

    /**
     * Load particle lemmas from particles.tsv.
     * Port of Python's get_part_lemmas().
     */
    private fun getPartLemmas(): Map<String, String> {
        val partLemmas = mutableMapOf<String, String>()

        try {
            val particlesPath = "${config.packPath}/dictionary/words_non_inflected/particles.tsv"
            val file = File(particlesPath)
            if (!file.exists()) {
                return partLemmas
            }

            BufferedReader(file.reader()).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    // Skip comments and empty lines
                    if (line!!.startsWith("#") || line!!.trim().isEmpty()) {
                        continue
                    }

                    val parts = line!!.split("\t")
                    if (parts.size >= 3) {
                        val form = parts[0]
                        val lemma = parts[2]
                        partLemmas[form] = lemma
                    }
                }
            }
        } catch (e: Exception) {
            // File not found or error reading - return empty map
        }

        return partLemmas
    }

    companion object {
        /**
         * Get default pack path for general dictionary.
         */
        private fun getDefaultPackPath(): String {
            // For unit tests, use the file system path
            val assetsPath = System.getProperty("user.dir") + "/src/main/assets/botok/general"
            val file = File(assetsPath)
            return if (file.exists()) {
                file.absolutePath
            } else {
                // Fallback for Android
                "botok/general"
            }
        }
    }
}
