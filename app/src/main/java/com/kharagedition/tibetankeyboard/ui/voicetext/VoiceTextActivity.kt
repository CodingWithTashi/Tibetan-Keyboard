package com.kharagedition.tibetankeyboard.ui.voicetext

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanKeyboardTheme

/** Voice Text: a notepad that opens our keyboard on the Tibetan layout, so the mic is one tap away. */
class VoiceTextActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TibetanKeyboardTheme {
                VoiceTextScreen(onBack = { finish() })
            }
        }
    }
}
