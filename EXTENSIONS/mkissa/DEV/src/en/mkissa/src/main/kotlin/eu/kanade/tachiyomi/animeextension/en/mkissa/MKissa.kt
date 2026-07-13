package eu.kanade.tachiyomi.animeextension.en.mkissa

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animeextension.en.mkissa.extractor.MKissaExtractor
import eu.kanade.tachiyomi.animeextension.en.mkissa.metadata.EpisodeMetadataFetcher
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import androidx.preference.PreferenceScreen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

/**
 * MKissa — Aniyomi anime extension (ext-lib 16).
 *
 * Site: https://mkissa.to (a SvelteKit frontend on the api.allanime.day GraphQL API).
 * API:  https://api.allanime.day/api (GraphQL — POST with full query strings).
 *
 * ★ The mkissa.to watch page (`/anime/<id>/p-<N>-<sub|dub>`) sits behind a Cloudflare
 * managed challenge ("Just a moment..."). That's handled LATER in the video-playback step
 * (Step 4) via WebView. The catalog + details + episodes layers (this file) use the
 * api.allanime.day API directly, which is NOT behind a managed challenge.
 *
 * ★ Episode URL uses the fork-compat encoding: `/anime/<id>/p-<N>-<translationType>#<episodeString>`.
 * The path is a valid URL (resolves to baseUrl — no DNS error in legacy-pipeline forks); the
 * fragment carries the raw episodeString for exact recovery in getHosterList.
 *
 * Version history:
 * - v16.1 (versionCode 1, versionId 1): popular, latest, search, filters, details, episodes + settings.
 *   Video extraction (Step 4) deferred — getHosterList returns empty for now.
 *
 * Reference (for understanding the API, NOT copied): SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/allanime/
 */
class MKissa : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "MKissa 180"
    override val baseUrl = "https://mkissa.to"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId = 1

    /** The GraphQL API endpoint (mkissa.to's frontend calls this; not behind a managed challenge). */
    private val apiUrl = "https://api.allanime.day/api"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── Preferences + Settings ────────────────────────────────────────
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    private val settings: MKissaSettings by lazy { MKissaSettings(preferences) }

    // ── Episode metadata enrichment (Anikage primary + Jikan fallback) ─
    private val metadataFetcher: EpisodeMetadataFetcher by lazy {
        EpisodeMetadataFetcher(client, json)
    }

    // ── Video extractor + WebViewFetcher ──────────────────────────────
    // ★ WebViewFetcher is ALWAYS created — it's needed for:
    // 1. Cloudflare Turnstile bypass (solveCloudflare uses WebView when OkHttp interceptor fails)
    // 2. Filemoon (Fm-Hls) same-origin playback API fetch
    // 3. Uni player API fetch
    // Even if Fm-Hls + Uni are disabled, the WebView is needed for CF bypass.
    private val webViewFetcher: eu.kanade.tachiyomi.animeextension.en.mkissa.extractor.WebViewFetcher by lazy {
        MKissaLog.i("WebViewFetcher: creating (needed for Cloudflare bypass + Fm-Hls + Uni)")
        eu.kanade.tachiyomi.animeextension.en.mkissa.extractor.WebViewFetcher(
            Injekt.get<Application>(),
        )
    }

    private val videoExtractor: MKissaExtractor by lazy {
        MKissaExtractor(client, headers, json, webViewFetcher)
    }

    // ── Headers ────────────────────────────────────────────────────────
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ── GraphQL POST helper ────────────────────────────────────────────
    private fun buildPost(variables: JsonObject, query: String): Request {
        val body = buildJsonObject {
            put("variables", variables)
            put("query", query)
        }.toString().toRequestBody("application/json".toMediaType())
        return POST(apiUrl, headers, body)
    }

    private inline fun <reified T> Response.parseJson(): T {
        val bodyStr = use { it.body?.string().orEmpty() }
        return json.decodeFromString<T>(bodyStr)
    }

    // ════════════════════════════════════════════════════════════════════
    // Popular
    // ════════════════════════════════════════════════════════════════════

    override fun popularAnimeRequest(page: Int): Request {
        val variables = buildJsonObject {
            put("type", "anime")
            put("size", PAGE_SIZE)
            put("dateRange", 1) // Daily popular (matches the site's default: mkissa.to/popular?type=anime&range=1)
            put("page", page)
        }
        return buildPost(variables, POPULAR_QUERY)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val parsed = response.parseJson<PopularResult>()
        val animeList = parsed.data.queryPopular.recommendations.mapNotNull { rec ->
            val card = rec.anyCard ?: return@mapNotNull null
            SAnime.create().apply {
                title = pickTitle(card.name, card.englishName, card.nativeName)
                thumbnail_url = card.thumbnail
                url = "/anime/${card.id}"
            }
        }
        // hasNext: if this page is full, there might be more. If < PAGE_SIZE, definitely the last page.
        // (The API wraps around past the last page, but a partial page signals the true end.)
        val hasNext = animeList.size == PAGE_SIZE
        return AnimesPage(animeList, hasNext)
    }

    // ════════════════════════════════════════════════════════════════════
    // Latest
    // ════════════════════════════════════════════════════════════════════

    override fun latestUpdatesRequest(page: Int): Request {
        val variables = buildJsonObject {
            putJsonObject("search") {
                put("allowAdult", false)
                put("allowUnknown", false)
                put("sortBy", "Recent")
            }
            put("limit", PAGE_SIZE)
            put("page", page)
            put("translationType", settings.preferredAudio) // "sub" or "dub"
            put("countryOrigin", "ALL")
        }
        return buildPost(variables, SEARCH_QUERY)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage = parseShows(response)

    // ════════════════════════════════════════════════════════════════════
    // Search
    // ════════════════════════════════════════════════════════════════════

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = MKissaFilters.getSearchParameters(filters)
        val variables = buildJsonObject {
            putJsonObject("search") {
                if (query.isNotBlank()) put("query", query)
                put("allowAdult", false)
                put("allowUnknown", false)
                params.sortBy.takeIf { it != "Recent" && it.isNotBlank() }?.let { put("sortBy", it) }
                params.season.takeIf { it != "all" && it.isNotBlank() }?.let { put("season", it) }
                params.releaseYear.toIntOrNull()?.let { put("year", it) }
                if (params.genres != "all" && params.genres.isNotBlank()) {
                    put("genres", json.parseToJsonElement(params.genres))
                    putJsonArray("excludeGenres") {}
                }
                if (params.types != "all" && params.types.isNotBlank()) {
                    put("types", json.parseToJsonElement(params.types))
                }
            }
            put("limit", PAGE_SIZE)
            put("page", page)
            put("translationType", settings.preferredAudio)
            put("countryOrigin", if (params.origin.isBlank()) "ALL" else params.origin)
        }
        return buildPost(variables, SEARCH_QUERY)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = parseShows(response)

    private fun parseShows(response: Response): AnimesPage {
        val parsed = response.parseJson<SearchResult>()
        val animeList = parsed.data.shows.edges.map { edge ->
            SAnime.create().apply {
                title = pickTitle(edge.name, edge.englishName, edge.nativeName)
                thumbnail_url = edge.thumbnail
                url = "/anime/${edge.id}"
            }
        }
        // ★ The API intermittently returns `total: null` (especially during cache refreshes).
        // The DTO makes `total` nullable (Int?) so parsing doesn't crash. We use the full-page
        // heuristic for hasNext: if this page has fewer than PAGE_SIZE items, it's the last page.
        // (The API wraps around past the last page, but a partial page signals the true end.)
        val hasNext = animeList.size == PAGE_SIZE
        return AnimesPage(animeList, hasNext)
    }

    // ════════════════════════════════════════════════════════════════════
    // Filters
    // ════════════════════════════════════════════════════════════════════

    override fun getFilterList(): AnimeFilterList = MKissaFilters.FILTER_LIST

    // ════════════════════════════════════════════════════════════════════
    // Anime Details
    // ════════════════════════════════════════════════════════════════════

    override fun animeDetailsRequest(anime: SAnime): Request {
        val id = extractAnimeId(anime.url)
        val variables = buildJsonObject { put("_id", id) }
        return buildPost(variables, DETAILS_QUERY)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val show = response.parseJson<DetailsResult>().data.show
        return SAnime.create().apply {
            title = pickTitle(show.name, show.englishName, show.nativeName)
            thumbnail_url = show.thumbnail
            description = buildDescription(show)
            genre = show.genres?.joinToString(", ")
            status = parseStatus(show.status)
            author = show.studios?.firstOrNull()
            artist = show.studios?.drop(1)?.joinToString(", ")?.ifEmpty { null }
            initialized = true
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Episodes
    // ════════════════════════════════════════════════════════════════════

    override fun episodeListRequest(anime: SAnime): Request {
        val id = extractAnimeId(anime.url)
        val variables = buildJsonObject { put("_id", id) }
        return buildPost(variables, EPISODES_QUERY)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val result = response.parseJson<EpisodesResult>()
        val subEps = result.data.show.availableEpisodesDetail.sub
        val dubEps = result.data.show.availableEpisodesDetail.dub
        val animeId = result.data.show.id
        return buildEpisodeList(animeId, subEps, dubEps, null, null)
    }

    /**
     * ★ Override the suspend [getEpisodeList] to enrich episodes with metadata
     * (thumbnails + titles + descriptions) from Anikage + Jikan.
     *
     * Respects the 3 user toggles in settings (all default ON):
     * - Load episode thumbnails
     * - Load episode titles
     * - Load episode descriptions
     *
     * If ALL 3 toggles are OFF, skips the metadata fetch entirely (zero API calls — fast).
     *
     * The metadata fetcher extracts the AniList media ID from the anime's thumbnail URL
     * (`bx<id>-` pattern) and queries Anikage first, then Jikan as a fallback.
     */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val loadThumbnails = settings.loadThumbnails
        val loadTitles = settings.loadTitles
        val loadDescriptions = settings.loadDescriptions

        // ★ Pre-warm the WebView during episode list fetch (background thread).
        // This hides the 2-10s WebView cold start from click-to-play.
        // Only warms up if Fm-Hls or Uni is enabled (otherwise webViewFetcher is null).
        webViewFetcher?.warmUp()

        // 1. Fetch the detail page → extract thumbnail URL (for AniList ID) + title (for Jikan fallback)
        val detailResponse = client.newCall(animeDetailsRequest(anime)).execute()
        val detail = detailResponse.parseJson<DetailsResult>().data.show
        val animeTitle = pickTitle(detail.name, detail.englishName, detail.nativeName)
        val thumbnailUrl = detail.thumbnail
        val anilistId = EpisodeMetadataFetcher.extractAnilistId(thumbnailUrl)

        // 2. Fetch episodes (the base episode list)
        val episodesResponse = client.newCall(episodeListRequest(anime)).execute()
        val episodesResult = episodesResponse.parseJson<EpisodesResult>()
        val subEps = episodesResult.data.show.availableEpisodesDetail.sub
        val dubEps = episodesResult.data.show.availableEpisodesDetail.dub
        val animeId = episodesResult.data.show.id
        val episodes = buildEpisodeList(animeId, subEps, dubEps, thumbnailUrl, animeTitle)

        // 3. Skip enrichment if all 3 toggles are OFF (zero API calls — fast)
        if (!loadThumbnails && !loadTitles && !loadDescriptions) {
            MKissaLog.d("getEpisodeList: all metadata toggles OFF — skipping enrichment")
            return episodes
        }

        // 4. Enrich episodes with metadata
        enrichEpisodesWithMetadata(episodes, anilistId, animeTitle, thumbnailUrl, loadThumbnails, loadTitles, loadDescriptions)

        return episodes
    }

    /** Build the SEpisode list from the sub/dub episode strings. (Extracted for reuse.) */
    private fun buildEpisodeList(
        animeId: String,
        subEps: List<String>,
        dubEps: List<String>,
        thumbnailUrl: String?,
        animeTitle: String?,
    ): List<SEpisode> {
        // Build a map: episodeNumber → set of available audio types.
        // ★ Rule §8: show sub/dub availability via the scanlator field (below the episode name),
        // NOT crammed into the name. One SEpisode per unique episode number; scanlator shows
        // all available audio types ("Sub", "Dub", or "Sub • Dub").
        val episodeAudioMap = mutableMapOf<Float, MutableList<String>>()
        for (epStr in subEps) {
            val num = epStr.toFloatOrNull() ?: continue
            episodeAudioMap.getOrPut(num) { mutableListOf() }.add("Sub")
        }
        for (epStr in dubEps) {
            val num = epStr.toFloatOrNull() ?: continue
            episodeAudioMap.getOrPut(num) { mutableListOf() }.add("Dub")
        }

        // ★ Sort DESCENDING (ep 12 first, ep 0 last).
        // Aniyomi displays episodes in REVERSE of the order returned by the extension (so the
        // latest episode appears at the top by default). Returning descending → Aniyomi reverses
        // → user sees ascending (ep 1 first). This matches the allanime reference + animepahe.
        // (Verified session 03 — returning ascending caused episodes to display 13→1 instead of 1→13.)
        val preferredAudio = settings.preferredAudio // "sub" or "dub"
        return episodeAudioMap.keys.sortedDescending().map { epNum ->
            val availableAudios = episodeAudioMap[epNum]!!
            // The translationType to use for the episode URL: prefer the user's preference if available,
            // otherwise the first available audio.
            val translationType = if (preferredAudio in availableAudios.map { it.lowercase() }) {
                preferredAudio
            } else {
                availableAudios.first().lowercase()
            }
            // Find the raw episode string matching this number for the chosen translationType.
            val epString = (if (translationType == "sub") subEps else dubEps)
                .firstOrNull { it.toFloatOrNull() == epNum }
                ?: epNum.toInt().toString()

            SEpisode.create().apply {
                episode_number = epNum
                name = formatEpisodeName(epNum)
                scanlator = availableAudios.joinToString(" • ") // "Sub", "Dub", or "Sub • Dub"
                // ★ Fork-compat encoding: valid path + metadata in fragment.
                // Path: /anime/<id>/p-<N>-<translationType> (valid URL → resolves to baseUrl, no DNS error).
                // Fragment: raw episodeString (for exact recovery in getHosterList).
                url = "/anime/$animeId/p-${formatEpNumForUrl(epNum)}-$translationType#$epString"
                date_upload = 0L
            }
        }
    }

    /** Enrich episodes with metadata from Anikage + Jikan. Never throws. */
    private suspend fun enrichEpisodesWithMetadata(
        episodes: List<SEpisode>,
        anilistId: String?,
        animeTitle: String,
        fallbackThumbnailUrl: String?,
        loadThumbnails: Boolean,
        loadTitles: Boolean,
        loadDescriptions: Boolean,
    ) {
        if (episodes.isEmpty()) return
        try {
            val metadata = metadataFetcher.fetch(anilistId, animeTitle, fallbackThumbnailUrl)
            if (metadata.isEmpty()) {
                MKissaLog.d("enrichEpisodesWithMetadata: no metadata available (anilistId=$anilistId)")
                return
            }
            var enrichedCount = 0
            for (ep in episodes) {
                val epNum = ep.episode_number.toInt()
                val epMeta = metadata[epNum] ?: continue
                var enriched = false
                if (loadThumbnails && !epMeta.thumbnailUrl.isNullOrBlank()) {
                    ep.preview_url = epMeta.thumbnailUrl
                    enriched = true
                }
                if (loadDescriptions && !epMeta.description.isNullOrBlank()) {
                    ep.summary = epMeta.description
                    enriched = true
                }
                if (loadTitles && !epMeta.title.isNullOrBlank()) {
                    // ★ Title format convention: "EP N - title" (NOT "Episode N - title")
                    ep.name = "EP $epNum - ${epMeta.title}"
                    enriched = true
                }
                if (enriched) enrichedCount++
            }
            MKissaLog.i("enrichEpisodesWithMetadata: enriched $enrichedCount/${episodes.size} episodes")
        } catch (e: Exception) {
            MKissaLog.e("enrichEpisodesWithMetadata: failed — ${e.message}", e)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Video pipeline (Step 4 — DEFERRED)
    // The user's current task is catalog + details + episodes only. Video extraction
    // (getHosterList) will be implemented in a follow-up session. For now, getHosterList
    // returns an empty list — the app will show "No available videos" until Step 4 is done.
    // ════════════════════════════════════════════════════════════════════

    override fun hosterListParse(response: Response): List<Hoster> = emptyList()

    // ext-lib 16 version (open stub — override for completeness)
    override fun videoListParse(response: Response, hoster: Hoster): List<Video> = emptyList()

    // Legacy single-arg version (for pre-ext-lib-16 forks — matches animepahe pattern)
    @Suppress("DEPRECATION", "overriding_deprecated")
    override fun videoListParse(response: Response): List<Video> = emptyList()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        return try {
            // Decode the episode metadata from the fragment.
            // episode.url = /anime/<showId>/p-<N>-<translationType>#<episodeString>
            val url = episode.url
            val path = url.substringBefore("#")
            val episodeString = url.substringAfter("#", "")
            val showId = path.substringAfter("/anime/").substringBefore("/")
            val translationType = if (path.contains("-sub")) "sub" else "dub"

            MKissaLog.i("getHosterList: showId=$showId, ep=$episodeString, type=$translationType")

            val enabledServers = settings.enabledServers
            val preferredServer = settings.preferredServer
            MKissaLog.d("getHosterList: enabledServers=$enabledServers, preferredServer='$preferredServer'")

            val videos = videoExtractor.extractVideos(
                showId = showId,
                translationType = translationType,
                episodeString = episodeString,
                enabledServers = enabledServers,
                preferredServer = preferredServer,
                baseUrl = baseUrl,
            )

            if (videos.isEmpty()) {
                MKissaLog.w("getHosterList: no videos extracted from any server")
                return emptyList()
            }

            // Sort videos by quality (preferred first) + audio type
            val sortedVideos = videos.sortedWith(
                compareByDescending<Video> { it.videoTitle.contains(settings.preferredQuality, true) }
                    .thenByDescending { it.videoTitle.contains(settings.preferredAudio, true) }
            )

            MKissaLog.i("getHosterList: returning ${sortedVideos.size} videos")
            // Return as a single Hoster with NO_HOSTER_LIST (flat video list)
            listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = sortedVideos))
        } catch (e: Exception) {
            // ★ NEVER throw — the app crashes if getHosterList throws.
            // Return emptyList on any exception (the app shows "no videos").
            MKissaLog.e("getHosterList: CRASHED — ${e.message}", e)
            emptyList()
        }
    }

    // ★ Fork compatibility — legacy-pipeline forks call getVideoList(SEpisode), not getHosterList.
    // Delegate to getHosterList + flatten the result.
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return try {
            getHosterList(episode).flatMap { it.videoList ?: emptyList() }
        } catch (e: Exception) {
            MKissaLog.e("getVideoList legacy fallback failed", e)
            emptyList()
        }
    }

    override fun seasonListParse(response: Response): List<SAnime> =
        throw UnsupportedOperationException("MKissa has no seasons concept")

    // ════════════════════════════════════════════════════════════════════
    // "Open in WebView" URL (ext-lib 14+)
    // ════════════════════════════════════════════════════════════════════

    override fun getEpisodeUrl(episode: SEpisode): String {
        // episode.url is "/anime/<id>/p-<N>-<translationType>#<epString>" — strip the fragment
        // to get the valid watch-page path.
        val path = episode.url.substringBefore("#")
        return "$baseUrl$path"
    }

    override fun getAnimeUrl(anime: SAnime): String {
        return "$baseUrl${anime.url}"
    }

    // ════════════════════════════════════════════════════════════════════
    // Settings (ConfigurableAnimeSource)
    // ════════════════════════════════════════════════════════════════════

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        settings.setupPreferenceScreen(screen)
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    /** Pick the title based on the user's preferred title style. */
    private fun pickTitle(romaji: String, english: String?, native: String?): String {
        return when (settings.titleStyle) {
            "eng" -> english?.takeIf { it.isNotBlank() } ?: romaji
            "native" -> native?.takeIf { it.isNotBlank() } ?: romaji
            else -> romaji
        }
    }

    /** Extract the anime _id from the stored URL path "/anime/<id>". */
    private fun extractAnimeId(url: String): String {
        return url.substringAfter("/anime/").substringBefore("/").substringBefore("#")
    }

    /** Parse the site's status string into an SAnime status constant. */
    private fun parseStatus(status: String?): Int {
        return when (status?.lowercase()) {
            "releasing" -> SAnime.ONGOING
            "finished" -> SAnime.COMPLETED
            "not yet released" -> SAnime.ONGOING
            "cancelled" -> SAnime.CANCELLED
            "hiatus" -> SAnime.ON_HIATUS
            else -> SAnime.UNKNOWN
        }
    }

    /** Build the description string with metadata appendix (type, aired, score). */
    private fun buildDescription(show: DetailsResult.DetailsData.Show): String {
        val descHtml = show.description.orEmpty()
        // Strip HTML tags (<br> → \n, <i>...</i> → just the text)
        val desc = descHtml
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .replace(Regex("<[^>]+>"), "")
            .trim()
        return buildString {
            append(desc)
            append("\n\n")
            show.type?.let { append("Type: $it") }
            show.season?.let { s ->
                append("\nAired: ${s.quarter ?: "-"} ${s.year ?: "-"}")
            }
            show.score?.let { append("\nScore: $it★") }
            show.rating?.let { append("\nRating: $it") }
        }
    }

    /** Format the episode name — clean (rule §8): "EP N - title" or just "Episode N" if no title. */
    private fun formatEpisodeName(epNum: Float): String {
        val intPart = epNum.toInt()
        val fracPart = epNum - intPart
        return if (fracPart == 0f) {
            "EP $intPart"
        } else {
            "EP $epNum"
        }
    }

    /** Format the episode number for the URL path (integer if whole, else the float string). */
    private fun formatEpNumForUrl(epNum: Float): String {
        val intPart = epNum.toInt()
        return if (epNum - intPart == 0f) intPart.toString() else epNum.toString()
    }

    companion object {
        private const val PAGE_SIZE = 40
    }
}
