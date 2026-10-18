package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.isNeedPurchase
import com.karencalulo.wishwait.domain.model.reasonsAfterSwitch
import com.karencalulo.wishwait.domain.model.reasonsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedWantTest {

    @Test
    fun `the toggle and Ollie's need rule always agree`() {
        // Need shows only need reasons; any one of them makes it a need.
        reasonsFor(true).forEach { assertTrue(isNeedPurchase(setOf(it))) }
        // Want shows only want reasons; none of them can make it a need.
        assertFalse(isNeedPurchase(reasonsFor(false).toSet()))
        assertEquals(Reason.entries.size, reasonsFor(true).size + reasonsFor(false).size)
    }

    @Test
    fun `switching keeps only reasons that fit the new choice`() {
        val picked = setOf(Reason.REPLACE_BROKEN, Reason.ON_SALE)
        assertEquals(setOf(Reason.REPLACE_BROKEN), reasonsAfterSwitch(picked, toNeed = true))
        assertEquals(setOf(Reason.ON_SALE), reasonsAfterSwitch(picked, toNeed = false))
        // A need without a reason is not a need yet — the form asks "which one is it?".
        assertFalse(isNeedPurchase(emptySet()))
    }
}
