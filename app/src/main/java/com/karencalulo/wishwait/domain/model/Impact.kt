package com.karencalulo.wishwait.domain.model

/**
 * How heavy a purchase is relative to one month of income.
 * [upperRatio] is exclusive; [baseDays] is the cooldown at "Balanced" strictness.
 */
enum class Tier(val upperRatio: Double, val baseDays: Int) {
    TREAT(0.02, 1),
    SMALL(0.10, 3),
    MEDIUM(0.25, 7),
    LARGE(0.50, 14),
    BIG(1.0, 21),
    HUGE(3.0, 30),
    MASSIVE(Double.POSITIVE_INFINITY, 30);

    companion object {
        fun fromRatio(ratio: Double): Tier =
            entries.firstOrNull { ratio < it.upperRatio } ?: MASSIVE
    }
}

enum class Affordability { EASY, OK, STRETCH, NOT_NOW }

data class PriceImpact(
    val price: Double,
    /** price / monthly income. 5.0 means "five months of salary". */
    val incomeRatio: Double,
    val workHours: Double,
    val workDays: Double,
    /** Months of saving at the user's saving pace, null if no savings pace was set. */
    val monthsToSave: Double?,
    val tier: Tier,
    val affordability: Affordability,
)

/** What past purchases in the same category taught us. */
data class RegretHistory(
    val bought: Int,
    val loved: Int,
    val regretted: Int,
    val bonusDays: Int,
)

/** After-purchase "is it paying for itself yet?" tracker. */
data class WorthProgress(
    val uses: Int,
    val costBasis: Double,
    val costPerUse: Double,
    val targetPerUse: Double,
    val targetUses: Int,
    val progress: Float,
    val isWorthIt: Boolean,
    val usesLeft: Int,
)

data class Verdict(
    val impact: PriceImpact,
    val recommendedDays: Int,
    val isNeed: Boolean,
    val riskyMoods: List<Mood>,
    val onSale: Boolean,
    val socialPressure: Boolean,
    val extraDaysForMood: Int,
    val history: RegretHistory? = null,
    /** Extra days because it's right after payday or a sale day. */
    val extraDaysForTiming: Int = 0,
    val paydayWindow: Boolean = false,
    val saleToday: SaleEvent? = null,
    val nextSale: SaleEvent? = null,
    /** Wants already bought this cutoff (null for needs or when income isn't set). */
    val cutoffSpentBefore: Double? = null,
    /** Wants share of this cutoff's pay including this item. */
    val cutoffShareWith: Double? = null,
    val cutoffLineShare: Double? = null,
    val overCutoffLine: Boolean = false,
    val extraDaysForCutoff: Int = 0,
    /** No payday set: "this month" instead of "this cutoff". */
    val cutoffByMonth: Boolean = false,
)

data class SavingsPlan(
    val months: Int,
    val monthlyAmount: Double,
    val remaining: Double,
)

data class InstallmentPlan(
    val months: Int,
    val monthlyPayment: Double,
    val totalPaid: Double,
    val interestPaid: Double,
    /** Monthly payment / monthly income. Above ~0.2 is a warning sign. */
    val shareOfIncome: Double,
)

data class CostPerUse(
    val perUse: Double,
    val totalUses: Int,
)

data class OpportunityCost(
    val futureValue: Double,
    val growth: Double,
    val years: Int,
)
