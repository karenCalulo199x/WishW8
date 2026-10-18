package com.karencalulo.wishwait.data.remote

import com.karencalulo.wishwait.domain.repository.LinkPreview

/**
 * Reads the same public "preview" tags that chat apps use to show link cards
 * (Open Graph, product meta tags, JSON-LD). Pure text parsing, no JavaScript.
 */
object HtmlPreviewParser {

    private val META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z:_-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
    private val TITLE = Regex("""<title[^>]*>([^<]{1,300})</title>""", RegexOption.IGNORE_CASE)
    private val JSON_LD_PRICE = Regex(""""(?:price|lowPrice)"\s*:\s*"?(\d[\d.,]*)""")

    fun parse(html: String): LinkPreview? {
        val meta = mutableMapOf<String, String>()
        META.findAll(html).forEach { tag ->
            val attrs = ATTR.findAll(tag.value).associate { m ->
                m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value ?: "")
            }
            val key = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])?.lowercase() ?: return@forEach
            val content = attrs["content"] ?: return@forEach
            if (content.isNotBlank() && key !in meta) meta[key] = decode(content.trim())
        }

        val title = meta["og:title"]
            ?: meta["twitter:title"]
            ?: meta["name"]
            ?: TITLE.find(html)?.groupValues?.get(1)?.let { decode(it.trim()) }
        val price = meta["product:price:amount"]
            ?: meta["og:price:amount"]
            ?: meta["price"]
            ?: JSON_LD_PRICE.find(html)?.groupValues?.get(1)

        if (title.isNullOrBlank() && price.isNullOrBlank()) return null
        return LinkPreview(title = title?.takeIf { it.isNotBlank() }, priceText = price?.takeIf { it.isNotBlank() })
    }

    private fun decode(s: String): String = s
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}
