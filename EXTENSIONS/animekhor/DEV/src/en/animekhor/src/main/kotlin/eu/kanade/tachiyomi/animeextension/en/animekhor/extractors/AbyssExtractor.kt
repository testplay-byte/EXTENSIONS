package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AbyssPlayer extraction — live-verified 2026-09-13:
 *   1. player page carries `datas="<base64 blob>"`
 *   2. POST the blob to https://enc-dec.app/api/dec-abyss  (community decrypt service)
 *   3. → { result: { sources: [{url, type: "720p", status: true, ...}] } }
 *
 * (The decrypted payload also carries subtitle slugs, but the subtitle URL pattern is not
 * publicly known — skipped for now rather than guessed.)
 */
class AbyssExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, prefix: String = "Abyss - "): List<Video> = runCatching {
        val page = client.newCall(GET(url)).execute().body.string()
        val datas = DATAS_REGEX.find(page)?.groupValues?.get(1) ?: return emptyList()

        val body = """{"text":"$datas"}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(DEC_API_URL)
            .post(body)
            .build()

        val response = client.newCall(request).execute().body.string()
        val root = Json.parseToJsonElement(response).jsonObject
        val result = root["result"]?.jsonObject ?: return emptyList()
        val sources = result["sources"]?.jsonArray ?: return emptyList()

        sources.mapNotNull { s ->
            val obj = runCatching { s.jsonObject }.getOrNull() ?: return@mapNotNull null
            val srcUrl = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.content ?: "source"
            val statusOk = obj["status"]?.jsonPrimitive?.content != "false"
            if (!statusOk) return@mapNotNull null
            Video(
                videoUrl = srcUrl,
                videoTitle = "$prefix$type",
                resolution = type.removeSuffix("p").toIntOrNull(),
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val DEC_API_URL = "https://enc-dec.app/api/dec-abyss"
        private val DATAS_REGEX = Regex("""datas\s*=\s*["'`]([A-Za-z0-9+/=]+)["'`]""")
    }
}
