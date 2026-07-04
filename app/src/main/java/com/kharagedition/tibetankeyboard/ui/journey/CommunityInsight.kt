package com.kharagedition.tibetankeyboard.ui.journey

/** Aspirational framing for the opt-in community comparison — same tone as [StreakLogic]'s milestones. */
enum class CommunityTier {
    ELITE, TOP, ABOVE_AVERAGE, BUILDING;
}

object CommunityInsight {

    /** Percentile-to-tier bucketing. Kept coarse so small denominators don't jitter the label. */
    fun tierFor(percentile: Int): CommunityTier = when {
        percentile >= 95 -> CommunityTier.ELITE
        percentile >= 80 -> CommunityTier.TOP
        percentile >= 50 -> CommunityTier.ABOVE_AVERAGE
        else -> CommunityTier.BUILDING
    }
}
