package com.karencalulo.wishwait.data.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.BuyAdvice
import com.karencalulo.wishwait.domain.model.EveNudge
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.common.displayName

object NotificationHelper {

    const val CHANNEL_ID = "cooldown_reminders"
    const val EXTRA_OPEN_WISH_ID = "open_wish_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Evening before a sale or payday, for a wish you still really want. */
    fun showEveNudge(context: Context, nudge: EveNudge) {
        val res = context.resources
        val wish = nudge.wish
        val (title, text) = if (nudge.sale != null) {
            res.getString(R.string.notif_sale_eve_title, nudge.sale.displayName(res)) to res.getString(R.string.notif_sale_eve_body, wish.name)
        } else {
            res.getString(R.string.notif_payday_eve_title) to res.getString(R.string.notif_payday_eve_body, wish.name)
        }
        post(context, wish, title, text, wish.id + "EVE")
    }

    /** Weekly nudge to save a backup (only when Google Drive backup is off). */
    @SuppressLint("MissingPermission")
    fun showBackupReminder(context: Context) {
        if (!canNotify(context)) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
            ?: return
        val contentIntent = PendingIntent.getActivity(
            context,
            BACKUP_NOTIFICATION_ID,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val res = context.resources
        val text = res.getString(R.string.notif_backup_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.notif_backup_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(BACKUP_NOTIFICATION_ID, notification)
    }

    private const val BACKUP_NOTIFICATION_ID = 7_208

    fun showReminder(context: Context, wish: Wish, type: ReminderType, advice: BuyAdvice? = null) {
        if (!canNotify(context)) return
        val res = context.resources
        val (title, text) = when (type) {
            ReminderType.MIDPOINT ->
                res.getString(R.string.notif_mid_title) to res.getString(R.string.notif_mid_body, wish.name)
            ReminderType.END -> {
                val waited = res.getQuantityString(R.plurals.days, wish.cooldownDays, wish.cooldownDays)
                val body = when (advice) {
                    is BuyAdvice.SpreadOut -> res.getString(R.string.notif_end_body_later, waited, Fmt.shortDate(advice.buyFrom))
                    is BuyAdvice.WaitForPayday -> res.getString(R.string.notif_end_body_later, waited, Fmt.shortDate(advice.paydayAt))
                    is BuyAdvice.BuyOnSale -> res.getString(R.string.notif_end_body_sale, waited, advice.sale.displayName(res))
                    is BuyAdvice.LetItGo -> res.getString(R.string.notif_end_body_faded, waited)
                    null -> res.getString(R.string.notif_end_body, waited)
                }
                res.getString(R.string.notif_end_title, wish.name) to body
            }
            ReminderType.REGRET_CHECK -> {
                val uses = wish.usageLog.size
                val usedLine = if (uses == 0) {
                    res.getString(R.string.notif_uses_none)
                } else {
                    res.getQuantityString(R.plurals.notif_uses, uses, uses)
                }
                res.getString(R.string.notif_regret_title, wish.name) to res.getString(R.string.notif_regret_body, usedLine)
            }
            ReminderType.PAYMENT_DUE -> {
                val plan = wish.payment
                val n = (plan?.paidCount ?: 0) + 1
                res.getString(R.string.notif_pay_title, wish.name) to
                    res.getString(R.string.notif_pay_body, n, plan?.months ?: n)
            }
        }

        post(context, wish, title, text, wish.id + type.name)
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, wish: Wish, title: String, text: String, tag: String) {
        if (!canNotify(context)) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_WISH_ID, wish.id)
            } ?: return
        val contentIntent = PendingIntent.getActivity(
            context,
            wish.id.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(tag.hashCode(), notification)
    }
}
