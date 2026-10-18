package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.Affordability
import com.karencalulo.wishwait.domain.model.PriceImpact
import com.karencalulo.wishwait.domain.model.Tier
import com.karencalulo.wishwait.domain.model.UserProfile

/** Turns a price into "what it really costs you": salary share, work hours, months of saving. */
class CalculatePriceImpactUseCase {

    operator fun invoke(price: Double, profile: UserProfile): PriceImpact {
        val safePrice = price.coerceAtLeast(0.0)
        val income = profile.monthlyIncome.coerceAtLeast(1.0)
        val ratio = safePrice / income
        val hourly = profile.hourlyRate
        val workHours = if (hourly > 0) safePrice / hourly else 0.0
        val monthsToSave = if (profile.monthlySavings > 0) safePrice / profile.monthlySavings else null

        return PriceImpact(
            price = safePrice,
            incomeRatio = ratio,
            workHours = workHours,
            workDays = workHours / UserProfile.HOURS_PER_WORKDAY,
            monthsToSave = monthsToSave,
            tier = Tier.fromRatio(ratio),
            affordability = affordability(safePrice, ratio, profile.monthlySavings),
        )
    }

    private fun affordability(price: Double, ratio: Double, monthlySavings: Double): Affordability =
        if (monthlySavings > 0) {
            when {
                price <= monthlySavings * 0.25 -> Affordability.EASY
                price <= monthlySavings -> Affordability.OK
                price <= monthlySavings * 6 -> Affordability.STRETCH
                else -> Affordability.NOT_NOW
            }
        } else {
            when {
                ratio < 0.05 -> Affordability.EASY
                ratio < 0.20 -> Affordability.OK
                ratio < 1.0 -> Affordability.STRETCH
                else -> Affordability.NOT_NOW
            }
        }
}
