package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.data.local.BackupJsonCodec
import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.BackupRules
import com.karencalulo.wishwait.domain.model.BackupStatus
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.DriveFile
import com.karencalulo.wishwait.domain.model.SavingsGoal
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.repository.BackupStatusRepository
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.repository.CloudBackupRepository
import com.karencalulo.wishwait.domain.repository.GoalRepository
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import com.karencalulo.wishwait.domain.repository.ReminderScheduler
import com.karencalulo.wishwait.domain.repository.WishRepository
import com.karencalulo.wishwait.domain.usecase.BackUpToDriveUseCase
import com.karencalulo.wishwait.domain.usecase.CheckDriveBackupUseCase
import com.karencalulo.wishwait.domain.usecase.ConnectDriveUseCase
import com.karencalulo.wishwait.domain.usecase.DisconnectDriveUseCase
import com.karencalulo.wishwait.domain.usecase.DriveBackupResult
import com.karencalulo.wishwait.domain.usecase.DriveConnectCheck
import com.karencalulo.wishwait.domain.usecase.ResetAllDataUseCase
import com.karencalulo.wishwait.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupTest {

    private class FakeWishes(initial: List<Wish> = emptyList()) : WishRepository {
        val state = MutableStateFlow(initial)
        override fun observeWishes(): Flow<List<Wish>> = state
        override fun observeWish(id: String): Flow<Wish?> = state.map { l -> l.firstOrNull { it.id == id } }
        override suspend fun getWish(id: String) = state.value.firstOrNull { it.id == id }
        override suspend fun getWishes() = state.value
        override suspend fun upsert(wish: Wish) { state.value = state.value.filter { it.id != wish.id } + wish }
        override suspend fun delete(id: String) { state.value = state.value.filter { it.id != id } }
        override suspend fun clear() { state.value = emptyList() }
        override suspend fun replaceAll(wishes: List<Wish>) { state.value = wishes }
    }

    private class FakeProfile(initial: UserProfile) : ProfileRepository {
        val state = MutableStateFlow(initial)
        override val profile: Flow<UserProfile> = state
        override suspend fun current() = state.value
        override suspend fun update(transform: (UserProfile) -> UserProfile) { state.value = transform(state.value) }
        override suspend fun reset() { state.value = UserProfile() }
    }

    private class FakeGoals : GoalRepository {
        val state = MutableStateFlow<SavingsGoal?>(null)
        override val goal: Flow<SavingsGoal?> = state
        override val completedCount: Flow<Int> = MutableStateFlow(0)
        override suspend fun current() = state.value
        override suspend fun save(goal: SavingsGoal?) { state.value = goal }
        override suspend fun markCompleted() = Unit
        override suspend fun clearAll() { state.value = null }
    }

    private class FakeStatus(initial: BackupStatus = BackupStatus()) : BackupStatusRepository {
        val state = MutableStateFlow(initial)
        override val status: Flow<BackupStatus> = state
        override suspend fun current() = state.value
        override suspend fun update(transform: (BackupStatus) -> BackupStatus) { state.value = transform(state.value) }
        override suspend fun reset() { state.value = BackupStatus() }
    }

    private class FakeDrive : CloudBackupRepository {
        var file: DriveFile = DriveFile.NotFound
        var uploads = 0
        var online = true
        override suspend fun upload(content: String): Boolean {
            if (!online) return false
            uploads++
            file = DriveFile.Found(uploads * 1_000L, content)
            return true
        }
        override suspend fun download(): DriveFile = if (online) file else DriveFile.Unreachable
        override suspend fun delete(): Boolean { if (!online) return false; file = DriveFile.NotFound; return true }
    }

    private object NoReminders : ReminderScheduler {
        override fun schedule(wish: Wish) = Unit
        override fun cancel(wishId: String) = Unit
    }

    private fun wish(id: String) = Wish(
        id = id, name = id, price = 1_000.0, category = Category.OTHER, reasons = emptySet(), moods = emptySet(),
        note = "", createdAt = 0L, cooldownDays = 7, cooldownEndsAt = 7 * DAY_MS, status = WishStatus.WAITING,
    )

    private val codec = BackupJsonCodec()
    private val clock = Clock { 10 * DAY_MS }

    private class Setup(wishes: List<Wish>, onboarded: Boolean = true, status: BackupStatus = BackupStatus()) {
        val wishes = FakeWishes(wishes)
        val profiles = FakeProfile(UserProfile(onboarded = onboarded, monthlyIncome = 30_000.0))
        val goals = FakeGoals()
        val status = FakeStatus(status)
        val drive = FakeDrive()
    }

    private fun backUp(s: Setup) = BackUpToDriveUseCase(s.wishes, s.profiles, s.goals, codec, s.drive, s.status, clock)

    @Test
    fun `backs up only when on, only after setup, and never uploads unchanged data twice`() = runBlocking {
        val s = Setup(listOf(wish("a")))
        assertEquals(DriveBackupResult.NOT_CONNECTED, backUp(s)())

        s.status.state.value = BackupStatus(driveConnected = true)
        assertEquals(DriveBackupResult.DONE, backUp(s)())
        assertEquals(DriveBackupResult.UP_TO_DATE, backUp(s)())
        assertEquals(1, s.drive.uploads)
        assertEquals(clock.now(), s.status.state.value.lastDriveBackupAt)
        assertEquals(clock.now(), s.status.state.value.lastBackupAt)

        s.wishes.upsert(wish("b"))
        assertEquals(DriveBackupResult.DONE, backUp(s)())
        assertEquals(DriveBackupResult.DONE, backUp(s)(force = true))
        assertEquals(3, s.drive.uploads)

        s.drive.online = false
        s.wishes.upsert(wish("c"))
        assertEquals(DriveBackupResult.FAILED, backUp(s)())
    }

    @Test
    fun `a fresh phone that isn't set up never overwrites the Drive backup`() = runBlocking {
        val s = Setup(emptyList(), onboarded = false, status = BackupStatus(driveConnected = true))
        assertEquals(DriveBackupResult.UP_TO_DATE, backUp(s)())
        assertEquals(0, s.drive.uploads)
    }

    @Test
    fun `connecting looks before writing, and can restore the found backup`() = runBlocking {
        // Old phone backed up two wishes.
        val old = Setup(listOf(wish("a"), wish("b")), status = BackupStatus(driveConnected = true))
        backUp(old)()

        // New phone: set up, empty, signs in with the same Google account.
        val phone = Setup(emptyList())
        phone.drive.file = old.drive.file
        val check = CheckDriveBackupUseCase(phone.drive, codec)()
        assertTrue(check is DriveConnectCheck.Existing)
        assertEquals(0, phone.drive.uploads) // nothing written yet

        val restore = RestoreBackupUseCase(phone.wishes, phone.profiles, NoReminders, phone.goals)
        ConnectDriveUseCase(phone.status, restore, backUp(phone), clock)(
            account = "karen@example.com",
            restoreFrom = (check as DriveConnectCheck.Existing).backup,
            restoredAt = check.modifiedAt,
        )
        assertEquals(setOf("a", "b"), phone.wishes.state.value.map { it.id }.toSet())
        assertTrue(phone.status.state.value.driveConnected)
        assertEquals("karen@example.com", phone.status.state.value.driveAccount)

        // Nothing in Drive yet: connecting backs up this phone right away.
        val first = Setup(listOf(wish("x")))
        assertEquals(DriveConnectCheck.Empty, CheckDriveBackupUseCase(first.drive, codec)())
        val result = ConnectDriveUseCase(first.status, RestoreBackupUseCase(first.wishes, first.profiles, NoReminders, first.goals), backUp(first), clock)(
            account = null,
            restoreFrom = null,
        )
        assertEquals(DriveBackupResult.DONE, result)
        assertEquals(1, first.drive.uploads)

        first.drive.online = false
        assertEquals(DriveConnectCheck.Unreachable, CheckDriveBackupUseCase(first.drive, codec)())
    }

    @Test
    fun `turning off keeps or deletes the Drive copy, erasing the phone stops backups`() = runBlocking {
        val s = Setup(listOf(wish("a")), status = BackupStatus(driveConnected = true, driveAccount = "k@x.com"))
        backUp(s)()
        assertTrue(DisconnectDriveUseCase(s.status, s.drive)(deleteBackup = false))
        assertFalse(s.status.state.value.driveConnected)
        assertTrue(s.drive.file is DriveFile.Found)

        s.status.state.value = s.status.state.value.copy(driveConnected = true)
        assertTrue(DisconnectDriveUseCase(s.status, s.drive)(deleteBackup = true))
        assertEquals(DriveFile.NotFound, s.drive.file)

        s.status.state.value = BackupStatus(driveConnected = true, reminderEnabled = true)
        ResetAllDataUseCase(s.wishes, s.profiles, NoReminders, s.goals, s.status)()
        assertEquals(BackupStatus(), s.status.state.value)
    }

    @Test
    fun `weekly reminder counts from the latest backup and stays quiet with Drive on`() {
        val week = BackupRules.REMINDER_DAYS * DAY_MS
        assertNull(BackupRules.nextReminderAt(BackupStatus()))
        assertNull(BackupRules.nextReminderAt(BackupStatus(reminderEnabled = true, reminderSince = 0L, driveConnected = true)))

        val on = BackupStatus(reminderEnabled = true, reminderSince = 0L, lastBackupAt = 3 * DAY_MS)
        assertEquals(3 * DAY_MS + week, BackupRules.nextReminderAt(on))
        assertFalse(BackupRules.reminderDue(on, 9 * DAY_MS))
        assertTrue(BackupRules.reminderDue(on, 10 * DAY_MS))
        // After reminding, the next one is a week later.
        assertEquals(10 * DAY_MS + week, BackupRules.nextReminderAt(on.copy(lastReminderAt = 10 * DAY_MS)))
    }

    @Test
    fun `fingerprint ignores the export time but sees real changes`() {
        val b = Backup(Backup.CURRENT_VERSION, 1L, UserProfile(onboarded = true), listOf(wish("a")), null)
        assertEquals(BackupRules.fingerprint(b, codec::encode), BackupRules.fingerprint(b.copy(exportedAt = 99L), codec::encode))
        assertFalse(BackupRules.fingerprint(b, codec::encode) == BackupRules.fingerprint(b.copy(wishes = emptyList()), codec::encode))
    }
}
