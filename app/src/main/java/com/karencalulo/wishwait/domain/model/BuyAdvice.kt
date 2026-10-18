package com.karencalulo.wishwait.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Ollie's "best day to buy" tip for a wish you still really want.
 * Only a suggestion: it never unlocks a wish early or blocks one.
 */
sealed interface BuyAdvice {
    /** A sale is coming and it isn't squeezed against payday — buy then if you still want it. */
    data class BuyOnSale(
        val sale: SaleEvent,
        val saleAt: Long,
        val daysAway: Int,
        val rightAfterPayday: Boolean,
        val daysWanted: Int,
    ) : BuyAdvice

    /** The sale falls in the tight last days before payday — wait for payday instead. */
    data class WaitForPayday(
        val sale: SaleEvent,
        val paydayAt: Long,
        val nextSale: SaleEvent,
        val daysWanted: Int,
    ) : BuyAdvice

    /** The want has faded — a sale is no reason to buy. */
    data class LetItGo(val from: Int, val to: Int) : BuyAdvice

    /**
     * Another wish is lined up for the same day, and buying both would push wants past
     * the line for that cutoff — buy this one in the next cutoff instead.
     */
    data class SpreadOut(
        val sharedDay: Long,
        val otherName: String,
        /** Wants share of that cutoff's pay if both were bought. */
        val combinedShare: Double,
        val lineShare: Double,
        /** Start of the next cutoff (next payday, or the 1st of next month). */
        val buyFrom: Long,
        val daysWanted: Int,
        /** No payday set: cutoffs are calendar months. */
        val byMonth: Boolean = false,
    ) : BuyAdvice
}

/** The day a tip points at, or null for "let it go". */
val BuyAdvice.targetDay: Long?
    get() = when (this) {
        is BuyAdvice.BuyOnSale -> saleAt
        is BuyAdvice.WaitForPayday -> paydayAt
        is BuyAdvice.SpreadOut -> buyFrom
        is BuyAdvice.LetItGo -> null
    }

/** A notification for the evening before a sale or payday. */
data class EveNudge(val wish: Wish, val sale: SaleEvent?)

object BuyAdvisor {

    /** Wish is ready, or will be within this many days. */
    const val NEAR_READY_DAYS = 3
    /** Only talk about a sale that comes within this many days of the wait ending. */
    const val SALE_HORIZON_DAYS = 7
    /** The last days before payday, when money is tightest. */
    const val LEAN_DAYS = 3
    /** A sale this many days after payday counts as "right after payday". */
    const val AFTER_PAYDAY_DAYS = 7
    /** One payday nudge per wish: only if it became ready within this many days. */
    const val PAYDAY_NUDGE_DAYS = 7

    /** Still wanted at 4–5 out of 5, or no lower than when it was added. */
    fun reallyWants(wish: Wish): Boolean {
        val first = wish.firstDesire ?: return false
        val latest = wish.latestDesire ?: return false
        return latest >= 4 || latest >= first
    }

    fun advise(
        wish: Wish,
        paydays: List<Int>,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
        paydaySales: List<Int> = emptyList(),
    ): BuyAdvice? {
        if (wish.status != WishStatus.WAITING) return null
        val today = startOfDay(now, zone)
        val readyDay = startOfDay(maxOf(now, wish.cooldownEndsAt), zone)
        if (daysBetween(today, readyDay) > NEAR_READY_DAYS) return null

        val (sale, saleAt) = saleOnOrAfter(readyDay, zone, paydaySales)
        if (daysBetween(readyDay, saleAt) > SALE_HORIZON_DAYS) return null

        val first = wish.firstDesire ?: return null
        val latest = wish.latestDesire ?: return null
        // "Let it go" is for wants; a real need just gets timing tips.
        if (!wish.isNeed && wish.desireLog.size >= 2 && latest < first) return BuyAdvice.LetItGo(first, latest)
        if (!wish.isNeed && !reallyWants(wish)) return null

        val daysWanted = daysBetween(startOfDay(wish.createdAt, zone), today).coerceAtLeast(0)
        val daysAway = daysBetween(today, saleAt)
        if (paydays.isEmpty()) return BuyAdvice.BuyOnSale(sale, saleAt, daysAway, false, daysWanted)

        val payday = paydayOnOrAfter(saleAt, paydays, zone)
        val untilPayday = daysBetween(saleAt, payday)
        if (untilPayday in 1..LEAN_DAYS) {
            val next = SpendingCalendar.nextSaleAfter(saleAt + DAY_MS / 2, zone, paydaySales)
            return BuyAdvice.WaitForPayday(sale, payday, next, daysWanted)
        }
        val last = SpendingCalendar.lastPayday(saleAt, paydays, zone)
        val afterPayday = last != null && daysBetween(last, saleAt) <= AFTER_PAYDAY_DAYS
        return BuyAdvice.BuyOnSale(sale, saleAt, daysAway, afterPayday, daysWanted)
    }

    /**
     * Tips for every waiting wish at once, so wishes lined up for the same day can be spread out:
     * the one you've wanted longest keeps the day; any other that would push wants past the
     * line for that cutoff is moved to the next cutoff. Needs are never moved.
     */
    fun adviseAll(
        wishes: List<Wish>,
        profile: UserProfile,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): Map<String, BuyAdvice> {
        val tips = wishes.mapNotNull { w ->
            advise(w, profile.paydays, now, zone, profile.paydaySales)?.let { w to it }
        }
        val result = tips.associate { (w, a) -> w.id to a }.toMutableMap()
        val pay = CutoffCalculator.cutoffPay(profile)
        if (pay <= 0) return result
        val line = pay * profile.strictness.wantsShare

        tips.filter { (w, _) -> !w.isNeed }
            .mapNotNull { (w, a) -> a.targetDay?.let { day -> Triple(day, w, a) } }
            .groupBy { it.first }
            .forEach { (day, group) ->
                if (group.size < 2) return@forEach
                val cutoff = CutoffCalculator.cutoffAt(day, profile.paydays, zone)
                var total = CutoffCalculator.spentIn(wishes, cutoff)
                var keeper: Wish? = null
                group.sortedWith(compareBy({ it.second.createdAt }, { it.second.id })).forEach { (_, w, a) ->
                    val first = keeper
                    if (first == null) {
                        keeper = w
                        total += w.price
                    } else if (total + w.price > line + 0.005) {
                        result[w.id] = BuyAdvice.SpreadOut(
                            sharedDay = day,
                            otherName = first.name,
                            combinedShare = (total + w.price) / pay,
                            lineShare = profile.strictness.wantsShare,
                            buyFrom = cutoff.end,
                            daysWanted = a.daysWanted ?: 0,
                            byMonth = profile.paydays.isEmpty(),
                        )
                    } else {
                        total += w.price
                    }
                }
            }
        return result
    }

    private val BuyAdvice.daysWanted: Int?
        get() = when (this) {
            is BuyAdvice.BuyOnSale -> daysWanted
            is BuyAdvice.WaitForPayday -> daysWanted
            is BuyAdvice.SpreadOut -> daysWanted
            is BuyAdvice.LetItGo -> null
        }

    /**
     * Who to nudge this evening. Sale eve: wishes whose best day is tomorrow's sale.
     * Payday eve: real wants that are ready by tomorrow and became ready recently
     * (so a wish left undecided isn't nudged every payday), plus wishes moved to this payday.
     * Wishes moved to a later cutoff are left alone.
     */
    fun eveNudges(
        wishes: List<Wish>,
        profile: UserProfile,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): List<EveNudge> {
        val paydays = profile.paydays
        val tomorrow = startOfDay(now, zone) + DAY_MS
        val tomorrowStart = startOfDay(tomorrow + DAY_MS / 2, zone) // DST-safe
        val saleTomorrow = SpendingCalendar.saleOn(tomorrowStart + DAY_MS / 2, zone, profile.paydaySales)
        val paydayTomorrow = paydays.isNotEmpty() && paydayOnOrAfter(tomorrowStart, paydays, zone) == tomorrowStart
        val tips = adviseAll(wishes, profile, now, zone)

        return wishes.filter { it.status == WishStatus.WAITING }.mapNotNull { wish ->
            val advice = tips[wish.id]
            when {
                advice is BuyAdvice.SpreadOut ->
                    if (paydayTomorrow && advice.buyFrom == tomorrowStart) EveNudge(wish, null) else null
                saleTomorrow != null && advice is BuyAdvice.BuyOnSale && advice.saleAt == tomorrowStart ->
                    EveNudge(wish, saleTomorrow)
                paydayTomorrow && advice !is BuyAdvice.LetItGo && reallyWants(wish) &&
                    wish.cooldownEndsAt <= tomorrowStart + DAY_MS &&
                    wish.cooldownEndsAt >= tomorrowStart - PAYDAY_NUDGE_DAYS * DAY_MS ->
                    EveNudge(wish, null)
                else -> null
            }
        }
    }

    /** Start of the next day (after [now]) that is a sale or a payday. */
    fun nextEveTarget(
        now: Long,
        paydays: List<Int>,
        zone: TimeZone = TimeZone.getDefault(),
        paydaySales: List<Int> = emptyList(),
    ): Long {
        val tomorrow = startOfDay(startOfDay(now, zone) + DAY_MS + DAY_MS / 2, zone)
        val sale = saleOnOrAfter(tomorrow, zone, paydaySales).second
        val payday = if (paydays.isEmpty()) null else paydayOnOrAfter(tomorrow, paydays, zone)
        return listOfNotNull(sale, payday).min()
    }

    fun saleOnOrAfter(dayStart: Long, zone: TimeZone, paydaySales: List<Int> = emptyList()): Pair<SaleEvent, Long> {
        val c = Calendar.getInstance(zone).apply { timeInMillis = dayStart }
        repeat(40) {
            SpendingCalendar.saleOn(c.timeInMillis, zone, paydaySales)?.let { return it to c.timeInMillis }
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        error("no sale within 40 days")
    }

    /** Start of the first payday on or after [dayStart]. "31" means the month's last day. */
    fun paydayOnOrAfter(dayStart: Long, paydays: List<Int>, zone: TimeZone): Long {
        val c = Calendar.getInstance(zone).apply { timeInMillis = dayStart }
        repeat(62) {
            val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            if (paydays.any { it.coerceIn(1, max) == day }) return c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        error("no payday within 62 days")
    }

    fun startOfDay(t: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Whole calendar days from one day start to another (DST-safe rounding). */
    fun daysBetween(fromDay: Long, toDay: Long): Int = Math.round((toDay - fromDay).toDouble() / DAY_MS).toInt()
}
