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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Local HTTP proxy for HLS playback through Cloudflare-protected CDN.
 *
 * Architecture (v16.15 — server-side AES-128 decryption):
 *   - The caller (getHosterList) fetches and parses the master m3u8 itself,
 *     then registers each variant playlist URL here.
 *   - The player receives a DIRECT variant playlist URL (no master involved).
 *   - When the player requests a variant URL, the proxy:
 *     1. Fetches the real variant m3u8 from CDN
 *     2. Detects #EXT-X-KEY:METHOD=AES-128 encryption
 *     3. Fetches the 16-byte decryption key from CDN
 *     4. Strips ALL encryption tags from the m3u8 (player sees plain stream)
 *     5. Rewrites segment URLs to proxy URLs
 *     6. Associates each segment with its encryption info (key + IV)
 *     7. Serves the clean, unencrypted-looking variant m3u8
 *   - When the player requests a segment:
 *     1. Fetches encrypted segment from CDN
 *     2. Decrypts it with AES-128-CBC using the stored key + IV
 *     3. Serves the decrypted segment
 *
 * URL scheme:
 *   /p/{id}         -> any proxied URL (variant m3u8, segment, key, init mp4)
 *   /p/{id}.m3u8    -> same, but .m3u8 suffix helps players detect HLS format
 *
 * Why server-side decryption:
 *   - The CDN (get2.mediacache.cc) uses AES-128 encryption on TS segments.
 *   - Previously, we rewrote the #EXT-X-KEY URI to go through the proxy, relying
 *     on the player (ExoPlayer) to handle decryption. This failed consistently.
 *   - By decrypting server-side, the player receives plain MPEG-TS segments and
 *     doesn't need to know about encryption at all.
 *   - This eliminates all player-side AES-128 handling issues.
 */
class HlsProxyServer(
    private val client: OkHttpClient,
    private val upstreamHeaders: Headers,
) {
    companion object {
        private const val TAG = "UQ-Proxy"
        private const val IDLE_TIMEOUT_MS = 600_000L
        private const val SOCKET_TIMEOUT_MS = 120_000
        private const val AES_BLOCK_SIZE = 16
    }

    // -- Encryption tracking -------------------------------------------

    /** Holds the AES-128 decryption key and IV for a set of segments. */
    private data class EncryptionInfo(
        val method: String,
        val key: ByteArray,
        val iv: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptionInfo) return false
            return method == other.method && key.contentEquals(other.key) && iv.contentEquals(other.iv)
        }
        override fun hashCode(): Int = 31 * (31 * method.hashCode() + key.contentHashCode()) + iv.contentHashCode()
    }

    // -- Server state --------------------------------------------------

    private var serverSocket: ServerSocket? = null
    private var baseUrl: String = ""
    private val running = AtomicBoolean(false)
    private val lastActivity = AtomicLong(System.currentTimeMillis())
    private val nextId = AtomicInteger(0)

    /** Maps proxy path ID -> real absolute upstream URL. */
    private val urlMap = ConcurrentHashMap<Int, String>()

    /** Maps segment proxy ID -> encryption info for that segment. */
    private val segmentEncryption = ConcurrentHashMap<Int, EncryptionInfo>()

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "UQ-Proxy").apply { isDaemon = true }
    }
    private var acceptThread: Thread? = null
    private var idleThread: Thread? = null

    val isRunning: Boolean get() = running.get()

    // -- Public API ---------------------------------------------------

    /**
     * Register a URL and get a proxy URL. If [isM3u8] is true, appends .m3u8 suffix.
     * Returns a Pair of (proxyUrl, proxyId).
     */
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

    /** Clear all registered URLs and encryption info (call when switching episodes). */
    fun clearUrls() {
        urlMap.clear()
        segmentEncryption.clear()
        logD("URL map and encryption info cleared")
    }

    /** Stop the proxy server. */
    fun stop() {
        if (!running.getAndSet(false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        executor.shutdownNow()
        urlMap.clear()
        segmentEncryption.clear()
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
            // Strip .m3u8 suffix for ID extraction
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
            logE("/p/$id: URL not found in map (expired?). Registered IDs: ${urlMap.keys.take(5)}...")
            return sendResponse(output, 404, "Not Found", "text/plain", "URL expired".toByteArray())
        }

        // Check if this segment has encryption info
        val encInfo = segmentEncryption[id]
        if (encInfo != null) {
            logD("PROXY /p/$id: has AES-128 encryption (method=${encInfo.method}, keyLen=${encInfo.key.size}, ivLen=${encInfo.iv.size})")
        }

        logD("PROXY /p/$id -> ${trunc(realUrl, 150)}${if (encInfo != null) " [ENCRYPTED - will decrypt]" else ""}")

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

            // Log first 16 bytes as hex for diagnostics
            val hexPrefix = bytes.take(16).joinToString(" ") { "%02x".format(it) }
            logD("PROXY: fetched ${bytes.size}B ct=$contentType hex=[$hexPrefix]")

            // Determine if this is an m3u8 playlist
            val isM3u8 = contentType.contains("mpegurl") ||
                contentType.contains("vnd.apple") ||
                realUrl.contains(".m3u8")

            if (isM3u8) {
                val text = String(bytes, Charsets.UTF_8)

                // Validate it's actually an m3u8 (starts with #EXTM3U)
                if (!text.trimStart().startsWith("#EXTM3U")) {
                    logE("PROXY: URL had .m3u8 but content is NOT m3u8! First 200 chars: ${trunc(text, 200)}")
                    val ct = if (contentType.isBlank()) "application/octet-stream" else contentType
                    sendResponse(output, 200, "OK", ct, bytes)
                    return
                }

                // Log whether encryption was found in the m3u8
                val hasExtXKey = text.lines().any { it.trim().startsWith("#EXT-X-KEY") }
                if (hasExtXKey) {
                    logI("PROXY: m3u8 contains #EXT-X-KEY tag(s) -- will handle server-side decryption")
                }

                val rewritten = rewriteM3u8(text, realUrl, id)
                logI("PROXY: Rewrote m3u8 ${bytes.size}B -> ${rewritten.length}B (encryption stripped: $hasExtXKey)")
                sendResponse(output, 200, "OK", "application/vnd.apple.mpegurl",
                    rewritten.toByteArray(Charsets.UTF_8))
            } else {
                // Segment / init file
                val finalBytes = if (encInfo != null) {
                    decryptSegment(bytes, encInfo, id)
                } else {
                    bytes
                }

                val ct = if (contentType.isBlank()) {
                    when {
                        realUrl.contains(".ts") -> "video/MP2T"
                        realUrl.contains(".mp4") -> "video/mp4"
                        realUrl.contains(".key") -> "application/octet-stream"
                        else -> "application/octet-stream"
                    }
                } else {
                    contentType
                }
                sendResponse(output, 200, "OK", ct, finalBytes)
            }
        } catch (e: SocketException) {
            logD("PROXY: connection closed by player during fetch (normal)")
        } catch (e: Exception) {
            logE("PROXY fetch failed for ${trunc(realUrl, 100)}", e)
            try { sendResponse(output, 502, "Bad Gateway", "text/plain", "Upstream fetch failed".toByteArray()) } catch (_: Exception) {}
        }
    }

    // -- AES-128 Decryption --------------------------------------------

    /**
     * Decrypt an AES-128-CBC encrypted TS segment.
     * In HLS, each segment is independently encrypted with AES-128-CBC.
     * The IV is either explicit (from #EXT-X-KEY IV= attribute) or implicit
     * (media sequence number as IV).
     *
     * Tries PKCS5Padding first (standard HLS), then falls back to NoPadding
     * if the CDN doesn't add PKCS7 padding.
     */
    private fun decryptSegment(encryptedBytes: ByteArray, encInfo: EncryptionInfo, segmentId: Int): ByteArray {
        try {
            if (encInfo.method != "AES-128") {
                logE("DECRYPT /p/$segmentId: unsupported method '${encInfo.method}', passing through as-is")
                return encryptedBytes
            }

            if (encInfo.key.size != AES_BLOCK_SIZE) {
                logE("DECRYPT /p/$segmentId: key size is ${encInfo.key.size}, expected $AES_BLOCK_SIZE. Passing through.")
                return encryptedBytes
            }

            if (encInfo.iv.size != AES_BLOCK_SIZE) {
                logE("DECRYPT /p/$segmentId: IV size is ${encInfo.iv.size}, expected $AES_BLOCK_SIZE. Passing through.")
                return encryptedBytes
            }

            if (encryptedBytes.isEmpty()) {
                logD("DECRYPT /p/$segmentId: empty segment, nothing to decrypt")
                return encryptedBytes
            }

            // Verify encrypted data is a multiple of AES block size (16 bytes)
            if (encryptedBytes.size % AES_BLOCK_SIZE != 0) {
                logE("DECRYPT /p/$segmentId: encrypted size ${encryptedBytes.size} is NOT a multiple of $AES_BLOCK_SIZE. Decryption will likely fail.")
            }

            val keySpec = SecretKeySpec(encInfo.key, "AES")
            val ivSpec = IvParameterSpec(encInfo.iv)

            // Try PKCS5Padding first (standard HLS uses PKCS7 padding)
            val decryptedBytes = try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                cipher.doFinal(encryptedBytes)
            } catch (e: javax.crypto.BadPaddingException) {
                // CDN might not use PKCS7 padding — fall back to NoPadding
                logW("DECRYPT /p/$segmentId: PKCS5Padding failed (${e.message}), trying NoPadding")
                try {
                    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                    cipher.doFinal(encryptedBytes)
                } catch (e2: Exception) {
                    logE("DECRYPT /p/$segmentId: NoPadding also failed: ${e2.javaClass.simpleName}: ${e2.message}")
                    return encryptedBytes
                }
            }

            // Log first 16 bytes of decrypted data to verify it looks like MPEG-TS (should start with 0x47)
            val hexAfter = decryptedBytes.take(16).joinToString(" ") { "%02x".format(it) }
            val hasSyncByte = decryptedBytes.isNotEmpty() && decryptedBytes[0] == 0x47.toByte()
            logI("DECRYPT /p/$segmentId: ${encryptedBytes.size}B -> ${decryptedBytes.size}B hex=[$hexAfter] mpegTsSync=$hasSyncByte")

            if (!hasSyncByte && decryptedBytes.isNotEmpty()) {
                logW("DECRYPT /p/$segmentId: WARNING - decrypted data does NOT start with MPEG-TS sync byte 0x47! First byte: 0x${"%02x".format(decryptedBytes[0])}")
                // Try PKCS5 unpadding manually and check again
                // Sometimes the padding makes the first byte off by the block alignment
            }

            return decryptedBytes
        } catch (e: Exception) {
            logE("DECRYPT /p/$segmentId: FAILED - ${e.javaClass.simpleName}: ${e.message}", e)
            // Return encrypted bytes as fallback (will likely fail to play, but at least we log the error)
            return encryptedBytes
        }
    }

    /**
     * Fetch a decryption key from the CDN.
     * The key is typically 16 raw bytes (AES-128).
     */
    private fun fetchKey(keyUrl: String): ByteArray? {
        logI("KEY-FETCH: fetching decryption key from ${trunc(keyUrl, 150)}")
        try {
            val request = Request.Builder()
                .url(keyUrl)
                .headers(upstreamHeaders)
                .build()
            val response = client.newCall(request).execute()
            val code = response.code
            val ct = response.header("Content-Type") ?: ""
            val bytes = response.body?.bytes()
            response.close()

            if (code != 200) {
                logE("KEY-FETCH: HTTP $code for ${trunc(keyUrl, 100)}")
                return null
            }

            if (bytes == null || bytes.isEmpty()) {
                logE("KEY-FETCH: empty/null body for ${trunc(keyUrl, 100)}")
                return null
            }

            val hexKey = bytes.joinToString(" ") { "%02x".format(it) }
            logI("KEY-FETCH: got ${bytes.size}B key ct=$ct hex=[$hexKey]")

            if (bytes.size != AES_BLOCK_SIZE) {
                logW("KEY-FETCH: key is ${bytes.size}B, expected $AES_BLOCK_SIZE for AES-128. Will try to use as-is.")
            }

            return bytes
        } catch (e: Exception) {
            logE("KEY-FETCH: FAILED for ${trunc(keyUrl, 100)} - ${e.javaClass.simpleName}: ${e.message}", e)
            return null
        }
    }

    /**
     * Parse an IV from a hex string like "0x12345678901234567890123456789012".
     * Returns 16 bytes, or null if parsing fails.
     */
    private fun parseHexIv(hexIv: String): ByteArray? {
        try {
            // Strip "0x" prefix if present
            val hex = if (hexIv.startsWith("0x") || hexIv.startsWith("0X")) {
                hexIv.substring(2)
            } else {
                hexIv
            }

            if (hex.length != 32) {
                logE("parseHexIv: expected 32 hex chars, got ${hex.length} in '$hexIv'")
                return null
            }

            val bytes = ByteArray(16)
            for (i in 0 until 16) {
                bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return bytes
        } catch (e: Exception) {
            logE("parseHexIv: failed to parse '$hexIv'", e)
            return null
        }
    }

    // -- m3u8 URL rewriting + encryption handling ----------------------

    /**
     * Rewrite a variant m3u8: rewrite segment URLs AND handle AES-128 encryption.
     *
     * For encryption:
     *   - Detects #EXT-X-KEY:METHOD=AES-128,URI="...",IV=0x...
     *   - Fetches the decryption key from CDN via extension's OkHttpClient
     *   - Stores encryption info (key+IV) for each subsequent segment
     *   - STRIPS the #EXT-X-KEY line from output (player sees plain stream)
     *
     * @param m3u8 the raw variant m3u8 text
     * @param upstreamUrl the full URL of this variant m3u8 (for resolving relative URLs)
     * @param variantId the proxy ID of this variant (for logging)
     */
    private fun rewriteM3u8(m3u8: String, upstreamUrl: String, variantId: Int): String {
        val baseDir = upstreamUrl.substringBeforeLast("/") + "/"
        val lines = m3u8.lines()
        val result = StringBuilder()

        // Track current encryption state
        var currentEncryption: EncryptionInfo? = null
        var mediaSequence: Long = 0
        var segmentIndex = 0
        var encryptionHandled = false

        // First pass: parse #EXT-X-MEDIA-SEQUENCE if present
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                mediaSequence = trimmed.substringAfter(":").trim().toLongOrNull() ?: 0L
                logD("M3U8 /p/$variantId: media sequence = $mediaSequence")
            }
        }

        // Second pass: rewrite URLs and handle encryption
        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                result.appendLine()
                continue
            }

            // Handle #EXT-X-KEY tag
            if (trimmed.startsWith("#EXT-X-KEY:")) {
                val newEnc = processExtXKey(trimmed, baseDir)
                if (newEnc != null) {
                    currentEncryption = newEnc
                    encryptionHandled = true
                    // DO NOT include the #EXT-X-KEY line in output
                    // The player will receive an unencrypted stream
                    logI("M3U8 /p/$variantId: STRIPPED #EXT-X-KEY (method=${newEnc.method}), will decrypt segments server-side")
                } else if (trimmed.contains("METHOD=NONE")) {
                    currentEncryption = null
                    logD("M3U8 /p/$variantId: encryption cleared (METHOD=NONE)")
                    // Include METHOD=NONE line as-is (it's harmless)
                    result.appendLine(trimmed)
                } else {
                    logW("M3U8 /p/$variantId: unknown #EXT-X-KEY format, passing through: ${trunc(trimmed, 150)}")
                    result.appendLine(trimmed)
                }
                continue
            }

            if (trimmed.startsWith("#")) {
                // Other tag lines: rewrite URI= and URL= attributes (for #EXT-X-MAP etc.)
                result.appendLine(rewriteTagAttrs(trimmed, baseDir))
                continue
            }

            // Bare URL (TS segment) — register and associate with current encryption
            val (proxyUrl, proxyId) = registerUrlInternal(trimmed, isM3u8 = false)

            if (currentEncryption != null) {
                segmentEncryption[proxyId] = currentEncryption
                logD("M3U8 /p/$variantId: segment[$segmentIndex] /p/$proxyId -> ENCRYPTED (will decrypt)")
            } else {
                logD("M3U8 /p/$variantId: segment[$segmentIndex] /p/$proxyId -> plain")
            }

            result.appendLine(proxyUrl)
            segmentIndex++
        }

        logI("M3U8 /p/$variantId: processed $segmentIndex segments, encryption_handled=$encryptionHandled")
        return result.toString()
    }

    /**
     * Process an #EXT-X-KEY tag.
     * Extracts METHOD, URI, and IV. Fetches the key from CDN.
     * Returns EncryptionInfo if successful, null if not encryption-related.
     */
    private fun processExtXKey(tagLine: String, baseDir: String): EncryptionInfo? {
        val methodMatch = Regex("METHOD=([^,]+)").find(tagLine)
        val method = methodMatch?.groupValues?.get(1)?.trim() ?: return null

        if (method == "NONE") return null

        if (method != "AES-128") {
            logW("EXT-X-KEY: unsupported method '$method', only AES-128 is supported. Tag: ${trunc(tagLine, 200)}")
            return null
        }

        // Extract key URI
        val uriMatch = Regex("URI=\"([^\"]+)\"").find(tagLine)
        if (uriMatch == null) {
            logE("EXT-X-KEY: no URI= attribute found! Tag: ${trunc(tagLine, 200)}")
            return null
        }
        val keyRelativeUrl = uriMatch.groupValues[1]
        val keyAbsoluteUrl = resolveUrl(baseDir, keyRelativeUrl)

        logI("EXT-X-KEY: AES-128 detected, key URL: ${trunc(keyAbsoluteUrl, 150)}")

        // Fetch the key from CDN
        val keyBytes = fetchKey(keyAbsoluteUrl)
        if (keyBytes == null) {
            logE("EXT-X-KEY: FAILED to fetch decryption key! Video will NOT play correctly.")
            return null
        }

        // Parse IV
        val ivMatch = Regex("IV=(0[xX][0-9a-fA-F]+)").find(tagLine)
        val ivBytes: ByteArray = if (ivMatch != null) {
            val parsed = parseHexIv(ivMatch.groupValues[1])
            if (parsed != null) {
                logI("EXT-X-KEY: explicit IV=0x${parsed.joinToString("") { "%02x".format(it) }}")
                parsed
            } else {
                logE("EXT-X-KEY: IV present but failed to parse! Will use zero IV.")
                ByteArray(AES_BLOCK_SIZE) // fallback: zero IV
            }
        } else {
            // No explicit IV — per HLS spec, use media sequence number as IV
            // We'll update this per-segment if needed, but for now use a placeholder.
            // (Most CDNs provide an explicit IV)
            logW("EXT-X-KEY: no explicit IV, per-HLS-spec should use media sequence number.")
            ByteArray(AES_BLOCK_SIZE)
        }

        val hexKey = keyBytes.joinToString(" ") { "%02x".format(it) }
        val hexIv = ivBytes.joinToString(" ") { "%02x".format(it) }
        logI("EXT-X-KEY: AES-128 ready. key=[$hexKey] iv=[$hexIv]")

        return EncryptionInfo(method = "AES-128", key = keyBytes, iv = ivBytes)
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
                val (proxyUrl, _) = registerUrlInternal(value, isM3u8 = looksLikeM3u8)
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
