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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.billing.BillingRoute
import com.kharagedition.tibetankeyboard.data.repository.RevenueCatManager.PremiumPlan
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldButton
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens

@Composable
fun PremiumScreen(
    state: PremiumUiState,
    actions: PremiumActions,
) {
    ScreenScaffold(bottomPadding = 16.dp) {
        BackHeader(stringResource(R.string.premium_title), onBack = actions.onBack)

        PaywallHero(
            title = stringResource(R.string.unlock_everything),
            subtitle = stringResource(R.string.premium_hero_subtitle),
            modifier = Modifier.padding(horizontal = ProScreenPadding),
        )

        ProBenefitList(Modifier.padding(horizontal = ProScreenPadding, vertical = 20.dp))

        Column(modifier = Modifier.padding(horizontal = ProScreenPadding)) {
            if (state.content == PaywallContent.Error) {
                Text(
                    stringResource(R.string.premium_plans_unavailable),
                    color = TibetanColors.CreamDim, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                GoldButton(stringResource(R.string.premium_retry), onClick = actions.onRetry)
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
                // What the Play sheet will say, before the user gets there: people start trials,
                // they rarely pay up front.
                val selected = state.selectedPlan
                val trialDays = selected?.freeTrialDays
                if (selected != null && trialDays != null) {
                    Spacer(Modifier.height(12.dp))
                    val trialLine = when (selected.analyticsPlan) {
                        AppAnalytics.Plan.ANNUAL -> R.plurals.premium_trial_then_year
                        AppAnalytics.Plan.MONTHLY -> R.plurals.premium_trial_then_month
                        else -> R.plurals.premium_trial_then
                    }
                    Text(
                        pluralStringResource(trialLine, trialDays, trialDays, selected.price),
                        color = TibetanColors.Gold300, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(14.dp))
                GoldButton(
                    text = when {
                        trialDays != null -> pluralStringResource(R.plurals.premium_start_trial, trialDays, trialDays)
                        selected?.analyticsPlan == AppAnalytics.Plan.LIFETIME -> stringResource(R.string.premium_buy_once)
                        else -> stringResource(R.string.start_subscription)
                    },
                    onClick = actions.onPurchase,
                    enabled = !state.isPurchasing && selected != null,
                )
            }
            if (state.billingRoute == BillingRoute.PLAY_DEGRADED) {
                // Nepal: Play charges USD and local wallets/domestic cards can't pay it.
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.premium_card_help),
                    color = TibetanColors.CreamDim, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
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

/** While RevenueCat decides which paywall this user gets: same header, no premature content. */
@Composable
fun PaywallLoadingScreen(onBack: () -> Unit) {
    ScreenScaffold(scrollable = false, bottomPadding = 0.dp) {
        BackHeader(stringResource(R.string.premium_title), onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TibetanColors.Gold300)
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
