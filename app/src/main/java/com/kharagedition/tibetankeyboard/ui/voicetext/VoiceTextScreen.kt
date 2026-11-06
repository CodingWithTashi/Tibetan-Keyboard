package com.kharagedition.tibetankeyboard.ui.voicetext

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldButton
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.theme.LocalTibetanFont
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceContent
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceController
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceFormat
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoicePolicy.Press
import com.kharagedition.tibetankeyboard.ui.keyboard.voice.VoiceState

class VoiceTextActions(
    val onBack: () -> Unit,
    val onTextChange: (String) -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
    val onClear: () -> Unit,
    val onUndo: () -> Unit,
    val onAllowMic: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/** Voice Text: a note on top (copy, share, clear), the hold-to-talk dock underneath. */
@Composable
fun VoiceTextScreen(text: String, voice: VoiceController, actions: VoiceTextActions) {
    val state by voice.state.collectAsState()
    val meter by voice.meter.collectAsState()
    val content = VoiceContent.of(state, meter.elapsedMs)

    ScreenScaffold(scrollable = false, horizontalPadding = 18.dp, bottomPadding = 18.dp) {
        BackHeader(stringResource(R.string.voice_text_title), onBack = actions.onBack)

        NoteCard(
            text = text,
            busy = state == VoiceState.Transcribing,
            canUndo = content.canUndo,
            actions = actions,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Dock(state = state, content = content, level = meter.level, elapsedMs = meter.elapsedMs, voice = voice, actions = actions)
    }
}

// ---------------------------------------------------------------------------------------------
// The note
// ---------------------------------------------------------------------------------------------

@Composable
private fun NoteCard(text: String, busy: Boolean, canUndo: Boolean, actions: VoiceTextActions, modifier: Modifier) {
    val has = text.isNotBlank()
    val scroll = rememberScrollState()
    // New words slide into view instead of appearing out of sight below the fold.
    LaunchedEffect(text.length, busy) { scroll.animateScrollTo(scroll.maxValue) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(TibetanColors.Brown700)
            .border(1.dp, TibetanColors.Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.voice_language_pill),
                color = TibetanColors.Gold300, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = LocalTibetanFont.current,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(TibetanColors.Gold300.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.voice_text_chars, text.length),
                color = TibetanColors.CreamDim, fontSize = 11.5.sp,
            )
        }
        Spacer(Modifier.height(10.dp))

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            if (!has && !busy) {
                Text(
                    stringResource(R.string.voice_text_placeholder),
                    color = TibetanColors.CreamFaint, fontSize = 17.sp, lineHeight = 26.sp,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = actions.onTextChange,
                textStyle = TextStyle(
                    color = TibetanColors.Cream, fontSize = 19.sp, lineHeight = 30.sp,
                    fontFamily = LocalTibetanFont.current,
                ),
                cursorBrush = SolidColor(TibetanColors.Gold300),
                modifier = Modifier.fillMaxWidth(),
            )
            if (busy) {
                Spacer(Modifier.height(if (has) 14.dp else 0.dp))
                WritingSkeleton()
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (has) 1f else 0.45f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(TibetanTokens.GoldVerticalBright)
                    .clickable(enabled = has, role = Role.Button, onClick = actions.onCopy)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Copy, null, tint = TibetanColors.Espresso, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.voice_text_copy),
                    color = TibetanColors.Espresso, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
            SmallAction(AppIcons.Share, stringResource(R.string.voice_text_share), enabled = has, onClick = actions.onShare)
            if (canUndo) {
                SmallAction(AppIcons.Undo, stringResource(R.string.voice_tool_undo), enabled = true, onClick = actions.onUndo)
            } else {
                SmallAction(AppIcons.Clear, stringResource(R.string.voice_tool_clear), enabled = has, onClick = actions.onClear)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(13.dp))
            .background(TibetanColors.Brown600)
            .border(1.dp, TibetanColors.Line2, RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = TibetanColors.Cream, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// The dock: status, wave, orb
// ---------------------------------------------------------------------------------------------

@Composable
private fun Dock(
    state: VoiceState,
    content: VoiceContent,
    level: Float,
    elapsedMs: Long,
    voice: VoiceController,
    actions: VoiceTextActions,
) {
    val warn = content.titleTone == VoiceContent.Tone.WARN
    val busy = state == VoiceState.Transcribing
    // How long the answer has taken, so a slow network reads as "still working", never "frozen".
    var waited by remember { mutableStateOf(0) }
    LaunchedEffect(busy) {
        waited = 0
        while (busy) {
            kotlinx.coroutines.delay(1000)
            waited++
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (content.confirmed) {
                Icon(AppIcons.Check, null, tint = TibetanColors.Jade, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
            }
            Text(
                stringResource(content.title),
                color = if (warn) TibetanColors.Warning else TibetanColors.Cream,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            if (busy) {
                Spacer(Modifier.size(10.dp))
                Text(
                    VoiceFormat.timer(waited * 1000L),
                    color = TibetanColors.Gold300, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            if (content.timer != VoiceContent.TimerStyle.NONE) {
                Spacer(Modifier.size(10.dp))
                Text(
                    VoiceFormat.timer(elapsedMs),
                    color = if (content.timer == VoiceContent.TimerStyle.ENDING) TibetanColors.Warning else TibetanColors.Gold300,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        val slow = busy && waited >= 8
        val sub = if (busy) (if (slow) R.string.voice_text_slow else R.string.voice_text_busy_body) else (content.body ?: content.hint)
        if (sub != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(sub),
                color = TibetanColors.CreamDim, fontSize = 13.5.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(12.dp))
        LevelBars(level = level, live = state is VoiceState.Listening, busy = state == VoiceState.Transcribing)
        Spacer(Modifier.height(14.dp))

        when {
            content.cta != null -> {
                GoldButton(
                    stringResource(content.cta.label),
                    onClick = if (content.cta == VoiceContent.Cta.ALLOW_MIC) actions.onAllowMic else actions.onOpenSettings,
                )
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Keeps the orb centred whether or not a side button shows.
                    Box(Modifier.size(52.dp)) {
                        if (content.retry) {
                            RoundButton(AppIcons.Refresh, stringResource(R.string.voice_text_retry)) { voice.retry() }
                        }
                    }
                    Orb(state = state, content = content, level = level, voice = voice)
                    Box(Modifier.size(52.dp)) {
                        if (state == VoiceState.Transcribing) {
                            RoundButton(AppIcons.Clear, stringResource(R.string.cd_voice_cancel)) { voice.cancel() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(TibetanColors.Brown600)
            .border(1.dp, TibetanColors.Line2, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = TibetanColors.Cream, modifier = Modifier.size(22.dp))
    }
}

/** Hold to talk, let go to send, slide left to cancel. TalkBack's double tap starts and stops. */
@Composable
private fun Orb(state: VoiceState, content: VoiceContent, level: Float, voice: VoiceController) {
    val density = LocalDensity.current
    val listening = state is VoiceState.Listening
    val cancelling = (state as? VoiceState.Listening)?.cancelArmed == true
    val busy = state == VoiceState.Transcribing
    val ring by animateFloatAsState(if (listening) 1f + level * 0.55f else 1f, tween(90), label = "ring")
    val press by animateFloatAsState(if (content.orbHeld) 1.08f else 1f, tween(120), label = "press")
    val fill = when {
        cancelling -> SolidColor(TibetanColors.Warning)
        busy -> SolidColor(TibetanColors.Brown500)
        else -> TibetanTokens.GoldVerticalBright
    }
    val label = stringResource(
        when {
            listening -> R.string.cd_voice_finish
            busy -> R.string.cd_voice_busy
            else -> R.string.cd_voice_start
        }
    )

    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(96.dp)
                    .scale(ring * 1.25f)
                    .clip(CircleShape)
                    .background((if (cancelling) TibetanColors.Warning else TibetanColors.Gold300).copy(alpha = 0.22f))
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(press)
                .clip(CircleShape)
                .background(fill)
                .semantics {
                    contentDescription = label
                    onClick {
                        if (listening) voice.finish() else voice.begin(Press.TAP)
                        true
                    }
                }
                .pointerInput(busy) {
                    if (busy) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        voice.begin(Press.HOLD)
                        var armed = false
                        do {
                            val event = awaitPointerEvent()
                            val p = event.changes.firstOrNull() ?: break
                            val dxDp = with(density) { (p.position.x - down.position.x).toDp().value }
                            val now = VoicePolicy.cancelArmed(dxDp)
                            if (now != armed) {
                                armed = now
                                voice.slide(armed)
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        voice.finish()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) Spinner() else Icon(
                if (cancelling) AppIcons.Clear else AppIcons.Mic,
                contentDescription = null,
                tint = TibetanColors.Espresso,
                modifier = Modifier.size(42.dp),
            )
        }
        if (busy) SpinnerRing()
    }
}

/** A gold arc turning around the orb while the words are being written. */
@Composable
private fun SpinnerRing() {
    val t = rememberInfiniteTransition(label = "ring")
    val angle by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle",
    )
    Canvas(Modifier.size(120.dp)) {
        drawArc(
            color = TibetanColors.Gold300, startAngle = angle, sweepAngle = 100f, useCenter = false,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Three dots that rise in turn, inside the busy orb. */
@Composable
private fun Spinner() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val a by t.animateFloat(
                0.3f, 1f,
                infiniteRepeatable(tween(500, delayMillis = i * 160, easing = LinearEasing), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(9.dp).alpha(a).clip(CircleShape).background(TibetanColors.Gold300))
        }
    }
}

/** A short rolling bar chart of the voice: moves while listening, breathes while thinking. */
@Composable
private fun LevelBars(level: Float, live: Boolean, busy: Boolean) {
    val bars = 28
    val history = remember { mutableStateOf(List(bars) { 0f }) }
    LaunchedEffect(level, live) {
        history.value = if (live) (history.value + level).takeLast(bars) else List(bars) { 0f }
    }
    val idle = TibetanColors.Brown400
    val t = rememberInfiniteTransition(label = "think")
    val phase by t.animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase",
    )
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val gap = 5.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        history.value.forEachIndexed { i, v ->
            val wave = 0.22f + 0.2f * kotlin.math.sin(phase - i * 0.45f)
            val h = (if (busy) wave else 0.08f + v * 0.92f).coerceIn(0.08f, 1f) * size.height
            drawRoundRect(
                color = if (busy) TibetanColors.Gold400.copy(alpha = 0.55f + wave) else if (live && v > 0.05f) TibetanColors.Gold300 else idle,
                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

/** Placeholder lines that shimmer where the words will appear: the answer is on its way. */
@Composable
private fun WritingSkeleton() {
    val t = rememberInfiniteTransition(label = "skeleton")
    val x by t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer",
    )
    val base = TibetanColors.Cream.copy(alpha = 0.08f)
    val glint = TibetanColors.Gold300.copy(alpha = 0.30f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(1f, 0.82f, 0.55f).forEach { frac ->
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            0f to base, (x - 0.25f).coerceIn(0f, 1f) to base,
                            x.coerceIn(0f, 1f) to glint,
                            (x + 0.25f).coerceIn(0f, 1f) to base, 1f to base,
                        )
                    )
            )
        }
    }
}
