package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.data.local.BackupJsonCodec
import com.karencalulo.wishwait.domain.model.Achievement
import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.TipSize
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.repository.ProfileRepository
import com.karencalulo.wishwait.domain.usecase.ComputeStatsUseCase
import com.karencalulo.wishwait.domain.usecase.RecordTipUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TipTest {

    private class FakeProfile(initial: UserProfile) : ProfileRepository {
        val state = MutableStateFlow(initial)
        override val profile: Flow<UserProfile> = state
        override suspend fun current() = state.value
        override suspend fun update(transform: (UserProfile) -> UserProfile) { state.value = transform(state.value) }
        override suspend fun reset() { state.value = UserProfile() }
    }

    @Test
    fun `a tip is counted and earns the hidden thank-you badge`() = runBlocking {
        val profiles = FakeProfile(UserProfile(onboarded = true))
        assertFalse(Achievement.OLLIE_FRIEND in ComputeStatsUseCase()(emptyList(), profiles.state.value).unlocked)

        RecordTipUseCase(profiles)()
        RecordTipUseCase(profiles)()

        assertEquals(2, profiles.state.value.tipsGiven)
        assertTrue(Achievement.OLLIE_FRIEND in ComputeStatsUseCase()(emptyList(), profiles.state.value).unlocked)
        // Only the thank-you badge hides until earned, so no badge ever reads as "pay to complete".
        assertEquals(listOf(Achievement.OLLIE_FRIEND), Achievement.entries.filter { it.hiddenUntilUnlocked })
    }

    @Test
    fun `tips survive backup and old backups default to none`() {
        val codec = BackupJsonCodec()
        val backup = Backup(Backup.CURRENT_VERSION, 1L, UserProfile(onboarded = true, tipsGiven = 3), emptyList(), null)
        assertEquals(3, codec.decode(codec.encode(backup))!!.profile.tipsGiven)
        val old = codec.encode(backup).replace(Regex("\"tipsGiven\"\\s*:\\s*3"), "\"x\": 0")
        assertEquals(0, codec.decode(old)!!.profile.tipsGiven)
    }

    @Test
    fun `product ids are unique and match Play Console`() {
        assertEquals(
            listOf("ollie_tip_small", "ollie_tip_coffee", "ollie_tip_lunch"),
            TipSize.entries.map { it.productId },
        )
    }
}
