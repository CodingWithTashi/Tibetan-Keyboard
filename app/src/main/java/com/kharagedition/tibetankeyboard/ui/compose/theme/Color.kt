package com.kharagedition.tibetankeyboard.ui.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * Warm brown/gold "monk-robe" palette.
 * Mirrors docs/tibetan-keyboard/project/tokens.css 1:1 so the Compose UI matches
 * the Claude Design handoff exactly.
 */
object TibetanColors {
    // Brown surface ramp (deep espresso -> warm brown)
    val Espresso = Color(0xFF1C1305)
    val Bg900 = Color(0xFF241808)
    val Bg800 = Color(0xFF2E2009)
    val Brown700 = Color(0xFF3C2B0E)
    val Brown600 = Color(0xFF4D3812)
    val Brown500 = Color(0xFF634916) // the signature brand brown
    val Brown400 = Color(0xFF7A5C1F)
    val Brown300 = Color(0xFF94732C)

    // Gold / saffron accent (monk-robe gold)
    val Gold500 = Color(0xFFC8881F)
    val Gold400 = Color(0xFFE3A53A)
    val Gold300 = Color(0xFFF2C264)
    val Gold200 = Color(0xFFF7D899)

    // Maroon — secondary Tibetan robe tone, used sparingly
    val Maroon = Color(0xFF8E2E27)
    val MaroonSoft = Color(0xFFB24A3F)
    val MaroonDeep = Color(0xFF5C1D18)

    // Text on warm surfaces
    val Cream = Color(0xFFFCF5E8)
    val Cream2 = Color(0xFFEBDCC0)
    val CreamDim = Color(0xFFC7B284)
    val CreamFaint = Color(0xFFFCF5E8).copy(alpha = 0.55f)
    val Line = Color(0xFFFCF5E8).copy(alpha = 0.10f)
    val Line2 = Color(0xFFFCF5E8).copy(alpha = 0.16f)

    // Success / live
    val Jade = Color(0xFF5BB98C)
}
