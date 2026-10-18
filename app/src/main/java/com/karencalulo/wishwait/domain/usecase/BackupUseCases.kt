package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.repository.BackupCodec
import com.karencalulo.wishwait.domain.repository.Clock
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import com.karencalulo.wishwait.domain.repository.ReminderScheduler
import com.karencalulo.wishwait.domain.repository.WishRepository

/** The backup as text: saved to a file the person picks, or to their own Google Drive. */
class ExportBackupUseCase(
    private val wishes: WishRepository,
    private val profiles: ProfileRepository,
    private val codec: BackupCodec,
    private val clock: Clock,
    private val goals: com.karencalulo.wishwait.domain.repository.GoalRepository? = null,
) {
    suspend operator fun invoke(): String = codec.encode(
        Backup(
            version = Backup.CURRENT_VERSION,
            exportedAt = clock.now(),
            profile = profiles.current(),
            wishes = wishes.getWishes(),
            goal = goals?.current(),
        ),
    )
}

class ReadBackupUseCase(private val codec: BackupCodec) {
    operator fun invoke(text: String): Backup? = codec.decode(text)
}

/** Replaces everything on this phone with the backup and re-arms its reminders. */
class RestoreBackupUseCase(
    private val wishes: WishRepository,
    private val profiles: ProfileRepository,
    private val scheduler: ReminderScheduler,
    private val goals: com.karencalulo.wishwait.domain.repository.GoalRepository? = null,
) {
    suspend operator fun invoke(backup: Backup) {
        goals?.save(backup.goal)
        wishes.getWishes().forEach { scheduler.cancel(it.id) }
        wishes.replaceAll(backup.wishes)
        profiles.update { backup.profile.copy(onboarded = true) }
        backup.wishes.forEach { scheduler.schedule(it) }
    }
}
