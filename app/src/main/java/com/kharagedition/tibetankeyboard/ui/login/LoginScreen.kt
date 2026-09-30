package com.kharagedition.tibetankeyboard.ui.login

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.IconTile
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens

/** One thing an account gives. */
private data class SignInBenefit(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val description: Int,
)

/** Only what the app really ties to the account; typing stats, for one, stay on the phone. */
private val SignInBenefits = listOf(
    SignInBenefit(AppIcons.Crown, R.string.sign_in_benefit_pro, R.string.sign_in_benefit_pro_desc),
    SignInBenefit(AppIcons.Bot, R.string.sign_in_benefit_ai, R.string.sign_in_benefit_ai_desc),
    SignInBenefit(AppIcons.Star, R.string.sign_in_benefit_community, R.string.sign_in_benefit_community_desc),
)

/**
 * Sign-in as a bottom sheet over the screen that asked for it: the user keeps their place, sees
 * what an account is for, and can swipe the sheet away. [LoginActivity]'s window is see-through
 * and shows nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    isLoading: Boolean,
    onGoogleSignIn: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = TibetanColors.Bg800,
        contentColor = TibetanColors.Cream,
        scrimColor = TibetanColors.Espresso.copy(alpha = 0.72f),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.sign_in_title, stringResource(R.string.app_name)),
                color = TibetanColors.Cream, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.sign_in_subtitle),
                color = TibetanColors.CreamDim, fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SignInBenefits.forEach { BenefitRow(it) }
            }

            Spacer(Modifier.height(24.dp))
            GoogleSignInButton(isLoading = isLoading, onClick = onGoogleSignIn)
            Spacer(Modifier.height(14.dp))
            LegalLine(onTerms = onTerms, onPrivacy = onPrivacy)
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 18.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(TibetanTokens.Pill)
            .background(TibetanColors.Line2),
    )
}

@Composable
private fun BenefitRow(benefit: SignInBenefit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(benefit.icon, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(benefit.title),
                color = TibetanColors.Cream, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
            )
            Text(stringResource(benefit.description), color = TibetanColors.CreamDim, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun LegalLine(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.by_signing_in), color = TibetanColors.CreamFaint, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.Center) {
            Text(
                stringResource(R.string.terms_of_service) + " ",
                color = TibetanColors.Gold300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(role = Role.Button, onClick = onTerms),
            )
            Text(stringResource(R.string.and_word) + " ", color = TibetanColors.CreamFaint, fontSize = 12.sp)
            Text(
                stringResource(R.string.privacy_policy),
                color = TibetanColors.Gold300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(role = Role.Button, onClick = onPrivacy),
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(TibetanColors.Cream)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = TibetanColors.Brown600, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_google),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.sign_in_with_google), color = TibetanColors.Espresso, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
