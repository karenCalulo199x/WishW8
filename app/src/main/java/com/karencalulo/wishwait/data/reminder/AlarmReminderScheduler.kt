package com.karencalulo.wishwait.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.karencalulo.wishwait.domain.model.ReminderTiming
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.repository.ReminderScheduler

enum class ReminderType { MIDPOINT, END, REGRET_CHECK, PAYMENT_DUE }

/**
 * Uses inexact, battery-friendly alarms (no exact-alarm permission needed).
 * A reminder a few minutes late is fine for a 30-day cooldown.
 * All reminders land between 9 AM and 9 PM (see [ReminderTiming]).
 */
class AlarmReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(wish: Wish) {
        cancel(wish.id)
        val now = System.currentTimeMillis()
        when (wish.status) {
            WishStatus.WAITING -> {
                if (wish.cooldownEndsAt > now) {
                    set(wish.id, ReminderType.END, ReminderTiming.friendly(wish.cooldownEndsAt))
                }
                if (wish.cooldownDays >= MIDPOINT_MIN_DAYS) {
                    val mid = ReminderTiming.friendly(wish.createdAt + (wish.cooldownEndsAt - wish.createdAt) / 2)
                    if (mid > now && mid < wish.cooldownEndsAt) set(wish.id, ReminderType.MIDPOINT, mid)
                }
            }
            WishStatus.BOUGHT -> {
                // "Still happy with it?" two weeks later, until they answer.
                val check = wish.regretCheckAt
                if (wish.gladRating == null && check != null && check > now) {
                    set(wish.id, ReminderType.REGRET_CHECK, ReminderTiming.friendly(check))
                }
                // Next installment: 9 AM on the due date (or right away if that morning already passed today).
                val due = wish.payment?.nextDueAt
                if (due != null) {
                    val morning = ReminderTiming.morningOf(due)
                    when {
                        morning > now -> set(wish.id, ReminderType.PAYMENT_DUE, morning)
                        due > now -> set(wish.id, ReminderType.PAYMENT_DUE, now + 60_000L)
                    }
                }
            }
            WishStatus.LET_GO -> Unit
        }
    }

    override fun cancel(wishId: String) {
        ReminderType.entries.forEach { alarmManager.cancel(pendingIntent(wishId, it)) }
    }

    private fun set(wishId: String, type: ReminderType, at: Long) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(wishId, type))
    }

    private fun pendingIntent(wishId: String, type: ReminderType): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            // Unique data URI so each wish/type pair gets its own PendingIntent.
            .setData(Uri.parse("wishwait://reminder/$wishId/${type.name}"))
            .putExtra(ReminderReceiver.EXTRA_WISH_ID, wishId)
            .putExtra(ReminderReceiver.EXTRA_TYPE, type.name)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val MIDPOINT_MIN_DAYS = 7
    }
}
