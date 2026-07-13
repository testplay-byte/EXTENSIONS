package eu.kanade.tachiyomi.animeextension.en.animepahe.extractor

import eu.kanade.tachiyomi.animeextension.en.animepahe.AnimepaheLog
import eu.kanade.tachiyomi.animeextension.en.animepahe.WebViewFetcher
import eu.kanade.tachiyomi.animeextension.en.animepahe.extractor.jsunpacker.JsUnpacker
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * ★ Kwik video extractor for AnimePahe 180 (Step 4).
 *
 * Uses the PROVEN JsUnpacker library (ported from keiyoushi.lib.jsunpacker) to unpack
 * Kwik's Dean Edwards packed JS. This is the exact same library the reference extension uses.
 *
 * Flow:
 * 1. Fetch the kwik embed page HTML
 * 2. Extract the <script> containing eval(function(p,a,c,k,e,d){...})
 * 3. JsUnpacker.unpackAndCombine() unpacks it natively (proven algorithm)
 * 4. Extract `const source='...'` from the unpacked JS → the video URL
 *
 * @param client The inherited OkHttpClient (has CloudflareInterceptor).
 * @param headers The source's headers (includes Referer: animepahe.pw).
 * @param webViewFetcher Optional WebView fallback for Cloudflare-blocked kwik fetches.
 */
class KwikExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val webViewFetcher: WebViewFetcher? = null,
) {
    /**
     * Extract the video stream URL from a kwik embed link.
     */
    suspend fun extractVideoUrl(kwikUrl: String, referer: String): String? {
        AnimepaheLog.d("KwikExtractor: fetching $kwikUrl")

        val html = fetchKwikHtml(kwikUrl, referer) ?: run {
            AnimepaheLog.e("KwikExtractor: failed to fetch kwik page")
            return null
        }

        AnimepaheLog.d("KwikExtractor: received ${html.length} chars from kwik.cx")
        return extractSourceFromHtml(html)
    }

    suspend fun getHlsVideo(kwikUrl: String, referer: String, quality: String): Video? {
        val videoUrl = extractVideoUrl(kwikUrl, referer) ?: return null
        val videoHeaders = Headers.Builder()
            .set("Referer", "https://kwik.cx/")
            .set("Origin", "https://kwik.cx")
            .build()
        AnimepaheLog.i("KwikExtractor: resolved $quality → ${AnimepaheLog.trunc(videoUrl, 80)}")
        return Video(
            videoUrl = videoUrl,
            videoTitle = quality,
            headers = videoHeaders,
            initialized = false,
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // HTML fetching (OkHttp-first, WebView-fallback)
    // ════════════════════════════════════════════════════════════════════

    private fun fetchKwikHtml(kwikUrl: String, referer: String): String? {
        var html: String? = null
        try {
            val req = Request.Builder()
                .url(kwikUrl)
                .headers(headers.newBuilder().set("Referer", referer).build())
                .build()
            val resp = client.newCall(req).execute()
            html = if (resp.isSuccessful) {
                resp.body?.string()
            } else {
                AnimepaheLog.w("KwikExtractor: OkHttp HTTP ${resp.code} for kwik.cx")
                resp.close()
                null
            }
        } catch (e: Exception) {
            AnimepaheLog.w("KwikExtractor: OkHttp failed — ${e.message}")
        }

        if (html == null && webViewFetcher != null) {
            html = try {
                AnimepaheLog.d("KwikExtractor: falling back to WebView for kwik.cx")
                webViewFetcher.fetchText(kwikUrl)
            } catch (e: Exception) {
                AnimepaheLog.w("KwikExtractor: WebView fallback failed — ${e.message}")
                null
            }
        }

        return html
    }

    // ════════════════════════════════════════════════════════════════════
    // Source URL extraction (JsUnpacker — proven algorithm)
    // ════════════════════════════════════════════════════════════════════

    private fun extractSourceFromHtml(html: String): String? {
        val hasPacker = html.contains("eval(function(")
        AnimepaheLog.d("KwikExtractor: has eval(function(...): $hasPacker")

        if (!hasPacker) {
            val directMatch = videoUrlRegex.find(html)
            if (directMatch != null) {
                AnimepaheLog.d("KwikExtractor: found video URL directly in HTML (no packer)")
                return directMatch.value.replace("\\/", "/")
            }
            AnimepaheLog.w("KwikExtractor: no eval(function(...)) and no direct URL found")
            return null
        }

        // ★ Use JsUnpacker — the PROVEN library from the reference extension
        // The reference pattern:
        //   val script = doc.selectFirst("script:containsData(eval\\(function)")?.data()
        //       ?.substringAfterLast("eval(function(")
        //   val unpacked = JsUnpacker.unpackAndCombine("eval(function($script")
        //   return unpacked.substringAfter("const source=\\'").substringBefore("\\';")
        val doc = org.jsoup.Jsoup.parse(html)
        val scriptData = doc.selectFirst("script:containsData(eval\\(function)")?.data()
        if (scriptData == null) {
            AnimepaheLog.w("KwikExtractor: could not find script tag with eval(function(")
            return null
        }

        // Extract the packed script (after "eval(function(")
        val packedScript = scriptData.substringAfterLast("eval(function(")
        if (packedScript.isBlank()) {
            AnimepaheLog.w("KwikExtractor: packed script is empty after substringAfterLast")
            return null
        }

        AnimepaheLog.d("KwikExtractor: packed script length = ${packedScript.length}")

        // Unpack using JsUnpacker
        val unpacked = JsUnpacker.unpackAndCombine("eval(function($packedScript")
        if (unpacked.isNullOrBlank()) {
            AnimepaheLog.w("KwikExtractor: JsUnpacker failed to unpack the script")
            return null
        }

        AnimepaheLog.d("KwikExtractor: unpacked JS length = ${unpacked.length}")

        // Extract the source URL: const source=\'https://...\'
        // The reference uses: unpacked.substringAfter("const source=\\'").substringBefore("\\';")
        val sourceUrl = unpacked.substringAfter("const source=\\'").substringBefore("\\';")
        if (sourceUrl.isNotBlank() && sourceUrl != unpacked) {
            val cleaned = sourceUrl.replace("\\/", "/")
            AnimepaheLog.d("KwikExtractor: found const source= URL via JsUnpacker")
            return cleaned
        }

        // Fallback: try without escaped quotes (const source='...')
        val sourceUrl2 = unpacked.substringAfter("const source='").substringBefore("';")
        if (sourceUrl2.isNotBlank() && sourceUrl2 != unpacked) {
            AnimepaheLog.d("KwikExtractor: found const source=' URL via JsUnpacker")
            return sourceUrl2
        }

        // Fallback: search for any m3u8 or mp4 URL in the unpacked JS
        val videoMatch = videoUrlRegex.find(unpacked)
        if (videoMatch != null) {
            val url = videoMatch.value.replace("\\/", "/")
            AnimepaheLog.d("KwikExtractor: found video URL via regex in unpacked JS")
            return url
        }

        AnimepaheLog.w("KwikExtractor: could not find source URL in unpacked JS")
        AnimepaheLog.w("KwikExtractor: unpacked JS snippet (first 500): ${AnimepaheLog.trunc(unpacked, 500)}")
        return null
    }

    companion object {
        // Matches any video URL (m3u8 or mp4) with optional escaped slashes
        private val videoUrlRegex = Regex("""https?:\\\\?/\\\\?/[^'""\s|\\]+\.(?:m3u8|mp4)[^'""\s|\\]*""")
    }
}
