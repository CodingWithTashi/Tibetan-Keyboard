package com.kharagedition.tibetankeyboard.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.MobileAds
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.service.MyFirebaseMessagingService
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons

import com.kharagedition.tibetankeyboard.ui.compose.components.BoText
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens
import com.kharagedition.tibetankeyboard.ui.home.HomeActivity

class SplashScreenActivity : AppCompatActivity() {

    private val splashDelayMs = 1100L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}

        if (intent?.getBooleanExtra(MyFirebaseMessagingService.EXTRA_FROM_NOTIFICATION, false) == true) {
            AppAnalytics.logNotificationOpened()
        }

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }

        setContent {
            TibetanKeyboardTheme {
                SplashContent(versionName)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val forceUpdate = intent?.getBooleanExtra(MyFirebaseMessagingService.EXTRA_FORCE_UPDATE, false) == true
            startActivity(Intent(this, HomeActivity::class.java).apply {
                if (forceUpdate) putExtra(MyFirebaseMessagingService.EXTRA_FORCE_UPDATE, true)
            })
            finish()
        }, splashDelayMs)
    }
}

@Composable
private fun SplashContent(versionName: String?) {
    val transition = rememberInfiniteTransition(label = "splash")
    val glow by transition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )

    Box(modifier = Modifier.fillMaxSize().background(TibetanColors.Bg900), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .alpha(glow)
                    .clip(RoundedCornerShape(24.dp))
                    .background(TibetanTokens.GoldVertical),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp,).clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.app_name), color = TibetanColors.Cream, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            BoText(stringResource(R.string.bo_app_name), color = TibetanColors.Gold300, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.author_name), color = TibetanColors.CreamDim, fontSize = 13.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = TibetanColors.Gold400,
                trackColor = TibetanColors.Brown700,
                strokeWidth = 3.dp,
                modifier = Modifier.size(30.dp),
            )
            if (versionName != null) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.version_label, versionName), color = TibetanColors.CreamFaint, fontSize = 12.sp)
            }
        }
    }
}
