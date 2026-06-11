package com.kharagedition.tibetankeyboard.ui.home

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BoText
import com.kharagedition.tibetankeyboard.ui.compose.components.IconTile
import com.kharagedition.tibetankeyboard.ui.compose.components.PillBadge
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.components.SectionLabel
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens

/** Immutable UI state driving the Home screen. */
data class HomeUiState(
    val keyboardEnabled: Boolean = false,
    val inputMethodSelected: Boolean = false,
    val isPremium: Boolean = false,
)

/** Callbacks for Home actions, owned by the Activity. */
class HomeActions(
    val onEnableKeyboard: () -> Unit,
    val onPickInputMethod: () -> Unit,
    val onChat: () -> Unit,
    val onThemes: () -> Unit,
    val onSettings: () -> Unit,
    val onShare: () -> Unit,
    val onRate: () -> Unit,
    val onAbout: () -> Unit,
    val onUpgrade: () -> Unit,
)

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val tag: String? = null,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    adSlot: (@Composable () -> Unit)? = null,
) {
    ScreenScaffold(horizontalPadding = 18.dp) {
        Spacer(Modifier.height(8.dp))
        BrandHeader()
        Spacer(Modifier.height(18.dp))
        SetupCard(state, actions)

        Spacer(Modifier.height(14.dp))
        if (state.keyboardEnabled && state.inputMethodSelected) {
            TestKeyboardField()
        } else {
            SetupDemo(if (!state.keyboardEnabled) R.drawable.keyboard else R.drawable.input)
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.quick_actions), color = TibetanColors.CreamDim, modifier = Modifier.padding(start = 2.dp))
        Spacer(Modifier.height(12.dp))
        QuickActionsGrid(state, actions)

        if (!state.isPremium) {
            Spacer(Modifier.height(16.dp))
            GoProBanner(onUpgrade = actions.onUpgrade)
            if (adSlot != null) {
                Spacer(Modifier.height(16.dp))
                adSlot()
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TibetanTokens.GoldVertical),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Lotus, null, tint = TibetanColors.Espresso, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(stringResource(R.string.app_name), color = TibetanColors.Cream, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            BoText(stringResource(R.string.bo_app_name), color = TibetanColors.CreamDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SetupCard(state: HomeUiState, actions: HomeActions) {
    val doneCount = (if (state.keyboardEnabled) 1 else 0) + (if (state.inputMethodSelected) 1 else 0)
    val allDone = doneCount == 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TibetanTokens.RadiusCard)
            .background(TibetanColors.Brown600)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (allDone) "You're all set!" else "Almost there!",
                color = TibetanColors.Cream, fontSize = 15.5.sp, fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.setup_progress, doneCount), color = TibetanColors.Gold300, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        // progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(TibetanColors.Bg800)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(doneCount / 2f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(TibetanTokens.GoldVertical)
            )
        }
        Spacer(Modifier.height(18.dp))

        SetupStep(
            index = 1,
            label = stringResource(R.string.setup_enable_keyboard),
            done = state.keyboardEnabled,
            active = !state.keyboardEnabled,
            onClick = actions.onEnableKeyboard,
        )
        Spacer(Modifier.height(12.dp))
        SetupStep(
            index = 2,
            label = stringResource(R.string.setup_choose_input),
            done = state.inputMethodSelected,
            active = state.keyboardEnabled && !state.inputMethodSelected,
            onClick = actions.onPickInputMethod,
        )
    }
}

@Composable
private fun SetupStep(index: Int, label: String, done: Boolean, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TibetanTokens.GoldVertical)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(99.dp))
                    .background(TibetanColors.Espresso.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$index", color = TibetanColors.Espresso, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Text(label, modifier = Modifier.weight(1f), color = TibetanColors.Espresso, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Icon(AppIcons.Chevron, null, tint = TibetanColors.Espresso, modifier = Modifier.size(20.dp))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(99.dp))
                    .then(
                        if (done) Modifier.background(TibetanTokens.GoldVertical)
                        else Modifier.background(TibetanColors.Brown700)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(AppIcons.Check, null, tint = TibetanColors.Espresso, modifier = Modifier.size(17.dp))
                else Text("$index", color = TibetanColors.CreamDim, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = TibetanColors.CreamDim,
                fontSize = 14.5.sp,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            )
        }
    }
}

@Composable
private fun QuickActionsGrid(state: HomeUiState, actions: HomeActions) {
    val items = listOf(
        QuickAction("AI Chat", AppIcons.Bot, tag = if (!state.isPremium) "PRO" else null, onClick = actions.onChat),
        QuickAction("Themes", AppIcons.Palette, onClick = actions.onThemes),
        QuickAction("Settings", AppIcons.Settings, onClick = actions.onSettings),
        QuickAction("Share", AppIcons.Share, onClick = actions.onShare),
        QuickAction("Rate", AppIcons.Star, onClick = actions.onRate),
        QuickAction("About", AppIcons.More, onClick = actions.onAbout),
    )
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                rowItems.forEach { item ->
                    QuickActionCard(item, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(item: QuickAction, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = item.onClick)
            .padding(start = 8.dp, end = 8.dp, top = 15.dp, bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            IconTile(item.icon, size = 42.dp, iconSize = 21.dp)
            Text(item.label, color = TibetanColors.Cream2, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
        if (item.tag != null) {
            PillBadge(item.tag, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun GoProBanner(onUpgrade: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(TibetanColors.Maroon, TibetanColors.MaroonDeep)
                )
            )
            .border(1.dp, TibetanColors.Gold200.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .clickable(onClick = onUpgrade)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(TibetanTokens.GoldVertical),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Crown, null, tint = TibetanColors.Espresso, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.go_pro), color = TibetanColors.Cream, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.go_pro_subtitle), color = TibetanColors.Cream2, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(TibetanColors.Gold200)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(stringResource(R.string.upgrade), color = TibetanColors.Espresso, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Animated setup walkthrough GIF (how to enable / pick the keyboard). */
@Composable
private fun SetupDemo(gifResId: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Bell, null, tint = TibetanColors.Gold300, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.how_to_set_up), color = TibetanColors.Cream, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)),
            factory = { ctx ->
                android.widget.ImageView(ctx).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
            },
            update = { iv -> Glide.with(iv).asGif().load(gifResId).into(iv) },
        )
    }
}

/** Inline field so the user can test the keyboard without leaving Home. */
@Composable
private fun TestKeyboardField() {
    var text by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Check, null, tint = TibetanColors.Jade, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.try_keyboard_here), color = TibetanColors.Cream, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TibetanColors.Bg800)
                .border(1.dp, TibetanColors.Line2, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                BoText("འདིར་ཡིག་འབྲི་རོགས་…", color = TibetanColors.CreamDim, fontSize = 15.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = TibetanColors.Cream, fontSize = 16.sp),
                cursorBrush = SolidColor(TibetanColors.Gold300),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
