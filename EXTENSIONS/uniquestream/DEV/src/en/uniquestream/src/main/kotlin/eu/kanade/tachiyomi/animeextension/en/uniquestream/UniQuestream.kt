package eu.kanade.tachiyomi.animeextension.en.uniquestream

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale


class UniQuestream : AnimeHttpSource(), ConfigurableAnimeSource {

    companion object {
        private const val TAG = "UniQuestream"
        private const val API = "/api/v1"
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US)
    }

    override val name = "UniQuestream"
    override val baseUrl = "https://anime.uniquestream.net"
    override val lang = "en"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun apiBuilder() = "$baseUrl$API".toHttpUrl().newBuilder()

    private inline fun <reified T> Response.parseJson(): T =
        body.string().let { json.decodeFromString<T>(it) }

    private inline fun <reified T> fetchJson(request: Request): T {
        return client.newCall(request).execute().parseJson()
    }

    private fun logD(msg: String) = Log.d(TAG, msg)
    private fun logE(msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    }

    // ════════════════════════════════════════════════════════════════
    // Catalogue: Popular
    // ════════════════════════════════════════════════════════════════

    override fun popularAnimeRequest(page: Int): Request {
        val url = apiBuilder()
            .addPathSegment("videos")
            .addPathSegment("popular")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url.toString(), headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val items = response.parseJson<List<SeriesDto>>()
        logD("popularAnimeParse: got ${items.size} items")
        return AnimesPage(items.map { it.toSAnime() }, items.size >= 20)
    }

    // ════════════════════════════════════════════════════════════════
    // Catalogue: Latest
    // ════════════════════════════════════════════════════════════════

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiBuilder()
            .addPathSegment("videos")
            .addPathSegment("new")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val items = response.parseJson<List<SeriesDto>>()
        logD("latestUpdatesParse: got ${items.size} items")
        return AnimesPage(items.map { it.toSAnime() }, items.size >= 20)
    }

    // ════════════════════════════════════════════════════════════════
    // Catalogue: Search
    // ════════════════════════════════════════════════════════════════

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val typeFilter = filters.filterIsInstance<TypeFilter>().firstOrNull()
        val typeValue = typeFilter?.value ?: "all"

        val url = apiBuilder()
            .addPathSegment("search")
            .addQueryParameter("query", query)
            .addQueryParameter("t", typeValue)
            .addQueryParameter("limit", "25")
            .build()
        return GET(url.toString(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val data = response.parseJson<SearchResponseDto>()
        // Include BOTH series and movies in search results
        val allItems = data.series + data.movies
        logD("searchAnimeParse: got ${data.series.size} series + ${data.movies.size} movies")
        return AnimesPage(allItems.map { it.toSAnime() }, false)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Type"),
        TypeFilter(),
    )

    private class TypeFilter : AnimeFilter.Select<String>(
        "Type",
        arrayOf("All", "TV Shows", "Movies"),
        0,
    ) {
        val value: String
            get() = when (state) {
                0 -> "all"
                1 -> "show"
                2 -> "movie"
                else -> "all"
            }
    }

    // ════════════════════════════════════════════════════════════════
    // Details
    // ════════════════════════════════════════════════════════════════

    override fun animeDetailsRequest(anime: SAnime): Request {
        val contentId = anime.url.substringAfter("/series/").substringBefore("/")
        val url = apiBuilder()
            .addPathSegment("series")
            .addPathSegment(contentId)
            .build()
        return GET(url.toString(), headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        return try {
            val data = response.parseJson<SeriesDetailDto>()
            logD("animeDetailsParse: title=${data.title}, genres=${data.genre?.size ?: 0}")
            SAnime.create().apply {
                setUrlWithoutDomain("/series/${data.contentId}")
                title = data.title.ifBlank { "Unknown" }
                author = data.seasons
                    ?.firstOrNull()?.let { _ -> null } // No studio in detail response
                description = buildString {
                    data.description?.let { append(it) }
                    data.ratingAvg?.let {
                        if (isNotEmpty()) append("\n\n")
                        append("★ Rating: $it/5")
                        data.ratingCount?.let { count -> append(" ($count votes)") }
                    }
                    data.audioLocales?.let { locales ->
                        if (isNotEmpty()) append("\n\n")
                        append("Audio: ${locales.joinToString(", ") { localeToLabel(it) }}")
                    }
                    data.subtitleLocales?.let { locales ->
                        append("\nSubtitles: ${locales.joinToString(", ") { localeToLabel(it) }}")
                    }
                }
                genre = data.genre?.joinToString(", ") { it.title }
                thumbnail_url = data.images
                    ?.firstOrNull { it.type == "poster_tall" }
                    ?.url
                status = SAnime.UNKNOWN // Detail API has no status field
                update_strategy = AnimeUpdateStrategy.ALWAYS_UPDATE
                initialized = true
            }
        } catch (e: Exception) {
            logE("animeDetailsParse: FAILED", e)
            SAnime.create().apply {
                initialized = true // Mark as initialized even on failure to prevent retry loop
            }
        }
    }

    override fun getAnimeUrl(anime: SAnime): String {
        val contentId = anime.url.substringAfter("/series/").substringBefore("/")
        return "$baseUrl/series/$contentId"
    }

    // ════════════════════════════════════════════════════════════════
    // Episodes — override getEpisodeList (suspend) for proper async
    // ════════════════════════════════════════════════════════════════
    // Matches AnimePahe pattern: override the suspend getEpisodeList
    // so we have full control over HTTP calls in a coroutine context.
    // episodeListRequest + episodeListParse are stubbed (never called).

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        logD("getEpisodeList: START url=${anime.url}")

        val contentId = anime.url.substringAfter("/series/").substringBefore("/")

        // 1. Fetch series detail to get seasons list
        val seriesData = try {
            val req = apiBuilder()
                .addPathSegment("series")
                .addPathSegment(contentId)
                .build()
            fetchJson<SeriesDetailDto>(GET(req.toString(), headers))
        } catch (e: Exception) {
            logE("getEpisodeList: failed to fetch series detail", e)
            return emptyList()
        }

        val seasons = seriesData.seasons
        if (seasons.isNullOrEmpty()) {
            logD("getEpisodeList: no seasons found for $contentId")
            return emptyList()
        }

        logD("getEpisodeList: found ${seasons.size} seasons for ${seriesData.title}")

        // 2. Fetch episodes for each season
        val episodes = mutableListOf<SEpisode>()

        for ((seasonIdx, season) in seasons.withIndex()) {
            var page = 1
            while (true) {
                val seasonUrl = apiBuilder()
                    .addPathSegment("season")
                    .addPathSegment(season.contentId)
                    .addPathSegment("episodes")
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("limit", "100")
                    .addQueryParameter("order_by", "asc")
                    .build()

                val items = try {
                    fetchJson<List<EpisodeDto>>(GET(seasonUrl.toString(), headers))
                } catch (e: Exception) {
                    logE("getEpisodeList: failed season ${season.title} page $page", e)
                    break
                }
                if (items.isEmpty()) break

                for (ep in items) {
                    if (ep.isClip == true) continue // Skip clips
                    val epNum = ep.episodeNumber?.toFloat()
                        ?: ep.episode?.toFloatOrNull()
                        ?: 0f
                    val hasDub = ep.audioLocales?.contains("en-US") == true
                    val hasSub = ep.audioLocales?.isNotEmpty() == true

                    val epName = buildString {
                        append("EP ${ep.episode ?: epNum.toInt()}")
                        ep.title?.let { if (it.isNotBlank()) append(" - $it") }
                    }

                    episodes.add(SEpisode.create().apply {
                        setUrlWithoutDomain("/episode/${ep.contentId}")
                        name = epName
                        episode_number = epNum
                        scanlator = when {
                            hasDub && hasSub -> "SUB \u2022 DUB"
                            hasDub -> "DUB"
                            hasSub -> "SUB"
                            else -> ""
                        }
                    })
                }
                if (items.size < 100) break
                page++
            }
            logD("getEpisodeList: season ${seasonIdx + 1}/${seasons.size} '${season.title}' done")
        }

        logD("getEpisodeList: total ${episodes.size} episodes")

        // Return DESCENDING so Aniyomi displays ascending (1, 2, 3, ...)
        return episodes.sortedByDescending { it.episode_number }
    }

    // episodeListRequest + episodeListParse: stubs (getEpisodeList is overridden above)
    override fun episodeListRequest(anime: SAnime): Request {
        val contentId = anime.url.substringAfter("/series/").substringBefore("/")
        val url = apiBuilder()
            .addPathSegment("series")
            .addPathSegment(contentId)
            .build()
        return GET(url.toString(), headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> = emptyList()

    override fun getEpisodeUrl(episode: SEpisode): String {
        val episodeId = episode.url.substringAfter("/episode/")
        return "$baseUrl/watch/$episodeId"
    }

    // ════════════════════════════════════════════════════════════════
    // Video Pipeline (ext-lib 16)
    // ════════════════════════════════════════════════════════════════
    // Override getHosterList() DIRECTLY (makes HTTP call inside)
    // hosterListParse / videoListParse are never called.

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val episodeId = episode.url.substringAfter("/episode/")
        logD("getHosterList: episode=$episodeId")

        // Try Japanese audio first (original), then English (dub)
        val locales = listOf("ja-JP", "en-US")
        val videos = mutableListOf<Video>()

        for (locale in locales) {
            val url = "$baseUrl$API/episode/$episodeId/media/dash/$locale"
            try {
                val response = client.newCall(GET(url, headers)).awaitSuccess()
                val data = response.parseJson<MediaResponseDto>()
                val hls = data.hls ?: continue

                val isOriginal = locale == "ja-JP"
                val isDub = locale == "en-US"

                // Original stream
                if (hls.playlist != null) {
                    val label = if (isOriginal) "SUB - Auto" else "DUB - Auto"
                    videos.add(makeVideo(hls.playlist, label, isOriginal))
                }

                // Hard-sub variants
                for (sub in hls.hardSubs.orEmpty()) {
                    val playlist = sub.playlist ?: continue
                    val subLabel = localeToLabel(sub.locale)
                    videos.add(makeVideo(playlist, "SUB ($subLabel) - Auto", false))
                }
            } catch (e: Exception) {
                logD("getHosterList: locale=$locale failed: ${e.message}")
            }
        }

        logD("getHosterList: returning ${videos.size} videos")
        return if (videos.isNotEmpty()) {
            listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = videos))
        } else {
            emptyList()
        }
    }

    private fun makeVideo(url: String, title: String, preferred: Boolean) = Video(
        videoUrl = url,
        videoTitle = title,
        resolution = null,
        bitrate = null,
        headers = headersOf("Referer", baseUrl),
        preferred = preferred,
        subtitleTracks = emptyList(),
        audioTracks = emptyList(),
        timestamps = emptyList(),
        mpvArgs = emptyList(),
        ffmpegStreamArgs = emptyList(),
        ffmpegVideoArgs = emptyList(),
        internalData = "",
        initialized = false,
    )

    override suspend fun resolveVideo(video: Video): Video? {
        if (video.initialized) return video
        // All videos have URLs set directly, just mark as initialized
        return video.copy(initialized = true)
    }

    // hosterListParse / videoListParse — never called (getHosterList is overridden)
    override fun hosterListParse(response: Response): List<Hoster> = emptyList()
    override fun videoListParse(response: Response, hoster: Hoster): List<Video> = emptyList()

    // Legacy pipeline fallback for pre-ext-lib-16 forks
    override fun videoListParse(response: Response): List<Video> = emptyList()
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return getHosterList(episode).flatMap { it.videoList.orEmpty() }
    }

    // ── Seasons ──────────────────────────────────────────────────────
    override fun seasonListParse(response: Response): List<SAnime> = emptyList()

    // ── Sorting ──────────────────────────────────────────────────────
    override fun List<Video>.sortVideos(): List<Video> {
        val audio = preferences.getString("preferred_audio", "SUB") ?: "SUB"
        return sortedWith(
            compareByDescending<Video> { it.preferred }
                .thenByDescending { it.videoTitle.contains(audio, true) }
                .thenByDescending { it.videoTitle.contains("1080", true) }
        )
    }

    // ── Preferences ──────────────────────────────────────────────────
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        // No preferences needed for now
    }

    // ── Utility ──────────────────────────────────────────────────────
    private fun tryParseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try { DATE_FMT.parse(dateStr)?.time ?: 0L } catch (_: Exception) { 0L }
    }

    private fun localeToLabel(locale: String): String = when (locale) {
        "en-US" -> "EN"
        "es-419" -> "LATAM"
        "es-ES" -> "ES"
        "pt-BR" -> "PT-BR"
        "ar-SA" -> "AR"
        "de-DE" -> "DE"
        "fr-FR" -> "FR"
        "it-IT" -> "IT"
        "ru-RU" -> "RU"
        "ja-JP" -> "JP"
        "ko-KR" -> "KR"
        "zh-CN" -> "CN"
        "zh-HK" -> "HK"
        "hi-IN" -> "HI"
        "id-ID" -> "ID"
        "th-TH" -> "TH"
        "vi-VN" -> "VI"
        "pl-PL" -> "PL"
        "ms-MY" -> "MS"
        "ta-IN" -> "TA"
        "te-IN" -> "TE"
        else -> locale.substringBefore("-")
    }
}

// ── DTOs ────────────────────────────────────────────────────────────

/**
 * DTO for popular/latest list items and search results.
 * The popular/new API returns: content_id, title, image, image_loading, type,
 * subbed, dubbed, description, list_id, score, studio, year, episodes_total,
 * status, audio_locales, subtitle_locales, is_new_recent, previous_rank,
 * rank_change, first_episode.
 * The search API returns the same shape but many fields are null.
 */
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
    /**
     * Convert to SAnime for list display (Popular / Latest / Search).
     * ★ Does NOT set initialized = true — the app will call animeDetailsParse
     *   when the user clicks on the anime to get the full details.
     */
    fun toSAnime() = SAnime.create().apply {
        url = "/series/$contentId"
        title = this@SeriesDto.title.ifBlank { "Unknown" }
        thumbnail_url = image
        genre = buildString {
            when {
                dubbed == true && subbed == true -> append("Sub, Dub")
                dubbed == true -> append("Dub")
                else -> append("Sub")
            }
            if (!type.isNullOrBlank()) {
                append(", ")
                append(if (type.equals("movie", true)) "Movie" else "TV")
            }
        }
        // Do NOT set initialized = true here!
        // The framework checks this flag to decide whether to call animeDetailsParse.
        // If true, it skips the details fetch and uses this incomplete SAnime directly.
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

/**
 * DTO for series detail response from /api/v1/series/{contentId}.
 * Response: content_id, title, description, images[], seasons[],
 * episode{}, audio_locales[], subtitle_locales[], genre[],
 * rating_avg, rating_count.
 * NOTE: No 'status' field in this response.
 */
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
    @SerialName("episode_count") val episodeCount: Int? = null,
)

/**
 * DTO for episode list from /api/v1/season/{seasonId}/episodes.
 * Response: title, episode, is_clip, content_id, episode_number,
 * duration_ms, image, image_loading, audio_locales[].
 */
@Serializable
data class EpisodeDto(
    @SerialName("content_id") val contentId: String = "",
    val title: String? = null,
    val episode: String? = null,
    @SerialName("episode_number") val episodeNumber: Double? = null,
    @SerialName("is_clip") val isClip: Boolean? = null,
    @SerialName("audio_locales") val audioLocales: List<String>? = null,
    @SerialName("available_date") val availableDate: String? = null,
)

/**
 * DTO for media response from /api/v1/episode/{epId}/media/dash/{locale}.
 * Response: title, content_id, media_id, dash, hls, versions, etc.
 */
@Serializable
data class MediaResponseDto(
    @SerialName("content_id") val contentId: String = "",
    val hls: HlsDto? = null,
) {
    @Serializable
    data class HlsDto(
        val locale: String = "",
        val playlist: String? = null,
        @SerialName("hard_subs") val hardSubs: List<HardSubDto>? = null,
        val subtitles: List<SubtitleDto>? = null,
        val original: Boolean? = null,
    )

    @Serializable
    data class HardSubDto(val locale: String = "", val playlist: String? = null)

    @Serializable
    data class SubtitleDto(val locale: String = "", val url: String? = null)
}

@Serializable
data class GenreDto(val title: String = "", val name: String = "")
