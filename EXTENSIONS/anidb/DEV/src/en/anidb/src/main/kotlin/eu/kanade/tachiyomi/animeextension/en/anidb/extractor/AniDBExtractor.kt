package eu.kanade.tachiyomi.animeextension.en.anidb.extractor

import eu.kanade.tachiyomi.animeextension.en.anidb.AniDBLog
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient

/**
 * AniDB video extractor — the full pipeline for a single episode:
 *
 *  1. Fetch the embed page (https://anidb.app/embed/<token>)
 *  2. Regex out the JW Player `sources: [{file: '<m3u8 URL>', type: 'hls'}]` blob
 *     → gives the master.m3u8 URL on https://hls.anidb.app/stream/<token>/master.m3u8
 *  3. Hand the master.m3u8 to [PlaylistUtils.extractFromHls] → one Video per quality
 *
 * ★ AniDB has a SINGLE video server (the site's own host, hls.anidb.app) — there is
 * no server-selection UI on the site. Each audio language (jpn/eng) has its own
 * embed token → its own m3u8 stream. Sub/dub is handled at the caller level by
 * fetching the languages API and calling [extract] once per language.
 *
 * ★ No token crypto (unlike AniKoto RC4 / MKissa AES-GCM). The embed token is a
 * plain string in the URL.
 *
 * ★ No Referer required for hls.anidb.app (verified via curl: HTTP 200 with and
 * without Referer). We still pass the embed URL as Referer for safety.
 *
 * Verified against the live embed page (see MEMORY/sites/site-analysis.md §player).
 */
class AniDBExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val playlistUtils: PlaylistUtils,
) {
    /**
     * Extract videos for a single audio language.
     *
     * @param embedUrl The embed player URL (https://anidb.app/embed/<token>)
     * @param audioLabel The display label for this audio ("Sub" or "Dub")
     * @return List<Video> — one per quality variant in the master.m3u8
     */
    suspend fun extract(embedUrl: String, audioLabel: String): List<Video> = withContext(Dispatchers.IO) {
        AniDBLog.i("extract: START embed=${AniDBLog.trunc(embedUrl, 80)} audio=$audioLabel")
        try {
            // 1. Fetch the embed page
            val embedResponse = client.newCall(GET(embedUrl, headers)).execute()
            val embedHtml = embedResponse.use { it.body?.string().orEmpty() }
            if (embedHtml.isBlank()) {
                AniDBLog.w("extract: empty embed page response")
                return@withContext emptyList()
            }
            AniDBLog.d("extract: embed page fetched (${embedHtml.length} chars)")

            // 2. Regex out the m3u8 URL from the JW Player `sources` blob.
            //    The embed page contains:
            //      sources: [{ file: 'https://hls.anidb.app/stream/<token>/master.m3u8', type: 'hls' }]
            //    The URL is single-quoted; may contain query params.
            val m3u8Url = M3U8_REGEX.find(embedHtml)?.groupValues?.get(1)
            if (m3u8Url.isNullOrBlank()) {
                AniDBLog.w("extract: no m3u8 URL found in embed page")
                AniDBLog.d("extract: embed HTML snippet: ${AniDBLog.trunc(embedHtml, 400)}")
                return@withContext emptyList()
            }
            AniDBLog.i("extract: found m3u8: ${AniDBLog.trunc(m3u8Url, 100)}")

            // 3. Extract quality variants via PlaylistUtils
            val videos = playlistUtils.extractFromHls(
                playlistUrl = m3u8Url,
                referer = embedUrl,
                videoNameGen = { quality -> "$audioLabel - $quality" },
            )
            AniDBLog.i("extract: DONE — ${videos.size} videos for $audioLabel")
            videos
        } catch (e: Exception) {
            AniDBLog.e("extract: FAILED for $audioLabel — ${e.message}", e)
            emptyList()
        }
    }

    companion object {
        // Matches: file: 'https://...master.m3u8' (single-quoted, may have query params)
        // Also handles double-quoted variant just in case.
        private val M3U8_REGEX = Regex("""file:\s*['"]([^'"]+\.m3u8[^'"]*)['"]""")
    }
}
