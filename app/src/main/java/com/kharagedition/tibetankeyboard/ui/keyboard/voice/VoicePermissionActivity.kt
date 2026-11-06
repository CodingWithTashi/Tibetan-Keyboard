package com.kharagedition.tibetankeyboard.ui.keyboard.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kharagedition.tibetankeyboard.analytics.AppAnalytics

/**
 * What the keyboard could not tell the user itself: an input method cannot show the system's
 * permission dialog. The voice panel's "Allow microphone" opens this transparent screen, which
 * asks and closes at once; the keyboard picks up from there (see `TibetanKeyboard`, which
 * resumes listening if the user is back on the same field).
 */
class VoicePermissionActivity : ComponentActivity() {

    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After a refusal Android still offers a rationale the first time; once it no longer does,
        // the user has said no for good and only the system settings can change it.
        val blocked = !granted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
        AppAnalytics.logVoicePermission(
            when {
                granted -> AppAnalytics.VoicePermission.GRANTED
                blocked -> AppAnalytics.VoicePermission.BLOCKED
                else -> AppAnalytics.VoicePermission.DENIED
            }
        )
        VoicePermissionResult.report(granted, blocked)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            // Allowed in the meantime (from the settings, say): the keyboard still has to hear it.
            VoicePermissionResult.report(granted = true, blocked = false)
            finish()
            return
        }
        if (savedInstanceState == null) request.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/**
 * The answer to the microphone request, passed from the permission screen to the keyboard (they
 * share a process, not a window).
 *
 * Needed because Android shows no dialog once the permission was refused for good: the keyboard
 * then never loses focus, never restarts, and so cannot notice the answer by itself.
 */
object VoicePermissionResult {
    /** The user refused with "don't ask again" (or the system did it for them). */
    @Volatile
    var blocked: Boolean = false
        private set

    /** Set by the keyboard service while it is alive; called on the main thread. */
    @Volatile
    var listener: ((granted: Boolean, blocked: Boolean) -> Unit)? = null

    fun report(granted: Boolean, blocked: Boolean) {
        this.blocked = blocked
        listener?.invoke(granted, blocked)
    }
}
