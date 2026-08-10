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

/**
 * Local HTTP proxy for HLS playback through Cloudflare-protected CDN.
 *
 * Architecture (v16.14 — Anikoto-style, no master pass-through):
 *   - The caller (getHosterList) fetches and parses the master m3u8 itself,
 *     then registers each variant playlist URL here.
 *   - The player receives a DIRECT variant playlist URL (no master involved).
 *   - When the player requests a variant URL, the proxy:
 *     1. Fetches the real variant m3u8 from CDN
 *     2. Rewrites all internal URLs (segments, keys, maps) to proxy URLs
 *     3. Serves the rewritten variant m3u8
 *   - When the player requests a segment/key/map, the proxy fetches
 *     from CDN and passes through.
 *
 * URL scheme:
 *   /p/{id}         -> any proxied URL (variant m3u8, segment, key, init mp4)
 *   /p/{id}.m3u8    -> same, but .m3u8 suffix helps MPV detect HLS format
 *
 * Why this works better than master pass-through:
 *   - One fewer m3u8 fetch+rewrite in the chain (master eliminated)
 *   - Player gets a simple, direct variant URL
 *   - Master parsing happens in Kotlin (debuggable) not in MPV
 *   - Follows the proven Anikoto proxy pattern
 */
class HlsProxyServer(
    private val client: OkHttpClient,
    private val upstreamHeaders: Headers,
) {
    companion object {
        private const val TAG = "UQ-Proxy"
        private const val IDLE_TIMEOUT_MS = 600_000L
        private const val SOCKET_TIMEOUT_MS = 120_000
    }

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

    /** Register a URL and get a proxy URL. If [isM3u8] is true, appends .m3u8 suffix. */
    fun registerUrl(realUrl: String, isM3u8: Boolean = false): String {
        ensureStarted()
        val id = nextId.getAndIncrement()
        urlMap[id] = realUrl
        val suffix = if (isM3u8) ".m3u8" else ""
        val proxyUrl = "$baseUrl/p/$id$suffix"
        logD("Registered /p/$id$suffix -> ${trunc(realUrl, 120)}")
        return proxyUrl
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
                sendResponse(output, 405, "Method Not Allowed", "text/plain", "Method Not Allowed".toByteArray())
                return
            }

            val path = parts[1]
            // Drain remaining headers
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
            }

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
            // Strip .m3u8 suffix for ID extraction
            val cleanPath = path.removeSuffix(".m3u8")
            val segments = cleanPath.trimStart('/').split("/")
            if (segments.size == 2 && segments[0] == "p") {
                val id = segments[1].toIntOrNull()
                if (id == null) {
                    return sendResponse(output, 400, "Bad Request", "text/plain", "Invalid ID".toByteArray())
                }
                serveProxied(id, output)
            } else {
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
            logE("/p/$id: URL not found in map (expired?)")
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
                logE("PROXY: null body, HTTP $code")
                return sendResponse(output, 502, "Bad Gateway", "text/plain", "Empty response".toByteArray())
            }

            if (code != 200) {
                logE("PROXY: HTTP $code for ${trunc(realUrl, 150)}")
                return sendResponse(output, code, "Upstream Error", "text/plain", "HTTP $code".toByteArray())
            }

            // Log first 16 bytes as hex for diagnostics
            val hexPrefix = bytes.take(16).joinToString(" ") { "%02x".format(it) }
            logD("PROXY: OK ${bytes.size}B ct=$contentType hex=[$hexPrefix]")

            // Determine if this is an m3u8 playlist
            val isM3u8 = contentType.contains("mpegurl") ||
                contentType.contains("vnd.apple") ||
                realUrl.contains(".m3u8")

            if (isM3u8) {
                val text = String(bytes, Charsets.UTF_8)

                // Validate it's actually an m3u8 (starts with #EXTM3U)
                if (!text.trimStart().startsWith("#EXTM3U")) {
                    logE("PROXY: URL had .m3u8 but content is NOT m3u8! First 200 chars: ${trunc(text, 200)}")
                    // Treat as non-m3u8 and pass through
                    val ct = if (contentType.isBlank()) "application/octet-stream" else contentType
                    sendResponse(output, 200, "OK", ct, bytes)
                    return
                }

                val rewritten = rewriteM3u8(text, realUrl)
                logI("PROXY: Rewrote m3u8 ${bytes.size}B -> ${rewritten.length}B")
                sendResponse(output, 200, "OK", "application/vnd.apple.mpegurl",
                    rewritten.toByteArray(Charsets.UTF_8))
            } else {
                // Segment / key / init file — pass through as-is
                val ct = if (contentType.isBlank()) {
                    // Guess based on URL extension
                    when {
                        realUrl.contains(".ts") -> "video/MP2T"
                        realUrl.contains(".mp4") -> "video/mp4"
                        realUrl.contains(".key") -> "application/octet-stream"
                        else -> "application/octet-stream"
                    }
                } else {
                    contentType
                }
                sendResponse(output, 200, "OK", ct, bytes)
            }
        } catch (e: SocketException) {
            logD("PROXY: connection closed by player during fetch (normal)")
        } catch (e: Exception) {
            logE("PROXY fetch failed for ${trunc(realUrl, 100)}", e)
            try { sendResponse(output, 502, "Bad Gateway", "text/plain", "Upstream fetch failed".toByteArray()) } catch (_: Exception) {}
        }
    }

    // -- m3u8 URL rewriting --------------------------------------------

    /**
     * Rewrite all URLs in a variant m3u8 playlist to point through this proxy.
     * Handles: #EXT-X-KEY (URI=), #EXT-X-MAP (URI=), bare segment URLs.
     * Does NOT need to handle #EXT-X-STREAM-INF (that's only in master playlists,
     * which we parse in getHosterList, not here).
     */
    private fun rewriteM3u8(m3u8: String, upstreamUrl: String): String {
        val baseDir = upstreamUrl.substringBeforeLast("/") + "/"
        val lines = m3u8.lines()
        val result = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            if (trimmed.startsWith("#")) {
                // Tag line: rewrite URI= and URL= attributes
                result.appendLine(rewriteTagAttrs(trimmed, baseDir))
                continue
            }

            // Bare URL (segment)
            result.appendLine(registerAndRewrite(trimmed, baseDir, isM3u8 = false))
        }

        return result.toString()
    }

    private fun rewriteTagAttrs(line: String, baseDir: String): String {
        val regex = Regex("(URI|URL)=\"([^\"]+)\"")
        return regex.replace(line) { match ->
            val attr = match.groupValues[1]
            val value = match.groupValues[2]
            if (value.startsWith("data:")) {
                "$attr=\"$value\""
            } else {
                val looksLikeM3u8 = value.contains(".m3u8")
                val proxyUrl = registerAndRewrite(value, baseDir, isM3u8 = looksLikeM3u8)
                "$attr=\"$proxyUrl\""
            }
        }
    }

    /** Resolve a relative URL against baseDir, register it, return proxy URL. */
    private fun registerAndRewrite(relativeOrAbsolute: String, baseDir: String, isM3u8: Boolean): String {
        val absolute = resolveUrl(baseDir, relativeOrAbsolute)
        return registerUrl(absolute, isM3u8)
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
    private fun logE(msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    }

    private fun trunc(s: String, maxLen: Int = 200): String =
        if (s.length <= maxLen) s else s.substring(0, maxLen) + "...[${s.length}]"
}
