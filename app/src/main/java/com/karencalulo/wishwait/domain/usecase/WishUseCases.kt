package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.DesireCheck
import com.karencalulo.wishwait.domain.model.HOUR_MS
import com.karencalulo.wishwait.domain.model.Mood
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.Stats
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import com.karencalulo.wishwait.domain.repository.ReminderScheduler
import com.karencalulo.wishwait.domain.repository.WishRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

data class WishDraft(
    val name: String,
    val price: Double,
    val category: Category,
    val reasons: Set<Reason>,
    val moods: Set<Mood>,
    val note: String,
    val desire: Int,
    val incomeAtCreation: Double? = null,
    val hoursAtCreation: Int? = null,
    val link: String? = null,
    val store: String? = null,
)

enum class CreateOutcome { COOLDOWN, BUY_NOW, LET_GO }

class ObserveWishesUseCase(private val repo: WishRepository) {
    operator fun invoke(): Flow<List<Wish>> = repo.observeWishes()
}

class ObserveWishUseCase(private val repo: WishRepository) {
    operator fun invoke(id: String): Flow<Wish?> = repo.observeWish(id)
}

class ObserveStatsUseCase(
    private val wishes: WishRepository,
    private val profiles: ProfileRepository,
    private val goals: com.karencalulo.wishwait.domain.repository.GoalRepository,
    private val compute: ComputeStatsUseCase,
) {
    operator fun invoke(): Flow<Stats> =
        combine(wishes.observeWishes(), profiles.profile, goals.completedCount) { list, profile, completed ->
            compute(list, profile, completed)
        }
}

class CreateWishUseCase(
    private val repo: WishRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    suspend operator fun invoke(draft: WishDraft, cooldownDays: Int, outcome: CreateOutcome): Wish {
        val now = clock.now()
        val days = cooldownDays.coerceIn(0, BuildVerdictUseCase.MAX_DAYS)
        val wish = Wish(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim(),
            price = draft.price,
            category = draft.category,
            reasons = draft.reasons,
            moods = draft.moods,
            note = draft.note.trim(),
            createdAt = now,
            cooldownDays = days,
            cooldownEndsAt = now + days * DAY_MS,
            status = when (outcome) {
                CreateOutcome.COOLDOWN -> if (days == 0) WishStatus.BOUGHT else WishStatus.WAITING
                CreateOutcome.BUY_NOW -> WishStatus.BOUGHT
                CreateOutcome.LET_GO -> WishStatus.LET_GO
            },
            decidedAt = if (outcome == CreateOutcome.COOLDOWN && days > 0) null else now,
            desireLog = listOf(DesireCheck(now, draft.desire.coerceIn(1, 5))),
            incomeAtCreation = draft.incomeAtCreation,
            hoursAtCreation = draft.hoursAtCreation,
            link = draft.link,
            store = draft.store,
        )
        repo.upsert(wish)
        // Waiting → cooldown nudges; bought → the 14-day "still happy?" check.
        scheduler.schedule(wish)
        return wish
    }
}

class DecideWishUseCase(
    private val repo: WishRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    suspend fun buy(id: String) = decide(id, WishStatus.BOUGHT)
    suspend fun letGo(id: String) = decide(id, WishStatus.LET_GO)

    private suspend fun decide(id: String, status: WishStatus): Wish? {
        val wish = repo.getWish(id) ?: return null
        val updated = wish.copy(status = status, decidedAt = clock.now())
        repo.upsert(updated)
        scheduler.schedule(updated) // cancels cooldown nudges, sets the regret check if bought
        return updated
    }
}

class ExtendCooldownUseCase(
    private val repo: WishRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: String, extraDays: Int) {
        val wish = repo.getWish(id) ?: return
        if (wish.status != WishStatus.WAITING || extraDays <= 0) return
        val base = maxOf(wish.cooldownEndsAt, clock.now())
        val newEnd = base + extraDays * DAY_MS
        val totalDays = ((newEnd - wish.createdAt + DAY_MS - 1) / DAY_MS).toInt()
        val updated = wish.copy(cooldownEndsAt = newEnd, cooldownDays = totalDays)
        repo.upsert(updated)
        scheduler.schedule(updated)
    }

    /** Keep waiting until [at] (e.g. the next payday). Never shortens a countdown. */
    suspend fun until(id: String, at: Long) {
        val wish = repo.getWish(id) ?: return
        if (wish.status != WishStatus.WAITING || at <= wish.cooldownEndsAt || at <= clock.now()) return
        val totalDays = ((at - wish.createdAt + DAY_MS - 1) / DAY_MS).toInt()
        val updated = wish.copy(cooldownEndsAt = at, cooldownDays = totalDays)
        repo.upsert(updated)
        scheduler.schedule(updated)
    }
}

/** Daily "how much do you still want it?" check-in. One entry per ~day; re-rating replaces it. */
class LogDesireUseCase(
    private val repo: WishRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: String, level: Int) {
        val wish = repo.getWish(id) ?: return
        val now = clock.now()
        val entry = DesireCheck(now, level.coerceIn(1, 5))
        val last = wish.desireLog.lastOrNull()
        val log = if (last != null && now - last.timestamp < REPLACE_WINDOW_MS && wish.desireLog.size > 1) {
            wish.desireLog.dropLast(1) + entry
        } else {
            wish.desireLog + entry
        }
        repo.upsert(wish.copy(desireLog = log))
    }

    companion object {
        const val REPLACE_WINDOW_MS = 12 * HOUR_MS
    }
}

class RatePurchaseUseCase(
    private val repo: WishRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: String, glad: Boolean) {
        val wish = repo.getWish(id) ?: return
        val updated = wish.copy(gladRating = glad)
        repo.upsert(updated)
        scheduler.schedule(updated) // rated → no need for the regret-check reminder
    }
}

class DeleteWishUseCase(
    private val repo: WishRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: String) {
        scheduler.cancel(id)
        repo.delete(id)
    }
}

class ResetAllDataUseCase(
    private val wishes: WishRepository,
    private val profiles: ProfileRepository,
    private val scheduler: ReminderScheduler,
    private val goals: com.karencalulo.wishwait.domain.repository.GoalRepository? = null,
    private val backupStatus: com.karencalulo.wishwait.domain.repository.BackupStatusRepository? = null,
) {
    suspend operator fun invoke() {
        wishes.getWishes().forEach { scheduler.cancel(it.id) }
        wishes.clear()
        goals?.clearAll()
        // Stops Drive backup so the empty phone never overwrites the backup in Drive.
        backupStatus?.reset()
        profiles.reset()
    }
}

/** Fix a typo or a price change. The cooldown and history stay as they are. */
class EditWishUseCase(
    private val repo: WishRepository,
    private val scheduler: ReminderScheduler,
) {
    /** [reasons] null = keep them. Changing need vs. want doesn't touch the countdown. */
    suspend operator fun invoke(
        id: String,
        name: String,
        price: Double,
        category: Category,
        note: String,
        reasons: Set<com.karencalulo.wishwait.domain.model.Reason>? = null,
    ) {
        val wish = repo.getWish(id) ?: return
        if (name.isBlank() || price <= 0) return
        val scale = if (wish.price > 0) price / wish.price else 1.0
        val updated = wish.copy(
            name = name.trim(),
            price = price,
            category = category,
            note = note.trim(),
            reasons = reasons ?: wish.reasons,
            // An installment follows the price it was set up for.
            payment = wish.payment?.let { it.copy(monthlyPayment = it.monthlyPayment * scale) },
        )
        repo.upsert(updated)
        scheduler.schedule(updated)
    }
}
