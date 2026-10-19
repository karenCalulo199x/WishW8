package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.Achievement
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.Stats
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus

/** Share of monthly income that counts as a first, early savings win (currency-neutral). */
private const val EARLY_SAVER_SHARE = 0.02
private const val WEEK_STREAK_DAYS = 7

class ComputeStatsUseCase(
    private val calculateWorth: CalculateWorthUseCase = CalculateWorthUseCase(),
) {

    operator fun invoke(wishes: List<Wish>, profile: UserProfile, goalsCompleted: Int = 0): Stats {
        val letGo = wishes.filter { it.status == WishStatus.LET_GO }
        val waiting = wishes.filter { it.status == WishStatus.WAITING }
        val mindful = wishes.count { it.boughtMindfully }
        val impulse = wishes.count { it.boughtEarly }
        val saved = letGo.sumOf { it.price }
        // Each wish uses the hourly rate from when it was added.
        val hoursSaved = letGo.sumOf { w ->
            val hourly = w.profileAtCreation(profile).hourlyRate
            if (hourly > 0) w.price / hourly else 0.0
        }

        val unlocked = buildSet {
            if (wishes.any { it.cooldownDays > 0 }) add(Achievement.FIRST_PAUSE)
            if (letGo.isNotEmpty()) add(Achievement.LET_IT_GO)
            if (letGo.size >= 5) add(Achievement.FREE_SPIRIT)
            if (mindful > 0) add(Achievement.MINDFUL_BUYER)
            if (wishes.any { it.isDecided && it.cooldownDays >= 30 && (it.decidedAt ?: 0L) >= it.cooldownEndsAt }) {
                add(Achievement.PATIENCE_PRO)
            }
            if (profile.monthlyIncome > 0 && saved >= profile.monthlyIncome) add(Achievement.MONTH_OF_FREEDOM)
            if (wishes.any { it.gladRating == true }) add(Achievement.NO_REGRETS)
            if (wishes.any { it.desireDrop >= 2 }) add(Achievement.FEELINGS_FADE)
            if (wishes.any { it.status == WishStatus.BOUGHT && it.usageLog.isNotEmpty() && calculateWorth(it, it.profileAtCreation(profile)).isWorthIt }) {
                add(Achievement.PAID_FOR_ITSELF)
            }
            if (wishes.any { it.payment?.isComplete == true }) add(Achievement.DEBT_FREE)
            if (goalsCompleted > 0) add(Achievement.GOAL_GETTER)
            if (profile.tipsGiven > 0) add(Achievement.OLLIE_FRIEND)
            if (profile.monthlyIncome > 0 && saved >= profile.monthlyIncome * EARLY_SAVER_SHARE) add(Achievement.EARLY_SAVER)
            if (letGo.size >= 10) add(Achievement.TEN_RECONSIDERED)
            if (hasWeekWithoutImpulseBuy(wishes)) add(Achievement.WEEK_STREAK)
        }

        return Stats(
            savedTotal = saved,
            letGoCount = letGo.size,
            mindfulBuys = mindful,
            impulseBuys = impulse,
            waitingCount = waiting.size,
            onHoldTotal = waiting.sumOf { it.price },
            hoursSaved = hoursSaved,
            lovedCount = wishes.count { it.status == WishStatus.BOUGHT && it.gladRating == true },
            regrettedCount = wishes.count { it.status == WishStatus.BOUGHT && it.gladRating == false },
            unlocked = unlocked,
        )
    }

    /** True once a decision was made at least [WEEK_STREAK_DAYS] days after the last impulse buy (or, if
     * there's never been one, after the account's first wish) — a week of clear-headed decisions. */
    private fun hasWeekWithoutImpulseBuy(wishes: List<Wish>): Boolean {
        val chronological = wishes.filter { it.decidedAt != null }.sortedBy { it.decidedAt }
        if (chronological.isEmpty()) return false
        val threshold = WEEK_STREAK_DAYS * DAY_MS
        var lastImpulseAt: Long? = wishes.minOfOrNull { it.createdAt }
        for (w in chronological) {
            val anchor = lastImpulseAt
            if (anchor != null && w.decidedAt!! - anchor >= threshold) return true
            if (w.boughtEarly) lastImpulseAt = w.decidedAt
        }
        return false
    }
}
