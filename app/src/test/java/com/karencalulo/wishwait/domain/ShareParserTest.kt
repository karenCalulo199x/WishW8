package com.karencalulo.wishwait.domain

import com.karencalulo.wishwait.domain.usecase.ParseSharedTextUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareParserTest {

    private val parse = ParseSharedTextUseCase()

    @Test
    fun `shopee style share`() {
        val item = parse("Check out this product on Shopee! Apple iPhone 15 128GB ₱49,990.00 https://shp.ee/abc123")
        assertEquals("Apple iPhone 15 128GB", item.name)
        assertEquals(49_990.0, item.price!!, 1e-9)
        assertEquals("https://shp.ee/abc123", item.link)
        assertEquals("Shopee", item.store)
    }

    @Test
    fun `lazada style share without price`() {
        val item = parse("I found this on Lazada: Samsung Galaxy Buds2 Pro https://s.lazada.com.ph/s.abcDE")
        assertEquals("Samsung Galaxy Buds2 Pro", item.name)
        assertNull(item.price)
        assertEquals("Lazada", item.store)
    }

    @Test
    fun `subject wins when it is a real title`() {
        val item = parse("https://www.amazon.com/dp/B0C1 \$129.99", subject = "Sony WH-1000XM5 Headphones")
        assertEquals("Sony WH-1000XM5 Headphones", item.name)
        assertEquals(129.99, item.price!!, 1e-9)
        assertEquals("Amazon", item.store)
    }

    @Test
    fun `plain text and unknown store`() {
        val item = parse("Nike Air Force 1 - PHP 5,495 https://shop.example.ph/p/123")
        assertEquals("Nike Air Force 1", item.name)
        assertEquals(5_495.0, item.price!!, 1e-9)
        assertEquals("Example", item.store)
    }

    @Test
    fun `check out prefix from shopee and lazada is removed`() {
        val shopee = parse("Check out Apple iPhone 15 128GB for ₱49,990.00 on Shopee! https://shp.ee/x1")
        assertEquals("Apple iPhone 15 128GB", shopee.name)
        assertEquals(49_990.0, shopee.price!!, 1e-9)

        val lazada = parse("Check out Samsung Galaxy Buds2 Pro on Lazada https://s.lazada.com.ph/s.x2")
        assertEquals("Samsung Galaxy Buds2 Pro", lazada.name)

        val subjectOnly = parse("https://s.lazada.com.ph/s.x3", subject = "Check out Xiaomi Redmi Note 13")
        assertEquals("Xiaomi Redmi Note 13", subjectOnly.name)

        val colon = parse("Check out: Nike Dunk Low https://shp.ee/x4")
        assertEquals("Nike Dunk Low", colon.name)

        // "the" that belongs to the brand must survive
        val brand = parse("Check out The North Face Jacket on Lazada https://s.lazada.com.ph/s.x5")
        assertEquals("The North Face Jacket", brand.name)
    }

    @Test
    fun `tiktok shares only a link`() {
        val promo = parse("Check out this product on TikTok Shop! https://vt.tiktok.com/ZSabc123/")
        assertEquals("", promo.name)
        assertNull(promo.price)
        assertEquals("TikTok Shop", promo.store)

        val bare = parse("https://vt.tiktok.com/ZSabc123/")
        assertEquals("", bare.name)
        assertEquals("https://vt.tiktok.com/ZSabc123/", bare.link)
    }

    @Test
    fun `copied shopee short link`() {
        val item = parse("https://s.shopee.ph/8Kq94S7zeK")
        assertEquals("", item.name)
        assertEquals("https://s.shopee.ph/8Kq94S7zeK", item.link)
        assertEquals("Shopee", item.store)
    }

    @Test
    fun `number formats`() {
        assertEquals(1299.5, parse.parseNumber("1,299.50")!!, 1e-9)
        assertEquals(1299.5, parse.parseNumber("1.299,50")!!, 1e-9)
        assertEquals(150_000.0, parse.parseNumber("150.000")!!, 1e-9)
        assertEquals(12.99, parse.parseNumber("12.99")!!, 1e-9)
        assertEquals(1_299.0, parse.parseNumber("1,299")!!, 1e-9)
    }

    @Test
    fun `model numbers are not mistaken for prices`() {
        val item = parse("Huawei P30 Pro https://shopee.ph/x")
        assertNull(item.price)
        assertEquals("Huawei P30 Pro", item.name)
    }
}
