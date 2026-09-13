package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/**
 * Dailymotion extraction — port of aniyomi lib/dailymotionextractor (Apache-2.0), simplified:
 * the public player metadata endpoint returns the auto (master) m3u8 + subtitle list.
 * Live-verified 2026-09-13 (metadata API → qualities.auto[0].url → playable m3u8).
 */
class DailymotionExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, prefix: String = "Dailymotion - "): List<Video> = runCatching {
        val htmlString = client.newCall(GET(url)).execute().body.string()

        // dmInternalData carries the ts/v1st anti-bot params the metadata endpoint expects
        val internalData = htmlString.substringAfter("\"dmInternalData\":").substringBefore("</script>")
        val ts = internalData.substringAfter("\"ts\":").substringBefore(",").trim('"')
        val v1st = internalData.substringAfter("\"v1st\":\"").substringBefore("\"")

        val videoId = url.substringAfterLast('/').substringBefore('?')
        val metadataUrl = "$DAILYMOTION_URL/player/metadata/video/$videoId?locale=en-US&dmV1st=$v1st&dmTs=$ts&is_native_app=0"

        val body = client.newCall(GET(metadataUrl)).execute().body.string()
        val json = json.parseToJsonElement(body).jsonObject

        val qualities = json["qualities"]?.jsonObject ?: return emptyList()
        val auto = qualities["auto"]?.jsonArray?.firstOrNull()?.jsonObject ?: return emptyList()
        val masterUrl = auto["url"]?.jsonPrimitive?.content ?: return emptyList()

        val subtitles = json["subtitles"]?.jsonObject?.get("data")?.jsonArray?.mapNotNull { s ->
            val obj = s.jsonObject
            val label = obj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val subUrl = obj["urls"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: return@mapNotNull null
            Track(subUrl, label)
        } ?: emptyList()

        playlistUtils.extractFromHls(
            playlistUrl = masterUrl,
            referer = "$DAILYMOTION_URL/",
            videoNameGen = { q -> "$prefix$q" },
            subtitleList = subtitles,
        )
    }.getOrDefault(emptyList())

    private val playlistUtils by lazy { PlaylistUtils(client) }

    companion object {
        private const val DAILYMOTION_URL = "https://www.dailymotion.com"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
