package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

/**
 * Rumble extraction — port of aniyomi lib/rumbleextractor (Apache-2.0).
 * The public HLS VOD endpoint is derived directly from the embed video id.
 */
class RumbleExtractor(private val client: OkHttpClient, private val headers: Headers) {

    fun videosFromUrl(url: String, prefix: String = "Rumble - "): List<Video> {
        val id = extractRumbleId(url) ?: return emptyList()
        val sourceUrl = "https://rumble.com/hls-vod/$id/playlist.m3u8"
        return playlistUtils.extractFromHls(
            playlistUrl = sourceUrl,
            referer = url,
            videoNameGen = { q -> "$prefix$q" },
        )
    }

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    private val regex by lazy { Regex("""rumble\.com/embed/v([a-zA-Z0-9]+)""") }

    private fun extractRumbleId(url: String): String? = regex.find(url)?.groupValues?.get(1)
}
