package com.kharagedition.botok

// Port of botok/vars.py
// Mirrors Python IntEnum values exactly (1-based for CharMarkers, 100-based for ChunkMarkers, 1000-based for WordMarkers)

const val BOTOK_VERSION = "0.9.0"

// Tibetan Unicode constants
const val TSEK = "་"       // U+0F0B tsheg / syllable separator
const val NAMCHE = "ཿ"     // U+0F7F namche / visarga (rnam bcad)
const val SHAD = "།"       // U+0F0D shad / sentence boundary
const val AA = "འ"         // U+0F60 a-chen
const val HASH = "#"
const val NO_POS = "NOPOS"

val VOWELS: Set<String> = setOf("ི")
val NO_SHAD_CONS: List<String> = listOf("ཀ", "ག", "ཤ")
val DAGDRA: List<String> = listOf("པ་", "པོ་", "བ་", "བོ་")

// ---------------------------------------------------------------------------
// CharMarkers — mirrors Python IntEnum("CharMarkers", [...], start=1)
// ---------------------------------------------------------------------------
object CharMarkers {
    const val CONS = 1
    const val SUB_CONS = 2
    const val VOW = 3
    const val TSEK = 4
    const val NORMAL_PUNCT = 5
    const val SPECIAL_PUNCT = 6
    const val NUMERAL = 7
    const val SYMBOL = 8
    const val IN_SYL_MARK = 9
    const val NON_BO_NON_SKRT = 10
    const val SKRT_CONS = 11
    const val SKRT_SUB_CONS = 12
    const val SKRT_VOW = 13
    const val SKRT_LONG_VOW = 14
    const val CJK = 15
    const val LATIN = 16
    const val OTHER = 17
    const val TRANSPARENT = 18
    const val NFC = 19

    val valueToName: Map<Int, String> = mapOf(
        CONS to "CONS",
        SUB_CONS to "SUB_CONS",
        VOW to "VOW",
        TSEK to "TSEK",
        NORMAL_PUNCT to "NORMAL_PUNCT",
        SPECIAL_PUNCT to "SPECIAL_PUNCT",
        NUMERAL to "NUMERAL",
        SYMBOL to "SYMBOL",
        IN_SYL_MARK to "IN_SYL_MARK",
        NON_BO_NON_SKRT to "NON_BO_NON_SKRT",
        SKRT_CONS to "SKRT_CONS",
        SKRT_SUB_CONS to "SKRT_SUB_CONS",
        SKRT_VOW to "SKRT_VOW",
        SKRT_LONG_VOW to "SKRT_LONG_VOW",
        CJK to "CJK",
        LATIN to "LATIN",
        OTHER to "OTHER",
        TRANSPARENT to "TRANSPARENT",
        NFC to "NFC"
    )

    fun nameToValue(name: String): Int =
        valueToName.entries.first { it.value == name }.key
}

// ---------------------------------------------------------------------------
// ChunkMarkers — mirrors Python IntEnum("ChunkMarkers", [...], start=100)
// ---------------------------------------------------------------------------
object ChunkMarkers {
    const val BO = 100
    const val LATIN = 101
    const val CJK = 102
    const val OTHER = 103
    const val TEXT = 104
    const val PUNCT = 105
    const val NON_PUNCT = 106
    const val SPACE = 107
    const val NON_SPACE = 108
    const val SYM = 109
    const val NON_SYM = 110
    const val NUM = 111
    const val NON_NUM = 112

    val valueToName: Map<Int, String> = mapOf(
        BO to "BO",
        LATIN to "LATIN",
        CJK to "CJK",
        OTHER to "OTHER",
        TEXT to "TEXT",
        PUNCT to "PUNCT",
        NON_PUNCT to "NON_PUNCT",
        SPACE to "SPACE",
        NON_SPACE to "NON_SPACE",
        SYM to "SYM",
        NON_SYM to "NON_SYM",
        NUM to "NUM",
        NON_NUM to "NON_NUM"
    )

    fun nameToValue(name: String): Int =
        valueToName.entries.first { it.value == name }.key
}

// ---------------------------------------------------------------------------
// chunk_values: maps chunk marker ints to human-readable names (from vars.py)
// ---------------------------------------------------------------------------
val chunkValues: Map<Int, String> = mapOf(
    ChunkMarkers.BO to "BO",
    ChunkMarkers.LATIN to "LATIN",
    ChunkMarkers.CJK to "CJK",
    ChunkMarkers.OTHER to "OTHER",
    ChunkMarkers.TEXT to "TEXT",
    ChunkMarkers.PUNCT to "PUNCT",
    ChunkMarkers.NON_PUNCT to "NON_PUNCT",
    ChunkMarkers.SPACE to "SPACE",
    ChunkMarkers.NON_SPACE to "NON_SPACE",
    ChunkMarkers.SYM to "SYM",
    ChunkMarkers.NON_SYM to "NON_SYM",
    ChunkMarkers.NUM to "NUM",
    ChunkMarkers.NON_NUM to "NON_NUM"
)

// ---------------------------------------------------------------------------
// WordMarkers — mirrors Python IntEnum("WordMarkers", [...], start=1000)
// ---------------------------------------------------------------------------
object WordMarkers {
    const val WORD = 1000
    const val NO_POS = 1001
    const val NON_WORD = 1002

    val valueToName: Map<Int, String> = mapOf(
        WORD to "WORD",
        NO_POS to "NO_POS",
        NON_WORD to "NON_WORD"
    )
}
