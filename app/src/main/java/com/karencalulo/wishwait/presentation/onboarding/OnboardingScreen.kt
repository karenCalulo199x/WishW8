package com.karencalulo.wishwait.presentation.onboarding

import com.karencalulo.wishwait.presentation.components.PaydayChips
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import com.karencalulo.wishwait.presentation.common.descriptionRes
import com.karencalulo.wishwait.presentation.common.labelRes
import com.karencalulo.wishwait.presentation.common.emoji
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.presentation.common.FriendVoice
import com.karencalulo.wishwait.presentation.common.MoneyFormatter
import com.karencalulo.wishwait.presentation.common.appViewModel
import com.karencalulo.wishwait.presentation.components.AmountField
import com.karencalulo.wishwait.presentation.components.EmojiAvatar

@Composable
fun OnboardingRoute(onFinished: () -> Unit) {
    val vm = appViewModel { OnboardingViewModel(it.getProfile, it.updateProfile) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.Finished -> onFinished()
            }
        }
    }
    OnboardingScreen(state, vm::onIntent)
}

@Composable
fun OnboardingScreen(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    BackHandler(enabled = state.page > 0) { onIntent(OnboardingIntent.Back) }
    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.page > 0) {
                    TextButton(onClick = { onIntent(OnboardingIntent.Back) }) { Text(stringResource(R.string.action_back)) }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onIntent(OnboardingIntent.Next) }) {
                    Text(stringResource(if (state.isLastPage) R.string.onb_lets_go else R.string.action_next))
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (state.isLastPage) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageDots(state.page, OnboardingState.PAGE_COUNT, Modifier.padding(24.dp))
            AnimatedContent(
                targetState = state.page,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                label = "onboarding",
                modifier = Modifier.weight(1f),
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> HowItWorksPage()
                        2 -> MoneyPage(state, onIntent)
                        else -> StylePage(state, onIntent)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
            )
        }
    }
}

@Composable
private fun ColumnScope.WelcomePage() {
    Spacer(Modifier.height(32.dp))
    EmojiAvatar(
        FriendVoice.AVATAR,
        size = 120.dp,
        container = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(Modifier.height(32.dp))
    Text(
        stringResource(R.string.onb_hi, FriendVoice.name(res())),
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.onb_pitch),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.onb_privacy_line),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ColumnScope.HowItWorksPage() {
    Text(stringResource(R.string.onb_how_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(24.dp))
    Step("🔍", stringResource(R.string.onb_step1_title), stringResource(R.string.onb_step1_body))
    Step("⏳", stringResource(R.string.onb_step2_title), stringResource(R.string.onb_step2_body))
    Step("💭", stringResource(R.string.onb_step3_title), stringResource(R.string.onb_step3_body))
    Step("🏆", stringResource(R.string.onb_step4_title), stringResource(R.string.onb_step4_body))
}

@Composable
private fun Step(emoji: String, title: String, body: String) {
    Row(Modifier.padding(vertical = 10.dp)) {
        EmojiAvatar(emoji, size = 48.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ColumnScope.MoneyPage(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    val symbol = remember(state.currencyCode) { MoneyFormatter(state.currencyCode).symbol }
    Text(stringResource(R.string.onb_money_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onb_money_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    AmountField(
        value = state.income,
        onValueChange = { onIntent(OnboardingIntent.IncomeChanged(it)) },
        label = stringResource(R.string.field_income),
        prefix = symbol,
        isError = state.incomeError,
        supportingText = stringResource(if (state.incomeError) R.string.field_income_error else R.string.field_income_hint),
    )
    Spacer(Modifier.height(12.dp))
    AmountField(
        value = state.hours,
        onValueChange = { onIntent(OnboardingIntent.HoursChanged(it)) },
        label = stringResource(R.string.field_hours),
        suffix = stringResource(R.string.field_hours_suffix),
        keyboardType = KeyboardType.Number,
        supportingText = stringResource(R.string.field_hours_hint),
    )
    Spacer(Modifier.height(12.dp))
    AmountField(
        value = state.savings,
        onValueChange = { onIntent(OnboardingIntent.SavingsChanged(it)) },
        label = stringResource(R.string.field_savings_optional),
        prefix = symbol,
        imeAction = ImeAction.Done,
        supportingText = stringResource(R.string.field_savings_hint),
    )
    AssistChip(
        onClick = { onIntent(OnboardingIntent.UseSuggestedSavings) },
        label = { Text(stringResource(R.string.onb_use_20)) },
    )
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.payday_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.payday_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    PaydayChips(selected = state.paydays, onSelect = { onIntent(OnboardingIntent.PaydaysSelected(it)) })
}

@Composable
private fun ColumnScope.StylePage(state: OnboardingState, onIntent: (OnboardingIntent) -> Unit) {
    Text(stringResource(R.string.onb_style_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onb_style_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Strictness.entries.forEach { option ->
        val emoji = option.emoji
        OutlinedCard(
            onClick = { onIntent(OnboardingIntent.StrictnessSelected(option)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .semantics {
                    role = Role.RadioButton
                    selected = state.strictness == option
                },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(option.labelRes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(option.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RadioButton(selected = state.strictness == option, onClick = null)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onb_style_example),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
