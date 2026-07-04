package com.kharagedition.tibetankeyboard.data.model

import kotlinx.serialization.Serializable

/**
 * Community-comparison payload for the Journey feature. PRIVACY: these two aggregate
 * numbers are the ONLY thing the Journey feature ever sends off-device, and only after
 * the user opts in on the Journey screen. No typed content, ever.
 */
@Serializable
data class JourneyWeeklyRequest(
    val wordsThisWeek: Int,
    val streakDays: Int,
)

@Serializable
data class JourneyWeeklyData(
    /** Share of other participants this user out-typed this week (0–100). */
    val percentile: Int = 0,
    val totalUsers: Int = 0,
)

@Serializable
data class JourneyWeeklyResponse(
    val success: Boolean = false,
    val data: JourneyWeeklyData? = null,
    val message: String? = null,
)
