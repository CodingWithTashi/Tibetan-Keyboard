package com.kharagedition.tibetankeyboard.ui.journey

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.util.CommonUtils
import com.kharagedition.tibetankeyboard.util.openPremiumUpgrade

/**
 * "Your Tibetan Journey" — streak & typing insights. Framework glue only:
 * navigation, the share intent and the upgrade route. All state lives in [JourneyViewModel].
 */
class JourneyActivity : AppCompatActivity() {

    private val viewModel: JourneyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The keyboard/Home log their own open events; notifications carry their source here.
        intent.getStringExtra(EXTRA_SOURCE)?.let { AppAnalytics.logJourneyOpened(it) }

        setContent {
            TibetanKeyboardTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                JourneyScreen(
                    state = state,
                    actions = JourneyActions(
                        onBack = { finish() },
                        onUpgrade = { openPremiumUpgrade(AppAnalytics.UpgradeSource.JOURNEY) },
                        onShare = { shareStreak() },
                        onToggleSync = { viewModel.setSyncEnabled(it) },
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun shareStreak() {
        val days = viewModel.uiState.value.streakDays
        if (days <= 0) return
        AppAnalytics.logJourneyShared(days)
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            getString(
                                R.string.journey_share_text,
                                days,
                                getString(R.string.app_name),
                                CommonUtils.PLAY_STORE_URL,
                            ),
                        )
                    },
                    null,
                )
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Optional [AppAnalytics.JourneySource] value describing what opened this screen. */
        const val EXTRA_SOURCE = "journey_source"
    }
}
