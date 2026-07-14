package eu.kanade.tachiyomi.animeextension.en.anidb

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animeextension.en.anidb.extractor.AniDBExtractor
import eu.kanade.tachiyomi.animeextension.en.anidb.extractor.PlaylistUtils
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
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
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder

/**
 * AniDB — Aniyomi anime extension (ext-lib 16).
 *
 * Site: https://anidb.app
 * Structure: Laravel + Alpine.js server-rendered HTML for browse/details; clean JSON
 * APIs for episodes + languages. JW Player embed page serves a single HLS master.m3u8.
 *
 * ★ AniDB is the simplest of our extensions: ONE video server (the site's own host
 * hls.anidb.app), no token crypto, no PNG wrapping, no WebView needed. Cloudflare
 * protects the main site but the inherited `client` (CloudflareInterceptor + desktop
 * UA) gets HTTP 200 — no Turnstile challenge for non-headless clients.
 *
 * ## URL structure (verified in MEMORY/sites/site-analysis.md)
 *  - Home:        /home
 *  - Browse:      /browse?type=&status=&season=&year=&genres=&sort=&page=N&q=<>
 *  - A-Z index:   /az?letter=A
 *  - By genre:    /genres/<id>
 *  - By theme:    /themes/<id>
 *  - Detail/watch:/anime/<slug>-<numeric-id>   ← player + episode list on the SAME page
 *  - Episodes API:/api/frontend/anime/<animeId>/episodes  → {episodes:[{id,number,number2,filler}]}
 *  - Languages API:/api/frontend/episode/<epId>/languages → {languages:[{code,name,embed_url}]}
 *  - Embed:       /embed/<token>  → JW Player `sources: [{file:'<m3u8>',type:'hls'}]`
 *  - HLS CDN:     https://hls.anidb.app/stream/<token>/master.m3u8  (no Referer required)
 *
 * ## Audio types
 *  - SUB (jpn / Japanese) — always available
 *  - DUB (eng / English)  — per-episode, not all anime have it
 *  - HSUB                  — not present on this site
 *
 * ## Reference (for understanding, NOT copied)
 *  - animepahe: HTML scrape + JSON API + single Kwik server (closest analog)
 *  - mkissa: PlaylistUtils HLS parser (adapted)
 *  - HOW_TO_BUILD_EXTENSION/02-catalog + 03-details + 04-playback
 */
class AniDB : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "AniDB 180"
    override val baseUrl = "https://anidb.app"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId = 1  // ★ STABLE — never bump after publish

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── Preferences + Settings ────────────────────────────────────────
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    private val settings: AniDBSettings by lazy { AniDBSettings(preferences) }

    // ── Extractor ─────────────────────────────────────────────────────
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val extractor by lazy { AniDBExtractor(client, headers, playlistUtils) }

    // ── Headers ───────────────────────────────────────────────────────
    // ★ Desktop Chrome UA — the site Cloudflare-challenges headless browsers,
    // but serves a full desktop UA without challenge. The inherited `client`
    // already has CloudflareInterceptor + cookieJar.
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .set("Referer", "$baseUrl/")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

    // ════════════════════════════════════════════════════════════════════
    // Popular — /browse with default sort (Trending). This matches what the
    // user sees on the site's home page top section.
    // ════════════════════════════════════════════════════════════════════
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/browse?sort=order_trending&page=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage = parseBrowsePage(response)

    // ════════════════════════════════════════════════════════════════════
    // Latest — /browse with sort=order_updated (Latest Updated).
    // ════════════════════════════════════════════════════════════════════
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/browse?sort=order_updated&page=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = parseBrowsePage(response)

    // ════════════════════════════════════════════════════════════════════
    // Search — /browse?q=<query> (composes with all filters + pagination).
    // ════════════════════════════════════════════════════════════════════
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val urlBuilder = "$baseUrl/browse".toHttpUrl().newBuilder()

        // Search query (composes with filters on this site — verified)
        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query)
        }

        // Filters — all compose together
        filters.forEach { filter ->
            when (filter) {
                is Filters.SortFilter -> if (!filter.isDefault()) {
                    urlBuilder.addQueryParameter("sort", filter.toUriPart())
                }
                is Filters.TypeFilter -> if (!filter.isDefault()) {
                    urlBuilder.addQueryParameter("type", filter.toUriPart())
                }
                is Filters.StatusFilter -> if (!filter.isDefault()) {
                    urlBuilder.addQueryParameter("status", filter.toUriPart())
                }
                is Filters.SeasonFilter -> if (!filter.isDefault()) {
                    urlBuilder.addQueryParameter("season", filter.toUriPart())
                }
                is Filters.YearFilter -> if (!filter.isDefault()) {
                    urlBuilder.addQueryParameter("year", filter.toUriPart())
                }
                is Filters.GenreFilter -> {
                    val selected = filter.state.filter { it.state }.map { it.id }
                    if (selected.isNotEmpty()) {
                        urlBuilder.addQueryParameter("genres", selected.joinToString(","))
                    }
                }
                is Filters.ThemeFilter -> {
                    val selected = filter.state.filter { it.state }.map { it.id }
                    if (selected.isNotEmpty()) {
                        // The site uses a single `themes` param (verified by inspecting the form)
                        urlBuilder.addQueryParameter("themes", selected.joinToString(","))
                    }
                }
                else -> { /* Header / Separator — ignore */ }
            }
        }

        urlBuilder.addQueryParameter("page", page.toString())
        return GET(urlBuilder.build(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = parseBrowsePage(response)

    // ════════════════════════════════════════════════════════════════════
    // Browse page parser — shared by popular, latest, and search.
    // Card structure (verified from /browse HTML):
    //   <a class="anime-card" href="https://anidb.app/anime/<slug>-<id>" title="<title>">
    //     <img src="https://cdn.xlsbox.com/poster/small/<ts>/<id>.jpg" alt="<title>">
    //     <span class="badge ...">TV</span>      ← type
    //     <span class="badge ...">8.7</span>     ← score
    //   </a>
    // ════════════════════════════════════════════════════════════════════
    private fun parseBrowsePage(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val cards = doc.select("a.anime-card")
        AniDBLog.d("parseBrowsePage: found ${cards.size} cards")

        val anime = cards.mapNotNull { card ->
            val href = card.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = card.attr("title").takeIf { it.isNotBlank() }
                ?: card.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val poster = card.selectFirst("img")?.attr("abs:src")?.takeIf { it.isNotBlank() }

            SAnime.create().apply {
                this.title = title
                thumbnail_url = poster
                setUrlWithoutDomain(href)
            }
        }

        // Pagination: AniDB uses ?page=N. Detect "Next" link for hasNext.
        val hasNext = doc.select("a[href*=page=]").any { link ->
            val text = link.text().lowercase()
            text.contains("next") || text.contains("»") || text.contains("→")
        }
        AniDBLog.d("parseBrowsePage: ${anime.size} parsed, hasNext=$hasNext")
        return AnimesPage(anime, hasNext)
    }

    // ════════════════════════════════════════════════════════════════════
    // Anime details — HTML page at /anime/<slug>-<id>.
    // The detail page IS the watch page (player + episode list side by side).
    // ════════════════════════════════════════════════════════════════════
    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        return SAnime.create().apply {
            title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
            // Description from <meta name="description"> (the page's synopsis)
            description = doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
            // Cover poster
            thumbnail_url = doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("img[src*=cdn.xlsbox.com]")?.attr("abs:src")

            // Parse the metadata grid (dt/dd pairs)
            // Structure: <dt class="text-muted ...">Type</dt><dd>...</dd>
            val meta = mutableMapOf<String, String>()
            doc.select("dt").forEach { dt ->
                val key = dt.text().trim().trimEnd(':')
                val value = dt.nextElementSibling()?.text()?.trim().orEmpty()
                if (key.isNotBlank() && value.isNotBlank()) meta[key] = value
            }

            type = meta["Type"]
            status = when (meta["Status"]?.lowercase()) {
                "currently airing" -> SAnime.ONGOING
                "finished airing" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
            author = meta["Studios"]  // studio as author
            // ★ The detail page lists the anime's genres as <a href="/browse?genres=<id>">
            // links in a footer section. Exclude the "All genres →" link. Themes use
            // /browse?themes=<id> (may be absent for some anime).
            genre = doc.select("a[href*=browse?genres=]")
                .map { it.text() }
                .filter { it.isNotBlank() && !it.contains("All genres") }
                .joinToString { it }
                .takeIf { it.isNotBlank() }
            val themes = doc.select("a[href*=browse?themes=]")
                .map { it.text() }
                .filter { it.isNotBlank() && !it.contains("All themes") }
                .joinToString { it }
            if (themes.isNotBlank()) {
                genre = (genre?.let { "$it, " } ?: "") + themes
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Episodes — fetched via JSON API (NOT in the detail HTML).
    // We override getEpisodeList to fetch /api/frontend/anime/<id>/episodes.
    //
    // The animeId is the trailing number in the URL (/anime/<slug>-<id>).
    // The episode.url encodes the episode ID + anime slug so we can resolve
    // the embed later without re-fetching the detail page.
    // ════════════════════════════════════════════════════════════════════
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        AniDBLog.i("getEpisodeList: START url=${anime.url}")
        val animeId = animeIdRegex.find(anime.url)?.groupValues?.get(1)
        if (animeId.isNullOrBlank()) {
            AniDBLog.w("getEpisodeList: no animeId in url ${anime.url}")
            return emptyList()
        }

        return try {
            val apiUrl = "$baseUrl/api/frontend/anime/$animeId/episodes"
            AniDBLog.d("getEpisodeList: fetching $apiUrl")
            val response = client.newCall(GET(apiUrl, headers)).execute()
            val body = response.use { it.body?.string().orEmpty() }
            if (body.isBlank()) {
                AniDBLog.w("getEpisodeList: empty response")
                return emptyList()
            }
            val data = json.decodeFromString<EpisodesResponse>(body)
            AniDBLog.i("getEpisodeList: got ${data.episodes.size} episodes")

            data.episodes.map { ep ->
                SEpisode.create().apply {
                    episode_number = ep.number.toFloat()
                    val epName = if (ep.number2 != null && ep.number2 > 0f) {
                        "${ep.number}.${ep.number2.toInt()}"
                    } else {
                        ep.number.toString()
                    }
                    name = "Episode $epName"
                    // Mark filler in the scanlator field (project rule §8: don't cram into name)
                    scanlator = if (ep.filler && settings.markFiller) "Filler" else null
                    // URL encodes the episode ID for the video extraction step.
                    // Format: /embed-resolve/<animeSlug>/<episodeId>
                    // We store the full anime path + epId so getHosterList can reconstruct.
                    setUrlWithoutDomain("${anime.url}#ep-${ep.id}")
                }
            }.reversed()  // newest first (Aniyomi displays ascending)
        } catch (e: Exception) {
            AniDBLog.e("getEpisodeList: FAILED — ${e.message}", e)
            emptyList()
        }
    }

    // episodeListParse is never called (getEpisodeList is overridden)
    override fun episodeListParse(response: Response): List<SEpisode> = emptyList()

    // ════════════════════════════════════════════════════════════════════
    // Video extraction — ext-lib 16 NEW pipeline:
    //   getHosterList(episode) → List<Hoster> with pre-populated videoList
    //
    // Flow:
    // 1. episode.url = /anime/<slug>-<id>#ep-<epId>
    // 2. Fetch /api/frontend/episode/<epId>/languages → list of {code, name, embed_url}
    // 3. For each language (jpn=SUB, eng=DUB): fetch embed page → regex m3u8 → PlaylistUtils
    // 4. Return all videos as a single Hoster (Hoster.NO_HOSTER_LIST)
    // ════════════════════════════════════════════════════════════════════
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        AniDBLog.i("========== getHosterList START ==========")
        AniDBLog.i("getHosterList: episode.url = ${episode.url}")
        AniDBLog.i("getHosterList: episode.name = ${episode.name}")

        val epId = episodeIdRegex.find(episode.url)?.groupValues?.get(1)
        if (epId.isNullOrBlank()) {
            AniDBLog.w("getHosterList: no episodeId in url ${episode.url}")
            return emptyList()
        }

        return try {
            // 1. Fetch the languages for this episode
            val langUrl = "$baseUrl/api/frontend/episode/$epId/languages"
            AniDBLog.d("getHosterList: fetching languages: $langUrl")
            val langResponse = client.newCall(GET(langUrl, headers)).execute()
            val langBody = langResponse.use { it.body?.string().orEmpty() }
            if (langBody.isBlank()) {
                AniDBLog.w("getHosterList: empty languages response")
                return emptyList()
            }

            val langData = json.decodeFromString<LanguagesResponse>(langBody)
            AniDBLog.i("getHosterList: ${langData.languages.size} languages: ${langData.languages.map { it.code }}")

            if (langData.languages.isEmpty()) {
                AniDBLog.w("getHosterList: no languages available for episode $epId")
                return emptyList()
            }

            // 2. Extract videos for each language
            val allVideos = mutableListOf<Video>()
            for (lang in langData.languages) {
                val audioLabel = when (lang.code.lowercase()) {
                    "jpn" -> "Sub"
                    "eng" -> "Dub"
                    else -> lang.name  // fallback to the site's name
                }
                AniDBLog.d("getHosterList: extracting $audioLabel (${lang.code})")
                val videos = extractor.extract(lang.embedUrl, audioLabel)
                allVideos.addAll(videos)
            }

            AniDBLog.i("getHosterList: DONE — ${allVideos.size} total videos")

            // 3. Return as a single Hoster with all videos
            listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = allVideos))
        } catch (e: Exception) {
            AniDBLog.e("getHosterList: FAILED — ${e.message}", e)
            emptyList()
        }
    }

    // hosterListParse is never called (getHosterList is overridden)
    override fun hosterListParse(response: Response): List<Hoster> = emptyList()

    // videoListParse is for legacy-pipeline forks (pre-ext-lib-16). Not used.
    override fun videoListParse(response: Response): List<Video> = emptyList()

    /** Sort videos by the user's preferred quality + audio. */
    override fun List<Video>.sortVideos(): List<Video> {
        val preferredQuality = settings.preferredQuality
        val preferredAudio = settings.preferredAudio
        AniDBLog.d("sortVideos: preferred quality=$preferredQuality, audio=$preferredAudio, $size videos")
        return sortedWith(
            compareByDescending<Video> { it.videoTitle.contains(preferredAudio, ignoreCase = true) }
                .thenByDescending { it.videoTitle.contains(preferredQuality, ignoreCase = true) }
                .thenByDescending { it.videoTitle },
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // Seasons — AniDB has no season concept (each anime is its own entry)
    // ════════════════════════════════════════════════════════════════════
    override fun seasonListParse(response: Response): List<SAnime> = emptyList()

    // ════════════════════════════════════════════════════════════════════
    // Filters
    // ════════════════════════════════════════════════════════════════════
    override fun getFilterList(): AnimeFilterList = Filters.getFilterList()

    // ════════════════════════════════════════════════════════════════════
    // Settings
    // ════════════════════════════════════════════════════════════════════
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        settings.setupPreferenceScreen(screen)
    }

    // ════════════════════════════════════════════════════════════════════
    // Utilities
    // ════════════════════════════════════════════════════════════════════
    /** Extracts the numeric anime ID from /anime/<slug>-<id>. */
    private val animeIdRegex = Regex("""/anime/[^?#]*?-(\d+)(?:[?#]|$)""")

    /** Extracts the episode ID from the #ep-<id> fragment in episode.url. */
    private val episodeIdRegex = Regex("""#ep-(\d+)""")
}
