package com.karencalulo.wishwait.presentation.home

import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.domain.usecase.AddToGoalUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveGoalUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveProfileUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveStatsUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishesUseCase
import com.karencalulo.wishwait.domain.usecase.RemoveGoalUseCase
import com.karencalulo.wishwait.domain.usecase.SetGoalUseCase
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(
    observeWishes: ObserveWishesUseCase,
    observeStats: ObserveStatsUseCase,
    observeProfile: ObserveProfileUseCase,
    observeGoal: ObserveGoalUseCase,
    private val setGoal: SetGoalUseCase,
    private val addToGoal: AddToGoalUseCase,
    private val removeGoal: RemoveGoalUseCase,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        combine(observeWishes(), observeStats(), observeProfile(), observeGoal()) { wishes, stats, profile, goal ->
            HomeData(wishes, stats, profile, goal)
        }.onEach { d ->
            setState {
                copy(isLoading = false, wishes = d.wishes, stats = d.stats, profile = d.profile, goal = d.goal)
            }
        }.launchIn(viewModelScope)
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ToggleHistory -> setState { copy(showAllHistory = !showAllHistory) }
            HomeIntent.OpenGoalEditor -> setState { copy(showGoalEditor = true) }
            is HomeIntent.SaveGoal -> viewModelScope.launch {
                setState { copy(showGoalEditor = false) }
                setGoal(intent.name, intent.emoji, intent.target)
            }
            HomeIntent.RemoveGoal -> viewModelScope.launch {
                setState { copy(showGoalEditor = false) }
                removeGoal()
            }
            HomeIntent.OpenAddMoney -> setState { copy(showAddMoney = true) }
            is HomeIntent.AddMoney -> viewModelScope.launch {
                setState { copy(showAddMoney = false) }
                val reached = addToGoal(intent.amount)
                if (reached) setState { copy(goalJustReached = true) }
            }
            HomeIntent.DismissDialogs -> setState {
                copy(showGoalEditor = false, showAddMoney = false, goalJustReached = false)
            }
        }
    }

    private data class HomeData(
        val wishes: List<com.karencalulo.wishwait.domain.model.Wish>,
        val stats: com.karencalulo.wishwait.domain.model.Stats,
        val profile: com.karencalulo.wishwait.domain.model.UserProfile,
        val goal: com.karencalulo.wishwait.domain.model.SavingsGoal?,
    )
}
