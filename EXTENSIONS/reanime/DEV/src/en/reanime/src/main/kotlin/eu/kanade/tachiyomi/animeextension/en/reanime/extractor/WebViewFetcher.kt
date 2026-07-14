package eu.kanade.tachiyomi.animeextension.en.reanime.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.animeextension.en.reanime.ReanimeLog
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * WebView-based fetcher for the Re:ANIME extension.
 *
 * Handles:
 * - `warmUp()` — pre-initializes the WebView on a background thread (hides cold start)
 * - `fetchText(url)` — uses Chrome's TLS stack to fetch a URL (bypasses Cloudflare)
 * - `interceptVideoUrls(url, timeoutMs)` — loads a flixcloud.cc embed page, intercepts
 *   `/api/m3u8/<token>` requests at the WebView network level, reads the master playlist
 *   body, and returns all captured (url, body) pairs for the extractor to parse.
 *
 * The m3u8 token is SINGLE-USE (re-fetching returns 410 "invalid_or_used_token").
 * So we intercept the request in shouldInterceptRequest, make it ourselves via OkHttp
 * (consuming the token), read the response body, and return it as a WebResourceResponse
 * to the WebView (so the player JS also gets the data). We keep the body for parsing.
 *
 * @param context The app context (for creating the WebView)
 * @param client The OkHttp client (inherited from the source — has CloudflareInterceptor)
 * @param defaultHeaders Default headers for OkHttp requests (Referer, User-Agent)
 */
class WebViewFetcher(
    private val context: Context,
    private val client: OkHttpClient,
    private val defaultHeaders: Headers,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fetchLock = Any()

    @Volatile
    private var webView: WebView? = null

    /** Pre-initialize the WebView on the main thread (call during episode list fetch). */
    fun warmUp() {
        ReanimeLog.d("WebViewFetcher: warmUp()")
        ensureWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) return
        synchronized(fetchLock) {
            if (webView != null) return
            val latch = CountDownLatch(1)
            mainHandler.post {
                try {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = DESKTOP_UA
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                ReanimeLog.d("WebViewFetcher: page loaded: $url")
                            }
                        }
                    }
                } catch (e: Exception) {
                    ReanimeLog.e("WebViewFetcher: ensureWebView failed", e)
                } finally {
                    latch.countDown()
                }
            }
            latch.await(10, TimeUnit.SECONDS)
        }
    }

    /**
     * Fetch a URL using the WebView's Chrome TLS stack (bypasses Cloudflare/anti-bot).
     * Returns the response body as a string.
     */
    fun fetchText(url: String, timeoutMs: Long = 30_000): String {
        ensureWebView()
        ReanimeLog.i("WebViewFetcher: fetchText — $url")

        val result = AtomicReference("")
        val latch = CountDownLatch(1)

        synchronized(fetchLock) {
            mainHandler.post {
                try {
                    webView?.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            view?.evaluateJavascript(
                                "(function(){return document.documentElement.outerHTML;})()",
                            ) { html ->
                                val decoded = parseJsStringResult(html)
                                result.set(decoded)
                                latch.countDown()
                            }
                        }
                    }
                    webView?.loadUrl(url)
                } catch (e: Exception) {
                    ReanimeLog.e("WebViewFetcher: fetchText failed", e)
                    latch.countDown()
                }
            }

            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                ReanimeLog.w("WebViewFetcher: fetchText timeout (${timeoutMs}ms)")
            }
        }

        return result.get()
    }

    // ════════════════════════════════════════════════════════════════════
    // interceptVideoUrls — network-capture approach
    // ════════════════════════════════════════════════════════════════════

    /**
     * Data captured from a single m3u8 request interception.
     *
     * @param requestUrl The /api/m3u8/<token> URL
     * @param body The master playlist body (for parsing quality variants)
     */
    data class CapturedM3u8(
        val requestUrl: String,
        val body: String,
    )

    /**
     * ★ Intercept video URLs by monitoring network requests at the WebView level.
     *
     * Loads the flixcloud.cc embed page in the WebView. When the player JS
     * fetches `/api/m3u8/<token>`, shouldInterceptRequest intercepts it:
     * 1. Makes the request ourselves via OkHttp (with WebView cookies + Referer)
     * 2. Reads the response body (the master playlist)
     * 3. Returns it as a WebResourceResponse to the WebView (player gets it too)
     * 4. Stores the (url, body) pair for the extractor to parse
     *
     * Also captures any direct .m3u8 / .mp4 URLs from network requests.
     *
     * @param url The flixcloud.cc embed URL (e.g. https://flixcloud.cc/e/<code>?v=1)
     * @param timeoutMs How long to wait for video URLs (default 30s)
     * @return List of captured m3u8 data (url + body)
     */
    fun interceptVideoUrls(
        url: String,
        timeoutMs: Long = 30_000,
    ): List<CapturedM3u8> {
        ensureWebView()
        ReanimeLog.i("WebViewFetcher: interceptVideoUrls — loading $url")

        val captured = ConcurrentLinkedQueue<CapturedM3u8>()
        val doneLatch = CountDownLatch(1)
        val foundFirst = AtomicReference(false)

        synchronized(fetchLock) {
            mainHandler.post {
                try {
                    webView?.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: ""

                            // ★ Intercept /api/m3u8/ requests — make them ourselves so we
                            // can read the response body (the token is single-use).
                            if (reqUrl.contains("/api/m3u8/") && reqUrl.contains("flixcloud")) {
                                try {
                                    val cookies = CookieManager.getInstance().getCookie(reqUrl)
                                    val reqHeaders = defaultHeaders.newBuilder()
                                        .set("Referer", "https://flixcloud.cc/")
                                        .apply {
                                            if (cookies.isNotBlank()) set("Cookie", cookies)
                                        }
                                        .build()

                                    ReanimeLog.i("WebViewFetcher: intercepting m3u8 request: ${ReanimeLog.trunc(reqUrl, 100)}")
                                    val response = client.newCall(GET(reqUrl, reqHeaders)).execute()
                                    val body = response.body?.string().orEmpty()
                                    response.close()

                                    if (body.isNotBlank()) {
                                        captured.add(CapturedM3u8(reqUrl, body))
                                        ReanimeLog.i("WebViewFetcher: captured m3u8 body (${body.length} chars)")
                                        if (foundFirst.compareAndSet(false, true)) {
                                            // Wait a bit for a possible second m3u8 (sub+dub), then signal done
                                            mainHandler.postDelayed({
                                                doneLatch.countDown()
                                            }, 3000)
                                        }
                                    }

                                    // Return the response to the WebView so the player JS works
                                    return WebResourceResponse(
                                        "application/vnd.apple.mpegurl",
                                        "utf-8",
                                        ByteArrayInputStream(body.toByteArray()),
                                    )
                                } catch (e: Exception) {
                                    ReanimeLog.e("WebViewFetcher: m3u8 intercept failed for $reqUrl", e)
                                }
                            }

                            // Also capture direct .m3u8 / .mp4 URLs (non-flixcloud)
                            if (looksLikeVideoUrl(reqUrl) && !reqUrl.contains("/api/m3u8/")) {
                                ReanimeLog.i("WebViewFetcher: captured video URL: ${ReanimeLog.trunc(reqUrl, 100)}")
                                captured.add(CapturedM3u8(reqUrl, ""))
                                if (foundFirst.compareAndSet(false, true)) {
                                    mainHandler.postDelayed({ doneLatch.countDown() }, 2000)
                                }
                            }

                            return null // let other requests proceed normally
                        }

                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            ReanimeLog.d("WebViewFetcher: page loaded: $loadedUrl")
                            // Inject JS to auto-click play if needed (some players don't auto-play)
                            view?.evaluateJavascript(PLAY_CLICK_JS) { result ->
                                ReanimeLog.d("WebViewFetcher: play click result: $result")
                            }
                        }
                    }

                    ReanimeLog.d("WebViewFetcher: loading embed page: $url")
                    webView?.loadUrl(url)
                } catch (e: Exception) {
                    ReanimeLog.e("WebViewFetcher: interceptVideoUrls failed", e)
                    doneLatch.countDown()
                }
            }

            // Wait for capture or timeout
            if (!doneLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                ReanimeLog.w("WebViewFetcher: interceptVideoUrls timeout (${timeoutMs}ms)")
            }
        }

        // Restore default WebViewClient
        mainHandler.post {
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    ReanimeLog.d("WebViewFetcher: page loaded: $url")
                }
            }
        }

        val result = captured.toList()
        ReanimeLog.i("WebViewFetcher: interceptVideoUrls DONE, captured ${result.size} m3u8(s)")
        return result
    }

    /** Check if a URL looks like a playable video stream. */
    private fun looksLikeVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> true
            lower.contains(".mp4") -> true
            lower.contains("/manifest.m3u8") -> true
            lower.contains("/playlist.m3u8") -> true
            else -> false
        }
    }

    /** Parse a JS eval result string (removes surrounding quotes). */
    private fun parseJsStringResult(result: String?): String {
        if (result.isNullOrBlank()) return ""
        var s = result.trim()
        if (s == "null") return ""
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
            s = s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\/", "/")
        }
        return s
    }

    /** JS to click play buttons (for players that don't auto-play). */
    private val PLAY_CLICK_JS = """
        (function() {
            try {
                // Try common play button selectors
                var selectors = [
                    '.vjs-big-play-button',
                    '[class*=play-button]',
                    '[class*=PlayButton]',
                    'button[aria-label*=Play]',
                    'button[aria-label*=play]',
                    '.play-btn',
                    '#play-button',
                    'video'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) { el.click(); break; }
                }
                // Also try playing the video element directly
                var v = document.querySelector('video');
                if (v && v.paused) { v.play().catch(function(){}); }
            } catch(e) {}
        })();
    """.trimIndent()

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
