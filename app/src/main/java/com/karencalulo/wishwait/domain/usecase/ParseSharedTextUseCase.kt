package com.karencalulo.wishwait.domain.usecase

import java.net.URI

/** What we could read from a "Share" coming out of a shopping app. Anything may be missing. */
data class SharedItem(
    val name: String,
    val price: Double?,
    val link: String?,
    val store: String?,
)

/**
 * Reads the plain text a shopping app shares (Shopee, Lazada, TikTok Shop, Amazon…).
 * Rule-based and fully offline: no web requests, no AI. Whatever it can't find, the user fills in.
 */
class ParseSharedTextUseCase {

    operator fun invoke(text: String?, subject: String? = null): SharedItem {
        val body = text.orEmpty().trim()
        val link = URL_REGEX.find(body)?.value?.trimEnd('.', ',', ')', ']')
        val price = findPrice(body) ?: subject?.let { findPrice(it) }
        val name = cleanName(subject?.takeIf { it.isNotBlank() && !isGeneric(it) } ?: body)
        return SharedItem(
            name = name,
            price = price,
            link = link,
            store = link?.let { storeFromLink(it) },
        )
    }

    // ---- Name ----

    private fun cleanName(raw: String): String {
        var s = raw
        s = URL_REGEX.replace(s, " ")
        s = PRICE_REGEX.replace(s, " ")
        BOILERPLATE.forEach { s = it.replace(s, " ") }
        s = s.replace(Regex("""#\S+"""), " ") // hashtags
            .replace(Regex("""[\s ]+"""), " ")
            .trim(' ', '-', '|', ':', '!', '.', ',', '"', '\'', '“', '”')
        // Shopee & Lazada titles start with "Check out <item>" — keep only the item.
        s = LEADING_PROMPT.replace(s, "")
        // Words left dangling after the price/store were removed ("… for", "… on").
        while (true) {
            val trimmed = TRAILING_FILLER.replace(s, "").trim(' ', '-', '|', ':', '!', '.', ',')
            if (trimmed == s) break
            s = trimmed
        }
        return if (s.length > MAX_NAME) s.take(MAX_NAME).substringBeforeLast(' ').trimEnd() else s
    }

    private fun isGeneric(subject: String): Boolean =
        BOILERPLATE.any { it.matches(subject.trim()) } || subject.trim().length < 3

    // ---- Price ----

    private fun findPrice(text: String): Double? {
        val match = PRICE_REGEX.find(text) ?: return null
        val number = match.groups["n1"]?.value ?: match.groups["n2"]?.value ?: return null
        return parseNumber(number)?.takeIf { it > 0 }
    }

    /** "1,299.50" / "1.299,50" / "1,299" / "12.99" → Double */
    /**
     * True for titles that describe the SHOP, not the product — e.g. Shopee's home page
     * ("Shopee Philippines | Shop Online with Promos and Vouchers") or a login/robot check.
     * These must never become the wish name.
     */
    fun isGenericPageTitle(title: String): Boolean {
        val t = title.lowercase().replace(Regex("""\s+"""), " ").trim()
        if (t.length < 3) return true
        if (SITE_NAMES.any { t == it || t.startsWith("$it ") || t.startsWith("$it|") || t.startsWith("$it -") }) return true
        return GENERIC_PHRASES.any { t.contains(it) }
    }

    fun parseNumber(raw: String): Double? {
        val s = raw.trim()
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastDot > lastComma) s.replace(",", "") else s.replace(".", "").replace(',', '.')
            lastComma >= 0 ->
                if (s.length - lastComma - 1 == 3) s.replace(",", "") else s.replace(',', '.')
            lastDot >= 0 ->
                // "150.000" (Rp/€ style thousands) vs "12.99" (decimal)
                if (s.split('.').drop(1).all { it.length == 3 }) s.replace(".", "") else s
            else -> s
        }
        return normalized.toDoubleOrNull()
    }

    // ---- Store ----

    private fun storeFromLink(link: String): String? {
        val host = runCatching { URI(link).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return null
        return STORES.entries.firstOrNull { (key, _) -> host.contains(key) }?.value
            ?: host.substringBeforeLast('.').substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    companion object {
        private const val MAX_NAME = 60

        private val LEADING_PROMPT = Regex("""(?i)^(check\s+(this\s+)?out|look\s+at)\s*[:\-–—]?\s*""")
        private val TRAILING_FILLER = Regex("""(?i)\s+(for|at|only|from|on|now|in)$""")

        private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

        // Currency before the number ("₱1,299", "PHP 1,299.00", "RM49") or after it ("1.299 €").
        private val PRICE_REGEX = Regex(
            """(?:₱|PHP|Php|\$|US\$|USD|€|EUR|£|GBP|¥|JPY|₹|INR|Rp\.?|IDR|RM|MYR|S\$|SGD|฿|THB|₫|VND|₩|KRW)\s?(?<n1>\d[\d.,]*\d|\d)""" +
                """|(?<n2>\d[\d.,]*\d|\d)\s?(?:₱|PHP|€|EUR|₫|VND|₩|KRW|฿|THB)""",
        )

        private val BOILERPLATE = listOf(
            Regex("""(?i)check\s+(this|it)\s+out[^!.\n]*[!.]?"""),
            Regex("""(?i)check\s+out\s+(this|the)\s+(product|item|deal|listing|link)(\s+on\s+\w+(\s+shop)?)?[!.:]?"""),
            Regex("""(?i)i\s+(found|saw)\s+(this|it)\s+on\s+\w+(\s+\w+)?[!.:]?"""),
            Regex("""(?i)(look|see)\s+what\s+i\s+found[^!.\n]*[!.:]?"""),
            Regex("""(?i)\b(shop|buy|order|get)\s+(it\s+)?now\b[!.:]?"""),
            Regex("""(?i)\bon\s+(shopee|lazada|tiktok\s*shop|tiktok|amazon|temu|shein|zalora|aliexpress)\b"""),
            Regex("""(?i)\b(download|get)\s+the\s+\w+\s+app[^!.\n]*[!.]?"""),
            Regex("""(?i)\b(free\s+shipping|flash\s+sale|limited\s+time)\b[!.]?"""),
            // Page-title suffixes: "Item | Shopee Philippines", "Item - TikTok Shop"
            Regex("""(?i)\s+[|\-–—]\s+[^|\-–—]*(shopee|lazada|tiktok|amazon|zalora|temu|shein|aliexpress|online\s+shopping)[^|]*$"""),
        )

        private val SITE_NAMES = listOf(
            "shopee", "lazada", "tiktok", "tiktok shop", "amazon", "zalora", "temu", "shein",
            "aliexpress", "ebay", "carousell", "facebook",
        )

        private val GENERIC_PHRASES = listOf(
            "shop online", "online shopping", "promos and vouchers", "best deals", "official store",
            "log in", "login", "sign in", "sign up", "verify", "verification", "captcha", "security check",
            "access denied", "just a moment", "are you a robot", "page not found", "404", "something went wrong",
            "download the app", "open the app",
        )

        private val STORES = linkedMapOf(
            "shopee" to "Shopee",
            "shp.ee" to "Shopee",
            "lazada" to "Lazada",
            "lzd.co" to "Lazada",
            "tiktok" to "TikTok Shop",
            "amazon" to "Amazon",
            "amzn" to "Amazon",
            "zalora" to "Zalora",
            "temu" to "Temu",
            "shein" to "Shein",
            "aliexpress" to "AliExpress",
            "ebay" to "eBay",
            "carousell" to "Carousell",
            "facebook" to "Facebook Marketplace",
            "fb.com" to "Facebook Marketplace",
        )
    }
}
