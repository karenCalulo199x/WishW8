package com.karencalulo.wishwait.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karencalulo.wishwait.WishWaitApp
import com.karencalulo.wishwait.domain.model.BuyAdvisor
import com.karencalulo.wishwait.domain.model.DAY_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * One app-wide alarm at 7 PM on the evening before the next sale (double date or Payday Sale) or payday.
 * When it fires, Ollie nudges only the wishes you still really want (see [BuyAdvisor.eveNudges]).
 */
class EveReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("eve_reminders", Context.MODE_PRIVATE)

    fun scheduleNext(paydays: List<Int>, paydaySales: List<Int>, now: Long = System.currentTimeMillis()) {
        val zone = TimeZone.getDefault()
        val lastFired = prefs.getLong(KEY_LAST_TARGET, 0L)
        var from = now
        repeat(3) {
            val target = BuyAdvisor.nextEveTarget(from, paydays, zone, paydaySales)
            val eve = atHour(target - DAY_MS / 2, EVE_HOUR, zone)
            val eveEnd = atHour(target - DAY_MS / 2, LATEST_HOUR, zone)
            when {
                target == lastFired -> from = target + DAY_MS / 2 // already nudged for this day
                eve > now -> return set(eve, target)
                now < eveEnd -> return set(now + 60_000L, target) // it's the eve right now
                else -> from = target + DAY_MS / 2
            }
        }
    }

    fun markFired(target: Long) = prefs.edit().putLong(KEY_LAST_TARGET, target).apply()

    private fun set(at: Long, target: Long) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(target))
    }

    private fun pendingIntent(target: Long): PendingIntent {
        val intent = Intent(context, EveReminderReceiver::class.java)
            .setAction(EveReminderReceiver.ACTION_EVE)
            .putExtra(EveReminderReceiver.EXTRA_TARGET, target)
        // Same request code + action: a new schedule replaces the old one.
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun atHour(dayTime: Long, hour: Int, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = dayTime
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val EVE_HOUR = 19
        const val LATEST_HOUR = 21
        private const val REQUEST_CODE = 7_019
        private const val KEY_LAST_TARGET = "last_target"
    }
}

class EveReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EVE) return
        val target = intent.getLongExtra(EXTRA_TARGET, 0L)
        val container = (context.applicationContext as WishWaitApp).container
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val profile = container.profileRepository.current()
                val now = System.currentTimeMillis()
                if (profile.remindersEnabled) {
                    BuyAdvisor.eveNudges(container.wishRepository.getWishes(), profile, now)
                        .forEach { NotificationHelper.showEveNudge(context, it) }
                }
                container.eveScheduler.markFired(target)
                container.eveScheduler.scheduleNext(profile.paydays, profile.paydaySales, now)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EVE = "com.karencalulo.wishwait.action.EVE"
        const val EXTRA_TARGET = "target"
    }
}
