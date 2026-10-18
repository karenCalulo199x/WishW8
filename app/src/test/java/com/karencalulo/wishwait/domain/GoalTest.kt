package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.data.local.BackupJsonCodec
import com.karencalulo.wishwait.domain.model.Achievement
import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.GoalDeposit
import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.repository.GoalRepository
import com.karencalulo.wishwait.domain.usecase.AddToGoalUseCase
import com.karencalulo.wishwait.domain.usecase.ComputeStatsUseCase
import com.karencalulo.wishwait.domain.usecase.SetGoalUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalTest {

    private class FakeGoals : GoalRepository {
        val state = MutableStateFlow<SavingsGoal?>(null)
        val completed = MutableStateFlow(0)
        override val goal: Flow<SavingsGoal?> = state
        override val completedCount: Flow<Int> = completed
        override suspend fun current() = state.value
        override suspend fun save(goal: SavingsGoal?) { state.value = goal }
        override suspend fun markCompleted() { completed.value++ }
        override suspend fun clearAll() { state.value = null; completed.value = 0 }
    }

    private val clock = Clock { 1_000L }

    @Test
    fun `letting go fills the goal and celebrates exactly once`() = runBlocking {
        val repo = FakeGoals()
        SetGoalUseCase(repo, clock)("Japan trip", "✈️", 60_000.0)
        val add = AddToGoalUseCase(repo, clock)

        assertFalse(add(25_000.0, wishId = "phone"))
        assertEquals(25_000.0, repo.state.value!!.saved, 1e-9)
        assertEquals(35_000.0, repo.state.value!!.remaining, 1e-9)

        assertTrue(add(40_000.0)) // crosses the target
        assertEquals(1, repo.completed.value)
        assertFalse(add(1_000.0)) // already reached: no second celebration
        assertEquals(1, repo.completed.value)
    }

    @Test
    fun `editing keeps the money saved, a new goal after reaching starts fresh`() = runBlocking {
        val repo = FakeGoals()
        val set = SetGoalUseCase(repo, clock)
        set("Bike", "🚗", 10_000.0)
        AddToGoalUseCase(repo, clock)(4_000.0)
        set("Better bike", "🚗", 15_000.0)
        assertEquals(4_000.0, repo.state.value!!.saved, 1e-9)

        AddToGoalUseCase(repo, clock)(11_000.0)
        assertTrue(repo.state.value!!.isReached)
        set("Emergency fund", "🛟", 50_000.0)
        assertEquals(0.0, repo.state.value!!.saved, 1e-9)
    }

    @Test
    fun `verdict share of goal`() {
        val goal = SavingsGoal("Japan trip", "✈️", 60_000.0, 0L, listOf(GoalDeposit(0, 10_000.0)))
        assertEquals(0.25, goal.shareOf(15_000.0), 1e-9)
        assertEquals(50_000.0, goal.remaining, 1e-9)
    }

    @Test
    fun `badge and backup include the goal`() {
        val stats = ComputeStatsUseCase()(emptyList(), UserProfile(), goalsCompleted = 1)
        assertTrue(Achievement.GOAL_GETTER in stats.unlocked)

        val goal = SavingsGoal("Japan trip", "✈️", 60_000.0, 5L, listOf(GoalDeposit(7, 2_500.0, "w1")))
        val backup = Backup(Backup.CURRENT_VERSION, 1L, UserProfile(onboarded = true), emptyList(), goal)
        val codec = BackupJsonCodec()
        assertEquals(goal, codec.decode(codec.encode(backup))!!.goal)
    }
}
