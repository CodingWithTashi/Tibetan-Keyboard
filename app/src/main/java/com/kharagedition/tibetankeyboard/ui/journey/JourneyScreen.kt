package com.kharagedition.tibetankeyboard.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldButton
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldToggle
import com.kharagedition.tibetankeyboard.ui.compose.components.PillBadge
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.components.SectionLabel
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens

/** Callbacks for Journey actions, owned by the Activity. */
class JourneyActions(
    val onBack: () -> Unit,
    val onUpgrade: () -> Unit,
    val onShare: () -> Unit,
    val onToggleSync: (Boolean) -> Unit,
)

@Composable
fun JourneyScreen(state: JourneyUiState, actions: JourneyActions) {
    ScreenScaffold {
        BackHeader(stringResource(R.string.journey_title), onBack = actions.onBack)

        Column(Modifier.padding(horizontal = 18.dp)) {
            StreakHero(state)

            Spacer(Modifier.height(14.dp))
            StatsGrid(state, onUpgrade = actions.onUpgrade)

            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.journey_insights_label), color = TibetanColors.CreamDim)
            Spacer(Modifier.height(10.dp))
            if (state.isPremium) {
                WeekChart(state.weekWords)
                Spacer(Modifier.height(12.dp))
                CommunityCard(state, actions.onToggleSync)
            } else {
                LockedInsightsCard(onUpgrade = actions.onUpgrade)
            }

            Spacer(Modifier.height(12.dp))
            PrivacyCard()

            if (state.streakDays > 0) {
                Spacer(Modifier.height(18.dp))
                GoldButton(stringResource(R.string.journey_share), onClick = actions.onShare)
            }
        }
    }
}

/** The flame: current streak, best streak and progress toward the next milestone. */
@Composable
private fun StreakHero(state: JourneyUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TibetanTokens.RadiusCard)
            .background(TibetanColors.Brown600)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔥", fontSize = 44.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.streakDays > 0) stringResource(R.string.journey_streak_days, state.streakDays)
            else stringResource(R.string.journey_streak_none_title),
            color = TibetanColors.Cream,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                state.streakDays == 0 -> stringResource(R.string.journey_streak_none_subtitle)
                state.typedToday -> stringResource(R.string.journey_streak_done_today)
                else -> stringResource(R.string.journey_streak_at_risk)
            },
            color = TibetanColors.CreamDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )

        val next = state.nextMilestone
        if (next != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(TibetanColors.Bg800)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((state.streakDays / next.toFloat()).coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(TibetanTokens.GoldVertical)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.journey_next_milestone, next),
                    color = TibetanColors.Gold300, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
                if (state.bestStreak > 0) {
                    Text(
                        stringResource(R.string.journey_best_streak, state.bestStreak),
                        color = TibetanColors.CreamDim, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(state: JourneyUiState, onUpgrade: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            StatTile(
                label = stringResource(R.string.journey_words_today),
                value = state.wordsToday.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.journey_words_week),
                value = state.wordsThisWeek.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            StatTile(
                label = stringResource(R.string.journey_words_total),
                value = state.totalWords.toString(),
                modifier = Modifier.weight(1f),
            )
            // Vocabulary size comes from Botok-tokenized unique words — a PRO insight.
            if (state.isPremium) {
                StatTile(
                    label = stringResource(R.string.journey_vocabulary),
                    value = state.vocabularySize.toString(),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LockedStatTile(
                    label = stringResource(R.string.journey_vocabulary),
                    onClick = onUpgrade,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(value, color = TibetanColors.Cream, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = TibetanColors.CreamDim, fontSize = 12.sp)
    }
}

@Composable
private fun LockedStatTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Column {
            Icon(AppIcons.Lock, null, tint = TibetanColors.Gold300, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, color = TibetanColors.CreamDim, fontSize = 12.sp)
        }
        PillBadge("PRO", modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** Last-7-days bar chart. Pure Compose boxes — no chart library needed for 7 bars. */
@Composable
private fun WeekChart(weekWords: List<Int>) {
    val max = (weekWords.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            stringResource(R.string.journey_week_chart_title),
            color = TibetanColors.Cream, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            weekWords.forEachIndexed { index, words ->
                val isToday = index == weekWords.lastIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((4 + 60 * (words / max.toFloat())).dp)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .then(
                            if (isToday) Modifier.background(TibetanTokens.GoldVertical)
                            else Modifier.background(TibetanColors.Bg800)
                        ),
                )
            }
        }
    }
}

/** Opt-in, numbers-only community comparison (PRO). */
@Composable
private fun CommunityCard(state: JourneyUiState, onToggleSync: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.journey_community_title),
                    color = TibetanColors.Cream, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.journey_community_desc),
                    color = TibetanColors.CreamDim, fontSize = 12.sp,
                )
            }
            Spacer(Modifier.size(10.dp))
            GoldToggle(state.syncEnabled, onToggleSync)
        }
        if (state.syncEnabled) {
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    state.comparing -> stringResource(R.string.journey_community_comparing)
                    state.percentile != null ->
                        stringResource(R.string.journey_community_result, state.percentile)
                    else -> stringResource(R.string.journey_community_signin)
                },
                color = TibetanColors.Gold300, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** What free users see instead of the chart + community comparison. */
@Composable
private fun LockedInsightsCard(onUpgrade: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Lock, null, tint = TibetanColors.Gold300, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.journey_insights_locked_title),
            color = TibetanColors.Cream, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.journey_insights_locked_desc),
            color = TibetanColors.CreamDim, fontSize = 12.5.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        GoldButton(stringResource(R.string.journey_unlock_pro), onClick = onUpgrade, fontSize = 14.sp)
    }
}

/** The trust statement — always visible, never behind PRO. */
@Composable
private fun PrivacyCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Bg800)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.Lock, null, tint = TibetanColors.Jade, modifier = Modifier.size(18.dp))
        Column {
            Text(
                stringResource(R.string.journey_privacy_title),
                color = TibetanColors.Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.journey_privacy_desc),
                color = TibetanColors.CreamDim, fontSize = 12.sp, lineHeight = 17.sp,
            )
        }
    }
}
