package com.kharagedition.tibetankeyboard.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens
import kotlin.math.roundToInt

/**
 * A Compose-native "Material tap target" coach-mark. Dims the screen, punches a rounded
 * spotlight around [targetBounds] (root coordinates, px), pulses a gold ripple to draw the
 * eye, and floats a description bubble next to it — styled with the warm brown/gold tokens.
 *
 * Tapping the spotlight runs [onTargetClick] (the real action); tapping the dimmed area or
 * "Got it" runs [onDismiss]. Render it as the last child of a full-screen [Box] so it overlays
 * everything and shares the same root coordinate space as the highlighted target.
 */
@Composable
fun TapTargetPrompt(
    targetBounds: Rect,
    title: String,
    description: String,
    hint: String,
    dismissLabel: String,
    onTargetClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val density = LocalDensity.current
    val pad = with(density) { 9.dp.toPx() }
    val cornerPx = with(density) { 18.dp.toPx() }

    // Rounded spotlight rect around the target (px, root space).
    val holeLeft = targetBounds.left - pad
    val holeTop = targetBounds.top - pad
    val holeW = targetBounds.width + pad * 2
    val holeH = targetBounds.height + pad * 2
    val holeBottom = holeTop + holeH

    // Entry fade for the scrim + bubble.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(240)) }

    // Pulsing ripple around the spotlight.
    val transition = rememberInfiniteTransition(label = "tapTarget")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val ringExpand = with(density) { (pulse * 13.dp.toPx()) }
    val ringAlpha = (1f - pulse) * 0.55f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val noRipple = remember { MutableInteractionSource() }

        // Outside the spotlight: tap to dismiss (transparent, catches every stray tap).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        )

        // Scrim with a punched-out rounded spotlight + pulsing gold ring.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(color = TibetanColors.Espresso.copy(alpha = 0.84f * appear.value))
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(holeLeft, holeTop),
                size = Size(holeW, holeH),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                blendMode = BlendMode.Clear,
            )
            // Crisp gold edge around the spotlight.
            drawRoundRect(
                color = TibetanColors.Gold300.copy(alpha = 0.9f * appear.value),
                topLeft = Offset(holeLeft, holeTop),
                size = Size(holeW, holeH),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = with(density) { 2.dp.toPx() }),
            )
            // Expanding ripple.
            drawRoundRect(
                color = TibetanColors.Gold300.copy(alpha = ringAlpha),
                topLeft = Offset(holeLeft - ringExpand, holeTop - ringExpand),
                size = Size(holeW + ringExpand * 2, holeH + ringExpand * 2),
                cornerRadius = CornerRadius(cornerPx + ringExpand, cornerPx + ringExpand),
                style = Stroke(width = with(density) { 2.dp.toPx() }),
            )
        }

        // Invisible click target sitting exactly over the spotlight -> runs the real action.
        Box(
            modifier = Modifier
                .offset { IntOffset(holeLeft.roundToInt(), holeTop.roundToInt()) }
                .size(with(density) { holeW.toDp() }, with(density) { holeH.toDp() })
                .clip(RoundedCornerShape(18.dp))
                .clickable(interactionSource = noRipple, indication = null, onClick = onTargetClick),
        )

        // Float the description bubble below the spotlight when it sits in the top half,
        // otherwise above it. Anchoring to the parent edge avoids measuring the bubble.
        val gap = with(density) { 14.dp.toPx() }
        val maxH = constraints.maxHeight
        val placeBelow = targetBounds.center.y < maxH / 2f
        val bubbleAlign = if (placeBelow) Alignment.TopStart else Alignment.BottomStart

        Column(
            modifier = Modifier
                .align(bubbleAlign)
                .offset {
                    if (placeBelow) IntOffset(0, (holeBottom + gap).roundToInt())
                    else IntOffset(0, -((maxH - holeTop) + gap).roundToInt())
                }
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .graphicsLayer { alpha = appear.value }
                .clip(TibetanTokens.RadiusCard)
                .background(TibetanColors.Brown600)
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                .padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                if (icon != null) {
                    IconTile(icon, size = 38.dp, iconSize = 20.dp)
                }
                Text(
                    title,
                    color = TibetanColors.Cream,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                description,
                color = TibetanColors.Cream2,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    hint,
                    color = TibetanColors.Gold300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(TibetanTokens.Pill)
                        .background(TibetanTokens.GoldVertical)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        dismissLabel,
                        color = TibetanColors.Espresso,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
