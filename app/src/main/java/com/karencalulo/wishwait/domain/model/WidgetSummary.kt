package com.karencalulo.wishwait.domain.model

/** What the home screen widget shows: money kept and the wish that needs you next. */
data class WidgetSummary(
    val keptTotal: Double,
    /** A wish ready to decide (oldest first), otherwise the one whose countdown ends soonest. */
    val nextWish: Wish?,
    val nextIsReady: Boolean,
    val waitingCount: Int,
) {
    companion object {
        fun of(wishes: List<Wish>, now: Long): WidgetSummary {
            val waiting = wishes.filter { it.status == WishStatus.WAITING }
            val ready = waiting.filter { it.isReady(now) }.minByOrNull { it.cooldownEndsAt }
            val next = ready ?: waiting.minByOrNull { it.cooldownEndsAt }
            return WidgetSummary(
                keptTotal = wishes.filter { it.status == WishStatus.LET_GO }.sumOf { it.price },
                nextWish = next,
                nextIsReady = ready != null,
                waitingCount = waiting.size,
            )
        }
    }
}
