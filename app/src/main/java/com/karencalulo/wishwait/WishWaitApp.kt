package com.karencalulo.wishwait

import android.app.Application
import com.karencalulo.wishwait.data.reminder.NotificationHelper
import com.karencalulo.wishwait.data.widget.WishWaitWidget
import com.karencalulo.wishwait.di.AppContainer
import com.karencalulo.wishwait.domain.model.BackupRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
class WishWaitApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        // Keep the "evening before a sale or payday" nudge in step with the payday setting.
        container.profileRepository.profile
            .map { it.paydays to it.paydaySales }
            .distinctUntilChanged()
            .onEach { (paydays, sales) -> container.eveScheduler.scheduleNext(paydays, sales) }
            .launchIn(appScope)
        // Keep the home screen widget in sync with wishes, currency and sale settings.
        combine(container.wishRepository.observeWishes(), container.profileRepository.profile) { _, _ -> Unit }
            .debounce(500)
            .onEach { WishWaitWidget.updateAll(this) }
            .launchIn(appScope)
        // Optional Google Drive backup: a couple of minutes after changes settle, if it's turned on.
        // Unchanged data is never uploaded twice (see BackUpToDriveUseCase).
        combine(
            container.wishRepository.observeWishes(),
            container.profileRepository.profile,
            container.goalRepository.goal,
            container.backupStatusRepository.status.map { it.driveConnected }.distinctUntilChanged(),
        ) { _, _, _, connected -> connected }
            .debounce(AUTO_BACKUP_DELAY_MS)
            .onEach { connected -> if (connected) runCatching { container.backUpToDrive() } }
            .launchIn(appScope)
        // Weekly backup reminder (only when Drive backup is off).
        container.backupStatusRepository.status
            .map { BackupRules.nextReminderAt(it) }
            .distinctUntilChanged()
            .onEach { container.backupReminderScheduler.schedule(it) }
            .launchIn(appScope)
        // A tip paid later (e.g. by e-wallet or cash) is finished the next time the app opens.
        appScope.launch { runCatching { container.tipJar.finishPending() } }
    }

    private companion object {
        const val AUTO_BACKUP_DELAY_MS = 2 * 60_000L
    }
}
