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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldButton
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors

/** Lambdas for [WebCheckoutScreen]. */
class WebCheckoutActions(
    val onBack: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onCheckAgain: () -> Unit = {},
)

/**
 * PRO for users Google Play can't sell to (Bhutan): the same benefits, paid by Visa/Mastercard on
 * RevenueCat's secure checkout, where they also pick monthly, yearly or lifetime. Signed-out users
 * are asked to sign in first so PRO stays on their account across devices and reinstalls.
 */
@Composable
fun WebCheckoutScreen(
    state: PremiumUiState,
    actions: WebCheckoutActions,
) {
    ScreenScaffold(bottomPadding = 16.dp) {
        BackHeader(stringResource(R.string.premium_title), onBack = actions.onBack)

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
            // The plan picker is on the hosted checkout page; say so, or it reads as one price.
            Text(
                stringResource(R.string.web_checkout_plans),
                color = TibetanColors.Gold300, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.web_checkout_cards),
                color = TibetanColors.CreamDim, fontSize = 12.5.sp, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            GoldButton(
                text = stringResource(
                    if (state.isSignedIn) R.string.web_checkout_continue else R.string.web_checkout_sign_in
                ),
                onClick = actions.onContinue,
                enabled = !state.checkingWebPurchase,
            )
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
                            .clickable(onClick = actions.onCheckAgain)
                            .padding(vertical = 10.dp),
                    )
                }
            }
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
