package com.kharagedition.tibetankeyboard.ui.compose.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em

/**
 * Typography.
 *
 * The design calls for "Plus Jakarta Sans" for UI text and "Noto Sans Tibetan"
 * for Tibetan script. We use the system sans family for UI (always available,
 * no network download) and the bundled Uchen font (assets/fonts) for Tibetan
 * script, which renders the རྒྱ Uchen style the design intends.
 */
object TibetanType {
    /** UI font — system sans (stands in for Plus Jakarta Sans). */
    val Ui: FontFamily = FontFamily.Default

    /**
     * Tibetan font. We use the platform default family: Android's Tibetan fallback is
     * Noto Sans Tibetan, a clean Uchen (དབུ་ཅན — block-letter) face. This guarantees
     * Uchen rendering, matches the IME (which also uses the system font), and avoids
     * bundling a font (the previously bundled Uchen TTF was empty/corrupt).
     */
    @Suppress("UNUSED_PARAMETER")
    fun tibetan(context: Context): FontFamily = FontFamily.Default
}

/**
 * Line height of every `Text` that does not set its own, as a multiple of its font size. Screens
 * set sizes inline, and Material's fixed 24sp was double the height of a 12sp caption (wrapped
 * captions looked double-spaced on narrow phones and at large font sizes) and no taller than a
 * 24sp title.
 */
val UiLineHeight = 1.4.em

/** Tibetan stacks reach above and below a Latin line, so Tibetan-only text gets more room. */
val TibetanLineHeight = 1.6.em

/** Material3 typography built on the UI font; per-screen sizes are set inline. */
val TibetanTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = TibetanType.Ui),
        headlineLarge = headlineLarge.copy(fontFamily = TibetanType.Ui),
        headlineMedium = headlineMedium.copy(fontFamily = TibetanType.Ui),
        titleLarge = titleLarge.copy(fontFamily = TibetanType.Ui),
        titleMedium = titleMedium.copy(fontFamily = TibetanType.Ui),
        // The style `Text` falls back to.
        bodyLarge = bodyLarge.copy(fontFamily = TibetanType.Ui, lineHeight = UiLineHeight),
        bodyMedium = bodyMedium.copy(fontFamily = TibetanType.Ui),
        labelLarge = labelLarge.copy(fontFamily = TibetanType.Ui),
        labelMedium = labelMedium.copy(fontFamily = TibetanType.Ui),
    )
}
