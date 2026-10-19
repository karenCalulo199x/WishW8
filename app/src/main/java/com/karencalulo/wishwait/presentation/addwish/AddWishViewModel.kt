package com.karencalulo.wishwait.presentation.addwish

import com.karencalulo.wishwait.domain.usecase.GetCutoffSpendingUseCase
import com.karencalulo.wishwait.domain.usecase.GetSpendingContextUseCase
import com.karencalulo.wishwait.domain.usecase.AddToGoalUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveGoalUseCase
import kotlinx.coroutines.flow.first
import com.karencalulo.wishwait.domain.usecase.FetchLinkDetailsUseCase
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.usecase.ParseSharedTextUseCase
import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.domain.usecase.BuildVerdictUseCase
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import com.karencalulo.wishwait.domain.usecase.CreateOutcome
import com.karencalulo.wishwait.domain.usecase.CreateWishUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveProfileUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishesUseCase
import com.karencalulo.wishwait.domain.usecase.LearnFromHistoryUseCase
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.usecase.WishDraft
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.domain.model.reasonsAfterSwitch
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AddWishViewModel(
    sharedText: SharedText?,
    private val parseSharedText: ParseSharedTextUseCase,
    private val fetchLinkDetails: FetchLinkDetailsUseCase,
    private val observeProfile: ObserveProfileUseCase,
    observeWishes: ObserveWishesUseCase,
    private val learnFromHistory: LearnFromHistoryUseCase,
    private val calculateImpact: CalculatePriceImpactUseCase,
    private val buildVerdict: BuildVerdictUseCase,
    private val createWish: CreateWishUseCase,
    observeGoal: ObserveGoalUseCase,
    private val addToGoal: AddToGoalUseCase,
    private val spendingContext: GetSpendingContextUseCase,
    private val cutoffSpending: GetCutoffSpendingUseCase,
) : MviViewModel<AddWishState, AddWishIntent, AddWishEffect>(initialState(sharedText, parseSharedText)) {

    private var pastWishes: List<Wish> = emptyList()

    init {
        observeProfile().onEach { p -> setState { copy(profile = p) } }.launchIn(viewModelScope)
        observeWishes().onEach { pastWishes = it }.launchIn(viewModelScope)
        observeGoal().onEach { g -> setState { copy(goal = g) } }.launchIn(viewModelScope)

        lookUpMissingDetails()
    }

    /** Share/paste was missing details (TikTok: link only, Lazada: no price) → read them from the page. */
    private fun lookUpMissingDetails() {
        val start = currentState
        val link = start.link
        if (start.fromShare && link != null && (!start.sharedNameFound || !start.sharedPriceFound)) {
            viewModelScope.launch {
                if (!observeProfile().first().fetchLinkDetails) return@launch
                setState { copy(lookingUp = true) }
                val found = fetchLinkDetails(link)
                setState {
                    val foundName = found?.name?.takeIf { it.isNotBlank() }
                    val foundPrice = found?.price
                    copy(
                        lookingUp = false,
                        // Never overwrite what the user already typed.
                        name = if (name.isBlank() && foundName != null) foundName else name,
                        price = if (price.isBlank() && foundPrice != null) Fmt.plain(foundPrice) else price,
                        sharedNameFound = sharedNameFound || foundName != null,
                        sharedPriceFound = sharedPriceFound || foundPrice != null,
                        foundOnline = found != null,
                    )
                }
            }
        }
    }

    override fun handleIntent(intent: AddWishIntent) {
        when (intent) {
            is AddWishIntent.PasteLink -> pasteLink(intent.text)
            is AddWishIntent.NameChanged -> setState { copy(name = intent.value.take(60), nameError = false) }
            is AddWishIntent.PriceChanged -> setState { copy(price = Fmt.sanitizeAmount(intent.value), priceError = false) }
            is AddWishIntent.DeliveryFeeChanged -> setState { copy(deliveryFee = Fmt.sanitizeAmount(intent.value)) }
            is AddWishIntent.CategorySelected -> setState { copy(category = intent.value) }
            is AddWishIntent.KindChanged -> setState {
                copy(isNeed = intent.isNeed, reasons = reasonsAfterSwitch(reasons, intent.isNeed), needReasonError = false)
            }
            is AddWishIntent.ReasonToggled -> setState {
                // Only reasons that match the current choice can be picked.
                if (intent.value.isNeed != isNeed) {
                    this
                } else {
                    val newReasons = if (intent.value in reasons) reasons - intent.value else reasons + intent.value
                    copy(reasons = newReasons, needReasonError = false)
                }
            }
            is AddWishIntent.NoteChanged -> setState { copy(note = intent.value.take(280)) }
            is AddWishIntent.DesireChanged -> setState { copy(desire = intent.value) }
            AddWishIntent.AskFriend -> askFriend()
            AddWishIntent.ContinueToReflect -> continueToReflect()
            AddWishIntent.Back -> setState {
                copy(
                    step = when (step) {
                        AddStep.REFLECT -> AddStep.BASICS
                        AddStep.VERDICT -> AddStep.REFLECT
                        AddStep.BASICS -> AddStep.BASICS
                    },
                )
            }
            is AddWishIntent.AdjustDays -> setState {
                copy(chosenDays = (chosenDays + intent.delta).coerceIn(0, BuildVerdictUseCase.MAX_DAYS))
            }
            is AddWishIntent.SetDays -> setState {
                copy(chosenDays = intent.value.coerceIn(0, BuildVerdictUseCase.MAX_DAYS))
            }
            AddWishIntent.StartCooldown -> save(CreateOutcome.COOLDOWN)
            AddWishIntent.BuyNowRequested -> {
                val recommended = currentState.verdict?.recommendedDays ?: 0
                if (recommended == 0) save(CreateOutcome.BUY_NOW) else setState { copy(showBuyNowConfirm = true) }
            }
            AddWishIntent.BuyNowConfirmed -> {
                setState { copy(showBuyNowConfirm = false) }
                save(CreateOutcome.BUY_NOW)
            }
            AddWishIntent.DismissDialog -> setState { copy(showBuyNowConfirm = false) }
            AddWishIntent.LetGo -> save(CreateOutcome.LET_GO)
            AddWishIntent.PlanPayment -> {
                val price = Fmt.parseAmount(currentState.price) ?: return
                sendEffect(AddWishEffect.OpenCalculator(price))
            }
            AddWishIntent.AddLetGoToGoal -> {
                val amount = currentState.celebrateAmount ?: return
                val wishId = currentState.letGoWishId
                viewModelScope.launch {
                    val reached = addToGoal(amount, wishId)
                    if (reached) {
                        setState { copy(celebrateAmount = null, goalJustReached = true) }
                    } else {
                        setState { copy(celebrateAmount = null) }
                        sendEffect(AddWishEffect.Close)
                    }
                }
            }
            AddWishIntent.CelebrationDone -> {
                setState { copy(celebrateAmount = null) }
                sendEffect(AddWishEffect.Close)
            }
        }
    }

    /** "Copy Link" in Shopee (its share panel doesn't list other apps) → paste here. */
    private fun pasteLink(text: String?) {
        val item = text?.takeIf { it.isNotBlank() }?.let { parseSharedText(it) }
        if (item == null || (item.link == null && item.name.isBlank())) {
            setState { copy(pasteFailed = true) }
            return
        }
        setState {
            copy(
                name = item.name.ifBlank { name },
                price = item.price?.let { Fmt.plain(it) } ?: price,
                reasons = if (isNeed) reasons else reasons + Reason.SAW_AN_AD,
                link = item.link,
                store = item.store,
                fromShare = true,
                sharedNameFound = item.name.isNotBlank() || name.isNotBlank(),
                sharedPriceFound = item.price != null || price.isNotBlank(),
                foundOnline = false,
                pasteFailed = false,
            )
        }
        lookUpMissingDetails()
    }

    /** Step 1 (basics) -> step 2 (reflect): only the name/price are required to move on. */
    private fun continueToReflect() {
        val s = currentState
        val price = Fmt.parseAmount(s.price)
        val nameError = s.name.isBlank()
        val priceError = price == null || price <= 0.0
        if (nameError || priceError) {
            setState { copy(nameError = nameError, priceError = priceError) }
        } else {
            setState { copy(step = AddStep.REFLECT) }
        }
    }

    private fun askFriend() {
        val s = currentState
        val price = Fmt.parseAmount(s.price)
        val nameError = s.name.isBlank()
        val priceError = price == null || price <= 0.0
        val needReasonError = s.isNeed && s.reasons.none { it.isNeed }
        if (nameError || priceError || needReasonError) {
            // Name/price shouldn't fail here since step 1 already checked them, but if they
            // somehow did, send the user back to where those fields actually live.
            val backToBasics = nameError || priceError
            setState {
                copy(
                    nameError = nameError,
                    priceError = priceError,
                    needReasonError = needReasonError,
                    step = if (backToBasics) AddStep.BASICS else step,
                )
            }
            return
        }
        val deliveryFee = Fmt.parseAmount(s.deliveryFee) ?: 0.0
        val impact = calculateImpact(price!! + deliveryFee, s.profile)
        val history = learnFromHistory(pastWishes, s.category)
        val verdict = buildVerdict(
            impact = impact,
            reasons = s.reasons,
            moods = s.moods,
            strictness = s.profile.strictness,
            desire = s.desire,
            history = history,
            timing = spendingContext(s.profile),
            cutoff = cutoffSpending(pastWishes, s.profile),
        )
        setState { copy(step = AddStep.VERDICT, verdict = verdict, chosenDays = verdict.recommendedDays) }
    }

    private fun save(outcome: CreateOutcome) {
        val s = currentState
        if (s.isSaving) return
        val price = Fmt.parseAmount(s.price) ?: return
        setState { copy(isSaving = true) }
        viewModelScope.launch {
            val draft = WishDraft(
                name = s.name,
                price = price,
                category = s.category,
                reasons = s.reasons,
                moods = s.moods,
                note = s.note,
                desire = s.desire,
                incomeAtCreation = s.profile.monthlyIncome.takeIf { it > 0 },
                hoursAtCreation = s.profile.hoursPerMonth,
                link = s.link,
                store = s.store,
                deliveryFee = Fmt.parseAmount(s.deliveryFee) ?: 0.0,
            )
            // "Buy now" keeps the recommended cooldown on record so it's honestly tracked as early.
            val days = when (outcome) {
                CreateOutcome.COOLDOWN -> s.chosenDays
                else -> s.verdict?.recommendedDays ?: s.chosenDays
            }
            val wish = createWish(draft, days, outcome)
            setState { copy(isSaving = false) }
            when (outcome) {
                CreateOutcome.LET_GO -> setState { copy(celebrateAmount = price, letGoWishId = wish.id) }
                else -> sendEffect(AddWishEffect.OpenWish(wish.id))
            }
        }
    }
}

/** Raw text handed over by another app's share sheet. */
data class SharedText(val text: String?, val subject: String?)

/** A shared item pre-fills the form — but still goes through Ollie's verdict like any other wish. */
private fun initialState(shared: SharedText?, parse: ParseSharedTextUseCase): AddWishState {
    if (shared == null) return AddWishState()
    val item = parse(shared.text, shared.subject)
    return AddWishState(
        name = item.name,
        price = item.price?.let { Fmt.plain(it) } ?: "",
        // Shopping in an app means it was seen online; the user can untick it.
        reasons = setOf(Reason.SAW_AN_AD),
        link = item.link,
        store = item.store,
        fromShare = true,
        sharedPriceFound = item.price != null,
        sharedNameFound = item.name.isNotBlank(),
    )
}
