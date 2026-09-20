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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager.PremiumPlan
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
    state: PremiumUiState,
    actions: PremiumActions,
) {
    // Ordered by real 28-day usage — keyboard 3,326 users, emoji 1,649, AI toolbar 1 — so ads,
    // themes and the journey lead and AI is the bonus rather than the headline.
    val features = listOf(
        Feature(
            AppIcons.NoAds,
            stringResource(R.string.premium_feature_no_ads),
            stringResource(R.string.premium_feature_no_ads_desc),
        ),
        Feature(
            AppIcons.Palette,
            stringResource(R.string.premium_feature_themes),
            stringResource(R.string.premium_feature_themes_desc),
        ),
        Feature(
            AppIcons.Sparkle,
            stringResource(R.string.premium_feature_suggestions),
            stringResource(R.string.premium_feature_suggestions_desc),
        ),
        Feature(
            AppIcons.Crown,
            stringResource(R.string.premium_feature_journey),
            stringResource(R.string.premium_feature_journey_desc),
        ),
        Feature(
            AppIcons.Bot,
            stringResource(R.string.premium_feature_ai),
            stringResource(R.string.premium_feature_ai_desc),
        ),
    )

    ScreenScaffold(bottomPadding = 16.dp) {
        BackHeader(stringResource(R.string.premium_title), onBack = actions.onBack)

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
                    stringResource(R.string.premium_hero_subtitle),
                    color = TibetanColors.Cream2, fontSize = 13.5.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
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

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (state.plans.isEmpty()) {
                Text(
                    stringResource(R.string.premium_loading_plans),
                    color = TibetanColors.CreamDim, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.plans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            selected = plan.id == state.selectedPlanId,
                            onClick = { actions.onSelectPlan(plan) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GoldButton(
                if (state.plans.isEmpty()) stringResource(R.string.premium_continue)
                else stringResource(R.string.start_subscription),
                onClick = actions.onPurchase,
            )
            Spacer(Modifier.height(11.dp))
            Text(
                stringResource(R.string.restore_purchase),
                color = TibetanColors.Gold300, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions.onRestore)
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.terms_privacy),
                color = TibetanColors.CreamFaint, fontSize = 11.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One selectable plan; selection reads from the gold border and fill, not colour alone. */
@Composable
private fun PlanCard(
    plan: PremiumPlan,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Keyed on the package type, not on savingPercent — an offering with annual but no monthly
    // has nothing to compute a saving from and would otherwise label itself "Monthly".
    val title = when (plan.analyticsPlan) {
        AppAnalytics.Plan.LIFETIME -> stringResource(R.string.premium_lifetime)
        AppAnalytics.Plan.ANNUAL -> stringResource(R.string.premium_annual)
        AppAnalytics.Plan.MONTHLY -> stringResource(R.string.monthly)
        else -> plan.title
    }
    val subtitle = when {
        plan.analyticsPlan == AppAnalytics.Plan.LIFETIME -> stringResource(R.string.premium_pay_once)
        plan.analyticsPlan != AppAnalytics.Plan.ANNUAL -> stringResource(R.string.cancel_anytime)
        plan.pricePerMonth != null -> stringResource(R.string.premium_per_month, plan.pricePerMonth)
        else -> stringResource(R.string.premium_billed_yearly)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TibetanColors.Brown600 else TibetanColors.Brown700)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) TibetanColors.Gold400 else TibetanColors.Gold200.copy(alpha = 0.22f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = TibetanColors.Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                plan.savingPercent?.let { pct ->
                    Text(
                        stringResource(R.string.premium_save_badge, pct),
                        color = TibetanColors.Espresso,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TibetanTokens.GoldVerticalBright)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(subtitle, color = TibetanColors.CreamDim, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                plan.price,
                color = if (selected) TibetanColors.Gold300 else TibetanColors.Cream2,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (plan.isRecommended) {
                Text(
                    stringResource(R.string.premium_best_value),
                    color = TibetanColors.Gold300, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
