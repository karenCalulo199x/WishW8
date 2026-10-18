package com.karencalulo.wishwait.presentation.settings

import android.content.res.Resources
import androidx.lifecycle.viewModelScope
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.BackupStatus
import com.karencalulo.wishwait.domain.usecase.BackUpToDriveUseCase
import com.karencalulo.wishwait.domain.usecase.CheckDriveBackupUseCase
import com.karencalulo.wishwait.domain.usecase.ConnectDriveUseCase
import com.karencalulo.wishwait.domain.usecase.DisconnectDriveUseCase
import com.karencalulo.wishwait.domain.usecase.DriveBackupResult
import com.karencalulo.wishwait.domain.usecase.DriveConnectCheck
import com.karencalulo.wishwait.domain.usecase.MarkFileBackupUseCase
import com.karencalulo.wishwait.domain.usecase.ReadDriveBackupUseCase
import com.karencalulo.wishwait.domain.usecase.SetBackupReminderUseCase
import kotlinx.coroutines.flow.Flow
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.usecase.ChangeCurrencyUseCase
import com.karencalulo.wishwait.domain.usecase.ExportBackupUseCase
import com.karencalulo.wishwait.domain.usecase.GetProfileUseCase
import com.karencalulo.wishwait.domain.usecase.ObserveWishesUseCase
import com.karencalulo.wishwait.domain.usecase.ReadBackupUseCase
import com.karencalulo.wishwait.domain.usecase.ResetAllDataUseCase
import com.karencalulo.wishwait.domain.usecase.RestoreBackupUseCase
import com.karencalulo.wishwait.domain.usecase.UpdateProfileUseCase
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SettingsState(
    val income: String = "",
    val hours: String = "",
    val savings: String = "",
    val currencyCode: String = "USD",
    val strictness: Strictness = Strictness.BALANCED,
    val remindersEnabled: Boolean = true,
    val fetchLinkDetails: Boolean = true,
    val dirty: Boolean = false,
    val hasData: Boolean = false,
    val showCurrencyPicker: Boolean = false,
    /** Currency the user picked, waiting for "convert or relabel?" */
    val pendingCurrency: String? = null,
    val conversionRate: String = "",
    val showResetConfirm: Boolean = false,
    val paydays: List<Int> = emptyList(),
    val paydaySales: List<Int> = emptyList(),
    val showPaydayPicker: Boolean = false,
    /** A backup file that was read and is waiting for confirmation. */
    val pendingRestore: Backup? = null,
    // ---- Google Drive backup (optional) ----
    val backup: BackupStatus = BackupStatus(),
    val driveBusy: Boolean = false,
    /** Found a backup in Drive while turning it on: restore it, or keep this phone's data? */
    val driveFound: DriveFound? = null,
    val showDriveOff: Boolean = false,
    val deleteDriveBackup: Boolean = false,
)

data class DriveFound(val modifiedAt: Long, val backup: Backup, val account: String?)

/** Why Google sign-in was started. */
enum class DrivePurpose { CONNECT, BACKUP_NOW, RESTORE }

sealed interface SettingsIntent {
    data class IncomeChanged(val value: String) : SettingsIntent
    data class HoursChanged(val value: String) : SettingsIntent
    data class SavingsChanged(val value: String) : SettingsIntent
    data object SaveMoney : SettingsIntent
    data class StrictnessSelected(val value: Strictness) : SettingsIntent
    data class RemindersToggled(val value: Boolean) : SettingsIntent
    data object OpenPaydayPicker : SettingsIntent
    data class PaydaysSelected(val days: List<Int>) : SettingsIntent
    data class PaydaySaleToggled(val day: Int, val on: Boolean) : SettingsIntent
    data class FetchLinksToggled(val value: Boolean) : SettingsIntent
    data object OpenCurrencyPicker : SettingsIntent
    data class CurrencySelected(val code: String) : SettingsIntent
    data class RateChanged(val value: String) : SettingsIntent
    data object ConvertCurrency : SettingsIntent
    data object RelabelCurrency : SettingsIntent
    data object ResetRequested : SettingsIntent
    data object ResetConfirmed : SettingsIntent
    data object DismissDialogs : SettingsIntent

    /** The user picked where to save; the screen writes the text the ViewModel produces. */
    data class ExportTo(val uri: String) : SettingsIntent
    data class ExportResult(val success: Boolean) : SettingsIntent
    data class BackupFileRead(val text: String?) : SettingsIntent
    data object RestoreConfirmed : SettingsIntent

    // Google Drive backup
    data object DriveSignIn : SettingsIntent
    data object DriveBackUpNow : SettingsIntent
    data object DriveRestore : SettingsIntent
    data class DriveAuthorized(val purpose: DrivePurpose, val account: String?) : SettingsIntent
    data class DriveAuthFailed(val cancelled: Boolean) : SettingsIntent
    data object DriveFoundRestore : SettingsIntent
    data object DriveFoundKeepPhone : SettingsIntent
    data object DriveTurnOffRequested : SettingsIntent
    data class DriveDeleteToggled(val value: Boolean) : SettingsIntent
    data object DriveTurnOffConfirmed : SettingsIntent
    data class BackupReminderToggled(val value: Boolean) : SettingsIntent
}

sealed interface SettingsEffect {
    data class Message(val text: (Resources) -> String) : SettingsEffect
    data class WriteBackup(val uri: String, val content: String) : SettingsEffect
    data object ResetDone : SettingsEffect
    /** Google sign-in needs the Activity (it may show Google's consent screen). */
    data class AuthorizeDrive(val purpose: DrivePurpose) : SettingsEffect
}

class SettingsUseCases(
    val getProfile: GetProfileUseCase,
    val updateProfile: UpdateProfileUseCase,
    val resetAllData: ResetAllDataUseCase,
    val observeWishes: ObserveWishesUseCase,
    val changeCurrency: ChangeCurrencyUseCase,
    val exportBackup: ExportBackupUseCase,
    val readBackup: ReadBackupUseCase,
    val restoreBackup: RestoreBackupUseCase,
    val backupStatus: Flow<BackupStatus>,
    val checkDriveBackup: CheckDriveBackupUseCase,
    val connectDrive: ConnectDriveUseCase,
    val backUpToDrive: BackUpToDriveUseCase,
    val readDriveBackup: ReadDriveBackupUseCase,
    val disconnectDrive: DisconnectDriveUseCase,
    val markFileBackup: MarkFileBackupUseCase,
    val setBackupReminder: SetBackupReminderUseCase,
)

class SettingsViewModel(
    private val uc: SettingsUseCases,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    init {
        viewModelScope.launch { load(uc.getProfile()) }
        uc.observeWishes().onEach { list -> setState { copy(hasData = list.isNotEmpty()) } }.launchIn(viewModelScope)
        uc.backupStatus.onEach { b -> setState { copy(backup = b) } }.launchIn(viewModelScope)
    }

    private fun load(p: UserProfile) = setState {
        copy(
            income = Fmt.plain(p.monthlyIncome),
            hours = p.hoursPerMonth.toString(),
            savings = Fmt.plain(p.monthlySavings),
            currencyCode = p.currencyCode,
            strictness = p.strictness,
            remindersEnabled = p.remindersEnabled,
            fetchLinkDetails = p.fetchLinkDetails,
            paydays = p.paydays,
            paydaySales = p.paydaySales,
            dirty = false,
        )
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.IncomeChanged -> setState { copy(income = Fmt.sanitizeAmount(intent.value), dirty = true) }
            is SettingsIntent.HoursChanged -> setState { copy(hours = intent.value.filter { it.isDigit() }.take(3), dirty = true) }
            is SettingsIntent.SavingsChanged -> setState { copy(savings = Fmt.sanitizeAmount(intent.value), dirty = true) }
            SettingsIntent.SaveMoney -> {
                val s = currentState
                viewModelScope.launch {
                    uc.updateProfile {
                        it.copy(
                            monthlyIncome = Fmt.parseAmount(s.income) ?: it.monthlyIncome,
                            hoursPerMonth = s.hours.toIntOrNull()?.takeIf { h -> h > 0 } ?: it.hoursPerMonth,
                            monthlySavings = Fmt.parseAmount(s.savings) ?: 0.0,
                        )
                    }
                    setState { copy(dirty = false) }
                    message { it.getString(R.string.msg_saved) }
                }
            }
            is SettingsIntent.StrictnessSelected -> {
                setState { copy(strictness = intent.value) }
                viewModelScope.launch { uc.updateProfile { it.copy(strictness = intent.value) } }
            }
            is SettingsIntent.RemindersToggled -> {
                setState { copy(remindersEnabled = intent.value) }
                viewModelScope.launch { uc.updateProfile { it.copy(remindersEnabled = intent.value) } }
            }

            SettingsIntent.OpenPaydayPicker -> setState { copy(showPaydayPicker = true) }
            is SettingsIntent.PaydaysSelected -> {
                val days = intent.days.filter { it in 1..31 }.distinct().sorted()
                setState { copy(paydays = days, showPaydayPicker = false) }
                viewModelScope.launch { uc.updateProfile { it.copy(paydays = days) } }
            }
            is SettingsIntent.PaydaySaleToggled -> {
                val days = (currentState.paydaySales - intent.day).let { if (intent.on) it + intent.day else it }.sorted()
                setState { copy(paydaySales = days) }
                viewModelScope.launch { uc.updateProfile { it.copy(paydaySales = days) } }
            }
            is SettingsIntent.FetchLinksToggled -> {
                setState { copy(fetchLinkDetails = intent.value) }
                viewModelScope.launch { uc.updateProfile { it.copy(fetchLinkDetails = intent.value) } }
            }

            // ---- Currency ----
            SettingsIntent.OpenCurrencyPicker -> setState { copy(showCurrencyPicker = true) }
            is SettingsIntent.CurrencySelected -> {
                val s = currentState
                when {
                    intent.code == s.currencyCode -> setState { copy(showCurrencyPicker = false) }
                    // Nothing to convert yet: just switch.
                    !s.hasData && (Fmt.parseAmount(s.income) ?: 0.0) <= 0.0 -> applyCurrency(intent.code, null)
                    else -> setState { copy(showCurrencyPicker = false, pendingCurrency = intent.code, conversionRate = "") }
                }
            }
            is SettingsIntent.RateChanged -> setState { copy(conversionRate = Fmt.sanitizeAmount(intent.value)) }
            SettingsIntent.ConvertCurrency -> {
                val code = currentState.pendingCurrency ?: return
                val rate = Fmt.parseAmount(currentState.conversionRate)?.takeIf { it > 0 } ?: return
                applyCurrency(code, rate)
            }
            SettingsIntent.RelabelCurrency -> {
                val code = currentState.pendingCurrency ?: return
                applyCurrency(code, null)
            }

            // ---- Reset ----
            SettingsIntent.ResetRequested -> setState { copy(showResetConfirm = true) }
            SettingsIntent.ResetConfirmed -> viewModelScope.launch {
                setState { copy(showResetConfirm = false) }
                uc.resetAllData()
                sendEffect(SettingsEffect.ResetDone)
            }
            SettingsIntent.DismissDialogs -> setState {
                copy(
                    showCurrencyPicker = false,
                    showResetConfirm = false,
                    pendingCurrency = null,
                    pendingRestore = null,
                    showPaydayPicker = false,
                    // Closing the "backup found" question without choosing leaves Drive backup off.
                    driveFound = null,
                    showDriveOff = false,
                    deleteDriveBackup = false,
                    driveBusy = false,
                )
            }

            // ---- Backup ----
            is SettingsIntent.ExportTo -> viewModelScope.launch {
                sendEffect(SettingsEffect.WriteBackup(intent.uri, uc.exportBackup()))
            }
            is SettingsIntent.ExportResult -> {
                if (intent.success) viewModelScope.launch { uc.markFileBackup() }
                message { it.getString(if (intent.success) R.string.msg_backup_saved else R.string.msg_backup_failed) }
            }
            is SettingsIntent.BackupFileRead -> {
                val backup = intent.text?.let { uc.readBackup(it) }
                if (backup == null) {
                    message { it.getString(R.string.msg_backup_invalid) }
                } else {
                    setState { copy(pendingRestore = backup) }
                }
            }
            SettingsIntent.RestoreConfirmed -> {
                val backup = currentState.pendingRestore ?: return
                viewModelScope.launch {
                    setState { copy(pendingRestore = null) }
                    uc.restoreBackup(backup)
                    load(uc.getProfile())
                    message { it.getString(R.string.msg_restored) }
                }
            }

            // ---- Google Drive backup ----
            SettingsIntent.DriveSignIn -> authorize(DrivePurpose.CONNECT)
            SettingsIntent.DriveBackUpNow -> authorize(DrivePurpose.BACKUP_NOW)
            SettingsIntent.DriveRestore -> authorize(DrivePurpose.RESTORE)
            is SettingsIntent.DriveAuthFailed -> {
                setState { copy(driveBusy = false) }
                if (!intent.cancelled) message { it.getString(R.string.drive_unavailable) }
            }
            is SettingsIntent.DriveAuthorized -> viewModelScope.launch { onAuthorized(intent.purpose, intent.account) }
            SettingsIntent.DriveFoundRestore -> {
                val found = currentState.driveFound ?: return
                viewModelScope.launch {
                    setState { copy(driveFound = null, driveBusy = true) }
                    uc.connectDrive(found.account, restoreFrom = found.backup, restoredAt = found.modifiedAt)
                    load(uc.getProfile())
                    setState { copy(driveBusy = false) }
                    message { it.getString(R.string.drive_restored_on) }
                }
            }
            SettingsIntent.DriveFoundKeepPhone -> {
                val found = currentState.driveFound ?: return
                viewModelScope.launch {
                    setState { copy(driveFound = null, driveBusy = true) }
                    val result = uc.connectDrive(found.account, restoreFrom = null)
                    setState { copy(driveBusy = false) }
                    backupMessage(result, turnedOn = true)
                }
            }
            SettingsIntent.DriveTurnOffRequested -> setState { copy(showDriveOff = true, deleteDriveBackup = false) }
            is SettingsIntent.DriveDeleteToggled -> setState { copy(deleteDriveBackup = intent.value) }
            SettingsIntent.DriveTurnOffConfirmed -> {
                val delete = currentState.deleteDriveBackup
                viewModelScope.launch {
                    setState { copy(showDriveOff = false, driveBusy = true) }
                    val ok = uc.disconnectDrive(deleteBackup = delete)
                    setState { copy(driveBusy = false, deleteDriveBackup = false) }
                    message {
                        it.getString(
                            when {
                                !ok -> R.string.drive_off_delete_failed
                                delete -> R.string.drive_off_deleted
                                else -> R.string.drive_off_kept
                            },
                        )
                    }
                }
            }
            is SettingsIntent.BackupReminderToggled -> viewModelScope.launch { uc.setBackupReminder(intent.value) }
        }
    }

    private fun authorize(purpose: DrivePurpose) {
        if (currentState.driveBusy) return
        setState { copy(driveBusy = true) }
        sendEffect(SettingsEffect.AuthorizeDrive(purpose))
    }

    private suspend fun onAuthorized(purpose: DrivePurpose, account: String?) {
        when (purpose) {
            DrivePurpose.CONNECT -> when (val check = uc.checkDriveBackup()) {
                // Look before writing: a new phone must never overwrite the backup it came to restore.
                is DriveConnectCheck.Existing ->
                    setState { copy(driveBusy = false, driveFound = DriveFound(check.modifiedAt, check.backup, account)) }
                DriveConnectCheck.Empty -> {
                    val result = uc.connectDrive(account, restoreFrom = null)
                    setState { copy(driveBusy = false) }
                    backupMessage(result, turnedOn = true)
                }
                DriveConnectCheck.Unreachable -> {
                    setState { copy(driveBusy = false) }
                    message { it.getString(R.string.drive_unavailable) }
                }
            }
            DrivePurpose.BACKUP_NOW -> {
                val result = uc.backUpToDrive(force = true)
                setState { copy(driveBusy = false) }
                backupMessage(result, turnedOn = false)
            }
            DrivePurpose.RESTORE -> {
                val backup = uc.readDriveBackup()
                setState { copy(driveBusy = false, pendingRestore = backup) }
                if (backup == null) message { it.getString(R.string.drive_nothing_to_restore) }
            }
        }
    }

    private fun backupMessage(result: DriveBackupResult, turnedOn: Boolean) = message {
        it.getString(
            when (result) {
                DriveBackupResult.DONE, DriveBackupResult.UP_TO_DATE ->
                    if (turnedOn) R.string.drive_on else R.string.drive_backed_up
                DriveBackupResult.NOT_CONNECTED, DriveBackupResult.FAILED -> R.string.drive_backup_failed
            },
        )
    }

    private fun applyCurrency(code: String, rate: Double?) {
        viewModelScope.launch {
            setState { copy(showCurrencyPicker = false, pendingCurrency = null) }
            uc.changeCurrency(code, rate)
            load(uc.getProfile())
            if (rate != null) message { it.getString(R.string.msg_currency_converted, code) }
        }
    }

    private fun message(text: (Resources) -> String) = sendEffect(SettingsEffect.Message(text))
}
