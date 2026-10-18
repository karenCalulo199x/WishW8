package com.karencalulo.wishwait.domain.usecase

import com.karencalulo.wishwait.domain.repository.LinkPreviewRepository

/**
 * When a share is missing the name or price (TikTok shares only a link, Lazada has no price),
 * read them from the product page. Offline-first: if this fails, the user just types them.
 */
class FetchLinkDetailsUseCase(
    private val repo: LinkPreviewRepository,
    private val parser: ParseSharedTextUseCase,
) {
    suspend operator fun invoke(link: String): SharedItem? {
        val preview = repo.fetch(link) ?: return null
        // A shop's home/login page title is not a product name.
        val name = preview.title
            ?.takeUnless { parser.isGenericPageTitle(it) }
            ?.let { parser(it).name }
            ?.takeUnless { parser.isGenericPageTitle(it) }
            .orEmpty()
        val price = preview.priceText?.let { parser.parseNumber(it) }?.takeIf { it > 0 }
        if (name.isBlank() && price == null) return null
        return SharedItem(name = name, price = price, link = link, store = null)
    }
}
