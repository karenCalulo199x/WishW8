package com.karencalulo.wishwait.presentation.detail

import com.karencalulo.wishwait.presentation.common.MoneyFormatter
import com.karencalulo.wishwait.domain.usecase.AddToGoalUseCase
import com.karencalulo.wishwait.domain.usecase.GetBuyAdviceUseCase
import com.karencalulo.wishwait.domain.usecase.GetCutoffSpendingUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishesUseCase
import com.karencalulo.wishwait.domain.model.ReminderTiming
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.usecase.ObserveGoalUseCase
import com.karencalulo.wishwait.presentation.common.labelRes
import com.karencalulo.wishwait.domain.usecase.EditWishUseCase
import com.karencalulo.wishwait.R
import android.content.res.Resources
import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import com.karencalulo.wishwait.domain.usecase.CalculateWorthUseCase
import com.karencalulo.wishwait.domain.usecase.DecideWishUseCase
import com.karencalulo.wishwait.domain.usecase.DeleteWishUseCase
import com.karencalulo.wishwait.domain.usecase.ExtendCooldownUseCase
import com.karencalulo.wishwait.domain.usecase.LogDesireUseCase
import com.karencalulo.wishwait.domain.usecase.LogUseUseCase
import com.karencalulo.wishwait.domain.usecase.MarkPaymentPaidUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveProfileUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishUseCase
import com.karencalulo.wishwait.domain.usecase.RatePurchaseUseCase
import com.karencalulo.wishwait.domain.usecase.SetPaymentPlanUseCase
import com.karencalulo.wishwait.domain.usecase.SetWorthTargetUseCase
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Everything the detail screen needs, grouped so the constructor stays readable. */
class WishDetailUseCases(
    val observeWish: ObserveWishUseCase,
    val observeProfile: ObserveProfileUseCase,
    val calculateImpact: CalculatePriceImpactUseCase,
    val calculateWorth: CalculateWorthUseCase,
    val decideWish: DecideWishUseCase,
    val extendCooldown: ExtendCooldownUseCase,
    val logDesire: LogDesireUseCase,
    val ratePurchase: RatePurchaseUseCase,
    val deleteWish: DeleteWishUseCase,
    val logUse: LogUseUseCase,
    val setWorthTarget: SetWorthTargetUseCase,
    val setPaymentPlan: SetPaymentPlanUseCase,
    val markPaymentPaid: MarkPaymentPaidUseCase,
    val editWish: EditWishUseCase,
    val observeGoal: ObserveGoalUseCase,
    val addToGoal: AddToGoalUseCase,
    val buyAdvice: GetBuyAdviceUseCase,
    val observeWishes: ObserveWishesUseCase,
    val cutoffSpending: GetCutoffSpendingUseCase,
    val clock: Clock,
)

class WishDetailViewModel(
    private val wishId: String,
    private val uc: WishDetailUseCases,
) : MviViewModel<WishDetailState, WishDetailIntent, WishDetailEffect>(WishDetailState()) {

    private var allWishes: List<Wish> = emptyList()

    init {
        combine(uc.observeWish(wishId), uc.observeProfile(), uc.observeWishes()) { wish, profile, all ->
            Triple(wish, profile, all)
        }
            .onEach { (wish, profile, all) ->
                allWishes = all
                setState {
                    copy(
                        isLoading = false,
                        wish = wish,
                        profile = profile,
                        impact = wish?.let { uc.calculateImpact(it.price, it.profileAtCreation(profile)) },
                        worth = wish?.takeIf { it.status == WishStatus.BOUGHT }
                            ?.let { uc.calculateWorth(it, it.profileAtCreation(profile)) },
                        buyAdvice = wish?.let { uc.buyAdvice(it, all, profile) },
                    )
                }
            }
            .launchIn(viewModelScope)
        uc.observeGoal().onEach { g -> setState { copy(goal = g) } }.launchIn(viewModelScope)
    }

    override fun handleIntent(intent: WishDetailIntent) {
        when (intent) {
            is WishDetailIntent.LogDesire -> launch {
                uc.logDesire(wishId, intent.level)
                message { it.getString(R.string.msg_checkin_saved) }
            }
            is WishDetailIntent.Extend -> launch {
                uc.extendCooldown(wishId, intent.days)
                message { it.getString(R.string.msg_extended, Fmt.days(it, intent.days)) }
            }
            WishDetailIntent.BuyRequested -> {
                val wish = currentState.wish ?: return
                if (wish.isReady(uc.clock.now())) buyIfUnderLine() else setState { copy(showBuyEarlyConfirm = true) }
            }
            WishDetailIntent.BuyEarlyConfirmed -> {
                setState { copy(showBuyEarlyConfirm = false) }
                buyIfUnderLine()
            }
            WishDetailIntent.BuyOverLineConfirmed -> {
                setState { copy(overLine = null) }
                buy()
            }
            WishDetailIntent.WaitForNextCutoff -> launch {
                val info = currentState.overLine ?: return@launch
                setState { copy(overLine = null) }
                val at = ReminderTiming.morningOf(info.nextCutoffStart)
                uc.extendCooldown.until(wishId, at)
                message { it.getString(R.string.msg_wait_next_cutoff, Fmt.date(at)) }
            }
            WishDetailIntent.LetGo -> launch {
                uc.decideWish.letGo(wishId)
                setState { copy(celebration = Celebration.LET_GO) }
            }
            is WishDetailIntent.RatePurchase -> launch {
                uc.ratePurchase(wishId, intent.glad)
                val category = currentState.wish?.category
                message { r ->
                    if (intent.glad) {
                        r.getString(R.string.msg_love_noted)
                    } else {
                        val label = category?.let { r.getString(it.labelRes).lowercase() } ?: ""
                        r.getString(R.string.msg_regret_noted, label)
                    }
                }
            }
            WishDetailIntent.DeleteRequested -> setState { copy(showDeleteConfirm = true) }
            WishDetailIntent.DeleteConfirmed -> launch {
                setState { copy(showDeleteConfirm = false) }
                uc.deleteWish(wishId)
                sendEffect(WishDetailEffect.Closed)
            }
            WishDetailIntent.DismissDialogs -> setState {
                copy(
                    showBuyEarlyConfirm = false,
                    showDeleteConfirm = false,
                    showPaymentSetup = false,
                    showTargetEditor = false,
                    showEditor = false,
                    overLine = null,
                )
            }
            WishDetailIntent.CelebrationDone -> setState { copy(celebration = null) }
            WishDetailIntent.PlanPayment -> currentState.wish?.let {
                sendEffect(WishDetailEffect.OpenCalculator(it.price))
            }

            // ---- After purchase ----
            WishDetailIntent.LogUse -> launch {
                val wasWorth = currentState.worth?.isWorthIt == true
                val updated = uc.logUse.add(wishId) ?: return@launch
                val worth = uc.calculateWorth(updated, updated.profileAtCreation(currentState.profile))
                when {
                    worth.isWorthIt && !wasWorth -> setState { copy(celebration = Celebration.WORTH_IT) }
                    else -> message { useMessage(it, worth.uses, worth.usesLeft, worth.progress) }
                }
            }
            WishDetailIntent.UndoUse -> launch { uc.logUse.undo(wishId) }
            WishDetailIntent.OpenTargetEditor -> setState { copy(showTargetEditor = true) }
            is WishDetailIntent.SaveTarget -> launch {
                setState { copy(showTargetEditor = false) }
                uc.setWorthTarget(wishId, intent.perUse)
            }
            WishDetailIntent.OpenPaymentSetup -> setState { copy(showPaymentSetup = true) }
            is WishDetailIntent.SavePaymentPlan -> launch {
                setState { copy(showPaymentSetup = false) }
                uc.setPaymentPlan(wishId, intent.months, intent.annualRate, intent.alreadyPaid)
                message { it.getString(R.string.msg_plan_saved) }
            }
            WishDetailIntent.RemovePaymentPlan -> launch {
                setState { copy(showPaymentSetup = false) }
                uc.setPaymentPlan.clear(wishId)
            }
            WishDetailIntent.MarkPaymentPaid -> launch {
                val plan = uc.markPaymentPaid(wishId) ?: return@launch
                if (plan.isComplete) {
                    setState { copy(celebration = Celebration.DEBT_FREE) }
                } else {
                    message { it.getString(R.string.msg_payment_done, plan.paidCount, plan.months) }
                }
            }
            WishDetailIntent.UndoPayment -> launch { uc.markPaymentPaid(wishId, delta = -1) }

            WishDetailIntent.AddLetGoToGoal -> launch {
                val wish = currentState.wish ?: return@launch
                val reached = uc.addToGoal(wish.price, wish.id)
                setState { copy(celebration = if (reached) Celebration.GOAL_REACHED else null) }
                if (!reached) {
                    val goalName = currentState.goal?.name.orEmpty()
                    val amount = MoneyFormatter(currentState.profile.currencyCode).format(wish.price)
                    message { it.getString(R.string.msg_added_to_goal, amount, goalName) }
                }
            }
            WishDetailIntent.OpenEditor -> setState { copy(showEditor = true) }
            is WishDetailIntent.SaveEdit -> launch {
                setState { copy(showEditor = false) }
                uc.editWish(wishId, intent.name, intent.price, intent.category, intent.note, intent.reasons)
                message { it.getString(R.string.msg_wish_updated) }
            }
        }
    }

    private fun useMessage(r: Resources, uses: Int, left: Int, progress: Float): String = when {
        left == 0 -> r.getString(R.string.use_already_worth, uses)
        uses == 1 -> r.getString(R.string.use_first, left)
        progress >= 0.75f -> r.getString(R.string.use_almost, uses, left)
        progress >= 0.5f -> r.getString(R.string.use_halfway, uses)
        else -> r.getString(R.string.use_logged, uses, left)
    }

    private fun message(text: (Resources) -> String) = sendEffect(WishDetailEffect.Message(text))

    /** Checkpoint at the moment of buying: wants that pile up in one cutoff get a gentle pause. */
    private fun buyIfUnderLine() {
        val wish = currentState.wish ?: return
        val profile = currentState.profile
        val cutoff = uc.cutoffSpending(allWishes.filter { it.id != wish.id }, profile)
        if (wish.isNeed || !cutoff.isOverWith(wish.price)) {
            buy()
            return
        }
        setState {
            copy(
                overLine = OverLineInfo(
                    total = cutoff.spent + wish.price,
                    share = cutoff.shareWith(wish.price),
                    lineShare = cutoff.lineShare,
                    nextCutoffStart = cutoff.cutoff.end,
                    hasPaydays = profile.paydays.isNotEmpty(),
                ),
            )
        }
    }

    private fun buy() = launch {
        val bought = uc.decideWish.buy(wishId)
        if (bought?.boughtMindfully == true) setState { copy(celebration = Celebration.MINDFUL_BUY) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
