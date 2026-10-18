package com.karencalulo.wishwait.domain.model

/**
 * Optional thank-you tips for Ollie. They unlock nothing: WishWait stays complete and free.
 * [productId] must match the one-time (consumable) in-app products created in Play Console.
 */
enum class TipSize(val productId: String, val emoji: String) {
    SMALL("ollie_tip_small", "🌱"),
    COFFEE("ollie_tip_coffee", "☕"),
    LUNCH("ollie_tip_lunch", "🍱"),
}

/** A tip with the price Google Play shows for this person (their currency, taxes included). */
data class TipOption(val size: TipSize, val price: String)

sealed interface TipEvent {
    /** Payment went through and was recorded. */
    data object Thanks : TipEvent
    /** E.g. a cash or e-wallet payment that finishes later. */
    data object Pending : TipEvent
    data object Cancelled : TipEvent
    data object Failed : TipEvent
}
