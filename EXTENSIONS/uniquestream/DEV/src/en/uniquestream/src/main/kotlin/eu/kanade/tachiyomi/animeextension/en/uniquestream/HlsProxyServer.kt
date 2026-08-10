package eu.kanade.tachiyomi.animeextension.en.uniquestream

import android.util.Base64
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
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
 * Architecture (v16.16 — server-side AES-128 decryption, hardened):
 *   - The caller (getHosterList) fetches and parses the master m3u8 itself,
 *     then registers each variant playlist URL here.
 *   - The player receives a DIRECT variant playlist URL (no master involved).
 *   - When the player requests a variant URL, the proxy:
 *     1. Fetches the real variant m3u8 from CDN
 *     2. Detects #EXT-X-KEY:METHOD=AES-128 encryption
 *     3. Fetches the 16-byte decryption key from CDN (handles base64-encoded keys)
 *     4. Strips ALL encryption tags from the m3u8 (player sees plain stream)
 *     5. Rewrites segment URLs to proxy URLs
 *     6. Associates each segment with its encryption info (key + per-segment IV)
 *     7. Serves the clean, unencrypted-looking variant m3u8
 *   - When the player requests a segment:
 *     1. Fetches encrypted segment from CDN
 *     2. Decrypts it with AES-128-CBC using the stored key + IV
 *     3. Serves the decrypted segment
 *
 * v16.16 fixes over v16.15:
 *   - Base64 key detection: CDN may return key as base64 string, not raw bytes
 *   - Per-segment IV: when no explicit IV, uses (mediaSequence + segmentIndex)
 *   - IV without 0x prefix: handles both IV=0x... and IV=hex... formats
 *   - Key validation: rejects HTML error pages, validates key is actually crypto key
 *   - Key fetch failure: strips broken #EXT-X-KEY instead of passing through
 *   - fMP4 segment support: detects MP4 init segments and CMAF fragments
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

    /** Holds the AES-128 decryption info. IV is per-segment. */
    private data class EncryptionInfo(
        val method: String,
        val key: ByteArray,
        /** If explicitIv is set, ALL segments share this IV. */
        val explicitIv: ByteArray?,
        /** If explicitIv is null, IV is computed as (mediaSequence + segIndex). */
        val mediaSequence: Long,
    ) {
        /** Compute the IV for a specific segment index. */
        fun ivForSegment(segmentIndex: Int): ByteArray {
            if (explicitIv != null) return explicitIv
            // Per HLS spec: IV = big-endian 128-bit representation of (mediaSequence + segmentIndex)
            val seqNum = mediaSequence + segmentIndex
            val iv = ByteArray(16)
            val bigInt = BigInteger.valueOf(seqNum)
            val bytes = bigInt.toByteArray() // may be 1-13 bytes, big-endian
            // Copy into the LAST bytes of the IV array (big-endian 128-bit)
            val srcOffset = if (bytes.size > 16) bytes.size - 16 else 0
            val destOffset = 16 - (bytes.size - srcOffset)
            System.arraycopy(bytes, srcOffset, iv, destOffset, bytes.size - srcOffset)
            return iv
        }
    }

    // -- Server state --------------------------------------------------

    private var serverSocket: ServerSocket? = null
    private var baseUrl: String = ""
    private val running = AtomicBoolean(false)
    private val lastActivity = AtomicLong(System.currentTimeMillis())
    private val nextId = AtomicInteger(0)

    /** Maps proxy path ID -> real absolute upstream URL. */
    private val urlMap = ConcurrentHashMap<Int, String>()

    /**
     * Maps segment proxy ID -> encryption info for that segment.
     * Key = proxy ID of the segment, Value = (encryption info, segment index within variant).
     */
    private val segmentEncryption = ConcurrentHashMap<Int, Pair<EncryptionInfo, Int>>()

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

        // Check if this segment has encryption info
        val encPair = segmentEncryption[id]
        if (encPair != null) {
            val (encInfo, segIdx) = encPair
            val iv = encInfo.ivForSegment(segIdx)
            logD("PROXY /p/$id: ENCRYPTED method=${encInfo.method} keyLen=${encInfo.key.size} segIdx=$segIdx explicitIv=${encInfo.explicitIv != null}")
        }

        logD("PROXY /p/$id -> ${trunc(realUrl, 150)}${if (encPair != null) " [WILL DECRYPT]" else ""}")

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
                    logE("PROXY: URL had .m3u8 but content is NOT m3u8! First 200: ${trunc(text, 200)}")
                    val ct = if (contentType.isBlank()) "application/octet-stream" else contentType
                    sendResponse(output, 200, "OK", ct, bytes)
                    return
                }

                val hasExtXKey = text.lines().any { it.trim().startsWith("#EXT-X-KEY") }
                if (hasExtXKey) {
                    logI("PROXY: m3u8 contains #EXT-X-KEY tag(s) -- will handle server-side decryption")
                }

                val rewritten = rewriteM3u8(text, realUrl, id)
                logI("PROXY: Rewrote m3u8 ${bytes.size}B -> ${rewritten.length}B (encryption_found=$hasExtXKey)")
                sendResponse(output, 200, "OK", "application/vnd.apple.mpegurl",
                    rewritten.toByteArray(Charsets.UTF_8))
            } else {
                // Segment / init file
                val finalBytes = if (encPair != null) {
                    val (encInfo, segIdx) = encPair
                    val iv = encInfo.ivForSegment(segIdx)
                    decryptSegment(bytes, encInfo.key, iv, id)
                } else {
                    bytes
                }

                val ct = if (contentType.isBlank()) {
                    guessContentType(realUrl, bytes)
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

    /** Guess content type from URL and magic bytes. */
    private fun guessContentType(url: String, bytes: ByteArray): String {
        // Check for MP4 box signature (fMP4 / CMAF)
        if (bytes.size >= 8) {
            val boxSize = ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    (bytes[3].toInt() and 0xFF)
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

    // -- AES-128 Decryption --------------------------------------------

    /**
     * Decrypt an AES-128-CBC encrypted segment.
     * Tries PKCS5Padding first (standard HLS), then falls back to NoPadding.
     */
    private fun decryptSegment(encryptedBytes: ByteArray, key: ByteArray, iv: ByteArray, segmentId: Int): ByteArray {
        try {
            if (key.size != AES_BLOCK_SIZE) {
                logE("DECRYPT /p/$segmentId: key size is ${key.size}, expected $AES_BLOCK_SIZE. CANNOT decrypt.")
                return encryptedBytes
            }
            if (iv.size != AES_BLOCK_SIZE) {
                logE("DECRYPT /p/$segmentId: IV size is ${iv.size}, expected $AES_BLOCK_SIZE. CANNOT decrypt.")
                return encryptedBytes
            }
            if (encryptedBytes.isEmpty()) {
                logD("DECRYPT /p/$segmentId: empty segment")
                return encryptedBytes
            }
            if (encryptedBytes.size % AES_BLOCK_SIZE != 0) {
                logE("DECRYPT /p/$segmentId: size ${encryptedBytes.size} not multiple of $AES_BLOCK_SIZE. CANNOT decrypt.")
                return encryptedBytes
            }

            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)

            val hexIv = iv.joinToString("") { "%02x".format(it) }
            logI("DECRYPT /p/$segmentId: ${encryptedBytes.size}B key=${key.size}B iv=0x$hexIv")

            // Try PKCS5Padding first (standard HLS uses PKCS7 padding)
            val decryptedBytes = try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                cipher.doFinal(encryptedBytes)
            } catch (e: javax.crypto.BadPaddingException) {
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

            // Verify decrypted content
            val hexAfter = decryptedBytes.take(16).joinToString(" ") { "%02x".format(it) }
            val isTs = decryptedBytes.isNotEmpty() && decryptedBytes[0] == 0x47.toByte()
            val isMp4 = decryptedBytes.size >= 8 && String(decryptedBytes, 4, 4) == "ftyp"
            logI("DECRYPT /p/$segmentId: ${encryptedBytes.size}B -> ${decryptedBytes.size}B hex=[$hexAfter] ts=$isTs mp4=$isMp4")

            if (!isTs && !isMp4 && decryptedBytes.isNotEmpty()) {
                logW("DECRYPT /p/$segmentId: decrypted data is neither TS(0x47) nor MP4(ftyp). First byte=0x${"%02x".format(decryptedBytes[0])}. Wrong key or IV?")
            }

            return decryptedBytes
        } catch (e: Exception) {
            logE("DECRYPT /p/$segmentId: FAILED - ${e.javaClass.simpleName}: ${e.message}", e)
            return encryptedBytes
        }
    }

    // -- Key fetching --------------------------------------------------

    /**
     * Fetch a decryption key from the CDN.
     * Handles both raw 16-byte keys and base64-encoded keys.
     */
    private fun fetchKey(keyUrl: String): ByteArray? {
        logI("KEY-FETCH: START url=${trunc(keyUrl, 150)}")
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

            logI("KEY-FETCH: HTTP $code ct=$ct size=${bytes?.size ?: 0}")

            if (code != 200) {
                logE("KEY-FETCH: HTTP $code for ${trunc(keyUrl, 100)}")
                return null
            }
            if (bytes == null || bytes.isEmpty()) {
                logE("KEY-FETCH: empty/null body")
                return null
            }

            // Reject obviously wrong responses (HTML error pages, etc.)
            if (bytes.size > 64) {
                val preview = String(bytes, 0, minOf(100, bytes.size), Charsets.UTF_8)
                logE("KEY-FETCH: response is ${bytes.size}B (too large for a key). First 100: ${trunc(preview, 100)}")
                return null
            }
            if (bytes.size >= 4 && String(bytes, 0, 4) == "<htm") {
                logE("KEY-FETCH: response is HTML, not a key! First 100: ${trunc(String(bytes, Charsets.UTF_8), 100)}")
                return null
            }

            // If exactly 16 bytes -> raw key (standard)
            val finalKey: ByteArray = if (bytes.size == AES_BLOCK_SIZE) {
                logI("KEY-FETCH: got raw 16-byte key (standard)")
                bytes
            } else {
                // Try base64 decoding
                val base64Str = String(bytes, Charsets.UTF_8).trim()
                try {
                    val decoded = Base64.decode(base64Str, Base64.DEFAULT)
                    if (decoded.size == AES_BLOCK_SIZE) {
                        logI("KEY-FETCH: decoded base64 ${bytes.size}B -> raw 16-byte key")
                        decoded
                    } else {
                        logE("KEY-FETCH: base64 decoded to ${decoded.size}B, expected $AES_BLOCK_SIZE. Original was ${bytes.size}B.")
                        return null
                    }
                } catch (e: Exception) {
                    logE("KEY-FETCH: ${bytes.size}B response is not raw key and not valid base64. ${e.javaClass.simpleName}: ${e.message}")
                    return null
                }
            }

            val hexKey = finalKey.joinToString(" ") { "%02x".format(it) }
            logI("KEY-FETCH: SUCCESS key=[$hexKey]")
            return finalKey
        } catch (e: Exception) {
            logE("KEY-FETCH: EXCEPTION ${e.javaClass.simpleName}: ${e.message}", e)
            return null
        }
    }

    /**
     * Parse an IV from hex string. Handles both "0x1234..." and "1234..." formats.
     * Returns 16 bytes, or null if parsing fails.
     */
    private fun parseHexIv(hexIv: String): ByteArray? {
        try {
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
     */
    private fun rewriteM3u8(m3u8: String, upstreamUrl: String, variantId: Int): String {
        val baseDir = upstreamUrl.substringBeforeLast("/") + "/"
        val lines = m3u8.lines()
        val result = StringBuilder()

        var currentEncryption: EncryptionInfo? = null
        var mediaSequence: Long = 0
        var segmentIndex = 0
        var encryptionHandled = false

        // First pass: parse #EXT-X-MEDIA-SEQUENCE
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
                val newEnc = processExtXKey(trimmed, baseDir, mediaSequence)
                if (newEnc != null) {
                    currentEncryption = newEnc
                    encryptionHandled = true
                    // STRIP the #EXT-X-KEY line — player sees no encryption
                    logI("M3U8 /p/$variantId: STRIPPED #EXT-X-KEY (method=${newEnc.method}, explicitIv=${newEnc.explicitIv != null}, mediaSeq=${newEnc.mediaSequence})")
                } else if (trimmed.contains("METHOD=NONE")) {
                    currentEncryption = null
                    logD("M3U8 /p/$variantId: encryption cleared (METHOD=NONE)")
                    result.appendLine(trimmed)
                } else {
                    // processExtXKey returned null but it wasn't METHOD=NONE
                    // This means key fetch failed or unsupported method
                    // Do NOT pass through the broken tag — strip it to avoid player errors
                    logE("M3U8 /p/$variantId: STRIPPING broken #EXT-X-KEY (key fetch likely failed). Video may not play.")
                }
                continue
            }

            if (trimmed.startsWith("#")) {
                result.appendLine(rewriteTagAttrs(trimmed, baseDir))
                continue
            }

            // Bare URL (segment)
            val (proxyUrl, proxyId) = registerUrlInternal(trimmed, isM3u8 = false)

            if (currentEncryption != null) {
                // Store encryption info with the segment index for per-segment IV computation
                segmentEncryption[proxyId] = Pair(currentEncryption, segmentIndex)
                logD("M3U8 /p/$variantId: seg[$segmentIndex] /p/$proxyId -> ENCRYPTED")
            } else {
                logD("M3U8 /p/$variantId: seg[$segmentIndex] /p/$proxyId -> plain")
            }

            result.appendLine(proxyUrl)
            segmentIndex++
        }

        logI("M3U8 /p/$variantId: DONE segments=$segmentIndex encryption=$encryptionHandled")
        return result.toString()
    }

    /**
     * Process an #EXT-X-KEY tag.
     * Extracts METHOD, URI, IV. Fetches the key. Returns EncryptionInfo or null.
     */
    private fun processExtXKey(tagLine: String, baseDir: String, mediaSequence: Long): EncryptionInfo? {
        val methodMatch = Regex("METHOD=([^,]+)").find(tagLine)
        val method = methodMatch?.groupValues?.get(1)?.trim() ?: return null

        if (method == "NONE") return null

        if (method != "AES-128") {
            logW("EXT-X-KEY: unsupported method '$method'. Tag: ${trunc(tagLine, 200)}")
            return null
        }

        // Extract key URI
        val uriMatch = Regex("URI=\"([^\"]+)\"").find(tagLine)
        if (uriMatch == null) {
            logE("EXT-X-KEY: no URI= attribute! Tag: ${trunc(tagLine, 200)}")
            return null
        }
        val keyRelativeUrl = uriMatch.groupValues[1]
        val keyAbsoluteUrl = resolveUrl(baseDir, keyRelativeUrl)
        logI("EXT-X-KEY: AES-128 detected keyUrl=${trunc(keyAbsoluteUrl, 150)}")

        // Fetch the key
        val keyBytes = fetchKey(keyAbsoluteUrl)
        if (keyBytes == null) {
            logE("EXT-X-KEY: FAILED to fetch decryption key!")
            return null
        }

        // Parse IV — handle both "0x..." and raw hex formats
        val ivMatch = Regex("IV=(0[xX][0-9a-fA-F]+|[0-9a-fA-F]{32})").find(tagLine)
        val explicitIv: ByteArray? = if (ivMatch != null) {
            val parsed = parseHexIv(ivMatch.groupValues[1])
            if (parsed != null) {
                logI("EXT-X-KEY: explicit IV=0x${parsed.joinToString("") { "%02x".format(it) }}")
                parsed
            } else {
                logE("EXT-X-KEY: IV present but failed to parse!")
                null // Will use per-segment IV
            }
        } else {
            logI("EXT-X-KEY: no explicit IV — will use per-segment (mediaSeq + segIndex)")
            null
        }

        val hexKey = keyBytes.joinToString(" ") { "%02x".format(it) }
        logI("EXT-X-KEY: READY key=[$hexKey] explicitIv=${explicitIv != null} mediaSeq=$mediaSequence")

        return EncryptionInfo(
            method = "AES-128",
            key = keyBytes,
            explicitIv = explicitIv,
            mediaSequence = mediaSequence,
        )
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
