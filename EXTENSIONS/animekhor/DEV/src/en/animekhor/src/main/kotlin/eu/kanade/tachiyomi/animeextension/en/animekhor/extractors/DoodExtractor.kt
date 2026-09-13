package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.net.URI

/**
 * DoodStream-family extraction (d000d.com on this site) — port of aniyomi
 * lib/doodextractor (Apache-2.0): embed → /pass_md5/<token> → final mp4 (+ anti-hotlink suffix).
 */
class DoodExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, prefix: String = "Doodstream"): List<Video> =
        listOfNotNull(videoFromUrl(url, prefix))

    fun videoFromUrl(url: String, prefix: String? = null): Video? = runCatching {
        val response = client.newCall(GET(url)).execute()
        val newUrl = response.request.url.toString()
        response.use {
            val doodHost = getBaseUrl(newUrl)
            val content = it.body.string()
            if (!content.contains("'/pass_md5/")) return null

            val extractedQuality = Regex("\\d{3,4}p")
                .find(content.substringAfter("<title>").substringBefore("</title>"))
                ?.groupValues?.getOrNull(0)

            val newQuality = listOfNotNull(
                prefix,
                "Doodstream " + (extractedQuality ?: "mirror"),
            ).joinToString(" - ")

            val md5 = doodHost + (Regex("/pass_md5/[^']*").find(content)?.value ?: return null)
            val token = md5.substringAfterLast("/")
            val randomString = createHashTable()
            val expiry = System.currentTimeMillis()

            val videoUrlStart = client.newCall(
                GET(md5, Headers.headersOf("referer", newUrl)),
            ).execute().body.string()

            val videoUrl = "$videoUrlStart$randomString?token=$token&expiry=$expiry"
            Video(
                videoUrl = videoUrl,
                videoTitle = newQuality,
                headers = doodHeaders(doodHost),
            )
        }
    }.getOrNull()

    private fun createHashTable(length: Int = 10): String {
        val alphabet = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return buildString {
            repeat(length) { append(alphabet.random()) }
        }
    }

    private fun getBaseUrl(url: String): String = URI(url).let {
        "${it.scheme}://${it.host}"
    }

    private fun doodHeaders(host: String) = Headers.Builder().apply {
        add("User-Agent", "Aniyomi")
        add("Referer", "https://$host/")
    }.build()
}
