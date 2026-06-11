package com.kharagedition.tibetankeyboard.ui.settings

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldToggle
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.components.IconTile
import com.kharagedition.tibetankeyboard.ui.compose.components.SectionLabel
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens

/** A selectable option backing a SharedPreferences value. */
data class PrefOption(val label: String, val sub: String?, val value: String)

data class SettingsState(
    val color: String = SettingsPrefs.COLOR_BROWN,
    val style: String = SettingsPrefs.STYLE_CLASSIC,
    val vibrate: Boolean = false,
    val sound: Boolean = true,
    val eventNotification: Boolean = true,
    val isPremium: Boolean = false,
    val isAuthenticated: Boolean = false,
)

class SettingsActions(
    val onBack: () -> Unit,
    val onColorChange: (String) -> Unit,
    val onStyleChange: (String) -> Unit,
    val onVibrate: (Boolean) -> Unit,
    val onSound: (Boolean) -> Unit,
    val onNotification: (Boolean) -> Unit,
    val onUpgrade: () -> Unit,
    val onLogout: () -> Unit,
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
    adSlot: (@Composable () -> Unit)? = null,
) {
    var dialog by remember { mutableStateOf<String?>(null) } // "color" | "style" | null

    ScreenScaffold {
        BackHeader(stringResource(R.string.title_activity_settings), onBack = actions.onBack)

        Column(Modifier.padding(horizontal = 18.dp)) {
                SectionLabel(stringResource(R.string.design_and_ui))
                Spacer(Modifier.height(9.dp))
                SettingsGroup {
                    SettingsRow(
                        icon = AppIcons.Image,
                        title = stringResource(R.string.keyboard_background_title),
                        subtitle = SettingsPrefs.colorLabel(state.color),
                        onClick = { dialog = "color" },
                        trailing = { Chevron() },
                    )
                    Divider()
                    SettingsRow(
                        icon = AppIcons.Palette,
                        title = stringResource(R.string.keyboard_layout_title),
                        subtitle = SettingsPrefs.styleLabel(state.style),
                        onClick = { dialog = "style" },
                        trailing = { Chevron() },
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel(stringResource(R.string.sound_and_control))
                Spacer(Modifier.height(9.dp))
                SettingsGroup {
                    SettingsRow(
                        icon = AppIcons.Vibrate,
                        title = stringResource(R.string.vibrate_on_tap),
                        trailing = { GoldToggle(state.vibrate, actions.onVibrate) },
                    )
                    Divider()
                    SettingsRow(
                        icon = AppIcons.Sound,
                        title = stringResource(R.string.sound_on_tap),
                        trailing = { GoldToggle(state.sound, actions.onSound) },
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel(stringResource(R.string.title_notification))
                Spacer(Modifier.height(9.dp))
                SettingsGroup {
                    SettingsRow(
                        icon = AppIcons.Bell,
                        title = stringResource(R.string.event_notifications),
                        trailing = { GoldToggle(state.eventNotification, actions.onNotification) },
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel(stringResource(R.string.title_account))
                Spacer(Modifier.height(9.dp))
                SettingsGroup {
                    SettingsRow(
                        icon = AppIcons.Crown,
                        title = stringResource(R.string.subscription),
                        subtitle = stringResource(if (state.isPremium) R.string.premium_plan else R.string.free_plan),
                        trailing = {
                            if (!state.isPremium) {
                                Box(
                                    modifier = Modifier
                                        .clip(TibetanTokens.Pill)
                                        .background(TibetanTokens.GoldVertical)
                                        .clickable(onClick = actions.onUpgrade)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(stringResource(R.string.upgrade), color = TibetanColors.Espresso, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                    )
                    if (state.isAuthenticated) {
                        Divider()
                        SettingsRow(
                            icon = AppIcons.Logout,
                            title = stringResource(R.string.logout),
                            onClick = actions.onLogout,
                            trailing = { Chevron() },
                        )
                    }
                }

                if (!state.isPremium && adSlot != null) {
                    Spacer(Modifier.height(20.dp))
                    adSlot()
                }
            }
        }

    when (dialog) {
        "color" -> OptionPickerDialog(
            title = stringResource(R.string.keyboard_background_title),
            options = SettingsPrefs.colorOptions,
            selected = state.color,
            onSelect = { actions.onColorChange(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "style" -> OptionPickerDialog(
            title = stringResource(R.string.keyboard_layout_title),
            options = SettingsPrefs.styleOptions,
            selected = state.style,
            onSelect = { actions.onStyleChange(it); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 2.dp),
    ) { content() }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, size = 40.dp, iconSize = 20.dp, background = TibetanColors.Brown700)
        Column(Modifier.weight(1f)) {
            Text(title, color = TibetanColors.Cream, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = TibetanColors.CreamDim, fontSize = 12.5.sp)
            }
        }
        trailing()
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TibetanColors.Line))
}

@Composable
private fun Chevron() {
    Icon(AppIcons.Chevron, null, tint = TibetanColors.CreamDim, modifier = Modifier.size(20.dp))
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<PrefOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TibetanColors.Brown700,
        title = { Text(title, color = TibetanColors.Cream, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = opt.value == selected, onClick = { onSelect(opt.value) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opt.value == selected,
                            onClick = { onSelect(opt.value) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = TibetanColors.Gold300,
                                unselectedColor = TibetanColors.CreamDim,
                            ),
                        )
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(opt.label, color = TibetanColors.Cream, fontSize = 15.sp)
                            if (opt.sub != null) Text(opt.sub, color = TibetanColors.CreamDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done), color = TibetanColors.Gold300) }
        },
    )
}
