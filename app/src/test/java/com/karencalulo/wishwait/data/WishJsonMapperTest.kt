package com.karencalulo.wishwait.data

import com.karencalulo.wishwait.data.local.WishJsonMapper
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DesireCheck
import com.karencalulo.wishwait.domain.model.Mood
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WishJsonMapperTest {

    @Test
    fun `round trip keeps every field`() {
        val wish = Wish(
            id = "abc",
            name = "Phone \"Pro\"",
            price = 99_999.5,
            category = Category.GADGETS,
            reasons = setOf(Reason.UPGRADE, Reason.ON_SALE),
            moods = setOf(Mood.BORED),
            note = "Saw it at the mall",
            createdAt = 1L,
            cooldownDays = 30,
            cooldownEndsAt = 2L,
            status = WishStatus.BOUGHT,
            decidedAt = 3L,
            desireLog = listOf(DesireCheck(1L, 5), DesireCheck(2L, 3)),
            gladRating = true,
        )
        val decoded = WishJsonMapper.decode(WishJsonMapper.encode(listOf(wish)))
        assertEquals(listOf(wish), decoded)
    }

    @Test
    fun `garbage input gives an empty list instead of crashing`() {
        assertTrue(WishJsonMapper.decode("not json").isEmpty())
        assertTrue(WishJsonMapper.decode(null).isEmpty())
    }
}
