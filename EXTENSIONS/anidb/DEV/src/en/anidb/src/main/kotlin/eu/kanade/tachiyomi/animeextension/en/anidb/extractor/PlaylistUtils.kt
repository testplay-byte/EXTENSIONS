package eu.kanade.tachiyomi.animeextension.en.anidb.extractor

import eu.kanade.tachiyomi.animeextension.en.anidb.AniDBLog
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.net.URL

/**
 * Simplified HLS (m3u8) playlist parser — ported from the keiyoushi
 * lib/playlistutils, adapted for ext-lib 16 (named-arg Video constructor).
 *
 * Fetches a master .m3u8 playlist, parses variant streams (#EXT-X-STREAM-INF),
 * and returns one [Video] per variant. Also extracts subtitle tracks
 * (#EXT-X-MEDIA:TYPE=SUBTITLES).
 *
 * For single-stream playlists (no variants), returns one Video pointing at
 * the playlist URL.
 *
 * Adapted from MKissa's PlaylistUtils (same proven implementation) — only
 * the logger reference was changed. See EXTENSIONS/mkissa/.../extractor/PlaylistUtils.kt
 * for the original.
 */
class PlaylistUtils(
    private val client: OkHttpClient,
    private val defaultHeaders: Headers = Headers.headersOf(),
) {
    /**
     * Extract videos from an HLS master playlist.
     *
     * @param playlistUrl The master .m3u8 URL
     * @param referer Optional Referer header for the master playlist + video requests
     * @param videoNameGen Lambda to generate the video title from the quality string
     * @param subtitleList Extra subtitle tracks to include
     * @return List<Video> — one per variant stream
     */
    suspend fun extractFromHls(
        playlistUrl: String,
        referer: String? = null,
        videoNameGen: (String) -> String = { it },
        subtitleList: List<Track> = emptyList(),
    ): List<Video> = withContext(Dispatchers.IO) {
        try {
            AniDBLog.d("PlaylistUtils: fetching HLS playlist: ${AniDBLog.trunc(playlistUrl, 100)}")
            val masterHeaders = buildHeaders(referer)
            val response = client.newCall(GET(playlistUrl, masterHeaders)).execute()
            val code = response.code
            val body = response.use { it.body?.string().orEmpty() }
            AniDBLog.d("PlaylistUtils: HLS fetch HTTP $code, body length=${body.length}")
            if (body.isBlank()) {
                AniDBLog.w("PlaylistUtils: empty HLS response (HTTP $code)")
                return@withContext emptyList()
            }
            if (code !in 200..299) {
                AniDBLog.w("PlaylistUtils: HLS fetch failed (HTTP $code) — body: ${body.take(200)}")
                return@withContext emptyList()
            }

            // Extract subtitle tracks from the master playlist
            val subs = parseSubtitleTracks(body) + subtitleList

            // Check if this is a master playlist (has variants) or a media playlist (single stream)
            if (!body.contains("#EXT-X-STREAM-INF:")) {
                // Single-stream media playlist — return one Video
                val videoHeaders = buildHeaders(referer)
                return@withContext listOf(
                    Video(
                        videoUrl = playlistUrl,
                        videoTitle = videoNameGen("Video"),
                        headers = videoHeaders,
                        subtitleTracks = subs,
                    ),
                )
            }

            // Parse variant streams from the master playlist
            val variants = parseVariants(body, playlistUrl)
            if (variants.isEmpty()) return@withContext emptyList()

            variants.map { variant ->
                Video(
                    videoUrl = variant.url,
                    videoTitle = videoNameGen(variant.quality),
                    headers = buildHeaders(referer),
                    subtitleTracks = subs,
                )
            }
        } catch (e: Exception) {
            AniDBLog.e("PlaylistUtils: exception extracting HLS", e)
            emptyList()
        }
    }

    private fun buildHeaders(referer: String?): Headers {
        val builder = defaultHeaders.newBuilder()
            .set("Accept", "*/*")
        if (referer != null) {
            builder.set("Referer", referer)
            val host = try { URL(referer).host } catch (_: Exception) { null }
            if (host != null) builder.set("Origin", "https://$host")
        }
        return builder.build()
    }

    private data class Variant(val url: String, val quality: String)

    private fun parseVariants(body: String, baseUrl: String): List<Variant> {
        val variants = mutableListOf<Variant>()
        val lines = body.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                // Parse RESOLUTION from the attributes
                val resolution = Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.get(1)
                val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                // The next non-empty line is the variant URL
                i++
                while (i < lines.size && lines[i].isBlank()) i++
                if (i < lines.size) {
                    val variantUrl = lines[i].trim()
                    val resolvedUrl = resolveUrl(variantUrl, baseUrl)
                    val quality = if (resolution != null) {
                        "${resolution}p"
                    } else if (bandwidth != null) {
                        "${bandwidth / 1000}kb/s"
                    } else {
                        "Unknown"
                    }
                    variants.add(Variant(resolvedUrl, quality))
                }
            }
            i++
        }
        // Sort by resolution descending (highest quality first)
        return variants.sortedByDescending { it.quality.replace("p", "").toIntOrNull() ?: 0 }
    }

    private fun parseSubtitleTracks(body: String): List<Track> {
        val tracks = mutableListOf<Track>()
        val regex = Regex("""#EXT-X-MEDIA:TYPE=SUBTITLES.*?NAME="([^"]+)".*?URI="([^"]+)"""")
        regex.findAll(body).forEach { match ->
            val name = match.groupValues[1]
            val uri = match.groupValues[2]
            tracks.add(Track(uri, name))
        }
        return tracks
    }

    private fun resolveUrl(url: String, baseUrl: String): String {
        return if (url.startsWith("http")) {
            url
        } else if (url.startsWith("//")) {
            "https:$url"
        } else if (url.startsWith("/")) {
            val base = URL(baseUrl)
            "${base.protocol}://${base.host}$url"
        } else {
            // Relative path — resolve against the base URL's parent
            val baseEnd = baseUrl.lastIndexOf('/')
            baseUrl.substring(0, baseEnd + 1) + url
        }
    }
}
