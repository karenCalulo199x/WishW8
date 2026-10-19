package com.karencalulo.wishwait.presentation.onboarding

import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.deviceCurrencyCode

data class OnboardingState(
    val page: Int = 0,
    val income: String = "",
    val hours: String = "160",
    val savings: String = "",
    val currencyCode: String = deviceCurrencyCode(),
    val strictness: Strictness = Strictness.BALANCED,
    val incomeError: Boolean = false,
    val paydays: List<Int> = emptyList(),
) {
    val isLastPage: Boolean get() = page == PAGE_COUNT - 1

    companion object {
        const val PAGE_COUNT = 4
        const val PAGE_MONEY = 2
    }
}

sealed interface OnboardingIntent {
    data object Next : OnboardingIntent
    data object Back : OnboardingIntent
    data class IncomeChanged(val value: String) : OnboardingIntent
    data class HoursChanged(val value: String) : OnboardingIntent
    data class SavingsChanged(val value: String) : OnboardingIntent
    data object UseSuggestedSavings : OnboardingIntent
    data class StrictnessSelected(val value: Strictness) : OnboardingIntent
    data class PaydaysSelected(val days: List<Int>) : OnboardingIntent
}

sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect
}
