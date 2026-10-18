package com.karencalulo.wishwait.data.remote

import com.karencalulo.wishwait.domain.repository.LinkPreview
import com.karencalulo.wishwait.domain.repository.LinkPreviewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Plain HTTPS GET of the shared product link — no SDK, no tracking, no cookies kept.
 * Follows short-link redirects (vt.tiktok.com → tiktok.com), stops after a few hops,
 * reads at most 1 MB, and gives up quickly. Any failure just means "type it yourself".
 */
class HttpLinkPreviewRepository : LinkPreviewRepository {

    override suspend fun fetch(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        runCatching { load(url) }.getOrNull()
    }

    private fun load(start: String): LinkPreview? {
        var current = start
        repeat(MAX_REDIRECTS + 1) {
            val url = URL(current)
            if (url.protocol != "https" && url.protocol != "http") return null
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    current = URL(url, location).toString()
                    return@repeat
                }
                if (code !in 200..299) return null
                val html = conn.inputStream.use { readLimited(it) }
                return HtmlPreviewParser.parse(html)
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun readLimited(input: InputStream): String {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (out.size() < MAX_BYTES) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val TIMEOUT_MS = 6_000
        private const val MAX_BYTES = 1_000_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile WishWait/1.0 (link preview)"
    }
}
