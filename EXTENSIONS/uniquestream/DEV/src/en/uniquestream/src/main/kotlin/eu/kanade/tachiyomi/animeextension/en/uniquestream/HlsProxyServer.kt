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
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Local HTTP proxy for HLS playback through Cloudflare-protected CDN.
 *
 * Why needed: The CDN (get2.mediacache.cc) sits behind Cloudflare.
 * MPV's internal HTTP client gets 403'd when fetching variant playlists
 * and segments directly. This proxy routes ALL HLS requests through the
 * extension's OkHttpClient (which has proper headers), bypassing the block.
 *
 * Architecture (ID-based URL mapping — zero URL encoding issues):
 *   1. getHosterList registers master m3u8 URL -> gets proxy URL /m/{id}.m3u8
 *   2. MPV requests /m/{id}.m3u8 -> proxy fetches master, rewrites URLs to /p/{id}.m3u8
 *   3. MPV requests /p/{id}.m3u8 -> proxy fetches real URL, rewrites if m3u8, else passes through
 *
 * v16.13 changes:
 *   - CRITICAL FIX: Append .m3u8 to all m3u8 proxy URLs so MPV/ffmpeg
 *     can detect the HLS format from the URL path (fixes "unrecognized file format")
 *   - Routing strips .m3u8 suffix before parsing IDs
 *   - Added Cache-Control: no-cache header (matches Anikoto pattern)
 *   - Segment content-type: video/MP2T (uppercase, matches Anikoto)
 *   - First-16-bytes hex logging for every proxied response (diagnostic)
 *   - Clearer, structured log format
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

    /** Register a master m3u8 URL. Returns the proxy URL for MPV. */
    fun registerMaster(realUrl: String): String {
        ensureStarted()
        val id = nextId.getAndIncrement()
        urlMap[id] = realUrl
        val proxyUrl = "$baseUrl/m/$id.m3u8"
        logI("MASTER registered: /m/$id.m3u8 -> ${trunc(realUrl, 120)}")
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
        if (running.get()) return
        val sock = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverSocket = sock
        baseUrl = "http://127.0.0.1:${sock.localPort}"
        running.set(true)
        lastActivity.set(System.currentTimeMillis())
        acceptThread = Thread({ acceptLoop() }, "UQ-Proxy-Accept").apply { isDaemon = true; start() }
        idleThread = Thread({ idleMonitor() }, "UQ-Proxy-Idle").apply { isDaemon = true; start() }
        logI("Proxy started at $baseUrl")
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
            // Strip .m3u8 suffix for routing (MPV/ffmpeg needs it for format detection)
            val cleanPath = path.removeSuffix(".m3u8")
            val segments = cleanPath.trimStart('/').split("/")
            when {
                segments.size == 2 && segments[0] == "m" -> {
                    val id = segments[1].toIntOrNull()
                    if (id == null) return sendResponse(output, 400, "Bad Request", "text/plain", "Invalid ID".toByteArray())
                    serveProxied(id, output, isMaster = true)
                }
                segments.size == 2 && segments[0] == "p" -> {
                    val id = segments[1].toIntOrNull()
                    if (id == null) return sendResponse(output, 400, "Bad Request", "text/plain", "Invalid ID".toByteArray())
                    serveProxied(id, output, isMaster = false)
                }
                else -> sendResponse(output, 404, "Not Found", "text/plain", "Not Found".toByteArray())
            }
        } catch (e: SocketException) {
            logD("Connection closed by player during $path (normal)")
        } catch (e: Exception) {
            Log.e(TAG, "routeRequest error for $path", e)
            try { sendResponse(output, 500, "Internal Server Error", "text/plain", "Error".toByteArray()) } catch (_: Exception) {}
        }
    }

    // -- Fetch upstream and serve --------------------------------------

    private fun serveProxied(id: Int, output: OutputStream, isMaster: Boolean) {
        val realUrl = urlMap[id]
        if (realUrl == null) {
            logE("/${if (isMaster) "m" else "p"}/$id: URL not found in map (expired?)")
            return sendResponse(output, 404, "Not Found", "text/plain", "URL expired".toByteArray())
        }

        val label = if (isMaster) "MASTER" else "PROXY"
        logD("$label /${if (isMaster) "m" else "p"}/$id -> ${trunc(realUrl, 150)}")

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
                logE("$label: null body, HTTP $code")
                return sendResponse(output, 502, "Bad Gateway", "text/plain", "Empty response".toByteArray())
            }

            if (code != 200) {
                logE("$label: HTTP $code for ${trunc(realUrl, 150)}")
                return sendResponse(output, code, "Upstream Error", "text/plain", "HTTP $code".toByteArray())
            }

            // Log first 16 bytes as hex for diagnostics
            val hexPrefix = bytes.take(16).joinToString(" ") { "%02x".format(it) }
            logD("$label: OK ${bytes.size}B ct=$contentType hex=[$hexPrefix]")

            val isM3u8 = contentType.contains("mpegurl") ||
                contentType.contains("vnd.apple") ||
                realUrl.contains(".m3u8")

            if (isM3u8) {
                val text = String(bytes, Charsets.UTF_8)
                val rewritten = rewriteM3u8(text, realUrl)
                if (isMaster) {
                    logMasterRewrite(text, rewritten)
                }
                sendResponse(output, 200, "OK", "application/vnd.apple.mpegurl",
                    rewritten.toByteArray(Charsets.UTF_8))
            } else {
                val outputBytes = stripPngHeader(bytes)
                val outputHex = outputBytes.take(16).joinToString(" ") { "%02x".format(it) }
                if (outputBytes.size != bytes.size) {
                    logI("PNG stripped: ${bytes.size}B -> ${outputBytes.size}B hex=[$outputHex]")
                }
                // Use video/MP2T for segments (matches Anikoto pattern, uppercase T)
                val ct = if (contentType.isBlank()) "video/MP2T" else contentType
                sendResponse(output, 200, "OK", ct, outputBytes)
            }
        } catch (e: SocketException) {
            logD("$label: connection closed by player during fetch (normal)")
        } catch (e: Exception) {
            logE("$label fetch failed for ${trunc(realUrl, 100)}", e)
            try { sendResponse(output, 502, "Bad Gateway", "text/plain", "Upstream fetch failed".toByteArray()) } catch (_: Exception) {}
        }
    }

    // -- m3u8 URL rewriting --------------------------------------------

    /**
     * Rewrite all URLs in an m3u8 playlist to point through this proxy.
     * URLs that look like m3u8 get .m3u8 appended (critical for MPV/ffmpeg format detection).
     */
    private fun rewriteM3u8(m3u8: String, upstreamUrl: String): String {
        val baseDir = upstreamUrl.substringBeforeLast("/") + "/"
        val lines = m3u8.lines()
        val result = StringBuilder()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                result.appendLine()
                i++
                continue
            }

            if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                // Variant playlist: tag line preserved, URL on NEXT line is rewritten
                result.appendLine(trimmed)
                i++
                if (i < lines.size) {
                    val nextLine = lines[i].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        result.appendLine(registerAndRewrite(nextLine, baseDir, isM3u8 = true))
                    } else {
                        result.appendLine(nextLine)
                    }
                }
                i++
                continue
            }

            if (trimmed.startsWith("#")) {
                // Tag line: rewrite URI= and URL= attributes
                result.appendLine(rewriteTagAttrs(trimmed, baseDir))
                i++
                continue
            }

            // Bare URL (segment or key reference)
            result.appendLine(registerAndRewrite(trimmed, baseDir, isM3u8 = false))
            i++
        }

        return result.toString()
    }

    private fun rewriteTagAttrs(line: String, baseDir: String): String {
        // Must use a regular (non-raw) Kotlin string here.
        // See v16.12 fix notes for why raw strings are broken for this pattern.
        val regex = Regex("(URI|URL)=\\"([^\\"]+)\\"")
        return regex.replace(line) { match ->
            val attr = match.groupValues[1]
            val value = match.groupValues[2]
            if (value.startsWith("data:")) {
                "$attr=\"$value\""
            } else {
                // Detect if this URL points to an m3u8 playlist
                val looksLikeM3u8 = value.contains(".m3u8")
                val proxyUrl = registerAndRewrite(value, baseDir, isM3u8 = looksLikeM3u8)
                "$attr=\"$proxyUrl\""
            }
        }
    }

    /** Resolve a relative URL against baseDir, register it, return proxy URL. */
    private fun registerAndRewrite(relativeOrAbsolute: String, baseDir: String, isM3u8: Boolean): String {
        val absolute = resolveUrl(baseDir, relativeOrAbsolute)
        val id = nextId.getAndIncrement()
        urlMap[id] = absolute
        // CRITICAL: Append .m3u8 for m3u8 URLs so MPV/ffmpeg can detect HLS format
        val suffix = if (isM3u8) ".m3u8" else ""
        return "$baseUrl/p/$id$suffix"
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        return base + relative
    }

    // -- PNG header stripping ------------------------------------------

    /**
     * Strip PNG wrapper from CDN-wrapped HLS segments.
     * Some CDNs wrap MPEG-TS data inside a minimal PNG container.
     * If the data starts with a PNG signature (89 50 4E 47), we find the
     * IEND marker and scan for MPEG-TS sync bytes to extract the real data.
     *
     * This is a no-op for data that doesn't start with a PNG signature
     * (e.g., raw MPEG-TS starting with 0x47, or AES-encrypted data).
     */
    private fun stripPngHeader(data: ByteArray): ByteArray {
        if (data.size < 8) return data
        if (!(data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte() &&
                data[2] == 'N'.code.toByte() && data[3] == 'G'.code.toByte())) {
            return data
        }

        var cut = -1
        for (i in 0 until data.size - 4) {
            if (data[i] == 'I'.code.toByte() && data[i + 1] == 'E'.code.toByte() &&
                data[i + 2] == 'N'.code.toByte() && data[i + 3] == 'D'.code.toByte()) {
                cut = i + 8
                break
            }
        }
        if (cut < 0 || cut >= data.size) return data

        val scanLimit = minOf(data.size - 188, cut + 400)
        for (i in cut until scanLimit) {
            if (data[i] == 0x47.toByte() && data[i + 188] == 0x47.toByte()) {
                logI("PNG stripped: ${data.size}B -> ${data.size - i}B (TS sync at offset $i)")
                return data.copyOfRange(i, data.size)
            }
        }

        logI("PNG stripped (no TS sync found): ${data.size}B -> ${data.size - cut}B")
        return data.copyOfRange(cut, data.size)
    }

    // -- Master m3u8 logging ------------------------------------------

    private fun logMasterRewrite(original: String, rewritten: String) {
        val origLines = original.lines().filter { it.isNotBlank() }
        val newLines = rewritten.lines().filter { it.isNotBlank() }
        logI("MASTER ORIGINAL (${origLines.size} lines):")
        origLines.forEachIndexed { idx, ln ->
            logD("  [${idx + 1}] ${trunc(ln, 200)}")
        }
        logI("MASTER REWRITTEN (${newLines.size} lines):")
        newLines.forEachIndexed { idx, ln ->
            logD("  [${idx + 1}] ${trunc(ln, 200)}")
        }
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
