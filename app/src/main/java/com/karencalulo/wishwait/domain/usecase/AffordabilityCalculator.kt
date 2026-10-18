package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.CostPerUse
import com.karencalulo.wishwait.domain.model.InstallmentPlan
import com.karencalulo.wishwait.domain.model.OpportunityCost
import com.karencalulo.wishwait.domain.model.SavingsPlan
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/** The "calculator of possibility": different ways to look at paying for something. */
class AffordabilityCalculator {

    /** Save up first, pay cash later. Null when no monthly amount is set. */
    fun savingsPlan(price: Double, alreadySaved: Double, monthlyAmount: Double): SavingsPlan? {
        val remaining = (price - alreadySaved).coerceAtLeast(0.0)
        if (remaining == 0.0) return SavingsPlan(0, monthlyAmount, 0.0)
        if (monthlyAmount <= 0) return null
        return SavingsPlan(
            months = ceil(remaining / monthlyAmount).toInt(),
            monthlyAmount = monthlyAmount,
            remaining = remaining,
        )
    }

    /** Amortized installment / loan. [annualRatePercent] 0 means a true 0% plan. */
    fun installment(
        price: Double,
        months: Int,
        annualRatePercent: Double,
        monthlyIncome: Double,
    ): InstallmentPlan? {
        if (price <= 0 || months <= 0) return null
        val r = annualRatePercent.coerceAtLeast(0.0) / 100.0 / 12.0
        val payment = if (r == 0.0) {
            price / months
        } else {
            price * r / (1 - (1 + r).pow(-months.toDouble()))
        }
        val total = payment * months
        return InstallmentPlan(
            months = months,
            monthlyPayment = payment,
            totalPaid = total,
            interestPaid = total - price,
            shareOfIncome = if (monthlyIncome > 0) payment / monthlyIncome else 0.0,
        )
    }

    /** What each use really costs over the item's useful life. */
    fun costPerUse(price: Double, usesPerWeek: Double, yearsOfUse: Double): CostPerUse? {
        val uses = (usesPerWeek * 52.0 * yearsOfUse).roundToInt()
        if (price <= 0 || uses <= 0) return null
        return CostPerUse(perUse = price / uses, totalUses = uses)
    }

    /** What the same money could grow to if saved/invested instead. */
    fun opportunityCost(price: Double, annualReturnPercent: Double, years: Int): OpportunityCost? {
        if (price <= 0 || years <= 0) return null
        val fv = price * (1 + annualReturnPercent.coerceAtLeast(0.0) / 100.0).pow(years.toDouble())
        return OpportunityCost(futureValue = fv, growth = fv - price, years = years)
    }
}
