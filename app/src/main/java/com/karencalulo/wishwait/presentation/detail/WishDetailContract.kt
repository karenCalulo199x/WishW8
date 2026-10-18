package com.karencalulo.wishwait.presentation.detail

import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.model.BuyAdvice
import com.karencalulo.wishwait.domain.model.Category
import android.content.res.Resources
import com.karencalulo.wishwait.domain.model.PriceImpact
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WorthProgress

/** Buying this would push wants past the line for this cutoff. */
data class OverLineInfo(
    /** Wants this cutoff including this item. */
    val total: Double,
    val share: Double,
    val lineShare: Double,
    /** When the next cutoff starts (next payday, or the 1st of next month). */
    val nextCutoffStart: Long,
    val hasPaydays: Boolean,
)

enum class Celebration { LET_GO, MINDFUL_BUY, WORTH_IT, DEBT_FREE, GOAL_REACHED }

data class WishDetailState(
    val isLoading: Boolean = true,
    val wish: Wish? = null,
    val impact: PriceImpact? = null,
    val worth: WorthProgress? = null,
    val profile: UserProfile = UserProfile(),
    val showBuyEarlyConfirm: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showPaymentSetup: Boolean = false,
    val showTargetEditor: Boolean = false,
    val showEditor: Boolean = false,
    val celebration: Celebration? = null,
    val goal: SavingsGoal? = null,
    val buyAdvice: BuyAdvice? = null,
    val overLine: OverLineInfo? = null,
)

sealed interface WishDetailIntent {
    data class LogDesire(val level: Int) : WishDetailIntent
    data class Extend(val days: Int) : WishDetailIntent
    data object BuyRequested : WishDetailIntent
    data object BuyEarlyConfirmed : WishDetailIntent
    data object BuyOverLineConfirmed : WishDetailIntent
    data object WaitForNextCutoff : WishDetailIntent
    data object LetGo : WishDetailIntent
    data class RatePurchase(val glad: Boolean) : WishDetailIntent
    data object DeleteRequested : WishDetailIntent
    data object DeleteConfirmed : WishDetailIntent
    data object DismissDialogs : WishDetailIntent
    data object CelebrationDone : WishDetailIntent
    data object PlanPayment : WishDetailIntent

    // After purchase
    data object LogUse : WishDetailIntent
    data object UndoUse : WishDetailIntent
    data object OpenTargetEditor : WishDetailIntent
    data class SaveTarget(val perUse: Double?) : WishDetailIntent
    data object OpenPaymentSetup : WishDetailIntent
    data class SavePaymentPlan(val months: Int, val annualRate: Double, val alreadyPaid: Int) : WishDetailIntent
    data object RemovePaymentPlan : WishDetailIntent
    data object MarkPaymentPaid : WishDetailIntent
    data object UndoPayment : WishDetailIntent

    data object AddLetGoToGoal : WishDetailIntent

    // Editing
    data object OpenEditor : WishDetailIntent
    data class SaveEdit(
        val name: String,
        val price: Double,
        val category: Category,
        val note: String,
        val reasons: Set<com.karencalulo.wishwait.domain.model.Reason>,
    ) : WishDetailIntent
}

sealed interface WishDetailEffect {
    data object Closed : WishDetailEffect
    data class OpenCalculator(val price: Double) : WishDetailEffect
    /** Text is built from resources in the UI so it can be translated. */
    data class Message(val text: (Resources) -> String) : WishDetailEffect
}
