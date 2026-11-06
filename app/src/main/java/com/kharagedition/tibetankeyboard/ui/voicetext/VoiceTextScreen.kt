package com.kharagedition.tibetankeyboard.ui.voicetext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.theme.LocalTibetanFont
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.keyboard.KeyboardLayoutHint

@Composable
fun VoiceTextScreen(onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ScreenScaffold(horizontalPadding = 18.dp) {
        BackHeader(stringResource(R.string.voice_text_title), onBack = onBack)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.voice_text_hint),
            color = TibetanColors.CreamDim,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TibetanColors.Brown700)
                .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .heightIn(min = 160.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = TibetanColors.Cream, fontSize = 18.sp, fontFamily = LocalTibetanFont.current),
                cursorBrush = SolidColor(TibetanColors.Gold300),
                keyboardOptions = remember {
                    KeyboardOptions(
                        platformImeOptions = PlatformImeOptions(
                            privateImeOptions = KeyboardLayoutHint.privateImeOption("bo"),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}
