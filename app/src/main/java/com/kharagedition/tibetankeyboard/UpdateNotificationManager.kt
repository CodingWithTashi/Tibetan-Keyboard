package com.kharagedition.tibetankeyboard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class UpdateNotificationManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "app_update_prefs"
        private const val LAST_UPDATE_CHECK = "last_update_check"
        private const val CURRENT_VERSION = "current_version"
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun checkForUpdates() {
        val currentVersionCode = getCurrentVersionCode()
        val lastKnownVersion = sharedPrefs.getInt(CURRENT_VERSION, 0)
        val lastCheckTime = sharedPrefs.getLong(LAST_UPDATE_CHECK, 0)
        val currentTime = System.currentTimeMillis()

        // Check once per day
        if (currentTime - lastCheckTime > 24 * 60 * 60 * 1000) {
            // Here you could make an API call to check for updates
            // For demo purposes, we'll simulate an update available
            simulateUpdateCheck(currentVersionCode, lastKnownVersion)

            sharedPrefs.edit()
                .putLong(LAST_UPDATE_CHECK, currentTime)
                .putInt(CURRENT_VERSION, currentVersionCode)
                .apply()
        }
    }

    private fun simulateUpdateCheck(currentVersion: Int, lastKnownVersion: Int) {
        // Simulate server response indicating update available
        // In real implementation, you'd call your server API here

        // Example: Show notification if we detect user might have an older version
        if (shouldShowUpdateNotification()) {
            showLocalUpdateNotification()
        }
    }

    private fun shouldShowUpdateNotification(): Boolean {
        // Add your logic here to determine when to show update notification
        // This could be based on server response, version comparison, etc.
        return true // For demo purposes
    }

    private fun showLocalUpdateNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "local_update_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Local Update Check",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=${context.packageName}")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            playStoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle("Update Available")
            .setContentText("Tap to update your keyboard app")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1002, notification)
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }
}