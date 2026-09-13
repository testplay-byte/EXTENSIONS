package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import eu.kanade.tachiyomi.network.GET

/**
 * Minimal HLS playlist handling (lite port of aniyomi lib/playlistutils, Apache-2.0).
 *
 * Covers exactly what AnimeKhor's hosters need:
 *  - fetch a master playlist → parse `#EXT-X-STREAM-INF` variants → one Video per variant
 *  - no variants → a single Video from the playlist itself
 *  - `#EXT-X-MEDIA:TYPE=SUBTITLES` entries → external subtitle Tracks (optional)
 */
class PlaylistUtils(private val client: OkHttpClient, private val headers: Headers? = null) {

    fun extractFromHls(
        playlistUrl: String,
        referer: String? = null,
        videoNameGen: (String) -> String = { quality -> "HLS - $quality" },
        subtitleList: List<Track> = emptyList(),
    ): List<Video> {
        val requestHeaders = buildHeaders(referer)
        val body = runCatching {
            client.newCall(GET(playlistUrl, requestHeaders)).execute().body.string()
        }.getOrNull() ?: return emptyList()

        val subs = subtitleList.ifEmpty { parseSubtitles(body) }

        val variantLines = body.split("#EXT-X-STREAM-INF").drop(1)
        if (variantLines.isEmpty()) {
            // Master without variants (or a media playlist) → single video
            val quality = body.lineSequence()
                .firstOrNull { it.startsWith("#EXT-X-STREAM-INF") }
                ?.substringAfter("RESOLUTION=")?.substringAfter("x")?.substringBefore(",")?.plus("p")
                ?: guessQualityFromUrl(playlistUrl)
            return listOf(
                Video(
                    videoUrl = playlistUrl,
                    videoTitle = videoNameGen(quality),
                    resolution = quality.removeSuffix("p").toIntOrNull(),
                    headers = requestHeaders,
                    subtitleTracks = subs,
                ),
            )
        }

        return variantLines.mapNotNull { line ->
            val resolution = line.substringAfter("RESOLUTION=", "")
                .substringBefore("\n").trim()
                .substringAfter("x", "").substringBefore(",").trim()
            val quality = resolution.takeIf { it.isNotEmpty() }?.plus("p")
                ?: guessQualityFromUrl(line) ?: "source"

            val urlPart = line.substringAfter("\n").lineSequence().firstOrNull {
                it.isNotBlank() && !it.startsWith("#")
            } ?: return@mapNotNull null
            val variantUrl = fixUrl(urlPart, playlistUrl)

            Video(
                videoUrl = variantUrl,
                videoTitle = videoNameGen(quality),
                resolution = quality.removeSuffix("p").toIntOrNull(),
                headers = buildHeaders(referer ?: playlistUrl.originOrNull()),
                subtitleTracks = subs,
            )
        }
    }

    private fun parseSubtitles(body: String): List<Track> = SUBTITLE_REGEX.findAll(body).mapNotNull {
        val name = it.groupValues[1].trim()
        val uri = it.groupValues[2].trim()
        if (uri.isBlank()) return@mapNotNull null
        Track(fixUrl(uri, ""), name)
    }.toList()

    private fun guessQualityFromUrl(url: String): String? =
        Regex("""(\d{3,4})p""").find(url)?.groupValues?.get(1)?.plus("p")

    private fun fixUrl(part: String, baseUrl: String): String = when {
        part.startsWith("http") -> part
        part.startsWith("//") -> "https:$part"
        baseUrl.isNotEmpty() -> baseUrl.toHttpUrlOrNull()?.resolve(part)?.toString() ?: part
        else -> part
    }

    private fun buildHeaders(referer: String?): Headers = referer?.let {
        mapOf("Referer" to it).toHeaders()
    } ?: okhttp3.Headers.headersOf()

    private fun String.originOrNull(): String = this.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}/" }

    companion object {
        private val SUBTITLE_REGEX =
            Regex("""#EXT-X-MEDIA:TYPE=SUBTITLES.*?NAME="(.*?)".*?URI="(.*?)"""")
    }
}
