package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * VidHide-platform extraction (ahvsh.com / sbsonic.com on this site) — port of
 * aniyomi lib/vidhideextractor (Apache-2.0): embed page contains packed player JS;
 * unpack it and read the m3u8 + caption tracks.
 */
class VidHideExtractor(private val client: OkHttpClient, private val headers: Headers) {

    fun videosFromUrl(url: String, videoNameGen: (String) -> String = { quality -> "VidHide - $quality" }): List<Video> = runCatching {
        val script = fetchAndExtractScript(url) ?: return emptyList()
        val playlists = sourceRegex.findAll(script).mapNotNull {
            fixUrl(it.groupValues[1], url)
        }.toList()
        if (playlists.isEmpty()) return emptyList()

        val subtitleList = extractSubtitles(script)

        playlists.flatMap { videoUrl ->
            playlistUtils.extractFromHls(
                playlistUrl = videoUrl,
                referer = url,
                videoNameGen = videoNameGen,
                subtitleList = subtitleList,
            )
        }
    }.getOrDefault(emptyList())

    private fun fetchAndExtractScript(url: String): String? {
        val body = client.newCall(GET(url, headers)).execute().body.string()
        val packed = Regex("""<script[^>]*>\s*(eval\(function\(p,a,c,k,e,d[^\n]+)\s*</script>""")
            .find(body)?.groupValues?.get(1)
        return packed?.let { JsUnpacker.unpackAndCombine(it) }
            ?: body.takeIf { it.contains("m3u8") }
    }

    private fun extractSubtitles(script: String): List<Track> = runCatching {
        val subtitleStr = script
            .substringAfter("tracks")
            .substringAfter("[")
            .substringBefore("]")
        val fixed = FIX_TRACKS_REGEX.replace(subtitleStr) { match -> "\"${match.value}\"" }
        Json.parseToJsonElement("[$fixed]").jsonArray.mapNotNull { el ->
            val obj = el.jsonObject
            val file = obj["file"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val kind = obj["kind"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (kind.equals("captions", true)) Track(fixUrl(file, ""), obj["label"]?.jsonPrimitive?.content ?: "") else null
        }
    }.getOrDefault(emptyList())

    private fun fixUrl(part: String, baseUrl: String): String = when {
        part.startsWith("http") -> part
        part.startsWith("//") -> "https:$part"
        baseUrl.toHttpUrlOrNull() != null -> baseUrl.toHttpUrlOrNull()!!.resolve(part)?.toString() ?: part
        else -> part
    }

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    companion object {
        // Capture both `https://domain/master.m3u8?query` and `/domain/master.m3u8?query`
        private val sourceRegex = Regex(""""((?:https?:/)?/[^"]*m3u8[^"]*)"""")
        private val FIX_TRACKS_REGEX by lazy { Regex("""(?<!")(file|kind|label)(?!")""") }
    }
}
