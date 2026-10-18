package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.CutoffCalculator
import com.karencalulo.wishwait.domain.model.GoalDeposit
import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.repository.GoalRepository
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import com.karencalulo.wishwait.domain.repository.WishRepository
import com.karencalulo.wishwait.domain.usecase.ChangeCurrencyUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Features that share money or rules must stay in step with each other. */
class AlignmentTest {

    private class FakeWishes(initial: List<Wish>) : WishRepository {
        val state = MutableStateFlow(initial)
        override fun observeWishes(): Flow<List<Wish>> = state
        override fun observeWish(id: String): Flow<Wish?> = state.map { l -> l.firstOrNull { it.id == id } }
        override suspend fun getWish(id: String) = state.value.firstOrNull { it.id == id }
        override suspend fun getWishes() = state.value
        override suspend fun upsert(wish: Wish) { state.value = state.value.filter { it.id != wish.id } + wish }
        override suspend fun delete(id: String) { state.value = state.value.filter { it.id != id } }
        override suspend fun clear() { state.value = emptyList() }
        override suspend fun replaceAll(wishes: List<Wish>) { state.value = wishes }
    }

    private class FakeProfile(initial: UserProfile) : ProfileRepository {
        val state = MutableStateFlow(initial)
        override val profile: Flow<UserProfile> = state
        override suspend fun current() = state.value
        override suspend fun update(transform: (UserProfile) -> UserProfile) { state.value = transform(state.value) }
        override suspend fun reset() { state.value = UserProfile() }
    }

    private class FakeGoals(initial: SavingsGoal?) : GoalRepository {
        val state = MutableStateFlow(initial)
        override val goal: Flow<SavingsGoal?> = state
        override val completedCount: Flow<Int> = MutableStateFlow(0)
        override suspend fun current() = state.value
        override suspend fun save(goal: SavingsGoal?) { state.value = goal }
        override suspend fun markCompleted() = Unit
        override suspend fun clearAll() { state.value = null }
    }

    @Test
    fun `converting currency converts wishes, income and the savings goal together`() = runBlocking {
        val wish = Wish(
            id = "w", name = "Bag", price = 5_000.0, category = Category.FASHION, reasons = emptySet(),
            moods = emptySet(), note = "", createdAt = 0L, cooldownDays = 7, cooldownEndsAt = 0L,
            status = WishStatus.LET_GO,
        )
        val wishes = FakeWishes(listOf(wish))
        val profile = FakeProfile(UserProfile(monthlyIncome = 50_000.0, currencyCode = "PHP"))
        val goals = FakeGoals(SavingsGoal("Japan", "✈️", 60_000.0, 0L, listOf(GoalDeposit(1L, 5_000.0, "w"))))

        ChangeCurrencyUseCase(wishes, profile, goals)("USD", 0.02)

        assertEquals(100.0, wishes.state.value.single().price, 1e-9)
        assertEquals(1_000.0, profile.state.value.monthlyIncome, 1e-9)
        val goal = goals.state.value!!
        assertEquals(1_200.0, goal.target, 1e-9)
        assertEquals(100.0, goal.saved, 1e-9)
        // The let-go wish and its goal deposit still match after converting.
        assertEquals(wishes.state.value.single().price, goal.deposits.single().amount, 1e-9)
    }

    @Test
    fun `relabel only leaves every amount untouched`() = runBlocking {
        val goals = FakeGoals(SavingsGoal("Japan", "✈️", 60_000.0, 0L))
        val profile = FakeProfile(UserProfile(monthlyIncome = 50_000.0, currencyCode = "PHP"))
        ChangeCurrencyUseCase(FakeWishes(emptyList()), profile, goals)("USD", null)
        assertEquals(60_000.0, goals.state.value!!.target, 1e-9)
        assertEquals(50_000.0, profile.state.value.monthlyIncome, 1e-9)
        assertEquals("USD", profile.state.value.currencyCode)
    }

    @Test
    fun `no payday set means the month is the cutoff, and the wording follows`() {
        val month = CutoffCalculator.of(emptyList(), UserProfile(monthlyIncome = 20_000.0), 0L)
        assertTrue(month.byMonth)
        assertEquals(20_000.0, month.cutoffPay, 1e-9)
        val split = CutoffCalculator.of(emptyList(), UserProfile(monthlyIncome = 20_000.0, paydays = listOf(15, 30)), 0L)
        assertFalse(split.byMonth)
        assertEquals(10_000.0, split.cutoffPay, 1e-9)
    }
}
