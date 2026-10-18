package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.data.local.BackupJsonCodec
import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.ReminderTiming
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FixesTest {

    private val zone = TimeZone.getTimeZone("Asia/Manila")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute)
    }.timeInMillis

    @Test
    fun `night reminders move to 9 AM, daytime ones stay`() {
        assertEquals(at(10, 9), ReminderTiming.friendly(at(9, 23, 58), zone)) // late night → next morning
        assertEquals(at(9, 9), ReminderTiming.friendly(at(9, 2, 15), zone)) // early morning → same morning
        assertEquals(at(9, 14, 30), ReminderTiming.friendly(at(9, 14, 30), zone)) // afternoon unchanged
        assertEquals(at(9, 9), ReminderTiming.morningOf(at(9, 18), zone))
    }

    @Test
    fun `old wishes keep the income they were judged with`() {
        val wish = Wish(
            id = "w", name = "Phone", price = 100_000.0, category = Category.GADGETS, reasons = emptySet(),
            moods = emptySet(), note = "", createdAt = 0, cooldownDays = 30, cooldownEndsAt = 1,
            status = WishStatus.WAITING, incomeAtCreation = 20_000.0, hoursAtCreation = 160,
        )
        val afterRaise = UserProfile(monthlyIncome = 50_000.0, hoursPerMonth = 160)
        val impact = CalculatePriceImpactUseCase()(wish.price, wish.profileAtCreation(afterRaise))
        assertEquals(5.0, impact.incomeRatio, 1e-9)

        val legacy = wish.copy(incomeAtCreation = null, hoursAtCreation = null)
        assertEquals(2.0, CalculatePriceImpactUseCase()(legacy.price, legacy.profileAtCreation(afterRaise)).incomeRatio, 1e-9)
    }

    @Test
    fun `backup round trip keeps profile and wishes`() {
        val codec = BackupJsonCodec()
        val wish = Wish(
            id = "a", name = "Bike", price = 15_000.0, category = Category.VEHICLE, reasons = emptySet(),
            moods = emptySet(), note = "red one", createdAt = 5, cooldownDays = 14, cooldownEndsAt = 99,
            status = WishStatus.WAITING, incomeAtCreation = 30_000.0, hoursAtCreation = 170,
        )
        val backup = Backup(
            version = Backup.CURRENT_VERSION,
            exportedAt = 123L,
            profile = UserProfile(
                monthlyIncome = 30_000.0, hoursPerMonth = 170, monthlySavings = 5_000.0,
                currencyCode = "PHP", strictness = Strictness.STRICT, onboarded = true, remindersEnabled = false,
            ),
            wishes = listOf(wish),
        )
        assertEquals(backup, codec.decode(codec.encode(backup)))
        assertNull(codec.decode("""{"hello":"world"}"""))
        assertNull(codec.decode("not json"))
    }
}
