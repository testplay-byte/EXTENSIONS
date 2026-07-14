package eu.kanade.tachiyomi.animeextension.en.reanime.extractor

import eu.kanade.tachiyomi.animeextension.en.reanime.FlixResponse
import eu.kanade.tachiyomi.animeextension.en.reanime.FlixServer
import eu.kanade.tachiyomi.animeextension.en.reanime.ReanimeLog
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Video extractor for reanime.to.
 *
 * Flow:
 * 1. GET /api/flix/<anilist_id>/<ep> → server list (HD-1/HD-2 × sub/dub)
 * 2. For each unique embed URL: load in WebView, intercept m3u8 requests
 * 3. Parse the master playlist for quality variants → create Video objects
 *
 * ★ Server deduplication: HD-1 sub and HD-1 dub share the SAME embed URL (v=1).
 * HD-2 sub and HD-2 dub share v=2. So we only load 2 unique embeds, not 4.
 * Each embed produces m3u8 URLs that may serve both sub and dub audio tracks.
 *
 * @param client The OkHttp client (inherited from the source — has CloudflareInterceptor)
 * @param headers Default headers (Referer, User-Agent)
 * @param json JSON parser
 * @param webViewFetcher WebView fetcher for flixcloud.cc embed loading + m3u8 interception
 */
class ReanimeExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val json: Json,
    private val webViewFetcher: WebViewFetcher,
) {

    /**
     * Extract all available videos for an episode.
     *
     * @param anilistId The AniList ID (used for /api/flix/<id>/<ep>)
     * @param epNumber The episode number
     * @param preferredAudio "sub" or "dub" — used for SORTING only (not filtering)
     * @param enableHD1 Whether HD-1 server is enabled in settings
     * @param enableHD2 Whether HD-2 server is enabled in settings
     * @param webviewTimeout WebView extraction timeout in seconds
     * @return List of Video objects (ALL servers — sub + dub — sorted by preference)
     */
    fun extractVideos(
        anilistId: String,
        epNumber: Int,
        preferredAudio: String,
        enableHD1: Boolean,
        enableHD2: Boolean,
        webviewTimeout: Int,
    ): List<Video> {
        val videos = mutableListOf<Video>()

        // 1. Fetch the server list from /api/flix/<anilist_id>/<ep>
        val flixUrl = "https://reanime.to/api/flix/$anilistId/$epNumber"
        ReanimeLog.i("extractVideos: fetching servers from $flixUrl")

        val flixResponse = try {
            client.newCall(GET(flixUrl, headers)).execute().use { resp ->
                parseFlixResponse(resp)
            }
        } catch (e: Exception) {
            ReanimeLog.e("extractVideos: failed to fetch server list — ${e.message}", e)
            return emptyList()
        }

        if (!flixResponse.success || flixResponse.servers.isEmpty()) {
            ReanimeLog.w("extractVideos: no servers returned (success=${flixResponse.success})")
            return emptyList()
        }

        ReanimeLog.i("extractVideos: ${flixResponse.servers.size} servers returned")
        flixResponse.servers.forEach { s ->
            ReanimeLog.d("  server: ${s.serverName} / ${s.dataType} / ${s.dataLink}")
        }

        // 2. Filter by enabled servers (but NOT by audio type — show both sub and dub)
        val enabledServers = flixResponse.servers.filter { server ->
            when (server.serverName.uppercase()) {
                "HD-1" -> enableHD1
                "HD-2" -> enableHD2
                else -> true // enable any future servers by default
            }
        }

        if (enabledServers.isEmpty()) {
            ReanimeLog.w("extractVideos: no servers enabled (HD1=$enableHD1, HD2=$enableHD2)")
            return emptyList()
        }

        // 3. Group servers by unique embed URL (HD-1 sub+dub share v=1, HD-2 sub+dub share v=2)
        // This way we only load each embed once, but create Videos for both sub and dub.
        val embedGroups = enabledServers.groupBy { it.dataLink }
        ReanimeLog.i("extractVideos: ${embedGroups.size} unique embed URLs to load")

        // 4. Extract videos from each unique embed
        for ((embedUrl, servers) in embedGroups) {
            try {
                val embedVideos = extractFromEmbed(embedUrl, servers, webviewTimeout)
                videos.addAll(embedVideos)
                ReanimeLog.i("extractVideos: $embedUrl → ${embedVideos.size} videos")
            } catch (e: Exception) {
                ReanimeLog.e("extractVideos: $embedUrl failed — ${e.message}", e)
            }
        }

        return videos
    }

    /**
     * Extract videos from a single flixcloud.cc embed URL.
     *
     * @param embedUrl The flixcloud.cc/e/<code>?v=<N> URL
     * @param servers All FlixServer entries that share this embed URL (e.g. HD-1 sub + HD-1 dub)
     * @param webviewTimeout WebView extraction timeout in seconds
     * @return List of Video objects
     */
    private fun extractFromEmbed(
        embedUrl: String,
        servers: List<FlixServer>,
        webviewTimeout: Int,
    ): List<Video> {
        val serverName = servers.firstOrNull()?.serverName ?: "Unknown"
        val audioTypes = servers.map { it.dataType.uppercase() }.distinct() // ["SUB"], ["DUB"], or ["SUB","DUB"]

        ReanimeLog.i("extractFromEmbed: $serverName (audio: $audioTypes) — loading $embedUrl")

        // Load the embed in WebView and intercept m3u8 requests
        val captured = webViewFetcher.interceptVideoUrls(
            url = embedUrl,
            timeoutMs = (webviewTimeout * 1000).toLong(),
        )

        if (captured.isEmpty()) {
            ReanimeLog.w("extractFromEmbed: no m3u8 captured for $serverName")
            return emptyList()
        }

        val videos = mutableListOf<Video>()
        val flixHeaders = Headers.Builder()
            .set("Referer", "https://flixcloud.cc/")
            .set("User-Agent", DESKTOP_UA)
            .build()

        for (m3u8 in captured) {
            if (m3u8.body.isNotBlank() && m3u8.body.startsWith("#EXTM3U")) {
                // Parse the master playlist for quality variants
                val variants = PlaylistUtils.parseMaster(m3u8.body, m3u8.requestUrl)
                if (variants.isEmpty()) {
                    // No variants found — it's a media playlist. Use the m3u8 URL directly.
                    // Create a Video for each audio type (sub/dub) — the HLS stream may
                    // contain both audio tracks; the player will let the user switch.
                    for (audio in audioTypes) {
                        videos.add(
                            Video(
                                videoUrl = m3u8.requestUrl,
                                videoTitle = "$serverName - $audio",
                                headers = flixHeaders,
                            ),
                        )
                    }
                } else {
                    // Create a Video for each quality variant × audio type
                    for (v in variants) {
                        for (audio in audioTypes) {
                            videos.add(
                                Video(
                                    videoUrl = v.url,
                                    videoTitle = "$serverName - ${v.qualityLabel} - $audio",
                                    headers = flixHeaders,
                                ),
                            )
                            ReanimeLog.d("  variant: ${v.qualityLabel} ($audio) → ${ReanimeLog.trunc(v.url, 80)}")
                        }
                    }
                }
            } else {
                // Direct video URL (no body or not m3u8 — e.g. a .mp4 from another CDN)
                for (audio in audioTypes) {
                    videos.add(
                        Video(
                            videoUrl = m3u8.requestUrl,
                            videoTitle = "$serverName - $audio",
                            headers = flixHeaders,
                        ),
                    )
                }
            }
        }

        return videos
    }

    /** Parse the /api/flix/ response. */
    private fun parseFlixResponse(response: Response): FlixResponse {
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) {
            ReanimeLog.w("parseFlixResponse: empty body")
            return FlixResponse()
        }
        return try {
            json.decodeFromString<FlixResponse>(body)
        } catch (e: Exception) {
            ReanimeLog.e("parseFlixResponse: JSON parse failed — ${e.message}", e)
            ReanimeLog.d("parseFlixResponse: body=${ReanimeLog.trunc(body, 500)}")
            FlixResponse()
        }
    }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
