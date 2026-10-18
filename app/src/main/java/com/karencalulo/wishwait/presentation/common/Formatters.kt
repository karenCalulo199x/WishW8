package com.karencalulo.wishwait.presentation.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.karencalulo.wishwait.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.HOUR_MS
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

class MoneyFormatter(currencyCode: String) {

    val currency: Currency = runCatching { Currency.getInstance(currencyCode) }
        .getOrElse { Currency.getInstance("USD") }

    private val formatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = this@MoneyFormatter.currency
    }

    val symbol: String get() = currency.getSymbol(Locale.getDefault())

    fun format(value: Double): String {
        val whole = abs(value) >= 1000 || value == floor(value)
        val digits = if (whole) 0 else 2
        formatter.minimumFractionDigits = digits
        formatter.maximumFractionDigits = digits
        return formatter.format(value)
    }
}

val LocalMoney = staticCompositionLocalOf { MoneyFormatter("USD") }

object Fmt {
    private val number = NumberFormat.getNumberInstance(Locale.getDefault())

    fun number(value: Double, decimals: Int = 0): String {
        number.minimumFractionDigits = decimals
        number.maximumFractionDigits = decimals
        return number.format(value)
    }

    /** 5.0 -> "5.0×", 0.25 -> "25%" */
    fun incomeShare(ratio: Double): String =
        if (ratio >= 1.0) "${number(ratio, 1)}×" else "${number(ratio * 100, if (ratio < 0.01) 1 else 0)}%"

    fun incomeShareLabel(res: Resources, ratio: Double): String = res.getString(
        if (ratio >= 1.0) R.string.income_share_label_multiple else R.string.income_share_label_fraction,
    )

    fun hours(res: Resources, h: Double): String = when {
        h < 1 -> res.getString(R.string.unit_min, number(h * 60))
        h < 10 -> res.getString(R.string.unit_hours, number(h, 1))
        else -> res.getString(R.string.unit_hours, number(h))
    }

    fun days(res: Resources, d: Int): String = res.getQuantityString(R.plurals.days, d, d)

    fun monthsCount(res: Resources, m: Int): String = res.getQuantityString(R.plurals.months_count, m, m)

    fun months(res: Resources, m: Double): String = when {
        m < 1 -> res.getString(R.string.unit_days_text, number(m * 30.4))
        m < 12 -> res.getString(R.string.unit_months, number(m, 1))
        else -> res.getString(R.string.unit_years, number(m / 12, 1))
    }

    fun date(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(millis))

    /** "Oct 9, 2026, 6:12 PM". */
    fun dateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

    /** "Oct 15" — for tight spaces like the widget. */
    fun shortDate(millis: Long): String {
        val locale = Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd")
        return java.text.SimpleDateFormat(pattern, locale).format(Date(millis))
    }

    fun percent(fraction: Double): String =
        "${number(fraction * 100, if (fraction > 0 && fraction < 0.01) 1 else 0)}%"

    /** Editable text for a stored amount: "20000" or "1250.5". */
    fun plain(value: Double): String =
        if (value <= 0.0) "" else if (value == floor(value)) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    /** Parses user-typed money ("1,250.50") leniently. */
    fun parseAmount(text: String): Double? =
        text.replace(",", "").trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /** Keeps only digits and a single decimal point. */
    fun sanitizeAmount(text: String): String {
        val cleaned = text.filter { it.isDigit() || it == '.' }
        val firstDot = cleaned.indexOf('.')
        return if (firstDot < 0) cleaned.take(12)
        else (cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "").take(2)).take(15)
    }
}

data class Countdown(val days: Long, val hours: Long, val minutes: Long, val seconds: Long) {
    fun short(res: Resources): String = when {
        days > 0 -> res.getString(R.string.countdown_days_left, days.toInt(), hours.toInt())
        hours > 0 -> res.getString(R.string.countdown_hours_left, hours.toInt(), minutes.toInt())
        else -> res.getString(R.string.countdown_minutes_left, minutes.toInt(), seconds.toInt())
    }

    companion object {
        fun of(ms: Long): Countdown {
            val safe = ms.coerceAtLeast(0)
            return Countdown(
                days = safe / DAY_MS,
                hours = (safe % DAY_MS) / HOUR_MS,
                minutes = (safe % HOUR_MS) / 60_000L,
                seconds = (safe % 60_000L) / 1_000L,
            )
        }
    }
}

/** Resources for building translated text inside composables. */
@Composable
@ReadOnlyComposable
fun res(): Resources = LocalContext.current.resources

/** Current time that recomposes every [periodMs]. Drives live countdowns. */
@Composable
fun rememberNow(periodMs: Long = 1_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}
