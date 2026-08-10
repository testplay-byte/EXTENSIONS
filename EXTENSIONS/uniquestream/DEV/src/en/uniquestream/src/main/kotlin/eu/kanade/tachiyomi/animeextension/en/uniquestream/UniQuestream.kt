package eu.kanade.tachiyomi.animeextension.en.uniquestream

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get


/**
 * UniQuestream — Aniyomi extension for anime.uniquestream.net
 *
 * Video pipeline (ext-lib 16, v16.16 — hardened AES-128 decryption):
 *   - Override getHosterList() directly (NOT videoListParse)
 *   - Single API call per episode: /api/v1/episode/{id}/media/dash/{locale}
 *   - getHosterList FETCHES the master m3u8 itself, PARSES it to extract
 *     variant playlists, and returns Video objects pointing to VARIANT proxy
 *     URLs directly (no master m3u8 in the player's view).
 *   - HLS proxy on 127.0.0.1 routes variant m3u8 + segment requests through
 *     the extension's OkHttpClient (CDN is behind Cloudflare).
 *   - **AES-128 decryption**: The CDN encrypts TS segments with AES-128.
 *     The proxy detects #EXT-X-KEY, fetches the 16-byte key, strips
 *     encryption tags from m3u8, and decrypts segments server-side.
 *     The player receives plain, unencrypted MPEG-TS data.
 *   - Auto-try-next: resolveVideo returns null on failure.
 */
class UniQuestream : AnimeHttpSource(), ConfigurableAnimeSource {

    companion object {
        private const val TAG = "UniQuestream"
        private const val TAG_V = "UniQuestream-Video"
        private const val API = "/api/v1"
        private const val PREF_AUDIO_KEY = "preferred_audio"
        private const val PAGE_LIMIT = 20

        private fun trunc(s: String, maxLen: Int = 300): String =
            if (s.length <= maxLen) s else s.substring(0, maxLen) + "...[${s.length} chars]"
    }

    override val name = "UniQuestream"
    override val baseUrl = "https://anime.uniquestream.net"
    override val lang = "en"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    // -- HLS Proxy ------------------------------------------------
    private val proxyUpstreamHeaders: Headers by lazy {
        headersOf(
            "Referer", baseUrl,
            "Origin", baseUrl,
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )
    }
    private var hlsProxy: HlsProxyServer? = null

    private fun ensureProxy(): HlsProxyServer {
        var proxy = hlsProxy
        if (proxy == null || !proxy.isRunning) {
            proxy = HlsProxyServer(client.newBuilder().build(), proxyUpstreamHeaders)
            hlsProxy = proxy
            logV("ensureProxy: started new proxy instance")
        }
        return proxy
    }

    // -- Master m3u8 parsing --------------------------------------

    private fun fetchMasterM3u8(masterUrl: String): String? {
        logV("fetchMasterM3u8: START fetching ${trunc(masterUrl, 120)}")
        try {
            val request = Request.Builder()
                .url(masterUrl)
                .headers(proxyUpstreamHeaders)
                .build()
            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val elapsed = System.currentTimeMillis() - startTime
            val code = response.code
            val contentType = response.header("Content-Type") ?: ""
            val body = response.body?.string()
            val contentLength = response.header("Content-Length")
            response.close()

            logV("fetchMasterM3u8: HTTP $code ct=$contentType len=${body?.length ?: 0} contentLength=$contentLength took=${elapsed}ms url=${trunc(masterUrl, 100)}")

            if (code != 200 || body == null) {
                logVE("fetchMasterM3u8: FAILED HTTP $code, body=${body != null}")
                // Log first 300 chars of body if it looks like an error page
                if (body != null && !body.trimStart().startsWith("#EXTM3U")) {
                    logVE("fetchMasterM3u8: non-m3u8 response: ${trunc(body, 300)}")
                }
                return null
            }

            val trimmed = body.trimStart()
            if (!trimmed.startsWith("#EXTM3U")) {
                logVE("fetchMasterM3u8: NOT m3u8! First 300: ${trunc(trimmed, 300)}")
                return null
            }

            // Check if master has any EXT-X-KEY (encryption) — shouldn't be in master but log it
            val hasKey = body.lines().any { it.trim().startsWith("#EXT-X-KEY") }
            if (hasKey) {
                logV("fetchMasterM3u8: NOTE - master m3u8 contains #EXT-X-KEY (unusual for master)")
            }

            // Count variants (EXT-X-STREAM-INF lines)
            val streamCount = body.lines().count { it.trim().startsWith("#EXT-X-STREAM-INF") }
            logV("fetchMasterM3u8: OK, $streamCount variant(s) detected, took=${elapsed}ms")

            return body
        } catch (e: Exception) {
            logVE("fetchMasterM3u8: EXCEPTION - ${e.javaClass.simpleName}: ${e.message}", e)
            return null
        }
    }

    private fun parseMasterM3u8(masterText: String, masterUrl: String): List<VariantInfo> {
        val baseDir = masterUrl.substringBeforeLast("/") + "/"
        val variants = mutableListOf<VariantInfo>()
        val lines = masterText.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val resolution = parseResolution(line)
                val bandwidth = parseBandwidth(line)
                val resolutionHeight = resolution?.substringAfter("x")?.toIntOrNull()
                val qualityLabel = resolution
                    ?: (if (bandwidth >= 5_000_000) "1080p"
                        else if (bandwidth >= 2_500_000) "720p"
                        else if (bandwidth >= 1_000_000) "480p"
                        else if (bandwidth >= 500_000) "360p"
                        else "Unknown")

                // Try URI= attribute first (inline format), then next line
                val uriMatch = Regex("URI=\"([^\"]+)\"").find(line)
                val variantUrl = if (uriMatch != null) {
                    resolveUrl(baseDir, uriMatch.groupValues[1])
                } else {
                    i++
                    if (i < lines.size) {
                        val nextLine = lines[i].trim().removeSurrounding("\"")
                        resolveUrl(baseDir, nextLine)
                    } else null
                }

                if (variantUrl != null) {
                    logV("parseMaster: $qualityLabel (${bandwidth}bps) -> ${trunc(variantUrl, 100)}")
                    variants.add(VariantInfo(variantUrl, qualityLabel, resolutionHeight))
                }
            }
            i++
        }
        return variants
    }

    private fun parseResolution(tagLine: String): String? =
        Regex("RESOLUTION=(\\d+x\\d+)").find(tagLine)?.groupValues?.get(1)

    private fun parseBandwidth(tagLine: String): Long =
        Regex("BANDWIDTH=(\\d+)").find(tagLine)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        return base + relative
    }

    private data class VariantInfo(val url: String, val qualityLabel: String, val resolution: Int?)

    // -- Helpers --------------------------------------------------

    private fun apiBuilder() = "$baseUrl$API".toHttpUrl().newBuilder()
    private inline fun <reified T> Response.parseJson(): T = body!!.string().let { json.decodeFromString<T>(it) }
    private fun logD(msg: String) = Log.d(TAG, msg)
    private fun logV(msg: String) = Log.d(TAG_V, msg)
    private fun logW(msg: String) = Log.w(TAG, msg)
    private fun logE(msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    }
    private fun logVE(msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG_V, msg, t) else Log.e(TAG_V, msg)
    }

    // ================== Catalogue ==================

    override fun popularAnimeRequest(page: Int): Request {
        val url = apiBuilder().addPathSegment("videos").addPathSegment("popular")
            .addQueryParameter("limit", "20").addQueryParameter("page", page.toString()).build()
        return GET(url.toString(), headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val items = response.parseJson<List<SeriesDto>>()
        return AnimesPage(items.map { it.toSAnime() }, items.size >= 20)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiBuilder().addPathSegment("videos").addPathSegment("new")
            .addQueryParameter("limit", "20").addQueryParameter("page", page.toString()).build()
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val items = response.parseJson<List<SeriesDto>>()
        return AnimesPage(items.map { it.toSAnime() }, items.size >= 20)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val typeFilter = filters.filterIsInstance<TypeFilter>().firstOrNull()
        val typeValue = typeFilter?.value ?: "all"
        val url = apiBuilder().addPathSegment("search")
            .addQueryParameter("query", query).addQueryParameter("t", typeValue)
            .addQueryParameter("limit", "25").build()
        return GET(url.toString(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val data = response.parseJson<SearchResponseDto>()
        val allItems = data.series + data.movies
        return AnimesPage(allItems.map { it.toSAnime() }, false)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(AnimeFilter.Header("Type"), TypeFilter())

    private class TypeFilter : AnimeFilter.Select<String>(
        "Type", arrayOf("All", "TV Shows", "Movies"), 0,
    ) {
        val value: String get() = when (state) { 0 -> "all"; 1 -> "show"; 2 -> "movie"; else -> "all" }
    }

    // ================== Details ==================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val contentId = anime.url.substringAfter("/series/").substringBefore("/")
        val url = apiBuilder().addPathSegment("series").addPathSegment(contentId).build()
        return GET(url.toString(), headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        return try {
            val data = response.parseJson<SeriesDetailDto>()
            SAnime.create().apply {
                setUrlWithoutDomain("/series/${data.contentId}")
                title = data.title.ifBlank { "Unknown" }
                description = buildString {
                    data.description?.let { append(it) }
                    data.ratingAvg?.let {
                        if (isNotEmpty()) append("\n\n")
                        append("\u2605 Rating: $it/5")
                        data.ratingCount?.let { count -> append(" ($count votes)") }
                    }
                    data.audioLocales?.let { locales ->
                        if (isNotEmpty()) append("\n\n")
                        append("Audio: ${locales.joinToString(", ") { localeToLabel(it) }}")
                    }
                    data.subtitleLocales?.let { locales ->
                        append("Subtitles: ${locales.joinToString(", ") { localeToLabel(it) }}")
                    }
                }
                genre = data.genre?.joinToString(", ") { it.title }
                thumbnail_url = data.images?.firstOrNull { it.type == "poster_tall" }?.url
                status = when {
                    data.status == "RELEASING" -> SAnime.ONGOING
                    data.status == "FINISHED" -> SAnime.COMPLETED
                    else -> SAnime.UNKNOWN
                }
                update_strategy = AnimeUpdateStrategy.ALWAYS_UPDATE
                initialized = true
            }
        } catch (e: Exception) {
            logE("animeDetailsParse: FAILED", e)
            SAnime.create().apply { initialized = true }
        }
    }

    override fun getAnimeUrl(anime: SAnime): String {
        val contentId = anime.url.substringAfter("/series/").substringBefore("/")
        return "$baseUrl/series/$contentId"
    }

    // ================== Episodes ==================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val contentId = anime.url.substringAfter("/series/").substringBefore("/")
        val seriesData = try {
            val req = apiBuilder().addPathSegment("series").addPathSegment(contentId).build()
            val resp = client.newCall(GET(req.toString(), headers)).awaitSuccess()
            resp.parseJson<SeriesDetailDto>()
        } catch (e: Exception) {
            logE("getEpisodeList: failed", e)
            return emptyList()
        }
        val seasons = seriesData.seasons
        if (seasons.isNullOrEmpty()) return emptyList()
        val isMultiSeason = seasons.size > 1
        val episodes = mutableListOf<SEpisode>()
        for ((seasonIdx, season) in seasons.withIndex()) {
            var page = 1
            val episodeOffset = if (isMultiSeason && seasonIdx > 0) {
                seasons.take(seasonIdx).sumOf { it.episodeCount ?: 0 }
            } else 0
            val seasonLabel = if (isMultiSeason) {
                val display = season.displayNumber?.takeIf { it.isNotBlank() } ?: (seasonIdx + 1).toString()
                "S$display"
            } else null
            while (true) {
                val seasonUrl = apiBuilder().addPathSegment("season").addPathSegment(season.contentId)
                    .addPathSegment("episodes").addQueryParameter("page", page.toString())
                    .addQueryParameter("limit", PAGE_LIMIT.toString()).addQueryParameter("order_by", "asc").build()
                val items = try {
                    val resp = client.newCall(GET(seasonUrl.toString(), headers)).awaitSuccess()
                    resp.parseJson<List<EpisodeDto>>()
                } catch (e: Exception) {
                    logE("getEpisodeList: failed season ${season.title} page $page", e)
                    break
                }
                if (items.isEmpty()) break
                for (ep in items) {
                    if (ep.isClip == true) continue
                    val epNum = ep.episodeNumber ?: ep.episode?.toFloatOrNull() ?: 0f
                    val globalEpNum = episodeOffset + epNum
                    val hasSub = ep.audioLocales?.contains("ja-JP") == true
                    val hasDub = ep.audioLocales?.contains("en-US") == true
                    val epName = buildString {
                        if (seasonLabel != null) append("$seasonLabel ")
                        append("EP ${ep.episode ?: epNum.toInt()}")
                        ep.title?.let { if (it.isNotBlank()) append(" - $it") }
                    }
                    episodes.add(SEpisode.create().apply {
                        setUrlWithoutDomain("/episode/${ep.contentId}")
                        name = epName
                        episode_number = globalEpNum
                        scanlator = when { hasSub && hasDub -> "Sub / Dub"; hasSub -> "Sub"; hasDub -> "Dub"; else -> "" }
                        preview_url = ep.image
                    })
                }
                if (items.size < PAGE_LIMIT) break
                page++
            }
        }
        return episodes.sortedByDescending { it.episode_number }
    }

    override fun episodeListRequest(anime: SAnime): Request {
        val contentId = anime.url.substringAfter("/series/").substringBefore("/")
        val url = apiBuilder().addPathSegment("series").addPathSegment(contentId).build()
        return GET(url.toString(), headers)
    }
    override fun episodeListParse(response: Response): List<SEpisode> = emptyList()

    override fun getEpisodeUrl(episode: SEpisode): String {
        val episodeId = episode.url.substringAfter("/episode/").substringBefore("?")
        return "$baseUrl/watch/$episodeId"
    }

    // ================== Video Pipeline (v16.14 Anikoto-style) ==================
    //
    // 1. API call gets master m3u8 URLs for each audio version
    // 2. We FETCH each master m3u8 using extension's OkHttpClient
    // 3. We PARSE the master to extract variant playlist URLs + quality info
    // 4. We register each variant URL with the proxy
    // 5. We return Videos pointing to VARIANT proxy URLs (not master)
    // 6. Player directly opens variant -> proxy rewrites segments -> playback

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val episodeId = episode.url.substringAfter("/episode/").substringBefore("?")
        logV("getHosterList: START episode=$episodeId")

        hlsProxy?.clearUrls()
        val proxy = ensureProxy()

        val media = try {
            val url = "$baseUrl$API/episode/$episodeId/media/dash/ja-JP"
            logV("getHosterList: fetching media API")
            val resp = client.newCall(GET(url, headers)).awaitSuccess()
            val body = resp.body!!.string()
            logV("getHosterList: API OK, len=${body.length}")
            json.decodeFromString<MediaResponseDto>(body)
        } catch (e: Exception) {
            logVE("getHosterList: media API FAILED", e)
            return emptyList()
        }

        val allVersions = mutableListOf<MediaResponseDto.HlsDto>()
        media.hls?.let { allVersions.add(it) }
        media.versions?.hls?.let { allVersions.addAll(it) }

        if (allVersions.isEmpty()) {
            logVE("getHosterList: NO versions found")
            return emptyList()
        }

        val original = allVersions.find { it.original == true }
        val englishDub = allVersions.find { it.locale == "en-US" && it.original != true }
        logV("getHosterList: original=${original?.locale}, englishDub=${englishDub?.locale}")

        val hosters = mutableListOf<Hoster>()
        val prefAudio = preferences.getString(PREF_AUDIO_KEY, "sub") ?: "sub"

        if (original != null) {
            val videos = buildVideosForVersion(proxy, original, "Sub", prefAudio == "sub")
            if (videos.isNotEmpty()) {
                hosters.add(Hoster(hosterName = "Sub (Japanese)", videoList = videos))
                logV("getHosterList: SUB hoster: ${videos.size} video(s)")
            }
        }

        if (englishDub != null) {
            val videos = buildVideosForVersion(proxy, englishDub, "Dub", prefAudio == "dub")
            if (videos.isNotEmpty()) {
                hosters.add(Hoster(hosterName = "Dub (English)", videoList = videos))
                logV("getHosterList: DUB hoster: ${videos.size} video(s)")
            }
        }

        val sorted = when (prefAudio) {
            "dub" -> hosters.sortedByDescending { it.hosterName.contains("Dub", true) }
            else -> hosters
        }

        logV("getHosterList: RETURNING ${sorted.size} hoster(s), ${sorted.sumOf { it.videoList?.size ?: 0 }} videos")
        return sorted
    }

    private fun buildVideosForVersion(
        proxy: HlsProxyServer,
        version: MediaResponseDto.HlsDto,
        audioLabel: String,
        isPreferredAudio: Boolean,
    ): List<Video> {
        val videos = mutableListOf<Video>()
        val subtitleTracks = buildSubtitleTracks(version.subtitles)

        version.playlist?.let { masterUrl ->
            videos.addAll(buildVideosFromMaster(proxy, masterUrl, audioLabel, isPreferredAudio, subtitleTracks))
        }

        for (hs in version.hardSubs.orEmpty()) {
            hs.playlist?.let { masterUrl ->
                val subLabel = localeToLabel(hs.locale)
                videos.addAll(buildVideosFromMaster(proxy, masterUrl, "$audioLabel ($subLabel)", false, emptyList()))
            }
        }

        return videos
    }

    private fun buildVideosFromMaster(
        proxy: HlsProxyServer,
        masterUrl: String,
        audioLabel: String,
        isPreferredAudio: Boolean,
        subtitleTracks: List<Track>,
    ): List<Video> {
        val masterText = fetchMasterM3u8(masterUrl)
        if (masterText != null) {
            val variants = parseMasterM3u8(masterText, masterUrl)
            if (variants.isNotEmpty()) {
                logV("buildVideos: parsed ${variants.size} variants")
                return variants.mapIndexed { index, variant ->
                    val proxyUrl = proxy.registerUrl(variant.url, isM3u8 = true)
                    val title = "$audioLabel - ${variant.qualityLabel}"
                    val preferred = isPreferredAudio && index == 0
                    logV("buildVideos: $title -> $proxyUrl")
                    makeVideo(proxyUrl, title, variant.resolution, preferred, subtitleTracks)
                }
            }
            logW("buildVideos: master OK but 0 variants (single-quality?)")
        } else {
            logW("buildVideos: failed to fetch master")
        }

        // Fallback: proxy the master URL as-is
        logV("buildVideos: FALLBACK -> proxy master as single variant")
        val proxyUrl = proxy.registerUrl(masterUrl, isM3u8 = true)
        return listOf(makeVideo(proxyUrl, "$audioLabel - Auto", null, isPreferredAudio, subtitleTracks))
    }

    private fun makeVideo(url: String, title: String, resolution: Int?, preferred: Boolean, subtitleTracks: List<Track>) = Video(
        videoUrl = url,
        videoTitle = title,
        resolution = resolution,
        bitrate = null,
        headers = null,
        preferred = preferred,
        subtitleTracks = subtitleTracks,
        audioTracks = emptyList(),
        timestamps = emptyList(),
        mpvArgs = emptyList(),
        ffmpegStreamArgs = emptyList(),
        ffmpegVideoArgs = emptyList(),
        internalData = "",
        initialized = false,
    )

    override suspend fun resolveVideo(video: Video): Video? {
        logV("resolveVideo: START title='${video.videoTitle}' url=${trunc(video.videoUrl, 150)} initialized=${video.initialized}")
        if (video.initialized) {
            logV("resolveVideo: already initialized, returning as-is")
            return video
        }
        if (video.videoUrl.isBlank()) {
            logVE("resolveVideo: blank URL -> null")
            return null
        }
        if (!video.videoUrl.contains("127.0.0.1")) {
            logVE("resolveVideo: URL is NOT a proxy URL (no 127.0.0.1) -> null. URL=${trunc(video.videoUrl, 150)}")
            return null
        }
        // Verify proxy is still running
        val proxy = hlsProxy
        if (proxy == null || !proxy.isRunning) {
            logVE("resolveVideo: proxy is null or not running! Cannot serve video.")
            return null
        }
        logV("resolveVideo: OK (proxy running at ${video.videoUrl.substringBefore("/p/")})")
        return video.copy(initialized = true)
    }

    // Stubs (getHosterList is overridden)
    override fun hosterListParse(response: Response): List<Hoster> = emptyList()
    override fun videoListParse(response: Response, hoster: Hoster): List<Video> = emptyList()
    override fun videoListParse(response: Response): List<Video> = emptyList()
    override suspend fun getVideoList(episode: SEpisode): List<Video> = getHosterList(episode).flatMap { it.videoList.orEmpty() }
    override fun seasonListParse(response: Response): List<SAnime> = emptyList()

    override fun List<Video>.sortVideos(): List<Video> {
        val prefAudio = preferences.getString(PREF_AUDIO_KEY, "sub") ?: "sub"
        return when (prefAudio) {
            "dub" -> sortedWith(compareByDescending<Video> { it.preferred }.thenByDescending { it.videoTitle.contains("Dub", true) })
            else -> sortedWith(compareByDescending<Video> { it.preferred }.thenByDescending { it.videoTitle.contains("Sub", true) })
        }
    }

    // -- Preferences -----------------------------------------------
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_AUDIO_KEY
            title = "Preferred audio"
            summary = "%s"
            entries = arrayOf("Sub (Japanese)", "Dub (English)", "Show All")
            entryValues = arrayOf("sub", "dub", "all")
            setDefaultValue("sub")
        }.let { screen.addPreference(it) }
    }

    private fun buildSubtitleTracks(subtitles: List<MediaResponseDto.SubtitleDto>?): List<Track> {
        if (subtitles.isNullOrEmpty()) return emptyList()
        return subtitles.mapNotNull { sub ->
            val url = sub.url ?: return@mapNotNull null
            Track(url, localeToLabel(sub.locale))
        }
    }

    private fun localeToLabel(locale: String): String = when (locale) {
        "en-US" -> "English"; "es-419" -> "Spanish (LATAM)"; "es-ES" -> "Spanish (Spain)"
        "pt-BR" -> "Portuguese (BR)"; "ar-SA" -> "Arabic"; "de-DE" -> "German"
        "fr-FR" -> "French"; "it-IT" -> "Italian"; "ru-RU" -> "Russian"
        "ja-JP" -> "Japanese"; "ko-KR" -> "Korean"; "zh-CN" -> "Chinese (Simplified)"
        "zh-HK" -> "Chinese (Hong Kong)"; "hi-IN" -> "Hindi"; "id-ID" -> "Indonesian"
        "th-TH" -> "Thai"; "vi-VN" -> "Vietnamese"; "pl-PL" -> "Polish"
        "ms-MY" -> "Malay"; "ta-IN" -> "Tamil"; "te-IN" -> "Telugu"
        else -> locale.substringBefore("-")
    }
}

// -- DTOs --------------------------------------------------------

@Serializable
data class SeriesDto(
    @SerialName("content_id") val contentId: String = "",
    val title: String = "",
    @SerialName("image") val image: String? = null,
    val type: String? = null,
    val subbed: Boolean? = null,
    val dubbed: Boolean? = null,
    val description: String? = null,
    val score: Double? = null,
    val studio: String? = null,
    val year: Int? = null,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
    val status: String? = null,
    @SerialName("audio_locales") val audioLocales: List<String>? = null,
    @SerialName("subtitle_locales") val subtitleLocales: List<String>? = null,
    @SerialName("first_episode") val firstEpisode: FirstEpisodeDto? = null,
    @SerialName("seasons_count") val seasonsCount: Int? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
) {
    fun toSAnime() = SAnime.create().apply {
        url = "/series/$contentId"
        title = this@SeriesDto.title.ifBlank { "Unknown" }
        thumbnail_url = image
        genre = buildString {
            when { dubbed == true && subbed == true -> append("Sub, Dub"); dubbed == true -> append("Dub"); else -> append("Sub") }
            if (!type.isNullOrBlank()) { append(", "); append(if (type.equals("movie", true)) "Movie" else "TV") }
        }
    }
}

@Serializable
data class FirstEpisodeDto(
    @SerialName("content_id") val contentId: String = "",
    @SerialName("episode_number") val episodeNumber: Double? = null,
    val title: String? = null,
)

@Serializable
data class SearchResponseDto(
    val series: List<SeriesDto> = emptyList(),
    val movies: List<SeriesDto> = emptyList(),
    @SerialName("episodes") val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class SeriesDetailDto(
    @SerialName("content_id") val contentId: String = "",
    val title: String = "",
    val description: String? = null,
    val images: List<ImageDto>? = null,
    val seasons: List<SeasonDto>? = null,
    val genre: List<GenreDto>? = null,
    val status: String? = null,
    @SerialName("audio_locales") val audioLocales: List<String>? = null,
    @SerialName("subtitle_locales") val subtitleLocales: List<String>? = null,
    @SerialName("rating_avg") val ratingAvg: Double? = null,
    @SerialName("rating_count") val ratingCount: Int? = null,
)

@Serializable
data class ImageDto(val url: String = "", val type: String = "")

@Serializable
data class SeasonDto(
    @SerialName("content_id") val contentId: String = "",
    val title: String = "",
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("season_seq_number") val seasonSeqNumber: Int? = null,
    @SerialName("display_number") val displayNumber: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("mal_id") val malId: String? = null,
)

@Serializable
data class EpisodeDto(
    @SerialName("content_id") val contentId: String = "",
    val title: String? = null,
    val episode: String? = null,
    @SerialName("episode_number") val episodeNumber: Float? = null,
    @SerialName("is_clip") val isClip: Boolean? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    val image: String? = null,
    @SerialName("image_loading") val imageLoading: String? = null,
    @SerialName("audio_locales") val audioLocales: List<String>? = null,
    @SerialName("available_date") val availableDate: String? = null,
)

@Serializable
data class MediaResponseDto(
    @SerialName("content_id") val contentId: String = "",
    val hls: HlsDto? = null,
    val versions: VersionsDto? = null,
) {
    @Serializable data class VersionsDto(val hls: List<HlsDto>? = null)
    @Serializable data class HlsDto(
        val locale: String = "",
        val playlist: String? = null,
        @SerialName("hard_subs") val hardSubs: List<HardSubDto>? = null,
        val subtitles: List<SubtitleDto>? = null,
        val original: Boolean? = null,
    )
    @Serializable data class HardSubDto(val locale: String = "", val playlist: String? = null)
    @Serializable data class SubtitleDto(val locale: String = "", val url: String? = null)
}

@Serializable
data class GenreDto(val title: String = "", val name: String = "")
