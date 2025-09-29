package com.kharagedition.tibetankeyboard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "app_update_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Handle both notification and data payloads
        // This ensures the notification works in all app states

        // Check if message contains a notification payload (background/terminated state)
        remoteMessage.notification?.let {
            Log.d(TAG, "Notification Title: ${it.title}")
            Log.d(TAG, "Notification Body: ${it.body}")

            // When app is in background/terminated, system handles notification
            // but we can still process data payload
            if (remoteMessage.data.isNotEmpty()) {
                handleDataPayload(remoteMessage.data)
            }
        }

        // Check if message contains a data payload (foreground state)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data Payload: ${remoteMessage.data}")

            // When app is in foreground, we must manually show notification
            if (isAppInForeground()) {
                handleDataPayload(remoteMessage.data)
            }
        }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        val type = data["type"]

        when (type) {
            "app_update" -> handleUpdateNotification(data)
            "general" -> handleGeneralNotification(data)
            else -> {
                // Handle unknown notification types
                Log.w(TAG, "Unknown notification type: $type")
                handleGeneralNotification(data)
            }
        }
    }

    private fun handleUpdateNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Update Available"
        val message = data["message"] ?: "A new version of the app is available"
        val version = data["version"] ?: ""
        val updateUrl = data["update_url"] // Optional custom URL

        showUpdateNotification(title, message, version, updateUrl)
    }

    private fun handleGeneralNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Notification"
        val message = data["message"] ?: ""

        showGeneralNotification(title, message)
    }

    private fun showUpdateNotification(
        title: String,
        message: String,
        version: String,
        customUrl: String? = null
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel(notificationManager)

        // Create intent to open Play Store or custom URL
        val intent = if (customUrl != null) {
            createCustomUrlIntent(customUrl)
        } else {
            createPlayStoreIntent()
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification with full details
        val fullMessage = if (version.isNotEmpty()) {
            "$message\nVersion: $version"
        } else {
            message
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullMessage))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_download,
                "Update Now",
                pendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showGeneralNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel(notificationManager)

        // Create intent to open the app
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates & Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for app updates and important messages"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createPlayStoreIntent(): Intent {
        val packageName = packageName

        return try {
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: ActivityNotFoundException) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun createCustomUrlIntent(url: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        return appProcesses.any {
            it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && it.processName == packageName
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Send token to your server
        sendTokenToServer(token)

        // Store token locally
        saveTokenLocally(token)
    }

    private fun sendTokenToServer(token: String) {
        // TODO: Implement your server communication here
        // Example: Use Retrofit or OkHttp to send token to your backend
        Log.d(TAG, "Sending token to server: $token")

        // Example implementation:
        // RetrofitClient.instance.updateFcmToken(token)
        //     .enqueue(object : Callback<Response> {
        //         override fun onResponse(call: Call<Response>, response: Response<Response>) {
        //             Log.d(TAG, "Token sent successfully")
        //         }
        //         override fun onFailure(call: Call<Response>, t: Throwable) {
        //             Log.e(TAG, "Failed to send token", t)
        //         }
        //     })
    }

    private fun saveTokenLocally(token: String) {
        val sharedPreferences = getSharedPreferences("FCM_PREFS", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("FCM_TOKEN", token).apply()
        Log.d(TAG, "Token saved locally")
    }
}