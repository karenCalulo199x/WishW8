package com.karencalulo.wishwait.presentation.detail

import androidx.compose.ui.semantics.clearAndSetSemantics
import com.karencalulo.wishwait.presentation.common.labelRes
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WorthProgress
import com.karencalulo.wishwait.domain.usecase.AffordabilityCalculator
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.LocalMoney
import com.karencalulo.wishwait.presentation.components.AmountField
import com.karencalulo.wishwait.presentation.components.SectionTitle
import com.karencalulo.wishwait.presentation.components.StatTile

/** "Is it paying for itself yet?" — cost per use drops with every logged use. */
@Composable
fun WorthItCard(
    wish: Wish,
    worth: WorthProgress,
    profile: UserProfile,
    now: Long,
    onIntent: (WishDetailIntent) -> Unit,
) {
    val money = LocalMoney.current
    val usingDefault = wish.worthTargetPerUse == null && profile.hourlyRate > 0
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (worth.isWorthIt) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(stringResource(R.string.worth_title)) {
                TextButton(onClick = { onIntent(WishDetailIntent.OpenTargetEditor) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.worth_goal))
                }
            }
            if (worth.uses == 0) {
                Text(stringResource(R.string.worth_not_used), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.worth_not_used_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(money.format(worth.costPerUse), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.worth_per_use),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { worth.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.worth_uses_of, worth.uses, worth.targetUses), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (worth.isWorthIt) stringResource(R.string.worth_done) else stringResource(R.string.worth_to_go, worth.usesLeft),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (worth.isWorthIt) {
                    stringResource(R.string.worth_paid_for_itself, money.format(worth.costPerUse), money.format(worth.targetPerUse))
                } else {
                    stringResource(
                        if (usingDefault) R.string.worth_goal_line_default else R.string.worth_goal_line,
                        money.format(worth.targetPerUse),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (wish.payment != null && wish.costBasis > wish.price + 0.5) {
                Text(
                    stringResource(R.string.worth_interest, money.format(wish.costBasis - wish.price)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val lastUse = wish.usageLog.lastOrNull()
            if (lastUse != null && now - lastUse > 30 * DAY_MS && !worth.isWorthIt) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.worth_unused_month),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onIntent(WishDetailIntent.LogUse) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.worth_used_it))
                }
                if (worth.uses > 0) {
                    TextButton(onClick = { onIntent(WishDetailIntent.UndoUse) }) { Text(stringResource(R.string.action_undo)) }
                }
            }
            if (lastUse != null) {
                Text(
                    stringResource(R.string.worth_last_used, Fmt.date(lastUse)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PaymentCard(wish: Wish, now: Long, onIntent: (WishDetailIntent) -> Unit) {
    val money = LocalMoney.current
    val plan = wish.payment
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (plan == null) {
                SectionTitle(stringResource(R.string.pay_how_title))
                Text(
                    stringResource(R.string.pay_how_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onIntent(WishDetailIntent.OpenPaymentSetup) }) {
                    Text(stringResource(R.string.pay_track))
                }
            } else {
            SectionTitle(stringResource(R.string.pay_title)) {
                TextButton(onClick = { onIntent(WishDetailIntent.OpenPaymentSetup) }) { Text(stringResource(R.string.action_edit)) }
            }
            if (plan.isComplete) {
                Text(stringResource(R.string.pay_paid_off), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.pay_paid_off_body, plan.months, money.format(plan.totalPaid)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    stringResource(R.string.pay_progress, plan.paidCount, plan.months),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { plan.paidCount.toFloat() / plan.months },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(MaterialTheme.shapes.small),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("📆", money.format(plan.monthlyPayment), stringResource(R.string.pay_per_month), Modifier.weight(1f))
                    StatTile("⏳", money.format(plan.remaining), stringResource(R.string.pay_left), Modifier.weight(1f))
                }
                plan.nextDueAt?.let { due ->
                    Spacer(Modifier.height(8.dp))
                    val overdue = due < now
                    Text(
                        if (overdue) stringResource(R.string.pay_overdue, plan.paidCount + 1, Fmt.date(due))
                        else stringResource(R.string.pay_next_due, Fmt.date(due)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onIntent(WishDetailIntent.MarkPaymentPaid) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pay_mark, plan.paidCount + 1))
                    }
                    if (plan.paidCount > 0) {
                        TextButton(onClick = { onIntent(WishDetailIntent.UndoPayment) }) { Text(stringResource(R.string.action_undo)) }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun PaymentSetupDialog(
    wish: Wish,
    onSave: (months: Int, annualRate: Double, alreadyPaid: Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    val existing = wish.payment
    var months by remember { mutableIntStateOf(existing?.months ?: 12) }
    var rate by remember { mutableStateOf("0") }
    var alreadyPaid by remember { mutableIntStateOf(existing?.paidCount ?: 0) }
    val preview = remember(months, rate) {
        AffordabilityCalculator().installment(wish.price, months, Fmt.parseAmount(rate) ?: 0.0, 0.0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pay_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.pay_months), style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(3, 6, 9, 12, 18, 24, 36).forEach { m ->
                        FilterChip(
                            selected = months == m,
                            onClick = {
                                months = m
                                alreadyPaid = alreadyPaid.coerceAtMost(m)
                            },
                            label = { Text("$m") },
                        )
                    }
                }
                AmountField(
                    value = rate,
                    onValueChange = { rate = Fmt.sanitizeAmount(it).take(5) },
                    label = stringResource(R.string.pay_interest),
                    suffix = "%",
                    supportingText = stringResource(R.string.pay_interest_hint),
                )
                Text(stringResource(R.string.pay_already), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { alreadyPaid = (alreadyPaid - 1).coerceAtLeast(0) }) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_fewer))
                    }
                    Text(
                        "$alreadyPaid",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    FilledTonalIconButton(onClick = { alreadyPaid = (alreadyPaid + 1).coerceAtMost(months) }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_more))
                    }
                }
                preview?.let {
                    Text(
                        stringResource(R.string.pay_preview, money.format(it.monthlyPayment), money.format(it.totalPaid)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.pay_first_due),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(months, Fmt.parseAmount(rate) ?: 0.0, alreadyPaid) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.pay_in_full), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
fun WorthTargetDialog(
    wish: Wish,
    profile: UserProfile,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = LocalMoney.current
    var text by remember { mutableStateOf(wish.worthTargetPerUse?.let { Fmt.plain(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🎯", fontSize = 32.sp, modifier = Modifier.clearAndSetSemantics { }) },
        title = { Text(stringResource(R.string.target_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.target_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AmountField(
                    value = text,
                    onValueChange = { text = Fmt.sanitizeAmount(it) },
                    label = stringResource(R.string.target_field),
                    prefix = money.symbol,
                    supportingText = if (profile.hourlyRate > 0) {
                        stringResource(R.string.target_hint_default, money.format(profile.hourlyRate))
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(Fmt.parseAmount(text)) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Line under the rating buttons: when we'll check in, or what we learned. */
@Composable
fun RegretFollowUp(wish: Wish, now: Long) {
    val check = wish.regretCheckAt
    val text = when {
        wish.gladRating == false ->
            stringResource(R.string.regret_noted, stringResource(wish.category.labelRes).lowercase())
        wish.gladRating == true -> stringResource(R.string.love_noted)
        check != null && check > now -> stringResource(R.string.regret_check_on, Fmt.date(check))
        else -> stringResource(R.string.regret_check_now)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The store link stays hidden during the cooldown (out of sight, out of mind)
 * and unlocks when it's time to decide. Never shown after letting go.
 */
@Composable
fun StoreLinkCard(wish: Wish, now: Long) {
    val link = wish.link ?: return
    if (wish.status == com.karencalulo.wishwait.domain.model.WishStatus.LET_GO) return
    val locked = wish.status == com.karencalulo.wishwait.domain.model.WishStatus.WAITING && !wish.isReady(now)
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val storeName = wish.store ?: stringResource(R.string.link_store_generic)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (locked) {
                Text(stringResource(R.string.link_locked_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.link_locked_body, storeName, Fmt.date(wish.cooldownEndsAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(
                    onClick = { runCatching { uriHandler.openUri(link) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        wish.store?.let { stringResource(R.string.link_open, it) }
                            ?: stringResource(R.string.link_open_generic),
                    )
                }
            }
        }
    }
}
