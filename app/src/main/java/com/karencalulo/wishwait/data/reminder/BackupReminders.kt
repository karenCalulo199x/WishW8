package com.karencalulo.wishwait.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.karencalulo.wishwait.WishWaitApp
import com.karencalulo.wishwait.domain.model.BackupRules
import com.karencalulo.wishwait.domain.model.ReminderTiming
import com.karencalulo.wishwait.domain.repository.BackupReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One alarm for the weekly "back up your wishes" reminder (only when Drive backup is off). */
class AlarmBackupReminderScheduler(private val context: Context) : BackupReminderScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Long?) {
        val intent = pendingIntent()
        alarmManager.cancel(intent)
        if (at == null) return
        val friendly = ReminderTiming.friendly(maxOf(at, System.currentTimeMillis() + 60_000L))
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, friendly, intent)
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, BackupReminderReceiver::class.java).setAction(BackupReminderReceiver.ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val REQUEST_CODE = 7_207
    }
}

class BackupReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val container = (context.applicationContext as WishWaitApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val status = container.backupStatusRepository.current()
                val profile = container.profileRepository.current()
                val now = System.currentTimeMillis()
                if (profile.onboarded && BackupRules.reminderDue(status, now)) {
                    if (profile.remindersEnabled) NotificationHelper.showBackupReminder(context)
                    // Counting the reminder moves the next one a week ahead (the app re-arms the alarm).
                    container.backupStatusRepository.update { it.copy(lastReminderAt = now) }
                }
                container.backupReminderScheduler.schedule(BackupRules.nextReminderAt(container.backupStatusRepository.current()))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.karencalulo.wishwait.action.BACKUP_REMINDER"
    }
}
