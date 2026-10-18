package com.karencalulo.wishwait.presentation.common

import android.content.res.Resources
import androidx.annotation.StringRes
import com.karencalulo.wishwait.R

/** Common pay schedules — one tap, no math. */
enum class PaydayPreset(@StringRes val labelRes: Int, val days: List<Int>) {
    FIFTEENTH_AND_THIRTIETH(R.string.payday_15_30, listOf(15, 30)),
    END_OF_MONTH(R.string.payday_end, listOf(31)),
    FIFTEENTH(R.string.payday_15, listOf(15)),
    ;

    companion object {
        fun matching(days: List<Int>): PaydayPreset? = entries.firstOrNull { it.days == days.sorted() }
    }
}

fun paydayLabel(res: Resources, days: List<Int>): String {
    if (days.isEmpty()) return res.getString(R.string.payday_none)
    PaydayPreset.matching(days)?.let { return res.getString(it.labelRes) }
    return res.getString(R.string.payday_days, days.sorted().joinToString(" & "))
}
