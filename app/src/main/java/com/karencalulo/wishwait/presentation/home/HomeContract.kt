package com.karencalulo.wishwait.presentation.home

import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.model.Stats
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus

data class HomeState(
    val isLoading: Boolean = true,
    val wishes: List<Wish> = emptyList(),
    val stats: Stats = Stats.EMPTY,
    val profile: UserProfile = UserProfile(),
    val showAllHistory: Boolean = false,
    val goal: SavingsGoal? = null,
    val showGoalEditor: Boolean = false,
    val showAddMoney: Boolean = false,
    val goalJustReached: Boolean = false,
) {
    val active: List<Wish> get() = wishes.filter { it.status == WishStatus.WAITING }.sortedBy { it.cooldownEndsAt }
    val decided: List<Wish> get() = wishes.filter { it.isDecided }.sortedByDescending { it.decidedAt ?: it.createdAt }
}

sealed interface HomeIntent {
    data object ToggleHistory : HomeIntent
    data object OpenGoalEditor : HomeIntent
    data class SaveGoal(val name: String, val emoji: String, val target: Double) : HomeIntent
    data object RemoveGoal : HomeIntent
    data object OpenAddMoney : HomeIntent
    data class AddMoney(val amount: Double) : HomeIntent
    data object DismissDialogs : HomeIntent
}

sealed interface HomeEffect
