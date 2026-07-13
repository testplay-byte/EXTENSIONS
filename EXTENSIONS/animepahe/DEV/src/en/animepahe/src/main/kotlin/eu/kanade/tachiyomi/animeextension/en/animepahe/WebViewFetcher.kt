package eu.kanade.tachiyomi.animeextension.en.animepahe

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * WebView-based HTTP fetcher that uses Chrome's TLS stack to bypass Cloudflare.
 *
 * Ported from AniKoto's WebViewFetcher (sessions 30-31) — simplified to only the methods
 * needed for episode metadata enrichment (fetchText + postJson + warmUp).
 *
 * Why this exists: some metadata APIs (AniList, Kitsu) MAY block OkHttp's TLS fingerprint
 * (Conscrypt/JA3) with 403. Chrome (BoringSSL) passes. This class uses Android's WebView
 * (Chrome's network stack) to execute `fetch()` calls, bypassing the WAF TLS block.
 *
 * ★ CRITICAL: The origin URL is a `data:text/html` blank page — NOT the extension's site.
 * Loading the extension site (e.g. animepahe.pw) as origin FAILS because Cloudflare shows
 * a challenge page with a strict CSP (`default-src 'none'`) that blocks ALL cross-origin
 * fetch() calls. A blank data: URL has NO CSP restrictions, so fetch() to any CORS-enabled
 * API works freely. AniList sends `Access-Control-Allow-Origin: *`, so null-origin fetch works.
 *
 * ★ Usage: EpisodeMetadataFetcher tries OkHttp FIRST (faster, simpler), falls back to this
 * WebView only if OkHttp returns 403/blocked. In practice, OkHttp works for AniList + Anikage
 * on most devices — the WebView is a safety net.
 *
 * ★ This same WebViewFetcher will be reused for Step 4 (video playback) — the WAF-bypass
 * technique is identical, just different target URLs.
 */
class WebViewFetcher(
    private val context: Context,
    private val originUrl: String = "data:text/html,<html><body></body></html>",
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var webView: WebView? = null
    @Volatile private var webViewReady = false
    private val atomicId = AtomicLong(0)
    private val pendingRequests = ConcurrentHashMap<String, RequestState>()
    private val fetchLock = Any()

    private inner class RequestState {
        val latch = CountDownLatch(1)
        var error: String? = null
        var textResult: String? = null
    }

    inner class JSInterface {
        @JavascriptInterface
        fun onResult(id: String, text: String) {
            pendingRequests[id]?.let { state ->
                state.textResult = text
                state.latch.countDown()
            }
        }

        @JavascriptInterface
        fun onError(id: String, error: String) {
            AnimepaheLog.e("WebViewFetcher: JS error for request $id: $error")
            pendingRequests[id]?.let { state ->
                state.error = error
                state.latch.countDown()
            }
        }
    }

    /**
     * Pre-warm the WebView on a background thread.
     * Call this early (e.g., during episode list fetch) so the WebView is ready
     * by the time metadata enrichment needs it. Non-blocking.
     */
    fun warmUp() {
        if (webView != null && webViewReady) return
        Thread {
            try {
                mainHandler.post {
                    try {
                        if (webView == null) {
                            webView = createWebView()
                            AnimepaheLog.i("WebViewFetcher: warmUp loading origin: $originUrl")
                            webView?.loadUrl(originUrl)
                        }
                    } catch (e: Exception) {
                        AnimepaheLog.e("WebViewFetcher: warmUp WebView creation failed", e)
                        webViewReady = true
                    }
                }
                val deadline = System.currentTimeMillis() + 10_000
                while (!webViewReady && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200)
                }
                AnimepaheLog.i("WebViewFetcher: warmUp ${if (webViewReady) "complete" else "timed out (will retry on first use)"}")
            } catch (e: Exception) {
                AnimepaheLog.e("WebViewFetcher: warmUp failed (will retry on first use)", e)
            }
        }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.blockNetworkImage = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                AnimepaheLog.i("WebViewFetcher: origin page loaded: $url")
                webViewReady = true
            }
        }
        addJavascriptInterface(JSInterface(), "Android")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null && webViewReady) return
        synchronized(fetchLock) {
            if (webView != null && webViewReady) return
            mainHandler.post {
                try {
                    webView = createWebView()
                    AnimepaheLog.i("WebViewFetcher: loading origin: $originUrl")
                    webView?.loadUrl(originUrl)
                } catch (e: Exception) {
                    AnimepaheLog.e("WebViewFetcher: failed to create WebView", e)
                    webViewReady = true
                }
            }
            val deadline = System.currentTimeMillis() + 30_000
            while (!webViewReady && System.currentTimeMillis() < deadline) {
                Thread.sleep(200)
            }
            if (!webViewReady) {
                AnimepaheLog.e("WebViewFetcher: timeout waiting for origin page load")
            }
        }
    }

    /** GET a URL via WebView (Chrome's TLS). Returns the response body text. */
    fun fetchText(url: String, timeoutMs: Long = 30_000): String {
        ensureWebView()
        val id = atomicId.incrementAndGet().toString()
        val state = RequestState()
        pendingRequests[id] = state
        val startTime = System.currentTimeMillis()
        AnimepaheLog.d("WebViewFetcher: fetchText id=$id url=${AnimepaheLog.trunc(url, 80)}")

        synchronized(fetchLock) {
            mainHandler.post {
                webView?.evaluateJavascript(buildFetchTextJs(id, url), null)
            }
            if (!state.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pendingRequests.remove(id)
                throw RuntimeException("WebViewFetcher: text fetch timeout for ${AnimepaheLog.trunc(url, 60)}")
            }
        }
        pendingRequests.remove(id)
        state.error?.let { throw RuntimeException("WebViewFetcher: $it") }
        val elapsed = System.currentTimeMillis() - startTime
        AnimepaheLog.i("WebViewFetcher: fetchText id=$id DONE in ${elapsed}ms")
        return state.textResult ?: throw RuntimeException("WebViewFetcher: no text result")
    }

    /** POST JSON to a URL via WebView (for AniList GraphQL). Uses Chrome's TLS. */
    fun postJson(url: String, jsonBody: String, timeoutMs: Long = 30_000): String {
        ensureWebView()
        val id = atomicId.incrementAndGet().toString()
        val state = RequestState()
        pendingRequests[id] = state
        val startTime = System.currentTimeMillis()
        AnimepaheLog.d("WebViewFetcher: postJson id=$id url=${AnimepaheLog.trunc(url, 60)}")

        synchronized(fetchLock) {
            mainHandler.post {
                webView?.evaluateJavascript(buildPostJsonJs(id, url, jsonBody), null)
            }
            if (!state.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pendingRequests.remove(id)
                throw RuntimeException("WebViewFetcher: postJson timeout for ${AnimepaheLog.trunc(url, 60)}")
            }
        }
        pendingRequests.remove(id)
        state.error?.let { throw RuntimeException("WebViewFetcher: $it") }
        val elapsed = System.currentTimeMillis() - startTime
        AnimepaheLog.i("WebViewFetcher: postJson id=$id DONE in ${elapsed}ms")
        return state.textResult ?: throw RuntimeException("WebViewFetcher: no postJson result")
    }

    fun destroy() {
        webViewReady = false
        mainHandler.post {
            try { webView?.destroy() } catch (_: Exception) {}
            webView = null
        }
        pendingRequests.clear()
    }

    // ── JavaScript builders ──────────────────────────────────────────────────

    private fun buildFetchTextJs(id: String, url: String): String {
        val escapedUrl = escapeJsString(url)
        return """
            (async function() {
                try {
                    const response = await fetch('$escapedUrl');
                    if (!response.ok) { Android.onError('$id', 'HTTP ' + response.status); return; }
                    const text = await response.text();
                    Android.onResult('$id', text);
                } catch(e) { Android.onError('$id', e.message); }
            })();
        """.trimIndent()
    }

    private fun buildPostJsonJs(id: String, url: String, jsonBody: String): String {
        val escapedUrl = escapeJsString(url)
        val escapedBody = escapeJsString(jsonBody)
        return """
            (async function() {
                try {
                    const response = await fetch('$escapedUrl', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                        body: '$escapedBody'
                    });
                    if (!response.ok) { Android.onError('$id', 'HTTP ' + response.status); return; }
                    const text = await response.text();
                    Android.onResult('$id', text);
                } catch(e) { Android.onError('$id', e.message); }
            })();
        """.trimIndent()
    }

    private fun escapeJsString(s: String): String =
        s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
}
