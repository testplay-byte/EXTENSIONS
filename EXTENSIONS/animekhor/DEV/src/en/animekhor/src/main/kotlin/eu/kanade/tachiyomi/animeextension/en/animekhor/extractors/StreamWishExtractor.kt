package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

/**
 * StreamWish extraction — port of aniyomi lib/streamwishextractor (Apache-2.0), simplified:
 *  - fetch the embed page (guard pages that only load /main.js can't be solved over plain
 *    OkHttp — in that case we return empty and the hoster is skipped gracefully; upstream
 *    solves it with a full JS deobfuscator, deferred for a later iteration)
 *  - unpack packed player JS → master m3u8 → variants (+ captions).
 */
class StreamWishExtractor(private val client: OkHttpClient, private val headers: Headers) {

    fun videosFromUrl(url: String, prefix: String = "StreamWish - "): List<Video> = runCatching {
        val response = client.newCall(GET(url, headers)).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body.string()
        if (body.isBlank() || GUARD_MARKER in body) return emptyList() // main.js guard page

        val doc = Jsoup.parse(body)
        val scriptBody = doc.selectFirst("script:containsData(m3u8)")?.data()
            ?.let { script ->
                if (script.contains("eval(function(p,a,c")) {
                    JsUnpacker.unpackAndCombine(script)
                } else {
                    script
                }
            }

        val masterUrl = scriptBody?.let { M3U8_REGEX.find(it)?.value } ?: return emptyList()

        val subtitleList = extractSubtitles(scriptBody)
        val referer = masterUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}/" }
            ?: "https://${url.toHttpUrlOrNull()?.host}/"

        playlistUtils.extractFromHls(
            playlistUrl = masterUrl,
            referer = referer,
            videoNameGen = { q -> "$prefix$q" },
            subtitleList = playlistUtils.fixSubtitleUrls(subtitleList, masterUrl),
        )
    }.getOrDefault(emptyList())

    private fun extractSubtitles(script: String): List<Track> = runCatching {
        val subtitleStr = script
            .substringAfter("tracks")
            .substringAfter("[")
            .substringBefore("]")
        val fixed = FIX_TRACKS_REGEX.replace(subtitleStr) { match -> "\"${match.value}\"" }
        json.parseToJsonElement("[$fixed]").jsonArray.mapNotNull { el ->
            val obj = el.jsonObject
            val file = obj["file"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val kind = obj["kind"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val label = obj["label"]?.jsonPrimitive?.content
            if (kind.equals("captions", true)) Track(file, label ?: "") else null
        }
    }.getOrDefault(emptyList())

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    companion object {
        private const val GUARD_MARKER = "Page is loading, please wait"
        private val M3U8_REGEX by lazy { Regex("""https[^"]*m3u8[^"]*""") }
        private val FIX_TRACKS_REGEX by lazy { Regex("""(?<!")(file|kind|label)(?!")""") }
    }
}

/**
 * Small helper on PlaylistUtils usage sites: resolves relative subtitle URLs against the
 * playlist origin. Kept here (rather than inside PlaylistUtils) to keep PlaylistUtils lean.
 */
fun PlaylistUtils.fixSubtitleUrls(subs: List<Track>, baseUrl: String): List<Track> = subs.mapNotNull {
    val fixed = when {
        it.url.startsWith("http") -> it.url
        it.url.startsWith("//") -> "https:${it.url}"
        baseUrl.toHttpUrlOrNull() != null -> baseUrl.toHttpUrlOrNull()!!.resolve(it.url)?.toString()
        else -> null
    } ?: return@mapNotNull null
    Track(fixed, it.lang)
}
