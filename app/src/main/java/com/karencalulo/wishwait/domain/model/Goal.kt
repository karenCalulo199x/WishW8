package com.karencalulo.wishwait.domain.model

/** Money set aside toward the goal. [wishId] is set when it came from letting a wish go. */
data class GoalDeposit(
    val timestamp: Long,
    val amount: Double,
    val wishId: String? = null,
)

/**
 * ONE savings goal at a time — "saying no to a want = saying yes to something bigger."
 * Just a name, an emoji and an amount. A tracker only: WishWait never touches a bank;
 * the user moves the money in their own bank/e-wallet app.
 */
data class SavingsGoal(
    val name: String,
    val emoji: String,
    val target: Double,
    val createdAt: Long,
    val deposits: List<GoalDeposit> = emptyList(),
) {
    val saved: Double get() = deposits.sumOf { it.amount }.coerceAtLeast(0.0)
    val remaining: Double get() = (target - saved).coerceAtLeast(0.0)
    val progress: Float get() = if (target > 0) (saved / target).toFloat().coerceIn(0f, 1f) else 0f
    val isReached: Boolean get() = target > 0 && saved >= target

    /** What share of this goal a price represents (0.34 = 34%). */
    fun shareOf(price: Double): Double = if (target > 0) price / target else 0.0

    companion object {
        val SUGGESTED_EMOJIS = listOf("🎯", "✈️", "🛟", "🏠", "🎓", "🚗", "💍", "📱", "🎁", "💰")
    }
}
