package com.kharagedition.tibetankeyboard.ui.subscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.billing.BillingRoute
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldButton
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors

@Composable
fun PremiumScreen(
    state: PremiumUiState,
    actions: PremiumActions,
) {
    val failed = state.content == PaywallContent.Error
    val selected = state.selectedPlan
    val trialDays = selected?.freeTrialDays

    PaywallScaffold(
        onBack = actions.onBack,
        footer = {
            if (failed) {
                GoldButton(stringResource(R.string.premium_retry), onClick = actions.onRetry)
                return@PaywallScaffold
            }
            // What the Play sheet will say, before the user gets there: people start trials,
            // they rarely pay up front.
            if (selected != null && trialDays != null) {
                val trialLine = when (selected.analyticsPlan) {
                    AppAnalytics.Plan.ANNUAL -> R.plurals.premium_trial_then_year
                    AppAnalytics.Plan.MONTHLY -> R.plurals.premium_trial_then_month
                    else -> R.plurals.premium_trial_then
                }
                PaywallPriceLine(pluralStringResource(trialLine, trialDays, trialDays, selected.price))
            }
            GoldButton(
                text = when {
                    trialDays != null -> pluralStringResource(R.plurals.premium_start_trial, trialDays, trialDays)
                    selected?.analyticsPlan == AppAnalytics.Plan.LIFETIME -> stringResource(R.string.premium_buy_once)
                    else -> stringResource(R.string.start_subscription)
                },
                onClick = actions.onPurchase,
                enabled = !state.isPurchasing && selected != null,
            )
        },
    ) {
        PaywallHero(
            title = stringResource(R.string.unlock_everything),
            subtitle = stringResource(R.string.premium_hero_subtitle),
            modifier = Modifier.padding(horizontal = ProScreenPadding),
        )

        ProBenefitList(Modifier.padding(horizontal = ProScreenPadding, vertical = 20.dp))

        Column(modifier = Modifier.padding(horizontal = ProScreenPadding)) {
            if (failed) {
                Text(
                    stringResource(R.string.premium_plans_unavailable),
                    color = TibetanColors.CreamDim, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.plans.forEach { plan ->
                        PlanCard(
                            plan = plan.analyticsPlan,
                            price = plan.price,
                            selected = plan.id == state.selectedPlanId,
                            onClick = { actions.onSelectPlan(plan) },
                            pricePerMonth = plan.pricePerMonth,
                            savingPercent = plan.savingPercent,
                            recommended = plan.isRecommended,
                            fallbackTitle = plan.title,
                        )
                    }
                }
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
