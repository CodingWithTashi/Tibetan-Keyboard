package com.kharagedition.tibetankeyboard.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/** Provides the bundled Tibetan Uchen [FontFamily] down the tree. */
val LocalTibetanFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

private val TibetanColorScheme = darkColorScheme(
    primary = TibetanColors.Gold400,
    onPrimary = TibetanColors.Espresso,
    secondary = TibetanColors.Brown500,
    onSecondary = TibetanColors.Cream,
    background = TibetanColors.Bg900,
    onBackground = TibetanColors.Cream,
    surface = TibetanColors.Brown700,
    onSurface = TibetanColors.Cream,
    surfaceVariant = TibetanColors.Brown600,
    onSurfaceVariant = TibetanColors.Cream2,
    error = TibetanColors.MaroonSoft,
)

/**
 * App-wide Compose theme. Always the warm dark-brown scheme (the design is a
 * single premium dark-warm look, independent of system light/dark).
 */
@Composable
fun TibetanKeyboardTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(
        LocalTibetanFont provides TibetanType.tibetan(context)
    ) {
        MaterialTheme(
            colorScheme = TibetanColorScheme,
            typography = TibetanTypography,
            content = content,
        )
    }
}
