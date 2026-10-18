package com.karencalulo.wishwait.domain.model

/** Titles and descriptions live in strings.xml (presentation/common/Labels.kt). */
enum class Achievement(val emoji: String) {
    FIRST_PAUSE("⏸️"),
    LET_IT_GO("🕊️"),
    MINDFUL_BUYER("✅"),
    PATIENCE_PRO("🧘"),
    FEELINGS_FADE("📉"),
    NO_REGRETS("💚"),
    FREE_SPIRIT("🦋"),
    MONTH_OF_FREEDOM("🏦"),
    PAID_FOR_ITSELF("💯"),
    DEBT_FREE("🎊"),
    GOAL_GETTER("🎯"),

    /** Sent Ollie a thank-you tip. Hidden until earned, so it never nudges anyone to pay. */
    OLLIE_FRIEND("🦉"),
    ;

    val hiddenUntilUnlocked: Boolean get() = this == OLLIE_FRIEND
}

data class Stats(
    /** Money not spent because wishes were let go. */
    val savedTotal: Double,
    val letGoCount: Int,
    val mindfulBuys: Int,
    val impulseBuys: Int,
    val waitingCount: Int,
    val onHoldTotal: Double,
    /** Work hours not traded for stuff. */
    val hoursSaved: Double,
    val lovedCount: Int,
    val regrettedCount: Int,
    val unlocked: Set<Achievement>,
) {
    companion object {
        val EMPTY = Stats(0.0, 0, 0, 0, 0, 0.0, 0.0, 0, 0, emptySet())
    }
}
