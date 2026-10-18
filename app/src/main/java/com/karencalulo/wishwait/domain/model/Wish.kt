package com.karencalulo.wishwait.domain.model

import java.util.Calendar

const val DAY_MS = 86_400_000L
const val HOUR_MS = 3_600_000L
const val REGRET_CHECK_DAYS = 14

enum class WishStatus { WAITING, BOUGHT, LET_GO }

enum class Category(val emoji: String) {
    GADGETS("📱"),
    FASHION("👟"),
    HOME("🛋️"),
    FOOD("🍜"),
    FUN("🎮"),
    TRAVEL("✈️"),
    BEAUTY("💄"),
    VEHICLE("🚗"),
    OTHER("🎁"),
}

/** Why the person wants it. "Need" reasons shorten the cooldown. Labels live in strings.xml. */
enum class Reason(val emoji: String, val isNeed: Boolean) {
    REPLACE_BROKEN("🔧", true),
    NEEDED_FOR_WORK("💼", true),
    UPGRADE("⬆️", false),
    SAW_AN_AD("📣", false),
    ON_SALE("🏷️", false),
    REWARD("🏆", false),
    EVERYONE_HAS_IT("👀", false),
    JUST_WANT_IT("✨", false),
}

/**
 * How the person feels right now. Risky moods (the HALT idea: hungry, angry,
 * lonely, tired — plus boredom and hype) are when impulse buys happen.
 */
enum class Mood(val emoji: String, val isRisky: Boolean) {
    CALM("😌", false),
    HAPPY("😊", false),
    EXCITED("🤩", true),
    BORED("🥱", true),
    STRESSED("😣", true),
    SAD("😔", true),
    TIRED("😴", true),
    HUNGRY("🍔", true),
}

data class DesireCheck(val timestamp: Long, val level: Int)

data class Wish(
    val id: String,
    val name: String,
    val price: Double,
    val category: Category,
    val reasons: Set<Reason>,
    val moods: Set<Mood>,
    val note: String,
    val createdAt: Long,
    val cooldownDays: Int,
    val cooldownEndsAt: Long,
    val status: WishStatus,
    val decidedAt: Long? = null,
    val desireLog: List<DesireCheck> = emptyList(),
    val gladRating: Boolean? = null,
    /** After purchase: one timestamp per logged use. */
    val usageLog: List<Long> = emptyList(),
    /** After purchase: "worth it" once each use costs this much or less. Null = 1 hour of work. */
    val worthTargetPerUse: Double? = null,
    /** After purchase: installment plan being paid off, if any. */
    val payment: PaymentPlan? = null,
    /** Income and work hours when the wish was added, so later raises don't rewrite history. */
    val incomeAtCreation: Double? = null,
    val hoursAtCreation: Int? = null,
    /** Store link from "Share to WishWait". Hidden while cooling down — out of sight, out of mind. */
    val link: String? = null,
    val store: String? = null,
) {
    /** The profile as it was when this wish was added (falls back to [current] for old data). */
    fun profileAtCreation(current: UserProfile): UserProfile = current.copy(
        monthlyIncome = incomeAtCreation?.takeIf { it > 0 } ?: current.monthlyIncome,
        hoursPerMonth = hoursAtCreation?.takeIf { it > 0 } ?: current.hoursPerMonth,
    )

    /** What the purchase really costs, including installment interest. */
    val costBasis: Double get() = payment?.totalPaid ?: price

    /** When we ask "Still happy with it?" */
    val regretCheckAt: Long? get() = decidedAt?.let { it + REGRET_CHECK_DAYS * DAY_MS }

    /** Bought before the cooldown finished — counted as an impulse buy. */
    val boughtEarly: Boolean
        get() = status == WishStatus.BOUGHT && (decidedAt ?: 0L) < cooldownEndsAt

    /** Bought after waiting it out (or when no wait was needed). */
    val boughtMindfully: Boolean
        get() = status == WishStatus.BOUGHT && !boughtEarly

    val isDecided: Boolean get() = status != WishStatus.WAITING

    /** Needs don't count toward the wants line per cutoff. */
    val isNeed: Boolean get() = isNeedPurchase(reasons)

    fun isReady(now: Long): Boolean = status == WishStatus.WAITING && now >= cooldownEndsAt

    fun remainingMillis(now: Long): Long = (cooldownEndsAt - now).coerceAtLeast(0L)

    fun progress(now: Long): Float {
        val total = cooldownEndsAt - createdAt
        if (total <= 0L) return 1f
        return ((now - createdAt).toFloat() / total).coerceIn(0f, 1f)
    }

    val firstDesire: Int? get() = desireLog.firstOrNull()?.level
    val latestDesire: Int? get() = desireLog.lastOrNull()?.level

    /** How many levels the desire has fallen since the first check-in (never negative). */
    val desireDrop: Int
        get() {
            val first = firstDesire ?: return 0
            val lowest = desireLog.minOfOrNull { it.level } ?: return 0
            return (first - lowest).coerceAtLeast(0)
        }
}

data class PaymentPlan(
    val months: Int,
    val monthlyPayment: Double,
    val firstDueAt: Long,
    val paidCount: Int = 0,
) {
    val isComplete: Boolean get() = paidCount >= months
    val totalPaid: Double get() = monthlyPayment * months
    val paidSoFar: Double get() = monthlyPayment * paidCount.coerceAtMost(months)
    val remaining: Double get() = monthlyPayment * (months - paidCount).coerceAtLeast(0)

    /** Due date of payment [index] (0-based), same day each calendar month. */
    fun dueAt(index: Int): Long = Calendar.getInstance().apply {
        timeInMillis = firstDueAt
        add(Calendar.MONTH, index)
    }.timeInMillis

    val nextDueAt: Long? get() = if (isComplete) null else dueAt(paidCount)
}

/**
 * [multiplier] scales cooldowns. [wantsShare] is the line for wants per cutoff
 * (share of one cutoff's pay), around the 50/30/20 rule's 30% for wants.
 */
enum class Strictness(val multiplier: Double, val wantsShare: Double) {
    CHILL(0.5, 0.40),
    BALANCED(1.0, 0.30),
    STRICT(1.5, 0.20),
}

/** Reasons shown for each choice: needs say why they're needed, wants say why they're wanted. */
fun reasonsFor(isNeed: Boolean): List<Reason> = Reason.entries.filter { it.isNeed == isNeed }

/** Switching need ↔ want keeps only the reasons that belong to the new choice. */
fun reasonsAfterSwitch(reasons: Set<Reason>, toNeed: Boolean): Set<Reason> = reasons.filter { it.isNeed == toNeed }.toSet()

/** A real need (broken, for work) — unless "just want it" / "everyone has it" is also picked. */
fun isNeedPurchase(reasons: Set<Reason>): Boolean =
    reasons.isNotEmpty() && reasons.any { it.isNeed } &&
        reasons.none { it == Reason.JUST_WANT_IT || it == Reason.EVERYONE_HAS_IT }

data class UserProfile(
    val monthlyIncome: Double = 0.0,
    val hoursPerMonth: Int = DEFAULT_HOURS_PER_MONTH,
    val monthlySavings: Double = 0.0,
    val currencyCode: String = "USD",
    val strictness: Strictness = Strictness.BALANCED,
    val onboarded: Boolean = false,
    val remindersEnabled: Boolean = true,
    /** Read name/price from a shared product link when the share text lacks them. */
    val fetchLinkDetails: Boolean = true,
    /** Days of the month you get paid (e.g. [15, 31] = 15th & end of month). Empty = not set. */
    val paydays: List<Int> = emptyList(),
    /** Which shop Payday Sales count as sale days (15 and/or 30). Double dates always count. */
    val paydaySales: List<Int> = DEFAULT_PAYDAY_SALES,
    /** Thank-you tips sent to Ollie. Only shows a small badge; nothing is ever locked. */
    val tipsGiven: Int = 0,
) {
    val hourlyRate: Double
        get() = if (hoursPerMonth > 0 && monthlyIncome > 0) monthlyIncome / hoursPerMonth else 0.0

    companion object {
        const val DEFAULT_HOURS_PER_MONTH = 160
        const val HOURS_PER_WORKDAY = 8.0
    }
}
