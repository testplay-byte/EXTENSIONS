package eu.kanade.tachiyomi.animeextension.en.uniquestream

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale


class UniQuestream : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "UniQuestream"
    override val baseUrl = "https://anime.uniquestream.net"
    override val lang = "en"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val preferences by getPreferencesLazy()

    companion object {
        private const val API = "/api/v1"
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US)
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun apiBuilder() = "$baseUrl$API".toHttpUrl().newBuilder()

    private inline fun <reified T> Response.parseJson(): T =
        body.string().let { json.decodeFromString<T>(it) }

    private inline fun <reified T> fetchJson(request: Request): T {
        return client.newCall(request).execute().parseJson()
    }

    // ── Catalogue: Popular ───────────────────────────────────────────

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
        return AnimesPage(items.map { it.toSAnime() }, items.size >= 20)
    }

    // ── Catalogue: Latest ────────────────────────────────────────────

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
        return AnimesPage(items.map { it.toSAnime() }, items.size >= 20)
    }

    // ── Catalogue: Search ────────────────────────────────────────────

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
        return AnimesPage(data.series.map { it.toSAnime() }, false)
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

    // ── Details ──────────────────────────────────────────────────────

    override fun animeDetailsParse(response: Response): SAnime {
        val data = response.parseJson<SeriesDetailDto>()
        return SAnime.create().apply {
            url = "/series/${data.contentId}"
            title = data.title
            description = data.description ?: ""
            genre = data.genre?.joinToString(", ") { it.title }
            thumbnail_url = data.images
                ?.firstOrNull { it.type == "poster_tall" }
                ?.url
            status = when {
                data.status == "finished" || data.status == "Finished" -> SAnime.COMPLETED
                data.status == "airing" || data.status == "Airing" -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }
            update_strategy = AnimeUpdateStrategy.ALWAYS_UPDATE
            initialized = true
        }
    }

    override fun getAnimeUrl(anime: SAnime): String = "$baseUrl/series/${anime.url.substringAfter("/series/")}"

    // ── Episodes ─────────────────────────────────────────────────────

    override fun episodeListRequest(anime: SAnime): Request {
        val contentId = anime.url.substringAfter("/series/").substringBefore("/")
        val url = apiBuilder()
            .addPathSegment("series")
            .addPathSegment(contentId)
            .build()
        return GET(url.toString(), headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val seriesData = response.parseJson<SeriesDetailDto>()
        val seasons = seriesData.seasons ?: return emptyList()
        val episodes = mutableListOf<SEpisode>()

        for (season in seasons) {
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
                } catch (_: Exception) {
                    break
                }
                if (items.isEmpty()) break

                for (ep in items) {
                    val hasDub = ep.audioLocales?.contains("en-US") == true
                    episodes.add(SEpisode.create().apply {
                        url = "/episode/${ep.contentId}"
                        name = ep.title ?: "EP ${ep.episode ?: ep.episodeNumber?.toInt() ?: "?"}"
                        episode_number = ep.episodeNumber?.toFloat() ?: ep.episode?.toFloatOrNull() ?: 0f
                        date_upload = tryParseDate(ep.availableDate)
                        scanlator = if (hasDub) "SUB \u2022 DUB" else "SUB"
                    })
                }
                if (items.size < 100) break
                page++
            }
        }
        // Return DESCENDING so Aniyomi displays ascending (latest first → reversed to 1, 2, 3…)
        return episodes.sortedByDescending { it.episode_number }
    }

    override fun getEpisodeUrl(episode: SEpisode): String {
        val episodeId = episode.url.substringAfter("/episode/")
        return "$baseUrl/watch/$episodeId"
    }

    // ── Video Pipeline (ext-lib 16: hoster-based) ────────────────────

    override fun hosterListRequest(episode: SEpisode): Request {
        val episodeId = episode.url.substringAfter("/episode/")
        val url = "$baseUrl$API/episode/$episodeId/media/dash/ja-JP"
        return GET(url, headers)
    }

    override fun hosterListParse(response: Response): List<Hoster> {
        val data = response.parseJson<MediaResponseDto>()
        val hls = data.hls ?: return emptyList()
        val videos = mutableListOf<Video>()
        val videoHeaders = headersOf("Referer", baseUrl)

        // Original stream (raw audio — SUB)
        if (hls.playlist != null) {
            videos.add(
                Video(
                    videoUrl = hls.playlist,
                    videoTitle = "SUB - Auto",
                    resolution = null,
                    bitrate = null,
                    headers = videoHeaders,
                    preferred = true,
                    subtitleTracks = emptyList(),
                    audioTracks = emptyList(),
                    timestamps = emptyList(),
                    mpvArgs = emptyList(),
                    ffmpegStreamArgs = emptyList(),
                    ffmpegVideoArgs = emptyList(),
                    internalData = "",
                    initialized = false,
                ),
            )
        }

        // Hard-sub variants (other subtitle languages)
        for (sub in hls.hardSubs.orEmpty()) {
            val locale = localeToLabel(sub.locale)
            val playlist = sub.playlist ?: continue
            videos.add(
                Video(
                    videoUrl = playlist,
                    videoTitle = "SUB ($locale) - Auto",
                    resolution = null,
                    bitrate = null,
                    headers = videoHeaders,
                    preferred = false,
                    subtitleTracks = emptyList(),
                    audioTracks = emptyList(),
                    timestamps = emptyList(),
                    mpvArgs = emptyList(),
                    ffmpegStreamArgs = emptyList(),
                    ffmpegVideoArgs = emptyList(),
                    internalData = "",
                    initialized = false,
                ),
            )
        }

        // DUB variant (lazy — fetched via resolveVideo)
        val originalLocale = hls.locale
        if (originalLocale != "en-US" && originalLocale != "en") {
            videos.add(
                Video(
                    videoUrl = "",
                    videoTitle = "DUB - Auto",
                    resolution = null,
                    bitrate = null,
                    headers = videoHeaders,
                    preferred = false,
                    subtitleTracks = emptyList(),
                    audioTracks = emptyList(),
                    timestamps = emptyList(),
                    mpvArgs = emptyList(),
                    ffmpegStreamArgs = emptyList(),
                    ffmpegVideoArgs = emptyList(),
                    internalData = "dub:${data.contentId}",
                    initialized = false,
                ),
            )
        }

        return listOf(
            Hoster(
                hosterUrl = "",
                hosterName = "HLS",
                videoList = videos,
            ),
        )
    }

    // videoListParse is not called since we pre-fill videoList on the Hoster
    override fun videoListParse(response: Response, hoster: Hoster): List<Video> {
        return hoster.videoList ?: emptyList()
    }

    override suspend fun resolveVideo(video: Video): Video? {
        if (video.initialized) return video
        if (video.videoUrl.isNotEmpty()) {
            return video.copy(initialized = true)
        }
        // Lazy DUB resolution
        if (video.internalData.startsWith("dub:")) {
            val contentId = video.internalData.removePrefix("dub:")
            val url = "$baseUrl$API/episode/$contentId/media/dash/en-US"
            return try {
                val resp = client.newCall(GET(url, headers)).awaitSuccess()
                val data = resp.parseJson<MediaResponseDto>()
                val playlist = data.hls?.playlist
                if (playlist != null) {
                    video.copy(
                        videoUrl = playlist,
                        initialized = true,
                    )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    // ── Legacy pipeline fallback (fork compatibility) ────────────────
    // Pre-ext-lib-16 forks call getVideoList(episode) directly.
    // Delegate to the hoster pipeline so both paths work.
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return getHosterList(episode).flatMap { it.videoList.orEmpty() }
    }

    // ── Seasons (not used — flat episode list) ───────────────────────

    override fun seasonListParse(response: Response): List<SAnime> {
        return emptyList()
    }

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
        // TODO: audio/quality preferences
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
        else -> locale.substringBefore("-")
    }
}

// ── DTOs ────────────────────────────────────────────────────────────

@Serializable
data class SeriesDto(
    @SerialName("content_id") val contentId: String = "",
    val title: String = "",
    @SerialName("image") val image: String? = null,
    val type: String? = null,
    val subbed: Boolean? = null,
    val dubbed: Boolean? = null,
) {
    fun toSAnime() = SAnime.create().apply {
        url = "/series/$contentId"
        this.title = title
        thumbnail_url = image
        genre = when {
            dubbed == true && subbed == true -> "Sub, Dub"
            dubbed == true -> "Dub"
            else -> "Sub"
        }
        initialized = true
    }
}

@Serializable
data class SearchResponseDto(
    val series: List<SeriesDto> = emptyList(),
    val movies: List<SeriesDto> = emptyList(),
    val episodes: List<EpisodeDto> = emptyList(),
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
)

@Serializable
data class ImageDto(val url: String = "", val type: String = "")

@Serializable
data class SeasonDto(
    @SerialName("content_id") val contentId: String = "",
    val title: String = "",
    @SerialName("episode_count") val episodeCount: Int? = null,
)

@Serializable
data class EpisodeDto(
    @SerialName("content_id") val contentId: String = "",
    val title: String? = null,
    val episode: String? = null,
    @SerialName("episode_number") val episodeNumber: Double? = null,
    @SerialName("audio_locales") val audioLocales: List<String>? = null,
    @SerialName("available_date") val availableDate: String? = null,
)

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
    )

    @Serializable
    data class HardSubDto(val locale: String = "", val playlist: String? = null)
}

@Serializable
data class GenreDto(val title: String = "", val name: String = "")
