package com.karencalulo.wishwait.data

import com.karencalulo.wishwait.data.remote.CombinedLinkPreviewRepository
import com.karencalulo.wishwait.data.remote.HtmlPreviewParser
import com.karencalulo.wishwait.domain.repository.LinkPreview
import com.karencalulo.wishwait.domain.repository.LinkPreviewRepository
import com.karencalulo.wishwait.domain.usecase.FetchLinkDetailsUseCase
import com.karencalulo.wishwait.domain.usecase.ParseSharedTextUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewTest {

    @Test
    fun `reads open graph title and product price`() {
        val html = """
            <html><head>
            <meta property="og:title" content="Wireless Earbuds Pro &amp; Case | TikTok Shop">
            <meta property="product:price:amount" content="1,299.00">
            <title>ignored</title>
            </head></html>
        """.trimIndent()
        val preview = HtmlPreviewParser.parse(html)!!
        assertEquals("Wireless Earbuds Pro & Case | TikTok Shop", preview.title)
        assertEquals("1,299.00", preview.priceText)
    }

    @Test
    fun `falls back to title tag and json-ld price`() {
        val html = """<html><head><title>Mini Fan USB</title>
            <script type="application/ld+json">{"@type":"Product","offers":{"price":"249.5","priceCurrency":"PHP"}}</script>
            </head></html>"""
        val preview = HtmlPreviewParser.parse(html)!!
        assertEquals("Mini Fan USB", preview.title)
        assertEquals("249.5", preview.priceText)
    }

    @Test
    fun `nothing useful gives null`() {
        assertNull(HtmlPreviewParser.parse("<html><body>Please enable JavaScript</body></html>"))
    }

    @Test
    fun `fetch use case cleans the store suffix and parses the price`() = runBlocking {
        val fake = object : LinkPreviewRepository {
            override suspend fun fetch(url: String) =
                LinkPreview(title = "Wireless Earbuds Pro | TikTok Shop", priceText = "1,299.00")
        }
        val item = FetchLinkDetailsUseCase(fake, ParseSharedTextUseCase())("https://vt.tiktok.com/ZS1/")!!
        assertEquals("Wireless Earbuds Pro", item.name)
        assertEquals(1_299.0, item.price!!, 1e-9)

        val shopeeTitle = ParseSharedTextUseCase()("Logitech MX Master 3S | Shopee Philippines").name
        assertEquals("Logitech MX Master 3S", shopeeTitle)
    }

    @Test
    fun `hidden browser fills only what the quick read missed`() = runBlocking {
        var browserCalls = 0
        val quick = object : LinkPreviewRepository {
            override suspend fun fetch(url: String) = LinkPreview(title = "Foam Mattress Topper", priceText = null)
        }
        val browser = object : LinkPreviewRepository {
            override suspend fun fetch(url: String): LinkPreview {
                browserCalls++
                return LinkPreview(title = "Other title", priceText = "541")
            }
        }
        val merged = CombinedLinkPreviewRepository(quick, browser).fetch("https://s.shopee.ph/x")!!
        assertEquals("Foam Mattress Topper", merged.title)
        assertEquals("541", merged.priceText)
        assertEquals(1, browserCalls)

        // Complete quick read → browser never opened.
        val complete = object : LinkPreviewRepository {
            override suspend fun fetch(url: String) = LinkPreview("A", "1")
        }
        CombinedLinkPreviewRepository(complete, browser).fetch("https://x")
        assertEquals(1, browserCalls)
    }

    @Test
    fun `shop home page titles are never used as the product name`() = runBlocking {
        val parser = ParseSharedTextUseCase()
        listOf(
            "Shopee Philippines | Shop Online with Promos and Vouchers",
            "Lazada Philippines: Online Shopping",
            "TikTok - Make Your Day",
            "Log in | Shopee",
            "Just a moment...",
        ).forEach { assertEquals(it, true, parser.isGenericPageTitle(it)) }
        assertEquals(false, parser.isGenericPageTitle("Foam Mattress Topper Double Size"))

        // Quick read got the shop shell; the hidden browser saw the real product.
        val quick = object : LinkPreviewRepository {
            override suspend fun fetch(url: String) =
                LinkPreview(title = "Shopee Philippines | Shop Online with Promos and Vouchers", priceText = null)
        }
        val browser = object : LinkPreviewRepository {
            override suspend fun fetch(url: String) = LinkPreview(title = "Japanese Floor Mattress | Shopee Philippines", priceText = "541")
        }
        val repo = CombinedLinkPreviewRepository(quick, browser) { !parser.isGenericPageTitle(it) }
        val item = FetchLinkDetailsUseCase(repo, parser)("https://s.shopee.ph/8Kq94S7zeK")!!
        assertEquals("Japanese Floor Mattress", item.name)
        assertEquals(541.0, item.price!!, 1e-9)

        // Both only saw the shell → no name at all (user types it), never the shop title.
        val shellOnly = CombinedLinkPreviewRepository(quick, quick) { !parser.isGenericPageTitle(it) }
        assertNull(FetchLinkDetailsUseCase(shellOnly, parser)("https://s.shopee.ph/x"))
    }

    @Test
    fun `failed fetch returns null so the user types it`() = runBlocking {
        val offline = object : LinkPreviewRepository {
            override suspend fun fetch(url: String): LinkPreview? = null
        }
        assertNull(FetchLinkDetailsUseCase(offline, ParseSharedTextUseCase())("https://vt.tiktok.com/ZS1/"))
    }
}
