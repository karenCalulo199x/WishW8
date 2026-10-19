package com.karencalulo.wishwait.presentation.wins

import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.model.Stats
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.usecase.ObserveGoalUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveStatsUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishesUseCase
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class WinsState(
    val isLoading: Boolean = true,
    val stats: Stats = Stats.EMPTY,
    val decided: List<Wish> = emptyList(),
    val averageDesireDrop: Double? = null,
    val goal: SavingsGoal? = null,
    val calendar: List<DayCell> = emptyList(),
    val calendarMonth: YearMonth = YearMonth.of(System.currentTimeMillis()),
    val canGoToNextMonth: Boolean = false,
    val streakDays: Int? = null,
)

sealed interface WinsIntent {
    data object PrevMonth : WinsIntent
    data object NextMonth : WinsIntent
}
sealed interface WinsEffect

class WinsViewModel(
    observeStats: ObserveStatsUseCase,
    observeWishes: ObserveWishesUseCase,
    observeGoal: ObserveGoalUseCase,
) : MviViewModel<WinsState, WinsIntent, WinsEffect>(WinsState()) {

    private val selectedMonth = MutableStateFlow(YearMonth.of(System.currentTimeMillis()))

    init {
        combine(observeStats(), observeWishes(), observeGoal(), selectedMonth) { stats, wishes, goal, month ->
            WinsSnapshot(stats, wishes, goal, month)
        }
            .onEach { (stats, wishes, goal, month) ->
                val decided = wishes.filter { it.isDecided }.sortedByDescending { it.decidedAt ?: it.createdAt }
                val tracked = wishes.filter { it.desireLog.size > 1 }
                val currentMonth = YearMonth.of(System.currentTimeMillis())
                setState {
                    copy(
                        isLoading = false,
                        stats = stats,
                        decided = decided,
                        averageDesireDrop = tracked.takeIf { it.isNotEmpty() }
                            ?.map { (it.firstDesire ?: 0) - (it.latestDesire ?: 0) }
                            ?.average(),
                        goal = goal,
                        calendar = buildMonthCalendar(wishes, month),
                        calendarMonth = month,
                        canGoToNextMonth = month < currentMonth,
                        streakDays = computeMindfulStreak(wishes),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: WinsIntent) {
        when (intent) {
            WinsIntent.PrevMonth -> selectedMonth.value = selectedMonth.value.prev()
            WinsIntent.NextMonth -> {
                val next = selectedMonth.value.next()
                if (next <= YearMonth.of(System.currentTimeMillis())) selectedMonth.value = next
            }
        }
    }

    private data class WinsSnapshot(val stats: Stats, val wishes: List<Wish>, val goal: SavingsGoal?, val month: YearMonth)
}
