package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.GoalDeposit
import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow

class ObserveGoalUseCase(private val repo: GoalRepository) {
    operator fun invoke(): Flow<SavingsGoal?> = repo.goal
}

/** Create the goal, or rename/re-target it (money already saved is kept). */
class SetGoalUseCase(
    private val repo: GoalRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(name: String, emoji: String, target: Double) {
        if (name.isBlank() || target <= 0) return
        val existing = repo.current()
        val keep = existing?.takeUnless { it.isReached }
        repo.save(
            SavingsGoal(
                name = name.trim().take(40),
                emoji = emoji,
                target = target,
                createdAt = keep?.createdAt ?: clock.now(),
                deposits = keep?.deposits ?: emptyList(),
            ),
        )
    }
}

/** Add money to the goal. Returns true when this deposit completed it (time to celebrate). */
class AddToGoalUseCase(
    private val repo: GoalRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(amount: Double, wishId: String? = null): Boolean {
        val goal = repo.current() ?: return false
        if (amount <= 0) return false
        val wasReached = goal.isReached
        val updated = goal.copy(deposits = goal.deposits + GoalDeposit(clock.now(), amount, wishId))
        repo.save(updated)
        val justReached = !wasReached && updated.isReached
        if (justReached) repo.markCompleted()
        return justReached
    }
}

class RemoveGoalUseCase(private val repo: GoalRepository) {
    suspend operator fun invoke() = repo.save(null)
}
