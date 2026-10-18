package com.karencalulo.wishwait.presentation.common

import android.content.res.Resources
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.BuyAdvice
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.Tier
import com.karencalulo.wishwait.domain.model.Verdict
import com.karencalulo.wishwait.domain.model.Wish

/**
 * Ollie the owl — the honest friend. Never preachy, never shaming.
 * All copy is hand-written and rule-based (no AI), and lives in strings.xml.
 */
object FriendVoice {

    const val AVATAR = "🦉"

    fun name(res: Resources): String = res.getString(R.string.friend_name)

    fun reaction(tier: Tier): String = when (tier) {
        Tier.TREAT -> "😄"
        Tier.SMALL -> "🙂"
        Tier.MEDIUM -> "🤔"
        Tier.LARGE -> "😮"
        Tier.BIG -> "😳"
        Tier.HUGE -> "😱"
        Tier.MASSIVE -> "🫨"
    }

    fun headline(res: Resources, v: Verdict): String = res.getString(
        when (v.impact.tier) {
            Tier.TREAT -> R.string.headline_treat
            Tier.SMALL -> R.string.headline_small
            Tier.MEDIUM -> R.string.headline_medium
            Tier.LARGE -> R.string.headline_large
            Tier.BIG -> R.string.headline_big
            Tier.HUGE -> R.string.headline_huge
            Tier.MASSIVE -> R.string.headline_massive
        },
    )

    fun message(
        res: Resources,
        v: Verdict,
        itemName: String,
        category: Category? = null,
        money: MoneyFormatter? = null,
    ): String {
        val name = itemName.ifBlank { res.getString(R.string.verdict_this) }
        val share = Fmt.incomeShare(v.impact.incomeRatio)
        val hours = Fmt.hours(res, v.impact.workHours)
        val parts = mutableListOf<String>()

        val base = when (v.impact.tier) {
            Tier.TREAT -> R.string.msg_treat
            Tier.SMALL -> R.string.msg_small
            Tier.MEDIUM -> R.string.msg_medium
            Tier.LARGE -> R.string.msg_large
            Tier.BIG -> R.string.msg_big
            Tier.HUGE, Tier.MASSIVE -> R.string.msg_huge
        }
        parts += res.getString(base, name, share, hours)

        if (v.riskyMoods.isNotEmpty()) {
            val moods = v.riskyMoods.joinToString(", ") { res.getString(it.labelRes).lowercase() }
            parts += res.getString(R.string.msg_moods, moods, Fmt.days(res, v.extraDaysForMood))
        }
        if (v.onSale) parts += res.getString(R.string.msg_sale)
        if (v.socialPressure) parts += res.getString(R.string.msg_social)
        v.history?.takeIf { it.bonusDays > 0 }?.let { h ->
            val what = category?.let { res.getString(it.labelRes).lowercase() } ?: ""
            parts += res.getString(
                R.string.msg_history,
                h.regretted,
                h.loved + h.regretted,
                what,
                Fmt.days(res, h.bonusDays),
            )
        }
        v.saleToday?.let { sale ->
            parts += res.getString(R.string.msg_sale_day, sale.displayName(res), Fmt.days(res, v.extraDaysForTiming))
        }
        if (v.paydayWindow && v.saleToday == null) {
            parts += res.getString(R.string.msg_payday, Fmt.days(res, v.extraDaysForTiming))
        }
        cutoffLine(res, v, money)?.let { parts += it }
        if (v.isNeed) parts += res.getString(R.string.msg_need)
        v.impact.monthsToSave?.let { months ->
            if (months >= 1) parts += res.getString(R.string.msg_months_to_save, Fmt.months(res, months))
        }
        return parts.joinToString("\n\n")
    }

    fun tips(res: Resources, v: Verdict): List<String> = buildList {
        add(R.string.tip_mute_store)
        add(R.string.tip_three_things)
        if (v.impact.tier >= Tier.MEDIUM) add(R.string.tip_second_hand)
        add(R.string.tip_already_own)
        if (v.impact.tier >= Tier.LARGE) add(R.string.tip_save_now)
        add(R.string.tip_delete_cards)
    }.map { res.getString(it) }

    private val reflections = listOf(
        R.string.reflect_1, R.string.reflect_2, R.string.reflect_3, R.string.reflect_4,
        R.string.reflect_5, R.string.reflect_6, R.string.reflect_7, R.string.reflect_8,
    )

    /** One reflection question per day per wish — keeps check-ins fresh. */
    fun reflection(res: Resources, wish: Wish, now: Long): String {
        val day = (now / 86_400_000L).toInt()
        return res.getString(reflections[Math.floorMod(day + wish.id.hashCode(), reflections.size)])
    }

    fun desireEmoji(level: Int): String = when (level) {
        1 -> "😐"
        2 -> "🙂"
        3 -> "😊"
        4 -> "😍"
        else -> "🤩"
    }

    fun desireInsight(res: Resources, wish: Wish): String? {
        val first = wish.firstDesire ?: return null
        val latest = wish.latestDesire ?: return null
        if (wish.desireLog.size < 2) return res.getString(R.string.desire_insight_start)
        return when {
            latest < first -> res.getString(
                R.string.desire_insight_drop,
                res.getString(desireLabelRes(first)),
                res.getString(desireLabelRes(latest)),
            )
            latest > first -> res.getString(R.string.desire_insight_rise)
            else -> res.getString(R.string.desire_insight_steady)
        }
    }

    /** Title + message for the "best day to buy" tip. */
    fun buyAdvice(res: Resources, advice: BuyAdvice): Pair<String, String> {
        fun intro(days: Int) = if (days > 0) {
            res.getString(R.string.buy_intro_days, Fmt.days(res, days))
        } else {
            res.getString(R.string.buy_intro_now)
        }
        return when (advice) {
            is BuyAdvice.BuyOnSale -> {
                val whenText = when (advice.daysAway) {
                    0 -> res.getString(R.string.buy_when_today)
                    1 -> res.getString(R.string.buy_when_tomorrow)
                    else -> res.getString(R.string.buy_when_on, Fmt.date(advice.saleAt))
                }
                val body = res.getString(
                    if (advice.rightAfterPayday) R.string.buy_sale_after_payday else R.string.buy_sale,
                    advice.sale.displayName(res),
                    whenText,
                )
                res.getString(R.string.buy_advice_title) to "${intro(advice.daysWanted)} $body"
            }
            is BuyAdvice.WaitForPayday -> res.getString(R.string.buy_advice_title) to
                "${intro(advice.daysWanted)} " + res.getString(
                    R.string.buy_wait_payday,
                    advice.sale.displayName(res),
                    Fmt.date(advice.paydayAt),
                    advice.nextSale.displayName(res),
                )
            is BuyAdvice.SpreadOut -> res.getString(R.string.buy_spread_title) to
                "${intro(advice.daysWanted)} " + res.getString(
                    if (advice.byMonth) R.string.buy_spread_month else R.string.buy_spread,
                    advice.otherName,
                    Fmt.percent(advice.combinedShare),
                    Fmt.percent(advice.lineShare),
                    Fmt.date(advice.buyFrom),
                )
            is BuyAdvice.LetItGo -> res.getString(R.string.buy_let_go_title) to res.getString(
                R.string.buy_let_go,
                desireEmoji(advice.from),
                desireEmoji(advice.to),
            )
        }
    }

    /**
     * "Wants this cutoff" line. Shown when other wants were already bought this cutoff,
     * or when this item alone crosses the line. Honest about only seeing WishWait buys.
     */
    private fun cutoffLine(res: Resources, v: Verdict, money: MoneyFormatter?): String? {
        val share = v.cutoffShareWith ?: return null
        val line = v.cutoffLineShare ?: return null
        val before = v.cutoffSpentBefore ?: return null
        if (before <= 0.0 && !v.overCutoffLine) return null
        val total = before + v.impact.price
        val amount = money?.format(total) ?: Fmt.number(total)
        val intro = res.getString(
            if (v.cutoffByMonth) R.string.msg_cutoff_intro_month else R.string.msg_cutoff_intro,
            amount,
            Fmt.percent(share),
        )
        val tail = when {
            !v.overCutoffLine -> res.getString(R.string.msg_cutoff_under, Fmt.percent(line))
            v.extraDaysForCutoff > 0 ->
                res.getString(R.string.msg_cutoff_over_days, Fmt.percent(line), Fmt.days(res, v.extraDaysForCutoff))
            else -> res.getString(R.string.msg_cutoff_over, Fmt.percent(line))
        }
        return "$intro $tail"
    }
}
