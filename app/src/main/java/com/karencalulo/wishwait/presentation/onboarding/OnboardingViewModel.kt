package com.karencalulo.wishwait.presentation.onboarding

import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.usecase.GetProfileUseCase
import com.karencalulo.wishwait.domain.usecase.UpdateProfileUseCase
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val getProfile: GetProfileUseCase,
    private val updateProfile: UpdateProfileUseCase,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {

    init {
        viewModelScope.launch {
            val p = getProfile()
            setState {
                copy(
                    currencyCode = p.currencyCode,
                    income = if (p.monthlyIncome > 0) Fmt.plain(p.monthlyIncome) else income,
                    savings = if (p.monthlySavings > 0) Fmt.plain(p.monthlySavings) else savings,
                    strictness = p.strictness,
                )
            }
        }
    }

    override fun handleIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Next -> next()
            OnboardingIntent.Back -> setState { copy(page = (page - 1).coerceAtLeast(0)) }
            is OnboardingIntent.IncomeChanged -> setState { copy(income = Fmt.sanitizeAmount(intent.value), incomeError = false) }
            is OnboardingIntent.HoursChanged -> setState { copy(hours = intent.value.filter { it.isDigit() }.take(3)) }
            is OnboardingIntent.SavingsChanged -> setState { copy(savings = Fmt.sanitizeAmount(intent.value)) }
            OnboardingIntent.UseSuggestedSavings -> {
                val income = Fmt.parseAmount(currentState.income) ?: return
                setState { copy(savings = (income * 0.2).toLong().toString()) }
            }
            is OnboardingIntent.StrictnessSelected -> setState { copy(strictness = intent.value) }
            is OnboardingIntent.PaydaysSelected -> setState { copy(paydays = intent.days) }
        }
    }

    private fun next() {
        val s = currentState
        if (s.page == OnboardingState.PAGE_MONEY && (Fmt.parseAmount(s.income) ?: 0.0) <= 0.0) {
            setState { copy(incomeError = true) }
            return
        }
        if (!s.isLastPage) {
            setState { copy(page = page + 1) }
            return
        }
        viewModelScope.launch {
            updateProfile { p ->
                p.copy(
                    monthlyIncome = Fmt.parseAmount(s.income) ?: 0.0,
                    hoursPerMonth = s.hours.toIntOrNull()?.takeIf { it > 0 } ?: UserProfile.DEFAULT_HOURS_PER_MONTH,
                    monthlySavings = Fmt.parseAmount(s.savings) ?: 0.0,
                    strictness = s.strictness,
                    paydays = s.paydays,
                    currencyCode = s.currencyCode,
                    onboarded = true,
                )
            }
            sendEffect(OnboardingEffect.Finished)
        }
    }
}
