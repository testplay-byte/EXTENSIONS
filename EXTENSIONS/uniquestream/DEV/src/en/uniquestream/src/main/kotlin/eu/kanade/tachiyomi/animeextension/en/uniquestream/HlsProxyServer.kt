/**
 * Local HTTP proxy for HLS playback through Cloudflare-protected CDN.
 *
 * Architecture (v16.17 — URL-rewriting proxy, ExoPlayer handles AES-128 decryption):
 *   - The caller (getHosterList) fetches and parses the master m3u8 itself,
 *     then registers each variant playlist URL here.
 *   - The player receives a DIRECT variant playlist URL (no master involved).
 *   - When the player requests a variant URL, the proxy:
 *     1. Fetches the real variant m3u8 from CDN (via our OkHttpClient → Cloudflare OK)
 *     2. Rewrites ALL URLs to proxy URLs (segments, key URI, init segments, etc.)
 *     3. Keeps #EXT-X-KEY tag INTACT (just rewrites the key URI to a proxy URL)
 *     4. Serves the rewritten variant m3u8 to ExoPlayer
 *   - When the player requests a segment/key/init:
 *     1. Fetches from CDN through our OkHttpClient (Cloudflare bypass)
 *     2. Serves the raw bytes as-is
 *   - ExoPlayer sees the #EXT-X-KEY tag, fetches the key from our proxy,
 *     and handles AES-128 decryption natively (it's a core, well-tested ExoPlayer feature).
 *
 * Why this approach (not server-side decryption like v16.15/v16.16):
 *   - v16.15/v16.16 tried server-side AES-128 decryption — still failed.
 *   - Server-side decryption has many failure points: key fetch, IV parsing,
 *     per-segment IV computation, padding mode, data validation.
 *   - If key fetch failed, the old code stripped #EXT-X-KEY and served encrypted
 *     data as plain → guaranteed "unrecognized file format" error.
 *   - ExoPlayer's built-in AES-128 is battle-tested and handles all edge cases.
 *   - By keeping #EXT-X-KEY and proxying the key URI, we let ExoPlayer do
 *     what it does best while the proxy handles Cloudflare.
 */
package eu.kanade.tachiyomi.animeextension.en.uniquestream

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

class HlsProxyServer(
    private val client: OkHttpClient,
    private val upstreamHeaders: Headers,
) {
    companion object {
        private const val TAG = "UQ-Proxy"
        private const val IDLE_TIMEOUT_MS = 600_000L
        private const val SOCKET_TIMEOUT_MS = 120_000
    }

    // -- Server state --------------------------------------------------

    private var serverSocket: ServerSocket? = null
    private var baseUrl: String = ""
    private val running = AtomicBoolean(false)
    private val lastActivity = AtomicLong(System.currentTimeMillis())
    private val nextId = AtomicInteger(0)

    /** Maps proxy path ID -> real absolute upstream URL. */
    private val urlMap = ConcurrentHashMap<Int, String>()

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "UQ-Proxy").apply { isDaemon = true }
    }
    private var acceptThread: Thread? = null
    private var idleThread: Thread? = null

    val isRunning: Boolean get() = running.get()

    // -- Public API ---------------------------------------------------

    /** Register a URL and get a proxy URL + ID. */
    private fun registerUrlInternal(realUrl: String, isM3u8: Boolean): Pair<String, Int> {
        ensureStarted()
        val id = nextId.getAndIncrement()
        urlMap[id] = realUrl
        val suffix = if (isM3u8) ".m3u8" else ""
        val proxyUrl = "$baseUrl/p/$id$suffix"
        logD("Registered /p/$id$suffix -> ${trunc(realUrl, 120)}")
        return Pair(proxyUrl, id)
    }

    /** Public register that only returns the URL (used by UniQuestream.kt). */
    fun registerUrl(realUrl: String, isM3u8: Boolean = false): String {
        return registerUrlInternal(realUrl, isM3u8).first
    }

    /** Clear all registered URLs (call when switching episodes). */
    fun clearUrls() {
        urlMap.clear()
        logD("URL map cleared")
    }

    /** Stop the proxy server. */
    fun stop() {
        if (!running.getAndSet(false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        executor.shutdownNow()
        urlMap.clear()
        logI("Proxy stopped")
    }

    // -- Server lifecycle ----------------------------------------------

    private fun ensureStarted() {
        if (!running.compareAndSet(false, true)) return
        try {
            val sock = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
            serverSocket = sock
            baseUrl = "http://127.0.0.1:${sock.localPort}"
            lastActivity.set(System.currentTimeMillis())
            acceptThread = Thread({ acceptLoop() }, "UQ-Proxy-Accept").apply { isDaemon = true; start() }
            idleThread = Thread({ idleMonitor() }, "UQ-Proxy-Idle").apply { isDaemon = true; start() }
            logI("Proxy started at $baseUrl")
        } catch (e: Exception) {
            running.set(false)
            throw e
        }
    }

    private fun acceptLoop() {
        while (running.get()) {
            try {
                val socket = serverSocket?.accept() ?: break
                socket.soTimeout = SOCKET_TIMEOUT_MS
                executor.execute { handleRequest(socket) }
            } catch (_: Exception) {}
        }
    }

    private fun idleMonitor() {
        while (running.get()) {
            try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            if (System.currentTimeMillis() - lastActivity.get() > IDLE_TIMEOUT_MS) {
                logD("Idle timeout -- stopping")
                stop()
                break
            }
        }
    }

    // -- Request handling ----------------------------------------------

    private fun handleRequest(socket: Socket) {
        try {
            lastActivity.set(System.currentTimeMillis())
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3 || parts[0] != "GET") {
                logD("handleRequest: non-GET request: $requestLine")
                sendResponse(output, 405, "Method Not Allowed", "text/plain", "Method Not Allowed".toByteArray())
                return
            }

            val path = parts[1]
            logD("handleRequest: $path")

            // Drain remaining headers
            var headerCount = 0
            while (true) {
                val line = readLine(input) ?: break
                headerCount++
                if (line.isEmpty()) break
            }
            logD("handleRequest: $path (drained $headerCount headers)")

            routeRequest(path, output)
        } catch (e: SocketException) {
            logD("Connection closed by player (normal)")
        } catch (e: Exception) {
            Log.e(TAG, "handleRequest error", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun routeRequest(path: String, output: OutputStream) {
        try {
            val cleanPath = path.removeSuffix(".m3u8")
            val segments = cleanPath.trimStart('/').split("/")
            if (segments.size == 2 && segments[0] == "p") {
                val id = segments[1].toIntOrNull()
                if (id == null) {
                    logE("routeRequest: invalid ID in path $path")
                    return sendResponse(output, 400, "Bad Request", "text/plain", "Invalid ID".toByteArray())
                }
                serveProxied(id, output)
            } else {
                logD("routeRequest: 404 for $path")
                sendResponse(output, 404, "Not Found", "text/plain", "Not Found".toByteArray())
            }
        } catch (e: SocketException) {
            logD("Connection closed by player during $path (normal)")
        } catch (e: Exception) {
            Log.e(TAG, "routeRequest error for $path", e)
            try { sendResponse(output, 500, "Internal Server Error", "text/plain", "Error".toByteArray()) } catch (_: Exception) {}
        }
    }

    // -- Fetch upstream and serve --------------------------------------

    private fun serveProxied(id: Int, output: OutputStream) {
        val realUrl = urlMap[id]
        if (realUrl == null) {
            logE("/p/$id: URL not found in map. Registered count=${urlMap.size}, keys=${urlMap.keys.take(5)}")
            return sendResponse(output, 404, "Not Found", "text/plain", "URL expired".toByteArray())
        }

        logD("PROXY /p/$id -> ${trunc(realUrl, 150)}")

        try {
            val request = Request.Builder()
                .url(realUrl)
                .headers(upstreamHeaders)
                .build()
            val response = client.newCall(request).execute()
            val code = response.code
            val contentType: String = response.header("Content-Type") ?: ""
            val bytes = response.body?.bytes()
            response.close()

            if (bytes == null) {
                logE("PROXY: null body, HTTP $code for ${trunc(realUrl, 100)}")
                return sendResponse(output, 502, "Bad Gateway", "text/plain", "Empty response".toByteArray())
            }

            if (code != 200) {
                logE("PROXY: HTTP $code for ${trunc(realUrl, 150)}")
                return sendResponse(output, code, "Upstream Error", "text/plain", "HTTP $code".toByteArray())
            }

            val hexPrefix = bytes.take(16).joinToString(" ") { "%02x".format(it) }
            logD("PROXY: fetched ${bytes.size}B ct=$contentType hex=[$hexPrefix]")

            // Determine if this is an m3u8 playlist
            val isM3u8 = contentType.contains("mpegurl") ||
                contentType.contains("vnd.apple") ||
                realUrl.contains(".m3u8")

            if (isM3u8) {
                val text = String(bytes, Charsets.UTF_8)

                if (!text.trimStart().startsWith("#EXTM3U")) {
                    logE("PROXY: URL had .m3u8 but content is NOT m3u8! First 300: ${trunc(text, 300)}")
                    val ct = if (contentType.isBlank()) "application/octet-stream" else contentType
                    sendResponse(output, 200, "OK", ct, bytes)
                    return
                }

                // Check for encryption
                val keyLines = text.lines().filter { it.trim().startsWith("#EXT-X-KEY") }
                val hasExtXKey = keyLines.isNotEmpty()
                logI("PROXY: m3u8 ${bytes.size}B, has #EXT-X-KEY = $hasExtXKey${if (hasExtXKey) " (${keyLines.size} tag(s))" else ""}")
                if (hasExtXKey) {
                    // Log the first key tag for debugging
                    logI("PROXY: first #EXT-X-KEY: ${trunc(keyLines.first(), 200)}")
                }

                // Count segments for logging
                val segCount = text.lines().count { !it.trim().startsWith("#") && it.trim().isNotEmpty() }
                logI("PROXY: m3u8 has $segCount segment(s)")

                // Rewrite the m3u8: rewrite ALL URLs to proxy URLs
                // Keep #EXT-X-KEY tag intact — just rewrite its URI to a proxy URL
                val rewritten = rewriteM3u8(text, realUrl, id)
                logI("PROXY: Rewrote m3u8 ${bytes.size}B -> ${rewritten.length}B")
                sendResponse(output, 200, "OK", "application/vnd.apple.mpegurl",
                    rewritten.toByteArray(Charsets.UTF_8))
            } else {
                // Segment, key, init file, etc. — serve as-is (ExoPlayer handles decryption)
                val ct = if (contentType.isBlank()) {
                    guessContentType(realUrl, bytes)
                } else {
                    contentType
                }
                logD("PROXY: serving ${bytes.size}B as $ct")
                sendResponse(output, 200, "OK", ct, bytes)
            }
        } catch (e: SocketException) {
            logD("PROXY: connection closed by player during fetch (normal)")
        } catch (e: Exception) {
            logE("PROXY fetch failed for ${trunc(realUrl, 100)}", e)
            try { sendResponse(output, 502, "Bad Gateway", "text/plain", "Upstream fetch failed".toByteArray()) } catch (_: Exception) {}
        }
    }

    /** Guess content type from URL and magic bytes. */
    private fun guessContentType(url: String, bytes: ByteArray): String {
        // Check for MP4 box signature (fMP4 / CMAF)
        if (bytes.size >= 8) {
            val boxType = String(bytes, 4, 4)
            if (boxType == "ftyp" || boxType == "moof" || boxType == "styp") {
                return "video/mp4"
            }
        }
        // Check for MPEG-TS sync byte
        if (bytes.isNotEmpty() && bytes[0] == 0x47.toByte()) {
            return "video/MP2T"
        }
        // Fall back to URL extension
        return when {
            url.contains(".ts") -> "video/MP2T"
            url.contains(".m4s") -> "video/mp4"
            url.contains(".mp4") -> "video/mp4"
            url.contains(".key") -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }

    // -- m3u8 URL rewriting -------------------------------------------

    /**
     * Rewrite a variant m3u8: rewrite ALL URLs to proxy URLs.
     * Keeps #EXT-X-KEY tag intact (ExoPlayer handles decryption).
     * All relative URLs are resolved to absolute before registration.
     */
    private fun rewriteM3u8(m3u8: String, upstreamUrl: String, variantId: Int): String {
        val baseDir = upstreamUrl.substringBeforeLast("/") + "/"
        val lines = m3u8.lines()
        val result = StringBuilder()
        var segmentCount = 0

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            // Handle #EXT-X-KEY tag: rewrite URI attribute but KEEP the tag
            // ExoPlayer needs this tag to know about encryption and fetch the key
            if (trimmed.startsWith("#EXT-X-KEY:")) {
                val rewritten = rewriteKeyTag(trimmed, baseDir)
                result.appendLine(rewritten)
                logI("M3U8 /p/$variantId: Rewrote #EXT-X-KEY key URI to proxy URL")
                continue
            }

            // Other tags: rewrite URI/URL attributes (e.g. #EXT-X-MAP:URI="init.mp4")
            if (trimmed.startsWith("#")) {
                result.appendLine(rewriteTagAttrs(trimmed, baseDir))
                continue
            }

            // Bare URL = segment. Resolve to absolute first, then register.
            val absoluteUrl = resolveUrl(baseDir, trimmed)
            val (proxyUrl, _) = registerUrlInternal(absoluteUrl, isM3u8 = false)
            result.appendLine(proxyUrl)
            segmentCount++
        }

        logI("M3U8 /p/$variantId: Rewrote $segmentCount segment(s)")
        return result.toString()
    }

    /**
     * Rewrite a #EXT-X-KEY tag: replace the URI with a proxy URL.
     * Keeps ALL other attributes (METHOD, IV, KEYFORMAT, etc.) intact.
     */
    private fun rewriteKeyTag(tagLine: String, baseDir: String): String {
        val uriMatch = Regex("URI=\"([^\"]+)\"").find(tagLine)
        if (uriMatch == null) {
            logW("rewriteKeyTag: no URI= in #EXT-X-KEY! Tag: ${trunc(tagLine, 200)}")
            return tagLine // pass through as-is
        }

        val keyRelativeUrl = uriMatch.groupValues[1]
        val keyAbsoluteUrl = resolveUrl(baseDir, keyRelativeUrl)
        val (proxyUrl, proxyId) = registerUrlInternal(keyAbsoluteUrl, isM3u8 = false)

        logI("rewriteKeyTag: /p/$proxyId key ${trunc(keyAbsoluteUrl, 100)} -> $proxyUrl")

        // Replace just the URI value, keep everything else
        return tagLine.replace(
            "URI=\"${uriMatch.groupValues[1]}\"",
            "URI=\"$proxyUrl\""
        )
    }

    /** Rewrite URI/URL attributes in HLS tags (e.g. #EXT-X-MAP:URI="init.mp4"). */
    private fun rewriteTagAttrs(line: String, baseDir: String): String {
        val regex = Regex("(URI|URL)=\"([^\"]+)\"")
        return regex.replace(line) { match ->
            val attr = match.groupValues[1]
            val value = match.groupValues[2]
            if (value.startsWith("data:")) {
                "$attr=\"$value\""
            } else {
                val absoluteUrl = resolveUrl(baseDir, value)
                val looksLikeM3u8 = absoluteUrl.contains(".m3u8")
                val (proxyUrl, _) = registerUrlInternal(absoluteUrl, isM3u8 = looksLikeM3u8)
                "$attr=\"$proxyUrl\""
            }
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        return base + relative
    }

    // -- HTTP response helpers ----------------------------------------

    private fun sendResponse(
        out: OutputStream,
        status: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
    ) {
        val header = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    // -- Utility ------------------------------------------------------

    private fun readLine(input: InputStream): String? {
        val buf = StringBuilder()
        var c: Int
        while (true) {
            c = input.read()
            if (c == -1) return if (buf.isEmpty()) null else buf.toString()
            if (c == '\r'.code) continue
            if (c == '\n'.code) break
            buf.append(c.toChar())
        }
        return buf.toString()
    }

    private fun logD(msg: String) = Log.d(TAG, msg)
    private fun logI(msg: String) = Log.i(TAG, msg)
    private fun logW(msg: String) = Log.w(TAG, msg)
    private fun logE(msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    }

    private fun trunc(s: String, maxLen: Int = 200): String =
        if (s.length <= maxLen) s else s.substring(0, maxLen) + "...[${s.length}]"
}
