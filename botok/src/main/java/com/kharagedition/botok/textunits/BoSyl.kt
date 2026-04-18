package com.kharagedition.botok.textunits

/**
 * Port of botok/textunits/bosyl.py
 *
 * Extends SylComponents with affix-specific methods:
 * - isAffixable() — true only when a new particle can still attach
 * - getAllAffixed() — all 11 affixed forms with metadata
 */
class BoSyl(jsonString: String) : SylComponents(jsonString) {

    data class AffixMetadata(val len: Int, val type: String, val aa: Boolean)

    /**
     * Ordered map of affix string → base metadata (without 'aa' field).
     * Insertion order matches Python's dict literal — critical for output parity.
     */
    private val affixes: LinkedHashMap<String, Pair<Int, String>> = linkedMapOf(
        "ར"      to Pair(1, "la"),
        "ས"      to Pair(1, "gis"),
        "འི"    to Pair(2, "gi"),
        "འམ"    to Pair(2, "am"),
        "འང"    to Pair(2, "ang"),
        "འོ"    to Pair(2, "o"),
        "འིའོ" to Pair(4, "gi+o"),
        "འིའམ" to Pair(4, "gi+am"),
        "འིའང" to Pair(4, "gi+ang"),
        "འོའམ" to Pair(4, "o+am"),
        "འོའང" to Pair(4, "o+ang")
    )

    /**
     * Port of Python's is_affixable(syl).
     *
     * True when:
     * 1. isThame() is true (syllable can host a particle), AND
     * 2. the syllable does not already end with one of the "used" endings
     *    (ར, ས, འི, འོ, མ, ང) — which would mean a particle is already attached.
     */
    fun isAffixable(syl: String): Boolean {
        if (!isThame(syl)) return false
        for (ending in listOf("ར", "ས", "འི", "འོ", "མ", "ང")) {
            if (syl.length > ending.length && syl.endsWith(ending)) return false
        }
        return true
    }

    /**
     * Port of Python's get_all_affixed(syl).
     *
     * @return list of (affixedSyl, AffixMetadata) if affixable; null otherwise.
     */
    fun getAllAffixed(syl: String): List<Pair<String, AffixMetadata>>? {
        if (!isAffixable(syl)) return null

        // Strip trailing འ (the aa vowel) if present
        var base = syl
        val aa = base.endsWith("འ") && base.length > 1
        if (aa) base = base.dropLast(1)

        return affixes.map { (affix, meta) ->
            val (len, type) = meta
            Pair(base + affix, AffixMetadata(len, type, aa))
        }
    }

    companion object {
        /**
         * Create a BoSyl instance with default JSON data.
         * Matches Python's BoSyl() no-arg constructor.
         */
        operator fun invoke(): BoSyl {
            return BoSyl(getDefaultJsonString())
        }

        private fun getDefaultJsonString(): String {
            val assetsPath = System.getProperty("user.dir") + "/src/main/assets/botok/resources/SylComponents.json"
            val file = java.io.File(assetsPath)
            return if (file.exists()) {
                file.readText()
            } else {
                "{}"
            }
        }
    }
}
