package eu.kanade.tachiyomi.animeextension.en.mkissa.extractor

import android.util.Base64
import eu.kanade.tachiyomi.animeextension.en.mkissa.MKissaLog
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * MKissa video extractor — handles the full pipeline:
 * 1. Fetch the episode stream data from api.allanime.day (GraphQL with persisted query)
 * 2. Decrypt the encrypted `tobeparsed` response (AES-GCM)
 * 3. Decrypt each source URL (XOR with prefix-based key)
 * 4. Dispatch to per-server extractors (Filemoon, Mp4Upload, Okru, internal, Uni)
 *
 * The 5 servers discovered on mkissa.to (verified session 05):
 * - **Fm-Hls** (Filemoon HLS) — `bysekoze.com/e/...` → FilemoonExtractor
 * - **Uni** (custom player) — `allanime.uns.bio/#...` → direct iframe (may need WebView)
 * - **Mp4** (Mp4Upload) — `mp4upload.com/embed-...` → Mp4uploadExtractor (JsUnpacker)
 * - **Ok** (OK.ru) — `ok.ru/videoembed/...` → OkruExtractor (data-options JSON)
 * - **Luf-Mp4** (internal allanime) — `/apivtwo/clock?id=...` → AllAnimeExtractor (clock.json)
 *
 * Ported from the allanime reference extension + the keiyoushi lib/ extractors, adapted for
 * ext-lib 16 (named-arg Video constructor).
 *
 * @param client The inherited OkHttpClient (has CloudflareInterceptor for on-device CF bypass)
 * @param headers The source's default headers (includes Referer: mkissa.to/)
 * @param json Json parser instance
 */
class MKissaExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val json: Json,
    private val webViewFetcher: WebViewFetcher? = null,
) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    // ── DTOs for the stream API response ──────────────────────────────

    @Serializable
    private data class EncryptedResponse(
        val data: EncryptedData = EncryptedData(),
    ) {
        @Serializable
        data class EncryptedData(
            val tobeparsed: String? = null,
        )
    }

    @Serializable
    private data class DecryptedEpisode(
        val episode: Episode? = null,
    ) {
        @Serializable
        data class Episode(
            val sourceUrls: List<SourceUrl> = emptyList(),
        ) {
            @Serializable
            data class SourceUrl(
                val sourceUrl: String = "",
                val type: String = "",
                val sourceName: String = "",
                val priority: Float = 0f,
            )
        }
    }

    @Serializable
    private data class ClockResponse(
        val links: List<ClockLink> = emptyList(),
    ) {
        @Serializable
        data class ClockLink(
            val link: String = "",
            val hls: Boolean? = null,
            val mp4: Boolean? = null,
            val resolutionStr: String = "",
            val subtitles: List<Subtitle>? = null,
        ) {
            @Serializable
            data class Subtitle(
                val src: String = "",
                val lang: String = "",
                val label: String? = null,
            )
        }
    }

    // ── Decryption constants (from the allanime reference) ────────────

    private val decryptSecret = "Xot36i3lK3"
    private val xorKeys = arrayOf("allanimenews", "1234567890123456789", "1234567890123456789012345", "s5feqxw21", "feqx1")
    private val xorMasks = xorKeys.map { key -> key.fold(0) { mask, ch -> mask xor ch.code } }.toIntArray()

    // ── Internal hoster names (from the allanime reference) ───────────

    private val internalHosterNames = arrayOf(
        "Default", "Ac", "Ak", "Kir", "Rab", "Luf-Mp4",
        "Si-Hls", "S-Mp4", "Ac-Hls", "Uv-Mp4", "Pn-Hls",
    )

    private val fallbackIframeEndpoint = "https://blog.allanime.day"

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Fetch + extract all videos for an episode.
     *
     * ★ The stream API returns NEED_CAPTCHA if the client hasn't solved the Cloudflare Turnstile
     * on the mkissa.to watch page. So we first load the watch page URL via the inherited client
     * (which has the CloudflareInterceptor — it solves the Turnstile on-device via WebView and
     * stores the cf_clearance cookie). Then the stream API call succeeds.
     *
     * @param showId The anime's _id (from the URL: /anime/<showId>)
     * @param translationType "sub" or "dub"
     * @param episodeString The raw episode string (e.g. "1", "5", "12")
     * @param enabledServers Set of server names to include (lowercase). Empty = all.
     * @param preferredServer The preferred server name (lowercase) — picked first in sorting.
     * @param baseUrl The site base URL (for loading the watch page).
     * @return List<Video> — all playable videos from all enabled servers
     */
    suspend fun extractVideos(
        showId: String,
        translationType: String,
        episodeString: String,
        enabledServers: Set<String> = emptySet(),
        preferredServer: String = "",
        baseUrl: String = "https://mkissa.to",
    ): List<Video> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch the encrypted stream data
            var responseBody = fetchStreamData(showId, translationType, episodeString)

            // ★ If NEED_CAPTCHA, load the watch page first to trigger the CloudflareInterceptor
            // (solves the Turnstile on-device via WebView), then retry the stream API.
            if (responseBody.contains("NEED_CAPTCHA")) {
                MKissaLog.i("MKissaExtractor: NEED_CAPTCHA detected — loading watch page to solve Cloudflare Turnstile")
                val watchPageUrl = "$baseUrl/anime/$showId/p-$episodeString-$translationType"
                solveCloudflare(watchPageUrl)

                // Retry the stream API after the captcha is solved
                MKissaLog.i("MKissaExtractor: retrying stream API after Cloudflare bypass")
                responseBody = fetchStreamData(showId, translationType, episodeString)
            }

            if (responseBody.isBlank()) {
                MKissaLog.w("MKissaExtractor: empty stream response")
                return@withContext emptyList()
            }

            // 2. Decrypt the tobeparsed payload (AES-GCM)
            val tobeparsed = try {
                json.decodeFromString<EncryptedResponse>(responseBody).data.tobeparsed
            } catch (e: Exception) {
                MKissaLog.e("fetchStreamData: JSON parse failed — ${e.message}")
                MKissaLog.e("fetchStreamData: raw body (first 500): ${responseBody.take(500)}")
                null
            }
            if (tobeparsed.isNullOrBlank()) {
                MKissaLog.w("MKissaExtractor: no tobeparsed in response (body length=${responseBody.length})")
                MKissaLog.w("MKissaExtractor: body preview: ${responseBody.take(300)}")
                return@withContext emptyList()
            }

            val decryptedJson = decryptTobeparsed(tobeparsed)
            if (decryptedJson.isBlank()) {
                MKissaLog.w("MKissaExtractor: decryption failed")
                return@withContext emptyList()
            }

            val episode = json.decodeFromString<DecryptedEpisode>(decryptedJson).episode
            if (episode == null || episode.sourceUrls.isEmpty()) {
                MKissaLog.w("MKissaExtractor: no sourceUrls in decrypted episode")
                return@withContext emptyList()
            }

            MKissaLog.i("MKissaExtractor: found ${episode.sourceUrls.size} sources")

            // 3. Decrypt each source URL + dispatch to the appropriate extractor
            val allVideos = mutableListOf<Video>()
            for (source in episode.sourceUrls) {
                val serverName = source.sourceName
                val serverKey = serverName.lowercase()

                try {
                    val decryptedUrl = decryptSourceUrl(source.sourceUrl)
                    MKissaLog.d("MKissaExtractor: $serverName → ${MKissaLog.trunc(decryptedUrl, 80)}")

                    // ★ Server filtering: skip EXTERNAL hosters if not in the enabled set.
                    // Internal hosters (DECRYPTED URL starts with /apivtwo/) are ALWAYS tried.
                    val isInternal = decryptedUrl.startsWith("/apivtwo/")
                    if (!isInternal && enabledServers.isNotEmpty() && serverKey !in enabledServers) {
                        MKissaLog.d("MKissaExtractor: skipping $serverName (not in enabled set)")
                        continue
                    }

                    val videos = when {
                        // ★ Internal allanime hoster — ANY source whose decrypted URL starts with /apivtwo/
                        // is an internal hoster (Luf-Mp4, Ac, Ak, Kir, etc.).
                        decryptedUrl.startsWith("/apivtwo/") -> extractInternal(decryptedUrl, serverName)

                        // Vn-Hls — vidnest.io embed (POST /dl → direct MP4 URLs)
                        serverKey == "vn-hls" || decryptedUrl.contains("vidnest.io") ->
                            extractVidnest(decryptedUrl, serverName)

                        // Filemoon (Fm-Hls) — bysekoze.com, filemoon, moonplayer
                        serverKey == "fm-hls" || decryptedUrl.contains("filemoon") || decryptedUrl.contains("bysekoze") ->
                            extractFilemoon(decryptedUrl, serverName)

                        // Mp4Upload (Mp4) — mp4upload.com
                        serverKey == "mp4" || decryptedUrl.contains("mp4upload.com") ->
                            extractMp4Upload(decryptedUrl, serverName)

                        // OK.ru (Ok) — ok.ru, okru
                        serverKey == "ok" || decryptedUrl.contains("ok.ru") || decryptedUrl.contains("okru") ->
                            extractOkru(decryptedUrl, serverName)

                        // Uni — allanime.uns.bio (custom player, uses AES-CBC encrypted API)
                        serverKey == "uni" || decryptedUrl.contains("uns.bio") ->
                            extractUni(decryptedUrl, serverName)

                        else -> {
                            MKissaLog.w("MKissaExtractor: unknown server '$serverName' (url=${MKissaLog.trunc(decryptedUrl, 60)}), skipping")
                            emptyList()
                        }
                    }
                    MKissaLog.i("MKissaExtractor: $serverName → ${videos.size} videos")
                    allVideos.addAll(videos)
                } catch (e: Exception) {
                    MKissaLog.e("MKissaExtractor: failed to extract from $serverName — ${e.message}", e)
                }
            }

            // ★ Sort: preferred server first, then by quality, then by audio type
            val sortedVideos = if (preferredServer.isNotBlank()) {
                allVideos.sortedWith(
                    compareByDescending<Video> { it.videoTitle.contains(preferredServer, true) }
                        .thenByDescending { it.videoTitle.contains("1080", true) }
                        .thenByDescending { it.videoTitle.contains("720", true) }
                )
            } else {
                allVideos.sortedByDescending { it.videoTitle.contains("1080", true) }
            }

            MKissaLog.i("MKissaExtractor: total ${sortedVideos.size} videos from all servers (preferred=$preferredServer)")
            sortedVideos
        } catch (e: Exception) {
            MKissaLog.e("MKissaExtractor: FAILED — ${e.message}", e)
            emptyList()
        }
    }

    // ── Cloudflare bypass (load watch page to trigger the interceptor) ──

    /**
     * Load the watch page URL to solve the Cloudflare Turnstile.
     *
     * ★ Two-stage approach:
     * 1. First try OkHttp (the inherited client with CloudflareInterceptor). If it succeeds
     *    (HTTP 200 or the interceptor solves the challenge), the cf_clearance cookie is set.
     * 2. If OkHttp fails ("Failed to bypass Cloudflare"), fall back to WebViewFetcher.
     *    The WebView loads the watch page using Chrome's full browser stack — it can solve
     *    Turnstile challenges that the interceptor can't. The cf_clearance cookie from the
     *    WebView is stored in CookieManager (shared with OkHttp via AndroidCookieJar).
     *
     * After this, the stream API retry should succeed (the cf_clearance cookie is sent).
     */
    private fun solveCloudflare(watchPageUrl: String) {
        // Stage 1: Try OkHttp (with CloudflareInterceptor)
        try {
            MKissaLog.i("solveCloudflare: loading watch page via OkHttp: $watchPageUrl")
            val req = GET(watchPageUrl, headers, CacheControl.FORCE_NETWORK)
            val response = client.newCall(req).execute()
            MKissaLog.i("solveCloudflare: OkHttp watch page HTTP ${response.code}")
            response.close()
        } catch (e: Exception) {
            MKissaLog.w("solveCloudflare: OkHttp failed — ${e.message}")
        }

        // Stage 2: If WebViewFetcher is available, use the dedicated Turnstile solver.
        // This loads the page, detects the challenge, auto-clicks the Turnstile checkbox,
        // and waits for the cf_clearance cookie to be set.
        if (webViewFetcher != null) {
            try {
                MKissaLog.i("solveCloudflare: solving Turnstile via WebView (auto-click): $watchPageUrl")
                val solved = webViewFetcher.solveCloudflareTurnstile(watchPageUrl, timeoutMs = 45_000)
                if (solved) {
                    MKissaLog.i("solveCloudflare: Turnstile solved — cf_clearance should be set")
                } else {
                    MKissaLog.w("solveCloudflare: Turnstile not solved — may need manual interaction")
                }
            } catch (e: Exception) {
                MKissaLog.w("solveCloudflare: WebView Turnstile solver failed — ${e.message}")
            }
        } else {
            MKissaLog.w("solveCloudflare: WebViewFetcher not available — cannot use Chrome Turnstile solver")
        }
    }

    // ── Stream API call ────────────────────────────────────────────────

    private suspend fun fetchStreamData(showId: String, translationType: String, episodeString: String): String {
        val variables = """{"showId":"$showId","translationType":"$translationType","episodeString":"$episodeString"}"""
        val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$STREAM_HASH"}}"""
        val encodedVars = java.net.URLEncoder.encode(variables, "UTF-8")
        val encodedExts = java.net.URLEncoder.encode(extensions, "UTF-8")
        val url = "https://api.allanime.day/api?variables=$encodedVars&extensions=$encodedExts"
        MKissaLog.i("fetchStreamData: requesting $url")

        // ★ Use FORCE_NETWORK to bypass OkHttp's 10-min default cache (ext-lib GET defaults to maxAge=10min).
        // A stale cached response (e.g. from a previous failed request) could cause "no tobeparsed".
        val req = GET(url, headers, CacheControl.FORCE_NETWORK)
        // Don't use awaitSuccess() — use execute() so we can log the response even on error.
        val response = client.newCall(req).execute()
        val code = response.code
        val body = response.use { it.body?.string().orEmpty() }
        MKissaLog.i("fetchStreamData: HTTP $code, body length=${body.length}")
        MKissaLog.d("fetchStreamData: body first 300 chars: ${body.take(300)}")
        if (code !in 200..299) {
            MKissaLog.w("fetchStreamData: non-2xx response ($code) — body: ${body.take(200)}")
        }
        return body
    }

    // ── AES-GCM decryption (tobeparsed) ────────────────────────────────

    private fun decryptTobeparsed(base64Payload: String): String {
        val blob = Base64.decode(base64Payload, Base64.DEFAULT)
        if (blob.size < 13) return ""
        val versionByte = blob[0].toInt() and 0xFF
        val iv = blob.sliceArray(1 until 13)
        val encryptedData = blob.sliceArray(13 until blob.size)

        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest("$decryptSecret:v$versionByte".toByteArray(Charsets.UTF_8))

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encryptedData), Charsets.UTF_8)
    }

    // ── XOR decryption (source URLs) ───────────────────────────────────

    private fun decryptSourceUrl(url: String): String {
        val (hexPayload, keyType) = when {
            url.startsWith("--") -> url.substring(2) to 3
            url.startsWith("#-") -> url.substring(2) to 2
            url.startsWith("##") -> url.substring(2) to 1
            url.startsWith("-#") -> url.substring(2) to 4
            url.startsWith("#") -> url.substring(1) to 0
            else -> url to null
        }

        val parsedChunks = try {
            hexPayload.chunked(2).map { it.toInt(16) }
        } catch (_: NumberFormatException) {
            return url
        }

        if (keyType == null) {
            // Try all XOR masks — return the one that looks like a URL
            for (mask in xorMasks) {
                val decrypted = String(CharArray(parsedChunks.size) { i -> ((parsedChunks[i] xor mask) and 0xFF).toChar() })
                if (decrypted.contains("/clock") || decrypted.contains("http")) return decrypted
            }
            return url
        }

        val mask = xorMasks[keyType]
        return String(CharArray(parsedChunks.size) { i -> ((parsedChunks[i] xor mask) and 0xFF).toChar() })
    }

    // ── Per-server extractors ──────────────────────────────────────────

    /** Vn-Hls (vidnest.io) extractor — POST /dl → direct MP4 URLs.
     *
     * The vidnest.io embed page has a form that POSTs to /dl with the file code.
     * The response HTML contains direct MP4 URLs at different qualities.
     */
    private suspend fun extractVidnest(url: String, name: String): List<Video> {
        return try {
            val httpUrl = url.toHttpUrl()
            val host = httpUrl.host
            val code = httpUrl.pathSegments.lastOrNull { it.isNotEmpty() } ?: return emptyList()
            MKissaLog.d("extractVidnest: host=$host, code=$code")

            // POST to /dl to get the download page with video URLs
            val postBody = "op=embed&file_code=$code&auto=1&referer="
            val vidnestHeaders = headers.newBuilder()
                .set("Referer", url)
                .set("Content-Type", "application/x-www-form-urlencoded")
                .build()
            val postUrl = "https://$host/dl"
            MKissaLog.d("extractVidnest: POSTing to $postUrl")
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url(postUrl)
                    .headers(vidnestHeaders)
                    .post(okhttp3.RequestBody.create(null, postBody))
                    .build(),
            ).execute()
            val body = response.use { it.body?.string().orEmpty() }
            MKissaLog.d("extractVidnest: response HTTP ${response.code}, body length=${body.length}")

            if (body.isBlank()) {
                MKissaLog.w("extractVidnest: empty response")
                return emptyList()
            }

            // Search for MP4 URLs in the response HTML
            val videoUrls = Regex("""https?://[^"'\s<>]+\.mp4[^"'\s<>]*""").findAll(body).map { it.value }.toList()
            if (videoUrls.isEmpty()) {
                MKissaLog.w("extractVidnest: no MP4 URLs found in response")
                return emptyList()
            }

            MKissaLog.i("extractVidnest: found ${videoUrls.size} video URLs")
            val vnHeaders = headers.newBuilder().set("Referer", "https://$host/").build()
            videoUrls.map { videoUrl ->
                // Try to extract quality from the URL or surrounding text
                val quality = if (videoUrl.contains("_x/")) "1080p" else if (videoUrl.contains("_o/")) "720p" else "Unknown"
                Video(
                    videoUrl = videoUrl,
                    videoTitle = "$name - $quality",
                    headers = vnHeaders,
                )
            }
        } catch (e: Exception) {
            MKissaLog.e("extractVidnest: failed for $name — ${e.message}", e)
            emptyList()
        }
    }

    /** Internal allanime hoster (Luf-Mp4, etc.) — fetches clock.json from the iframe endpoint.
     *
     * ★ The clock.json endpoint is on `blog.allanime.day` but requires the correct Referer header
     * (`https://allmanga.to/` — the allanime reference's default site URL). With `Referer: mkissa.to/`
     * it returns 500. Also tries WebView fallback if all OkHttp attempts fail.
     */
    private suspend fun extractInternal(url: String, name: String): List<Video> {
        return try {
            val clockPath = url.replace("/clock?", "/clock.json?")

            // ★ The allanime reference uses `Referer: https://allmanga.to/` for the clock.json call.
            // With `Referer: mkissa.to/`, blog.allanime.day returns 500.
            val allanimeHeaders = headers.newBuilder()
                .set("Referer", "https://allmanga.to/")
                .build()

            val endpoints = listOf(
                "https://blog.allanime.day",
                "https://allmanga.to",
            )

            var body = ""
            var successEndpoint = ""
            for (endpoint in endpoints) {
                val clockUrl = endpoint + clockPath
                MKissaLog.d("extractInternal: trying $clockUrl (Referer: allmanga.to)")
                try {
                    val response = client.newCall(GET(clockUrl, allanimeHeaders, CacheControl.FORCE_NETWORK)).execute()
                    val code = response.code
                    body = response.use { it.body?.string().orEmpty() }
                    MKissaLog.d("extractInternal: $endpoint → HTTP $code, body length=${body.length}")
                    if (code in 200..299 && body.isNotBlank() && body != "error") {
                        // Try to JSON-parse — if it fails, it's not the clock.json data
                        try {
                            json.decodeFromString<ClockResponse>(body)
                            successEndpoint = endpoint
                            break
                        } catch (e: Exception) {
                            MKissaLog.d("extractInternal: $endpoint returned 200 but not valid JSON — probably HTML page")
                            body = ""
                        }
                    }
                    body = ""
                } catch (e: Exception) {
                    MKissaLog.d("extractInternal: $endpoint failed — ${e.message}")
                }
            }

            // ★ If OkHttp failed, try WebView's fetchText (Chrome's TLS + cookies)
            if (body.isBlank() && webViewFetcher != null) {
                MKissaLog.i("extractInternal: OkHttp failed — trying WebView for clock.json")
                for (endpoint in endpoints) {
                    val clockUrl = endpoint + clockPath
                    try {
                        MKissaLog.d("extractInternal: trying WebView: $clockUrl")
                        val webBody = webViewFetcher.fetchText(clockUrl, timeoutMs = 15_000)
                        if (webBody.isNotBlank() && webBody != "error") {
                            try {
                                json.decodeFromString<ClockResponse>(webBody)
                                body = webBody
                                successEndpoint = endpoint
                                MKissaLog.i("extractInternal: WebView success from $endpoint")
                                break
                            } catch (e: Exception) {
                                MKissaLog.d("extractInternal: WebView returned non-JSON from $endpoint")
                            }
                        }
                    } catch (e: Exception) {
                        MKissaLog.d("extractInternal: WebView failed from $endpoint — ${e.message}")
                    }
                }
            }

            if (body.isBlank()) {
                MKissaLog.w("extractInternal: all attempts failed for $name")
                return emptyList()
            }
            MKissaLog.i("extractInternal: success from $successEndpoint, body length=${body.length}")

            val clockData = json.decodeFromString<ClockResponse>(body)
            MKissaLog.d("extractInternal: parsed ${clockData.links.size} links")

            clockData.links.flatMap { link ->
                val subtitles = link.subtitles?.map { sub ->
                    val label = sub.label?.let { " - $it" } ?: ""
                    Track(sub.src, Locale(sub.lang).displayLanguage + label)
                } ?: emptyList()

                when {
                    link.mp4 == true -> listOf(
                        Video(
                            videoUrl = link.link,
                            videoTitle = "$name - ${link.resolutionStr}",
                            headers = headers,
                            subtitleTracks = subtitles,
                        ),
                    )
                    link.hls == true -> playlistUtils.extractFromHls(
                        link.link,
                        referer = "$successEndpoint/",
                        videoNameGen = { quality -> "$quality ($name - ${link.resolutionStr})" },
                        subtitleList = subtitles,
                    )
                    else -> emptyList()
                }
            }
        } catch (e: Exception) {
            MKissaLog.e("extractInternal: failed for $name — ${e.message}", e)
            emptyList()
        }
    }

    /** Filemoon extractor (Fm-Hls) — API + WebView same-origin fallback.
     *
     * Strategy:
     * 1. GET embed details → find embed_frame_url (works with OkHttp)
     * 2. Try OkHttp for the playback API (works if CF clearance is available)
     * 3. If OkHttp returns 405, load the embed frame page in WebView (sets same-origin),
     *    then use fetchSameOrigin to call the playback API (no CORS — same origin!)
     * 4. Parse sources → extract HLS via PlaylistUtils
     */
    private suspend fun extractFilemoon(url: String, name: String): List<Video> {
        return try {
            val httpUrl = url.toHttpUrl()
            val host = httpUrl.host
            val mediaId = if (httpUrl.pathSegments.size > 1 && httpUrl.pathSegments[0] == "e") {
                httpUrl.pathSegments[1]
            } else {
                httpUrl.pathSegments.lastOrNull { it.isNotEmpty() } ?: return emptyList()
            }
            MKissaLog.d("extractFilemoon: host=$host, mediaId=$mediaId")

            // Step 1: get embed details → find the embed frame URL
            val detailsResponse = client.newCall(GET("https://$host/api/videos/$mediaId/embed/details", headers)).execute()
            val detailsCode = detailsResponse.code
            val detailsBody = detailsResponse.use { it.body?.string().orEmpty() }
            MKissaLog.d("extractFilemoon: details API HTTP $detailsCode, body length=${detailsBody.length}")
            if (detailsCode !in 200..299) {
                MKissaLog.w("extractFilemoon: details API returned HTTP $detailsCode")
                return emptyList()
            }

            val embedUrl = detailsBody.substringAfter("embed_frame_url", "")
                .substringAfter(":").substringAfter('"').substringBefore('"')
            if (embedUrl.isBlank()) {
                MKissaLog.w("extractFilemoon: no embed_frame_url found")
                return emptyList()
            }
            val embedHost = embedUrl.toHttpUrl().host
            MKissaLog.d("extractFilemoon: embed frame URL: $embedUrl (host=$embedHost)")

            // Step 2: Try OkHttp for the playback API
            val apiUrls = listOf(
                "https://$embedHost/api/videos/$mediaId/embed/playback",
                "https://$host/api/videos/$mediaId/embed/playback",
            )

            var playbackBody = ""
            for (apiUrl in apiUrls) {
                MKissaLog.d("extractFilemoon: trying OkHttp playback API: $apiUrl")
                try {
                    val playbackHeaders = headers.newBuilder()
                        .set("Referer", embedUrl)
                        .set("X-Embed-Origin", host)
                        .set("X-Embed-Parent", url)
                        .set("X-Embed-Referer", url)
                        .set("Accept", "*/*")
                        .build()
                    val response = client.newCall(GET(apiUrl, playbackHeaders)).execute()
                    val code = response.code
                    val body = response.use { it.body?.string().orEmpty() }
                    MKissaLog.d("extractFilemoon: OkHttp playback API HTTP $code, body length=${body.length}")
                    if (code in 200..299 && body.isNotBlank() && !body.contains("\"error\"")) {
                        playbackBody = body
                        break
                    }
                } catch (e: Exception) {
                    MKissaLog.d("extractFilemoon: OkHttp playback API exception: ${e.message}")
                }
            }

            // ★ Step 3: If OkHttp failed, use loadAndExtractVideo to load the embed frame page,
            // auto-click the play buttons, and extract the video URL from the video element.
            if (playbackBody.isBlank() && webViewFetcher != null) {
                MKissaLog.i("extractFilemoon: OkHttp failed — using loadAndExtractVideo (auto-click play buttons)")
                val videoSrc = webViewFetcher.loadAndExtractVideo(
                    url = embedUrl,
                    clickDelayMs = 4000, // Fm-Hls has a "Loading your video" step — wait longer between clicks
                    maxClicks = 3,
                    pollTimeoutMs = 20_000,
                )
                if (videoSrc.isNotBlank() && videoSrc.startsWith("http")) {
                    MKissaLog.i("extractFilemoon: found video URL via loadAndExtractVideo: ${MKissaLog.trunc(videoSrc, 80)}")
                    val fmHeaders = headers.newBuilder().set("Referer", "https://$embedHost/").build()
                    return if (videoSrc.contains(".m3u8")) {
                        playlistUtils.extractFromHls(
                            videoSrc,
                            referer = "https://$embedHost/",
                            videoNameGen = { q -> "$q ($name)" },
                        )
                    } else {
                        listOf(Video(videoUrl = videoSrc, videoTitle = "$name - MP4", headers = fmHeaders))
                    }
                }
                MKissaLog.w("extractFilemoon: loadAndExtractVideo did not find a video URL")
            }

            if (playbackBody.isBlank()) {
                MKissaLog.w("extractFilemoon: all playback API attempts failed. Skipping $name.")
                return emptyList()
            }

            // Step 4: Parse sources
            val sources = parseFilemoonSources(playbackBody)
            if (sources.isNullOrEmpty()) {
                MKissaLog.w("extractFilemoon: no sources in playback response — body: ${playbackBody.take(200)}")
                return emptyList()
            }

            buildFilemoonVideos(sources, host, name)
        } catch (e: Exception) {
            MKissaLog.e("extractFilemoon: failed for $name — ${e.message}", e)
            emptyList()
        }
    }

    /** Build Video objects from Filemoon sources. */
    private suspend fun buildFilemoonVideos(
        sources: List<FilemoonSource>,
        host: String,
        name: String,
    ): List<Video> {
        val videoHeaders = headers.newBuilder()
            .set("Referer", "https://$host/")
            .build()

        val allVideos = sources.flatMap { source ->
            val streamUrl = source.url ?: source.file ?: return@flatMap emptyList<Video>()
            val quality = source.label ?: "Unknown"
            MKissaLog.d("extractFilemoon: source: quality=$quality, url=${MKissaLog.trunc(streamUrl, 80)}")
            playlistUtils.extractFromHls(
                streamUrl,
                referer = "https://$host/",
                videoNameGen = { q -> "$q ($name - $quality)" },
            )
        }
        MKissaLog.i("extractFilemoon: extracted ${allVideos.size} videos from $name")
        return allVideos
    }

    @Serializable
    private data class FilemoonSource(
        val url: String? = null,
        val file: String? = null,
        val label: String? = null,
    )

    @Serializable
    private data class FilemoonPlaybackResponse(
        val sources: List<FilemoonSource>? = null,
    )

    private fun parseFilemoonSources(body: String): List<FilemoonSource>? {
        return try {
            json.decodeFromString<FilemoonPlaybackResponse>(body).sources
        } catch (_: Exception) {
            // Try to find sources array in the raw JSON
            val sourcesStart = body.indexOf("\"sources\"")
            if (sourcesStart < 0) return null
            val arrayStart = body.indexOf('[', sourcesStart)
            val arrayEnd = body.lastIndexOf(']')
            if (arrayStart < 0 || arrayEnd < 0) return null
            try {
                json.decodeFromString<FilemoonPlaybackResponse>("{${body.substring(sourcesStart, arrayEnd + 1)}}").sources
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Mp4Upload extractor (Mp4) — JsUnpacker approach */
    private suspend fun extractMp4Upload(url: String, name: String): List<Video> {
        return try {
            val refererHeaders = headers.newBuilder()
                .set("Referer", "https://mp4upload.com/")
                .build()
            val response = client.newCall(GET(url, refererHeaders)).awaitSuccess()
            val body = response.use { it.body?.string().orEmpty() }

            // Look for packed JS or player.src
            val scriptData = if (body.contains("eval(function(p,a,c,k,e,d)")) {
                val packedScript = body.substringAfterLast("eval(function(")
                eu.kanade.tachiyomi.animeextension.en.mkissa.extractor.jsunpacker.JsUnpacker
                    .unpackAndCombine("eval(function($packedScript")
            } else {
                // Try direct player.src
                body
            } ?: return emptyList()

            val videoUrl = scriptData.substringAfter(".src(").substringBefore(")")
                .substringAfter("src:").substringAfter('"').substringBefore('"')
            if (videoUrl.isBlank() || !videoUrl.startsWith("http")) return emptyList()

            val resolution = Regex("""\WHEIGHT=(\d+)""").find(scriptData)?.groupValues?.let { "${it[1]}p" } ?: "Unknown"
            listOf(
                Video(
                    videoUrl = videoUrl,
                    videoTitle = "$name - $resolution",
                    headers = refererHeaders,
                ),
            )
        } catch (e: Exception) {
            MKissaLog.e("extractMp4Upload: failed for $name — ${e.message}", e)
            emptyList()
        }
    }

    /** OK.ru extractor (Ok) — data-options JSON approach.
     *
     * The ok.ru embed page has a `data-options` attribute containing HTML-entity-encoded JSON.
     * After decoding `&quot;` → `"`, the JSON has a `flashvars.metadata` field which is a
     * JSON STRING (double-encoded) containing `ondemandHls` (HLS playlist URL) and/or a
     * `videos` array with direct MP4 URLs at different qualities.
     *
     * ★ The escaped quotes (`\"`) inside the metadata string mean URLs contain `\u0026`
     * (Unicode ampersand) which has a BACKSLASH. Regexes using `[^\\]+` stop at the backslash
     * and capture only a partial URL. Must use `(.*?)` (non-greedy) until the closing `\"`.
     */
    private suspend fun extractOkru(url: String, name: String): List<Video> {
        return try {
            val response = client.newCall(GET(url, headers)).execute()
            val body = response.use { it.body?.string().orEmpty() }
            if (body.isBlank()) {
                MKissaLog.w("extractOkru: empty response from $url")
                return emptyList()
            }

            // Extract data-options attribute + decode HTML entities
            val dataOptions = Regex("""data-options="([^"]+)"""").find(body)?.groupValues?.get(1)
                ?.replace("&quot;", "\"")
                ?.replace("&amp;", "&")
                ?: run {
                    MKissaLog.w("extractOkru: no data-options attribute found")
                    return emptyList()
                }

            MKissaLog.d("extractOkru: data-options length=${dataOptions.length}")
            // Log a preview so we can see the structure (first 200 chars)
            MKissaLog.d("extractOkru: data-options preview: ${dataOptions.take(200)}")

            // Check if ondemandHls is present at all
            val hasHls = dataOptions.contains("ondemandHls")
            val hasDash = dataOptions.contains("ondemandDash")
            val hasVideos = dataOptions.contains("\"videos\"") || dataOptions.contains("\\\"videos\\\"")
            MKissaLog.d("extractOkru: hasHls=$hasHls, hasDash=$hasDash, hasVideos=$hasVideos")

            // Try HLS first (best quality — adaptive playlist with multiple resolutions)
            val hlsUrl = extractOkruHlsUrl(dataOptions)
            if (hlsUrl != null) {
                MKissaLog.i("extractOkru: found HLS URL (${hlsUrl.length} chars): ${MKissaLog.trunc(hlsUrl, 100)}")
                val okruHeaders = headers.newBuilder().set("Referer", "https://ok.ru/").build()
                val videos = playlistUtils.extractFromHls(
                    hlsUrl,
                    referer = "https://ok.ru/",
                    videoNameGen = { q -> "$q ($name)" },
                )
                if (videos.isNotEmpty()) {
                    MKissaLog.i("extractOkru: HLS extraction returned ${videos.size} videos")
                    return videos
                }
                MKissaLog.w("extractOkru: HLS extraction returned 0 videos (CDN may be IP-locked or geo-blocked)")
            } else {
                MKissaLog.d("extractOkru: no ondemandHls URL found")
            }

            // Fallback: parse direct video URLs from the videos array
            val directVideos = extractOkruDirectVideos(dataOptions, name)
            if (directVideos.isNotEmpty()) {
                MKissaLog.i("extractOkru: found ${directVideos.size} direct video URLs")
                return directVideos
            }

            MKissaLog.w("extractOkru: no HLS or direct videos found in data-options")
            emptyList()
        } catch (e: Exception) {
            MKissaLog.e("extractOkru: failed for $name — ${e.message}", e)
            emptyList()
        }
    }

    /** Extract the ondemandHls URL from the data-options JSON.
     *
     * ★ The URL is inside a JSON string (flashvars.metadata), so it has `\"` escaping AND
     * contains `\u0026` (Unicode ampersand). Must use `(.*?)` (non-greedy) until the closing `\"`
     * — NOT `[^\\]+` which stops at the backslash in `\u0026`.
     */
    private fun extractOkruHlsUrl(dataOptions: String): String? {
        // After HTML entity decoding, the metadata JSON string has escaped quotes: \"ondemandHls\":\"<url>\"
        // The URL may contain \u0026 (Unicode ampersand) — don't use [^\\]+, use (.*?) until \"
        val patterns = listOf(
            // Pattern 1: \"ondemandHls\":\"<url>\"  (escaped quotes inside metadata JSON string)
            Regex("""ondemandHls\\":\\"(.*?)\\""""),
            // Pattern 2: "ondemandHls":"<url>"  (unescaped — if metadata is already parsed)
            Regex("""ondemandHls":"([^"]+)"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(dataOptions)
            if (match != null) {
                val url = match.groupValues[1]
                    .replace("\\\\u0026", "&")
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
                if (url.startsWith("http")) return url
            }
        }
        return null
    }

    /** Extract direct MP4 URLs from the videos array in data-options.
     *
     * ★ The videos array is inside the metadata JSON string, so entries have escaped quotes:
     * {\"name\":\"full\",\"url\":\"https://...\"}
     */
    private fun extractOkruDirectVideos(dataOptions: String, name: String): List<Video> {
        val okruHeaders = headers.newBuilder().set("Referer", "https://ok.ru/").build()
        val results = mutableListOf<Video>()

        // Pattern: {\"name\":\"<quality>\",...,\"url\":\"<url>\"}  (escaped quotes)
        // Use (.*?) for the URL to handle \u0026 (backslash) in the URL
        val videoEntries = Regex(
            """\{\\"name\\":\\"([^"]+)\\"[^}]*\\"url\\":\\"(.*?)\\"[^}]*\}""",
        ).findAll(dataOptions)

        for (entry in videoEntries) {
            val quality = entry.groupValues[1]
            val videoUrl = entry.groupValues[2]
                .replace("\\\\u0026", "&")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
            if (videoUrl.startsWith("https://")) {
                val qualityLabel = fixOkruQuality(quality)
                MKissaLog.d("extractOkru: direct video: $qualityLabel → ${MKissaLog.trunc(videoUrl, 80)}")
                results.add(
                    Video(
                        videoUrl = videoUrl,
                        videoTitle = "$name - $qualityLabel",
                        headers = okruHeaders,
                    ),
                )
            }
        }

        // Also try unescaped pattern (in case metadata is already parsed)
        if (results.isEmpty()) {
            val unescapedEntries = Regex(
                """\{"name":"([^"]+)"[^}]*"url":"([^"]+)"[^}]*\}""",
            ).findAll(dataOptions)
            for (entry in unescapedEntries) {
                val quality = entry.groupValues[1]
                val videoUrl = entry.groupValues[2].replace("\\u0026", "&")
                if (videoUrl.startsWith("https://")) {
                    val qualityLabel = fixOkruQuality(quality)
                    results.add(
                        Video(
                            videoUrl = videoUrl,
                            videoTitle = "$name - $qualityLabel",
                            headers = okruHeaders,
                        ),
                    )
                }
            }
        }

        return results
    }

    private fun fixOkruQuality(quality: String): String {
        return when (quality.lowercase()) {
            "ultra" -> "2160p"
            "quad" -> "1440p"
            "full" -> "1080p"
            "hd" -> "720p"
            "sd" -> "480p"
            "low" -> "360p"
            "lowest" -> "240p"
            "mobile" -> "144p"
            else -> quality
        }
    }

    /** Uni server — custom JS player (allanime.uns.bio).
     *
     * The Uni player loads allanime.uns.bio/#<hash>, fetches encrypted video data from its API,
     * and requires the user to click the play button (which triggers ad redirects). After 2-3
     * clicks (with ad redirects blocked), the video source is set on the video element.
     *
     * ★ Uses loadAndExtractVideo: loads the page, blocks ad redirects, auto-clicks play,
     * polls video.src until the video URL is found.
     */
    private suspend fun extractUni(url: String, name: String): List<Video> {
        if (webViewFetcher == null) {
            MKissaLog.w("extractUni: WebViewFetcher not available. Skipping $name.")
            return emptyList()
        }

        return try {
            MKissaLog.i("extractUni: using loadAndExtractVideo for $url")
            val videoSrc = webViewFetcher.loadAndExtractVideo(
                url = url,
                clickDelayMs = 3000, // 3s between clicks (ad redirects take time)
                maxClicks = 4, // Uni needs 3 clicks (ad, ad, video) — try 4 to be safe
                pollTimeoutMs = 20_000,
            )

            if (videoSrc.isBlank() || !videoSrc.startsWith("http")) {
                MKissaLog.w("extractUni: no video URL found via loadAndExtractVideo")
                return emptyList()
            }

            MKissaLog.i("extractUni: found video URL: ${MKissaLog.trunc(videoSrc, 80)}")
            val uniHeaders = headers.newBuilder().set("Referer", "https://allanime.uns.bio/").build()

            if (videoSrc.contains(".m3u8")) {
                playlistUtils.extractFromHls(
                    videoSrc,
                    referer = "https://allanime.uns.bio/",
                    videoNameGen = { q -> "$q ($name)" },
                )
            } else {
                listOf(Video(videoUrl = videoSrc, videoTitle = "$name - MP4", headers = uniHeaders))
            }
        } catch (e: Exception) {
            MKissaLog.e("extractUni: failed for $name — ${e.message}", e)
            emptyList()
        }
    }

    companion object {
        /** The persisted query hash for the episode stream API (from the allanime reference). */
        private const val STREAM_HASH = "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec"

        /** All server names (for settings UI). */
        val SERVER_NAMES = arrayOf("Fm-Hls", "Uni", "Mp4", "Ok", "Luf-Mp4")
    }
}
