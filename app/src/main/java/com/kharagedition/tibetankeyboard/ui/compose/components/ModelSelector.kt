package com.kharagedition.tibetankeyboard.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.settings.SettingsPrefs

/**
 * Compact pill that lets the user switch the Claude model (Haiku 4.5 / Sonnet 4.6).
 * Shared by the Chat and Translate screens.
 */
@Composable
fun ModelSelector(
    model: String,
    onModelChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TibetanColors.Brown600)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(999.dp))
            .clickable { open = true }
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(AppIcons.Sparkle, null, tint = TibetanColors.Gold300, modifier = Modifier.size(15.dp))
        Text(
            SettingsPrefs.modelLabel(model),
            color = TibetanColors.Cream,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(AppIcons.ExpandMore, null, tint = TibetanColors.CreamDim, modifier = Modifier.size(16.dp))

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SettingsPrefs.modelOptions.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(opt.label, fontWeight = FontWeight.SemiBold)
                            if (opt.value == model) {
                                Icon(AppIcons.Check, null, tint = TibetanColors.Gold300, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = { open = false; onModelChange(opt.value) },
                )
            }
        }
    }
}
