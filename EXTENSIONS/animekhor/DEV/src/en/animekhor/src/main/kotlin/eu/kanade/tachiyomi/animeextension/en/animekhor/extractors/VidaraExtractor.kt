package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Vidara extraction — live-verified 2026-09-13:
 *   POST https://vidara.to/api/stream  {"filecode": <id from /e/<id>>}  (JSON)
 *   → { streaming_url: master.m3u8, subtitles: [{file_path, language}], ... }
 */
class VidaraExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, prefix: String = "Vidara - "): List<Video> = runCatching {
        val filecode = url.substringAfter("/e/").substringBefore('?').substringBefore('#')
        if (filecode.isBlank()) return emptyList()

        val body = """{"filecode":"$filecode","device":"web"}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$VIDARA_BASE/api/stream")
            .post(body)
            .header("Referer", url)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val responseBody = client.newCall(request).execute().body.string()
        val json = Json.parseToJsonElement(responseBody).let { it.jsonObject }

        val streamingUrl = json["streaming_url"]?.jsonPrimitive?.content ?: return emptyList()
        val subtitles = json["subtitles"]?.jsonArray?.mapNotNull { s ->
            val obj = s.jsonObject
            val path = obj["file_path"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val lang = obj["language"]?.jsonPrimitive?.content ?: ""
            Track(path, lang)
        } ?: emptyList()

        playlistUtils.extractFromHls(
            playlistUrl = streamingUrl,
            referer = "$VIDARA_BASE/",
            videoNameGen = { q -> "$prefix$q" },
            subtitleList = subtitles,
        )
    }.getOrDefault(emptyList())

    private val playlistUtils by lazy { PlaylistUtils(client) }

    companion object {
        private const val VIDARA_BASE = "https://vidara.to"
        private val Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
