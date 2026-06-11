package com.kharagedition.tibetankeyboard.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldButton
import com.kharagedition.tibetankeyboard.ui.compose.components.IconTile
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens

private data class Feature(val icon: ImageVector, val title: String, val desc: String)

@Composable
fun PremiumScreen(
    priceLabel: String,
    onBack: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    val features = listOf(
        Feature(AppIcons.NoAds, "Remove all ads", "A clean, distraction-free keyboard"),
        Feature(AppIcons.Bot, "AI Assistant", "Chat & compose in Tibetan with AI"),
        Feature(AppIcons.Sparkle, "Next-word suggestions", "Smart Tibetan word prediction"),
        Feature(AppIcons.Palette, "Premium themes", "Exclusive keyboard styles & colors"),
//        Feature(AppIcons.Translate, "Tibetan ⇄ English", "Inline translation as you type"),
        Feature(AppIcons.Spell, "Spell check", "Catch typos in Tibetan automatically"),
    )

    ScreenScaffold(bottomPadding = 16.dp) {
        BackHeader(stringResource(R.string.premium_title), onBack = onBack)

        // hero
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(TibetanColors.MaroonDeep, TibetanColors.Maroon, TibetanColors.MaroonDeep)
                    )
                )
                .border(1.dp, TibetanColors.Gold200.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(TibetanTokens.GoldVerticalBright),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Crown, null, tint = TibetanColors.Espresso, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.unlock_everything), color = TibetanColors.Cream, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Power up your Tibetan typing with AI,\nsmart suggestions and no ads.",
                    color = TibetanColors.Cream2, fontSize = 13.5.sp, lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        // features
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            features.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(f.icon, size = 42.dp, iconSize = 21.dp)
                    Column(Modifier.weight(1f)) {
                        Text(f.title, color = TibetanColors.Cream, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(f.desc, color = TibetanColors.CreamDim, fontSize = 12.5.sp)
                    }
                    Icon(AppIcons.Check, null, tint = TibetanColors.Gold300, modifier = Modifier.size(20.dp))
                }
            }
        }

        // price + cta
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TibetanColors.Brown600)
                    .border(1.5.dp, TibetanColors.Gold400, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.monthly), color = TibetanColors.Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.cancel_anytime), color = TibetanColors.CreamDim, fontSize = 12.sp)
                }
                Text(priceLabel, color = TibetanColors.Gold300, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            GoldButton(stringResource(R.string.start_subscription), onClick = onPurchase)
            Spacer(Modifier.height(11.dp))
            Text(
                stringResource(R.string.restore_purchase),
                color = TibetanColors.Gold300, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRestore)
                    .padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.terms_privacy),
                color = TibetanColors.CreamFaint, fontSize = 11.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
