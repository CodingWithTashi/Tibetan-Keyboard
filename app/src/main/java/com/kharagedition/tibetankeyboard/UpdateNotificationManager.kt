package com.kharagedition.tibetankeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Wraps the Google Play In-App Updates API.
 *
 * Strategy:
 * - Flexible update (background download + user-visible install banner) for routine releases.
 * - Immediate update (full-screen, blocking) when the release is ≥30 days stale or when
 *   a push notification forces the issue.
 *
 * Debug builds: skips the Play API entirely and simulates a downloaded flexible update
 * after 3 s so the install banner UI can be verified without a Play-signed APK.
 */
class InAppUpdateManager(context: Context) {

    private val appUpdateManager = AppUpdateManagerFactory.create(context)
    private var installStateListener: InstallStateUpdatedListener? = null

    /**
     * Queries the Play Store for an available update and starts the appropriate flow.
     * Safe to call from [HomeActivity.onCreate] and whenever [forceImmediate] is true
     * (e.g. from a "force_update" FCM payload).
     *
     * In DEBUG builds the Play API is bypassed and [onFlexibleDownloadComplete] is invoked
     * after 3 s so the install banner can be tested locally.
     */
    fun checkForUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        forceImmediate: Boolean = false,
        onFlexibleDownloadComplete: () -> Unit = {},
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "checkForUpdate: DEBUG build — Play API skipped (debug.keystore ≠ release key). " +
                    "Simulating flexible-update downloaded in ${DEBUG_DELAY_MS} ms.")
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "checkForUpdate: DEBUG simulate → invoking onFlexibleDownloadComplete")
                onFlexibleDownloadComplete()
            }, DEBUG_DELAY_MS)
            return
        }

        Log.d(TAG, "checkForUpdate: querying Play API (forceImmediate=$forceImmediate)")
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                val availability = info.updateAvailability()
                val installStatus = info.installStatus()
                val stalenessDays = info.clientVersionStalenessDays()
                val canFlexible = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                val canImmediate = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                Log.d(TAG, "checkForUpdate: updateAvailability=$availability " +
                        "installStatus=$installStatus stalenessDays=$stalenessDays " +
                        "canFlexible=$canFlexible canImmediate=$canImmediate")

                when (availability) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        Log.d(TAG, "checkForUpdate: UPDATE_AVAILABLE — starting update flow")
                        startUpdate(info, forceImmediate, launcher, onFlexibleDownloadComplete)
                    }
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        Log.d(TAG, "checkForUpdate: update already in progress — resuming immediate flow")
                        if (canImmediate) startImmediateUpdate(info, launcher)
                    }
                    else -> {
                        Log.d(TAG, "checkForUpdate: no update available (availability=$availability)")
                    }
                }

                // A flexible update finished downloading while the app was not in the foreground.
                if (installStatus == InstallStatus.DOWNLOADED) {
                    Log.d(TAG, "checkForUpdate: flexible update already DOWNLOADED — notifying")
                    onFlexibleDownloadComplete()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "checkForUpdate: Play API call failed — $e")
            }
    }

    /**
     * Lightweight resume check for [HomeActivity.onResume]: only asks whether a previously
     * started flexible download has completed, without re-launching the consent dialog.
     */
    fun checkIfUpdateDownloaded(onDownloaded: () -> Unit) {
        if (BuildConfig.DEBUG) return  // debug simulation already fires from checkForUpdate

        Log.d(TAG, "checkIfUpdateDownloaded: querying Play API")
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                val status = info.installStatus()
                Log.d(TAG, "checkIfUpdateDownloaded: installStatus=$status")
                if (status == InstallStatus.DOWNLOADED) {
                    Log.d(TAG, "checkIfUpdateDownloaded: DOWNLOADED — notifying")
                    onDownloaded()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "checkIfUpdateDownloaded: Play API call failed — $e")
            }
    }

    /** Installs a downloaded flexible update (triggers app restart). */
    fun completeFlexibleUpdate() {
        Log.d(TAG, "completeFlexibleUpdate: calling appUpdateManager.completeUpdate()")
        appUpdateManager.completeUpdate()
    }

    /** Must be called from [HomeActivity.onDestroy] to prevent listener leaks. */
    fun unregisterListener() {
        if (installStateListener != null) {
            Log.d(TAG, "unregisterListener: removing InstallStateUpdatedListener")
            appUpdateManager.unregisterListener(installStateListener!!)
            installStateListener = null
        }
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun startUpdate(
        info: AppUpdateInfo,
        forceImmediate: Boolean,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onFlexibleDownloadComplete: () -> Unit,
    ) {
        val stalenessDays = info.clientVersionStalenessDays() ?: 0
        val canImmediate = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        val canFlexible = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

        val chosenType = when {
            (forceImmediate || stalenessDays >= 30) && canImmediate -> AppUpdateType.IMMEDIATE
            canFlexible -> AppUpdateType.FLEXIBLE
            canImmediate -> AppUpdateType.IMMEDIATE
            else -> {
                Log.w(TAG, "startUpdate: neither FLEXIBLE nor IMMEDIATE allowed — skipping")
                return
            }
        }

        Log.d(TAG, "startUpdate: launching ${if (chosenType == AppUpdateType.IMMEDIATE) "IMMEDIATE" else "FLEXIBLE"} " +
                "update flow (forceImmediate=$forceImmediate stalenessDays=$stalenessDays)")

        if (chosenType == AppUpdateType.IMMEDIATE) {
            startImmediateUpdate(info, launcher)
        } else {
            startFlexibleUpdate(info, launcher, onFlexibleDownloadComplete)
        }
    }

    private fun startImmediateUpdate(
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        Log.d(TAG, "startImmediateUpdate: starting full-screen update UI")
        appUpdateManager.startUpdateFlowForResult(
            info, launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
        )
    }

    private fun startFlexibleUpdate(
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onDownloadComplete: () -> Unit,
    ) {
        Log.d(TAG, "startFlexibleUpdate: registering InstallStateUpdatedListener + showing consent")
        installStateListener = InstallStateUpdatedListener { state ->
            Log.d(TAG, "InstallStateUpdatedListener: installStatus=${state.installStatus()}")
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                Log.d(TAG, "startFlexibleUpdate: download COMPLETE — notifying caller")
                onDownloadComplete()
            }
        }.also { appUpdateManager.registerListener(it) }

        appUpdateManager.startUpdateFlowForResult(
            info, launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
        )
    }

    companion object {
        private const val TAG = "InAppUpdate"
        private const val DEBUG_DELAY_MS = 3_000L
    }
}
