package com.karencalulo.wishwait.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.karencalulo.wishwait.domain.repository.LinkPreview
import com.karencalulo.wishwait.domain.repository.LinkPreviewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * For shop pages that build their content with JavaScript (Shopee, TikTok Shop, Lazada):
 * load the link in an invisible on-device browser, let the page render, then read
 * the product name and price the same way a person would see them.
 *
 * - Never opens other apps (shop "open in app" redirects are blocked).
 * - Images and media are not loaded.
 * - Cookies are cleared afterwards so shops can't build a profile through WishWait.
 */
class WebViewLinkReader(
    private val appContext: Context,
    private val isUsefulTitle: (String) -> Boolean = { true },
) : LinkPreviewRepository {

    override suspend fun fetch(url: String): LinkPreview? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(TOTAL_TIMEOUT_MS) { read(url) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun read(url: String): LinkPreview? = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val webView = WebView(appContext)
        var best: LinkPreview? = null
        var finished = false
        var attempts = 0

        fun done() {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            runCatching {
                webView.stopLoading()
                webView.destroy()
                CookieManager.getInstance().removeAllCookies(null)
            }
            if (cont.isActive) cont.resume(best)
        }

        fun poll() {
            if (finished) return
            webView.evaluateJavascript(EXTRACT_JS) { raw ->
                parseResult(raw, isUsefulTitle)?.let { found ->
                    best = LinkPreview(
                        title = found.title ?: best?.title,
                        priceText = found.priceText ?: best?.priceText,
                    )
                }
                attempts++
                val complete = best?.title != null && best?.priceText != null
                if (complete || attempts >= MAX_POLLS) done() else handler.postDelayed({ poll() }, POLL_MS)
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        webView.webViewClient = object : WebViewClient() {
            // Stay on http(s) pages; never jump into the Shopee/TikTok app.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // Single-page shops keep rendering after "finished"; give them a moment.
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ poll() }, FIRST_POLL_MS)
            }
        }

        // Lay the invisible browser out like a phone screen so pages render normally.
        val w = View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY)
        val h = View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY)
        webView.measure(w, h)
        webView.layout(0, 0, WIDTH_PX, HEIGHT_PX)

        cont.invokeOnCancellation { handler.post { done() } }
        handler.postDelayed({ done() }, TOTAL_TIMEOUT_MS - 500)
        webView.loadUrl(url)
    }

    private fun parseResult(raw: String?, isUseful: (String) -> Boolean): LinkPreview? = runCatching {
        if (raw.isNullOrBlank() || raw == "null") return null
        // evaluateJavascript returns a JSON-encoded string literal.
        val json = JSONObject(JSONArray("[$raw]").getString(0))
        // Several candidates (h1, og:title, document.title…): take the first that names a product.
        val titles = json.optJSONArray("titles")
        val title = (0 until (titles?.length() ?: 0))
            .map { titles!!.optString(it).trim() }
            .firstOrNull { it.isNotBlank() && it != "null" && isUseful(it) }
        val price = json.optString("price").takeIf { it.isNotBlank() && it != "null" }
        if (title == null && price == null) null else LinkPreview(title, price)
    }.getOrNull()

    companion object {
        private const val TOTAL_TIMEOUT_MS = 15_000L
        private const val FIRST_POLL_MS = 1_200L
        private const val POLL_MS = 1_000L
        private const val MAX_POLLS = 8
        private const val WIDTH_PX = 1080
        private const val HEIGHT_PX = 2400

        /**
         * 1) Preview tags (og:title, product price), 2) JSON-LD product data,
         * 3) otherwise the biggest price-looking text on the page that isn't struck through —
         *    the main price is almost always the largest one.
         */
        private val EXTRACT_JS = """
            (function(){
              function meta(n){var e=document.querySelector('meta[property="'+n+'"],meta[name="'+n+'"],meta[itemprop="'+n+'"]');return e?e.getAttribute('content'):null;}
              var titles=[];var h=document.querySelector('h1');if(h&&h.innerText)titles.push(h.innerText);
              titles.push(meta('og:title'),meta('twitter:title'),document.title);
              var price=meta('product:price:amount')||meta('og:price:amount');
              if(!price){try{var ld=document.querySelectorAll('script[type="application/ld+json"]');for(var i=0;i<ld.length&&!price;i++){var m=ld[i].textContent.match(/"(?:price|lowPrice)"\s*:\s*"?(\d[\d.,]*)/);if(m)price=m[1];}}catch(e){}}
              if(!price&&document.body){
                var re=/^\s*(₱|PHP|\$|US\$|RM|Rp|S\$|฿|₫|€|£|¥|₹)\s?\d[\d.,]*(\s*[-–~]\s*\S+)?\s*$/;
                var best=null,size=0,els=document.body.getElementsByTagName('*');
                for(var j=0;j<els.length&&j<6000;j++){
                  var el=els[j];if(el.children.length>3)continue;
                  var t=(el.innerText||'').trim();if(!t||t.length>40||!re.test(t))continue;
                  var cs=getComputedStyle(el);if((cs.textDecorationLine||'').indexOf('line-through')>=0)continue;
                  var s=parseFloat(cs.fontSize)||0;if(s>size){size=s;best=t;}
                }
                if(best){var mm=best.match(/\d[\d.,]*/);price=mm?mm[0]:null;}
              }
              return JSON.stringify({titles:titles.filter(function(x){return !!x;}),price:price||null});
            })()
        """.trimIndent()
    }
}
