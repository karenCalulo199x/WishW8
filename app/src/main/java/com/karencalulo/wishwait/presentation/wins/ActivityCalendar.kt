package com.karencalulo.wishwait.presentation.wins

import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DayStatus { NONE, MINDFUL, MISSED }

/** [emoji] is the category emoji of the mindful decision that day, when there is one. */
data class DayCell(val dayStart: Long, val status: DayStatus, val emoji: String? = null, val isToday: Boolean = false)

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
        val isToday = day == today
        when {
            mindful != null -> DayCell(day, DayStatus.MINDFUL, mindful.category.emoji, isToday)
            impulse -> DayCell(day, DayStatus.MISSED, IMPULSE_EMOJI, isToday)
            else -> DayCell(day, DayStatus.NONE, isToday = isToday)
        }
    }
}

/** Single-letter weekday labels (M T W T F S S), Monday-first, following the device's locale names. */
fun mondayStartWeekdayLabels(): List<String> {
    val symbols = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
    val order = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
    )
    return order.map { symbols[it].take(1) }
}

/** Blank cells to prepend so the first real day lands in its correct Monday-first weekday column. */
fun leadingBlankCount(firstDayStart: Long): Int {
    val dow = Calendar.getInstance().apply { timeInMillis = firstDayStart }.get(Calendar.DAY_OF_WEEK)
    return (dow + 5) % 7
}

private val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())

/** "Sep 20 – Oct 19" style label for the calendar's date range. */
fun calendarRangeLabel(days: List<DayCell>): String? {
    val first = days.firstOrNull() ?: return null
    val last = days.lastOrNull() ?: return null
    return "${monthDayFormat.format(Date(first.dayStart))} – ${monthDayFormat.format(Date(last.dayStart))}"
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
