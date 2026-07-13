package eu.kanade.tachiyomi.animeextension.en.mkissa.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.animeextension.en.mkissa.MKissaLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * WebView-based fetcher for the Fm-Hls (Filemoon) and Uni servers.
 *
 * Ported from AniKoto's WebViewFetcher (sessions 30-31, 51) with:
 * - `warmUp()` — pre-initializes the WebView on a background thread during episode list fetch
 *   so it's ready by the time the user clicks play (hides the 2-10s cold start)
 * - `fetchText(url)` — uses Chrome's TLS stack to fetch a URL (bypasses Cloudflare/anti-bot)
 * - `fetchRenderedText(url)` — loads a URL in the WebView, waits for page load, returns the
 *   rendered HTML (after JS runs — needed for JS SPA players like Filemoon/Uni)
 * - Serialized fetches (prevents concurrent WebView issues)
 * - `data:text/html` origin (no CSP restrictions — cross-origin fetch works)
 *
 * ★ Performance: the WebView is ONLY initialized if Fm-Hls or Uni is enabled in settings.
 * If both are disabled, this class is never instantiated — zero overhead.
 *
 * @param context The app context (for creating the WebView)
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
            MKissaLog.e("WebViewFetcher: JS error for request $id: $error")
            pendingRequests[id]?.let { state ->
                state.error = error
                state.latch.countDown()
            }
        }
    }

    /**
     * ★ Pre-warm the WebView on a background thread.
     * Call this during episode list fetch so the WebView is ready by click-to-play.
     * Non-blocking: starts init and returns immediately. 10s timeout.
     */
    fun warmUp() {
        if (webView != null && webViewReady) return
        MKissaLog.i("WebViewFetcher: warmUp — pre-initializing WebView")
        Thread {
            try {
                mainHandler.post {
                    try {
                        if (webView == null) {
                            webView = WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.blockNetworkImage = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        MKissaLog.d("WebViewFetcher: warmUp page loaded: $url")
                                        webViewReady = true
                                    }
                                }
                                addJavascriptInterface(JSInterface(), "Android")
                            }
                            MKissaLog.d("WebViewFetcher: warmUp loading origin: $originUrl")
                            webView?.loadUrl(originUrl)
                        }
                    } catch (e: Exception) {
                        MKissaLog.e("WebViewFetcher: warmUp creation failed", e)
                        webViewReady = true
                    }
                }
                val deadline = System.currentTimeMillis() + 10_000
                while (!webViewReady && System.currentTimeMillis() < deadline) {
                    Thread.sleep(200)
                }
                MKissaLog.i("WebViewFetcher: warmUp ${if (webViewReady) "complete" else "timed out (will retry on first use)"}")
            } catch (e: Exception) {
                MKissaLog.e("WebViewFetcher: warmUp failed (will retry on first use)", e)
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
                                MKissaLog.d("WebViewFetcher: origin page loaded: $url")
                                webViewReady = true
                            }
                        }
                        addJavascriptInterface(JSInterface(), "Android")
                    }
                    MKissaLog.d("WebViewFetcher: loading origin: $originUrl")
                    webView?.loadUrl(originUrl)
                } catch (e: Exception) {
                    MKissaLog.e("WebViewFetcher: failed to create WebView", e)
                    webViewReady = true
                }
            }
            val deadline = System.currentTimeMillis() + 30_000
            while (!webViewReady && System.currentTimeMillis() < deadline) {
                Thread.sleep(200)
            }
            if (!webViewReady) {
                MKissaLog.e("WebViewFetcher: timeout waiting for origin page load")
            }
        }
    }

    /**
     * Fetch a URL's text content via WebView (Chrome's TLS stack).
     * Uses the data: URL origin → no CSP → cross-origin fetch works.
     */
    fun fetchText(url: String, timeoutMs: Long = 30_000): String {
        ensureWebView()
        val id = atomicId.incrementAndGet().toString()
        val state = RequestState()
        pendingRequests[id] = state
        val startTime = System.currentTimeMillis()
        MKissaLog.d("WebViewFetcher: fetchText id=$id url=${MKissaLog.trunc(url, 80)}")

        synchronized(fetchLock) {
            mainHandler.post {
                webView?.evaluateJavascript(buildFetchTextJs(id, url), null)
            }
            if (!state.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pendingRequests.remove(id)
                throw RuntimeException("WebViewFetcher: text fetch timeout for ${MKissaLog.trunc(url, 60)}")
            }
        }
        pendingRequests.remove(id)
        state.error?.let { throw RuntimeException("WebViewFetcher: $it") }
        val elapsed = System.currentTimeMillis() - startTime
        MKissaLog.i("WebViewFetcher: fetchText id=$id DONE in ${elapsed}ms")
        return state.textResult ?: throw RuntimeException("WebViewFetcher: no text result")
    }

    /**
     * Load a URL in the WebView, wait for the page to fully render (JS included),
     * then return the page's HTML content. This is needed for JS SPA players that
     * load video data dynamically.
     *
     * ★ Uses a separate loadUrl + onPageFinished + evaluateJavascript pattern (not fetch()).
     * The page loads in the WebView's own context (same-origin), so all JS + cookies work.
     */
    fun fetchRenderedText(url: String, waitMs: Long = 5000, timeoutMs: Long = 30_000): String {
        ensureWebView()
        val startTime = System.currentTimeMillis()
        MKissaLog.d("WebViewFetcher: fetchRenderedText url=${MKissaLog.trunc(url, 80)}")

        val pageLoadedLatch = CountDownLatch(1)
        var renderedHtml = ""

        synchronized(fetchLock) {
            mainHandler.post {
                try {
                    // Set a temporary WebViewClient that waits for page load
                    webView?.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            MKissaLog.d("WebViewFetcher: page loaded: $loadedUrl")
                            // Wait a bit for JS to execute (player scripts, API calls)
                            mainHandler.postDelayed({
                                try {
                                    view?.evaluateJavascript(
                                        "(function(){ return document.documentElement.outerHTML; })()",
                                    ) { result ->
                                        renderedHtml = parseJsStringResult(result)
                                        pageLoadedLatch.countDown()
                                    }
                                } catch (e: Exception) {
                                    MKissaLog.e("WebViewFetcher: evaluateJavascript failed", e)
                                    pageLoadedLatch.countDown()
                                }
                            }, waitMs)
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                            // Log video-related requests for debugging
                            val reqUrl = request?.url?.toString() ?: ""
                            if (reqUrl.contains(".m3u8") || reqUrl.contains(".mp4") || reqUrl.contains("/api/")) {
                                MKissaLog.d("WebViewFetcher: intercepted request: ${MKissaLog.trunc(reqUrl, 100)}")
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    MKissaLog.d("WebViewFetcher: loading URL: $url")
                    webView?.loadUrl(url)
                } catch (e: Exception) {
                    MKissaLog.e("WebViewFetcher: loadUrl failed", e)
                    pageLoadedLatch.countDown()
                }
            }

            if (!pageLoadedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                MKissaLog.w("WebViewFetcher: fetchRenderedText timeout after ${timeoutMs}ms")
                return ""
            }
        }

        // Restore the default WebViewClient for future fetchText calls
        mainHandler.post {
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    MKissaLog.d("WebViewFetcher: page loaded: $url")
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        MKissaLog.i("WebViewFetcher: fetchRenderedText DONE in ${elapsed}ms, ${renderedHtml.length} chars")
        return renderedHtml
    }

    /**
     * ★ Solve a Cloudflare Turnstile challenge on a page using native touch events.
     *
     * The mkissa.to watch page shows a Turnstile popup with a "Verify you are human" checkbox.
     * The checkbox is inside a cross-origin iframe (challenges.cloudflare.com) — JavaScript
     * CANNOT click inside it. But Android's native MotionEvent API CAN — it simulates a
     * physical touch at screen coordinates, which the WebView processes as real user input.
     *
     * This method:
     * 1. Loads the page in the WebView
     * 2. Waits for the Turnstile iframe to render (2s)
     * 3. Gets the iframe's bounding rect via JavaScript (we CAN read the rect of a cross-origin iframe)
     * 4. Dispatches a native MotionEvent (ACTION_DOWN + ACTION_UP) at the center of the iframe
     * 5. Waits for the page to redirect / cf_clearance cookie to be set (up to 15s)
     *
     * @param url The watch page URL
     * @param timeoutMs Total timeout (default 45s)
     * @return true if the challenge was solved
     */
    fun solveCloudflareTurnstile(url: String, timeoutMs: Long = 45_000): Boolean {
        ensureWebView()
        val startTime = System.currentTimeMillis()
        MKissaLog.i("WebViewFetcher: solveCloudflareTurnstile — loading $url")

        val solvedLatch = CountDownLatch(1)
        var solved = false

        synchronized(fetchLock) {
            mainHandler.post {
                try {
                    webView?.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            MKissaLog.d("WebViewFetcher: Turnstile page loaded: $loadedUrl")

                            // Check if this is the challenge page or the actual content
                            view?.evaluateJavascript(
                                "(function(){ return document.title; })()",
                            ) { titleResult ->
                                val title = parseJsStringResult(titleResult)
                                MKissaLog.d("WebViewFetcher: page title = '$title'")

                                if (title.contains("Just a moment") || title.contains("challenge") || title.contains("Performing")) {
                                    // This is the Cloudflare challenge page — auto-click the Turnstile checkbox
                                    MKissaLog.i("WebViewFetcher: Cloudflare challenge detected — preparing native touch event")

                                    // Wait 2s for the Turnstile widget to fully render
                                    mainHandler.postDelayed({
                                        // Step 1: Get the Turnstile iframe's bounding rect via JavaScript
                                        // We CAN read the rect of a cross-origin iframe element (just not its contents)
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var iframe = document.querySelector('iframe[src*="challenges.cloudflare.com"]') ||
                                                             document.querySelector('iframe[title*="Widget"]') ||
                                                             document.querySelector('iframe[title*="Cloudflare"]') ||
                                                             document.querySelector('#turnstile-wrapper iframe') ||
                                                             document.querySelector('iframe');
                                                if (iframe) {
                                                    var rect = iframe.getBoundingClientRect();
                                                    return JSON.stringify({
                                                        left: rect.left,
                                                        top: rect.top,
                                                        width: rect.width,
                                                        height: rect.height,
                                                        centerX: rect.left + rect.width / 2,
                                                        centerY: rect.top + rect.height / 2
                                                    });
                                                }
                                                return null;
                                            })();
                                        """.trimIndent()) { rectResult ->
                                            val rect = parseJsStringResult(rectResult)
                                            MKissaLog.d("WebViewFetcher: Turnstile iframe rect: $rect")

                                            if (rect.isNotBlank() && rect != "null") {
                                                // Parse the JSON rect
                                                try {
                                                    val centerX = rect.substringAfter("\"centerX\":").substringBefore(",").toFloatOrNull() ?: 0f
                                                    val centerY = rect.substringAfter("\"centerY\":").substringBefore("}").toFloatOrNull() ?: 0f
                                                    MKissaLog.i("WebViewFetcher: dispatching native touch at ($centerX, $centerY)")

                                                    // Step 2: Dispatch native MotionEvent at the iframe's center
                                                    // Convert CSS pixels to physical pixels (density)
                                                    val density = context.resources.displayMetrics.density
                                                    val physicalX = centerX * density
                                                    val physicalY = centerY * density

                                                    val downTime = SystemClock.uptimeMillis()
                                                    val downEvent = MotionEvent.obtain(
                                                        downTime, downTime, MotionEvent.ACTION_DOWN,
                                                        physicalX, physicalY, 0,
                                                    )
                                                    view?.dispatchTouchEvent(downEvent)
                                                    downEvent.recycle()

                                                    // Small delay between DOWN and UP (like a real tap)
                                                    mainHandler.postDelayed({
                                                        val upTime = SystemClock.uptimeMillis()
                                                        val upEvent = MotionEvent.obtain(
                                                            downTime, upTime, MotionEvent.ACTION_UP,
                                                            physicalX, physicalY, 0,
                                                        )
                                                        view?.dispatchTouchEvent(upEvent)
                                                        upEvent.recycle()
                                                        MKissaLog.i("WebViewFetcher: native touch event dispatched (tap)")
                                                    }, 100)

                                                    // Step 3: Wait 10s for the Turnstile to process + page to redirect
                                                    mainHandler.postDelayed({
                                                        view?.evaluateJavascript(
                                                            "(function(){ return document.title + '|' + document.cookie; })()",
                                                        ) { result ->
                                                            val parsed = parseJsStringResult(result)
                                                            val newTitle = parsed.substringBefore("|")
                                                            val cookies = parsed.substringAfter("|")
                                                            MKissaLog.d("WebViewFetcher: after tap — title='$newTitle', has_cf_clearance=${cookies.contains("cf_clearance")}")
                                                            if (cookies.contains("cf_clearance") || (!newTitle.contains("Just a moment") && !newTitle.contains("challenge") && !newTitle.contains("Performing"))) {
                                                                MKissaLog.i("WebViewFetcher: Turnstile SOLVED — cf_clearance set or page changed")
                                                                solved = true
                                                                solvedLatch.countDown()
                                                            } else {
                                                                // Retry the tap one more time
                                                                MKissaLog.d("WebViewFetcher: first tap didn't solve — retrying")
                                                                val downTime2 = SystemClock.uptimeMillis()
                                                                val downEvent2 = MotionEvent.obtain(
                                                                    downTime2, downTime2, MotionEvent.ACTION_DOWN,
                                                                    physicalX, physicalY, 0,
                                                                )
                                                                view?.dispatchTouchEvent(downEvent2)
                                                                downEvent2.recycle()
                                                                mainHandler.postDelayed({
                                                                    val upEvent2 = MotionEvent.obtain(
                                                                        downTime2, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                                                                        physicalX, physicalY, 0,
                                                                    )
                                                                    view?.dispatchTouchEvent(upEvent2)
                                                                    upEvent2.recycle()
                                                                }, 100)

                                                                // Wait 5 more seconds
                                                                mainHandler.postDelayed({
                                                                    view?.evaluateJavascript(
                                                                        "(function(){ return document.title + '|' + document.cookie; })()",
                                                                    ) { result2 ->
                                                                        val parsed2 = parseJsStringResult(result2)
                                                                        val title2 = parsed2.substringBefore("|")
                                                                        val cookies2 = parsed2.substringAfter("|")
                                                                        if (cookies2.contains("cf_clearance") || (!title2.contains("Just a moment") && !title2.contains("challenge") && !title2.contains("Performing"))) {
                                                                            MKissaLog.i("WebViewFetcher: Turnstile SOLVED on retry")
                                                                            solved = true
                                                                        } else {
                                                                            MKissaLog.w("WebViewFetcher: Turnstile NOT solved after 2 taps")
                                                                        }
                                                                        solvedLatch.countDown()
                                                                    }
                                                                }, 5000)
                                                            }
                                                        }
                                                    }, 10000)
                                                } catch (e: Exception) {
                                                    MKissaLog.e("WebViewFetcher: failed to parse iframe rect — ${e.message}")
                                                    solvedLatch.countDown()
                                                }
                                            } else {
                                                MKissaLog.w("WebViewFetcher: no Turnstile iframe found — page may have already solved")
                                                // Check if the page already has cf_clearance
                                                view?.evaluateJavascript("(function(){ return document.cookie; })()") { cookieResult ->
                                                    val cookies = parseJsStringResult(cookieResult)
                                                    if (cookies.contains("cf_clearance")) {
                                                        MKissaLog.i("WebViewFetcher: cf_clearance already present — no tap needed")
                                                        solved = true
                                                    }
                                                    solvedLatch.countDown()
                                                }
                                            }
                                        }
                                    }, 2000)
                                } else {
                                    // Page loaded successfully — not a challenge page
                                    MKissaLog.i("WebViewFetcher: page loaded (not a challenge) — title='$title'")
                                    solved = true
                                    solvedLatch.countDown()
                                }
                            }
                        }
                    }
                    MKissaLog.d("WebViewFetcher: loading watch page for Turnstile: $url")
                    webView?.loadUrl(url)
                } catch (e: Exception) {
                    MKissaLog.e("WebViewFetcher: solveCloudflareTurnstile loadUrl failed", e)
                    solvedLatch.countDown()
                }
            }

            if (!solvedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                MKissaLog.w("WebViewFetcher: solveCloudflareTurnstile timeout after ${timeoutMs}ms")
            }
        }

        // Restore the default WebViewClient
        mainHandler.post {
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    MKissaLog.d("WebViewFetcher: page loaded: $url")
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        MKissaLog.i("WebViewFetcher: solveCloudflareTurnstile DONE in ${elapsed}ms, solved=$solved")
        return solved
    }

    /**
     * ★ Load a video player page, auto-click play buttons, and extract the video URL.
     *
     * This handles players that require user interaction (clicking play) before the video
     * source is set. Also blocks ad redirects (common on free streaming sites).
     *
     * @param url The player page URL
     * @param clickDelayMs Delay between clicks (default 3s)
     * @param maxClicks Maximum number of click attempts (default 3)
     * @param pollTimeoutMs How long to poll for video.src (default 15s)
     * @return The video URL (m3u8 or mp4), or empty string if not found
     */
    fun loadAndExtractVideo(
        url: String,
        clickDelayMs: Long = 3000,
        maxClicks: Int = 3,
        pollTimeoutMs: Long = 15_000,
    ): String {
        ensureWebView()
        MKissaLog.i("WebViewFetcher: loadAndExtractVideo — loading $url")

        val videoUrlLatch = CountDownLatch(1)
        var videoUrl = ""
        var clickCount = 0

        synchronized(fetchLock) {
            mainHandler.post {
                try {
                    webView?.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            MKissaLog.d("WebViewFetcher: page loaded: $loadedUrl")

                            // Check if video.src is already set
                            checkVideoSrc(view) { src ->
                                if (src.isNotBlank()) {
                                    videoUrl = src
                                    videoUrlLatch.countDown()
                                } else {
                                    // Auto-click the play button
                                    clickCount++
                                    if (clickCount <= maxClicks) {
                                        MKissaLog.i("WebViewFetcher: auto-clicking play button (attempt $clickCount/$maxClicks)")
                                        view?.evaluateJavascript("""
                                            (function() {
                                                // Try various play button selectors
                                                var btn = document.querySelector('[data-play]') ||
                                                         document.querySelector('.play-button') ||
                                                         document.querySelector('button[aria-label*="play"]') ||
                                                         document.querySelector('vds-play-button') ||
                                                         document.querySelector('[part*="play"]') ||
                                                         document.querySelector('.vds-play-button') ||
                                                         document.querySelector('#vid_play') ||
                                                         document.querySelector('#desk button') ||
                                                         document.querySelector('#desk');
                                                if (btn) { btn.click(); return 'clicked: ' + btn.tagName; }

                                                // Try clicking the video element
                                                var v = document.querySelector('video');
                                                if (v) {
                                                    try { v.play(); } catch(e) {}
                                                    v.click();
                                                    return 'clicked video';
                                                }

                                                // Try clicking any clickable element in the center
                                                var desk = document.querySelector('#desk') ||
                                                           document.querySelector('.player') ||
                                                           document.querySelector('[data-player]');
                                                if (desk) { desk.click(); return 'clicked desk'; }

                                                return 'no play button found';
                                            })();
                                        """.trimIndent()) { clickResult ->
                                            MKissaLog.d("WebViewFetcher: click result: ${parseJsStringResult(clickResult)}")
                                        }

                                        // After the click delay, check video.src again
                                        mainHandler.postDelayed({
                                            checkVideoSrc(view) { src ->
                                                if (src.isNotBlank()) {
                                                    videoUrl = src
                                                    videoUrlLatch.countDown()
                                                } else if (clickCount < maxClicks) {
                                                    // Trigger another onPageFinished-like cycle
                                                    mainHandler.post {
                                                        view?.evaluateJavascript("(function(){ return document.title; })()") { _ ->
                                                            // Re-check after a short delay
                                                            mainHandler.postDelayed({
                                                                checkVideoSrc(view) { src2 ->
                                                                    if (src2.isNotBlank()) {
                                                                        videoUrl = src2
                                                                        videoUrlLatch.countDown()
                                                                    } else if (clickCount < maxClicks) {
                                                                        // Force another click cycle
                                                                        onPageFinished(view, loadedUrl)
                                                                    }
                                                                }
                                                            }, clickDelayMs)
                                                        }
                                                    }
                                                }
                                            }
                                        }, clickDelayMs)
                                    }
                                }
                            }
                        }

                        // ★ Block ad redirects — only allow the original player URL
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val reqUrl = request?.url?.toString() ?: ""
                            // Allow the original player page + same-origin requests
                            if (reqUrl.startsWith(url.substringBefore("#")) ||
                                reqUrl.startsWith("data:") ||
                                reqUrl.startsWith("about:")) {
                                return false // allow
                            }
                            // Block everything else (ad redirects)
                            MKissaLog.d("WebViewFetcher: blocked redirect to: ${MKissaLog.trunc(reqUrl, 80)}")
                            return true // block
                        }
                    }
                    MKissaLog.d("WebViewFetcher: loading player page: $url")
                    webView?.loadUrl(url)
                } catch (e: Exception) {
                    MKissaLog.e("WebViewFetcher: loadAndExtractVideo failed", e)
                    videoUrlLatch.countDown()
                }
            }

            if (!videoUrlLatch.await(pollTimeoutMs + (clickDelayMs * maxClicks), TimeUnit.MILLISECONDS)) {
                MKissaLog.w("WebViewFetcher: loadAndExtractVideo timeout")
            }
        }

        // Restore the default WebViewClient
        mainHandler.post {
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    MKissaLog.d("WebViewFetcher: page loaded: $url")
                }
            }
        }

        MKissaLog.i("WebViewFetcher: loadAndExtractVideo DONE, videoUrl=${MKissaLog.trunc(videoUrl, 80)}")
        return videoUrl
    }

    /** Check if the video element has a src set. */
    private fun checkVideoSrc(view: WebView?, callback: (String) -> Unit) {
        view?.evaluateJavascript("""
            (function() {
                var v = document.querySelector('video');
                if (v) {
                    var src = v.src || v.currentSrc || '';
                    if (!src) {
                        var s = v.querySelector('source');
                        if (s) src = s.src;
                    }
                    return src;
                }
                return '';
            })();
        """.trimIndent()) { result ->
            val src = parseJsStringResult(result)
            if (src.isNotBlank() && src.startsWith("http")) {
                MKissaLog.i("WebViewFetcher: found video src: ${MKissaLog.trunc(src, 100)}")
            }
            callback(src)
        }
    }

    /**
     * ★ Same-origin fetch from the currently loaded page.
     * After [fetchRenderedText] loads a page, the WebView is on that page's origin.
     * This method evaluates `fetch(url)` from the page's JavaScript context — the fetch
     * is same-origin (no CORS issues). Used for APIs that don't send CORS headers
     * (Filemoon playback API, Uni player API).
     *
     * @param url The URL to fetch (relative or absolute — if relative, uses the page's origin)
     * @param timeoutMs Timeout in milliseconds
     * @return The response text, or empty string on failure
     */
    fun fetchSameOrigin(url: String, timeoutMs: Long = 20_000): String {
        ensureWebView()
        val id = atomicId.incrementAndGet().toString()
        val state = RequestState()
        pendingRequests[id] = state
        val startTime = System.currentTimeMillis()
        MKissaLog.d("WebViewFetcher: fetchSameOrigin id=$id url=${MKissaLog.trunc(url, 80)}")

        synchronized(fetchLock) {
            mainHandler.post {
                webView?.evaluateJavascript(buildFetchTextJs(id, url), null)
            }
            if (!state.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pendingRequests.remove(id)
                MKissaLog.w("WebViewFetcher: fetchSameOrigin timeout for ${MKissaLog.trunc(url, 60)}")
                return ""
            }
        }
        pendingRequests.remove(id)
        state.error?.let {
            MKissaLog.w("WebViewFetcher: fetchSameOrigin error: $it")
            return ""
        }
        val elapsed = System.currentTimeMillis() - startTime
        MKissaLog.i("WebViewFetcher: fetchSameOrigin id=$id DONE in ${elapsed}ms")
        return state.textResult ?: ""
    }

    /**
     * ★ Evaluate arbitrary JavaScript in the currently loaded page's context.
     * Returns the result as a string (or empty string on failure).
     */
    fun evaluateJs(js: String, timeoutMs: Long = 15_000): String {
        ensureWebView()
        val latch = CountDownLatch(1)
        var result = ""
        synchronized(fetchLock) {
            mainHandler.post {
                webView?.evaluateJavascript(js) { res ->
                    result = parseJsStringResult(res)
                    latch.countDown()
                }
            }
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                MKissaLog.w("WebViewFetcher: evaluateJs timeout")
                return ""
            }
        }
        return result
    }

    fun destroy() {
        webViewReady = false
        mainHandler.post {
            try { webView?.destroy() } catch (_: Exception) {}
            webView = null
        }
        pendingRequests.clear()
    }

    private fun parseJsStringResult(result: String?): String {
        if (result.isNullOrBlank() || result == "null") return ""
        return try {
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result.substring(1, result.length - 1)
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\'", "'")
            } else {
                result
            }
        } catch (e: Exception) {
            result
        }
    }

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

    private fun escapeJsString(s: String): String {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
    }
}
