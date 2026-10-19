package com.karencalulo.wishwait.presentation.addwish

import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.Mood
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Verdict

enum class AddStep { FORM, VERDICT }

data class AddWishState(
    val step: AddStep = AddStep.FORM,
    val name: String = "",
    val price: String = "",
    val deliveryFee: String = "",
    val category: Category = Category.GADGETS,
    /** Need or want, picked by the user. A need must also say why (a need reason). */
    val isNeed: Boolean = false,
    val reasons: Set<Reason> = emptySet(),
    val needReasonError: Boolean = false,
    val moods: Set<Mood> = emptySet(),
    val note: String = "",
    val desire: Int = 4,
    val detailsExpanded: Boolean = false,
    val nameError: Boolean = false,
    val priceError: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val verdict: Verdict? = null,
    val chosenDays: Int = 0,
    val isSaving: Boolean = false,
    val showBuyNowConfirm: Boolean = false,
    val celebrateAmount: Double? = null,
    /** The wish just let go — its money can go to the savings goal. */
    val letGoWishId: String? = null,
    val goal: SavingsGoal? = null,
    val goalJustReached: Boolean = false,
    /** Filled when the wish came from "Share to WishWait". */
    val link: String? = null,
    val store: String? = null,
    val fromShare: Boolean = false,
    val sharedPriceFound: Boolean = false,
    val sharedNameFound: Boolean = false,
    /** Reading name/price from the shared link right now. */
    val lookingUp: Boolean = false,
    val foundOnline: Boolean = false,
    /** Clipboard had no product link. */
    val pasteFailed: Boolean = false,
)

sealed interface AddWishIntent {
    data class PasteLink(val text: String?) : AddWishIntent
    data class NameChanged(val value: String) : AddWishIntent
    data class PriceChanged(val value: String) : AddWishIntent
    data class DeliveryFeeChanged(val value: String) : AddWishIntent
    data class CategorySelected(val value: Category) : AddWishIntent
    data class KindChanged(val isNeed: Boolean) : AddWishIntent
    data class ReasonToggled(val value: Reason) : AddWishIntent
    data class MoodToggled(val value: Mood) : AddWishIntent
    data class NoteChanged(val value: String) : AddWishIntent
    data class DesireChanged(val value: Int) : AddWishIntent
    data object AskFriend : AddWishIntent
    data object ToggleDetails : AddWishIntent
    data object BackToForm : AddWishIntent
    data class AdjustDays(val delta: Int) : AddWishIntent
    data class SetDays(val value: Int) : AddWishIntent
    data object StartCooldown : AddWishIntent
    data object BuyNowRequested : AddWishIntent
    data object BuyNowConfirmed : AddWishIntent
    data object DismissDialog : AddWishIntent
    data object LetGo : AddWishIntent
    data object PlanPayment : AddWishIntent
    data object CelebrationDone : AddWishIntent
    data object AddLetGoToGoal : AddWishIntent
}

sealed interface AddWishEffect {
    data class OpenWish(val id: String) : AddWishEffect
    data class OpenCalculator(val price: Double) : AddWishEffect
    data object Close : AddWishEffect
}
