package com.kharagedition.tibetankeyboard.util

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val isSubscribed: Boolean = false,
    val subscriptionType: SubscriptionType = SubscriptionType.FREE,
    val subscriptionStartDate: Long? = null,
    val subscriptionEndDate: Long? = null,
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val analytics: UserAnalytics = UserAnalytics()
) {
    // Empty constructor for Firebase
    constructor() : this("")
}

// SubscriptionType.kt - Enum for subscription types
enum class SubscriptionType(val displayName: String, val price: Double) {
    FREE("Free", 0.0),
    MONTHLY("Monthly Premium", 9.99),
    YEARLY("Yearly Premium", 99.99),
    LIFETIME("Lifetime Premium", 299.99)
}

// DeviceInfo.kt - Device information for analytics
data class DeviceInfo(
    val deviceModel: String = "",
    val androidVersion: String = "",
    val appVersion: String = "",
    val deviceId: String = "",
    val fcmToken: String = ""
) {
    constructor() : this("")
}

// UserAnalytics.kt - Analytics data
data class UserAnalytics(
    val totalLogins: Long = 1,
    val lastActiveDate: Long = System.currentTimeMillis(),
    val featuresUsed: MutableList<String> = mutableListOf(),
    val sessionCount: Long = 0,
    val averageSessionDuration: Long = 0,
    val referralSource: String = "direct"
) {
    constructor() : this(1)
}