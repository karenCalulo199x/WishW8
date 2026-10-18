package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.BackupRules
import com.karencalulo.wishwait.domain.model.DriveFile
import com.karencalulo.wishwait.domain.repository.BackupCodec
import com.karencalulo.wishwait.domain.repository.BackupStatusRepository
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.repository.CloudBackupRepository
import com.karencalulo.wishwait.domain.repository.GoalRepository
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import com.karencalulo.wishwait.domain.repository.WishRepository

enum class DriveBackupResult { DONE, UP_TO_DATE, NOT_CONNECTED, FAILED }

/** Saves this phone's data to the person's own Google Drive. Skips the upload when nothing changed. */
class BackUpToDriveUseCase(
    private val wishes: WishRepository,
    private val profiles: ProfileRepository,
    private val goals: GoalRepository,
    private val codec: BackupCodec,
    private val cloud: CloudBackupRepository,
    private val status: BackupStatusRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(force: Boolean = false): DriveBackupResult {
        val current = status.current()
        if (!current.driveConnected) return DriveBackupResult.NOT_CONNECTED
        val backup = Backup(Backup.CURRENT_VERSION, clock.now(), profiles.current(), wishes.getWishes(), goals.current())
        // Never upload a fresh, never-set-up phone over a real backup.
        if (!backup.profile.onboarded) return DriveBackupResult.UP_TO_DATE
        val hash = BackupRules.fingerprint(backup, codec::encode)
        if (!force && hash == current.lastUploadedHash) return DriveBackupResult.UP_TO_DATE
        if (!cloud.upload(codec.encode(backup))) return DriveBackupResult.FAILED
        val now = clock.now()
        status.update { it.copy(lastDriveBackupAt = now, lastBackupAt = now, lastUploadedHash = hash) }
        return DriveBackupResult.DONE
    }
}

/** What was found when connecting Drive. */
sealed interface DriveConnectCheck {
    /** No backup in Drive yet: safe to start backing up this phone. */
    data object Empty : DriveConnectCheck
    /** A backup exists: ask whether to restore it or replace it with this phone's data. */
    data class Existing(val modifiedAt: Long, val backup: Backup) : DriveConnectCheck
    data object Unreachable : DriveConnectCheck
}

/**
 * Step 1 of connecting (after Google sign-in): look before writing, so a new phone
 * never overwrites the backup it is about to restore.
 */
class CheckDriveBackupUseCase(
    private val cloud: CloudBackupRepository,
    private val codec: BackupCodec,
) {
    suspend operator fun invoke(): DriveConnectCheck = when (val file = cloud.download()) {
        DriveFile.NotFound -> DriveConnectCheck.Empty
        DriveFile.Unreachable -> DriveConnectCheck.Unreachable
        is DriveFile.Found -> codec.decode(file.content)
            ?.let { DriveConnectCheck.Existing(file.modifiedAt, it) }
            // An unreadable file is treated as empty: this phone's backup replaces it.
            ?: DriveConnectCheck.Empty
    }
}

/** Step 2 of connecting: turn on Drive backup, optionally restoring the found backup first. */
class ConnectDriveUseCase(
    private val status: BackupStatusRepository,
    private val restore: RestoreBackupUseCase,
    private val backUp: BackUpToDriveUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(account: String?, restoreFrom: Backup?, restoredAt: Long? = null): DriveBackupResult {
        if (restoreFrom != null) {
            restore(restoreFrom)
            // The restored data came from Drive; the next automatic backup simply refreshes it.
            status.update {
                it.copy(
                    driveConnected = true,
                    driveAccount = account,
                    lastDriveBackupAt = restoredAt ?: clock.now(),
                    lastBackupAt = restoredAt ?: clock.now(),
                    lastUploadedHash = null,
                )
            }
            return DriveBackupResult.DONE
        }
        status.update { it.copy(driveConnected = true, driveAccount = account, lastUploadedHash = null) }
        return backUp(force = true)
    }
}

/** Stops Drive backup. The backup can stay in Drive (to restore later) or be deleted. */
class DisconnectDriveUseCase(
    private val status: BackupStatusRepository,
    private val cloud: CloudBackupRepository,
) {
    /** Returns false only when deleting was asked for and failed (backup is turned off anyway). */
    suspend operator fun invoke(deleteBackup: Boolean): Boolean {
        val deleted = if (deleteBackup) cloud.delete() else true
        status.update { it.copy(driveConnected = false, driveAccount = null, lastUploadedHash = null) }
        return deleted
    }
}

/** Reads the backup currently in Drive for "Restore". */
class ReadDriveBackupUseCase(
    private val cloud: CloudBackupRepository,
    private val codec: BackupCodec,
) {
    suspend operator fun invoke(): Backup? = (cloud.download() as? DriveFile.Found)?.let { codec.decode(it.content) }
}

/** An exported backup file counts as a backup (for the weekly reminder). */
class MarkFileBackupUseCase(private val status: BackupStatusRepository, private val clock: Clock) {
    suspend operator fun invoke() {
        val now = clock.now()
        status.update { it.copy(lastBackupAt = now) }
    }
}

class SetBackupReminderUseCase(private val status: BackupStatusRepository, private val clock: Clock) {
    suspend operator fun invoke(on: Boolean) {
        val now = clock.now()
        status.update { it.copy(reminderEnabled = on, reminderSince = if (on) now else it.reminderSince) }
    }
}
