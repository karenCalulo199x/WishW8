package com.karencalulo.wishwait.domain.model

import java.util.Calendar
import java.util.TimeZone

enum class SaleKind { DOUBLE_DATE, PAYDAY_MID, PAYDAY_END }

/**
 * A shopping sale: a double date (1.1 … 12.12, the big four 9.9–12.12 are "mega"),
 * or the shops' Payday Sales on the 15th and the 30th (last day of February).
 */
data class SaleEvent(val month: Int, val day: Int, val kind: SaleKind = SaleKind.DOUBLE_DATE) {
    /** "10.10" for double dates. Payday-sale names come from strings.xml. */
    val label: String get() = "$month.$day"
    val isMega: Boolean get() = kind == SaleKind.DOUBLE_DATE && month >= 9
}

/** Days of the month of the shops' Payday Sales (15th and 30th). */
const val MID_MONTH_SALE_DAY = 15
const val END_MONTH_SALE_DAY = 30
val DEFAULT_PAYDAY_SALES = listOf(MID_MONTH_SALE_DAY, END_MONTH_SALE_DAY)

/** When money "feels" plentiful or apps push hardest — Ollie gets a bit stricter. */
data class SpendingContext(
    val inPaydayWindow: Boolean = false,
    val daysSincePayday: Int? = null,
    val saleToday: SaleEvent? = null,
    val saleTomorrow: SaleEvent? = null,
    val nextSale: SaleEvent? = null,
) {
    companion object {
        val NONE = SpendingContext()
    }
}

/**
 * Pure date logic for paydays and double-date sales.
 * Paydays are days of the month (1–31); a day past the month's end means "last day".
 */
object SpendingCalendar {

    /** First 3 days counting payday itself (day 0, 1, 2). */
    const val PAYDAY_WINDOW_DAYS = 3

    /** [paydaySales]: which Payday Sales (15 and/or 30) the person wants Ollie to watch. */
    fun saleOn(
        timeMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
        paydaySales: List<Int> = emptyList(),
    ): SaleEvent? {
        val c = Calendar.getInstance(zone).apply { timeInMillis = timeMillis }
        val month = c.get(Calendar.MONTH) + 1
        val day = c.get(Calendar.DAY_OF_MONTH)
        val lastDay = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        return when {
            month == day -> SaleEvent(month, day)
            MID_MONTH_SALE_DAY in paydaySales && day == MID_MONTH_SALE_DAY -> SaleEvent(month, day, SaleKind.PAYDAY_MID)
            END_MONTH_SALE_DAY in paydaySales && day == END_MONTH_SALE_DAY.coerceAtMost(lastDay) ->
                SaleEvent(month, day, SaleKind.PAYDAY_END)
            else -> null
        }
    }

    /** The next sale strictly after today. */
    fun nextSaleAfter(
        timeMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
        paydaySales: List<Int> = emptyList(),
    ): SaleEvent {
        val c = Calendar.getInstance(zone).apply { timeInMillis = timeMillis }
        repeat(40) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            saleOn(c.timeInMillis, zone, paydaySales)?.let { return it }
        }
        error("no sale within 40 days")
    }

    /** Start (midnight) of the most recent payday on or before [now], or null if none set. */
    fun lastPayday(now: Long, paydays: List<Int>, zone: TimeZone = TimeZone.getDefault()): Long? {
        if (paydays.isEmpty()) return null
        val today = startOfDay(now, zone)
        // Look back through this month and the previous one.
        val candidates = (0..1).flatMap { monthsBack ->
            paydays.map { payday -> paydayIn(today, monthsBack, payday, zone) }
        }
        return candidates.filter { it <= today }.maxOrNull()
    }

    fun context(
        now: Long,
        paydays: List<Int>,
        zone: TimeZone = TimeZone.getDefault(),
        paydaySales: List<Int> = emptyList(),
    ): SpendingContext {
        val last = lastPayday(now, paydays, zone)
        val since = last?.let { ((startOfDay(now, zone) - it + DAY_MS / 2) / DAY_MS).toInt() }
        return SpendingContext(
            inPaydayWindow = since != null && since < PAYDAY_WINDOW_DAYS,
            daysSincePayday = since,
            saleToday = saleOn(now, zone, paydaySales),
            saleTomorrow = saleOn(now + DAY_MS, zone, paydaySales),
            nextSale = nextSaleAfter(now, zone, paydaySales),
        )
    }

    private fun paydayIn(today: Long, monthsBack: Int, payday: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = today
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -monthsBack)
            set(Calendar.DAY_OF_MONTH, payday.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }.timeInMillis

    private fun startOfDay(t: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
