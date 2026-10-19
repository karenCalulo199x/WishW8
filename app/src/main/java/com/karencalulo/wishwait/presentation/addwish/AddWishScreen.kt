@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.karencalulo.wishwait.presentation.addwish

import com.karencalulo.wishwait.presentation.components.GoalReachedDialog
import androidx.compose.material.icons.rounded.ContentPaste
import android.content.ClipboardManager
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.karencalulo.wishwait.presentation.common.labelRes
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.data.reminder.NotificationHelper
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.Mood
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.Tier
import com.karencalulo.wishwait.domain.model.Verdict
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.FriendVoice
import com.karencalulo.wishwait.presentation.common.LocalMoney
import com.karencalulo.wishwait.presentation.common.appViewModel
import com.karencalulo.wishwait.presentation.components.ConfettiOverlay
import com.karencalulo.wishwait.presentation.components.DesirePicker
import com.karencalulo.wishwait.presentation.components.NeedWantPicker
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.karencalulo.wishwait.presentation.components.FriendBubble
import com.karencalulo.wishwait.presentation.components.ImpactMeter
import com.karencalulo.wishwait.presentation.components.MoneyField
import com.karencalulo.wishwait.presentation.components.StatTile

@Composable
fun AddWishRoute(
    shared: SharedText? = null,
    onClose: () -> Unit,
    onOpenWish: (String) -> Unit,
    onOpenCalculator: (Double) -> Unit,
) {
    val vm = appViewModel(key = "add_${shared?.hashCode() ?: 0}") {
        AddWishViewModel(
            shared,
            it.parseSharedText,
            it.fetchLinkDetails,
            it.observeProfile,
            it.observeWishes,
            it.learnFromHistory,
            it.calculateImpact,
            it.buildVerdict,
            it.createWish,
            it.observeGoal,
            it.addToGoal,
            it.spendingContext,
            it.cutoffSpending,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is AddWishEffect.OpenWish -> onOpenWish(effect.id)
                is AddWishEffect.OpenCalculator -> onOpenCalculator(effect.price)
                AddWishEffect.Close -> onClose()
            }
        }
    }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start the cooldown either way; reminders just won't show without permission.
        vm.onIntent(AddWishIntent.StartCooldown)
    }
    val startCooldown = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.canNotify(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.onIntent(AddWishIntent.StartCooldown)
        }
    }

    AddWishScreen(state, vm::onIntent, onClose, startCooldown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWishScreen(
    state: AddWishState,
    onIntent: (AddWishIntent) -> Unit,
    onClose: () -> Unit,
    onStartCooldown: () -> Unit,
) {
    BackHandler(enabled = state.step == AddStep.VERDICT) { onIntent(AddWishIntent.BackToForm) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (state.step == AddStep.FORM) stringResource(R.string.add_title_form) else stringResource(R.string.add_title_verdict, FriendVoice.name(res()))) },
                    navigationIcon = {
                        if (state.step == AddStep.FORM) {
                            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close)) }
                        } else {
                            IconButton(onClick = { onIntent(AddWishIntent.BackToForm) }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.add_edit_wish))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { (fadeIn() + slideInVertically { it / 6 }) togetherWith fadeOut() },
                label = "step",
                modifier = Modifier.padding(padding),
            ) { step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    when (step) {
                        AddStep.FORM -> WishForm(state, onIntent)
                        AddStep.VERDICT -> state.verdict?.let { VerdictContent(state, it, onIntent, onStartCooldown) }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        val celebrate = state.celebrateAmount
        if (celebrate != null) {
            ConfettiOverlay(trigger = 1)
            AlertDialog(
                onDismissRequest = { onIntent(AddWishIntent.CelebrationDone) },
                icon = { Text("🕊️", style = MaterialTheme.typography.displaySmall, modifier = Modifier.clearAndSetSemantics { }) },
                title = { Text(stringResource(R.string.kept_title, LocalMoney.current.format(celebrate))) },
                text = {
                    Text(
                        stringResource(R.string.kept_body_add),
                        textAlign = TextAlign.Center,
                    )
                },
                confirmButton = {
                    val goal = state.goal?.takeUnless { it.isReached }
                    if (goal != null) {
                        // One tap: the money you didn't spend moves you toward the goal.
                        Button(onClick = { onIntent(AddWishIntent.AddLetGoToGoal) }) {
                            Text(stringResource(R.string.goal_letgo_add, goal.emoji, goal.name))
                        }
                    } else {
                        Button(onClick = { onIntent(AddWishIntent.CelebrationDone) }) { Text(stringResource(R.string.kept_ok)) }
                    }
                },
                dismissButton = if (state.goal?.isReached == false) {
                    { TextButton(onClick = { onIntent(AddWishIntent.CelebrationDone) }) { Text(stringResource(R.string.goal_letgo_skip)) } }
                } else {
                    null
                },
            )
        }
        val reachedGoal = state.goal
        if (state.goalJustReached && reachedGoal != null) {
            GoalReachedDialog(reachedGoal.name, onDone = { onIntent(AddWishIntent.CelebrationDone) })
        }
    }

    if (state.showBuyNowConfirm) {
        val days = state.verdict?.recommendedDays ?: 0
        AlertDialog(
            onDismissRequest = { onIntent(AddWishIntent.DismissDialog) },
            icon = { Text(FriendVoice.AVATAR, style = MaterialTheme.typography.displaySmall) },
            title = { Text(stringResource(R.string.skip_title)) },
            text = {
                Text(
                    stringResource(R.string.skip_body, Fmt.days(res(), days)),
                )
            },
            confirmButton = {
                Button(onClick = onStartCooldownAfterDismiss(onIntent, onStartCooldown)) { Text(stringResource(R.string.skip_wait)) }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(AddWishIntent.BuyNowConfirmed) }) { Text(stringResource(R.string.skip_buy)) }
            },
        )
    }
}

private fun onStartCooldownAfterDismiss(
    onIntent: (AddWishIntent) -> Unit,
    onStartCooldown: () -> Unit,
): () -> Unit = {
    onIntent(AddWishIntent.DismissDialog)
    onStartCooldown()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WishForm(state: AddWishState, onIntent: (AddWishIntent) -> Unit) {
    val money = LocalMoney.current
    if (state.fromShare) {
        SharedBanner(state)
        Spacer(Modifier.height(16.dp))
    } else {
        PasteLinkRow(state, onIntent)
        Spacer(Modifier.height(16.dp))
    }
    Text(stringResource(R.string.add_question), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    // Shared with missing details (TikTok: link only, Lazada: no price) → cursor goes to the first gap.
    val nameFocus = remember { FocusRequester() }
    val priceFocus = remember { FocusRequester() }
    // "Need" picked without saying why → scroll the picker into view so the hint is seen.
    val kindBringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(state.needReasonError) {
        if (state.needReasonError) runCatching { kindBringIntoView.bringIntoView() }
    }
    val needsName = state.fromShare && !state.sharedNameFound
    val needsPrice = state.fromShare && !state.sharedPriceFound
    LaunchedEffect(needsName, needsPrice, state.lookingUp) {
        if (state.lookingUp) return@LaunchedEffect
        runCatching {
            when {
                needsName && state.name.isBlank() -> nameFocus.requestFocus()
                needsPrice && state.price.isBlank() -> priceFocus.requestFocus()
            }
        }
    }
    OutlinedTextField(
        value = state.name,
        onValueChange = { onIntent(AddWishIntent.NameChanged(it)) },
        label = { Text(stringResource(R.string.field_what)) },
        placeholder = { Text(stringResource(R.string.field_what_placeholder)) },
        isError = state.nameError,
        supportingText = if (state.nameError) ({ Text(stringResource(R.string.field_what_error)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(nameFocus),
    )
    Spacer(Modifier.height(8.dp))
    val parsed = Fmt.parseAmount(state.price)
    val hint = when {
        state.priceError -> stringResource(R.string.field_price_error)
        parsed != null && parsed > 0 && state.profile.hourlyRate > 0 ->
            stringResource(R.string.price_work_hint, Fmt.hours(res(), parsed / state.profile.hourlyRate))
        else -> null
    }
    MoneyField(
        modifier = Modifier.focusRequester(priceFocus),
        value = state.price,
        onValueChange = { onIntent(AddWishIntent.PriceChanged(it)) },
        label = stringResource(R.string.field_price),
        isError = state.priceError,
        supportingText = hint,
        imeAction = ImeAction.Next,
        textStyle = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(8.dp))
    MoneyField(
        value = state.deliveryFee,
        onValueChange = { onIntent(AddWishIntent.DeliveryFeeChanged(it)) },
        label = stringResource(R.string.field_delivery_fee),
        imeAction = ImeAction.Done,
    )

    FormLabel(stringResource(R.string.add_category))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Category.entries.forEach { c ->
            FilterChip(
                selected = state.category == c,
                onClick = { onIntent(AddWishIntent.CategorySelected(c)) },
                label = { Text("${c.emoji} ${stringResource(c.labelRes)}") },
            )
        }
    }

    FormLabel(stringResource(R.string.kind_title), stringResource(R.string.add_why_sub))
    NeedWantPicker(
        isNeed = state.isNeed,
        reasons = state.reasons,
        onKindChange = { onIntent(AddWishIntent.KindChanged(it)) },
        onReasonToggle = { onIntent(AddWishIntent.ReasonToggled(it)) },
        showError = state.needReasonError,
        modifier = Modifier.bringIntoViewRequester(kindBringIntoView),
    )

    FormLabel(stringResource(R.string.add_mood), stringResource(R.string.add_mood_sub))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mood.entries.forEach { m ->
            FilterChip(
                selected = m in state.moods,
                onClick = { onIntent(AddWishIntent.MoodToggled(m)) },
                label = { Text("${m.emoji} ${stringResource(m.labelRes)}") },
            )
        }
    }

    FormLabel(stringResource(R.string.add_desire))
    DesirePicker(selected = state.desire, onSelect = { onIntent(AddWishIntent.DesireChanged(it)) })

    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.note,
        onValueChange = { onIntent(AddWishIntent.NoteChanged(it)) },
        label = { Text(stringResource(R.string.field_note)) },
        placeholder = { Text(stringResource(R.string.field_note_placeholder)) },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onIntent(AddWishIntent.AskFriend) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(stringResource(R.string.add_ask, FriendVoice.name(res())), style = MaterialTheme.typography.titleMedium)
    }
    if (state.profile.monthlyIncome <= 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.add_income_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FormLabel(title: String, subtitle: String? = null) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun tierColor(tier: Tier): Color = when (tier) {
    Tier.TREAT, Tier.SMALL -> MaterialTheme.colorScheme.primary
    Tier.MEDIUM, Tier.LARGE -> MaterialTheme.colorScheme.tertiary
    Tier.BIG, Tier.HUGE, Tier.MASSIVE -> MaterialTheme.colorScheme.error
}

@Composable
private fun VerdictContent(
    state: AddWishState,
    verdict: Verdict,
    onIntent: (AddWishIntent) -> Unit,
    onStartCooldown: () -> Unit,
) {
    val money = LocalMoney.current
    val impact = verdict.impact

    // Headline number
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "${state.category.emoji} ${state.name} · ${money.format(impact.price)}",
                style = MaterialTheme.typography.titleMedium,
            )
            val deliveryFee = Fmt.parseAmount(state.deliveryFee) ?: 0.0
            if (deliveryFee > 0) {
                val itemPrice = Fmt.parseAmount(state.price) ?: 0.0
                Text(
                    stringResource(
                        R.string.verdict_true_cost,
                        money.format(impact.price),
                        money.format(itemPrice),
                        money.format(deliveryFee),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.profile.budgetShare(impact.price)?.let { share ->
                Text(
                    if (share > 1.0) {
                        stringResource(R.string.verdict_over_budget, money.format(state.profile.availableBudget))
                    } else {
                        stringResource(R.string.verdict_budget_share, Fmt.percent(share), money.format(state.profile.availableBudget))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (share > 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Fmt.incomeShare(impact.incomeRatio),
                    style = MaterialTheme.typography.displayMedium,
                    color = tierColor(impact.tier),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Fmt.incomeShareLabel(res(), impact.incomeRatio),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            ImpactMeter(impact.incomeRatio)
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("⏱️", Fmt.hours(res(), impact.workHours), stringResource(R.string.verdict_stat_hours), Modifier.weight(1f))
        StatTile("📅", Fmt.number(impact.workDays, if (impact.workDays < 10) 1 else 0), stringResource(R.string.verdict_stat_days), Modifier.weight(1f))
        StatTile(
            "🐷",
            impact.monthsToSave?.let { Fmt.months(res(), it) } ?: stringResource(impact.affordability.labelRes),
            stringResource(if (impact.monthsToSave != null) R.string.verdict_stat_saving else R.string.verdict_stat_afford),
            Modifier.weight(1f),
        )
    }

    // Reframe: the same money, pointed at the goal.
    state.goal?.takeUnless { it.isReached }?.let { goal ->
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (impact.price >= goal.remaining) {
                    stringResource(R.string.verdict_goal_finish, goal.emoji, goal.name)
                } else {
                    stringResource(
                        R.string.verdict_goal_share,
                        money.format(impact.price),
                        Fmt.percent(goal.shareOf(impact.price)),
                        goal.emoji,
                        goal.name,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    FriendBubble(
        headline = FriendVoice.headline(res(), verdict),
        message = FriendVoice.message(res(), verdict, state.name, state.category, LocalMoney.current),
        reaction = FriendVoice.reaction(impact.tier),
    )

    // Cooldown picker
    Spacer(Modifier.height(20.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.verdict_cooldown), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { onIntent(AddWishIntent.AdjustDays(-1)) },
                    enabled = state.chosenDays > 0,
                ) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_fewer_days)) }
                Text(
                    if (state.chosenDays == 0) stringResource(R.string.no_wait) else Fmt.days(res(), state.chosenDays),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                FilledTonalIconButton(onClick = { onIntent(AddWishIntent.AdjustDays(1)) }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_more_days))
                }
            }
            val sub = when {
                state.chosenDays == 0 -> stringResource(R.string.verdict_no_wait_sub)
                state.chosenDays < verdict.recommendedDays ->
                    stringResource(R.string.verdict_shorter_sub, Fmt.days(res(), verdict.recommendedDays), decideDate(state.chosenDays))
                else -> stringResource(R.string.verdict_decide_on, decideDate(state.chosenDays))
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)

            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in COOLDOWN_PRESETS) {
                    FilterChip(
                        selected = state.chosenDays == preset,
                        onClick = { onIntent(AddWishIntent.SetDays(preset)) },
                        label = { Text(Fmt.days(res(), preset)) },
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = if (state.chosenDays == 0) ({ onIntent(AddWishIntent.StartCooldown) }) else onStartCooldown,
        enabled = !state.isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            if (state.chosenDays == 0) stringResource(R.string.verdict_go_for_it) else stringResource(R.string.verdict_start, Fmt.days(res(), state.chosenDays)),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { onIntent(AddWishIntent.PlanPayment) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.Calculate, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.verdict_plan))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onIntent(AddWishIntent.LetGo) }, enabled = !state.isSaving) {
            Text(stringResource(R.string.verdict_let_go))
        }
        if (state.chosenDays > 0) {
            TextButton(onClick = { onIntent(AddWishIntent.BuyNowRequested) }, enabled = !state.isSaving) {
                Text(stringResource(R.string.verdict_buy_anyway), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Strategies
    Spacer(Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.verdict_tips_title), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            FriendVoice.tips(res(), verdict).forEach { tip ->
                Text("•  $tip", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

private fun decideDate(days: Int): String = Fmt.date(System.currentTimeMillis() + days * DAY_MS)

private val COOLDOWN_PRESETS = listOf(1, 3, 7, 30)

@Composable
private fun SharedBanner(state: AddWishState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                state.store?.let { stringResource(R.string.share_banner_title, it) }
                    ?: stringResource(R.string.share_banner_title_generic),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.share_banner_body), style = MaterialTheme.typography.bodySmall)
            if (state.lookingUp) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.store?.let { stringResource(R.string.share_looking_up, it) }
                            ?: stringResource(R.string.share_looking_up_generic),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (state.foundOnline && state.sharedNameFound && state.sharedPriceFound) {
                Text(
                    state.store?.let { stringResource(R.string.share_found_online, it) }
                        ?: stringResource(R.string.share_found_online_generic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (!state.sharedNameFound) {
                Text(
                    state.store?.let { stringResource(R.string.share_banner_no_name_store, it) }
                        ?: stringResource(R.string.share_banner_no_name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            } else if (!state.sharedPriceFound) {
                Text(
                    state.store?.let { stringResource(R.string.share_banner_no_price_store, it) }
                        ?: stringResource(R.string.share_banner_no_price),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (state.link != null) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.share_link_hidden), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Some shops (Shopee's "Share Links to Earn Commission" panel) only list partner apps.
 * "Copy Link" there + paste here does the same as Share → WishWait.
 * The clipboard is read only when the user taps the button.
 */
@Composable
private fun PasteLinkRow(state: AddWishState, onIntent: (AddWishIntent) -> Unit) {
    val context = LocalContext.current
    Column {
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val text = runCatching {
                    clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                }.getOrNull()
                onIntent(AddWishIntent.PasteLink(text))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.paste_link))
        }
        Text(
            stringResource(if (state.pasteFailed) R.string.paste_none else R.string.paste_link_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.pasteFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
