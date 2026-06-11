package com.kharagedition.tibetankeyboard.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors

/**
 * Standard screen container: full-bleed warm background behind the system bars, with
 * content inset below the status bar / above the navigation bar and (optionally) scrollable.
 * Removes the repeated `fillMaxSize + background + insets + verticalScroll` boilerplate.
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
    val base = modifier
        .fillMaxSize()
        .background(background)
        .statusBarsPadding()
        .navigationBarsPadding()
    Column(
        modifier = (if (scrollable) base.verticalScroll(rememberScrollState()) else base)
            .padding(horizontal = horizontalPadding)
            .padding(bottom = bottomPadding),
        content = content,
    )
}
