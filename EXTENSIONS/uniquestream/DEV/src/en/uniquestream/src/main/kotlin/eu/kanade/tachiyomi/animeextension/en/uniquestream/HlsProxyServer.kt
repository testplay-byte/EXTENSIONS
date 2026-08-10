package eu.kanade.tachiyomi.animeextension.en.uniquestream

import android.util.Log
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal local HTTP proxy that:
 * 1. Serves HLS playlists with rewritten segment URLs pointing to this proxy.
 * 2. Fetches PNG-wrapped segments, strips the PNG header, serves raw MPEG-TS.
 *
 * Routes:
 *   GET /proxy?url=<url-encoded upstream URL>
 *     - If response is m3u8 text: rewrites relative URLs to proxy URLs.
 *     - If response is binary: strips PNG header, serves as video/mp2t.
 *     - Otherwise: transparent passthrough.
 */
class HlsProxyServer(
    private val client: OkHttpClient,
    private val segmentHeaders: Headers,
) {
    companion object {
        private const val TAG = "UniQuestream-Proxy"
        private const val IDLE_TIMEOUT_MS = 600_000L // 10 minutes
        private const val SOCKET_TIMEOUT_MS = 120_000
    }

    private var serverSocket: ServerSocket? = null
    private var baseUrl: String = ""
    private val _running = AtomicBoolean(false)
    private val lastActivity = AtomicLong(System.currentTimeMillis())

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "UQ-Proxy-Worker").apply { isDaemon = true }
    }
    private var acceptThread: Thread? = null
    private var idleThread: Thread? = null

    val running: Boolean get() = _running.get()

    fun start(): String {
        if (_running.get()) return baseUrl
        val sock = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverSocket = sock
        baseUrl = "http://127.0.0.1:${sock.localPort}"
        _running.set(true)
        lastActivity.set(System.currentTimeMillis())

        acceptThread = Thread({ acceptLoop() }, "UQ-Proxy-Accept").apply { isDaemon = true; start() }
        idleThread = Thread({ idleMonitor() }, "UQ-Proxy-Idle").apply { isDaemon = true; start() }
        Log.i(TAG, "Proxy started at $baseUrl")
        return baseUrl
    }

    fun stop() {
        if (!_running.getAndSet(false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        executor.shutdownNow()
        Log.i(TAG, "Proxy stopped")
    }

    /** Build a proxy URL for the given upstream URL. */
    fun proxyUrl(upstreamUrl: String): String {
        val encoded = java.net.URLEncoder.encode(upstreamUrl, "UTF-8")
        return "$baseUrl/proxy?url=$encoded"
    }

    // ── Accept loop ──────────────────────────────────────────────

    private fun acceptLoop() {
        while (_running.get()) {
            try {
                val socket = serverSocket?.accept() ?: break
                socket.soTimeout = SOCKET_TIMEOUT_MS
                lastActivity.set(System.currentTimeMillis())
                executor.submit { handleRequest(socket) }
            } catch (_: Exception) {
                // ServerSocket closed or interrupted
            }
        }
    }

    // ── Idle monitor ─────────────────────────────────────────────

    private fun idleMonitor() {
        while (_running.get()) {
            try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            if (System.currentTimeMillis() - lastActivity.get() > IDLE_TIMEOUT_MS) {
                Log.i(TAG, "Idle timeout — stopping")
                stop()
                break
            }
        }
    }

    // ── Request handler ──────────────────────────────────────────

    private fun handleRequest(socket: Socket) {
        try {
            val `in` = socket.getInputStream()
            val out = socket.getOutputStream()

            // Parse request line: "GET /path HTTP/1.1\r\n"
            val requestLine = readLine(`in`) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 3 || parts[0] != "GET") {
                sendResponse(out, 400, "Bad Request", "text/plain", "Bad Request".toByteArray())
                return
            }

            val rawPath = parts[1]
            // Parse query params
            val queryStart = rawPath.indexOf('?')
            val path = if (queryStart >= 0) rawPath.substring(0, queryStart) else rawPath
            val queryString = if (queryStart >= 0) rawPath.substring(queryStart + 1) else ""

            // Consume remaining headers
            while (true) {
                val line = readLine(`in`) ?: break
                if (line.isEmpty()) break
            }

            if (path == "/proxy") {
                val params = parseQuery(queryString)
                val upstreamUrl = params["url"]?.let { URLDecoder.decode(it, "UTF-8") }
                if (upstreamUrl == null) {
                    sendResponse(out, 400, "Bad Request", "text/plain", "Missing url param".toByteArray())
                    return
                }
                handleProxyRequest(out, upstreamUrl)
            } else {
                sendResponse(out, 404, "Not Found", "text/plain", "Not Found".toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
        lastActivity.set(System.currentTimeMillis())
    }

    // ── Proxy: fetch upstream, process, serve ─────────────────────

    private fun handleProxyRequest(out: OutputStream, upstreamUrl: String) {
        try {
            val request = Request.Builder()
                .url(upstreamUrl)
                .headers(segmentHeaders)
                .build()
            val response = client.newCall(request).execute()
            val body = response.body
            val contentType = body?.contentType()?.toString() ?: ""
            val bytes = body?.bytes() ?: return
            response.close()

            // Determine if this is an m3u8 playlist or a binary segment
            if (isM3u8(contentType, upstreamUrl)) {
                // Rewrite relative URLs in the m3u8 to point through the proxy
                val text = String(bytes, Charsets.UTF_8)
                val rewritten = rewriteM3u8Urls(text, upstreamUrl)
                sendResponse(out, 200, "OK", "application/vnd.apple.mpegurl",
                    rewritten.toByteArray(Charsets.UTF_8))
            } else if (isPngWrapped(bytes)) {
                // PNG-wrapped segment — strip header, serve as raw MPEG-TS
                val processed = stripPngHeader(bytes)
                sendResponse(out, 200, "OK", "video/mp2t", processed)
            } else {
                // Other binary (AES key, etc.) — pass through with original type
                val ct = if (contentType.isBlank()) "application/octet-stream" else contentType
                sendResponse(out, 200, "OK", ct, bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to proxy $upstreamUrl", e)
            try {
                sendResponse(out, 502, "Bad Gateway", "text/plain",
                    "Upstream fetch failed".toByteArray())
            } catch (_: Exception) {}
        }
    }

    // ── m3u8 URL rewriting ────────────────────────────────────────

    /**
     * Rewrite relative URLs in an m3u8 playlist to point through the proxy.
     * Handles: #EXT-X-KEY URI=, #EXT-X-MEDIA URI=, #EXT-X-STREAM-INF URL=,
     * segment lines (bare URLs or after #EXTINF), and #EXT-X-I-FRAME-STREAM-INF URI=.
     */
    private fun rewriteM3u8Urls(m3u8: String, baseUrl: String): String {
        val lines = m3u8.lines()
        val result = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#EXT")) {
                // Tag line — may contain URI= or URL= that needs rewriting
                val rewritten = rewriteTagLine(trimmed, baseUrl)
                result.appendLine(rewritten)
            } else {
                // Bare URL (segment reference)
                val absolute = resolveUrl(baseUrl, trimmed)
                result.appendLine(proxyUrl(absolute))
            }
        }
        return result.toString()
    }

    private fun rewriteTagLine(line: String, baseUrl: String): String {
        // Rewrite URI="..." or URL="..." attributes to point through proxy
        val uriRegex = Regex("(URI|URL)=\"([^\"]+)\"")
        return uriRegex.replace(line) { match ->
            val attr = match.groupValues[1]
            val value = match.groupValues[2]
            // Don't rewrite data: URIs
            if (value.startsWith("data:")) {
                "$attr=\"$value\""
            } else {
                // Resolve relative/absolute URLs and route through proxy
                val absolute = resolveUrl(baseUrl, value)
                "$attr=\"${proxyUrl(absolute)}\""
            }
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        val baseEnd = base.lastIndexOf('/')
        val baseDir = if (baseEnd >= 0) base.substring(0, baseEnd + 1) else "$base/"
        return baseDir + relative
    }

    private fun isM3u8(contentType: String, url: String): Boolean {
        return contentType.contains("mpegurl") ||
            contentType.contains("vnd.apple") ||
            url.contains(".m3u8")
    }

    // ── PNG header stripping ──────────────────────────────────────

    /**
     * Check if data starts with a PNG signature.
     */
    private fun isPngWrapped(data: ByteArray): Boolean {
        return data.size >= 8 &&
            data[0] == 0x89.toByte() &&
            data[1] == 'P'.code.toByte() &&
            data[2] == 'N'.code.toByte() &&
            data[3] == 'G'.code.toByte()
    }

    /**
     * Strips PNG header from segment data. Uses the IEND chunk detection algorithm:
     * 1. Check for PNG signature (89 50 4E 47)
     * 2. Scan for IEND chunk marker
     * 3. Cut after IEND chunk (IEND + 4-byte CRC)
     * 4. Align to MPEG-TS sync byte 0x47 at 188-byte stride
     */
    private fun stripPngHeader(data: ByteArray): ByteArray {
        if (data.size < 8) return data

        // Check PNG signature
        if (data[0] != 0x89.toByte() || data[1] != 'P'.code.toByte() ||
            data[2] != 'N'.code.toByte() || data[3] != 'G'.code.toByte()
        ) {
            // Not PNG — pass through
            return data
        }

        // Scan for IEND chunk
        val iendMarker = byteArrayOf('I'.code.toByte(), 'E'.code.toByte(),
            'N'.code.toByte(), 'D'.code.toByte())
        var cut = -1
        for (i in 0..data.size - 4) {
            if (data[i] == iendMarker[0] && data[i + 1] == iendMarker[1] &&
                data[i + 2] == iendMarker[2] && data[i + 3] == iendMarker[3]
            ) {
                cut = i + 8 // Skip IEND (4) + CRC (4)
                break
            }
        }
        if (cut < 0 || cut >= data.size) return data

        var stripped = data.copyOfRange(cut, data.size)

        // MPEG-TS alignment: find 0x47 sync byte at 188-byte stride
        val scanLimit = minOf(stripped.size - 188, 400)
        for (i in 0..scanLimit) {
            if (stripped[i] == 0x47.toByte() && stripped[i + 188] == 0x47.toByte()) {
                return stripped.copyOfRange(i, stripped.size)
            }
        }
        return stripped
    }

    // ── HTTP response helpers ─────────────────────────────────────

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
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    // ── Utility ──────────────────────────────────────────────────

    private fun readLine(`in`: InputStream): String? {
        val buf = StringBuilder()
        var c: Int
        while (true) {
            c = `in`.read()
            if (c == -1) return if (buf.isEmpty()) null else buf.toString()
            if (c == '\r') continue
            if (c == '\n') break
            buf.append(c.toChar())
        }
        return buf.toString()
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").associate { param ->
            val eq = param.indexOf('=')
            if (eq >= 0) {
                param.substring(0, eq) to param.substring(eq + 1)
            } else {
                param to ""
            }
        }
    }
}
