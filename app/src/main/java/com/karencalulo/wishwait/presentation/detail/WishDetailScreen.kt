package com.karencalulo.wishwait.presentation.detail

import com.karencalulo.wishwait.presentation.components.GoalReachedDialog
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.karencalulo.wishwait.presentation.common.labelRes
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.HOUR_MS
import com.karencalulo.wishwait.domain.model.PriceImpact
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.presentation.common.Countdown
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.FriendVoice
import com.karencalulo.wishwait.presentation.common.LocalMoney
import com.karencalulo.wishwait.presentation.common.appViewModel
import com.karencalulo.wishwait.presentation.common.rememberNow
import com.karencalulo.wishwait.presentation.components.ConfettiOverlay
import com.karencalulo.wishwait.presentation.components.CountdownRing
import com.karencalulo.wishwait.presentation.components.DesireChart
import com.karencalulo.wishwait.presentation.components.DesirePicker
import com.karencalulo.wishwait.presentation.components.EmojiAvatar
import com.karencalulo.wishwait.presentation.components.FriendBubble
import com.karencalulo.wishwait.presentation.components.SectionTitle
import com.karencalulo.wishwait.presentation.components.StatTile
import kotlin.math.ceil

@Composable
fun WishDetailRoute(
    wishId: String,
    onBack: () -> Unit,
    onOpenCalculator: (Double) -> Unit,
) {
    val vm = appViewModel(key = "detail_$wishId") {
        WishDetailViewModel(
            wishId = wishId,
            uc = WishDetailUseCases(
                observeWish = it.observeWish,
                observeProfile = it.observeProfile,
                calculateImpact = it.calculateImpact,
                calculateWorth = it.calculateWorth,
                decideWish = it.decideWish,
                extendCooldown = it.extendCooldown,
                logDesire = it.logDesire,
                ratePurchase = it.ratePurchase,
                deleteWish = it.deleteWish,
                logUse = it.logUse,
                setWorthTarget = it.setWorthTarget,
                setPaymentPlan = it.setPaymentPlan,
                markPaymentPaid = it.markPaymentPaid,
                editWish = it.editWish,
                observeGoal = it.observeGoal,
                addToGoal = it.addToGoal,
                buyAdvice = it.buyAdvice,
                observeWishes = it.observeWishes,
                cutoffSpending = it.cutoffSpending,
                clock = it.clock,
            ),
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = res()
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                WishDetailEffect.Closed -> onBack()
                is WishDetailEffect.OpenCalculator -> onOpenCalculator(effect.price)
                is WishDetailEffect.Message -> snackbar.showSnackbar(effect.text(resources))
            }
        }
    }
    WishDetailScreen(state, vm::onIntent, onBack, snackbar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishDetailScreen(
    state: WishDetailState,
    onIntent: (WishDetailIntent) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val now = rememberNow()
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(state.wish?.name ?: "") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (state.wish != null) {
                            IconButton(onClick = { onIntent(WishDetailIntent.OpenEditor) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                            IconButton(onClick = { onIntent(WishDetailIntent.DeleteRequested) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            val wish = state.wish
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                wish == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.detail_gone))
                }
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Header(wish, state.impact)
                    when (wish.status) {
                        WishStatus.WAITING -> {
                            if (wish.isReady(now)) DecisionCard(wish, onIntent) else CountdownCard(wish, now, onIntent)
                            state.buyAdvice?.let { advice ->
                                val (title, message) = FriendVoice.buyAdvice(res(), advice)
                                FriendBubble(headline = title, message = message)
                            }
                            StoreLinkCard(wish, now)
                            CheckInCard(wish, now, onIntent)
                            ReflectionCard(wish, now)
                            PastYouCard(wish)
                            AffordCard(wish, state, onIntent)
                        }
                        WishStatus.BOUGHT -> {
                            BoughtCard(wish, now, onIntent)
                            StoreLinkCard(wish, now)
                            state.worth?.let { WorthItCard(wish, it, wish.profileAtCreation(state.profile), now, onIntent) }
                            PaymentCard(wish, now, onIntent)
                            PastYouCard(wish)
                            if (wish.desireLog.size > 1) CheckInHistory(wish)
                        }
                        WishStatus.LET_GO -> {
                            LetGoCard(wish, state)
                            PastYouCard(wish)
                            if (wish.desireLog.size > 1) CheckInHistory(wish)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        when (state.celebration) {
            Celebration.LET_GO -> {
                val goal = state.goal?.takeUnless { it.isReached }
                CelebrationDialog(
                    emoji = "🕊️",
                    title = stringResource(R.string.kept_title, state.wish?.let { LocalMoney.current.format(it.price) } ?: ""),
                    body = stringResource(R.string.celebrate_kept_body),
                    onDone = { onIntent(WishDetailIntent.CelebrationDone) },
                    // One tap: move the money you didn't spend toward the goal.
                    primaryLabel = goal?.let { stringResource(R.string.goal_letgo_add, it.emoji, it.name) },
                    onPrimary = { onIntent(WishDetailIntent.AddLetGoToGoal) },
                    skipLabel = goal?.let { stringResource(R.string.goal_letgo_skip) },
                )
            }
            Celebration.GOAL_REACHED -> GoalReachedDialog(
                state.goal?.name.orEmpty(),
                onDone = { onIntent(WishDetailIntent.CelebrationDone) },
            )
            Celebration.MINDFUL_BUY -> CelebrationDialog(
                emoji = "🎉",
                title = stringResource(R.string.celebrate_mindful_title),
                body = stringResource(R.string.celebrate_mindful_body),
                onDone = { onIntent(WishDetailIntent.CelebrationDone) },
            )
            Celebration.WORTH_IT -> CelebrationDialog(
                emoji = "💯",
                title = stringResource(R.string.celebrate_worth_title),
                body = stringResource(
                    R.string.celebrate_worth_body,
                    state.worth?.let { LocalMoney.current.format(it.costPerUse) } ?: "",
                ),
                onDone = { onIntent(WishDetailIntent.CelebrationDone) },
            )
            Celebration.DEBT_FREE -> CelebrationDialog(
                emoji = "🎊",
                title = stringResource(R.string.celebrate_debt_title),
                body = stringResource(R.string.celebrate_debt_body, state.wish?.name ?: ""),
                onDone = { onIntent(WishDetailIntent.CelebrationDone) },
            )
            null -> Unit
        }
    }

    val dialogWish = state.wish
    if (state.showPaymentSetup && dialogWish != null) {
        PaymentSetupDialog(
            wish = dialogWish,
            onSave = { months, rate, paid -> onIntent(WishDetailIntent.SavePaymentPlan(months, rate, paid)) },
            onRemove = { onIntent(WishDetailIntent.RemovePaymentPlan) },
            onDismiss = { onIntent(WishDetailIntent.DismissDialogs) },
        )
    }
    if (state.showEditor && dialogWish != null) {
        EditWishDialog(
            wish = dialogWish,
            onSave = { name, price, category, note, reasons ->
                onIntent(WishDetailIntent.SaveEdit(name, price, category, note, reasons))
            },
            onDismiss = { onIntent(WishDetailIntent.DismissDialogs) },
        )
    }
    if (state.showTargetEditor && dialogWish != null) {
        WorthTargetDialog(
            wish = dialogWish,
            profile = dialogWish.profileAtCreation(state.profile),
            onSave = { onIntent(WishDetailIntent.SaveTarget(it)) },
            onDismiss = { onIntent(WishDetailIntent.DismissDialogs) },
        )
    }

    if (state.showBuyEarlyConfirm) {
        val remaining = state.wish?.remainingMillis(now) ?: 0L
        val days = ceil(remaining.toDouble() / DAY_MS).toInt()
        AlertDialog(
            onDismissRequest = { onIntent(WishDetailIntent.DismissDialogs) },
            icon = { Text(FriendVoice.AVATAR, fontSize = 36.sp, modifier = Modifier.clearAndSetSemantics { }) },
            title = { Text(stringResource(R.string.early_title, Fmt.days(res(), days))) },
            text = {
                Text(
                    stringResource(if (remaining < DAY_MS) R.string.early_body_hours else R.string.early_body),
                )
            },
            confirmButton = {
                Button(onClick = { onIntent(WishDetailIntent.DismissDialogs) }) { Text(stringResource(R.string.early_keep_waiting)) }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(WishDetailIntent.BuyEarlyConfirmed) }) { Text(stringResource(R.string.early_buy)) }
            },
        )
    }

    state.overLine?.let { info ->
        val money = LocalMoney.current
        val nextDate = Fmt.date(info.nextCutoffStart)
        AlertDialog(
            onDismissRequest = { onIntent(WishDetailIntent.DismissDialogs) },
            icon = { Text(FriendVoice.AVATAR, fontSize = 36.sp, modifier = Modifier.clearAndSetSemantics { }) },
            title = { Text(stringResource(R.string.over_line_title), textAlign = TextAlign.Center) },
            text = {
                Text(
                    stringResource(
                        if (info.hasPaydays) R.string.over_line_body else R.string.over_line_body_month,
                        money.format(info.total),
                        Fmt.percent(info.share),
                        Fmt.percent(info.lineShare),
                        stringResource(if (info.hasPaydays) R.string.over_line_next_payday else R.string.over_line_next_month, nextDate),
                    ),
                )
            },
            confirmButton = {
                Button(onClick = { onIntent(WishDetailIntent.WaitForNextCutoff) }) {
                    Text(stringResource(if (info.hasPaydays) R.string.over_line_wait_payday else R.string.over_line_wait_month))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(WishDetailIntent.BuyOverLineConfirmed) }) {
                    Text(stringResource(R.string.over_line_buy))
                }
            },
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { onIntent(WishDetailIntent.DismissDialogs) },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { onIntent(WishDetailIntent.DeleteConfirmed) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(WishDetailIntent.DismissDialogs) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CelebrationDialog(
    emoji: String,
    title: String,
    body: String,
    onDone: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    skipLabel: String? = null,
) {
    ConfettiOverlay(trigger = 1)
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Text(emoji, fontSize = 40.sp, modifier = Modifier.clearAndSetSemantics { }) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(body, textAlign = TextAlign.Center) },
        confirmButton = {
            if (primaryLabel != null) {
                Button(onClick = onPrimary) { Text(primaryLabel) }
            } else {
                Button(onClick = onDone) { Text(stringResource(R.string.action_yay)) }
            }
        },
        dismissButton = skipLabel?.let { label -> { TextButton(onClick = onDone) { Text(label) } } },
    )
}

@Composable
private fun Header(wish: Wish, impact: PriceImpact?) {
    val money = LocalMoney.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        EmojiAvatar(wish.category.emoji, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(money.format(wish.price), style = MaterialTheme.typography.headlineMedium)
            if (impact != null) {
                Text(
                    stringResource(
                        R.string.detail_impact_line,
                        Fmt.incomeShare(impact.incomeRatio),
                        Fmt.incomeShareLabel(res(), impact.incomeRatio),
                        Fmt.hours(res(), impact.workHours),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CountdownCard(wish: Wish, now: Long, onIntent: (WishDetailIntent) -> Unit) {
    val c = Countdown.of(wish.remainingMillis(now))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.detail_cooling), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            CountdownRing(
                progress = wish.progress(now),
                size = 168.dp,
                strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${c.days}", style = MaterialTheme.typography.displayMedium)
                    Text(stringResource(if (c.days == 1L) R.string.detail_day_left else R.string.detail_days_left), style = MaterialTheme.typography.labelLarge)
                    Text(
                        "%02d:%02d:%02d".format(c.hours, c.minutes, c.seconds),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.detail_decide_on, Fmt.date(wish.cooldownEndsAt), elapsedDays(wish, now), wish.cooldownDays),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onIntent(WishDetailIntent.Extend(7)) }) {
                    Icon(Icons.Rounded.MoreTime, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.detail_plus7))
                }
                OutlinedButton(onClick = { onIntent(WishDetailIntent.LetGo) }) { Text(stringResource(R.string.verdict_let_go)) }
            }
            TextButton(onClick = { onIntent(WishDetailIntent.BuyRequested) }) {
                Text(stringResource(R.string.detail_cant_wait), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }
        }
    }
}

private fun elapsedDays(wish: Wish, now: Long): Int =
    (((now - wish.createdAt) / DAY_MS) + 1).toInt().coerceIn(1, wish.cooldownDays.coerceAtLeast(1))

@Composable
private fun DecisionCard(wish: Wish, onIntent: (WishDetailIntent) -> Unit) {
    val drop = (wish.firstDesire ?: 0) - (wish.latestDesire ?: 0)
    val advice = when {
        wish.desireLog.size < 2 -> stringResource(R.string.decide_advice_no_checkins)
        drop >= 2 -> stringResource(R.string.decide_advice_drop)
        drop < 0 -> stringResource(R.string.decide_advice_rise)
        else -> stringResource(R.string.decide_advice_steady)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.decide_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            FriendBubble(headline = stringResource(R.string.decide_waited, Fmt.days(res(), wish.cooldownDays)), message = advice)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onIntent(WishDetailIntent.BuyRequested) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.decide_buy))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onIntent(WishDetailIntent.LetGo) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.decide_let_go))
            }
            TextButton(onClick = { onIntent(WishDetailIntent.Extend(7)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.decide_more_time))
            }
        }
    }
}

@Composable
private fun CheckInCard(wish: Wish, now: Long, onIntent: (WishDetailIntent) -> Unit) {
    val last = wish.desireLog.lastOrNull()
    val checkedRecently = last != null && wish.desireLog.size > 1 && now - last.timestamp < 12 * HOUR_MS
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(stringResource(R.string.checkin_title))
            Text(
                stringResource(if (checkedRecently) R.string.checkin_done_today else R.string.checkin_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            DesirePicker(
                selected = if (checkedRecently) last?.level else null,
                onSelect = { onIntent(WishDetailIntent.LogDesire(it)) },
            )
            if (wish.desireLog.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                DesireChart(wish.desireLog.map { it.level })
                FriendVoice.desireInsight(res(), wish)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CheckInHistory(wish: Wish) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(stringResource(R.string.checkin_history_title))
            DesireChart(wish.desireLog.map { it.level })
            FriendVoice.desireInsight(res(), wish)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReflectionCard(wish: Wish, now: Long) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💭", fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.reflect_title), style = MaterialTheme.typography.labelMedium)
                Text(FriendVoice.reflection(res(), wish, now), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PastYouCard(wish: Wish) {
    if (wish.reasons.isEmpty() && wish.moods.isEmpty() && wish.note.isBlank()) return
    Column {
        SectionTitle(stringResource(R.string.past_you_title))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            wish.reasons.forEach { SuggestionChip(onClick = {}, label = { Text("${it.emoji} ${stringResource(it.labelRes)}") }) }
            wish.moods.forEach { SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.past_you_feeling, it.emoji, stringResource(it.labelRes).lowercase())) }) }
        }
        if (wish.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "“${wish.note}”",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.past_you_added, Fmt.date(wish.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AffordCard(wish: Wish, state: WishDetailState, onIntent: (WishDetailIntent) -> Unit) {
    val money = LocalMoney.current
    val savings = state.profile.monthlySavings
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(stringResource(R.string.afford_title))
            if (savings > 0) {
                val months = ceil(wish.price / savings).toInt()
                Text(
                    stringResource(R.string.afford_savings, money.format(savings), Fmt.monthsCount(res(), months.coerceAtLeast(1))),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    stringResource(R.string.afford_no_savings),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("🧾", money.format(wish.price / 365.0 / 2), stringResource(R.string.afford_per_day), Modifier.weight(1f))
                StatTile("📈", money.format(wish.price * 1.06 * 1.06 * 1.06 * 1.06 * 1.06), stringResource(R.string.afford_invested), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = { onIntent(WishDetailIntent.PlanPayment) },
                label = { Text(stringResource(R.string.afford_open_calc)) },
                leadingIcon = { Icon(Icons.Rounded.Calculate, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun BoughtCard(wish: Wish, now: Long, onIntent: (WishDetailIntent) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (wish.boughtEarly) "🛍️" else "✅", fontSize = 40.sp, modifier = Modifier.clearAndSetSemantics { })
            Text(
                stringResource(if (wish.boughtEarly) R.string.bought_early else R.string.bought_mindfully),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.bought_on, Fmt.date(wish.decidedAt ?: wish.createdAt)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.glad_question), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val glad = wish.gladRating
                if (glad == true) {
                    Button(onClick = { onIntent(WishDetailIntent.RatePurchase(true)) }) { Text(stringResource(R.string.glad_love)) }
                } else {
                    OutlinedButton(onClick = { onIntent(WishDetailIntent.RatePurchase(true)) }) { Text(stringResource(R.string.glad_love)) }
                }
                if (glad == false) {
                    Button(onClick = { onIntent(WishDetailIntent.RatePurchase(false)) }) { Text(stringResource(R.string.glad_regret)) }
                } else {
                    OutlinedButton(onClick = { onIntent(WishDetailIntent.RatePurchase(false)) }) { Text(stringResource(R.string.glad_regret)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            RegretFollowUp(wish, now)
        }
    }
}

@Composable
private fun LetGoCard(wish: Wish, state: WishDetailState) {
    val money = LocalMoney.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🕊️", fontSize = 40.sp, modifier = Modifier.clearAndSetSemantics { })
            Text(stringResource(R.string.let_go_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.let_go_kept, money.format(wish.price)), style = MaterialTheme.typography.headlineSmall)
            state.impact?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.let_go_hours, Fmt.hours(res(), it.workHours)),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
