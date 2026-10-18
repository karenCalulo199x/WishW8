package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.domain.model.SaleEvent
import com.karencalulo.wishwait.domain.model.SpendingCalendar
import com.karencalulo.wishwait.domain.model.SpendingContext
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.Tier
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.usecase.BuildVerdictUseCase
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PaydayTest {

    private val zone = TimeZone.getTimeZone("Asia/Manila")

    private fun day(month: Int, d: Int, hour: Int = 12): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, month - 1, d, hour, 0)
    }.timeInMillis

    @Test
    fun `double-date sales`() {
        assertEquals(SaleEvent(10, 10), SpendingCalendar.saleOn(day(10, 10), zone))
        assertTrue(SpendingCalendar.saleOn(day(11, 11), zone)!!.isMega)
        assertFalse(SpendingCalendar.saleOn(day(3, 3), zone)!!.isMega)
        assertNull(SpendingCalendar.saleOn(day(10, 9), zone))

        val ctx = SpendingCalendar.context(day(10, 9), emptyList(), zone)
        assertEquals(SaleEvent(10, 10), ctx.saleTomorrow)
        assertEquals(SaleEvent(10, 10), ctx.nextSale)
        assertEquals(SaleEvent(11, 11), SpendingCalendar.nextSaleAfter(day(10, 10), zone))
        assertEquals(SaleEvent(1, 1), SpendingCalendar.nextSaleAfter(day(12, 20), zone))
    }

    @Test
    fun `payday window on 15th and 30th`() {
        val paydays = listOf(15, 30)
        assertTrue(SpendingCalendar.context(day(10, 15), paydays, zone).inPaydayWindow)
        assertTrue(SpendingCalendar.context(day(10, 17), paydays, zone).inPaydayWindow)
        assertFalse(SpendingCalendar.context(day(10, 18), paydays, zone).inPaydayWindow)
        assertEquals(9, SpendingCalendar.context(day(10, 9), paydays, zone).daysSincePayday) // since Sep 30

        // "30th" in February falls on the last day.
        val feb = SpendingCalendar.lastPayday(day(3, 1), listOf(30), zone)!!
        val c = Calendar.getInstance(zone).apply { timeInMillis = feb }
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH))
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH))

        assertNull(SpendingCalendar.lastPayday(day(10, 9), emptyList(), zone))
    }

    @Test
    fun `ollie adds days on sale days and right after payday`() {
        val impact = CalculatePriceImpactUseCase()(3_000.0, UserProfile(monthlyIncome = 20_000.0))
        assertEquals(Tier.MEDIUM, impact.tier)
        val build = BuildVerdictUseCase()

        val normal = build(impact, emptySet(), emptySet(), Strictness.BALANCED)
        val mega = build(impact, emptySet(), emptySet(), Strictness.BALANCED, timing = SpendingContext(saleToday = SaleEvent(10, 10)))
        val payday = build(impact, emptySet(), emptySet(), Strictness.BALANCED, timing = SpendingContext(inPaydayWindow = true))
        val both = build(impact, emptySet(), emptySet(), Strictness.BALANCED, timing = SpendingContext(inPaydayWindow = true, saleToday = SaleEvent(3, 3)))

        assertEquals(7, normal.recommendedDays)
        assertEquals(10, mega.recommendedDays)
        assertEquals(9, payday.recommendedDays)
        assertEquals(11, both.recommendedDays)
        assertTrue(payday.paydayWindow)
    }
}
