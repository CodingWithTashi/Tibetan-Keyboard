package com.kharagedition.tibetankeyboard.ui.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Radii, gradients and elevation tokens mirroring tokens.css.
 * Gradients are exposed as factory functions because Compose [Brush]es are not const.
 */
object TibetanTokens {
    // Radii
    val RadiusKey = RoundedCornerShape(10.dp)
    val RadiusCard = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(999.dp)

    // --grad-gold: linear-gradient(135deg, gold-300 0%, gold-400 48%, gold-500 100%)
    val GradGold = Brush.linearGradient(
        colorStops = arrayOf(
            0f to TibetanColors.Gold300,
            0.48f to TibetanColors.Gold400,
            1f to TibetanColors.Gold500,
        )
    )

    // The 180deg gold gradient used on pills, toggles, keys: gold-300 -> gold-500
    val GoldVertical = Brush.verticalGradient(
        listOf(TibetanColors.Gold300, TibetanColors.Gold500)
    )

    // Brighter gold for CTAs: gold-200 -> gold-500
    val GoldVerticalBright = Brush.verticalGradient(
        listOf(TibetanColors.Gold200, TibetanColors.Gold500)
    )

    // text-gold: linear-gradient(135deg, gold-200, gold-400)
    val GoldText = Brush.linearGradient(
        listOf(TibetanColors.Gold200, TibetanColors.Gold400)
    )

    // --grad-brown: linear-gradient(170deg, brown-600 0%, bg-800 100%)
    val GradBrown = Brush.linearGradient(
        colors = listOf(TibetanColors.Brown600, TibetanColors.Bg800),
        start = Offset(0f, 0f),
        end = Offset(0.17f, 1f),
    )
}
