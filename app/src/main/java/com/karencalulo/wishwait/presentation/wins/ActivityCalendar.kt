package com.karencalulo.wishwait.presentation.wins

import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import java.util.Calendar

enum class DayStatus { NONE, MINDFUL, MISSED }

data class DayCell(val dayStart: Long, val status: DayStatus)

private const val CALENDAR_WINDOW_DAYS = 30

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
        val status = when {
            decisions.any { it.boughtMindfully || it.status == WishStatus.LET_GO } -> DayStatus.MINDFUL
            decisions.any { it.boughtEarly } -> DayStatus.MISSED
            else -> DayStatus.NONE
        }
        DayCell(day, status)
    }
}
