package com.karencalulo.wishwait.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.karencalulo.wishwait.domain.model.BackupStatus
import com.karencalulo.wishwait.domain.repository.BackupStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class BackupStatusRepositoryImpl(private val dataStore: DataStore<Preferences>) : BackupStatusRepository {

    private object Keys {
        val connected = booleanPreferencesKey("backup_drive_connected")
        val account = stringPreferencesKey("backup_drive_account")
        val lastDrive = longPreferencesKey("backup_last_drive_at")
        val lastAny = longPreferencesKey("backup_last_at")
        val hash = stringPreferencesKey("backup_last_hash")
        val reminder = booleanPreferencesKey("backup_reminder")
        val reminderSince = longPreferencesKey("backup_reminder_since")
        val lastReminder = longPreferencesKey("backup_last_reminder_at")
        val all = listOf(connected, account, lastDrive, lastAny, hash, reminder, reminderSince, lastReminder)
    }

    override val status: Flow<BackupStatus> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toStatus() }
        .distinctUntilChanged()

    override suspend fun current(): BackupStatus = status.first()

    override suspend fun update(transform: (BackupStatus) -> BackupStatus) {
        dataStore.edit { prefs ->
            val s = transform(prefs.toStatus())
            prefs[Keys.connected] = s.driveConnected
            prefs[Keys.reminder] = s.reminderEnabled
            prefs.setOrRemove(Keys.account, s.driveAccount)
            prefs.setOrRemove(Keys.lastDrive, s.lastDriveBackupAt)
            prefs.setOrRemove(Keys.lastAny, s.lastBackupAt)
            prefs.setOrRemove(Keys.hash, s.lastUploadedHash)
            prefs.setOrRemove(Keys.reminderSince, s.reminderSince)
            prefs.setOrRemove(Keys.lastReminder, s.lastReminderAt)
        }
    }

    override suspend fun reset() {
        dataStore.edit { prefs -> Keys.all.forEach { prefs.remove(it) } }
    }

    private fun Preferences.toStatus() = BackupStatus(
        driveConnected = this[Keys.connected] ?: false,
        driveAccount = this[Keys.account],
        lastDriveBackupAt = this[Keys.lastDrive],
        lastBackupAt = this[Keys.lastAny],
        lastUploadedHash = this[Keys.hash],
        reminderEnabled = this[Keys.reminder] ?: false,
        reminderSince = this[Keys.reminderSince],
        lastReminderAt = this[Keys.lastReminder],
    )

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
}
