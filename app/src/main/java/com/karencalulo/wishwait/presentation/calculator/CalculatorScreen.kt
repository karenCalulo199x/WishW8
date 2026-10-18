package com.karencalulo.wishwait.presentation.calculator

import com.karencalulo.wishwait.presentation.common.labelRes
import com.karencalulo.wishwait.presentation.common.res
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.presentation.addwish.tierColor
import com.karencalulo.wishwait.domain.model.CutoffCalculator
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.FriendVoice
import com.karencalulo.wishwait.presentation.common.LocalMoney
import com.karencalulo.wishwait.presentation.common.appViewModel
import com.karencalulo.wishwait.presentation.components.AmountField
import com.karencalulo.wishwait.presentation.components.ImpactMeter
import com.karencalulo.wishwait.presentation.components.MoneyField
import com.karencalulo.wishwait.presentation.components.StatTile
import kotlin.math.roundToInt

@Composable
fun CalculatorRoute(initialPrice: Double?) {
    val vm = appViewModel(key = "calc_${initialPrice ?: "none"}") {
        CalculatorViewModel(initialPrice, it.observeProfile, it.calculateImpact, it.calculator)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    CalculatorScreen(state, vm::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(state: CalculatorState, onIntent: (CalculatorIntent) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.calc_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoneyField(
                value = state.price,
                onValueChange = { onIntent(CalculatorIntent.PriceChanged(it)) },
                label = stringResource(R.string.calc_price),
                imeAction = ImeAction.Done,
                textStyle = MaterialTheme.typography.headlineSmall,
            )

            val impact = state.impact
            if (impact == null) {
                Text(
                    stringResource(R.string.calc_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Text(
                                Fmt.incomeShare(impact.incomeRatio),
                                style = MaterialTheme.typography.headlineLarge,
                                color = tierColor(impact.tier),
                            )
                            Spacer(Modifier.padding(4.dp))
                            Column {
                                Text(Fmt.incomeShareLabel(res(), impact.incomeRatio), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.calc_hours_afford, Fmt.hours(res(), impact.workHours), stringResource(impact.affordability.labelRes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ImpactMeter(impact.incomeRatio)
                    }
                }

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalcMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = { onIntent(CalculatorIntent.ModeSelected(mode)) },
                            label = { Text("${mode.emoji} ${stringResource(mode.labelRes)}") },
                        )
                    }
                }

                AnimatedContent(
                    targetState = state.mode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "mode",
                ) { mode ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (mode) {
                            CalcMode.SAVE_UP -> SaveUpPanel(state, onIntent)
                            CalcMode.INSTALLMENT -> InstallmentPanel(state, onIntent)
                            CalcMode.PER_USE -> PerUsePanel(state, onIntent)
                            CalcMode.INVEST -> InvestPanel(state, onIntent)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.calc_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResultCard(
    big: String,
    caption: String,
    detail: String? = null,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(caption, style = MaterialTheme.typography.labelLarge)
            Text(big, style = MaterialTheme.typography.headlineMedium)
            if (detail != null) {
                Spacer(Modifier.height(6.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SaveUpPanel(state: CalculatorState, onIntent: (CalculatorIntent) -> Unit) {
    val money = LocalMoney.current
    MoneyField(
        value = state.monthlySave,
        onValueChange = { onIntent(CalculatorIntent.MonthlySaveChanged(it)) },
        label = stringResource(R.string.calc_save_monthly),
    )
    MoneyField(
        value = state.alreadySaved,
        onValueChange = { onIntent(CalculatorIntent.AlreadySavedChanged(it)) },
        label = stringResource(R.string.calc_save_already),
        imeAction = ImeAction.Done,
    )
    val plan = state.savingsPlan
    when {
        plan == null -> Text(
            stringResource(R.string.calc_save_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        plan.months == 0 -> ResultCard(
            stringResource(R.string.calc_save_now_big),
            stringResource(R.string.calc_save_now_caption),
            stringResource(R.string.calc_save_now_detail),
        )
        else -> {
            val date = System.currentTimeMillis() + plan.months * 30L * DAY_MS
            ResultCard(
                big = Fmt.monthsCount(res(), plan.months),
                caption = stringResource(R.string.calc_save_by, Fmt.date(date)),
                detail = stringResource(R.string.calc_save_detail, money.format(plan.monthlyAmount), money.format(plan.remaining)),
            )
        }
    }
}

@Composable
private fun InstallmentPanel(state: CalculatorState, onIntent: (CalculatorIntent) -> Unit) {
    val money = LocalMoney.current
    val r = res()
    Text(stringResource(R.string.calc_months), style = MaterialTheme.typography.titleSmall)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(3, 6, 12, 18, 24, 36).forEach { m ->
            FilterChip(
                selected = state.installmentMonths == m,
                onClick = { onIntent(CalculatorIntent.MonthsSelected(m)) },
                label = { Text("$m") },
            )
        }
    }
    AmountField(
        value = state.annualRate,
        onValueChange = { onIntent(CalculatorIntent.RateChanged(it)) },
        label = stringResource(R.string.pay_interest),
        suffix = "%",
        supportingText = stringResource(R.string.calc_interest_hint),
        imeAction = ImeAction.Done,
    )
    val plan = state.installment ?: return
    // Same line Ollie uses everywhere: each payment lands in one cutoff and counts toward its wants line.
    val cutoffPay = CutoffCalculator.cutoffPay(state.profile)
    val lineShare = state.profile.strictness.wantsShare
    val heavy = cutoffPay > 0 && plan.monthlyPayment > cutoffPay * lineShare + 0.005
    val splitPay = state.profile.paydays.distinct().size >= 2
    ResultCard(
        big = stringResource(R.string.calc_per_month_big, money.format(plan.monthlyPayment)),
        caption = stringResource(R.string.calc_for_months, Fmt.monthsCount(res(), plan.months)),
        detail = buildString {
            append(r.getString(R.string.calc_total_paid, money.format(plan.totalPaid)))
            if (plan.interestPaid > 0.5) append(r.getString(R.string.calc_interest_part, money.format(plan.interestPaid)))
            if (state.profile.monthlyIncome > 0) {
                append(r.getString(R.string.calc_share_income, Fmt.percent(plan.shareOfIncome)))
                if (splitPay && cutoffPay > 0) {
                    append(r.getString(R.string.calc_share_cutoff, Fmt.percent(plan.monthlyPayment / cutoffPay)))
                }
            }
        },
        container = if (heavy) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        content = if (heavy) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    )
    if (heavy) {
        Text(
            stringResource(
                if (state.profile.paydays.isEmpty()) R.string.calc_line_rule_month else R.string.calc_line_rule,
                Fmt.percent(lineShare),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PerUsePanel(state: CalculatorState, onIntent: (CalculatorIntent) -> Unit) {
    val money = LocalMoney.current
    LabeledSlider(
        label = stringResource(R.string.calc_uses_week),
        valueText = Fmt.number(state.usesPerWeek.toDouble(), if (state.usesPerWeek < 1) 1 else 0),
        value = state.usesPerWeek,
        range = 0.25f..14f,
        onChange = { onIntent(CalculatorIntent.UsesChanged((it * 4).roundToInt() / 4f)) },
    )
    LabeledSlider(
        label = stringResource(R.string.calc_years_use),
        valueText = Fmt.number(state.yearsOfUse.toDouble(), 1),
        value = state.yearsOfUse,
        range = 0.5f..10f,
        onChange = { onIntent(CalculatorIntent.YearsOfUseChanged((it * 2).roundToInt() / 2f)) },
    )
    val cpu = state.costPerUse ?: return
    ResultCard(
        big = stringResource(R.string.calc_per_use_big, money.format(cpu.perUse)),
        caption = stringResource(R.string.calc_across_uses, Fmt.number(cpu.totalUses.toDouble())),
        detail = if (cpu.perUse < (state.profile.hourlyRate.takeIf { it > 0 } ?: Double.MAX_VALUE) / 4) {
            stringResource(R.string.calc_per_use_good)
        } else {
            stringResource(R.string.calc_per_use_bad)
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("☕", money.format(cpu.perUse * 4.33 * state.usesPerWeek), stringResource(R.string.calc_per_month_eff), Modifier.weight(1f))
        StatTile("📆", Fmt.number(cpu.totalUses.toDouble()), stringResource(R.string.calc_total_uses), Modifier.weight(1f))
    }
}

@Composable
private fun InvestPanel(state: CalculatorState, onIntent: (CalculatorIntent) -> Unit) {
    val money = LocalMoney.current
    AmountField(
        value = state.investRate,
        onValueChange = { onIntent(CalculatorIntent.InvestRateChanged(it)) },
        label = stringResource(R.string.calc_return),
        suffix = "%",
        supportingText = stringResource(R.string.calc_return_hint),
        imeAction = ImeAction.Done,
    )
    LabeledSlider(
        label = stringResource(R.string.calc_years),
        valueText = "${state.investYears.roundToInt()}",
        value = state.investYears,
        range = 1f..30f,
        onChange = { onIntent(CalculatorIntent.InvestYearsChanged(it.roundToInt().toFloat())) },
    )
    val opp = state.opportunity ?: return
    ResultCard(
        big = money.format(opp.futureValue),
        caption = stringResource(R.string.calc_invest_caption, opp.years),
        detail = stringResource(R.string.calc_invest_detail, money.format(opp.growth)),
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
