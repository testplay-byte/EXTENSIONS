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
 * 2. For each matching server: load the flixcloud.cc embed in WebView
 * 3. Intercept /api/m3u8/<token> requests → read the master playlist body
 * 4. Parse the master for quality variants → create Video objects
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
     * @param audioType "sub" or "dub" — filters which flix servers to use
     * @param enableHD1 Whether HD-1 server is enabled in settings
     * @param enableHD2 Whether HD-2 server is enabled in settings
     * @param webviewTimeout WebView extraction timeout in seconds
     * @return List of Video objects
     */
    fun extractVideos(
        anilistId: String,
        epNumber: Int,
        audioType: String,
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

        // 2. Filter + deduplicate servers
        val targetServers = flixResponse.servers.filter { server ->
            // Match audio type
            if (server.dataType != audioType) return@filter false
            // Match enabled servers
            when (server.serverName.uppercase()) {
                "HD-1" -> enableHD1
                "HD-2" -> enableHD2
                else -> true // enable any future servers by default
            }
        }

        if (targetServers.isEmpty()) {
            ReanimeLog.w("extractVideos: no servers match (audioType=$audioType, HD1=$enableHD1, HD2=$enableHD2)")
            return emptyList()
        }

        // 3. Extract videos from each matching server
        for (server in targetServers) {
            try {
                val serverVideos = extractFromServer(server, webviewTimeout)
                videos.addAll(serverVideos)
                ReanimeLog.i("extractVideos: ${server.serverName} (${server.dataType}) → ${serverVideos.size} videos")
            } catch (e: Exception) {
                ReanimeLog.e("extractVideos: ${server.serverName} (${server.dataType}) failed — ${e.message}", e)
            }
        }

        return videos
    }

    /** Extract videos from a single flixcloud.cc server embed. */
    private fun extractFromServer(server: FlixServer, webviewTimeout: Int): List<Video> {
        val embedUrl = server.dataLink
        val serverName = server.serverName
        val audioLabel = server.dataType.uppercase() // "SUB" or "DUB"

        ReanimeLog.i("extractFromServer: $serverName ($audioLabel) — loading $embedUrl")

        // Load the embed in WebView and intercept m3u8 requests
        val captured = webViewFetcher.interceptVideoUrls(
            url = embedUrl,
            timeoutMs = (webviewTimeout * 1000).toLong(),
        )

        if (captured.isEmpty()) {
            ReanimeLog.w("extractFromServer: no m3u8 captured for $serverName ($audioLabel)")
            return emptyList()
        }

        val videos = mutableListOf<Video>()
        val flixHeaders = Headers.Builder()
            .set("Referer", "https://flixcloud.cc/")
            .set("User-Agent", DESKTOP_UA)
            .build()

        for (m3u8 in captured) {
            if (m3u8.body.isNotBlank()) {
                // Parse the master playlist for quality variants
                val variants = PlaylistUtils.parseMaster(m3u8.body, m3u8.requestUrl)
                if (variants.isEmpty()) {
                    // No variants found — use the m3u8 URL directly
                    videos.add(
                        Video(
                            videoUrl = m3u8.requestUrl,
                            videoTitle = "$serverName - $audioLabel",
                            headers = flixHeaders,
                        ),
                    )
                } else {
                    // Create a Video for each quality variant
                    for (v in variants) {
                        videos.add(
                            Video(
                                videoUrl = v.url,
                                videoTitle = "$serverName - ${v.qualityLabel} - $audioLabel",
                                headers = flixHeaders,
                            ),
                        )
                        ReanimeLog.d("  variant: ${v.qualityLabel} (${v.resolution}) → ${ReanimeLog.trunc(v.url, 80)}")
                    }
                }
            } else {
                // Direct video URL (no body — e.g. a .mp4 or .m3u8 from another CDN)
                videos.add(
                    Video(
                        videoUrl = m3u8.requestUrl,
                        videoTitle = "$serverName - $audioLabel",
                        headers = flixHeaders,
                    ),
                )
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
