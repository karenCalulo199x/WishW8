package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.PaymentPlan
import com.karencalulo.wishwait.domain.model.RegretHistory
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.model.WorthProgress
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.repository.ReminderScheduler
import com.karencalulo.wishwait.domain.repository.WishRepository
import java.util.Calendar
import kotlin.math.ceil

/**
 * The after-purchase "worth-it" tracker: cost per use drops every time you use it.
 * Default target: each use should cost no more than 1 hour of your work.
 */
class CalculateWorthUseCase {

    operator fun invoke(wish: Wish, profile: UserProfile): WorthProgress {
        val basis = wish.costBasis.coerceAtLeast(0.0)
        val target = (wish.worthTargetPerUse ?: profile.hourlyRate.takeIf { it > 0 } ?: (basis / DEFAULT_USES))
            .coerceAtLeast(0.01)
        val targetUses = ceil(basis / target).toInt().coerceAtLeast(1)
        val uses = wish.usageLog.size
        return WorthProgress(
            uses = uses,
            costBasis = basis,
            costPerUse = if (uses > 0) basis / uses else basis,
            targetPerUse = target,
            targetUses = targetUses,
            progress = (uses.toFloat() / targetUses).coerceIn(0f, 1f),
            isWorthIt = uses >= targetUses,
            usesLeft = (targetUses - uses).coerceAtLeast(0),
        )
    }

    companion object {
        const val DEFAULT_USES = 100.0
    }
}

class LogUseUseCase(
    private val repo: WishRepository,
    private val clock: Clock,
) {
    suspend fun add(id: String): Wish? {
        val wish = repo.getWish(id) ?: return null
        if (wish.status != WishStatus.BOUGHT) return wish
        val updated = wish.copy(usageLog = wish.usageLog + clock.now())
        repo.upsert(updated)
        return updated
    }

    suspend fun undo(id: String) {
        val wish = repo.getWish(id) ?: return
        if (wish.usageLog.isEmpty()) return
        repo.upsert(wish.copy(usageLog = wish.usageLog.dropLast(1)))
    }
}

class SetWorthTargetUseCase(private val repo: WishRepository) {
    suspend operator fun invoke(id: String, perUse: Double?) {
        val wish = repo.getWish(id) ?: return
        repo.upsert(wish.copy(worthTargetPerUse = perUse?.takeIf { it > 0 }))
    }
}

/** Track an installment plan after buying: monthly payment, due dates, payment reminders. */
class SetPaymentPlanUseCase(
    private val repo: WishRepository,
    private val scheduler: ReminderScheduler,
    private val calculator: AffordabilityCalculator,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: String, months: Int, annualRatePercent: Double, alreadyPaid: Int = 0) {
        val wish = repo.getWish(id) ?: return
        val plan = calculator.installment(wish.price, months, annualRatePercent, 0.0) ?: return
        val start = wish.decidedAt ?: clock.now()
        val firstDue = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.MONTH, 1)
        }.timeInMillis
        val updated = wish.copy(
            payment = PaymentPlan(
                months = months,
                monthlyPayment = plan.monthlyPayment,
                firstDueAt = firstDue,
                paidCount = alreadyPaid.coerceIn(0, months),
            ),
        )
        repo.upsert(updated)
        scheduler.schedule(updated)
    }

    suspend fun clear(id: String) {
        val wish = repo.getWish(id) ?: return
        val updated = wish.copy(payment = null)
        repo.upsert(updated)
        scheduler.schedule(updated)
    }
}

class MarkPaymentPaidUseCase(
    private val repo: WishRepository,
    private val scheduler: ReminderScheduler,
) {
    /** Returns the updated plan (check [PaymentPlan.isComplete] to celebrate). */
    suspend operator fun invoke(id: String, delta: Int = 1): PaymentPlan? {
        val wish = repo.getWish(id) ?: return null
        val plan = wish.payment ?: return null
        val newPlan = plan.copy(paidCount = (plan.paidCount + delta).coerceIn(0, plan.months))
        val updated = wish.copy(payment = newPlan)
        repo.upsert(updated)
        scheduler.schedule(updated)
        return newPlan
    }
}

/**
 * Learns from regret: if past purchases in this category were regretted,
 * future wishes in it get a longer cooldown.
 */
class LearnFromHistoryUseCase {

    operator fun invoke(wishes: List<Wish>, category: Category): RegretHistory {
        val bought = wishes.filter { it.status == WishStatus.BOUGHT && it.category == category }
        val loved = bought.count { it.gladRating == true }
        val regretted = bought.count { it.gladRating == false }
        val rated = loved + regretted
        val mostlyRegret = rated > 0 && regretted * 2 >= rated
        val bonus = when {
            !mostlyRegret -> 0
            regretted >= 2 -> 7
            else -> 3
        }
        return RegretHistory(bought = bought.size, loved = loved, regretted = regretted, bonusDays = bonus)
    }
}
