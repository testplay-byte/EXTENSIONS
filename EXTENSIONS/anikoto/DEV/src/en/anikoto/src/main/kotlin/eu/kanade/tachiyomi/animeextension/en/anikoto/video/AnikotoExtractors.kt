package eu.kanade.tachiyomi.animeextension.en.anikoto.video

import eu.kanade.tachiyomi.animeextension.en.anikoto.AnikotoLog
import eu.kanade.tachiyomi.animeextension.en.anikoto.VidTubeSourcesResponse
import eu.kanade.tachiyomi.animeextension.en.anikoto.VidTubeTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

/**
 * Video stream extractors for Anikoto.
 * Per WORKSPACE/WORKFLOW/04_VIDEO_EXTRACTION_PLAYBACK/ANIKOTO/extraction-flows.md.
 *
 * Two flows:
 * - [resolveVidTube] (Flow A): VidPlay-1, HD-1, Vidstream-2, VidCloud-1
 *   iframe → data-id → getSources/getSourcesNew → master m3u8 → variants → segments
 *   ★ session 27: unified to getSources (works on all 3 hosts with the type param).
 *   ★ session 52: megaplay.buzz re-encrypted getSources ("enc" AES blob, sources.file
 *   gone). Now: try getSourcesNew (plaintext again on ALL hosts) first, then getSources
 *   with AES-256-CBC decryption of the "enc" blob via [MegaPlayDecrypt]. See
 *   MEMORY/sites/getsources-migration-and-id-analysis.md §3.
 *   ★ session 27: per-stream Referer stored in AudioStream for the proxy to use.
 * - [resolveKiwi] (Flow B): Kiwi-Stream
 *   iframe URL#<base64-fragment> → decode → direct m3u8 → variants → segments
 */
class AnikotoExtractors(
    private val client: OkHttpClient,
    private val json: Json,
    private val webViewFetcher: WebViewFetcher? = null,
) {
    // ── Flow A: VidTube (VidPlay-1, HD-1, Vidstream-2) ──────────────────────

    /** ★ session 52: parsed getSources/getSourcesNew result — master m3u8 + subtitle tracks. */
    private data class SourcesData(val masterM3u8: String, val tracks: List<VidTubeTrack>)

    /**
     * ★ session 52: Resolve the master m3u8 + subtitle tracks for a data-id.
     *
     * megaplay.buzz (HD-1, Vidstream-2) now ENCRYPTS its getSources response ("enc"
     * AES-256-CBC blob — plaintext sources.file is gone; that is what broke playback on
     * 2026-09-09). Strategy, both endpoints verified live:
     * 1. `getSourcesNew?id=X&type=Y` — plaintext sources.file AGAIN on ALL hosts
     *    (megaplay, vidtube, vidwish — the player's own newclient.min.js rewrites
     *    getSources → getSourcesNew exactly like this).
     * 2. `getSources?id=X&type=Y` — fallback: still plaintext on vidtube/vidwish, but on
     *    megaplay returns the "enc" blob → decrypted via [MegaPlayDecrypt]
     *    (AES-256-CBC, key "i?LMTAx0Q6,:}50U" zero-padded to 32 bytes, IV "W0;27ToaUpl_P%'c"
     *    — constants extracted from megaplay's lib/newclient.min.js).
     *
     * The returned m3u8 host rotates per response (megap.shiora.site, megap.mikora.top,
     * s1.akirax.buzz) — all verified WAF-free as of session 52.
     */
    private suspend fun fetchSourcesData(host: String, dataId: String, audioType: String): SourcesData? {
        // Attempt 1: getSourcesNew (plaintext on all hosts — verified live session 52)
        try {
            val url = "https://$host/stream/getSourcesNew?id=$dataId&type=$audioType"
            AnikotoLog.d("resolveVidTube: [2/5] GET getSourcesNew: ${AnikotoLog.trunc(url, 80)}")
            val body = fetchString(url, vidtubeApiHeaders(host))
            parseSourcesBody(body)?.let { return it }
            AnikotoLog.w("resolveVidTube: getSourcesNew response had no usable file (host=$host)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnikotoLog.w("resolveVidTube: getSourcesNew FAILED on $host — ${e.message?.take(80)}")
        }

        // Attempt 2: getSources (legacy — plaintext on vidtube/vidwish, enc-encrypted on megaplay)
        try {
            val url = "https://$host/stream/getSources?id=$dataId&type=$audioType"
            AnikotoLog.d("resolveVidTube: [2/5] GET getSources: ${AnikotoLog.trunc(url, 80)}")
            val body = fetchString(url, vidtubeApiHeaders(host))
            parseSourcesBody(body)?.let { return it }
            AnikotoLog.w("resolveVidTube: getSources response had no usable file (host=$host)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnikotoLog.w("resolveVidTube: getSources FAILED on $host — ${e.message?.take(80)}")
        }
        return null
    }

    /**
     * Parse a getSources/getSourcesNew JSON body → [SourcesData], handling BOTH shapes
     * (verified live session 52):
     * - plaintext: `{"sources":{"file":"https://...m3u8"},"tracks":[...]}`
     * - encrypted: `{"tracks":[...],"enc":"<base64url AES blob>"}` → decrypt → `{"file":"..."}`
     */
    private fun parseSourcesBody(body: String): SourcesData? {
        val sources = json.decodeFromString(VidTubeSourcesResponse.serializer(), body)
        // Shape 1: plaintext sources.file
        sources.sources?.file?.takeIf { it.startsWith("http") }?.let { file ->
            return SourcesData(file, sources.tracks)
        }
        // Shape 2: encrypted enc blob
        val enc = sources.enc?.takeIf { it.isNotBlank() } ?: return null
        AnikotoLog.i("resolveVidTube: response is ENCRYPTED (enc blob, ${enc.length} chars) — decrypting")
        val decrypted = MegaPlayDecrypt.decrypt(enc) ?: return null
        AnikotoLog.d("resolveVidTube: decrypted enc → ${AnikotoLog.trunc(decrypted, 80)}")
        val file = try {
            (json.parseToJsonElement(decrypted) as? JsonObject)?.get("file")?.jsonPrimitive?.content
        } catch (e: Exception) {
            AnikotoLog.e("resolveVidTube: decrypted enc JSON parse FAILED — ${e.message?.take(80)}")
            null
        }
        return file?.takeIf { it.startsWith("http") }?.let { SourcesData(it, sources.tracks) }
    }

    suspend fun resolveVidTube(iframeUrl: String, audioType: String, hosterName: String): AudioStream? {
        val host = extractHost(iframeUrl) ?: run {
            AnikotoLog.e("resolveVidTube: could not extract host from $iframeUrl")
            return null
        }
        AnikotoLog.i("resolveVidTube: START hoster=$hosterName audio=$audioType host=$host")
        try {
            // Step 1: GET iframe page → extract data-id
            AnikotoLog.d("resolveVidTube: [1/5] GET iframe page: ${AnikotoLog.trunc(iframeUrl, 80)}")
            val pageHtml = fetchString(iframeUrl, vidtubePageHeaders(host))
            val dataId = DATA_ID_REGEX.matcher(pageHtml).takeIf { it.find() }?.group(1)
            if (dataId == null) {
                AnikotoLog.e("resolveVidTube: no data-id found in iframe HTML (len=${pageHtml.length})")
                return null
            }
            AnikotoLog.i("resolveVidTube: data-id=$dataId")

            // Step 2: Fetch sources m3u8 + tracks.
            // ★ session 52: megaplay.buzz (HD-1, Vidstream-2) ENCRYPTED its getSources response
            // ("enc" AES-256-CBC blob — sources.file is gone). That broke playback: every
            // megaplay server logged `sources.file='null'` and returned null.
            // New strategy (both endpoints verified live 2026-09-09):
            //   1. getSourcesNew?id=X&type=Y — plaintext sources.file AGAIN on ALL hosts
            //      (megaplay + vidtube + vidwish; the player's own newclient.min.js rewrites
            //      getSources → getSourcesNew the same way).
            //   2. getSources?id=X&type=Y — fallback: plaintext on vidtube/vidwish, but on
            //      megaplay returns the "enc" blob → decrypt with AES-256-CBC
            //      (key "i?LMTAx0Q6,:}50U" zero-padded to 32, IV "W0;27ToaUpl_P%'c" —
            //      extracted from megaplay's lib/newclient.min.js). See MegaPlayDecrypt.
            // The master m3u8 host now ROTATES per response (megap.shiora.site,
            // megap.mikora.top, s1.akirax.buzz) — all currently WAF-free.
            val sourcesData = fetchSourcesData(host, dataId, audioType)
            if (sourcesData == null) {
                AnikotoLog.e("resolveVidTube: no valid m3u8 from getSourcesNew/getSources (host=$host)")
                return null
            }
            val masterM3u8 = sourcesData.masterM3u8
            AnikotoLog.i("resolveVidTube: m3u8=${AnikotoLog.trunc(masterM3u8, 80)}")
            AnikotoLog.i("resolveVidTube: subs=${sourcesData.tracks.size} track(s)")
            // Step 3: parse master m3u8 → variants
            AnikotoLog.d("resolveVidTube: [3/5] fetching master m3u8")
            val masterText = fetchString(masterM3u8, segHeaders(host))
            if (!masterText.startsWith("#EXTM3U")) {
                AnikotoLog.e("resolveVidTube: master is not m3u8 (starts with ${masterText.take(40)})")
                return null
            }
            val variantInfos = parseMasterPlaylist(masterText, masterM3u8)
            if (variantInfos.isEmpty()) {
                AnikotoLog.e("resolveVidTube: no variants in master m3u8")
                return null
            }
            AnikotoLog.i("resolveVidTube: ${variantInfos.size} variants: ${variantInfos.joinToString { "${it.quality}(${it.bandwidth})" }}")

            // Step 4: for each variant, fetch media playlist → parse segments
            // ★ NO ad filtering — the reference v3 keeps ALL segments.
            // ★ session 51: parallelized — all variants fetched concurrently instead of sequentially.
            // Filtering removes real video content (only 12 of 143 segments are on nekostream.site,
            // but the full episode needs all 143). This fixes duration, audio, and buffering.
            AnikotoLog.d("resolveVidTube: [4/5] fetching ${variantInfos.size} variant playlists in parallel (NO ad filter)")
            // ★ session 51 fix: catch CancellationException separately and re-throw it.
            // Kotlin's catch(e: Exception) would swallow CancellationException, preventing
            // proper coroutine cancellation — causing hangs or partial data.
            val variantDataList = coroutineScope {
                variantInfos.map { v ->
                    async(Dispatchers.IO) {
                        variantSemaphore.withPermit {
                            try {
                                val varText = fetchString(v.url, segHeaders(host))
                                val segs = parseVariantSegments(varText, v.url, filterAds = false)
                                AnikotoLog.d("resolveVidTube:   variant ${v.quality}: ${segs.size} segments (all kept, no filter)")
                                if (segs.isNotEmpty()) VariantData(v.quality, v.bandwidth, v.resolution, segs) else null
                            } catch (e: CancellationException) {
                                throw e // ★ MUST re-throw — never swallow CancellationException
                            } catch (e: Exception) {
                                AnikotoLog.e("resolveVidTube:   variant ${v.quality} fetch FAILED: ${e.message}")
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            if (variantDataList.isEmpty()) {
                AnikotoLog.e("resolveVidTube: no variants could be loaded")
                return null
            }

            // Step 5: build subtitles
            val subtitles = sourcesData.tracks.mapNotNull { track ->
                if (track.file.startsWith("http") && track.label.isNotEmpty()) {
                    SubtitleData(track.file, track.label, inferLang(track.label))
                } else null
            }

            val audioLabel = when (audioType) {
                "sub" -> "SUB"
                "hsub" -> "HSUB"
                "dub" -> "DUB"
                else -> audioType.uppercase()
            }
            // ★ per-stream Referer: the proxy must send the iframe host as Referer when
            // fetching segments/subtitles. VidCloud-1 segments (on cloudvideo.lat) 403
            // with vidtube.site Referer; they need vidwish.live. Same pattern for other hosts.
            val streamReferer = "https://$host/"
            AnikotoLog.i("resolveVidTube: SUCCESS hoster=$hosterName audio=$audioLabel variants=${variantDataList.size} subs=${subtitles.size} referer=$streamReferer")
            return AudioStream(audioType, audioLabel, hosterName, variantDataList, subtitles, streamReferer)
        } catch (e: Exception) {
            AnikotoLog.e("resolveVidTube: FAILED hoster=$hosterName audio=$audioType", e)
            return null
        }
    }

    // ── Flow B: Kiwi-Stream (base64 fragment → direct m3u8) ──────────────────

    suspend fun resolveKiwi(iframeUrl: String, audioType: String, hosterName: String): AudioStream? {
        AnikotoLog.i("resolveKiwi: START hoster=$hosterName audio=$audioType")
        try {
            // Step 1: decode base64 fragment → direct m3u8 URL
            val fragment = iframeUrl.substringAfter("#")
            if (fragment.isBlank()) {
                AnikotoLog.e("resolveKiwi: no #fragment in iframe URL")
                return null
            }
            val masterM3u8 = try {
                android.util.Base64.decode(fragment, android.util.Base64.DEFAULT)
                    .toString(Charsets.ISO_8859_1)
            } catch (e: Exception) {
                AnikotoLog.e("resolveKiwi: base64 decode failed", e)
                return null
            }
            if (!masterM3u8.startsWith("http")) {
                AnikotoLog.e("resolveKiwi: decoded fragment is not a URL: ${masterM3u8.take(60)}")
                return null
            }
            AnikotoLog.i("resolveKiwi: decoded m3u8=${AnikotoLog.trunc(masterM3u8, 80)}")

            // Step 2: parse master m3u8 (Referer: vibeplayer.site)
            AnikotoLog.d("resolveKiwi: [2/4] fetching master m3u8")
            val masterText = fetchString(masterM3u8, kiwiHeaders())
            if (!masterText.startsWith("#EXTM3U")) {
                AnikotoLog.e("resolveKiwi: master is not m3u8 (starts with ${masterText.take(40)})")
                return null
            }
            val variantInfos = parseMasterPlaylist(masterText, masterM3u8)
            if (variantInfos.isEmpty()) {
                AnikotoLog.e("resolveKiwi: no variants in master m3u8")
                return null
            }
            AnikotoLog.i("resolveKiwi: ${variantInfos.size} variants: ${variantInfos.joinToString { it.quality }}")

            // Step 3: for each variant, fetch + parse segments (★ NO ad filtering for Kiwi)
            // ★ session 51: parallelized — all variants fetched concurrently.
            AnikotoLog.d("resolveKiwi: [3/4] fetching ${variantInfos.size} variant playlists in parallel (NO ad filter)")
            val variantDataList = coroutineScope {
                variantInfos.map { v ->
                    async(Dispatchers.IO) {
                        variantSemaphore.withPermit {
                            try {
                                val varText = fetchString(v.url, kiwiHeaders())
                                val segs = parseVariantSegments(varText, v.url, filterAds = false)
                                AnikotoLog.d("resolveKiwi:   variant ${v.quality}: ${segs.size} segments (no filter)")
                                if (segs.isNotEmpty()) VariantData(v.quality, v.bandwidth, v.resolution, segs) else null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AnikotoLog.e("resolveKiwi:   variant ${v.quality} fetch FAILED: ${e.message}")
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            if (variantDataList.isEmpty()) {
                AnikotoLog.e("resolveKiwi: no variants could be loaded")
                return null
            }

            // Kiwi labels: mapper "sub" = H-SUB, "dub" = A-DUB
            val audioLabel = if (audioType == "sub") "H-SUB" else "A-DUB"
            // ★ per-stream Referer for Kiwi: vibeplayer.site (where the m3u8 lives).
            // The proxy uses this for segment fetches. Matches the kiwiHeaders() used above.
            val streamReferer = "https://vibeplayer.site/"
            AnikotoLog.i("resolveKiwi: SUCCESS hoster=$hosterName audio=$audioLabel variants=${variantDataList.size} referer=$streamReferer")
            return AudioStream(audioType, audioLabel, hosterName, variantDataList, emptyList(), streamReferer)
        } catch (e: Exception) {
            AnikotoLog.e("resolveKiwi: FAILED hoster=$hosterName audio=$audioType", e)
            return null
        }
    }

    // ── HLS parsing helpers ──────────────────────────────────────────────────

    data class VariantInfo(
        val url: String,
        val bandwidth: Int,
        val quality: String,
        val resolution: Int,
    )

    private fun parseMasterPlaylist(text: String, masterUrl: String): List<VariantInfo> {
        val base = masterUrl.substringBeforeLast("/") + "/"
        val variants = mutableListOf<VariantInfo>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val nextLine = lines.getOrNull(i + 1)?.trim() ?: ""
                if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                    val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val resStr = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)?.groupValues?.get(2) ?: ""
                    val name = Regex("""NAME="([^"]+)"""").find(line)?.groupValues?.get(1)
                    val resolution = resStr.toIntOrNull() ?: 0
                    // ★ If NAME is missing, derive quality from RESOLUTION (e.g., "720" → "720p")
                    val quality = when {
                        !name.isNullOrBlank() && name != "Unknown" -> name
                        resolution > 0 -> "${resolution}p"
                        else -> "Unknown"
                    }
                    val url = if (nextLine.startsWith("http")) nextLine else base + nextLine
                    variants.add(VariantInfo(url, bandwidth, quality, resolution))
                    i += 2
                } else { i++ }
            } else { i++ }
        }
        return variants
    }

    /**
     * Parse a media playlist into [SegmentInfo]s.
     * @param filterAds if true, keep only segments on nekostream.site (real CDN).
     *                  If false, keep all segments (Kiwi — all on ad CDN).
     */
    private fun parseVariantSegments(text: String, variantUrl: String, filterAds: Boolean): List<SegmentInfo> {
        val base = variantUrl.substringBeforeLast("/") + "/"
        val segments = mutableListOf<SegmentInfo>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            if (lines[i].startsWith("#EXTINF:")) {
                val duration = lines[i].substringAfter("#EXTINF:").substringBefore(",").toDoubleOrNull() ?: 0.0
                val nextLine = lines.getOrNull(i + 1)?.trim() ?: ""
                if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                    val url = if (nextLine.startsWith("http")) nextLine else base + nextLine
                    if (!filterAds || isRealSegment(url)) {
                        segments.add(SegmentInfo(url, duration))
                    }
                    i += 2
                } else { i++ }
            } else { i++ }
        }
        return segments
    }

    /** Keep nekostream.site (real CDN), drop ipstatp.com/ibyteimg.com (ad CDN). */
    private fun isRealSegment(url: String): Boolean = url.contains("nekostream.site")

    // ── HTTP + headers ───────────────────────────────────────────────────────

    /**
     * ★ session 30-31: Fetch a URL as a string, with WebView fallback for WAF-blocked CDNs.
     *
     * OkHttp's TLS fingerprint (Conscrypt/JA3) is blocked by cdn.mewstream.buzz's WAF.
     * For known WAF-blocked hosts, skip OkHttp and go straight to WebViewFetcher (Chrome's TLS).
     * For other hosts, try OkHttp first — VidCloud-1/VidPlay-1/Kiwi never trigger the fallback.
     */
    private suspend fun fetchString(url: String, headers: Headers): String = withContext(Dispatchers.IO) {
        // ★ session 31: for WAF-blocked CDN hosts, go straight to WebView (skip OkHttp 403 wait)
        if (isWafBlockedHost(url) && webViewFetcher != null) {
            return@withContext webViewFetcher.fetchText(url)
        }
        val req = Request.Builder().url(url).headers(headers).build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                resp.body?.string() ?: throw RuntimeException("Empty body")
            }
        } catch (e: Exception) {
            // Fallback to WebView for WAF hosts on any error
            if (isWafBlockedHost(url) && webViewFetcher != null) {
                AnikotoLog.i("fetchString: OkHttp failed (${e.message?.take(50)}), falling back to WebView for ${AnikotoLog.trunc(url, 60)}")
                webViewFetcher.fetchText(url)
            } else {
                throw e
            }
        }
    }

    /** ★ session 31: WAF-blocked CDN hosts that require WebView (Chrome TLS). */
    private fun isWafBlockedHost(url: String): Boolean {
        return url.contains("mewstream.buzz") ||
            url.contains("voltara.click") ||
            url.contains("zaptrix.buzz")
    }

    private fun extractHost(url: String): String? {
        return try {
            val m = HOST_REGEX.matcher(url)
            if (m.find()) m.group(1) else null
        } catch (e: Exception) { null }
    }

    private fun vidtubePageHeaders(host: String) = Headers.Builder()
        .set("User-Agent", BROWSER_UA)
        .set("Referer", "https://$host/")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()

    private fun vidtubeApiHeaders(host: String) = Headers.Builder()
        .set("User-Agent", BROWSER_UA)
        .set("Referer", "https://$host/")
        .set("X-Requested-With", "XMLHttpRequest")
        .set("Accept", "*/*")
        .build()

    private fun segHeaders(host: String) = Headers.Builder()
        .set("User-Agent", BROWSER_UA)
        .set("Referer", "https://$host/")
        .set("Accept", "*/*")
        .build()

    private fun kiwiHeaders() = Headers.Builder()
        .set("User-Agent", BROWSER_UA)
        .set("Referer", "https://vibeplayer.site/")
        .set("Accept", "*/*")
        .build()

    private fun inferLang(label: String): String = when {
        label.contains("English", true) -> "eng"
        label.contains("Spanish", true) -> "spa"
        label.contains("French", true) -> "fra"
        label.contains("German", true) -> "deu"
        label.contains("Portuguese", true) -> "por"
        label.contains("Japanese", true) -> "jpn"
        else -> "und"
    }

    companion object {
        // ★ session 51: limit concurrent variant playlist fetches to 2.
        // With N server tasks × M variants, parallel fetching could create 40+ concurrent
        // requests, overwhelming the CDN. The semaphore limits each server's variant
        // fetches to 2 at a time — enough for parallelism but safe for CDNs.
        private val variantSemaphore = Semaphore(2)

        // ★ session 29: DESKTOP Chrome UA (not mobile). The cdn.mewstream.buzz WAF rejects
        // mobile UAs — verified via the reference Next.js project which uses this exact UA
        // and successfully fetches from cdn.mewstream.buzz. Mobile Chrome UA gets 403.
        // Also: no Accept-Language header (the reference project sends minimal headers;
        // extra headers can trigger WAF rules).
        private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val DATA_ID_REGEX: Pattern = Pattern.compile("""data-id="(\d+)"""")
        private val HOST_REGEX: Pattern = Pattern.compile("https?://([^/]+)")
    }
}
