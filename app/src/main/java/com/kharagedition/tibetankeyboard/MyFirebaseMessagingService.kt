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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check if message contains data payload for update notification
        remoteMessage.data.isNotEmpty().let {
            if (remoteMessage.data["type"] == "app_update") {
                handleUpdateNotification(remoteMessage.data)
            }
        }
    }

    private fun handleUpdateNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Update Available"
        val message = data["message"] ?: "A new version of the app is available"
        val version = data["version"] ?: ""

        showUpdateNotification(title, message, version)
    }

    private fun showUpdateNotification(title: String, message: String, version: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "app_update_channel"

        // Create notification channel (for Android O and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for app updates"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open Play Store
        val playStoreIntent = createPlayStoreIntent()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            playStoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_update) // Add your update icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_download,
                "Update Now",
                pendingIntent
            )
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun createPlayStoreIntent(): Intent {
        val packageName = packageName

        // Try to open Play Store app first
        return try {
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        } catch (e: ActivityNotFoundException) {
            // Fallback to Play Store website
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to your server
        sendTokenToServer(token)
    }

    private fun sendTokenToServer(token: String) {
        // Implement your server communication here
        // This is where you'd send the FCM token to your backend
        Log.d("FCM", "New token: $token")
    }
}