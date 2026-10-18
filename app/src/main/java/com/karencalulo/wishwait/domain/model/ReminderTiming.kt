package com.karencalulo.wishwait.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Keeps notifications out of sleeping hours. Reminders are only ever moved
 * later, never earlier, so a cooldown still lasts at least as long as promised.
 */
object ReminderTiming {

    const val DAY_START_HOUR = 9
    const val DAY_END_HOUR = 21

    /** Same moment if it's daytime; otherwise the next 9:00 AM. */
    fun friendly(at: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = at }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when {
            hour < DAY_START_HOUR -> cal.atHour(DAY_START_HOUR)
            hour >= DAY_END_HOUR -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.atHour(DAY_START_HOUR)
            }
            else -> at
        }
    }

    /** 9:00 AM on the same calendar day (used for payment due dates). */
    fun morningOf(at: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply { timeInMillis = at }.atHour(DAY_START_HOUR)

    private fun Calendar.atHour(hour: Int): Long {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        return timeInMillis
    }
}
