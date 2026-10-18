package com.karencalulo.wishwait.domain.repository

import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import kotlinx.coroutines.flow.Flow

interface WishRepository {
    fun observeWishes(): Flow<List<Wish>>
    fun observeWish(id: String): Flow<Wish?>
    suspend fun getWish(id: String): Wish?
    suspend fun getWishes(): List<Wish>
    suspend fun upsert(wish: Wish)
    suspend fun delete(id: String)
    suspend fun clear()
    suspend fun replaceAll(wishes: List<Wish>)
}

interface ProfileRepository {
    val profile: Flow<UserProfile>
    suspend fun current(): UserProfile
    suspend fun update(transform: (UserProfile) -> UserProfile)
    suspend fun reset()
}

/** Schedules the "halfway" and "time to decide" nudges for a wish. */
interface ReminderScheduler {
    fun schedule(wish: Wish)
    fun cancel(wishId: String)
}

/** The single savings goal (or none). */
interface GoalRepository {
    val goal: Flow<SavingsGoal?>
    suspend fun current(): SavingsGoal?
    suspend fun save(goal: SavingsGoal?)
    /** How many goals were ever completed (for the badge, survives starting a new goal). */
    val completedCount: Flow<Int>
    suspend fun markCompleted()
    suspend fun clearAll()
}

/** Raw preview details read from a public product page. */
data class LinkPreview(val title: String?, val priceText: String?)

/**
 * Opens ONE link the user shared to read its public title/price.
 * Only that page is requested; nothing about the user is sent.
 */
interface LinkPreviewRepository {
    suspend fun fetch(url: String): LinkPreview?
}

/** Turns everything into one portable text file and back. */
interface BackupCodec {
    fun encode(backup: Backup): String
    fun decode(text: String): Backup?
}

fun interface Clock {
    fun now(): Long
}

/** Optional tips for Ollie through Google Play. Buying needs an Activity, so that part lives in the data layer. */
interface TipJar {
    /** Tips with local prices, or null when Google Play isn't available right now. */
    suspend fun options(): List<com.karencalulo.wishwait.domain.model.TipOption>?
    val events: Flow<com.karencalulo.wishwait.domain.model.TipEvent>
    /** Finishes tips that were paid but not yet recorded (e.g. a payment that completed later). */
    suspend fun finishPending()
}

/** Backup status, kept on this phone only. */
interface BackupStatusRepository {
    val status: Flow<com.karencalulo.wishwait.domain.model.BackupStatus>
    suspend fun current(): com.karencalulo.wishwait.domain.model.BackupStatus
    suspend fun update(transform: (com.karencalulo.wishwait.domain.model.BackupStatus) -> com.karencalulo.wishwait.domain.model.BackupStatus)
    suspend fun reset()
}

/**
 * The person's own Google Drive (hidden app folder). Calls return false / Unreachable
 * when Drive can't be reached or access was removed — they never throw.
 */
interface CloudBackupRepository {
    /** Uploads (creates or replaces) the backup file. */
    suspend fun upload(content: String): Boolean
    suspend fun download(): com.karencalulo.wishwait.domain.model.DriveFile
    suspend fun delete(): Boolean
}

/** Weekly backup reminder alarm. */
interface BackupReminderScheduler {
    fun schedule(at: Long?)
}
