package com.kharagedition.tibetankeyboard.ui.journey

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityInsightTest {

    @Test
    fun `tier boundaries match the aspirational copy`() {
        assertEquals(CommunityTier.ELITE, CommunityInsight.tierFor(100))
        assertEquals(CommunityTier.ELITE, CommunityInsight.tierFor(95))
        assertEquals(CommunityTier.TOP, CommunityInsight.tierFor(94))
        assertEquals(CommunityTier.TOP, CommunityInsight.tierFor(80))
        assertEquals(CommunityTier.ABOVE_AVERAGE, CommunityInsight.tierFor(79))
        assertEquals(CommunityTier.ABOVE_AVERAGE, CommunityInsight.tierFor(50))
        assertEquals(CommunityTier.BUILDING, CommunityInsight.tierFor(49))
        assertEquals(CommunityTier.BUILDING, CommunityInsight.tierFor(0))
    }
}
