package com.kharagedition.tibetankeyboard.ui.subscription

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharagedition.tibetankeyboard.R
import com.kharagedition.tibetankeyboard.subscription.BillingStore
import com.kharagedition.tibetankeyboard.subscription.CancelReason
import com.kharagedition.tibetankeyboard.subscription.ManageStep
import com.kharagedition.tibetankeyboard.subscription.PlanKind
import com.kharagedition.tibetankeyboard.subscription.SubscriptionSnapshot
import com.kharagedition.tibetankeyboard.subscription.SubscriptionStatus
import com.kharagedition.tibetankeyboard.ui.compose.components.AppIcons
import com.kharagedition.tibetankeyboard.ui.compose.components.BackHeader
import com.kharagedition.tibetankeyboard.ui.compose.components.ChevronIcon
import com.kharagedition.tibetankeyboard.ui.compose.components.GoldButton
import com.kharagedition.tibetankeyboard.ui.compose.components.PillBadge
import com.kharagedition.tibetankeyboard.ui.compose.components.ScreenScaffold
import com.kharagedition.tibetankeyboard.ui.compose.components.SectionLabel
import com.kharagedition.tibetankeyboard.ui.compose.components.SettingsDivider
import com.kharagedition.tibetankeyboard.ui.compose.components.SettingsGroup
import com.kharagedition.tibetankeyboard.ui.compose.components.SettingsRow
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanColors
import com.kharagedition.tibetankeyboard.ui.compose.theme.TibetanTokens
import java.text.DateFormat
import java.util.Date

/** Subscription overview and the cancel flow, in the app's own design system. */
@Composable
fun ManageSubscriptionScreen(
    state: ManageUiState,
    actions: ManageSubscriptionActions,
) {
    ScreenScaffold(scrollable = false, bottomPadding = 0.dp) {
        BackHeader(
            title = stringResource(
                if (state.step == ManageStep.OVERVIEW) R.string.subscription else R.string.sub_cancel_title
            ),
            onBack = actions.onBack,
        )
        val snapshot = state.snapshot
        when {
            state.loading -> CenteredBox { CircularProgressIndicator(color = TibetanColors.Gold300) }
            snapshot == null -> LoadFailed(actions.onRetry)
            else -> AnimatedContent(
                targetState = state.step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val slide = tween<androidx.compose.ui.unit.IntOffset>(260)
                    (slideInHorizontally(slide) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(260)))
                        .togetherWith(
                            slideOutHorizontally(slide) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(180))
                        )
                },
                label = "manage-step",
            ) { step ->
                Column(Modifier.fillMaxSize()) {
                    when (step) {
                        ManageStep.OVERVIEW -> Overview(state, snapshot, actions)
                        ManageStep.REASON -> ReasonStep(state, actions)
                        ManageStep.OFFER -> OfferStep(state, actions)
                        ManageStep.HANDOFF -> HandoffStep(state, snapshot, actions)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.Overview(
    state: ManageUiState,
    s: SubscriptionSnapshot,
    actions: ManageSubscriptionActions,
) {
    val status = SubscriptionStatus.of(s)
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ProScreenPadding)
            .padding(bottom = 24.dp),
    ) {
        PlanCard(s, status)

        when (status) {
            SubscriptionStatus.BILLING_ISSUE -> Notice(stringResource(R.string.sub_manage_billing_issue), warning = true)
            SubscriptionStatus.ENDING -> Notice(stringResource(R.string.sub_manage_ending_note), warning = false)
            else -> Unit
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.sub_manage_section))
        Spacer(Modifier.height(9.dp))
        SettingsGroup {
            val managedInStore = s.store == BillingStore.PLAY || s.store == BillingStore.WEB
            if (status == SubscriptionStatus.ENDING && managedInStore) {
                SettingsRow(
                    icon = AppIcons.Crown,
                    title = stringResource(R.string.sub_manage_resubscribe),
                    subtitle = stringResource(R.string.sub_manage_resubscribe_desc),
                    onClick = actions.onResubscribe,
                    trailing = { ChevronIcon() },
                )
                SettingsDivider()
            }
            if (managedInStore && status != SubscriptionStatus.LIFETIME) {
                SettingsRow(
                    icon = Icons.Rounded.CreditCard,
                    title = stringResource(R.string.sub_manage_payment),
                    subtitle = stringResource(
                        if (s.store == BillingStore.WEB) R.string.sub_manage_payment_desc_web
                        else R.string.sub_manage_payment_desc_play
                    ),
                    onClick = actions.onUpdatePayment,
                    trailing = { ChevronIcon() },
                )
                SettingsDivider()
            }
            SettingsRow(
                icon = Icons.Rounded.Refresh,
                title = stringResource(R.string.sub_manage_restore),
                subtitle = stringResource(R.string.sub_manage_restore_desc),
                onClick = if (state.busy) null else actions.onRestore,
                trailing = {
                    if (state.busy) {
                        CircularProgressIndicator(
                            color = TibetanColors.Gold300,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        ChevronIcon()
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Rounded.MailOutline,
                title = stringResource(R.string.sub_manage_support),
                subtitle = stringResource(R.string.sub_manage_support_desc),
                onClick = actions.onContactSupport,
                trailing = { ChevronIcon() },
            )
        }

        if (state.canCancel) {
            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Rounded.Close,
                    title = stringResource(R.string.sub_manage_cancel),
                    onClick = actions.onStartCancel,
                    titleColor = TibetanColors.Cream2,
                    iconTint = TibetanColors.CreamDim,
                    trailing = { ChevronIcon() },
                )
            }
        }

        val footer = when (s.store) {
            BillingStore.PLAY -> R.string.sub_manage_footer_play
            BillingStore.WEB -> R.string.sub_manage_footer_web
            else -> null
        }
        if (footer != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(footer),
                color = TibetanColors.CreamFaint,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
    }
}

/** The subscription at a glance: product, status, price, next date and store. */
@Composable
private fun PlanCard(s: SubscriptionSnapshot, status: SubscriptionStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TibetanTokens.GradBrown)
            .border(1.dp, TibetanColors.Gold200.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(TibetanTokens.GoldVerticalBright),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Crown, null, tint = TibetanColors.Espresso, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.premium_title),
                    color = TibetanColors.Cream, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text(stringResource(planLabel(s.plan)), color = TibetanColors.CreamDim, fontSize = 13.sp)
            }
            StatusPill(status)
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(TibetanColors.Line))
        Spacer(Modifier.height(6.dp))

        priceText(s)?.let { Fact(stringResource(R.string.sub_manage_price), it) }
        dateLabel(status)?.let { label ->
            val date = s.expiresAtMs?.let(::formatDate)
            if (date != null) Fact(stringResource(label), date)
        }
        storeLabel(s.store)?.let { Fact(stringResource(R.string.sub_manage_billed_via), stringResource(it)) }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TibetanColors.CreamDim, fontSize = 13.sp)
        Text(value, color = TibetanColors.Cream, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(status: SubscriptionStatus) {
    val (label, color) = when (status) {
        SubscriptionStatus.ACTIVE -> R.string.sub_status_active to TibetanColors.Jade
        SubscriptionStatus.GRANTED -> R.string.sub_status_active to TibetanColors.Jade
        SubscriptionStatus.TRIAL -> R.string.sub_status_trial to TibetanColors.Gold300
        SubscriptionStatus.LIFETIME -> R.string.sub_status_lifetime to TibetanColors.Gold300
        SubscriptionStatus.ENDING -> R.string.sub_status_ending to TibetanColors.Cream2
        SubscriptionStatus.BILLING_ISSUE -> R.string.sub_status_billing to TibetanColors.Warning
        SubscriptionStatus.INACTIVE -> R.string.sub_status_inactive to TibetanColors.CreamDim
    }
    Row(
        modifier = Modifier
            .clip(TibetanTokens.Pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(stringResource(label), color = color, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Notice(text: String, warning: Boolean) {
    Spacer(Modifier.height(12.dp))
    Text(
        text,
        color = if (warning) TibetanColors.Cream else TibetanColors.Cream2,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (warning) TibetanColors.MaroonDeep else TibetanColors.Brown700)
            .border(
                1.dp,
                if (warning) TibetanColors.Warning.copy(alpha = 0.45f) else TibetanColors.Line,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Cancel flow
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.ReasonStep(state: ManageUiState, actions: ManageSubscriptionActions) {
    StepLayout(
        footer = {
            GoldButton(
                text = stringResource(R.string.premium_continue),
                onClick = actions.onContinueFromReason,
                enabled = state.reason != null && !state.busy,
            )
            QuietButton(stringResource(R.string.sub_cancel_keep), actions.onKeep)
        },
    ) {
        StepHeading(
            stringResource(R.string.sub_cancel_reason_heading),
            stringResource(R.string.sub_cancel_reason_body),
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CancelReason.entries.forEach { reason ->
                ChoiceRow(
                    text = stringResource(reasonLabel(reason)),
                    selected = state.reason == reason,
                    onClick = { actions.onSelectReason(reason) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.OfferStep(state: ManageUiState, actions: ManageSubscriptionActions) {
    val offer = state.offer ?: return
    StepLayout(
        footer = {
            GoldButton(
                text = stringResource(R.string.sub_offer_accept, offer.discountedPrice),
                onClick = actions.onAcceptOffer,
                enabled = !state.busy,
            )
            QuietButton(stringResource(R.string.sub_offer_decline), actions.onDeclineOffer)
        },
    ) {
        PaywallHero(
            title = stringResource(R.string.sub_offer_heading),
            subtitle = stringResource(R.string.sub_offer_body),
            top = { PillBadge(stringResource(R.string.sub_offer_badge, offer.percentOff)) },
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    offer.fullPrice,
                    color = TibetanColors.CreamFaint, fontSize = 17.sp,
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
                Spacer(Modifier.width(10.dp))
                // One localized "%1$s / month" string, with the price picked out, so translators
                // control the word order.
                Text(emphasizedPrice(stringResource(R.string.premium_per_month, offer.discountedPrice), offer.discountedPrice))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                pluralStringResource(R.plurals.sub_offer_terms, offer.months, offer.months, offer.fullPrice),
                color = TibetanColors.Cream2.copy(alpha = 0.8f), fontSize = 12.5.sp, lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.sub_offer_keep_section))
        Spacer(Modifier.height(12.dp))
        ProBenefitList()
    }
}

@Composable
private fun ColumnScope.HandoffStep(
    state: ManageUiState,
    s: SubscriptionSnapshot,
    actions: ManageSubscriptionActions,
) {
    val web = s.store == BillingStore.WEB
    val until = s.expiresAtMs?.let(::formatDate) ?: stringResource(R.string.sub_handoff_period_end)
    StepLayout(
        footer = {
            GoldButton(
                text = stringResource(if (web) R.string.sub_handoff_open_web else R.string.sub_handoff_open_play),
                onClick = actions.onOpenStore,
            )
            QuietButton(stringResource(R.string.sub_handoff_keep), actions.onKeep)
        },
    ) {
        StepHeading(
            stringResource(if (web) R.string.sub_handoff_heading_web else R.string.sub_handoff_heading_play),
            stringResource(if (web) R.string.sub_handoff_body_web else R.string.sub_handoff_body_play, until),
        )
        if (s.store == BillingStore.PLAY) {
            // Play opens under whichever Google account it last used, not the one that subscribed.
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.sub_handoff_account_hint),
                color = TibetanColors.CreamFaint, fontSize = 12.5.sp, lineHeight = 17.sp,
            )
        }
        if (state.reason == CancelReason.BOUGHT_BY_MISTAKE && s.store == BillingStore.PLAY) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TibetanColors.Brown700)
                    .border(1.dp, TibetanColors.Line, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(R.string.sub_handoff_refund_title),
                    color = TibetanColors.Cream, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.sub_handoff_refund_body),
                    color = TibetanColors.CreamDim, fontSize = 12.5.sp, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = actions.onRequestRefund),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.sub_handoff_refund_action),
                        color = TibetanColors.Gold300, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    )
                    Icon(AppIcons.Chevron, null, tint = TibetanColors.Gold300, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.sub_handoff_lose_section), color = TibetanColors.CreamDim)
        Spacer(Modifier.height(12.dp))
        ProBenefitList(muted = true)
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

/** Scrolling body with a footer pinned above the navigation bar. */
@Composable
private fun ColumnScope.StepLayout(
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ProScreenPadding)
            .padding(top = 4.dp, bottom = 16.dp),
        content = content,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TibetanColors.Bg900)
            .padding(horizontal = ProScreenPadding)
            .padding(top = 10.dp, bottom = 12.dp),
        content = footer,
    )
}

@Composable
private fun StepHeading(title: String, body: String) {
    Text(title, color = TibetanColors.Cream, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
    Spacer(Modifier.height(6.dp))
    Text(body, color = TibetanColors.CreamDim, fontSize = 13.5.sp, lineHeight = 19.sp)
}

/** Selectable answer; selection reads from the radio, fill and gold border, not colour alone. */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TibetanColors.Brown600 else TibetanColors.Brown700)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) TibetanColors.Gold400 else TibetanColors.Gold200.copy(alpha = 0.18f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) TibetanColors.Gold400 else TibetanColors.CreamDim.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(TibetanColors.Gold300))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = TibetanColors.Cream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Secondary action under a [GoldButton]: gold text, full-width tap target. */
@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = TibetanColors.Gold300,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun ColumnScope.CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ColumnScope.LoadFailed(onRetry: () -> Unit) {
    CenteredBox {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.sub_manage_load_failed),
                color = TibetanColors.CreamDim, fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            GoldButton(stringResource(R.string.premium_retry), onClick = onRetry)
        }
    }
}

private fun planLabel(plan: PlanKind) = when (plan) {
    PlanKind.MONTHLY -> R.string.sub_manage_plan_monthly
    PlanKind.ANNUAL -> R.string.sub_manage_plan_annual
    PlanKind.LIFETIME -> R.string.sub_manage_plan_lifetime
    PlanKind.OTHER -> R.string.sub_manage_plan_other
}

private fun reasonLabel(reason: CancelReason) = when (reason) {
    CancelReason.TOO_EXPENSIVE -> R.string.sub_cancel_reason_expensive
    CancelReason.NOT_USING -> R.string.sub_cancel_reason_not_using
    CancelReason.MISSING_FEATURE -> R.string.sub_cancel_reason_missing
    CancelReason.BOUGHT_BY_MISTAKE -> R.string.sub_cancel_reason_mistake
    CancelReason.OTHER -> R.string.sub_cancel_reason_other
}

private fun dateLabel(status: SubscriptionStatus) = when (status) {
    SubscriptionStatus.ACTIVE -> R.string.sub_manage_renews_on
    SubscriptionStatus.TRIAL -> R.string.sub_manage_trial_ends_on
    SubscriptionStatus.ENDING -> R.string.sub_manage_ends_on
    SubscriptionStatus.BILLING_ISSUE, SubscriptionStatus.GRANTED -> R.string.sub_manage_expires_on
    SubscriptionStatus.LIFETIME, SubscriptionStatus.INACTIVE -> null
}

private fun storeLabel(store: BillingStore) = when (store) {
    BillingStore.PLAY -> R.string.sub_manage_store_play
    BillingStore.WEB -> R.string.sub_manage_store_web
    BillingStore.GRANTED -> R.string.sub_manage_store_granted
    BillingStore.OTHER -> null
}

@Composable
private fun priceText(s: SubscriptionSnapshot): String? = when {
    s.plan == PlanKind.LIFETIME -> stringResource(R.string.sub_manage_price_once)
    s.price == null -> null
    s.plan == PlanKind.MONTHLY -> stringResource(R.string.premium_per_month, s.price)
    s.plan == PlanKind.ANNUAL -> stringResource(R.string.sub_manage_price_year, s.price)
    else -> s.price
}

/** [text] with [price] in it set large and gold, the rest small. */
private fun emphasizedPrice(text: String, price: String): AnnotatedString = buildAnnotatedString {
    val rest = SpanStyle(color = TibetanColors.Cream2, fontSize = 14.sp)
    val at = text.indexOf(price)
    if (at < 0) {
        withStyle(rest) { append(text) }
        return@buildAnnotatedString
    }
    withStyle(rest) { append(text.substring(0, at)) }
    withStyle(SpanStyle(color = TibetanColors.Gold200, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)) { append(price) }
    withStyle(rest) { append(text.substring(at + price.length)) }
}

private fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(ms))
