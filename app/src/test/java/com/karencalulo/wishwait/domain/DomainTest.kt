package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.domain.model.Achievement
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.DAY_MS
import com.karencalulo.wishwait.domain.model.DesireCheck
import com.karencalulo.wishwait.domain.model.Mood
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.Tier
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.usecase.AffordabilityCalculator
import com.karencalulo.wishwait.domain.usecase.BuildVerdictUseCase
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import com.karencalulo.wishwait.domain.usecase.ComputeStatsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    private val profile = UserProfile(monthlyIncome = 20_000.0, hoursPerMonth = 160, monthlySavings = 4_000.0)
    private val impact = CalculatePriceImpactUseCase()
    private val verdict = BuildVerdictUseCase()
    private val calc = AffordabilityCalculator()

    @Test
    fun `iPhone 100K on 20K salary is 5x income and a 30-day cooldown`() {
        val i = impact(100_000.0, profile)
        assertEquals(5.0, i.incomeRatio, 1e-9)
        assertEquals(800.0, i.workHours, 1e-9)
        assertEquals(100.0, i.workDays, 1e-9)
        assertEquals(25.0, i.monthsToSave!!, 1e-9)
        assertEquals(Tier.MASSIVE, i.tier)

        val v = verdict(i, setOf(Reason.JUST_WANT_IT), emptySet(), Strictness.BALANCED)
        assertEquals(30, v.recommendedDays)
    }

    @Test
    fun `risky mood lengthens the cooldown`() {
        val i = impact(3_000.0, profile) // 15% -> MEDIUM, 7 days
        assertEquals(Tier.MEDIUM, i.tier)
        val calm = verdict(i, emptySet(), setOf(Mood.CALM), Strictness.BALANCED)
        val bored = verdict(i, emptySet(), setOf(Mood.BORED), Strictness.BALANCED)
        assertEquals(7, calm.recommendedDays)
        assertEquals(11, bored.recommendedDays)
        assertEquals(4, bored.extraDaysForMood)
    }

    @Test
    fun `real need halves the wait and tiny calm needs need no wait`() {
        val big = impact(10_000.0, profile) // 50% -> BIG, 21 days
        val need = verdict(big, setOf(Reason.REPLACE_BROKEN), emptySet(), Strictness.BALANCED)
        assertEquals(11, need.recommendedDays)

        val tiny = impact(200.0, profile) // 1% -> TREAT
        val v = verdict(tiny, setOf(Reason.REPLACE_BROKEN), emptySet(), Strictness.BALANCED)
        assertEquals(0, v.recommendedDays)
    }

    @Test
    fun `strict mode lengthens and chill shortens`() {
        val i = impact(100_000.0, profile)
        assertEquals(45, verdict(i, emptySet(), emptySet(), Strictness.STRICT).recommendedDays)
        assertEquals(15, verdict(i, emptySet(), emptySet(), Strictness.CHILL).recommendedDays)
    }

    @Test
    fun `zero income does not crash`() {
        val i = impact(500.0, UserProfile())
        assertEquals(Tier.MASSIVE, i.tier)
        assertNull(i.monthsToSave)
    }

    @Test
    fun `installment math`() {
        val zero = calc.installment(12_000.0, 12, 0.0, 20_000.0)!!
        assertEquals(1_000.0, zero.monthlyPayment, 1e-9)
        assertEquals(0.05, zero.shareOfIncome, 1e-9)

        val withInterest = calc.installment(100_000.0, 12, 24.0, 20_000.0)!!
        // 2%/month amortized over 12 months
        assertEquals(9_455.96, withInterest.monthlyPayment, 0.01)
        assertTrue(withInterest.interestPaid > 13_000)
    }

    @Test
    fun `savings plan, cost per use and opportunity cost`() {
        val plan = calc.savingsPlan(100_000.0, 10_000.0, 4_000.0)!!
        assertEquals(23, plan.months)
        assertEquals(0, calc.savingsPlan(5_000.0, 6_000.0, 0.0)!!.months)
        assertNull(calc.savingsPlan(5_000.0, 0.0, 0.0))

        val cpu = calc.costPerUse(52_000.0, 1.0, 2.0)!!
        assertEquals(104, cpu.totalUses)
        assertEquals(500.0, cpu.perUse, 1e-9)

        val opp = calc.opportunityCost(10_000.0, 10.0, 2)!!
        assertEquals(12_100.0, opp.futureValue, 1e-6)
    }

    @Test
    fun `stats and achievements`() {
        val now = 1_000_000_000_000L
        fun wish(id: String, price: Double, status: WishStatus, days: Int, decidedAt: Long?) = Wish(
            id = id, name = id, price = price, category = Category.GADGETS, reasons = emptySet(),
            moods = emptySet(), note = "", createdAt = now, cooldownDays = days,
            cooldownEndsAt = now + days * DAY_MS, status = status, decidedAt = decidedAt,
            desireLog = listOf(DesireCheck(now, 5), DesireCheck(now + DAY_MS, 2)),
        )
        val wishes = listOf(
            wish("a", 25_000.0, WishStatus.LET_GO, 30, now + 30 * DAY_MS),
            wish("b", 1_000.0, WishStatus.BOUGHT, 3, now + 3 * DAY_MS),
            wish("c", 2_000.0, WishStatus.BOUGHT, 7, now + DAY_MS),
            wish("d", 5_000.0, WishStatus.WAITING, 7, null),
        )
        val s = ComputeStatsUseCase()(wishes, profile)
        assertEquals(25_000.0, s.savedTotal, 1e-9)
        assertEquals(1, s.letGoCount)
        assertEquals(1, s.mindfulBuys)
        assertEquals(1, s.impulseBuys)
        assertEquals(5_000.0, s.onHoldTotal, 1e-9)
        assertEquals(200.0, s.hoursSaved, 1e-9)
        assertTrue(Achievement.PATIENCE_PRO in s.unlocked)
        assertTrue(Achievement.MONTH_OF_FREEDOM in s.unlocked)
        assertTrue(Achievement.FEELINGS_FADE in s.unlocked)
        assertNotNull(s.unlocked.firstOrNull { it == Achievement.FIRST_PAUSE })
    }
}
