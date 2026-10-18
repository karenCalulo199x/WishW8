package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.domain.model.BuyAdvice
import com.karencalulo.wishwait.domain.model.BuyAdvisor
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DesireCheck
import com.karencalulo.wishwait.data.local.BackupJsonCodec
import com.karencalulo.wishwait.domain.model.Backup
import com.karencalulo.wishwait.domain.model.SaleEvent
import com.karencalulo.wishwait.domain.model.SaleKind
import com.karencalulo.wishwait.domain.model.SpendingCalendar
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BuyAdviceTest {

    private val zone = TimeZone.getTimeZone("Asia/Manila")

    private fun at(month: Int, d: Int, hour: Int = 12): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, month - 1, d, hour, 0)
    }.timeInMillis

    private fun dayStart(month: Int, d: Int) = at(month, d, 0)

    private fun wish(created: Long, readyAt: Long, vararg desire: Int, id: String = "w") = Wish(
        id = id, name = "Headphones", price = 3_000.0, category = Category.GADGETS,
        reasons = emptySet(), moods = emptySet(), note = "", createdAt = created,
        cooldownDays = 7, cooldownEndsAt = readyAt, status = WishStatus.WAITING,
        desireLog = desire.mapIndexed { i, level -> DesireCheck(created + i * 1_000L, level) },
    )

    @Test
    fun `real want, sale tomorrow, not squeezed against payday - buy on the sale`() {
        val w = wish(at(10, 1), at(10, 9, 9), 4)
        val advice = BuyAdvisor.advise(w, listOf(15, 30), at(10, 9), zone) as BuyAdvice.BuyOnSale
        assertEquals(SaleEvent(10, 10), advice.sale)
        assertEquals(1, advice.daysAway)
        assertEquals(8, advice.daysWanted)
        assertEquals(false, advice.rightAfterPayday)
    }

    @Test
    fun `sale in the last days before payday - wait for payday`() {
        val w = wish(at(12, 1), at(12, 11), 5)
        val advice = BuyAdvisor.advise(w, listOf(15), at(12, 10), zone) as BuyAdvice.WaitForPayday
        assertEquals(SaleEvent(12, 12), advice.sale)
        assertEquals(dayStart(12, 15), advice.paydayAt)
        assertEquals(SaleEvent(1, 1), advice.nextSale)
    }

    @Test
    fun `sale soon after payday - get both`() {
        val w = wish(at(11, 1), at(11, 8), 4)
        val advice = BuyAdvisor.advise(w, listOf(5), at(11, 9), zone) as BuyAdvice.BuyOnSale
        assertEquals(SaleEvent(11, 11), advice.sale)
        assertTrue(advice.rightAfterPayday)
    }

    @Test
    fun `faded want - let it go, no sale talk`() {
        val w = wish(at(10, 1), at(10, 9, 9), 5, 2)
        assertEquals(BuyAdvice.LetItGo(5, 2), BuyAdvisor.advise(w, listOf(15, 30), at(10, 9), zone))
    }

    @Test
    fun `nothing to say when the wait is far off or no sale is near`() {
        assertNull(BuyAdvisor.advise(wish(at(10, 1), at(10, 25), 5), listOf(15, 30), at(10, 9), zone))
        assertNull(BuyAdvisor.advise(wish(at(10, 1), at(10, 12), 5), listOf(15, 30), at(10, 12, 15), zone))
        assertNull(BuyAdvisor.advise(wish(at(10, 1), at(10, 9), 1, 1).copy(status = WishStatus.BOUGHT), emptyList(), at(10, 9), zone))
    }

    @Test
    fun `eve nudges for tomorrow's sale and payday`() {
        val ready = wish(at(10, 1), at(10, 9, 9), 4, id = "a")
        val faded = wish(at(10, 1), at(10, 9, 9), 5, 1, id = "b")
        val sale = BuyAdvisor.eveNudges(listOf(ready, faded), UserProfile(paydays = listOf(15, 30), paydaySales = emptyList()), at(10, 9, 19), zone)
        assertEquals(listOf("a"), sale.map { it.wish.id })
        assertEquals(SaleEvent(10, 10), sale.single().sale)

        val recent = wish(at(10, 1), at(10, 12), 5, id = "c")
        val stale = wish(at(8, 1), at(9, 1), 5, id = "d")
        val payday = BuyAdvisor.eveNudges(listOf(recent, stale), UserProfile(paydays = listOf(15), paydaySales = emptyList()), at(10, 14, 19), zone)
        assertEquals(listOf("c"), payday.map { it.wish.id })
        assertNull(payday.single().sale)
    }

    @Test
    fun `next eve target is the nearest sale or payday`() {
        assertEquals(dayStart(10, 10), BuyAdvisor.nextEveTarget(at(10, 9), listOf(15, 30), zone))
        assertEquals(dayStart(10, 15), BuyAdvisor.nextEveTarget(at(10, 10), listOf(15, 30), zone))
        assertEquals(dayStart(11, 11), BuyAdvisor.nextEveTarget(at(10, 10), emptyList(), zone))
    }

    @Test
    fun `payday sales on the 15th and 30th can be switched on and off`() {
        val both = listOf(15, 30)
        assertEquals(SaleEvent(10, 15, SaleKind.PAYDAY_MID), SpendingCalendar.saleOn(at(10, 15), zone, both))
        assertEquals(SaleEvent(10, 30, SaleKind.PAYDAY_END), SpendingCalendar.saleOn(at(10, 30), zone, both))
        assertEquals(SaleEvent(2, 28, SaleKind.PAYDAY_END), SpendingCalendar.saleOn(at(2, 28), zone, both))
        assertNull(SpendingCalendar.saleOn(at(10, 15), zone, emptyList()))
        assertNull(SpendingCalendar.saleOn(at(10, 30), zone, listOf(15)))
        assertFalse(SaleEvent(12, 15, SaleKind.PAYDAY_MID).isMega)
        assertEquals(SaleEvent(10, 15, SaleKind.PAYDAY_MID), SpendingCalendar.nextSaleAfter(at(10, 10), zone, both))
        assertEquals(dayStart(10, 15), BuyAdvisor.nextEveTarget(at(10, 10), emptyList(), zone, both))
    }

    @Test
    fun `payday sale on payday itself is a good day for a real want`() {
        val w = wish(at(10, 1), at(10, 13), 5)
        val advice = BuyAdvisor.advise(w, listOf(15, 30), at(10, 13), zone, paydaySales = listOf(15, 30)) as BuyAdvice.BuyOnSale
        assertEquals(SaleKind.PAYDAY_MID, advice.sale.kind)
        assertTrue(advice.rightAfterPayday)
    }

    @Test
    fun `payday sales default on and survive backup`() {
        assertEquals(listOf(15, 30), UserProfile().paydaySales)
        val codec = BackupJsonCodec()
        val off = UserProfile(onboarded = true, paydaySales = emptyList())
        val back = codec.decode(codec.encode(Backup(Backup.CURRENT_VERSION, 1L, off, emptyList(), null)))!!
        assertEquals(emptyList<Int>(), back.profile.paydaySales)
    }
}
