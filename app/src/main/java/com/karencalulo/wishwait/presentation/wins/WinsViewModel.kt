package com.karencalulo.wishwait.presentation.wins

import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.domain.model.Stats
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.usecase.ObserveStatsUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishesUseCase
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class WinsState(
    val isLoading: Boolean = true,
    val stats: Stats = Stats.EMPTY,
    val decided: List<Wish> = emptyList(),
    val averageDesireDrop: Double? = null,
)

sealed interface WinsIntent
sealed interface WinsEffect

class WinsViewModel(
    observeStats: ObserveStatsUseCase,
    observeWishes: ObserveWishesUseCase,
) : MviViewModel<WinsState, WinsIntent, WinsEffect>(WinsState()) {

    init {
        combine(observeStats(), observeWishes()) { stats, wishes -> stats to wishes }
            .onEach { (stats, wishes) ->
                val decided = wishes.filter { it.isDecided }.sortedByDescending { it.decidedAt ?: it.createdAt }
                val tracked = wishes.filter { it.desireLog.size > 1 }
                setState {
                    copy(
                        isLoading = false,
                        stats = stats,
                        decided = decided,
                        averageDesireDrop = tracked.takeIf { it.isNotEmpty() }
                            ?.map { (it.firstDesire ?: 0) - (it.latestDesire ?: 0) }
                            ?.average(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: WinsIntent) = Unit
}
