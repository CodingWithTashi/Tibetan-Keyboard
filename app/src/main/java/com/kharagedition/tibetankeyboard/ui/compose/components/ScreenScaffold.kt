package com.kharagedition.tibetankeyboard.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors

/**
 * The widest a screen's content grows. Past it (tablets, unfolded foldables, a phone on its
 * side) the content stays a centred column instead of stretching edge to edge.
 */
val ScreenMaxWidth = 600.dp

/**
 * Standard screen container: full-bleed warm background, with the content kept clear of
 * everything that can cover it — status and navigation bars, the display cutout (it sits on a
 * side in landscape) and the keyboard — and (optionally) scrollable. With the keyboard counted,
 * a focused field scrolls into view instead of the window sliding up under the status bar.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    background: Color = TibetanColors.Bg900,
    scrollable: Boolean = true,
    horizontalPadding: Dp = 0.dp,
    bottomPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // The whole width scrolls, not just the centred column.
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ScreenMaxWidth)
                .fillMaxWidth()
                .then(if (scrollable) Modifier else Modifier.fillMaxHeight())
                .padding(horizontal = horizontalPadding)
                .padding(bottom = bottomPadding),
            content = content,
        )
    }
}
