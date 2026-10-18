package com.karencalulo.wishwait.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.karencalulo.wishwait.MainActivity
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.WishWaitApp
import com.karencalulo.wishwait.data.reminder.NotificationHelper
import com.karencalulo.wishwait.domain.model.BuyAdvice
import com.karencalulo.wishwait.domain.model.BuyAdvisor
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.HOUR_MS
import com.karencalulo.wishwait.domain.model.SpendingCalendar
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WidgetSummary
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.MoneyFormatter
import com.karencalulo.wishwait.presentation.common.displayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home screen widget: money kept, the next countdown, and "Ask Ollie first".
 * Plain RemoteViews (no extra library). Refreshed when wishes change and every 30 minutes.
 */
class WishWaitWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ADD = "open_add"

        /** Redraw every placed widget (call after wishes or settings change). */
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WishWaitWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val container = (context.applicationContext as WishWaitApp).container
            val wishes = container.wishRepository.getWishes()
            val profile = container.profileRepository.current()
            val views = build(context, wishes, profile, System.currentTimeMillis())
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context, wishes: List<Wish>, profile: UserProfile, now: Long): RemoteViews {
            val res = context.resources
            val summary = WidgetSummary.of(wishes, now)
            val views = RemoteViews(context.packageName, R.layout.widget_wishwait)

            views.setTextViewText(R.id.widget_kept_amount, MoneyFormatter(profile.currencyCode).format(summary.keptTotal))

            val next = summary.nextWish
            // Same "best day to buy" tip as the wish screen, so the widget never says something different.
            val advice = next?.let { BuyAdvisor.adviseAll(wishes, profile, now)[it.id] }
            val nextText = when {
                next == null -> res.getString(R.string.widget_empty)
                advice is BuyAdvice.LetItGo -> res.getString(R.string.widget_let_go, next.name)
                advice is BuyAdvice.SpreadOut -> res.getString(R.string.widget_spread, next.name, Fmt.shortDate(advice.buyFrom))
                advice is BuyAdvice.WaitForPayday ->
                    res.getString(R.string.widget_wait_payday, next.name, Fmt.shortDate(advice.paydayAt))
                advice is BuyAdvice.BuyOnSale -> res.getString(R.string.widget_best_day, next.name, advice.sale.displayName(res))
                summary.nextIsReady -> res.getString(R.string.widget_ready, next.name)
                else -> res.getString(R.string.widget_waiting, next.name, timeLeft(context, next.cooldownEndsAt - now))
            }
            views.setTextViewText(R.id.widget_next, nextText)
            val more = summary.waitingCount - 1
            if (more > 0) {
                views.setTextViewText(R.id.widget_more, res.getQuantityString(R.plurals.widget_more, more, more))
                views.setViewVisibility(R.id.widget_more, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_more, View.GONE)
            }

            val timing = SpendingCalendar.context(now, profile.paydays, paydaySales = profile.paydaySales)
            val saleText = when {
                timing.saleToday != null -> res.getString(R.string.widget_sale_today, timing.saleToday.displayName(res))
                timing.saleTomorrow != null -> res.getString(R.string.widget_sale_tomorrow, timing.saleTomorrow.displayName(res))
                else -> null
            }
            if (saleText != null) {
                views.setTextViewText(R.id.widget_sale, saleText)
                views.setViewVisibility(R.id.widget_sale, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_sale, View.GONE)
            }

            views.setOnClickPendingIntent(R.id.widget_root, open(context, REQUEST_HOME) {})
            views.setOnClickPendingIntent(R.id.widget_add, open(context, REQUEST_ADD) { putExtra(EXTRA_OPEN_ADD, true) })
            if (next != null) {
                views.setOnClickPendingIntent(
                    R.id.widget_next,
                    open(context, REQUEST_WISH) { putExtra(NotificationHelper.EXTRA_OPEN_WISH_ID, next.id) },
                )
            }
            return views
        }

        private fun timeLeft(context: Context, ms: Long): String {
            val res = context.resources
            return if (ms >= DAY_MS) {
                val days = ((ms + DAY_MS - 1) / DAY_MS).toInt()
                res.getQuantityString(R.plurals.widget_days_left, days, days)
            } else {
                val hours = ((ms + HOUR_MS - 1) / HOUR_MS).toInt().coerceAtLeast(1)
                res.getQuantityString(R.plurals.widget_hours_left, hours, hours)
            }
        }

        private fun open(context: Context, requestCode: Int, extras: Intent.() -> Unit): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                extras()
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val REQUEST_HOME = 8_101
        private const val REQUEST_ADD = 8_102
        private const val REQUEST_WISH = 8_103
    }
}
