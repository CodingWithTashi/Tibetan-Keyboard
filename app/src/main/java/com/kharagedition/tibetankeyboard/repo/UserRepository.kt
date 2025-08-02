package com.kharagedition.tibetankeyboard.repo

// UserRepository.kt - Repository for user data operations
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import com.kharagedition.tibetankeyboard.util.DeviceInfo
import com.kharagedition.tibetankeyboard.util.SubscriptionType
import com.kharagedition.tibetankeyboard.util.User

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val analyticsCollection = db.collection("user_analytics")

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val ANALYTICS_COLLECTION = "user_analytics"
    }

    /**
     * Create or update user profile in Firestore
     */
    suspend fun createOrUpdateUser(
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String,
        context: Context,
        isNewUser: Boolean = true
    ): Result<User> {
        return try {
            val deviceInfo = getDeviceInfo(context)
            val existingUser = if (!isNewUser) getUserById(uid) else null

            val user = if (existingUser != null) {
                // Update existing user
                existingUser.copy(
                    displayName = displayName,
                    email = email,
                    photoUrl = photoUrl,
                    lastLoginAt = System.currentTimeMillis(),
                    deviceInfo = deviceInfo,
                    analytics = existingUser.analytics.copy(
                        totalLogins = existingUser.analytics.totalLogins + 1,
                        lastActiveDate = System.currentTimeMillis()
                    )
                )
            } else {
                // Create new user
                User(
                    uid = uid,
                    displayName = displayName,
                    email = email,
                    photoUrl = photoUrl,
                    deviceInfo = deviceInfo
                )
            }

            // Save to Firestore
            usersCollection.document(uid)
                .set(user, SetOptions.merge())
                .await()

            // Track analytics event
            trackUserEvent(uid, if (isNewUser) "user_registered" else "user_login")

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user by ID
     */
    suspend fun getUserById(uid: String): User? {
        return try {
            val document = usersCollection.document(uid).get().await()
            document.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update user subscription
     */
    suspend fun updateSubscription(
        uid: String,
        subscriptionType: SubscriptionType,
        startDate: Long = System.currentTimeMillis(),
        endDate: Long? = null
    ): Result<Boolean> {
        return try {
            val updates = mapOf(
                "isSubscribed" to (subscriptionType != SubscriptionType.FREE),
                "subscriptionType" to subscriptionType,
                "subscriptionStartDate" to startDate,
                "subscriptionEndDate" to endDate
            )

            usersCollection.document(uid)
                .update(updates)
                .await()

            // Track subscription event
            trackUserEvent(uid, "subscription_updated", mapOf(
                "subscription_type" to subscriptionType.name,
                "is_subscribed" to (subscriptionType != SubscriptionType.FREE)
            ))

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if user has premium access
     */
    suspend fun hasePremiumAccess(uid: String): Boolean {
        return try {
            val user = getUserById(uid)
            when {
                user == null -> false
                user.subscriptionType == SubscriptionType.LIFETIME -> true
                user.subscriptionType == SubscriptionType.FREE -> false
                user.subscriptionEndDate == null -> false
                else -> System.currentTimeMillis() < user.subscriptionEndDate!!
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Track user events for analytics
     */
    suspend fun trackUserEvent(
        uid: String,
        eventName: String,
        parameters: Map<String, Any> = emptyMap()
    ) {
        try {
            val eventData = mapOf(
                "userId" to uid,
                "eventName" to eventName,
                "timestamp" to System.currentTimeMillis(),
                "parameters" to parameters
            )

            analyticsCollection.add(eventData).await()
        } catch (e: Exception) {
            // Log error but don't throw to avoid breaking user flow
        }
    }

    /**
     * Update user analytics data
     */
    suspend fun updateUserAnalytics(
        uid: String,
        sessionDuration: Long = 0,
        featuresUsed: List<String> = emptyList()
    ) {
        try {
            val user = getUserById(uid) ?: return
            val analytics = user.analytics

            val updatedAnalytics = analytics.copy(
                sessionCount = analytics.sessionCount + 1,
                averageSessionDuration = if (sessionDuration > 0) {
                    (analytics.averageSessionDuration + sessionDuration) / 2
                } else analytics.averageSessionDuration,
                featuresUsed = (analytics.featuresUsed + featuresUsed).distinct().toMutableList(),
                lastActiveDate = System.currentTimeMillis()
            )

            usersCollection.document(uid)
                .update("analytics", updatedAnalytics)
                .await()
        } catch (e: Exception) {
            // Log error but don't throw
        }
    }

    /**
     * Get device information for analytics
     */
    private suspend fun getDeviceInfo(context: Context): DeviceInfo {
        return try {
            val fcmToken = FirebaseMessaging.getInstance().token.await()

            DeviceInfo(
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = Build.VERSION.RELEASE,
                appVersion = getAppVersion(context),
                deviceId = getDeviceId(context),
                fcmToken = fcmToken
            )
        } catch (e: Exception) {
            DeviceInfo()
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            "Unknown"
        }
    }
}