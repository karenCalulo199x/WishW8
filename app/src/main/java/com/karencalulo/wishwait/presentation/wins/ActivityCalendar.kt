package com.karencalulo.wishwait.presentation.wins

import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DayStatus { NONE, MINDFUL, MISSED, FUTURE }

/** [emoji] is the category emoji of the mindful decision that day, when there is one. */
data class DayCell(val dayStart: Long, val status: DayStatus, val emoji: String? = null, val isToday: Boolean = false)

/** A calendar month, independent of day/time. [month] is 0-based (Calendar.JANUARY..DECEMBER). */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    fun next() = if (month == Calendar.DECEMBER) YearMonth(year + 1, Calendar.JANUARY) else YearMonth(year, month + 1)
    fun prev() = if (month == Calendar.JANUARY) YearMonth(year - 1, Calendar.DECEMBER) else YearMonth(year, month - 1)
    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })

    companion object {
        fun of(t: Long): YearMonth = Calendar.getInstance().apply { timeInMillis = t }
            .let { YearMonth(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
    }
}

private const val IMPULSE_EMOJI = "⚡"
private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * One cell per day of [yearMonth], from the 1st to the last day of that month. A day is MINDFUL if a
 * wish was let go or bought mindfully that day, MISSED if an impulse buy happened that day (mindful
 * wins if both occurred), FUTURE if it hasn't happened yet, else NONE.
 */
fun buildMonthCalendar(wishes: List<Wish>, yearMonth: YearMonth, now: Long = System.currentTimeMillis()): List<DayCell> {
    val today = startOfDay(now)
    val byDay = wishes
        .filter { it.isDecided && it.decidedAt != null }
        .groupBy { startOfDay(it.decidedAt!!) }

    val monthStart = Calendar.getInstance().apply {
        clear()
        set(yearMonth.year, yearMonth.month, 1)
    }
    val daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)

    return (0 until daysInMonth).map { i ->
        val day = monthStart.timeInMillis + i * DAY_MS
        val isToday = day == today
        if (day > today) {
            DayCell(day, DayStatus.FUTURE, isToday = isToday)
        } else {
            val decisions = byDay[day].orEmpty()
            val mindful = decisions.firstOrNull { it.boughtMindfully || it.status == WishStatus.LET_GO }
            val impulse = decisions.any { it.boughtEarly }
            when {
                mindful != null -> DayCell(day, DayStatus.MINDFUL, mindful.category.emoji, isToday)
                impulse -> DayCell(day, DayStatus.MISSED, IMPULSE_EMOJI, isToday)
                else -> DayCell(day, DayStatus.NONE, isToday = isToday)
            }
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

/** "October 2026" style label for the calendar header. */
fun monthLabel(yearMonth: YearMonth): String {
    val cal = Calendar.getInstance().apply { clear(); set(yearMonth.year, yearMonth.month, 1) }
    return monthYearFormat.format(Date(cal.timeInMillis))
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
