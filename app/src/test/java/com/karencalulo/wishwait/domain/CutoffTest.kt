package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.domain.model.BuyAdvice
import com.karencalulo.wishwait.domain.model.BuyAdvisor
import com.karencalulo.wishwait.domain.model.Category
import com.karencalulo.wishwait.domain.model.Cutoff
import com.karencalulo.wishwait.domain.model.CutoffCalculator
import com.karencalulo.wishwait.domain.model.CutoffSpending
import com.karencalulo.wishwait.domain.model.DesireCheck
import com.karencalulo.wishwait.domain.model.PaymentPlan
import com.karencalulo.wishwait.domain.model.Reason
import com.karencalulo.wishwait.domain.model.SaleEvent
import com.karencalulo.wishwait.domain.model.SpendingContext
import com.karencalulo.wishwait.domain.model.Strictness
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.domain.model.Wish
import com.karencalulo.wishwait.domain.model.WishStatus
import com.karencalulo.wishwait.domain.usecase.BuildVerdictUseCase
import com.karencalulo.wishwait.domain.usecase.CalculatePriceImpactUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CutoffTest {

    private val zone = TimeZone.getTimeZone("Asia/Manila")

    private fun at(month: Int, d: Int, hour: Int = 12): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, month - 1, d, hour, 0)
    }.timeInMillis

    private fun dayStart(month: Int, d: Int) = at(month, d, 0)

    private fun wish(
        id: String,
        price: Double,
        status: WishStatus = WishStatus.WAITING,
        decidedAt: Long? = null,
        reasons: Set<Reason> = emptySet(),
        created: Long = at(10, 1),
        readyAt: Long = at(10, 9, 9),
        payment: PaymentPlan? = null,
    ) = Wish(
        id = id, name = id, price = price, category = Category.OTHER, reasons = reasons, moods = emptySet(),
        note = "", createdAt = created, cooldownDays = 7, cooldownEndsAt = readyAt, status = status,
        decidedAt = decidedAt, desireLog = listOf(DesireCheck(created, 5)), payment = payment,
    )

    private val profile = UserProfile(
        monthlyIncome = 20_000.0,
        paydays = listOf(15, 30),
        paydaySales = emptyList(),
        strictness = Strictness.BALANCED,
        onboarded = true,
    )

    @Test
    fun `cutoffs run from payday to the next payday`() {
        val p = listOf(15, 30)
        assertEquals(Cutoff(dayStart(10, 15), dayStart(10, 30)), CutoffCalculator.cutoffAt(at(10, 20), p, zone))
        assertEquals(Cutoff(dayStart(9, 30), dayStart(10, 15)), CutoffCalculator.cutoffAt(at(10, 9), p, zone))
        assertEquals(Cutoff(dayStart(2, 15), dayStart(2, 28)), CutoffCalculator.cutoffAt(at(2, 20), p, zone))
        assertEquals(Cutoff(dayStart(2, 28), dayStart(3, 15)), CutoffCalculator.cutoffAt(at(3, 1), p, zone))
        // No payday set: the calendar month is the cutoff.
        assertEquals(Cutoff(dayStart(10, 1), dayStart(11, 1)), CutoffCalculator.cutoffAt(at(10, 20), emptyList(), zone))
    }

    @Test
    fun `cutoff pay splits income across paydays`() {
        assertEquals(10_000.0, CutoffCalculator.cutoffPay(profile), 1e-9)
        assertEquals(20_000.0, CutoffCalculator.cutoffPay(profile.copy(paydays = emptyList())), 1e-9)
        assertEquals(0.0, CutoffCalculator.cutoffPay(profile.copy(monthlyIncome = 0.0)), 1e-9)
    }

    @Test
    fun `only wants bought in the cutoff count, installments by due date, never twice`() {
        val cutoff = CutoffCalculator.cutoffAt(at(10, 20), profile.paydays, zone) // Oct 15 – Oct 30
        val wishes = listOf(
            wish("want", 2_000.0, WishStatus.BOUGHT, decidedAt = at(10, 16)),
            wish("need", 5_000.0, WishStatus.BOUGHT, decidedAt = at(10, 16), reasons = setOf(Reason.REPLACE_BROKEN)),
            wish("letgo", 9_000.0, WishStatus.LET_GO, decidedAt = at(10, 16)),
            wish("earlier", 3_000.0, WishStatus.BOUGHT, decidedAt = at(10, 10)),
            wish("waiting", 4_000.0),
            // Bought on installment this cutoff: counts the Oct 20 payment, not the full price.
            wish(
                "phone", 12_000.0, WishStatus.BOUGHT, decidedAt = at(10, 16),
                payment = PaymentPlan(months = 3, monthlyPayment = 1_000.0, firstDueAt = at(9, 20)),
            ),
        )
        assertEquals(3_000.0, CutoffCalculator.spentIn(wishes, cutoff), 1e-9)
    }

    @Test
    fun `strictness sets the line, going over adds a flat 3 days for wants only`() {
        assertEquals(0.40, Strictness.CHILL.wantsShare, 1e-9)
        assertEquals(0.30, Strictness.BALANCED.wantsShare, 1e-9)
        assertEquals(0.20, Strictness.STRICT.wantsShare, 1e-9)

        val impact = CalculatePriceImpactUseCase()(1_000.0, profile)
        val build = BuildVerdictUseCase()
        fun cutoff(spent: Double, share: Double = 0.30) = CutoffSpending(Cutoff(0, 1), 10_000.0, spent, share)

        val plain = build(impact, emptySet(), emptySet(), Strictness.BALANCED)
        val under = build(impact, emptySet(), emptySet(), Strictness.BALANCED, cutoff = cutoff(1_000.0))
        val over = build(impact, emptySet(), emptySet(), Strictness.BALANCED, cutoff = cutoff(2_500.0))
        assertEquals(plain.recommendedDays, under.recommendedDays)
        assertEquals(0.20, under.cutoffShareWith!!, 1e-9)
        assertFalse(under.overCutoffLine)
        assertEquals(plain.recommendedDays + 3, over.recommendedDays)
        assertTrue(over.overCutoffLine)
        assertEquals(3, over.extraDaysForCutoff)

        // Strict line is 20%: 1,500 + 1,000 = 25% is over.
        val strict = build(impact, emptySet(), emptySet(), Strictness.STRICT, cutoff = cutoff(1_500.0, 0.20))
        val strictPlain = build(impact, emptySet(), emptySet(), Strictness.STRICT)
        assertEquals(strictPlain.recommendedDays + 3, strict.recommendedDays)

        // Real needs: no cutoff days, no sale/payday days.
        val need = setOf(Reason.REPLACE_BROKEN)
        val needPlain = build(impact, need, emptySet(), Strictness.BALANCED)
        val needBusy = build(
            impact, need, emptySet(), Strictness.BALANCED,
            timing = SpendingContext(inPaydayWindow = true, saleToday = SaleEvent(10, 10)),
            cutoff = cutoff(9_000.0),
        )
        assertEquals(needPlain.recommendedDays, needBusy.recommendedDays)
        assertNull(needBusy.cutoffShareWith)
        assertEquals(0, needBusy.extraDaysForTiming)
    }

    @Test
    fun `two wishes lined up for the same sale are spread across cutoffs`() {
        val bag = wish("Bag", 2_000.0, created = at(10, 1))
        val shoes = wish("Shoes", 1_500.0, created = at(10, 3))
        val tips = BuyAdvisor.adviseAll(listOf(shoes, bag), profile, at(10, 9), zone)

        assertTrue(tips["Bag"] is BuyAdvice.BuyOnSale) // wanted longest, keeps 10.10
        val spread = tips["Shoes"] as BuyAdvice.SpreadOut
        assertEquals("Bag", spread.otherName)
        assertEquals(0.35, spread.combinedShare, 1e-9)
        assertEquals(dayStart(10, 15), spread.buyFrom)

        // Chill's 40% line fits both.
        val chill = BuyAdvisor.adviseAll(listOf(shoes, bag), profile.copy(strictness = Strictness.CHILL), at(10, 9), zone)
        assertTrue(chill["Shoes"] is BuyAdvice.BuyOnSale)

        // Evening before 10.10: only the bag gets the sale nudge.
        val sale = BuyAdvisor.eveNudges(listOf(shoes, bag), profile, at(10, 9, 19), zone)
        assertEquals(listOf("Bag"), sale.map { it.wish.id })

        // Evening before the Oct 15 payday (bag bought on 10.10): the shoes' turn.
        val boughtBag = bag.copy(status = WishStatus.BOUGHT, decidedAt = at(10, 10))
        val payday = BuyAdvisor.eveNudges(listOf(shoes, boughtBag), profile, at(10, 14, 19), zone)
        assertEquals(listOf("Shoes"), payday.map { it.wish.id })
        assertNull(payday.single().sale)
    }

    @Test
    fun `needs get timing tips, never let-it-go`() {
        val need = wish("Laptop charger", 1_200.0, reasons = setOf(Reason.REPLACE_BROKEN))
            .let { it.copy(desireLog = listOf(DesireCheck(at(10, 1), 5), DesireCheck(at(10, 5), 2))) }
        assertTrue(BuyAdvisor.advise(need, profile.paydays, at(10, 9), zone) is BuyAdvice.BuyOnSale)
        val want = need.copy(reasons = emptySet())
        assertTrue(BuyAdvisor.advise(want, profile.paydays, at(10, 9), zone) is BuyAdvice.LetItGo)
    }
}
