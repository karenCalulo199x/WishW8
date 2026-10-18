package com.karencalulo.wishwait.presentation.calculator

import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.domain.usecase.AffordabilityCalculator
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveProfileUseCase
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

class CalculatorViewModel(
    initialPrice: Double?,
    observeProfile: ObserveProfileUseCase,
    private val calculateImpact: CalculatePriceImpactUseCase,
    private val calculator: AffordabilityCalculator,
) : MviViewModel<CalculatorState, CalculatorIntent, CalculatorEffect>(
    CalculatorState(price = initialPrice?.let { Fmt.plain(it) } ?: ""),
) {

    private var profileLoaded = false

    init {
        observeProfile().onEach { p ->
            setState {
                val withProfile = copy(
                    profile = p,
                    monthlySave = if (!profileLoaded && monthlySave.isBlank()) Fmt.plain(p.monthlySavings) else monthlySave,
                )
                withProfile.recompute()
            }
            profileLoaded = true
        }.launchIn(viewModelScope)
    }

    override fun handleIntent(intent: CalculatorIntent) {
        setState {
            when (intent) {
                is CalculatorIntent.PriceChanged -> copy(price = Fmt.sanitizeAmount(intent.value))
                is CalculatorIntent.ModeSelected -> copy(mode = intent.value)
                is CalculatorIntent.MonthlySaveChanged -> copy(monthlySave = Fmt.sanitizeAmount(intent.value))
                is CalculatorIntent.AlreadySavedChanged -> copy(alreadySaved = Fmt.sanitizeAmount(intent.value))
                is CalculatorIntent.MonthsSelected -> copy(installmentMonths = intent.value)
                is CalculatorIntent.RateChanged -> copy(annualRate = Fmt.sanitizeAmount(intent.value).take(5))
                is CalculatorIntent.UsesChanged -> copy(usesPerWeek = intent.value)
                is CalculatorIntent.YearsOfUseChanged -> copy(yearsOfUse = intent.value)
                is CalculatorIntent.InvestRateChanged -> copy(investRate = Fmt.sanitizeAmount(intent.value).take(5))
                is CalculatorIntent.InvestYearsChanged -> copy(investYears = intent.value)
            }.recompute()
        }
    }

    private fun CalculatorState.recompute(): CalculatorState {
        val p = Fmt.parseAmount(price)?.takeIf { it > 0 }
            ?: return copy(impact = null, savingsPlan = null, installment = null, costPerUse = null, opportunity = null)
        return copy(
            impact = calculateImpact(p, profile),
            savingsPlan = calculator.savingsPlan(
                price = p,
                alreadySaved = Fmt.parseAmount(alreadySaved) ?: 0.0,
                monthlyAmount = Fmt.parseAmount(monthlySave) ?: 0.0,
            ),
            installment = calculator.installment(
                price = p,
                months = installmentMonths,
                annualRatePercent = Fmt.parseAmount(annualRate) ?: 0.0,
                monthlyIncome = profile.monthlyIncome,
            ),
            costPerUse = calculator.costPerUse(p, usesPerWeek.toDouble(), yearsOfUse.toDouble()),
            opportunity = calculator.opportunityCost(p, Fmt.parseAmount(investRate) ?: 0.0, investYears.roundToInt()),
        )
    }
}
