package com.kharagedition.tibetankeyboard.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.data.model.ChatMessage
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.CharCounter
import com.kharagedition.tibetankeyboard.ui.compose.components.ModelSelector
import com.kharagedition.tibetankeyboard.util.AiLimits
import com.kharagedition.tibetankeyboard.ui.compose.theme.LocalTibetanFont
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActions(
    val onBack: () -> Unit,
    val onSend: (String) -> Unit,
    val onCopy: (String) -> Unit,
    val onClear: () -> Unit,
    val onLogout: () -> Unit,
    val onUpgrade: () -> Unit,
    val onModelChange: (String) -> Unit,
)

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    isPremium: Boolean,
    model: String,
    actions: ChatActions,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TibetanColors.Bg900)
            .statusBarsPadding()
            .imePadding()
    ) {
        ChatHeader(model, actions)

        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, isLoading) {
            val target = messages.size - 1 + if (isLoading) 1 else 0
            if (target >= 0) listState.animateScrollToItem(target.coerceAtLeast(0))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.isFromUser) UserBubble(msg) else BotBubble(msg, actions.onCopy)
            }
            if (isLoading) item { TypingIndicator() }
        }

        ChatInput(
            enabled = !isLoading,
            onSubmit = { text ->
                if (!isPremium) actions.onUpgrade() else actions.onSend(text)
            },
        )
    }
}

@Composable
private fun ChatHeader(model: String, actions: ChatActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            AppIcons.Back, stringResource(R.string.cd_back), tint = TibetanColors.Cream,
            modifier = Modifier.clip(CircleShape).clickable(onClick = actions.onBack).padding(4.dp).size(22.dp),
        )
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(TibetanTokens.GoldVertical),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bot, null, tint = TibetanColors.Espresso, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ai_assistant), color = TibetanColors.Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.online_tibetan), color = TibetanColors.Jade, fontSize = 12.sp)
        }
        ModelSelector(model = model, onModelChange = actions.onModelChange)
        Box {
            Icon(
                AppIcons.More, stringResource(R.string.cd_menu), tint = TibetanColors.Cream,
                modifier = Modifier.clip(CircleShape).clickable { menuOpen = true }.padding(6.dp).size(22.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.clear_chat_action)) }, onClick = { menuOpen = false; actions.onClear() })
                DropdownMenuItem(text = { Text(stringResource(R.string.sign_out)) }, onClick = { menuOpen = false; actions.onLogout() })
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(TibetanColors.Line))
}

@Composable
private fun UserBubble(msg: ChatMessage) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp))
                .background(TibetanTokens.GoldVertical)
                .padding(horizontal = 15.dp, vertical = 11.dp),
        ) {
            Text(
                msg.message, color = TibetanColors.Espresso, fontSize = 16.sp,
                fontWeight = FontWeight.Medium, lineHeight = 24.sp, fontFamily = LocalTibetanFont.current,
            )
        }
        Text(timeOf(msg.timestamp), color = TibetanColors.CreamFaint, fontSize = 10.5.sp, modifier = Modifier.padding(top = 4.dp, end = 2.dp))
    }
}

@Composable
private fun BotBubble(msg: ChatMessage, onCopy: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth(0.88f)) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(TibetanColors.Brown600),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bot, null, tint = TibetanColors.Gold300, modifier = Modifier.size(18.dp))
        }
        Column {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 5.dp, bottomEnd = 18.dp))
                    .background(TibetanColors.Brown600)
                    .padding(horizontal = 15.dp, vertical = 12.dp),
            ) {
                Text(
                    msg.message, color = TibetanColors.Cream, fontSize = 16.sp,
                    lineHeight = 27.sp, fontFamily = LocalTibetanFont.current,
                )
            }
            Row(
                modifier = Modifier.padding(top = 6.dp, start = 4.dp).clickable { onCopy(msg.message) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(AppIcons.Copy, null, tint = TibetanColors.CreamDim, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.copy), color = TibetanColors.CreamDim, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(TibetanColors.Brown600),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bot, null, tint = TibetanColors.Gold300, modifier = Modifier.size(18.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 5.dp, bottomEnd = 18.dp))
                .background(TibetanColors.Brown600)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text("···", color = TibetanColors.CreamDim, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ChatInput(enabled: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val overLimit = text.length > AiLimits.MAX_INPUT_CHARS
    val canSend = enabled && text.isNotBlank() && !overLimit
    val submit = {
        val t = text.trim()
        if (t.isNotEmpty() && t.length <= AiLimits.MAX_INPUT_CHARS) { onSubmit(t); text = "" }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(TibetanColors.Brown700)
                    .border(1.dp, if (overLimit) TibetanColors.MaroonSoft else TibetanColors.Line2, RoundedCornerShape(23.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text("འདིར་འབྲི་རོགས་…", color = TibetanColors.CreamDim, fontSize = 16.sp, fontFamily = LocalTibetanFont.current)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(color = TibetanColors.Cream, fontSize = 16.sp, fontFamily = LocalTibetanFont.current),
                    cursorBrush = SolidColor(TibetanColors.Gold300),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canSend) TibetanTokens.GoldVertical else SolidColor(TibetanColors.Brown600))
                    .clickable(enabled = canSend) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Send,
                    stringResource(R.string.cd_send),
                    tint = if (canSend) TibetanColors.Espresso else TibetanColors.CreamDim,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, end = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            CharCounter(current = text.length)
        }
    }
}

private fun timeOf(date: Date): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
