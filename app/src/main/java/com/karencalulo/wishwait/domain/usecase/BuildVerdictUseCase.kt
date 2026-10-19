package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.CutoffSpending
import com.karencalulo.wishwait.domain.model.SpendingContext
import com.karencalulo.wishwait.domain.model.Mood
import com.karencalulo.wishwait.domain.model.PriceImpact
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.RegretHistory
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.Tier
import com.karencalulo.wishwait.domain.model.Verdict
import com.karencalulo.wishwait.domain.model.isNeedPurchase
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Picks a cooling-off period (delayed gratification) from the price weight,
 * the reasons, the current mood and the user's strictness.
 *
 * - Base days grow with the price tier (1 day for a treat, 30 days for months of salary).
 * - Real needs halve the wait: the goal is to avoid impulse buys, not necessities.
 * - Risky moods, or a "really want" / "must have" desire rating, add 50% (min +1 day):
 *   feelings fade, wants that survive them are real.
 * - Regretted past buys in the same category add 3–7 days: the app learns from you.
 * - Wants only (never real needs, never a 0-day treat): sale day +2 (+3 for 9.9–12.12),
 *   payday window +2, and +3 if this would push wants past the line for this cutoff.
 * - Strictness scales the base wait and sets the cutoff line; the extras above are flat.
 */
class BuildVerdictUseCase {

    operator fun invoke(
        impact: PriceImpact,
        reasons: Set<Reason>,
        moods: Set<Mood>,
        strictness: Strictness,
        history: RegretHistory? = null,
        timing: SpendingContext = SpendingContext.NONE,
        cutoff: CutoffSpending? = null,
        desire: Int = 3,
    ): Verdict {
        val isNeed = isNeedPurchase(reasons)
        val riskyMoods = moods.filter { it.isRisky }
        val highDesire = riskyMoods.isEmpty() && desire >= HIGH_DESIRE_THRESHOLD
        val boostTriggered = riskyMoods.isNotEmpty() || highDesire

        var days = impact.tier.baseDays * strictness.multiplier
        if (isNeed) days /= 2.0

        var extraForMood = 0
        if (boostTriggered) {
            val boosted = ceil(days * 1.5).coerceAtLeast(days + 1)
            extraForMood = (boosted - days).roundToInt()
            days = boosted
        }

        val regretBonus = history?.bonusDays ?: 0
        var finalDays = days.roundToInt() + regretBonus
        // A calm, needed, tiny purchase needs no wait at all (unless this category has burned you before).
        if (impact.tier == Tier.TREAT && !boostTriggered && regretBonus == 0 &&
            (isNeed || strictness == Strictness.CHILL)
        ) {
            finalDays = 0
        } else {
            finalDays = finalDays.coerceAtLeast(1)
        }

        // Extras are for wants: a real need shouldn't wait longer because of a sale or payday.
        val wantsExtras = finalDays > 0 && !isNeed

        // Payday & sale days: when money feels plentiful and apps push hardest.
        var timingExtra = 0
        if (wantsExtras) {
            timing.saleToday?.let { timingExtra += if (it.isMega) MEGA_SALE_DAYS else SALE_DAYS }
            if (timing.inPaydayWindow) timingExtra += PAYDAY_DAYS
            finalDays += timingExtra
        }

        // Wants this cutoff: lots of "small" buys in one pay period add up.
        val cutoffForWants = cutoff?.takeIf { it.hasPay && !isNeed }
        val overLine = cutoffForWants?.isOverWith(impact.price) == true
        val cutoffExtra = if (wantsExtras && overLine) CUTOFF_DAYS else 0
        finalDays += cutoffExtra

        return Verdict(
            impact = impact,
            recommendedDays = finalDays.coerceAtMost(MAX_DAYS),
            isNeed = isNeed,
            riskyMoods = riskyMoods,
            highDesire = highDesire,
            onSale = Reason.ON_SALE in reasons,
            socialPressure = Reason.EVERYONE_HAS_IT in reasons || Reason.SAW_AN_AD in reasons,
            extraDaysForMood = extraForMood,
            history = history,
            extraDaysForTiming = timingExtra,
            paydayWindow = timing.inPaydayWindow && timingExtra > 0,
            saleToday = timing.saleToday?.takeIf { timingExtra > 0 },
            nextSale = timing.nextSale,
            cutoffSpentBefore = cutoffForWants?.spent,
            cutoffShareWith = cutoffForWants?.shareWith(impact.price),
            cutoffLineShare = cutoffForWants?.lineShare,
            overCutoffLine = overLine,
            extraDaysForCutoff = cutoffExtra,
            cutoffByMonth = cutoffForWants?.byMonth == true,
        )
    }

    companion object {
        const val MAX_DAYS = 90
        const val HIGH_DESIRE_THRESHOLD = 4
        const val PAYDAY_DAYS = 2
        const val SALE_DAYS = 2
        const val MEGA_SALE_DAYS = 3
        const val CUTOFF_DAYS = 3
    }
}
