package com.kharagedition.tibetankeyboard.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BoText
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens

@Composable
fun LoginScreen(
    isLoading: Boolean,
    onGoogleSignIn: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(TibetanColors.Bg900).systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // brand
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(TibetanTokens.GoldVertical),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Lotus, null, tint = TibetanColors.Espresso, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), color = TibetanColors.Cream, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            BoText(stringResource(R.string.bo_app_name), color = TibetanColors.Gold300, fontSize = 16.sp)

            Spacer(Modifier.height(40.dp))
            Text(stringResource(R.string.welcome), color = TibetanColors.Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.login_subtitle),
                color = TibetanColors.CreamDim, fontSize = 14.sp, lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            GoogleSignInButton(isLoading = isLoading, onClick = onGoogleSignIn)
        }

        // privacy footer
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.by_signing_in), color = TibetanColors.CreamFaint, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.Center) {
                    Text(
                        stringResource(R.string.terms_of_service),
                        color = TibetanColors.Gold300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onTerms),
                    )
                    Text(stringResource(R.string.and_word), color = TibetanColors.CreamFaint, fontSize = 12.sp)
                    Text(
                        stringResource(R.string.privacy_policy),
                        color = TibetanColors.Gold300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onPrivacy),
                    )
                }
            }
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
            .clickable(enabled = !isLoading, onClick = onClick),
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
