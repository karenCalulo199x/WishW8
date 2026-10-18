package com.karencalulo.wishwait.data.remote

import com.karencalulo.wishwait.domain.repository.LinkPreview
import com.karencalulo.wishwait.domain.repository.LinkPreviewRepository

/** Fast plain read first; if it misses the name or price, ask the hidden browser. */
class CombinedLinkPreviewRepository(
    private val quick: LinkPreviewRepository,
    private val browser: LinkPreviewRepository,
    /** Rejects shop-wide titles like "Shopee Philippines | Shop Online…". */
    private val isUsefulTitle: (String) -> Boolean = { true },
) : LinkPreviewRepository {

    override suspend fun fetch(url: String): LinkPreview? {
        val first = quick.fetch(url)
        val quickTitle = first?.title?.takeIf(isUsefulTitle)
        if (quickTitle != null && first.priceText != null) return LinkPreview(quickTitle, first.priceText)
        val second = runCatching { browser.fetch(url) }.getOrNull()
        val title = quickTitle ?: second?.title?.takeIf(isUsefulTitle)
        val price = first?.priceText ?: second?.priceText
        return if (title == null && price == null) null else LinkPreview(title, price)
    }
}
