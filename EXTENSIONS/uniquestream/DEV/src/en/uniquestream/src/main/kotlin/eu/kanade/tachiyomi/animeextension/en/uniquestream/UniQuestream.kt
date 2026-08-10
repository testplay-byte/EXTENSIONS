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


class UniQuestream : AnimeHttpSource(), ConfigurableAnimeSource {

    companion object {
        private const val TAG = "UniQuestream"
        private const val API = "/api/v1"
        private const val PREF_AUDIO_KEY = "preferred_audio"
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
                        append("\nSubtitles: ${locales.joinToString(", ") { localeToLabel(it) }}")
                    }
                }
                genre = data.genre?.joinToString(", ") { it.title }
                thumbnail_url = data.images
                    ?.firstOrNull { it.type == "poster_tall" }
                    ?.url
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
            SAnime.create().apply {
                initialized = true
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
    // Matches AnimePahe/AniKoto pattern: override the suspend getEpisodeList
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
            val resp = client.newCall(GET(req.toString(), headers)).awaitSuccess()
            resp.parseJson<SeriesDetailDto>()
        } catch (e: Exception) {
            logE("getEpisodeList: failed to fetch series detail", e)
            return emptyList()
        }

        val seasons = seriesData.seasons
        if (seasons.isNullOrEmpty()) {
            logD("getEpisodeList: no seasons found for $contentId")
            return emptyList()
        }

        val isMultiSeason = seasons.size > 1
        logD("getEpisodeList: found ${seasons.size} seasons for ${seriesData.title} (multi=$isMultiSeason)")

        // 2. Fetch episodes for each season
        val episodes = mutableListOf<SEpisode>()

        for ((seasonIdx, season) in seasons.withIndex()) {
            var page = 1
            // Calculate episode number offset for multi-season shows
            // so episodes are in chronological order across seasons
            val episodeOffset = if (isMultiSeason && seasonIdx > 0) {
                seasons.take(seasonIdx).sumOf { it.episodeCount ?: 0 }
            } else 0

            // Season display label for multi-season shows
            val seasonLabel = if (isMultiSeason) {
                val display = season.displayNumber
                    ?.takeIf { it.isNotBlank() }
                    ?: (seasonIdx + 1).toString()
                "S$display"
            } else null

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
                    val resp = client.newCall(
                        GET(seasonUrl.toString(), headers)
                    ).awaitSuccess()
                    resp.parseJson<List<EpisodeDto>>()
                } catch (e: Exception) {
                    logE("getEpisodeList: failed season ${season.title} page $page", e)
                    break
                }
                if (items.isEmpty()) break

                for (ep in items) {
                    if (ep.isClip == true) continue

                    val epNum = ep.episodeNumber
                        ?: ep.episode?.toFloatOrNull()
                        ?: 0f
                    val globalEpNum = episodeOffset + epNum

                    // Determine sub/dub availability from audio_locales
                    val hasSub = ep.audioLocales?.contains("ja-JP") == true
                    val hasDub = ep.audioLocales?.contains("en-US") == true

                    // Clean episode name: just number and title (rule §8)
                    val epName = buildString {
                        if (seasonLabel != null) append("$seasonLabel ")
                        append("EP ${ep.episode ?: epNum.toInt()}")
                        ep.title?.let { if (it.isNotBlank()) append(" - $it") }
                    }

                    episodes.add(SEpisode.create().apply {
                        setUrlWithoutDomain("/episode/${ep.contentId}")
                        name = epName
                        episode_number = globalEpNum
                        // Scanlator field shows sub/dub availability (rule §8)
                        scanlator = when {
                            hasSub && hasDub -> "Sub / Dub"
                            hasSub -> "Sub"
                            hasDub -> "Dub"
                            else -> ""
                        }
                        // Episode thumbnail
                        preview_url = ep.image
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
        val episodeId = episode.url.substringAfter("/episode/").substringBefore("?")
        return "$baseUrl/watch/$episodeId"
    }

    // ════════════════════════════════════════════════════════════════
    // Video Pipeline (ext-lib 16)
    // ════════════════════════════════════════════════════════════════
    // Override getHosterList() DIRECTLY.
    // hosterListParse / videoListParse are never called.
    //
    // API discovery: A single call to /episode/{id}/media/dash/{anyLocale}
    // returns the requested locale's HLS data PLUS a versions.hls[] array
    // containing ALL other available audio versions. This means ONE API
    // call gives us everything we need — no need to call per-locale.
    //
    // The API even works with an invalid locale — it returns a default
    // version's data with the full versions.hls[] array.

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val episodeId = episode.url.substringAfter("/episode/").substringBefore("?")
        logD("getHosterList: episode=$episodeId")

        // Single API call — any locale works, versions.hls has everything
        val media = try {
            val url = "$baseUrl$API/episode/$episodeId/media/dash/ja-JP"
            val resp = client.newCall(GET(url, headers)).awaitSuccess()
            resp.parseJson<MediaResponseDto>()
        } catch (e: Exception) {
            logE("getHosterList: media API failed", e)
            return emptyList()
        }

        // Collect ALL audio versions from hls + versions.hls
        val allVersions = mutableListOf<MediaResponseDto.HlsDto>()
        media.hls?.let { allVersions.add(it) }
        media.versions?.hls?.let { allVersions.addAll(it) }

        if (allVersions.isEmpty()) {
            logE("getHosterList: no versions found")
            return emptyList()
        }

        // Find the original (Japanese) and English dub versions
        val original = allVersions.find { it.original == true }
        val englishDub = allVersions.find {
            it.locale == "en-US" && it.original != true
        }

        val hosters = mutableListOf<Hoster>()
        val prefAudio = preferences.getString(PREF_AUDIO_KEY, "sub") ?: "sub"

        // ── Build SUB hoster (original Japanese audio) ──────────────
        if (original != null) {
            val videos = mutableListOf<Video>()
            val isPreferred = (prefAudio == "sub")

            // Original playlist (no burned-in subs — player handles soft subs)
            original.playlist?.let { playlistUrl ->
                videos.add(makeVideo(playlistUrl, "Sub - Auto", isPreferred))
            }

            // Hard-sub variants (separate video streams with burned-in subtitles)
            for (hs in original.hardSubs.orEmpty()) {
                hs.playlist?.let { playlistUrl ->
                    videos.add(
                        makeVideo(
                            playlistUrl,
                            "Sub (${localeToLabel(hs.locale)})",
                            false,
                        ),
                    )
                }
            }

            if (videos.isNotEmpty()) {
                hosters.add(
                    Hoster(
                        hosterName = "Sub (Japanese)",
                        videoList = videos,
                    ),
                )
            }
        }

        // ── Build DUB hoster (English dub) ──────────────────────────
        if (englishDub != null) {
            val videos = mutableListOf<Video>()
            val isPreferred = (prefAudio == "dub")

            // English dub main playlist
            englishDub.playlist?.let { playlistUrl ->
                videos.add(makeVideo(playlistUrl, "Dub - Auto", isPreferred))
            }

            // Hard-sub variants for English dub (e.g., Spanish hard-subs on English audio)
            for (hs in englishDub.hardSubs.orEmpty()) {
                hs.playlist?.let { playlistUrl ->
                    videos.add(
                        makeVideo(
                            playlistUrl,
                            "Dub (${localeToLabel(hs.locale)})",
                            false,
                        ),
                    )
                }
            }

            if (videos.isNotEmpty()) {
                hosters.add(
                    Hoster(
                        hosterName = "Dub (English)",
                        videoList = videos,
                    ),
                )
            }
        }

        // ── Sort hosters based on user preference ───────────────────
        val sorted = when (prefAudio) {
            "dub" -> hosters.sortedByDescending { it.hosterName.contains("Dub", true) }
            else -> hosters // SUB first (default)
        }

        logD("getHosterList: returning ${sorted.size} hosters, ${sorted.sumOf { it.videoList?.size ?: 0 }} videos")
        return sorted
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
        val prefAudio = preferences.getString(PREF_AUDIO_KEY, "sub") ?: "sub"
        return when (prefAudio) {
            "dub" -> sortedWith(
                compareByDescending<Video> { it.preferred }
                    .thenByDescending { it.videoTitle.contains("Dub", true) },
            )
            else -> sortedWith(
                compareByDescending<Video> { it.preferred }
                    .thenByDescending { it.videoTitle.contains("Sub", true) },
            )
        }
    }

    // ── Preferences ──────────────────────────────────────────────────
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

    // ── Utility ──────────────────────────────────────────────────────
    private fun localeToLabel(locale: String): String = when (locale) {
        "en-US" -> "English"
        "es-419" -> "Spanish (LATAM)"
        "es-ES" -> "Spanish (Spain)"
        "pt-BR" -> "Portuguese (BR)"
        "ar-SA" -> "Arabic"
        "de-DE" -> "German"
        "fr-FR" -> "French"
        "it-IT" -> "Italian"
        "ru-RU" -> "Russian"
        "ja-JP" -> "Japanese"
        "ko-KR" -> "Korean"
        "zh-CN" -> "Chinese (Simplified)"
        "zh-HK" -> "Chinese (Hong Kong)"
        "hi-IN" -> "Hindi"
        "id-ID" -> "Indonesian"
        "th-TH" -> "Thai"
        "vi-VN" -> "Vietnamese"
        "pl-PL" -> "Polish"
        "ms-MY" -> "Malay"
        "ta-IN" -> "Tamil"
        "te-IN" -> "Telugu"
        else -> locale.substringBefore("-")
    }
}

// ── DTOs ────────────────────────────────────────────────────────────

/**
 * DTO for popular/latest list items and search results.
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
     * Does NOT set initialized = true — the app calls animeDetailsParse on click.
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
    @SerialName("display_number") val displayNumber: String? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("mal_id") val malId: String? = null,
)

/**
 * DTO for episode list from /api/v1/season/{seasonId}/episodes.
 */
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

/**
 * DTO for media response from /api/v1/episode/{epId}/media/dash/{locale}.
 *
 * Key insight: The API returns the requested locale's HLS data in `hls`,
 * AND all other audio versions in `versions.hls[]`. A single call gives
 * us every available audio version for the episode.
 */
@Serializable
data class MediaResponseDto(
    @SerialName("content_id") val contentId: String = "",
    val hls: HlsDto? = null,
    val versions: VersionsDto? = null,
) {
    @Serializable
    data class VersionsDto(
        val hls: List<HlsDto>? = null,
    )

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
