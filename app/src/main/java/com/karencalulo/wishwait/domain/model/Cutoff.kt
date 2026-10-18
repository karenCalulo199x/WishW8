package com.karencalulo.wishwait.domain.model

import java.util.Calendar
import java.util.TimeZone

/** One pay period: from a payday (start of day) up to, not including, the next payday. */
data class Cutoff(val start: Long, val end: Long) {
    operator fun contains(t: Long): Boolean = t >= start && t < end
}

/**
 * Wants bought through WishWait in one cutoff, against the person's line
 * ([Strictness.wantsShare] of one cutoff's pay). Needs never count.
 * Only knows what went through WishWait — the wording in the UI says so.
 */
data class CutoffSpending(
    val cutoff: Cutoff,
    /** Monthly income ÷ number of paydays (the whole month when no payday is set). */
    val cutoffPay: Double,
    val spent: Double,
    val lineShare: Double,
    /** No payday set: the cutoff is the calendar month (the UI says "this month"). */
    val byMonth: Boolean = false,
) {
    val hasPay: Boolean get() = cutoffPay > 0
    val line: Double get() = cutoffPay * lineShare
    val share: Double get() = shareWith(0.0)

    fun shareWith(extra: Double): Double = if (hasPay) (spent + extra) / cutoffPay else 0.0

    /** Would buying [extra] more push wants past the line? Small tolerance for rounding. */
    fun isOverWith(extra: Double): Boolean = hasPay && spent + extra > line + 0.005
}

object CutoffCalculator {

    fun cutoffPay(profile: UserProfile): Double {
        if (profile.monthlyIncome <= 0) return 0.0
        val count = profile.paydays.distinct().size
        return if (count == 0) profile.monthlyIncome else profile.monthlyIncome / count
    }

    /** The cutoff that contains [t]. Without paydays, the calendar month is the cutoff. */
    fun cutoffAt(t: Long, paydays: List<Int>, zone: TimeZone = TimeZone.getDefault()): Cutoff {
        val start = (if (paydays.isEmpty()) null else SpendingCalendar.lastPayday(t, paydays, zone))
            ?: monthStart(t, zone)
        val nextDay = BuyAdvisor.startOfDay(start + DAY_MS + DAY_MS / 2, zone)
        val end = if (paydays.isEmpty()) {
            Calendar.getInstance(zone).apply {
                timeInMillis = start
                add(Calendar.MONTH, 1)
            }.timeInMillis
        } else {
            BuyAdvisor.paydayOnOrAfter(nextDay, paydays, zone)
        }
        return Cutoff(start, end)
    }

    /**
     * Wants spent in [cutoff]: items bought in it (full price), or for items on an
     * installment plan, only the payments due in it — never both, so nothing is counted twice.
     */
    fun spentIn(wishes: List<Wish>, cutoff: Cutoff): Double =
        wishes.filter { it.status == WishStatus.BOUGHT && !it.isNeed }.sumOf { wish ->
            val plan = wish.payment
            when {
                plan != null -> (0 until plan.months).count { plan.dueAt(it) in cutoff } * plan.monthlyPayment
                (wish.decidedAt ?: Long.MIN_VALUE) in cutoff -> wish.price
                else -> 0.0
            }
        }

    fun of(wishes: List<Wish>, profile: UserProfile, now: Long, zone: TimeZone = TimeZone.getDefault()): CutoffSpending =
        forCutoff(wishes, profile, cutoffAt(now, profile.paydays, zone))

    fun forCutoff(wishes: List<Wish>, profile: UserProfile, cutoff: Cutoff): CutoffSpending = CutoffSpending(
        cutoff = cutoff,
        cutoffPay = cutoffPay(profile),
        spent = spentIn(wishes, cutoff),
        lineShare = profile.strictness.wantsShare,
        byMonth = profile.paydays.isEmpty(),
    )

    private fun monthStart(t: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = t
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
