package com.kharagedition.tibetankeyboard.ui.subscription

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.revenuecat.purchases.ui.revenuecatui.customercenter.CustomerCenter

/**
 * RevenueCat's Customer Center: cancellation, restore, the retention offer and the exit survey
 * (all configured in the dashboard). Refund Request and Change Plans are iOS-only and stay hidden.
 */
class ManageSubscriptionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppAnalytics.logManageSubscriptionOpened()
        setContent {
            // CustomerCenter reads MaterialTheme, not TibetanKeyboardTheme's tokens, so seed a
            // dark scheme with the app palette instead of Material's default purple.
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = TibetanColors.Gold300,
                    onPrimary = TibetanColors.Espresso,
                    background = TibetanColors.Espresso,
                    onBackground = TibetanColors.Cream,
                    surface = TibetanColors.Brown700,
                    onSurface = TibetanColors.Cream,
                )
            ) {
                CustomerCenter(modifier = Modifier.fillMaxSize()) { finish() }
            }
        }
    }

    companion object {
        fun open(context: Context) {
            val intent = Intent(context, ManageSubscriptionActivity::class.java)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
