package com.kharagedition.tibetankeyboard.ui.subscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.kharagedition.tibetankeyboard.subscription.WebPlan
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldButton
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors

/** Lambdas for [WebCheckoutScreen]. */
class WebCheckoutActions(
    val onBack: () -> Unit = {},
    val onSelectPlan: (WebPlan) -> Unit = {},
    val onContinue: () -> Unit = {},
    val onCheckAgain: () -> Unit = {},
)

/**
 * PRO for users Google Play can't sell to (Bhutan): [PremiumScreen]'s pitch and plan picker, paid
 * by Visa/Mastercard on RevenueCat's secure checkout, which opens on the picked plan. Signed-out
 * users are asked to sign in first so PRO stays on their account across devices and reinstalls.
 */
@Composable
fun WebCheckoutScreen(
    state: PremiumUiState,
    actions: WebCheckoutActions,
) {
    val selected = state.selectedWebPlan
    // The checkout gives the trial only to customers who never bought anything, so it is promised
    // only to them.
    val trialDays = selected?.trialDays?.takeIf { state.webTrialEligible }

    PaywallScaffold(
        onBack = actions.onBack,
        footer = if (!state.webCheckoutAvailable) null else {
            {
                if (selected != null && trialDays != null) {
                    val trialLine = when (selected.plan) {
                        AppAnalytics.Plan.ANNUAL -> R.plurals.premium_trial_then_year
                        AppAnalytics.Plan.MONTHLY -> R.plurals.premium_trial_then_month
                        else -> R.plurals.premium_trial_then
                    }
                    PaywallPriceLine(pluralStringResource(trialLine, trialDays, trialDays, selected.price))
                }
                GoldButton(
                    text = when {
                        !state.isSignedIn -> stringResource(R.string.web_checkout_sign_in)
                        trialDays != null -> pluralStringResource(R.plurals.premium_start_trial, trialDays, trialDays)
                        else -> stringResource(R.string.web_checkout_continue)
                    },
                    onClick = actions.onContinue,
                    enabled = !state.checkingWebPurchase && selected != null,
                )
                CheckoutStatus(state, actions.onCheckAgain)
            }
        },
    ) {
        PaywallHero(
            title = stringResource(R.string.unlock_everything),
            subtitle = stringResource(R.string.web_checkout_subtitle),
            modifier = Modifier.padding(horizontal = ProScreenPadding),
        )

        ProBenefitList(Modifier.padding(horizontal = ProScreenPadding, vertical = 20.dp))

        Column(modifier = Modifier.padding(horizontal = ProScreenPadding)) {
            if (!state.webCheckoutAvailable) {
                Text(
                    stringResource(R.string.web_checkout_unavailable),
                    color = TibetanColors.Cream2, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
                return@Column
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.webPlans.forEach { plan ->
                    val annual = plan.plan == AppAnalytics.Plan.ANNUAL
                    PlanCard(
                        plan = plan.plan,
                        price = plan.price,
                        selected = plan.packageId == selected?.packageId,
                        onClick = { actions.onSelectPlan(plan) },
                        pricePerMonth = plan.pricePerMonth,
                        savingPercent = state.webAnnualSaving.takeIf { annual },
                        // Whichever plan is pre-selected, a year is the one that costs least.
                        recommended = annual && state.webAnnualSaving != null,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.web_checkout_cards),
                color = TibetanColors.CreamDim, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Under the button: why to sign in, or where the payment stands after the checkout tab. */
@Composable
private fun CheckoutStatus(state: PremiumUiState, onCheckAgain: () -> Unit) {
    when {
        !state.isSignedIn -> Footnote(stringResource(R.string.web_checkout_sign_in_why))
        state.checkingWebPurchase -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = TibetanColors.Gold300, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.web_checkout_checking), color = TibetanColors.CreamDim, fontSize = 12.5.sp)
        }
        state.webPurchasePending -> {
            Footnote(stringResource(R.string.web_checkout_not_found))
            Text(
                stringResource(R.string.web_checkout_check_again),
                color = TibetanColors.Gold300, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onCheckAgain)
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        color = TibetanColors.CreamFaint, fontSize = 11.5.sp, lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        textAlign = TextAlign.Center,
    )
}
