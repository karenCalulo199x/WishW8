package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow

class ObserveProfileUseCase(private val repo: ProfileRepository) {
    operator fun invoke(): Flow<UserProfile> = repo.profile
}

class GetProfileUseCase(private val repo: ProfileRepository) {
    suspend operator fun invoke(): UserProfile = repo.current()
}

class UpdateProfileUseCase(private val repo: ProfileRepository) {
    suspend operator fun invoke(transform: (UserProfile) -> UserProfile) = repo.update(transform)
}

/**
 * Switching currency either converts every amount with an exchange rate
 * (moving countries) or just relabels them (the default currency was wrong).
 */
class ChangeCurrencyUseCase(
    private val wishes: com.karencalulo.wishwait.domain.repository.WishRepository,
    private val profiles: ProfileRepository,
    private val goals: com.karencalulo.wishwait.domain.repository.GoalRepository? = null,
) {
    suspend operator fun invoke(newCode: String, rate: Double?) {
        if (rate != null && rate > 0) {
            wishes.getWishes().forEach { w ->
                wishes.upsert(
                    w.copy(
                        price = w.price * rate,
                        worthTargetPerUse = w.worthTargetPerUse?.times(rate),
                        incomeAtCreation = w.incomeAtCreation?.times(rate),
                        payment = w.payment?.let { it.copy(monthlyPayment = it.monthlyPayment * rate) },
                    ),
                )
            }
            // The savings goal is money too: convert it with everything else.
            goals?.current()?.let { g ->
                goals.save(
                    g.copy(
                        target = g.target * rate,
                        deposits = g.deposits.map { d -> d.copy(amount = d.amount * rate) },
                    ),
                )
            }
            profiles.update {
                it.copy(
                    currencyCode = newCode,
                    monthlyIncome = it.monthlyIncome * rate,
                    monthlySavings = it.monthlySavings * rate,
                )
            }
        } else {
            profiles.update { it.copy(currencyCode = newCode) }
        }
    }
}

/** Payday window + today's/next double-date sale, for Ollie and the Home banner. */
class GetSpendingContextUseCase(private val clock: com.karencalulo.wishwait.domain.repository.Clock) {
    operator fun invoke(profile: UserProfile): com.karencalulo.wishwait.domain.model.SpendingContext =
        com.karencalulo.wishwait.domain.model.SpendingCalendar.context(clock.now(), profile.paydays, paydaySales = profile.paydaySales)
}

/**
 * Ollie's "best day to buy" tip for a wish (sale vs. payday), or null when there's nothing to say.
 * Looks at all wishes so two lined up for the same day can be spread across cutoffs.
 */
class GetBuyAdviceUseCase(private val clock: com.karencalulo.wishwait.domain.repository.Clock) {
    operator fun invoke(
        wish: com.karencalulo.wishwait.domain.model.Wish,
        allWishes: List<com.karencalulo.wishwait.domain.model.Wish>,
        profile: UserProfile,
    ): com.karencalulo.wishwait.domain.model.BuyAdvice? {
        val wishes = allWishes.filter { it.id != wish.id } + wish
        return com.karencalulo.wishwait.domain.model.BuyAdvisor.adviseAll(wishes, profile, clock.now())[wish.id]
    }
}

/** Wants bought through WishWait in the current cutoff, against the strictness line. */
class GetCutoffSpendingUseCase(private val clock: com.karencalulo.wishwait.domain.repository.Clock) {
    operator fun invoke(
        wishes: List<com.karencalulo.wishwait.domain.model.Wish>,
        profile: UserProfile,
    ): com.karencalulo.wishwait.domain.model.CutoffSpending =
        com.karencalulo.wishwait.domain.model.CutoffCalculator.of(wishes, profile, clock.now())
}

/** A tip went through: Ollie remembers to say thanks (and shows the badge). */
class RecordTipUseCase(private val profiles: ProfileRepository) {
    suspend operator fun invoke() = profiles.update { it.copy(tipsGiven = it.tipsGiven + 1) }
}
