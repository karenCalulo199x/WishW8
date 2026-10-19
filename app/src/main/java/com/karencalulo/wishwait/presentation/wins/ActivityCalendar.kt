package com.karencalulo.wishwait.presentation.wins

import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import java.util.Calendar

enum class DayStatus { NONE, MINDFUL, MISSED }

/** [emoji] is the category emoji of the mindful decision that day, when there is one. */
data class DayCell(val dayStart: Long, val status: DayStatus, val emoji: String? = null)

private const val CALENDAR_WINDOW_DAYS = 30
private const val IMPULSE_EMOJI = "⚡"

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * One cell per day for the last 30 days. A day is MINDFUL if a wish was let go or bought mindfully
 * that day, MISSED if an impulse buy happened that day (mindful wins if both occurred), else NONE.
 */
fun buildActivityCalendar(wishes: List<Wish>, now: Long = System.currentTimeMillis()): List<DayCell> {
    val today = startOfDay(now)
    val byDay = wishes
        .filter { it.isDecided && it.decidedAt != null }
        .groupBy { startOfDay(it.decidedAt!!) }

    return (CALENDAR_WINDOW_DAYS - 1 downTo 0).map { offset ->
        val day = today - offset * DAY_MS
        val decisions = byDay[day].orEmpty()
        val mindful = decisions.firstOrNull { it.boughtMindfully || it.status == WishStatus.LET_GO }
        val impulse = decisions.any { it.boughtEarly }
        when {
            mindful != null -> DayCell(day, DayStatus.MINDFUL, mindful.category.emoji)
            impulse -> DayCell(day, DayStatus.MISSED, IMPULSE_EMOJI)
            else -> DayCell(day, DayStatus.NONE)
        }
    }
}

/** Days since the last impulse buy (or since the first wish, if there's never been one). Null with no wishes yet. */
fun computeMindfulStreak(wishes: List<Wish>, now: Long = System.currentTimeMillis()): Int? {
    if (wishes.isEmpty()) return null
    val lastImpulseAt = wishes
        .filter { it.boughtEarly }
        .maxOfOrNull { it.decidedAt ?: it.createdAt }
    val anchor = lastImpulseAt ?: wishes.minOf { it.createdAt }
    return ((startOfDay(now) - startOfDay(anchor)) / DAY_MS).toInt().coerceAtLeast(0)
}
