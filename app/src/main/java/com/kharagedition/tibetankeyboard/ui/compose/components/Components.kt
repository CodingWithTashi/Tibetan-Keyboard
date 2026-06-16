package com.kharagedition.tibetankeyboard.ui.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.ui.compose.theme.LocalTibetanFont
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens
import com.kharagedition.tibetankeyboard.util.AiLimits

/** Tibetan-script text using the bundled Uchen font. */
@Composable
fun BoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TibetanColors.Cream,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        fontFamily = LocalTibetanFont.current,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Rounded-square icon chip used across rows and grids. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    background: Color = TibetanColors.Brown600,
    tint: Color = TibetanColors.Gold300,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(13.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** The gold gradient pill toggle from the design. Purely visual; caller owns state. */
@Composable
fun GoldToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val knobAlign by animateDpAsState(if (checked) 22.dp else 3.dp, label = "knob")
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 27.dp)
            .clip(TibetanTokens.Pill)
            .then(
                if (checked) Modifier.background(TibetanTokens.GoldVertical)
                else Modifier
                    .background(TibetanColors.Brown700)
                    .border(1.dp, TibetanColors.Line2, TibetanTokens.Pill)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobAlign)
                .size(21.dp)
                .clip(CircleShape)
                .background(if (checked) TibetanColors.Espresso else TibetanColors.CreamDim)
        )
    }
}

/** Uppercase section label (gold or cream-dim variants). */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TibetanColors.Gold300,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
    )
}

/**
 * Live `current / max` character counter for AI inputs (chat & translate).
 *
 * Always visible so the user sees the per-request cap
 * ([AiLimits.MAX_INPUT_CHARS]) and watches it update as they type. Turns
 * warning-red once over the limit so the over-cap state reads at a glance.
 */
@Composable
fun CharCounter(
    current: Int,
    modifier: Modifier = Modifier,
    max: Int = AiLimits.MAX_INPUT_CHARS,
) {
    val over = current > max
    Text(
        text = "$current / $max",
        modifier = modifier,
        color = if (over) TibetanColors.MaroonSoft else TibetanColors.CreamDim,
        fontSize = 11.5.sp,
        fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
    )
}

/** Small rounded badge, e.g. "PRO". */
@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Brush = TibetanTokens.GoldVertical,
    textColor: Color = TibetanColors.Espresso,
) {
    Box(
        modifier = modifier
            .clip(TibetanTokens.Pill)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp)
    }
}

/** Full-width gold CTA button. */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = TibetanTokens.GoldVerticalBright,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TibetanColors.Espresso, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
    }
}

/** Brush-tinted (gold gradient) text — the design's `.text-gold` helper. */
fun goldTextStyle(base: TextStyle = TextStyle.Default): TextStyle =
    base.copy(brush = TibetanTokens.GoldText)

@Suppress("unused")
val solidGoldBrush: Brush = SolidColor(TibetanColors.Gold300)

/** Header row with back arrow + title used by sub-screens. */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = TibetanColors.Cream,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            AppIcons.Back,
            contentDescription = "Back",
            tint = titleColor,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(4.dp)
                .size(23.dp),
        )
        Text(title, color = titleColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

/** Fills the screen with the warm background colour. */
@Composable
fun ScreenBackground(
    color: Color = TibetanColors.Bg900,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(color)) { content() }
}
