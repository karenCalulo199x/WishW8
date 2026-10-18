package com.karencalulo.wishwait.presentation.tips

import android.content.res.Resources
import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.TipEvent
import com.karencalulo.wishwait.domain.model.TipOption
import com.karencalulo.wishwait.domain.model.TipSize
import com.karencalulo.wishwait.domain.repository.TipJar
import com.karencalulo.wishwait.domain.usecase.ObserveProfileUseCase
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class TipJarState(
    val isLoading: Boolean = true,
    val options: List<TipOption> = emptyList(),
    /** Google Play couldn't show tips (offline, not installed from Play, products not set up yet). */
    val unavailable: Boolean = false,
    val tipsGiven: Int = 0,
    val celebrate: Boolean = false,
)

sealed interface TipJarIntent {
    data object Retry : TipJarIntent
    data class Tip(val size: TipSize) : TipJarIntent
    data class LaunchFailed(val size: TipSize) : TipJarIntent
    data object CelebrationDone : TipJarIntent
}

sealed interface TipJarEffect {
    /** Google Play's payment sheet needs the Activity, so the screen opens it. */
    data class OpenPayment(val size: TipSize) : TipJarEffect
    data class Message(val text: (Resources) -> String) : TipJarEffect
}

class TipJarViewModel(
    private val tipJar: TipJar,
    observeProfile: ObserveProfileUseCase,
) : MviViewModel<TipJarState, TipJarIntent, TipJarEffect>(TipJarState()) {

    init {
        observeProfile().onEach { p -> setState { copy(tipsGiven = p.tipsGiven) } }.launchIn(viewModelScope)
        tipJar.events.onEach { event ->
            when (event) {
                TipEvent.Thanks -> setState { copy(celebrate = true) }
                TipEvent.Pending -> message { it.getString(R.string.tip_pending) }
                TipEvent.Cancelled -> message { it.getString(R.string.tip_cancelled) }
                TipEvent.Failed -> message { it.getString(R.string.tip_failed) }
            }
        }.launchIn(viewModelScope)
        load()
    }

    override fun handleIntent(intent: TipJarIntent) {
        when (intent) {
            TipJarIntent.Retry -> load()
            is TipJarIntent.Tip -> sendEffect(TipJarEffect.OpenPayment(intent.size))
            is TipJarIntent.LaunchFailed -> message { it.getString(R.string.tip_failed) }
            TipJarIntent.CelebrationDone -> setState { copy(celebrate = false) }
        }
    }

    private fun load() {
        setState { copy(isLoading = true, unavailable = false) }
        viewModelScope.launch {
            val options = runCatching { tipJar.options() }.getOrNull()
            setState { copy(isLoading = false, options = options.orEmpty(), unavailable = options.isNullOrEmpty()) }
        }
    }

    private fun message(text: (Resources) -> String) = sendEffect(TipJarEffect.Message(text))
}
