package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.WidgetSummary
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTest {

    private val now = 100 * DAY_MS

    private fun wish(id: String, price: Double, endsInDays: Int, status: WishStatus = WishStatus.WAITING) = Wish(
        id = id, name = id, price = price, category = Category.OTHER, reasons = emptySet(), moods = emptySet(),
        note = "", createdAt = now - 10 * DAY_MS, cooldownDays = 10, cooldownEndsAt = now + endsInDays * DAY_MS,
        status = status,
    )

    @Test
    fun `ready wish comes first, then the soonest countdown`() {
        val s = WidgetSummary.of(listOf(wish("later", 1.0, 9), wish("soon", 1.0, 2), wish("ready", 1.0, -1)), now)
        assertEquals("ready", s.nextWish!!.id)
        assertTrue(s.nextIsReady)
        assertEquals(3, s.waitingCount)

        val waitingOnly = WidgetSummary.of(listOf(wish("later", 1.0, 9), wish("soon", 1.0, 2)), now)
        assertEquals("soon", waitingOnly.nextWish!!.id)
        assertFalse(waitingOnly.nextIsReady)
    }

    @Test
    fun `kept total counts only let-go wishes`() {
        val s = WidgetSummary.of(
            listOf(wish("a", 1_500.0, 0, WishStatus.LET_GO), wish("b", 900.0, 0, WishStatus.BOUGHT), wish("c", 200.0, 0, WishStatus.LET_GO)),
            now,
        )
        assertEquals(1_700.0, s.keptTotal, 1e-9)
        assertNull(s.nextWish)
        assertEquals(0, s.waitingCount)
    }
}
