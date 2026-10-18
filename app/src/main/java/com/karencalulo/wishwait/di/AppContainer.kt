package com.karencalulo.wishwait.di

import com.karencalulo.wishwait.domain.usecase.GetSpendingContextUseCase
import com.karencalulo.wishwait.domain.usecase.GetBuyAdviceUseCase
import com.karencalulo.wishwait.domain.usecase.GetCutoffSpendingUseCase
import com.karencalulo.wishwait.domain.usecase.RemoveGoalUseCase
import com.karencalulo.wishwait.domain.usecase.AddToGoalUseCase
import com.karencalulo.wishwait.domain.usecase.SetGoalUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveGoalUseCase
import com.karencalulo.wishwait.data.repository.GoalRepositoryImpl
import com.karencalulo.wishwait.domain.repository.GoalRepository
import com.karencalulo.wishwait.data.remote.WebViewLinkReader
import com.karencalulo.wishwait.data.remote.CombinedLinkPreviewRepository
import com.karencalulo.wishwait.data.remote.HttpLinkPreviewRepository
import com.karencalulo.wishwait.domain.repository.LinkPreviewRepository
import com.karencalulo.wishwait.domain.usecase.FetchLinkDetailsUseCase
import com.karencalulo.wishwait.domain.usecase.ParseSharedTextUseCase
import android.content.Context
import com.karencalulo.wishwait.data.local.wishWaitDataStore
import com.karencalulo.wishwait.data.reminder.AlarmReminderScheduler
import com.karencalulo.wishwait.data.repository.ProfileRepositoryImpl
import com.karencalulo.wishwait.data.repository.WishRepositoryImpl
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import com.karencalulo.wishwait.domain.repository.ReminderScheduler
import com.karencalulo.wishwait.domain.repository.WishRepository
import com.karencalulo.wishwait.domain.usecase.AffordabilityCalculator
import com.karencalulo.wishwait.domain.usecase.BuildVerdictUseCase
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import com.karencalulo.wishwait.domain.usecase.CalculateWorthUseCase
import com.karencalulo.wishwait.domain.usecase.ChangeCurrencyUseCase
import com.karencalulo.wishwait.domain.usecase.EditWishUseCase
import com.karencalulo.wishwait.domain.usecase.ExportBackupUseCase
import com.karencalulo.wishwait.domain.usecase.ReadBackupUseCase
import com.karencalulo.wishwait.domain.usecase.RestoreBackupUseCase
import com.karencalulo.wishwait.domain.repository.BackupCodec
import com.karencalulo.wishwait.data.local.BackupJsonCodec
import com.karencalulo.wishwait.domain.usecase.LearnFromHistoryUseCase
import com.karencalulo.wishwait.domain.usecase.LogUseUseCase
import com.karencalulo.wishwait.domain.usecase.MarkPaymentPaidUseCase
import com.karencalulo.wishwait.domain.usecase.SetPaymentPlanUseCase
import com.karencalulo.wishwait.domain.usecase.SetWorthTargetUseCase
import com.karencalulo.wishwait.domain.usecase.ComputeStatsUseCase
import com.karencalulo.wishwait.domain.usecase.CreateWishUseCase
import com.karencalulo.wishwait.domain.usecase.DecideWishUseCase
import com.karencalulo.wishwait.domain.usecase.DeleteWishUseCase
import com.karencalulo.wishwait.domain.usecase.ExtendCooldownUseCase
import com.karencalulo.wishwait.domain.usecase.GetProfileUseCase
import com.karencalulo.wishwait.domain.usecase.LogDesireUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveProfileUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveStatsUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishesUseCase
import com.karencalulo.wishwait.domain.usecase.RatePurchaseUseCase
import com.karencalulo.wishwait.domain.usecase.ResetAllDataUseCase
import com.karencalulo.wishwait.domain.usecase.UpdateProfileUseCase

/**
 * Manual dependency injection. Small app, no annotation processing,
 * nothing magical — every dependency is visible right here.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.wishWaitDataStore

    val clock: Clock = Clock { System.currentTimeMillis() }
    val wishRepository: WishRepository = WishRepositoryImpl(dataStore)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(dataStore)
    val reminderScheduler: ReminderScheduler = AlarmReminderScheduler(appContext)
    val eveScheduler = com.karencalulo.wishwait.data.reminder.EveReminderScheduler(appContext)
    val goalRepository: GoalRepository = GoalRepositoryImpl(dataStore)
    val backupStatusRepository: com.karencalulo.wishwait.domain.repository.BackupStatusRepository =
        com.karencalulo.wishwait.data.repository.BackupStatusRepositoryImpl(dataStore)
    val backupReminderScheduler: com.karencalulo.wishwait.domain.repository.BackupReminderScheduler =
        com.karencalulo.wishwait.data.reminder.AlarmBackupReminderScheduler(appContext)

    // Pure calculators
    val calculateImpact = CalculatePriceImpactUseCase()
    val buildVerdict = BuildVerdictUseCase()
    val calculator = AffordabilityCalculator()
    private val computeStats = ComputeStatsUseCase(CalculateWorthUseCase())

    // Profile
    val observeProfile = ObserveProfileUseCase(profileRepository)
    val getProfile = GetProfileUseCase(profileRepository)
    val updateProfile = UpdateProfileUseCase(profileRepository)

    // Wishes
    val observeWishes = ObserveWishesUseCase(wishRepository)
    val observeWish = ObserveWishUseCase(wishRepository)
    val observeStats = ObserveStatsUseCase(wishRepository, profileRepository, goalRepository, computeStats)
    val createWish = CreateWishUseCase(wishRepository, reminderScheduler, clock)
    val decideWish = DecideWishUseCase(wishRepository, reminderScheduler, clock)
    val extendCooldown = ExtendCooldownUseCase(wishRepository, reminderScheduler, clock)
    val logDesire = LogDesireUseCase(wishRepository, clock)
    val ratePurchase = RatePurchaseUseCase(wishRepository, reminderScheduler)

    // After purchase
    val calculateWorth = CalculateWorthUseCase()
    val logUse = LogUseUseCase(wishRepository, clock)
    val setWorthTarget = SetWorthTargetUseCase(wishRepository)
    val setPaymentPlan = SetPaymentPlanUseCase(wishRepository, reminderScheduler, calculator, clock)
    val markPaymentPaid = MarkPaymentPaidUseCase(wishRepository, reminderScheduler)
    val learnFromHistory = LearnFromHistoryUseCase()

    // Savings goal (one at a time)
    val observeGoal = ObserveGoalUseCase(goalRepository)
    val setGoal = SetGoalUseCase(goalRepository, clock)
    val addToGoal = AddToGoalUseCase(goalRepository, clock)
    val removeGoal = RemoveGoalUseCase(goalRepository)
    val parseSharedText = ParseSharedTextUseCase()
    val spendingContext = GetSpendingContextUseCase(clock)
    val buyAdvice = GetBuyAdviceUseCase(clock)
    val cutoffSpending = GetCutoffSpendingUseCase(clock)

    // Optional tips for Ollie (Google Play Billing). A completed tip is only counted on this device.
    val recordTip = com.karencalulo.wishwait.domain.usecase.RecordTipUseCase(profileRepository)
    val tipJar = com.karencalulo.wishwait.data.billing.PlayTipJar(appContext) { recordTip() }
    // Quick plain read first; hidden on-device browser for JavaScript-built shop pages.
    private val isProductTitle: (String) -> Boolean = { !parseSharedText.isGenericPageTitle(it) }
    private val linkPreviews: LinkPreviewRepository = CombinedLinkPreviewRepository(
        quick = HttpLinkPreviewRepository(),
        browser = WebViewLinkReader(appContext, isProductTitle),
        isUsefulTitle = isProductTitle,
    )
    val fetchLinkDetails = FetchLinkDetailsUseCase(linkPreviews, parseSharedText)

    // Editing, currency, backup
    val editWish = EditWishUseCase(wishRepository, reminderScheduler)
    val changeCurrency = ChangeCurrencyUseCase(wishRepository, profileRepository, goalRepository)
    private val backupCodec: BackupCodec = BackupJsonCodec()
    val exportBackup = ExportBackupUseCase(wishRepository, profileRepository, backupCodec, clock, goalRepository)
    val readBackup = ReadBackupUseCase(backupCodec)
    val restoreBackup = RestoreBackupUseCase(wishRepository, profileRepository, reminderScheduler, goalRepository)
    val deleteWish = DeleteWishUseCase(wishRepository, reminderScheduler)
    val resetAllData = ResetAllDataUseCase(wishRepository, profileRepository, reminderScheduler, goalRepository, backupStatusRepository)

    // Optional Google Drive backup (the person's own Drive, hidden app folder; no WishWait server).
    val driveAuth = com.karencalulo.wishwait.data.cloud.DriveAuth(appContext)
    val cloudBackup: com.karencalulo.wishwait.domain.repository.CloudBackupRepository =
        com.karencalulo.wishwait.data.cloud.DriveCloudBackupRepository(driveAuth)
    val backUpToDrive = com.karencalulo.wishwait.domain.usecase.BackUpToDriveUseCase(
        wishRepository, profileRepository, goalRepository, backupCodec, cloudBackup, backupStatusRepository, clock,
    )
    val checkDriveBackup = com.karencalulo.wishwait.domain.usecase.CheckDriveBackupUseCase(cloudBackup, backupCodec)
    val connectDrive = com.karencalulo.wishwait.domain.usecase.ConnectDriveUseCase(backupStatusRepository, restoreBackup, backUpToDrive, clock)
    val disconnectDrive = com.karencalulo.wishwait.domain.usecase.DisconnectDriveUseCase(backupStatusRepository, cloudBackup)
    val readDriveBackup = com.karencalulo.wishwait.domain.usecase.ReadDriveBackupUseCase(cloudBackup, backupCodec)
    val markFileBackup = com.karencalulo.wishwait.domain.usecase.MarkFileBackupUseCase(backupStatusRepository, clock)
    val setBackupReminder = com.karencalulo.wishwait.domain.usecase.SetBackupReminderUseCase(backupStatusRepository, clock)
}
