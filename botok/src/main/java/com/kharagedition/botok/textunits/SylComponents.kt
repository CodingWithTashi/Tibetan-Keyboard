package com.kharagedition.botok.textunits

import kotlinx.serialization.json.*
import java.io.File

/**
 * Port of botok/textunits/sylcomponents.py
 *
 * Loads SylComponents.json and provides syllable decomposition methods:
 * - getParts()      → root + suffix decomposition
 * - getMingzhi()    → particle-agreement key
 * - getInfo()       → dadrag / thame / syllable-itself
 * - isThame()       → affixability indicator
 * - normalizeDadrag() → strip spurious da second-suffix
 */
open class SylComponents(jsonString: String) {

    // ------------------------------------------------------------------
    // Data fields from SylComponents.json
    // ------------------------------------------------------------------

    protected val dadrag: List<String>
    protected val roots: Map<String, String>
    protected val suffixes: List<String>
    protected val csuffixes: List<String>
    private val exceptions: Set<String>
    private val ambiguous: Map<String, List<String>>

    /** Combined mingzhi lookup: m_roots + m_exceptions + m_wazurs */
    private val mingzhis: Map<String, String>

    init {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        dadrag   = json["dadrag"]!!.jsonArray.map { it.jsonPrimitive.content }
        roots    = json["roots"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        suffixes = json["suffixes"]!!.jsonArray.map { it.jsonPrimitive.content }
        csuffixes = json["Csuffixes"]!!.jsonArray.map { it.jsonPrimitive.content }

        val special = json["special"]!!.jsonArray.map { it.jsonPrimitive.content }
        val wazurs  = json["wazurs"]!!.jsonArray.map { it.jsonPrimitive.content }
        exceptions  = (special + wazurs).toHashSet()

        ambiguous = json["ambiguous"]!!.jsonObject.mapValues { entry ->
            entry.value.jsonArray.map { it.jsonPrimitive.content }
        }

        val mRoots      = json["m_roots"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        val mExceptions = json["m_exceptions"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        val mWazurs     = json["m_wazurs"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        val merged = LinkedHashMap<String, String>(mRoots.size + mExceptions.size + mWazurs.size)
        merged.putAll(mRoots)
        merged.putAll(mExceptions)
        merged.putAll(mWazurs)
        mingzhis = merged
    }

    // ------------------------------------------------------------------
    // Return type for getParts()
    // ------------------------------------------------------------------

    sealed class SylParts {
        /** A single decomposition: root + suffix (suffix="" or "x" for exceptions). */
        data class Single(val root: String, val suffix: String) : SylParts()
        /** Multiple possible decompositions (or ambiguous entry) — caller treats as unknown. */
        object Multiple : SylParts()
    }

    // ------------------------------------------------------------------
    // getParts()
    // ------------------------------------------------------------------

    /**
     * Port of Python's get_parts(syl).
     *
     * @return SylParts.Single  — one unambiguous decomposition
     *         SylParts.Multiple — more than one solution or ambiguous
     *         null              — syllable is not well-formed
     */
    fun getParts(syl: String): SylParts? {
        // Exception syllables (special + wazurs) → return (syl, "x")
        if (syl in exceptions) return SylParts.Single(syl, "x")

        // Ambiguous → treat same as "multiple solutions" (getMingzhi will return null)
        if (syl in ambiguous) return SylParts.Multiple

        val lS = syl.length

        // Find all possible roots (longest prefix first, max 6 chars)
        val root = mutableListOf<String>()
        for (len in 6 downTo 1) {
            if (syl.length > len - 1) {
                val prefix = syl.substring(0, len)
                if (prefix in roots) root.add(prefix)
            }
        }

        // Find all possible suffixes (shortest first, max 5 chars from end)
        // Python uses syl[l_s-k:] which clips to index 0 for k > l_s
        val suffix = mutableListOf<String>()
        if (lS > 1) {
            for (offset in 1..5) {
                val start = (lS - offset).coerceAtLeast(0)
                val s = syl.substring(start)
                if (s in suffixes) suffix.add(s)
            }
        }

        // "C" (pure-consonant) roots have special handling
        if (root.isNotEmpty() && roots[root[0]] == "C") {
            if (root[0] == syl) return SylParts.Single(root[0], "")
            for (s in suffix) {
                if (s in csuffixes && root[0] + s == syl) return SylParts.Single(root[0], s)
            }
        }

        // Build all valid (root, suffix) pairs
        val solutions = mutableListOf<Pair<String, String>>()
        if (suffix.isNotEmpty() && root.isNotEmpty()) {
            for (r in root) {
                for (s in suffix) {
                    // Unexpected འ after "A"-type root
                    if (roots[r] == "A" && s == "འ" && r + s == syl) return null
                    val pair = Pair(r, s)
                    if (r + s == syl && pair !in solutions) solutions.add(pair)
                }
            }
            return when {
                solutions.isEmpty() -> null
                solutions.size > 1  -> SylParts.Multiple
                else                -> SylParts.Single(solutions[0].first, solutions[0].second)
            }
        } else if (root.isNotEmpty()) {
            for (r in root) {
                if (r in roots && r == syl && roots[r] != "NB") {
                    val pair = Pair(r, "")
                    if (pair !in solutions) solutions.add(pair)
                }
            }
            return when {
                solutions.isEmpty() -> null
                solutions.size > 1  -> SylParts.Multiple
                else                -> SylParts.Single(solutions[0].first, solutions[0].second)
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // normalizeDadrag()
    // ------------------------------------------------------------------

    /**
     * Port of Python's normalize_dadrag(syl).
     * If the syllable is invalid and ends with ད (possible dadrag), strip it.
     */
    fun normalizeDadrag(syl: String): String {
        val components = getParts(syl)
        if (components is SylParts.Multiple || components == null) {
            if (syl.isNotEmpty() && syl.last() == 'ད') {
                val trimmed = getParts(syl.dropLast(1))
                if (trimmed is SylParts.Single) return syl.dropLast(1)
            }
        }
        return syl
    }

    // ------------------------------------------------------------------
    // getMingzhi()
    // ------------------------------------------------------------------

    /**
     * Port of Python's get_mingzhi(syl).
     * Returns the mingzhi character used for particle agreement, or null.
     */
    fun getMingzhi(syl: String): String? {
        val components = getParts(syl)
        if (components is SylParts.Multiple || components == null) {
            // Try stripping dadrag ད
            if (syl.isNotEmpty() && syl.last() == 'ད') {
                val trimmed = getParts(syl.dropLast(1))
                if (trimmed is SylParts.Multiple || trimmed == null) return null
                return mingzhis[(trimmed as SylParts.Single).root]
            }
            return null
        }
        return mingzhis[(components as SylParts.Single).root]
    }

    // ------------------------------------------------------------------
    // getInfo()
    // ------------------------------------------------------------------

    /**
     * Port of Python's get_info(syl).
     * Returns "dadrag", "thame", the syllable itself, or null.
     */
    fun getInfo(syl: String): String? {
        val mingzhi = getMingzhi(syl) ?: return null
        return when {
            syl in dadrag -> "dadrag"
            Regex(
                Regex.escape(mingzhi) +
                "([ྱྲླྭྷ]?[ིེོུ]?(འ?[ིོུ]?ར?ས?|(འ[མང])|(འོའ[མང])|(འིའ[ོམང])))$"
            ).containsMatchIn(syl) -> "thame"
            else -> syl
        }
    }

    // ------------------------------------------------------------------
    // isThame()
    // ------------------------------------------------------------------

    /**
     * Port of Python's is_thame(syl).
     * True if the syllable is affixable or already affixed.
     */
    fun isThame(syl: String): Boolean = getInfo(syl) == "thame"

    companion object {
        private var defaultJsonString: String? = null

        /**
         * Load the default SylComponents.json from assets.
         * Can be called once to pre-load the data (optional).
         */
        fun initialize() {
            if (defaultJsonString == null) {
                val assetsPath = System.getProperty("user.dir") + "/src/main/assets/botok/resources/SylComponents.json"
                val file = File(assetsPath)
                if (file.exists()) {
                    defaultJsonString = file.readText()
                } else {
                    // For Android, this would need to be loaded via Context
                    defaultJsonString = "{}"
                }
            }
        }

        /**
         * Get or load the default JSON string.
         */
        private fun getDefaultJsonString(): String {
            if (defaultJsonString == null) {
                initialize()
            }
            return defaultJsonString!!
        }

        /**
         * Create a SylComponents instance with default JSON data.
         * Automatically loads the JSON if not already loaded.
         */
        fun createDefault(): SylComponents {
            return SylComponents(getDefaultJsonString())
        }
    }
}
