package eu.kanade.tachiyomi.animeextension.en.animekhor.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient

/**
 * ok.ru extraction — port of aniyomi lib/okruextractor (Apache-2.0).
 * Live-verified 2026-09-13: embed page carries div[data-options] with the videos JSON.
 */
class OkruExtractor(private val client: OkHttpClient, private val headers: Headers = okhttp3.Headers.headersOf()) {

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> = runCatching {
        val document = client.newCall(GET(url, headers)).execute()
            .use { org.jsoup.Jsoup.parse(it.body.string()) }

        val videoString = document.selectFirst("div[data-options]")?.attr("data-options")
            ?: return emptyList()

        val arrayData = videoString
            .substringAfter("\\\"videos\\\":[{\\\"name\\\":\\\"")
            .substringBefore("]")

        arrayData.split("{\\\"name\\\":\\\"").reversed().mapNotNull { data ->
            val videoUrl = data.extractLink("url")
            val quality = data.substringBefore("\\\"").let(::fixQuality)
            val videoQuality = "Okru:$quality".addPrefix(prefix)

            if (videoUrl.startsWith("https://")) {
                Video(videoUrl = videoUrl, videoTitle = videoQuality, headers = null)
            } else {
                null
            }
        }
    }.getOrDefault(emptyList())

    private fun String.addPrefix(prefix: String) = prefix.takeIf(String::isNotBlank)
        ?.let { "$prefix $this" }
        ?: this

    private fun String.extractLink(attr: String) = substringAfter("$attr\\\":\\\"")
        .substringBefore("\\\"")
        .replace("\\\\u0026", "&")

    private fun fixQuality(quality: String): String = when (quality) {
        "ultra" -> "2160p"
        "quad" -> "1440p"
        "full" -> "1080p"
        "hd" -> "720p"
        "sd" -> "480p"
        "low" -> "360p"
        "lowest" -> "240p"
        "mobile" -> "144p"
        else -> quality
    }
}
