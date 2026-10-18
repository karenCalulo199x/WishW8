package com.karencalulo.wishwait.presentation.calculator

import com.karencalulo.wishwait.R
import androidx.annotation.StringRes
import com.karencalulo.wishwait.domain.model.CostPerUse
import com.karencalulo.wishwait.domain.model.InstallmentPlan
import com.karencalulo.wishwait.domain.model.OpportunityCost
import com.karencalulo.wishwait.domain.model.PriceImpact
import com.karencalulo.wishwait.domain.model.SavingsPlan
import com.karencalulo.wishwait.domain.model.UserProfile

enum class CalcMode(val emoji: String, @StringRes val labelRes: Int) {
    SAVE_UP("🐷", R.string.calc_mode_save),
    INSTALLMENT("💳", R.string.calc_mode_installment),
    PER_USE("🔁", R.string.calc_mode_per_use),
    INVEST("📈", R.string.calc_mode_invest),
}

data class CalculatorState(
    val price: String = "",
    val profile: UserProfile = UserProfile(),
    val mode: CalcMode = CalcMode.SAVE_UP,
    // Save up
    val monthlySave: String = "",
    val alreadySaved: String = "",
    // Installment
    val installmentMonths: Int = 12,
    val annualRate: String = "0",
    // Cost per use
    val usesPerWeek: Float = 3f,
    val yearsOfUse: Float = 2f,
    // Invest
    val investRate: String = "6",
    val investYears: Float = 5f,
    // Results
    val impact: PriceImpact? = null,
    val savingsPlan: SavingsPlan? = null,
    val installment: InstallmentPlan? = null,
    val costPerUse: CostPerUse? = null,
    val opportunity: OpportunityCost? = null,
)

sealed interface CalculatorIntent {
    data class PriceChanged(val value: String) : CalculatorIntent
    data class ModeSelected(val value: CalcMode) : CalculatorIntent
    data class MonthlySaveChanged(val value: String) : CalculatorIntent
    data class AlreadySavedChanged(val value: String) : CalculatorIntent
    data class MonthsSelected(val value: Int) : CalculatorIntent
    data class RateChanged(val value: String) : CalculatorIntent
    data class UsesChanged(val value: Float) : CalculatorIntent
    data class YearsOfUseChanged(val value: Float) : CalculatorIntent
    data class InvestRateChanged(val value: String) : CalculatorIntent
    data class InvestYearsChanged(val value: Float) : CalculatorIntent
}

sealed interface CalculatorEffect
