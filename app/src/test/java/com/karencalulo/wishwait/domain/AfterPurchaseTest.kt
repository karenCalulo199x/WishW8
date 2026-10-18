package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.data.local.WishJsonMapper
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.PaymentPlan
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.usecase.BuildVerdictUseCase
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import com.karencalulo.wishwait.domain.usecase.CalculateWorthUseCase
import com.karencalulo.wishwait.domain.usecase.LearnFromHistoryUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AfterPurchaseTest {

    private val profile = UserProfile(monthlyIncome = 20_000.0, hoursPerMonth = 160) // 125/hour

    private fun bought(
        id: String = "x",
        price: Double = 1_000.0,
        category: Category = Category.GADGETS,
        glad: Boolean? = null,
        uses: Int = 0,
        target: Double? = null,
        payment: PaymentPlan? = null,
    ) = Wish(
        id = id, name = id, price = price, category = category, reasons = emptySet(), moods = emptySet(),
        note = "", createdAt = 0L, cooldownDays = 3, cooldownEndsAt = 3 * DAY_MS, status = WishStatus.BOUGHT,
        decidedAt = 3 * DAY_MS, gladRating = glad, usageLog = List(uses) { it.toLong() },
        worthTargetPerUse = target, payment = payment,
    )

    @Test
    fun `worth defaults to one hour of work per use`() {
        val worth = CalculateWorthUseCase()(bought(price = 1_000.0, uses = 2), profile)
        assertEquals(125.0, worth.targetPerUse, 1e-9)
        assertEquals(8, worth.targetUses)
        assertEquals(500.0, worth.costPerUse, 1e-9)
        assertEquals(6, worth.usesLeft)
        assertFalse(worth.isWorthIt)

        val done = CalculateWorthUseCase()(bought(price = 1_000.0, uses = 8), profile)
        assertTrue(done.isWorthIt)
        assertEquals(1f, done.progress)
    }

    @Test
    fun `custom target and installment interest count toward cost`() {
        val plan = PaymentPlan(months = 10, monthlyPayment = 120.0, firstDueAt = 0L)
        val worth = CalculateWorthUseCase()(bought(price = 1_000.0, target = 50.0, payment = plan), profile)
        assertEquals(1_200.0, worth.costBasis, 1e-9)
        assertEquals(24, worth.targetUses)
    }

    @Test
    fun `payment plan due dates follow calendar months`() {
        val start = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 15, 9, 0, 0) }.timeInMillis
        val plan = PaymentPlan(months = 3, monthlyPayment = 100.0, firstDueAt = start, paidCount = 1)
        val next = Calendar.getInstance().apply { timeInMillis = plan.nextDueAt!! }
        assertEquals(Calendar.FEBRUARY, next.get(Calendar.MONTH))
        assertEquals(15, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(200.0, plan.remaining, 1e-9)
        assertNull(plan.copy(paidCount = 3).nextDueAt)
    }

    @Test
    fun `regretted categories get a longer cooldown`() {
        val learn = LearnFromHistoryUseCase()
        val history = listOf(
            bought("a", glad = false),
            bought("b", glad = false),
            bought("c", glad = true),
            bought("d", category = Category.FASHION, glad = false),
        )
        val gadgets = learn(history, Category.GADGETS)
        assertEquals(2, gadgets.regretted)
        assertEquals(7, gadgets.bonusDays)
        assertEquals(3, learn(history, Category.FASHION).bonusDays)
        assertEquals(0, learn(history, Category.TRAVEL).bonusDays)
        assertEquals(0, learn(listOf(bought("e", glad = true)), Category.GADGETS).bonusDays)

        val impact = CalculatePriceImpactUseCase()(3_000.0, profile) // MEDIUM -> 7 days
        val verdict = BuildVerdictUseCase()(impact, setOf(Reason.UPGRADE), emptySet(), Strictness.BALANCED, gadgets)
        assertEquals(14, verdict.recommendedDays)
    }

    @Test
    fun `after-purchase fields survive storage`() {
        val wish = bought(
            uses = 3,
            target = 42.5,
            glad = true,
            payment = PaymentPlan(months = 6, monthlyPayment = 200.0, firstDueAt = 99L, paidCount = 2),
        )
        assertEquals(listOf(wish), WishJsonMapper.decode(WishJsonMapper.encode(listOf(wish))))
    }
}
