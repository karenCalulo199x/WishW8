package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.Achievement
import com.karencalulo.wishwait.domain.model.Stats
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus

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
}
