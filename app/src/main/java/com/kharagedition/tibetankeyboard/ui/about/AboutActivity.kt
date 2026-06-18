package com.kharagedition.tibetankeyboard.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme
import com.kharagedition.tibetankeyboard.util.CommonUtils

/** Compose About screen — replaces the old BottomSheetDialog. Same links/intents. */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TibetanKeyboardTheme {
                AboutScreen(
                    AboutActions(
                        onBack = { finish() },
                        onShare = { shareApp() },
                        onRate = { openUrl(CommonUtils.PLAY_STORE_URL) },
                        onDictionary = { openUrl(CommonUtils.PLAY_STORE_DICTIONARY_URL) },
                        onCalendar = { openUrl(CommonUtils.PLAY_STORE_CALENDAR_URL) },
                        onGithub = { openUrl(CommonUtils.GITHUB_URL) },
                        onGmail = { sendEmail() },
                    )
                )
            }
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                putExtra(
                    Intent.EXTRA_TEXT,
                    "\nCheck out this Keyboard Application.\n\n${CommonUtils.PLAY_STORE_URL}"
                )
            }
            startActivity(Intent.createChooser(shareIntent, "choose one"))
        } catch (_: Exception) {
        }
    }

    private fun sendEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:developer.kharag@gmail.com")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
