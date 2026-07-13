package eu.kanade.tachiyomi.animeextension.en.animepahe

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import eu.kanade.tachiyomi.animeextension.en.animepahe.dto.AiringAnimeDto
import eu.kanade.tachiyomi.animeextension.en.animepahe.dto.EpisodeDto
import eu.kanade.tachiyomi.animeextension.en.animepahe.dto.ResponseDto
import eu.kanade.tachiyomi.animeextension.en.animepahe.dto.SearchResultDto
import eu.kanade.tachiyomi.animeextension.en.animepahe.extractor.KwikExtractor
import eu.kanade.tachiyomi.animeextension.en.animepahe.metadata.EpisodeMetadataFetcher
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * AnimePahe — Aniyomi anime extension (ext-lib 16).
 *
 * Site: https://animepahe.pw
 * Structure: JSON API (`/api?m=...`) for popular/search/episodes; HTML browse pages for filters.
 *
 * ★ Quirk: animepahe does NOT provide permanent anime URLs. `/a/<animeId>` redirects to a
 * session-based `/anime/<session>` URL. The session changes, so we store the animeId in
 * [SAnime.url] (`/a/<id>`) and resolve the session on-demand in [animeDetailsRequest] and
 * [getEpisodeList] via [fetchSession].
 *
 * ★ Anti-bot: Cloudflare + DDoS-Guard. The inherited `client` (from `network.client`) has a
 * CloudflareInterceptor that handles the challenge on-device (via WebView). Raw HTTP clients
 * (curl, headless browsers) are blocked — verification happens on-device.
 *
 * ★ Episode metadata enrichment (ported from AniKoto sessions 35-38):
 * Overriding [getEpisodeList] to fetch the detail page, extract the MAL ID from external links,
 * then enrich episodes with thumbnails + titles + descriptions from Jikan + AniList + Anikage + Kitsu.
 * Uses [WebViewFetcher] for AniList/Kitsu (Cloudflare-protected). Respects 3 user toggles.
 *
 * ★ Episode URL uses fork-compat encoding (`/watch/<animeSession>/ep-<N>#<episodeSession>`)
 * so legacy-pipeline forks don't DNS-error.
 *
 * Version history:
 * - v16.1 (versionCode 1, versionId 1): popular, search, filters, details, episodes + metadata enrichment.
 *   Video extraction (Step 4) deferred — [videoListParse] returns empty for now.
 *
 * Reference (for understanding, NOT copied): SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/animepahe/
 */
class AnimePahe : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "AnimePahe 180"
    override val baseUrl by lazy { settings.preferredDomain }
    override val lang = "en"
    override val supportsLatest = true
    override val versionId = 1

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── Preferences + Settings ────────────────────────────────────────
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    private val settings: AnimepaheSettings by lazy { AnimepaheSettings(preferences) }

    // ── WebView + Metadata ────────────────────────────────────────────
    private val webViewFetcher: WebViewFetcher by lazy {
        // ★ Use the default data:text/html origin (NOT the site URL — causes CSP block).
        // See FEATURES/episode-metadata-enrichment.md §okhttp-first-webview-fallback.
        WebViewFetcher(Injekt.get<Application>())
    }

    private val metadataFetcher: EpisodeMetadataFetcher by lazy {
        EpisodeMetadataFetcher(client, json, webViewFetcher)
    }

    private val kwikExtractor: KwikExtractor by lazy {
        KwikExtractor(client, headers, webViewFetcher)
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    /** ★ Show a toast notification to the user (on main thread). Non-blocking + never throws. */
    private suspend fun showToast(message: String) {
        try {
            val app = Injekt.get<Application>()
            withContext(Dispatchers.Main) {
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            AnimepaheLog.e("showToast: failed to show toast", e)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Popular + Latest — both use /api?m=airing (the only browse endpoint).
    // ★ animepahe has NO dedicated "popular" or "latest" API — only "airing"
    //   (currently-airing anime). Both tabs show the same airing list.
    //   The user considers these "latest" rather than "popular", so the Latest
    //   tab is the primary browse entry point. Popular is the same data (fallback).
    // ════════════════════════════════════════════════════════════════════
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/api?m=airing&page=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage = parseAiringList(response)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api?m=airing&page=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = parseAiringList(response)

    private fun parseAiringList(response: Response): AnimesPage {
        val data = json.decodeFromString<ResponseDto<AiringAnimeDto>>(response.body.string())
        val anime = data.items.map { dto ->
            SAnime.create().apply {
                title = dto.title
                thumbnail_url = dto.snapshot
                artist = dto.fansub.takeIf { it.isNotBlank() }
                setUrlWithoutDomain("/a/${dto.id}")
            }
        }
        val hasNext = data.currentPage < data.lastPage
        return AnimesPage(anime, hasNext)
    }

    // ════════════════════════════════════════════════════════════════════
    // Search — if query: JSON API /api?m=search&q=...
    //          if no query + filter: HTML browse page
    // ════════════════════════════════════════════════════════════════════
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            return GET("$baseUrl/api?m=search&q=$encoded", headers)
        }

        val genre = filters.filterIsInstance<Filters.GenresFilter>().firstOrNull()
        val demographic = filters.filterIsInstance<Filters.DemographicFilter>().firstOrNull()
        val theme = filters.filterIsInstance<Filters.ThemeFilter>().firstOrNull()
        val year = filters.filterIsInstance<Filters.YearFilter>().firstOrNull()
        val season = filters.filterIsInstance<Filters.SeasonFilter>().firstOrNull()

        return when {
            genre != null && !genre.isDefault() ->
                GET("$baseUrl/anime/genre/${genre.toUriPart()}", headers)
            demographic != null && !demographic.isDefault() ->
                GET("$baseUrl/anime/demographic/${demographic.toUriPart()}", headers)
            theme != null && !theme.isDefault() ->
                GET("$baseUrl/anime/theme/${theme.toUriPart()}", headers)
            year != null && !year.isDefault() && season != null ->
                GET("$baseUrl/anime/season/${season.toUriPart()}-${year.toUriPart()}", headers)
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val url = response.request.url
        val pathSegments = url.pathSegments

        return when {
            pathSegments.contains("api") && url.queryParameter("m") == "search" -> {
                val data = json.decodeFromString<ResponseDto<SearchResultDto>>(response.body.string())
                val anime = data.items.map { dto ->
                    SAnime.create().apply {
                        title = dto.title
                        thumbnail_url = dto.poster
                        setUrlWithoutDomain("/a/${dto.id}")
                    }
                }
                AnimesPage(anime, false)
            }
            pathSegments.contains("anime") -> {
                val doc = response.asJsoup()
                val entries = doc.select("div.index div > a").mapNotNull { a ->
                    a.attr("href").takeIf { it.isNotBlank() }?.let { href ->
                        SAnime.create().apply {
                            setUrlWithoutDomain(href)
                            title = a.ownText()
                        }
                    }
                }
                AnimesPage(entries, false)
            }
            else -> popularAnimeParse(response)
        }
    }

    // (Latest implementation moved above — shares parseAiringList with Popular)

    // ════════════════════════════════════════════════════════════════════
    // Anime details — HTML page at /anime/<session> (reached via /a/<id> redirect)
    // ════════════════════════════════════════════════════════════════════
    override fun animeDetailsRequest(anime: SAnime): Request {
        val animeId = animeIdRegex.find(anime.url)?.groupValues?.get(1)
        return if (animeId != null) {
            GET("$baseUrl/a/$animeId", headers)
        } else {
            GET("$baseUrl${anime.url}", headers)
        }
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        return SAnime.create().apply {
            title = doc.selectFirst("div.title-wrapper > h1 > span")!!.text()
            author = doc.selectFirst("div.col-sm-4.anime-info p:contains(Studios:)")
                ?.text()?.replace("Studios: ", "")
                ?.takeIf { it.isNotBlank() }
            doc.selectFirst("div.col-sm-4.anime-info p:contains(Status:) a")?.text()
                ?.let { status = parseStatus(it) }
            thumbnail_url = doc.selectFirst("div.anime-poster a")?.attr("href")
            genre = doc.select(
                "div.anime-genre ul li, " +
                    "div.col-sm-4.anime-info p:contains(Demographic:) a, " +
                    "div.col-sm-4.anime-info p:contains(Theme:) a",
            ).joinToString { it.text() }.takeIf { it.isNotBlank() }
            description = buildString {
                append(doc.select("div.anime-summary").text())
                listOf("Synonyms:", "Japanese:", "Aired:", "Season:").forEach { label ->
                    doc.selectFirst("div.col-sm-4.anime-info p:contains($label)")?.text()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { append("\n\n$it") }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Episodes — override getEpisodeList to: resolve session → fetch API → enrich with metadata
    // ════════════════════════════════════════════════════════════════════
    /**
     * ★ Overrides the default getEpisodeList to:
     * 1. Fetch the detail page (follows /a/<id> redirect → /anime/<session>)
     * 2. Extract the MAL ID from external links + anime cover URL
     * 3. Pre-warm the WebView (for AniList/Kitsu metadata fetches)
     * 4. Fetch all episode pages (recursive pagination)
     * 5. Enrich episodes with metadata (thumbnails + titles + descriptions)
     *
     * The session is extracted from the detail page's final URL (after redirect).
     */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        AnimepaheLog.i("getEpisodeList: START url=${anime.url}")

        // Pre-warm WebView for metadata fetches (AniList/Kitsu need Chrome's TLS)
        webViewFetcher.warmUp()

        // 1. Fetch the detail page (follows /a/<id> → /anime/<session> redirect)
        val detailResponse = client.newCall(animeDetailsRequest(anime)).execute()
        val detailDoc = detailResponse.use { it.asJsoup() }

        // 2. Extract the session from the final URL (after redirect)
        val session = detailResponse.request.url.pathSegments.last()
        AnimepaheLog.i("getEpisodeList: session=$session")

        // 3. Extract MAL ID from external links + anime cover URL (for metadata enrichment)
        val malId = extractMalId(detailDoc)
        val animeCoverUrl = detailDoc.selectFirst("div.anime-poster a")?.attr("href")
        AnimepaheLog.d("getEpisodeList: malId=$malId, cover=${AnimepaheLog.trunc(animeCoverUrl ?: "null", 50)}")

        // 4. Fetch all episode pages (recursive pagination)
        val rawEpisodes = mutableListOf<Pair<EpisodeDto, String>>() // (dto, episodeSession)
        var pageUrl: String? = "$baseUrl/api?m=release&id=$session&sort=episode_asc&page=1"
        var pageCount = 0
        val maxPages = 50  // safety limit

        while (pageUrl != null && pageCount < maxPages) {
            val currentUrl = pageUrl
            pageCount++
            val pageResponse = client.newCall(GET(currentUrl, headers)).execute()
            val data = pageResponse.use { resp ->
                json.decodeFromString<ResponseDto<EpisodeDto>>(resp.body.string())
            }
            data.items.forEach { ep ->
                rawEpisodes.add(ep to ep.session)
            }
            // Next page?
            pageUrl = if (data.currentPage < data.lastPage) {
                currentUrl.substringBeforeLast("&page=") + "&page=${data.currentPage + 1}"
            } else {
                null
            }
        }
        AnimepaheLog.i("getEpisodeList: fetched ${rawEpisodes.size} episodes from $pageCount page(s)")

        // 5. ★ Multi-season renumbering: if the first episode number > 1, the site is
        // continuing numbering from a previous season (e.g. Season 2 starts at ep 13).
        // Renumber starting from 1 so each season's episodes are sequential from 1.
        val minEpNum = rawEpisodes.minOfOrNull { it.first.episodeNumber } ?: 0f
        val epOffset = if (minEpNum > 1f) {
            val offset = minEpNum - 1f
            AnimepaheLog.i("getEpisodeList: multi-season detected (first ep=$minEpNum), renumbering with offset=$offset")
            offset
        } else {
            0f
        }

        // 6. Build SEpisode list with adjusted episode numbers
        val episodes = rawEpisodes.map { (dto, epSession) ->
            val adjustedNum = dto.episodeNumber - epOffset
            SEpisode.create().apply {
                episode_number = adjustedNum
                val epName = if (floor(adjustedNum) == ceil(adjustedNum)) {
                    adjustedNum.toInt().toString()
                } else {
                    adjustedNum.toString()
                }
                name = "Episode $epName"
                date_upload = parseDate(dto.createdAt)
                // ★ The URL must be the REAL play page path: /play/<animeSession>/<episodeSession>
                // This is what the base class fetches in getVideoList(episode) → videoListParse.
                // ★ Fork compat: /play/... is a valid path on animepahe (resolves to baseUrl, no DNS error).
                // The adjusted episode number is only for display (episode_number + name), NOT for the URL.
                setUrlWithoutDomain("/play/$session/$epSession")
            }
        }
        AnimepaheLog.i("getEpisodeList: built ${episodes.size} episodes (offset=$epOffset, range ${episodes.firstOrNull()?.episode_number}-${episodes.lastOrNull()?.episode_number})")

        // 7. Enrich with metadata (thumbnails + titles + descriptions)
        // ★ Uses the ADJUSTED episode number for metadata lookup — this is correct because
        // if the MAL ID is for the current season, Jikan/AniList episode N = this season's ep N.
        enrichEpisodesWithMetadata(episodes, malId, animeCoverUrl)

        // Reverse to descending (newest first) — matches reference behavior
        return episodes.reversed()
    }

    /**
     * ★ Enrich episodes with metadata from Jikan + AniList + Anikage + Kitsu.
     * Respects 3 user toggles (loadThumbnails, loadTitles, loadDescriptions).
     * If ALL are OFF, the fetcher is skipped entirely (no API calls).
     * Non-breaking: wrapped in try-catch, never throws.
     */
    private suspend fun enrichEpisodesWithMetadata(
        episodes: List<SEpisode>,
        malId: String?,
        animeCoverUrl: String?,
    ) {
        if (episodes.isEmpty()) return

        val loadThumbnails = settings.loadThumbnails
        val loadTitles = settings.loadTitles
        val loadDescriptions = settings.loadDescriptions

        // Skip entirely if all toggles are OFF
        if (!loadThumbnails && !loadTitles && !loadDescriptions) {
            AnimepaheLog.d("enrichEpisodesWithMetadata: skipped (all toggles OFF)")
            return
        }

        if (malId.isNullOrBlank()) {
            AnimepaheLog.d("enrichEpisodesWithMetadata: no MAL ID available, skipping")
            return
        }

        try {
            AnimepaheLog.d("enrichEpisodesWithMetadata: malId=$malId, thumbs=$loadThumbnails, titles=$loadTitles, descs=$loadDescriptions")

            val metadata = metadataFetcher.fetch(malId, animeCoverUrl)
            if (metadata.isEmpty()) {
                AnimepaheLog.d("enrichEpisodesWithMetadata: no metadata for malId=$malId")
                return
            }

            var enrichedCount = 0
            for (ep in episodes) {
                val epNum = ep.episode_number.toInt()
                val epMeta = metadata[epNum] ?: continue

                if (loadThumbnails) {
                    ep.preview_url = epMeta.thumbnailUrl
                }
                if (loadDescriptions) {
                    ep.summary = epMeta.description
                }
                if (loadTitles) {
                    val sourceTitle = epMeta.title
                    if (!sourceTitle.isNullOrBlank()) {
                        ep.name = "EP $epNum - $sourceTitle"
                    }
                }
                enrichedCount++
            }
            AnimepaheLog.i("enrichEpisodesWithMetadata: enriched $enrichedCount/${episodes.size} episodes")
        } catch (e: Exception) {
            AnimepaheLog.e("enrichEpisodesWithMetadata: failed — ${e.message}", e)
        }
    }

    /**
     * Extract the MyAnimeList anime ID from the detail page's external links.
     * Looks for an <a> href matching myanimelist.net/anime/<id>.
     * Returns null if no MAL link found (enrichment will be skipped).
     */
    private fun extractMalId(doc: org.jsoup.nodes.Document): String? {
        val externalLinks = doc.select("div.col-sm-4.anime-info p:contains(External Links:) a")
        for (link in externalLinks) {
            val href = link.attr("abs:href")
            val match = malUrlRegex.find(href)
            if (match != null) {
                val malId = match.groupValues[1]
                AnimepaheLog.d("extractMalId: found MAL ID $malId from $href")
                return malId
            }
        }
        AnimepaheLog.d("extractMalId: no MAL link found in ${externalLinks.size} external links")
        return null
    }

    // ════════════════════════════════════════════════════════════════════
    // Episodes — stub (never called; getEpisodeList is overridden above)
    // ════════════════════════════════════════════════════════════════════
    override fun episodeListParse(response: Response): List<SEpisode> = emptyList()

    // ════════════════════════════════════════════════════════════════════
    // Video extraction — Step 4 (Kwik HLS)
    // ════════════════════════════════════════════════════════════════════
    // ★ The app (Animiru/Aniyomi) uses the ext-lib 16 NEW pipeline:
    //   getHosterList(episode) → returns List<Hoster> with pre-populated videoList
    //   hosterListParse(response) is NEVER CALLED when getHosterList is overridden.
    //
    // Flow:
    // 1. episode.url = /play/<animeSession>/<episodeSession> (the real play page path)
    // 2. Fetch the play page → parse div#resolutionMenu > button.dropdown-item
    // 3. Each button has: data-src (kwik URL), data-resolution (360/720/1080), data-audio (jpn/eng)
    // 4. For each: resolve the kwik link via KwikExtractor → create a Video
    // 5. Return videos.toHosterList() (single Hoster with all videos)

    /**
     * ★ Override getHosterList to do the REAL extraction.
     * The base class's implementation calls hosterListParse (which returns empty) —
     * so we MUST override this to do the actual work.
     */
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        AnimepaheLog.i("========== getHosterList START ==========")
        AnimepaheLog.i("getHosterList: episode.url = ${episode.url}")
        AnimepaheLog.i("getHosterList: episode.name = ${episode.name}")

        return try {
            // 1. Fetch the play page
            val playPageUrl = "$baseUrl${episode.url}"
            AnimepaheLog.d("getHosterList: fetching play page: $playPageUrl")
            val response = client.newCall(GET(playPageUrl, headers)).execute()
            val doc = response.use { it.asJsoup() }

            // 2. Parse resolution buttons
            // HTML structure (verified from user-provided play page HTML):
            // <div class="dropdown-menu" id="resolutionMenu">
            //   <button data-src="https://kwik.cx/e/..." data-fansub="SubsPlease"
            //           data-resolution="1080" data-audio="jpn" class="dropdown-item">
            //     SubsPlease · 1080p
            //   </button>
            // </div>
            val buttons = doc.select("div#resolutionMenu > button")
            if (buttons.isEmpty()) {
                AnimepaheLog.w("getHosterList: no resolution buttons found on play page")
                showToast("No video sources found for this episode")
                return emptyList()
            }

            AnimepaheLog.i("getHosterList: found ${buttons.size} resolution buttons")

            // 3. Resolve each kwik link → create a Video
            val videos = mutableListOf<Video>()
            for (btn in buttons) {
                val kwikLink = btn.attr("data-src")
                val resolution = btn.attr("data-resolution")  // "360", "720", "1080"
                val audio = btn.attr("data-audio")            // "jpn" (sub) or "eng" (dub)

                if (kwikLink.isBlank()) {
                    AnimepaheLog.w("getHosterList: button has no data-src, skipping")
                    continue
                }

                // Build quality label: "1080p (Sub)" or "1080p (Dub)"
                val qualityLabel = buildQualityLabel(resolution, audio)
                AnimepaheLog.d("getHosterList: resolving $qualityLabel: ${AnimepaheLog.trunc(kwikLink, 60)}")

                val video = kwikExtractor.getHlsVideo(kwikLink, referer = playPageUrl, quality = qualityLabel)
                if (video != null) {
                    videos.add(video)
                    AnimepaheLog.i("getHosterList: resolved $qualityLabel ✓")
                } else {
                    AnimepaheLog.w("getHosterList: failed to resolve $qualityLabel")
                }
            }

            AnimepaheLog.i("getHosterList: DONE — ${videos.size}/${buttons.size} videos extracted")

            if (videos.isEmpty()) {
                showToast("Failed to extract video sources (Kwik extraction failed)")
            }

            // 4. Return as a single Hoster with all videos (the app shows the quality options)
            listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = videos))
        } catch (e: Exception) {
            AnimepaheLog.e("getHosterList: FAILED — ${e.message}", e)
            showToast("Failed to load videos: ${e.message}")
            emptyList()
        }
    }

    /** Build a quality label from the data-resolution + data-audio attributes. */
    private fun buildQualityLabel(resolution: String, audio: String): String {
        val res = if (resolution.isNotBlank()) "${resolution}p" else "Unknown"
        val aud = when (audio.lowercase()) {
            "eng" -> "Dub"
            "jpn" -> "Sub"
            else -> ""
        }
        return if (aud.isNotBlank()) "$res ($aud)" else res
    }

    // hosterListParse is never called (getHosterList is overridden above)
    override fun hosterListParse(response: Response): List<Hoster> = emptyList()

    // videoListParse is for legacy-pipeline forks (pre-ext-lib-16). Not used by the new pipeline.
    override fun videoListParse(response: Response): List<Video> = emptyList()

    /** Sort videos by the user's preferred quality + audio. */
    override fun List<Video>.sortVideos(): List<Video> {
        val preferredQuality = settings.preferredQuality
        val preferredAudio = settings.preferredAudio
        AnimepaheLog.d("sortVideos: preferred quality=$preferredQuality, audio=$preferredAudio, $size videos")
        return sortedWith(
            compareByDescending<Video> { it.videoTitle.contains(preferredQuality) }
                .thenByDescending {
                    val titleLower = it.videoTitle.lowercase()
                    when (preferredAudio) {
                        "dub" -> titleLower.contains("dub")
                        else -> titleLower.contains("sub") || !titleLower.contains("dub")
                    }
                }
                .thenByDescending { it.videoTitle }
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // Seasons — animepahe has no season concept
    // ════════════════════════════════════════════════════════════════════
    override fun seasonListParse(response: Response): List<SAnime> = emptyList()

    // ════════════════════════════════════════════════════════════════════
    // Filters
    // ════════════════════════════════════════════════════════════════════
    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        Filters.GenresFilter(),
        AnimeFilter.Separator(),
        Filters.DemographicFilter(),
        AnimeFilter.Separator(),
        Filters.ThemeFilter(),
        AnimeFilter.Separator(),
        Filters.YearFilter(),
        Filters.SeasonFilter(),
    )

    // ════════════════════════════════════════════════════════════════════
    // Settings
    // ════════════════════════════════════════════════════════════════════
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        settings.setupPreferenceScreen(screen)
    }

    // ════════════════════════════════════════════════════════════════════
    // Utilities
    // ════════════════════════════════════════════════════════════════════
    private val animeIdRegex = Regex("""/a/(\d+)""")
    private val malUrlRegex = Regex("""myanimelist\.net/anime/(\d+)""")

    private fun parseStatus(statusString: String?): Int = when (statusString) {
        "Currently Airing" -> SAnime.ONGOING
        "Finished Airing" -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    private fun parseDate(dateString: String): Long = try {
        dateFormatter.parse(dateString)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}
