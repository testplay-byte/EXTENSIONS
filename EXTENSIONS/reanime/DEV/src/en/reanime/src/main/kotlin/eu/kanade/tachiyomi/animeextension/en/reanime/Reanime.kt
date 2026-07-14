package eu.kanade.tachiyomi.animeextension.en.reanime

import android.app.Application
import eu.kanade.tachiyomi.animeextension.en.reanime.extractor.ReanimeExtractor
import eu.kanade.tachiyomi.animeextension.en.reanime.extractor.WebViewFetcher
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import androidx.preference.PreferenceScreen
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

/**
 * Re:ANIME — Aniyomi anime extension (ext-lib 16).
 *
 * Site: https://reanime.to (SvelteKit SSR frontend on /api/v1/ REST API).
 * Video host: flixcloud.cc (HLS via single-use /api/m3u8/<token>).
 *
 * Both reanime.to and flixcloud.cc are Cloudflare-Turnstile-protected.
 * The extension uses the inherited client (CloudflareInterceptor) for API
 * requests, and a WebView for flixcloud.cc video extraction (m3u8 interception).
 *
 * API endpoints:
 * - GET /api/v1/search?q=&limit=&offset=&year=&season=&format=  (public catalog)
 * - GET /api/v1/anime/<anime_id>/episodes?limit=2000             (episode list)
 * - GET /api/flix/<anilist_id>/<ep>                              (server list)
 * - flixcloud.cc/e/<code>?v=<N>                                  (video embed)
 * - flixcloud.cc/api/m3u8/<single-use-token>                     (HLS master)
 */
class Reanime : ConfigurableAnimeSource, AnimeHttpSource() {

    override val name = "Re:ANIME 180"
    override val baseUrl = "https://reanime.to"
    override val lang = "en"

    private val json = Json { ignoreUnknownKeys = true }

    private val settings = reanimeSettings

    // WebView fetcher — lazy (only created when video extraction is needed)
    private val webViewFetcher: WebViewFetcher by lazy {
        WebViewFetcher(
            Injekt.get<Application>(),
            client,
            headers,
        )
    }

    private val videoExtractor: ReanimeExtractor by lazy {
        ReanimeExtractor(client, headers, json, webViewFetcher)
    }

    // ── Headers ────────────────────────────────────────────────────────
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("User-Agent", DESKTOP_UA)

    private inline fun <reified T> Response.parseJson(): T {
        val bodyStr = use { it.body?.string().orEmpty() }
        return json.decodeFromString<T>(bodyStr)
    }

    // ════════════════════════════════════════════════════════════════════
    // Popular
    // ════════════════════════════════════════════════════════════════════

    override fun popularAnimeRequest(page: Int): Request {
        // Empty q= returns the popular/trending list
        val offset = (page - 1) * PAGE_SIZE
        return GET("$baseUrl/api/v1/search?limit=$PAGE_SIZE&offset=$offset", headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage = parseSearch(response)

    // ════════════════════════════════════════════════════════════════════
    // Latest
    // ════════════════════════════════════════════════════════════════════

    override fun latestUpdatesRequest(page: Int): Request {
        // reanime.to's public API doesn't support sort=latest. Use the popular list
        // (same as popular — the homepage's "Latest Episodes" section is SSR'd).
        val offset = (page - 1) * PAGE_SIZE
        return GET("$baseUrl/api/v1/search?limit=$PAGE_SIZE&offset=$offset", headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage = parseSearch(response)

    // ════════════════════════════════════════════════════════════════════
    // Search
    // ════════════════════════════════════════════════════════════════════

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = ReanimeFilters.getSearchParams(filters)
        val offset = (page - 1) * PAGE_SIZE
        val url = buildString {
            append("$baseUrl/api/v1/search?limit=$PAGE_SIZE&offset=$offset")
            if (query.isNotBlank()) {
                append("&q=").append(URLEncoder.encode(query, "UTF-8"))
            }
            if (params.year.isNotBlank()) append("&year=").append(params.year)
            if (params.season.isNotBlank()) append("&season=").append(params.season)
            if (params.format.isNotBlank()) append("&format=").append(params.format)
        }
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = parseSearch(response)

    private fun parseSearch(response: Response): AnimesPage {
        val parsed = response.parseJson<SearchResponse>()
        val animeList = parsed.results.map { result ->
            SAnime.create().apply {
                title = pickTitle(result.title)
                thumbnail_url = result.cover_image?.large?.ifBlank { null }
                    ?: result.cover_image?.medium?.ifBlank { null }
                    ?: result.cover_image?.extra_large?.ifBlank { null }
                url = "/anime/${result.anime_id}"
                initialized = false
            }
        }
        val hasNext = animeList.size == PAGE_SIZE
        return AnimesPage(animeList, hasNext)
    }

    // ════════════════════════════════════════════════════════════════════
    // Filters
    // ════════════════════════════════════════════════════════════════════

    override fun getFilterList(): AnimeFilterList = ReanimeFilters.FILTER_LIST

    // ════════════════════════════════════════════════════════════════════
    // Anime Details
    // ════════════════════════════════════════════════════════════════════

    override fun animeDetailsRequest(anime: SAnime): Request {
        return GET("$baseUrl${anime.url}", headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val body = response.body?.string().orEmpty()
        val doc = Jsoup.parse(body)
        val bodyText = doc.body().text()

        return SAnime.create().apply {
            // Title from h1
            title = doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: doc.selectFirst("h2")?.text()?.trim() ?: ""

            // Genres — known genre names found as link text
            genre = doc.select("a[href]").eachText()
                .filter { it in KNOWN_GENRES }
                .distinct()
                .joinToString(", ")
                .ifBlank { null }

            // Synopsis — the longest text block on the page (typically the description paragraph)
            description = extractSynopsis(doc, bodyText)

            // Status — search for "Finished" or "Releasing" in the text
            status = when {
                bodyText.contains("\\bFinished\\b".toRegex()) -> SAnime.COMPLETED
                bodyText.contains("\\bReleasing\\b".toRegex()) -> SAnime.ONGOING
                bodyText.contains("Not Yet Aired", true) -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }

            // Studios — first link after the "Studios" heading
            author = extractStudios(doc)

            initialized = true
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Episodes
    // ════════════════════════════════════════════════════════════════════

    override fun episodeListRequest(anime: SAnime): Request {
        val animeId = extractAnimeId(anime.url)
        return GET("$baseUrl/api/v1/anime/$animeId/episodes?limit=2000", headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        // ★ Fallback: only called by forks that don't use getEpisodeList().
        // Without the anilist_id (which requires the anime's thumbnail_url),
        // video extraction won't work. Standard Aniyomi/Animiru uses
        // getEpisodeList() which encodes the anilist_id in the episode URL.
        val animeId = extractAnimeIdFromUrl(response.request.url.toString())
        val result = response.parseJson<EpisodesResponse>()
        if (result.data.isEmpty()) return emptyList()

        val audioType = settings.preferredAudio
        return result.data.map { ep ->
            SEpisode.create().apply {
                episode_number = ep.episode_number.toFloat()
                name = formatEpisodeName(ep)
                scanlator = buildScanlator(ep)
                url = "/watch/$animeId?ep=${ep.episode_number}&lang=$audioType"
                date_upload = parseDate(ep.aired)
            }
        }.sortedByDescending { it.episode_number }
    }

    /**
     * ★ Override getEpisodeList to extract the AniList ID from the anime's
     * thumbnail URL (bx<id> pattern) and encode it in episode URLs.
     */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        // Pre-warm the WebView (hides cold start during video extraction)
        try { webViewFetcher.warmUp() } catch (e: Exception) {
            ReanimeLog.w("getEpisodeList: WebView warmUp failed — ${e.message}")
        }

        // Extract anilist_id from thumbnail_url (bx<id> pattern in AniList CDN URLs)
        val anilistId = extractAnilistId(anime.thumbnail_url)
        val animeId = extractAnimeId(anime.url)
        ReanimeLog.i("getEpisodeList: animeId=$animeId, anilistId=$anilistId, thumb=${ReanimeLog.trunc(anime.thumbnail_url ?: "", 80)}")

        // Fetch episodes
        val episodesResponse = client.newCall(episodeListRequest(anime)).execute()
        val result = episodesResponse.use { it.parseJson<EpisodesResponse>() }
        if (result.data.isEmpty()) return emptyList()

        val audioType = settings.preferredAudio

        return result.data.map { ep ->
            SEpisode.create().apply {
                episode_number = ep.episode_number.toFloat()
                name = formatEpisodeName(ep)
                scanlator = buildScanlator(ep)
                // ★ Encode metadata: /watch/<anime_id>?ep=<N>&lang=<audio>#<anilist_id>
                url = "/watch/$animeId?ep=${ep.episode_number}&lang=$audioType#$anilistId"
                date_upload = parseDate(ep.aired)
            }
        }.sortedByDescending { it.episode_number }
    }

    // ════════════════════════════════════════════════════════════════════
    // Video pipeline
    // ════════════════════════════════════════════════════════════════════

    override fun hosterListParse(response: Response): List<Hoster> = emptyList()

    @Suppress("DEPRECATION", "overriding_deprecated")
    override fun videoListParse(response: Response): List<Video> = emptyList()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        return try {
            // Decode episode metadata from the URL
            // url = /watch/<anime_id>?ep=<N>&lang=<sub|dub>#<anilist_id>
            val url = episode.url
            val path = url.substringBefore("#")
            val anilistId = url.substringAfter("#", "")
            val animeId = path.substringAfter("/watch/").substringBefore("?")
            val epStr = path.substringAfter("ep=").substringBefore("&")
            val langType = path.substringAfter("lang=", "").substringBefore("#")
            val epNumber = epStr.toIntOrNull() ?: 1

            ReanimeLog.i("getHosterList: animeId=$animeId, anilistId=$anilistId, ep=$epNumber, lang=$langType")

            if (anilistId.isBlank()) {
                ReanimeLog.w("getHosterList: missing anilist_id — cannot fetch video sources")
                return emptyList()
            }

            val videos = videoExtractor.extractVideos(
                anilistId = anilistId,
                epNumber = epNumber,
                audioType = langType.ifBlank { settings.preferredAudio },
                enableHD1 = settings.enableHD1,
                enableHD2 = settings.enableHD2,
                webviewTimeout = settings.webviewTimeout,
            )

            if (videos.isEmpty()) {
                ReanimeLog.w("getHosterList: no videos extracted")
                return emptyList()
            }

            // Sort: preferred quality first, then by quality label
            val sortedVideos = videos.sortedWith(
                compareByDescending<Video> {
                    it.videoTitle.contains(settings.preferredQuality, true)
                }.thenByDescending { it.videoTitle }
            )

            ReanimeLog.i("getHosterList: returning ${sortedVideos.size} videos")
            listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = sortedVideos))
        } catch (e: Exception) {
            ReanimeLog.e("getHosterList: CRASHED — ${e.message}", e)
            emptyList()
        }
    }

    /** Fork compatibility — legacy getVideoList delegates to getHosterList. */
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return try {
            getHosterList(episode).flatMap { it.videoList ?: emptyList() }
        } catch (e: Exception) {
            ReanimeLog.e("getVideoList legacy fallback failed", e)
            emptyList()
        }
    }

    override fun seasonListParse(response: Response): List<SAnime> =
        throw UnsupportedOperationException("Re:ANIME has no seasons concept")

    // ════════════════════════════════════════════════════════════════════
    // "Open in WebView" URLs
    // ════════════════════════════════════════════════════════════════════

    override fun getEpisodeUrl(episode: SEpisode): String {
        val path = episode.url.substringBefore("#")
        return "$baseUrl$path"
    }

    override fun getAnimeUrl(anime: SAnime): String {
        return "$baseUrl${anime.url}"
    }

    // ════════════════════════════════════════════════════════════════════
    // Settings
    // ════════════════════════════════════════════════════════════════════

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        settings.setupPreferenceScreen(screen)
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    /** Pick the best available title. */
    private fun pickTitle(title: Title): String {
        return title.english?.takeIf { it.isNotBlank() }
            ?: title.romaji?.takeIf { it.isNotBlank() }
            ?: title.native?.takeIf { it.isNotBlank() }
            ?: "Unknown"
    }

    /** Extract the anime_id from a stored URL path "/anime/<anime_id>". */
    private fun extractAnimeId(url: String): String {
        return url.substringAfter("/anime/").substringBefore("/").substringBefore("?").substringBefore("#")
    }

    /** Extract anime_id from a full API URL (for episodeListParse fallback). */
    private fun extractAnimeIdFromUrl(url: String): String {
        return url.substringAfter("/api/v1/anime/").substringBefore("/")
    }

    /**
     * Extract the AniList ID from an AniList CDN cover URL.
     * URLs look like: https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/bx20-xxx.jpg
     * The ID is the number after "bx".
     */
    private fun extractAnilistId(thumbnailUrl: String?): String {
        if (thumbnailUrl.isNullOrBlank()) return ""
        val regex = Regex("/bx(\\d+)-")
        return regex.find(thumbnailUrl)?.groupValues?.getOrNull(1) ?: ""
    }

    /** Parse the site's status string into an SAnime status constant. */
    private fun parseStatus(status: String?): Int {
        return when (status?.lowercase()) {
            "finished" -> SAnime.COMPLETED
            "releasing" -> SAnime.ONGOING
            "not yet aired" -> SAnime.ONGOING
            "cancelled" -> SAnime.CANCELLED
            "hiatus" -> SAnime.ON_HIATUS
            else -> SAnime.UNKNOWN
        }
    }

    /** Extract the synopsis from the details page HTML. */
    private fun extractSynopsis(doc: org.jsoup.nodes.Document, bodyText: String): String? {
        // The synopsis is the longest paragraph-like text block on the page
        // that doesn't contain navigation text
        val candidates = doc.select("p, div, span")
        var best: String? = null
        var bestLen = 0
        for (el in candidates) {
            val text = el.text().trim()
            if (text.length > bestLen && text.length > 80 &&
                !text.contains("Watch Now") &&
                !text.contains("Alternative Titles") &&
                !text.contains("Add to List") &&
                !text.contains("Sign In") &&
                !text.contains("RELATED SEASONS")
            ) {
                best = text
                bestLen = text.length
            }
        }
        return best
    }

    /** Extract the first studio from the details page. */
    private fun extractStudios(doc: org.jsoup.nodes.Document): String? {
        // Find the "Studios" heading and get the links after it
        val studiosHeading = doc.select("h2, h3").firstOrNull { it.text().trim() == "Studios" }
        return studiosHeading?.parent()?.select("a")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Format the episode name from the episode data. */
    private fun formatEpisodeName(ep: Episode): String {
        val num = ep.episode_number
        val title = ep.title.takeIf { it.isNotBlank() && it != "Episode $num" }
        return if (title != null) {
            "EP $num - $title"
        } else {
            "EP $num"
        }
    }

    /** Build the scanlator field (shows sub/dub availability). */
    private fun buildScanlator(ep: Episode): String? {
        // We don't have per-episode sub/dub info from the episodes API,
        // so just show the preferred audio type
        return settings.preferredAudio.uppercase() // "SUB" or "DUB"
    }

    /** Parse an ISO date string to epoch millis. */
    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.LocalDate.parse(dateStr.substringBefore("T"))
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (e2: Exception) { 0L }
        }
    }

    companion object {
        private const val PAGE_SIZE = 40
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        /** Known anime genres for HTML parsing of the details page. */
        private val KNOWN_GENRES = setOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
            "Mecha", "Mystery", "Psychological", "Romance", "Sci-Fi",
            "Slice of Life", "Sports", "Supernatural", "Thriller", "Ecchi",
            "Harem", "Isekai", "Mahou Shoujo", "Music", "School", "Seinen",
            "Shoujo", "Shounen", "Slice-of-Life", "Space", "Vampire",
            "Yaoi", "Yuri", "Police", "Samurai", "Military", "Historical",
            "Parody", "Demons", "Magic", "Super Power", "Martial Arts",
            "Kids", "Josei", "Game", "Cars", "Dementia", "Gender Bender",
        )
    }
}
