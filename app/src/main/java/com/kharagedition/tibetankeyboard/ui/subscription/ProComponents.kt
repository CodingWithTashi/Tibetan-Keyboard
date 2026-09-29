package com.kharagedition.tibetankeyboard.ui.subscription

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.IconTile
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens

/** Side margin of every PRO screen (paywalls, card checkout, manage subscription). */
val ProScreenPadding = 18.dp

/**
 * The frame of our paywalls: the pitch scrolls, the [footer] (price line and button) stays on
 * screen. In the scrolling column the button sat below three plan cards, out of sight until the
 * user scrolled to it.
 */
@Composable
fun PaywallScaffold(
    onBack: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenScaffold(scrollable = false, bottomPadding = 0.dp) {
        BackHeader(stringResource(R.string.premium_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            content = content,
        )
        if (footer != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(TibetanColors.Gold200.copy(alpha = 0.14f)))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ProScreenPadding, vertical = 12.dp),
                content = footer,
            )
        }
    }
}

/** What the button will cost, right above it: "Free for 7 days, then $9.99 a year". */
@Composable
fun PaywallPriceLine(text: String) {
    Text(
        text,
        color = TibetanColors.Gold300, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        textAlign = TextAlign.Center,
    )
}

/** One thing PRO unlocks. */
data class ProBenefit(val icon: ImageVector, @StringRes val title: Int, @StringRes val description: Int)

/**
 * PRO's benefits, the same list and order everywhere. Ordered by real 28-day usage — keyboard
 * 3,326 users, emoji 1,649, AI toolbar 1 — so ads, themes and the journey lead and AI is the bonus.
 */
val ProBenefits = listOf(
    ProBenefit(AppIcons.NoAds, R.string.premium_feature_no_ads, R.string.premium_feature_no_ads_desc),
    ProBenefit(AppIcons.Palette, R.string.premium_feature_themes, R.string.premium_feature_themes_desc),
    ProBenefit(AppIcons.Sparkle, R.string.premium_feature_suggestions, R.string.premium_feature_suggestions_desc),
    ProBenefit(AppIcons.Crown, R.string.premium_feature_journey, R.string.premium_feature_journey_desc),
    ProBenefit(AppIcons.Bot, R.string.premium_feature_ai, R.string.premium_feature_ai_desc),
)

/**
 * The benefits list. [muted] is the "what you'd lose" look of the cancel flow: dimmed icons and no
 * checkmarks.
 */
@Composable
fun ProBenefitList(modifier: Modifier = Modifier, muted: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ProBenefits.forEach { benefit ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(
                    benefit.icon,
                    size = 40.dp,
                    iconSize = 20.dp,
                    background = if (muted) TibetanColors.Brown700 else TibetanColors.Brown600,
                    tint = if (muted) TibetanColors.CreamDim else TibetanColors.Gold300,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(benefit.title),
                        color = if (muted) TibetanColors.Cream2 else TibetanColors.Cream,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(benefit.description), color = TibetanColors.CreamDim, fontSize = 12.5.sp)
                }
                if (!muted) Icon(AppIcons.Check, null, tint = TibetanColors.Gold300, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * The maroon card at the top of every PRO pitch: [top] (the crown by default), a title, a
 * subtitle, then any [extra] content (the retention offer's prices).
 */
@Composable
fun PaywallHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit = { CrownTile() },
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TibetanTokens.GradMaroon)
            .border(1.dp, TibetanColors.Gold200.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        top()
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = TibetanColors.Cream, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = TibetanColors.Cream2, fontSize = 13.5.sp, lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        extra()
    }
}

@Composable
private fun CrownTile() {
    Box(
        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(TibetanTokens.GoldVerticalBright),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Crown, null, tint = TibetanColors.Espresso, modifier = Modifier.size(32.dp))
    }
}

/**
 * One selectable plan, the same card on every paywall; selection reads from the gold border and
 * fill, not colour alone. [plan] is one of [AppAnalytics.Plan]; [fallbackTitle] names any other.
 */
@Composable
fun PlanCard(
    plan: String,
    price: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pricePerMonth: String? = null,
    savingPercent: Int? = null,
    recommended: Boolean = false,
    fallbackTitle: String = "",
) {
    // Keyed on the plan, not on savingPercent — an offering with annual but no monthly has
    // nothing to compute a saving from and would otherwise label itself "Monthly".
    val title = when (plan) {
        AppAnalytics.Plan.LIFETIME -> stringResource(R.string.premium_lifetime)
        AppAnalytics.Plan.ANNUAL -> stringResource(R.string.premium_annual)
        AppAnalytics.Plan.MONTHLY -> stringResource(R.string.monthly)
        else -> fallbackTitle
    }
    val subtitle = when {
        plan == AppAnalytics.Plan.LIFETIME -> stringResource(R.string.premium_pay_once)
        plan != AppAnalytics.Plan.ANNUAL -> stringResource(R.string.cancel_anytime)
        pricePerMonth != null -> stringResource(R.string.premium_per_month, pricePerMonth)
        else -> stringResource(R.string.premium_billed_yearly)
    }

    Row(
        modifier = modifier
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
                savingPercent?.let { pct ->
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
                price,
                color = if (selected) TibetanColors.Gold300 else TibetanColors.Cream2,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (recommended) {
                Text(
                    stringResource(R.string.premium_best_value),
                    color = TibetanColors.Gold300, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
