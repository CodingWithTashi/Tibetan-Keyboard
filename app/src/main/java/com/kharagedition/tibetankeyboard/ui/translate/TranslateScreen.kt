package com.kharagedition.tibetankeyboard.ui.translate

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.CharCounter
import com.kharagedition.tibetankeyboard.ui.compose.components.ModelSelector
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.components.SectionLabel
import com.kharagedition.tibetankeyboard.ui.keyboard.KeyboardLayoutHint
import com.kharagedition.tibetankeyboard.ui.compose.theme.LocalTibetanFont
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens
import com.kharagedition.tibetankeyboard.ui.settings.SettingsPrefs
import com.kharagedition.tibetankeyboard.util.AiLimits

class TranslateActions(
    val onBack: () -> Unit,
    val onInputChange: (String) -> Unit,
    val onSourceLang: (String) -> Unit,
    val onTargetLang: (String) -> Unit,
    val onSwap: () -> Unit,
    val onTranslate: () -> Unit,
    val onModelChange: (String) -> Unit,
    val onCopy: (String) -> Unit,
    val onUpgrade: () -> Unit,
)

private val LANGUAGES = listOf("bo", "en", "zh-CN")

@Composable
private fun langLabel(code: String): String = when (code) {
    "bo" -> stringResource(R.string.lang_tibetan)
    "en" -> stringResource(R.string.lang_english)
    "zh-CN" -> stringResource(R.string.lang_chinese)
    else -> code
}

@Composable
fun TranslateScreen(state: TranslateUiState, actions: TranslateActions) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    ScreenScaffold(horizontalPadding = 18.dp) {
        BackHeader(stringResource(R.string.ai_translate), onBack = actions.onBack)

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelSelector(
                model = state.model,
                onModelChange = actions.onModelChange,
                options = SettingsPrefs.translateEngineOptions,
                labelOf = SettingsPrefs::engineLabel,
            )
        }

        Spacer(Modifier.height(14.dp))
        LanguageBar(state, actions)

        Spacer(Modifier.height(14.dp))
        InputCard(state, actions)

        Spacer(Modifier.height(14.dp))
        TranslateButton(
            isLoading = state.isLoading,
            enabled = state.input.length <= AiLimits.MAX_INPUT_CHARS,
            onClick = {
                // Dismiss the soft keyboard so the result is fully visible.
                // hide() is what actually collapses the IME; clearFocus() alone
                // does not on most devices.
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                if (!state.isPremium) actions.onUpgrade() else actions.onTranslate()
            },
        )

        Spacer(Modifier.height(18.dp))
        OutputCard(state, actions)
    }
}

@Composable
private fun LanguageBar(state: TranslateUiState, actions: TranslateActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LanguageChip(state.sourceLang, Modifier.weight(1f), onPick = actions.onSourceLang)
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(TibetanColors.Brown600)
                .clickable(onClick = actions.onSwap),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.SwapHoriz,
                contentDescription = stringResource(R.string.cd_swap_languages),
                tint = TibetanColors.Gold300,
                modifier = Modifier.size(18.dp),
            )
        }
        LanguageChip(state.targetLang, Modifier.weight(1f), onPick = actions.onTargetLang)
    }
}

@Composable
private fun LanguageChip(code: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(langLabel(code), color = TibetanColors.Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Icon(AppIcons.ExpandMore, null, tint = TibetanColors.CreamDim, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LANGUAGES.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(langLabel(lang)) },
                    onClick = { open = false; onPick(lang) },
                )
            }
        }
    }
}

@Composable
private fun InputCard(state: TranslateUiState, actions: TranslateActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TibetanTokens.RadiusCard)
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, TibetanTokens.RadiusCard)
            .padding(16.dp),
    ) {
        SectionLabel(langLabel(state.sourceLang), color = TibetanColors.CreamDim)
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
            if (state.input.isEmpty()) {
                Text(
                    stringResource(R.string.translate_input_hint),
                    color = TibetanColors.CreamDim,
                    fontSize = 16.sp,
                    fontFamily = LocalTibetanFont.current,
                )
            }
            BasicTextField(
                value = state.input,
                onValueChange = actions.onInputChange,
                textStyle = TextStyle(color = TibetanColors.Cream, fontSize = 16.sp, fontFamily = LocalTibetanFont.current),
                cursorBrush = SolidColor(TibetanColors.Gold300),
                // Tell our IME which script the source language expects so it opens
                // on the right layout (QWERTY for an English source, Uchen for
                // Tibetan) instead of leaving the user on the last-used language.
                keyboardOptions = remember(state.sourceLang) {
                    KeyboardOptions(
                        platformImeOptions = PlatformImeOptions(
                            privateImeOptions = KeyboardLayoutHint.privateImeOption(state.sourceLang),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            CharCounter(current = state.input.length)
        }
    }
}

@Composable
private fun TranslateButton(isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val active = enabled && !isLoading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) TibetanTokens.GoldVertical else SolidColor(TibetanColors.Brown600))
            .clickable(enabled = active, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val contentColor = if (enabled) TibetanColors.Espresso else TibetanColors.CreamDim
        if (isLoading) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(AppIcons.Translate, null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.translate), color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OutputCard(state: TranslateUiState, actions: TranslateActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TibetanTokens.RadiusCard)
            .background(TibetanColors.Brown600)
            .border(1.dp, TibetanColors.Line, TibetanTokens.RadiusCard)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(langLabel(state.targetLang), color = TibetanColors.Gold300)
            if (state.output.isNotEmpty()) {
                Row(
                    modifier = Modifier.clickable { actions.onCopy(state.output) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(AppIcons.Copy, null, tint = TibetanColors.CreamDim, modifier = Modifier.size(15.dp))
                    Text(stringResource(R.string.copy), color = TibetanColors.CreamDim, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val body = when {
            state.error != null -> state.error
            state.output.isNotEmpty() -> state.output
            else -> stringResource(R.string.translate_empty_hint)
        }
        Text(
            body,
            color = when {
                state.error != null -> TibetanColors.Cream2
                state.output.isNotEmpty() -> TibetanColors.Cream
                else -> TibetanColors.CreamDim
            },
            fontSize = 16.sp,
            lineHeight = 26.sp,
            fontFamily = LocalTibetanFont.current,
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        )
    }
}
