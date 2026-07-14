package eu.kanade.tachiyomi.animeextension.en.miruro

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.zip.GZIPInputStream

/**
 * MiruroExtractor — handles the pipe API response decryption, proxy URL
 * building, and stream → Video conversion.
 *
 * The pipe API obfuscates responses: when `x-obfuscated: 2` header is set,
 * the body is base64url → XOR(PIPE_KEY) → gzip → JSON. Otherwise it's plain JSON.
 *
 * Video stream URLs are wrapped through Miruro's proxy servers
 * (vault01/02.ultracloud.cc) with the URL + Referer XOR-obfuscated using
 * PROXY_KEY. Selection between vault01/vault02 is deterministic via FNV-1a
 * hash of "$episodeId|$anilistId" mod 2.
 *
 * Verified against the yuzono reference (MiruroExtractor.kt). We implement
 * inline (no lib deps) per our project convention.
 *
 * @param client the inherited OkHttp client (has CloudflareInterceptor + cookieJar)
 * @param headers the default headers (Referer = baseUrl)
 * @param mirrorBaseUrl the current mirror (https://www.miruro.tv)
 * @param providerDisplayName function resolving provider alias → display name
 */
class MiruroExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val mirrorBaseUrl: String,
    private val providerDisplayName: (String) -> String,
) {

    companion object {
        private const val TAG = "MiruroExtractor"

        // ★ Crypto keys — hardcoded in the miruro.tv frontend JS (public, obfuscation not security).
        // Verified from yuzono Miruro.kt companion line 721-722.
        val PIPE_KEY: ByteArray = hexToBytes("71951034f8fbcf53d89db52ceb3dc22c")
        val PROXY_KEY: ByteArray = hexToBytes("a54d389c18527d9fd3e7f0643e27edbe")

        // Miruro's frontend proxy servers (from VITE_PROXY_A / VITE_PROXY_B in env2.js).
        private const val PROXY_A = "https://vault01.ultracloud.cc/"
        private const val PROXY_B = "https://vault02.ultracloud.cc/"

        // FNV-1a 32-bit hash constants (IETF RFC 7020).
        private const val FNV_OFFSET_BASIS: Int = 2166136261.toInt()
        private const val FNV_PRIME: Int = 16777619

        // Referer that StreamDto defaults to. The proxy uses this for kwik-served HLS,
        // but Miruro's own vault-*.owocdn.top CDN expects a Miruro referer.
        internal const val KWIK_DEFAULT_REFERER = "https://kwik.cx/"

        // Host patterns for embed pre-routing.
        private val MEGACLOUD_HOSTS = listOf("megacloud.tv", "megacloud.club")
        private val RAPID_CLOUD_HOSTS = listOf("rapid-cloud.co", "scloud")

        private fun hexToBytes(hex: String): ByteArray {
            val out = ByteArray(hex.length / 2)
            for (i in out.indices) {
                out[i] = (hex.substring(i * 2, i * 2 + 2).toInt(16) and 0xFF).toByte()
            }
            return out
        }

        /**
         * Encode a ByteArray to base64url without padding (matches frontend btoa + replace).
         */
        private fun base64UrlNoPad(data: ByteArray): String =
            Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

        /**
         * XOR-obfuscate a UTF-8 string with [key] bytes (cycled) and base64url-encode.
         * Mirrors the frontend's ix() function.
         */
        private fun xorEncode(input: String, key: ByteArray): String {
            val bytes = input.toByteArray(Charsets.UTF_8)
            val out = ByteArray(bytes.size)
            for (i in bytes.indices) {
                out[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            return base64UrlNoPad(out)
        }

        /**
         * FNV-1a 32-bit hash of a string, mod 2 — selects between PROXY_A (even) and PROXY_B (odd).
         * Mirrors the frontend's Xb() function.
         */
        private fun fnv1aMod2(seed: String): Int {
            if (seed.isEmpty()) return 0
            var hash = FNV_OFFSET_BASIS
            for (b in seed.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (b.toInt() and 0xFF)
                hash *= FNV_PRIME
            }
            return hash and 1
        }

        /**
         * Build a Miruro proxy URL wrapping [streamUrl] + [referer] through vault01/02.
         * Format: `{proxyBase}{xorEncode(streamUrl)}~{xorEncode(referer)}/pl.m3u8`
         *
         * If [proxyKey] is empty, returns the original streamUrl unchanged.
         */
        fun buildProxiedUrl(
            streamUrl: String,
            referer: String,
            proxyKey: ByteArray,
            proxySeed: String,
        ): String {
            if (proxyKey.isEmpty()) return streamUrl
            val proxyBase = if (fnv1aMod2(proxySeed) == 0) PROXY_A else PROXY_B
            val obfUrl = xorEncode(streamUrl, proxyKey)
            val obfReferer = xorEncode(referer, proxyKey)
            return "${proxyBase}$obfUrl~$obfReferer/pl.m3u8"
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Decrypt a pipe API response.
     *
     * - If `x-obfuscated` header != "2": plain JSON, return body as-is.
     * - If "2": base64url decode → XOR with PIPE_KEY (cycled) → gunzip → UTF-8 string.
     */
    fun decryptResponse(response: Response): String {
        val obfuscated = response.header("x-obfuscated") ?: "1"
        val bodyStr = response.use { it.body?.string()?.trim() ?: "" }

        if (obfuscated != "2") {
            return bodyStr
        }

        if (bodyStr.isEmpty()) {
            MiruroLog.w("decryptResponse: empty body with x-obfuscated=2")
            return ""
        }

        return try {
            val decoded = Base64.decode(bodyStr, Base64.URL_SAFE)
            for (i in decoded.indices) {
                decoded[i] = (decoded[i].toInt() xor PIPE_KEY[i % PIPE_KEY.size].toInt()).toByte()
            }
            GZIPInputStream(java.io.ByteArrayInputStream(decoded)).use { gzip ->
                gzip.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            MiruroLog.e("decryptResponse: failed to decrypt (${e.message})")
            ""
        }
    }

    /**
     * Parse a sources pipe response into a list of [Video]s.
     *
     * For each stream:
     * - "hls" → wrap through the Miruro proxy (vault01/02), set Referer/Origin to mirror.
     * - "embed" → attempt inline extraction (MegaCloud / RapidCloud patterns, else pass
     *   the embed URL through for the app's WebView). We do NOT implement full embed
     *   extraction inline (that requires the lib extractors yuzono uses); instead we
     *   return the embed URL as a Video with a note, so the user can see it in the list
     *   and on-device WebView resolution can be added later if needed.
     *
     * @param response the pipe `sources` response
     * @param subType the sub-type key (sub/dub/ssub/h-sub) for labeling
     * @param providerKey the provider alias (kiwi/bee/...) for labeling
     * @param episodeId the episode ID (for the proxy seed)
     * @param anilistId the AniList ID (for the proxy seed)
     */
    fun parseStreamsFromResponse(
        response: Response,
        subType: String?,
        providerKey: String = "",
        episodeId: String = "",
        anilistId: String = "",
    ): List<Video> {
        val jsonStr = try {
            decryptResponse(response)
        } catch (e: Exception) {
            MiruroLog.e("parseStreamsFromResponse: decrypt failed: ${e.message}")
            return emptyList()
        }

        val sourcesDto = try {
            SourcesResponseDto.parse(jsonStr)
        } catch (e: Exception) {
            MiruroLog.e("parseStreamsFromResponse: parse failed: ${e.message}")
            return emptyList()
        }

        if (sourcesDto.streams.isEmpty()) {
            MiruroLog.w("parseStreams: empty streams (subType=$subType, provider=$providerKey)")
            return emptyList()
        }

        MiruroLog.d("parseStreams: ${sourcesDto.streams.size} streams, ${sourcesDto.subtitles.size} subs (subType=$subType, provider=$providerKey)")

        val subTypeLabel = when (subType) {
            "sub" -> "Sub"
            "dub" -> "Dub"
            "ssub" -> "Soft Sub"
            "h-sub" -> "Hard Sub"
            null -> null
            else -> subType.replaceFirstChar { it.uppercase() }
        }

        val subtitles = sourcesDto.subtitles
            .filter { it.url.isNotEmpty() }
            .map { Track(it.url, it.label.ifEmpty { it.language }) }

        val videos = mutableListOf<Video>()
        val proxySeed = "$episodeId|$anilistId"

        for (stream in sourcesDto.streams) {
            if (stream.url.isEmpty()) continue

            val qualityInt = stream.quality.toIntOrNull() ?: 0
            val width = stream.resolution?.width ?: 0
            val height = stream.resolution?.height ?: 0
            val streamTypeLabel = stream.type.uppercase()

            val qualityLabel = buildString {
                if (providerKey.isNotEmpty()) append("${providerDisplayName(providerKey)} - ")
                if (qualityInt > 0) append("${qualityInt}p") else append("default")
                if (subTypeLabel != null) append(" $subTypeLabel")
                if (width > 0 && height > 0) append(" - ${width}x$height")
                if (stream.codec.isNotEmpty()) append(" ${stream.codec}")
                if (stream.audio.isNotEmpty()) append(" ${stream.audio}")
                if (stream.fansub.isNotEmpty()) append(" ${stream.fansub}")
                append(" $streamTypeLabel")
            }.trim()

            when (stream.type.lowercase()) {
                "hls" -> {
                    val proxyReferer = stream.referer.trim().ifEmpty { KWIK_DEFAULT_REFERER }
                    val proxiedUrl = buildProxiedUrl(
                        streamUrl = stream.url,
                        referer = proxyReferer,
                        proxyKey = PROXY_KEY,
                        proxySeed = proxySeed,
                    )
                    val proxyHeaders = headers.newBuilder()
                        .set("Referer", "${mirrorBaseUrl.trimEnd('/')}/")
                        .set("Origin", mirrorBaseUrl.trimEnd('/'))
                        .build()
                    videos.add(
                        Video(
                            videoUrl = proxiedUrl,
                            videoTitle = qualityLabel,
                            headers = proxyHeaders,
                            subtitleTracks = subtitles,
                        ),
                    )
                }
                "embed" -> {
                    // ★ Embed extraction: MegaCloud / RapidCloud require dedicated extractors
                    // (yuzono uses lib:megacloudextractor / lib:rapidcloudextractor). We don't
                    // have those libs in our ext-lib v16 stubs project, so for v16.1 we pass
                    // the embed URL through as a Video with a clear label. The app's player
                    // can attempt to load it; if it fails, the user falls back to an HLS stream.
                    // TODO (future): implement MegaCloud/RapidCloud extraction inline (like
                    // AnimePahe's KwikExtractor) once we capture live embed responses on-device.
                    val embedHost = runCatching { stream.url.toHttpUrlOrNull()?.host }.getOrNull() ?: "embed"
                    val embedLabel = "$qualityLabel [embed:$embedHost]"
                    val embedHeaders = headers.newBuilder()
                        .set("Referer", "${mirrorBaseUrl.trimEnd('/')}/")
                        .build()
                    videos.add(
                        Video(
                            videoUrl = stream.url,
                            videoTitle = embedLabel,
                            headers = embedHeaders,
                            subtitleTracks = subtitles,
                        ),
                    )
                }
                else -> {
                    MiruroLog.w("parseStreams: unknown type '${stream.type}', skipping: ${MiruroLog.trunc(stream.url)}")
                }
            }
        }

        MiruroLog.d("parseStreams: built ${videos.size} videos from ${sourcesDto.streams.size} streams")
        return videos
    }
}
