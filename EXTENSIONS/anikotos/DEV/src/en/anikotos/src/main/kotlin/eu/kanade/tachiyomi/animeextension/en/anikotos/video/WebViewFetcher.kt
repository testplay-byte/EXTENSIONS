package eu.kanade.tachiyomi.animeextension.en.anikotos.video

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.animeextension.en.anikotos.AnikotoSLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * ★ session 30-31: WebView-based HTTP fetcher that uses Chrome's TLS stack.
 *
 * The cdn.mewstream.buzz WAF blocks OkHttp's TLS fingerprint (Conscrypt/JA3) with HTTP 403.
 * Segment CDN hosts (g5vh.voltara.click, f4qh.zaptrix.buzz) may also be unresolvable by
 * OkHttp's DNS. Chrome (BoringSSL) is allowed. This class uses Android's WebView (Chrome's
 * network stack) to execute `fetch()` calls, bypassing both the WAF TLS block and DNS issues.
 *
 * ★ session 31 optimizations:
 * - Uses FileReader.readAsDataURL (native) instead of String.fromCharCode+btoa (JS, very slow)
 * - Larger chunks (700KB → ~933KB base64, under 1MB IPC limit)
 * - Serialized fetches (no concurrent evaluateJavascript — prevents "connection abort")
 * - Timing logs for diagnosis
 * - 60s timeout for bytes (segments can be 2MB+)
 */
class WebViewFetcher(
    private val context: Context,
    private val originUrl: String = "https://megaplay.buzz/",
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var webView: WebView? = null
    @Volatile private var webViewReady = false
    private val atomicId = AtomicLong(0)
    private val pendingRequests = ConcurrentHashMap<String, RequestState>()
    private val fetchLock = Any() // ★ serialize all fetches (prevents concurrent WebView issues)

    private open inner class RequestState {
        val latch = CountDownLatch(1)
        var error: String? = null
    }

    private inner class TextRequestState : RequestState() {
        var textResult: String? = null
    }

    private inner class ByteRequestState : RequestState() {
        val chunks = mutableListOf<ByteArray>()
    }

    inner class JSInterface {
        @JavascriptInterface
        fun onResult(id: String, text: String) {
            pendingRequests[id]?.let { state ->
                (state as? TextRequestState)?.let {
                    it.textResult = text
                    it.latch.countDown()
                }
            }
        }

        @JavascriptInterface
        fun onChunk(id: String, index: Int, total: Int, base64data: String) {
            pendingRequests[id]?.let { state ->
                (state as? ByteRequestState)?.let {
                    synchronized(it.chunks) {
                        it.chunks.add(Base64.decode(base64data, Base64.DEFAULT))
                    }
                }
            }
        }

        @JavascriptInterface
        fun onBytesComplete(id: String, totalSize: Int) {
            pendingRequests[id]?.let { state ->
                (state as? ByteRequestState)?.latch?.countDown()
            }
        }

        @JavascriptInterface
        fun onError(id: String, error: String) {
            AnikotoSLog.e("WebViewFetcher: JS error for request $id: $error")
            pendingRequests[id]?.let { state ->
                state.error = error
                state.latch.countDown()
            }
        }
    }

    /**
     * ★ session 51: Pre-warm the WebView on a background thread.
     * Call this early (e.g., during episode list fetch) so the WebView is ready
     * by the time the user clicks play. Non-blocking: starts init and returns immediately.
     * If already warmed, returns instantly.
     * ★ session 51 fix: Uses a 10-second timeout (shorter than ensureWebView's 30s).
     * If warmUp times out, ensureWebView will be called again with the full 30s when
     * the video is actually played — no data is lost, just a slower first play.
     */
    fun warmUp() {
        if (webView != null && webViewReady) return
        Thread {
            try {
                // Start WebView init on main thread
                mainHandler.post {
                    try {
                        if (webView == null) {
                            webView = WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.blockNetworkImage = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        AnikotoSLog.i("WebViewFetcher: warmUp page loaded: $url")
                                        webViewReady = true
                                    }
                                }
                                addJavascriptInterface(JSInterface(), "Android")
                            }
                            AnikotoSLog.i("WebViewFetcher: warmUp loading origin: $originUrl")
                            webView?.loadUrl(originUrl)
                        }
                    } catch (e: Exception) {
                        AnikotoSLog.e("WebViewFetcher: warmUp WebView creation failed", e)
                        webViewReady = true // Prevent spin-wait from hanging
                    }
                }
                // Wait with shorter timeout than ensureWebView (10s vs 30s)
                val deadline = System.currentTimeMillis() + 10_000
                while (!webViewReady && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200)
                }
                AnikotoSLog.i("WebViewFetcher: warmUp ${if (webViewReady) "complete" else "timed out (will retry on first use)"}")
            } catch (e: Exception) {
                AnikotoSLog.e("WebViewFetcher: warmUp failed (will retry on first use)", e)
            }
        }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null && webViewReady) return
        synchronized(fetchLock) {
            if (webView != null && webViewReady) return
            mainHandler.post {
                try {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.blockNetworkImage = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                AnikotoSLog.i("WebViewFetcher: origin page loaded: $url")
                                webViewReady = true
                            }
                        }
                        addJavascriptInterface(JSInterface(), "Android")
                    }
                    AnikotoSLog.i("WebViewFetcher: loading origin: $originUrl")
                    webView?.loadUrl(originUrl)
                } catch (e: Exception) {
                    AnikotoSLog.e("WebViewFetcher: failed to create WebView", e)
                    webViewReady = true
                }
            }
            val deadline = System.currentTimeMillis() + 30_000
            while (!webViewReady && System.currentTimeMillis() < deadline) {
                Thread.sleep(200)
            }
            if (!webViewReady) {
                AnikotoSLog.e("WebViewFetcher: timeout waiting for origin page load")
            }
        }
    }

    fun fetchText(url: String, timeoutMs: Long = 30_000): String {
        ensureWebView()
        val id = atomicId.incrementAndGet().toString()
        val state = TextRequestState()
        pendingRequests[id] = state
        val startTime = System.currentTimeMillis()
        AnikotoSLog.d("WebViewFetcher: fetchText id=$id url=${AnikotoSLog.trunc(url, 80)}")

        synchronized(fetchLock) {
            mainHandler.post {
                webView?.evaluateJavascript(buildFetchTextJs(id, url), null)
            }
            if (!state.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pendingRequests.remove(id)
                throw RuntimeException("WebViewFetcher: text fetch timeout for ${AnikotoSLog.trunc(url, 60)}")
            }
        }
        pendingRequests.remove(id)
        state.error?.let { throw RuntimeException("WebViewFetcher: $it") }
        val elapsed = System.currentTimeMillis() - startTime
        AnikotoSLog.i("WebViewFetcher: fetchText id=$id DONE in ${elapsed}ms")
        return state.textResult ?: throw RuntimeException("WebViewFetcher: no text result")
    }

    fun fetchBytes(url: String, timeoutMs: Long = 60_000): ByteArray {
        ensureWebView()
        val id = atomicId.incrementAndGet().toString()
        val state = ByteRequestState()
        pendingRequests[id] = state
        val startTime = System.currentTimeMillis()
        AnikotoSLog.d("WebViewFetcher: fetchBytes id=$id url=${AnikotoSLog.trunc(url, 80)}")

        synchronized(fetchLock) {
            mainHandler.post {
                webView?.evaluateJavascript(buildFetchBytesJs(id, url), null)
            }
            if (!state.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pendingRequests.remove(id)
                throw RuntimeException("WebViewFetcher: bytes fetch timeout for ${AnikotoSLog.trunc(url, 60)}")
            }
        }
        pendingRequests.remove(id)
        state.error?.let { throw RuntimeException("WebViewFetcher: $it") }
        synchronized(state.chunks) {
            if (state.chunks.isEmpty()) throw RuntimeException("WebViewFetcher: no bytes received")
            val result = if (state.chunks.size == 1) state.chunks[0] else state.chunks.reduce { acc, chunk -> acc + chunk }
            val elapsed = System.currentTimeMillis() - startTime
            AnikotoSLog.i("WebViewFetcher: fetchBytes id=$id DONE in ${elapsed}ms size=${result.size}")
            return result
        }
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

    private fun buildFetchBytesJs(id: String, url: String): String {
        val escapedUrl = escapeJsString(url)
        // ★ session 31: use FileReader.readAsDataURL (native, fast) instead of
        // String.fromCharCode+btoa (JS, extremely slow for 1MB+).
        // Chunk size: 700KB binary → ~933KB base64 (under 1MB IPC limit).
        return """
            (async function() {
                try {
                    const response = await fetch('$escapedUrl');
                    if (!response.ok) { Android.onError('$id', 'HTTP ' + response.status); return; }
                    const buf = await response.arrayBuffer();
                    const bytes = new Uint8Array(buf);
                    var chunkSize = 700000;
                    var numChunks = Math.ceil(bytes.length / chunkSize);
                    for (var i = 0; i < numChunks; i++) {
                        var start = i * chunkSize;
                        var end = Math.min(start + chunkSize, bytes.length);
                        var chunk = bytes.subarray(start, end);
                        var base64 = await new Promise(function(resolve) {
                            var reader = new FileReader();
                            reader.onload = function() { resolve(reader.result.split(',')[1]); };
                            reader.readAsDataURL(new Blob([chunk]));
                        });
                        Android.onChunk('$id', i, numChunks, base64);
                    }
                    Android.onBytesComplete('$id', bytes.length);
                } catch(e) { Android.onError('$id', e.message); }
            })();
        """.trimIndent()
    }

    private fun escapeJsString(s: String): String {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
    }
}
