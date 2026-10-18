package com.karencalulo.wishwait.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karencalulo.wishwait.WishWaitApp
import com.karencalulo.wishwait.domain.model.BuyAdvisor
import com.karencalulo.wishwait.domain.model.WishStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val wishId = intent.getStringExtra(EXTRA_WISH_ID) ?: return
        val type = ReminderType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) }
            ?: ReminderType.END
        val container = (context.applicationContext as WishWaitApp).container
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val wish = container.wishRepository.getWish(wishId)
                val profile = container.profileRepository.current()
                val stillRelevant = wish != null && when (type) {
                    ReminderType.MIDPOINT, ReminderType.END -> wish.status == WishStatus.WAITING
                    ReminderType.REGRET_CHECK -> wish.status == WishStatus.BOUGHT && wish.gladRating == null
                    ReminderType.PAYMENT_DUE -> wish.status == WishStatus.BOUGHT && wish.payment?.isComplete == false
                }
                if (wish != null && stillRelevant && profile.remindersEnabled) {
                    // "Time to decide" carries the same best-day tip as the wish screen and widget.
                    val advice = if (type == ReminderType.END) {
                        BuyAdvisor.adviseAll(container.wishRepository.getWishes(), profile, System.currentTimeMillis())[wish.id]
                    } else {
                        null
                    }
                    NotificationHelper.showReminder(context, wish, type, advice)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.karencalulo.wishwait.action.REMIND"
        const val EXTRA_WISH_ID = "wish_id"
        const val EXTRA_TYPE = "type"
    }
}

/** Alarms don't survive a reboot — re-schedule cooldowns, regret checks and payment reminders. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as WishWaitApp).container
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                container.wishRepository.getWishes()
                    .filter { it.status != WishStatus.LET_GO }
                    .forEach { container.reminderScheduler.schedule(it) }
                val profile = container.profileRepository.current()
                container.eveScheduler.scheduleNext(profile.paydays, profile.paydaySales)
                container.backupReminderScheduler.schedule(
                    com.karencalulo.wishwait.domain.model.BackupRules.nextReminderAt(container.backupStatusRepository.current()),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
