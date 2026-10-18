package com.karencalulo.wishwait.domain.model

/**
 * Where the person's backups stand. Kept on this phone only (not inside backups).
 * Google Drive backup is optional: the backup goes to a hidden WishWait folder in the
 * person's own Drive. There is no WishWait server or account.
 */
data class BackupStatus(
    val driveConnected: Boolean = false,
    /** Google account email shown in Settings, if Google shared it. */
    val driveAccount: String? = null,
    val lastDriveBackupAt: Long? = null,
    /** Last backup of any kind (Drive or an exported file). */
    val lastBackupAt: Long? = null,
    /** Fingerprint of the last data uploaded, so unchanged data isn't uploaded again. */
    val lastUploadedHash: String? = null,
    /** Weekly "back up your wishes" reminder, for people who don't use Drive backup. */
    val reminderEnabled: Boolean = false,
    val reminderSince: Long? = null,
    val lastReminderAt: Long? = null,
)

/** Result of looking for the backup file in Drive. */
sealed interface DriveFile {
    data object NotFound : DriveFile
    /** Offline, access removed, or Drive not set up for this app yet. */
    data object Unreachable : DriveFile
    data class Found(val modifiedAt: Long, val content: String) : DriveFile
}

object BackupRules {

    const val REMINDER_DAYS = 7

    /**
     * When the weekly reminder should next check in, or null when it's off or not needed
     * (Drive backup is on). Counts from the latest backup, reminder or switch-on.
     */
    fun nextReminderAt(status: BackupStatus): Long? {
        if (!status.reminderEnabled || status.driveConnected) return null
        val base = listOfNotNull(status.lastBackupAt, status.lastReminderAt, status.reminderSince).maxOrNull() ?: return null
        return base + REMINDER_DAYS * DAY_MS
    }

    /** True when the reminder is due now (an hour of slack for late alarms). */
    fun reminderDue(status: BackupStatus, now: Long): Boolean {
        val next = nextReminderAt(status) ?: return false
        return now >= next - HOUR_MS
    }

    /**
     * Fingerprint of a backup's content, ignoring the export time, so the same data
     * isn't uploaded twice.
     */
    fun fingerprint(backup: Backup, codec: (Backup) -> String): String =
        codec(backup.copy(exportedAt = 0L)).hashCode().toString(16) + ":" + backup.wishes.size
}
