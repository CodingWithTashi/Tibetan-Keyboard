package com.kharagedition.tibetankeyboard.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors

/** Rounded card that groups [SettingsRow]s, as on the Settings screen. */
@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 2.dp),
    ) { content() }
}

/** One row inside a [SettingsGroup]: icon tile, title, optional subtitle, trailing slot. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = TibetanColors.Cream,
    iconTint: Color = TibetanColors.Gold300,
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
        IconTile(icon, size = 40.dp, iconSize = 20.dp, background = TibetanColors.Brown700, tint = iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, color = TibetanColors.CreamDim, fontSize = 12.5.sp)
            }
        }
        trailing()
    }
}

/** Hairline between [SettingsRow]s. */
@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TibetanColors.Line))
}

/** Trailing chevron for rows that navigate. */
@Composable
fun ChevronIcon() {
    Icon(AppIcons.Chevron, null, tint = TibetanColors.CreamDim, modifier = Modifier.size(20.dp))
}
