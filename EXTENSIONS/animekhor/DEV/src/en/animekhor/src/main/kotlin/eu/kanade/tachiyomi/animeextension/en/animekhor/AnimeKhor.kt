package eu.kanade.tachiyomi.animeextension.en.animekhor

import android.app.Application
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.en.animekhor.extractors.AbyssExtractor
import eu.kanade.tachiyomi.animeextension.en.animekhor.extractors.DailymotionExtractor
import eu.kanade.tachiyomi.animeextension.en.animekhor.extractors.DoodExtractor
import eu.kanade.tachiyomi.animeextension.en.animekhor.extractors.OkruExtractor
import eu.kanade.tachiyomi.animeextension.en.animekhor.extractors.RumbleExtractor
import eu.kanade.tachiyomi.animeextension.en.animekhor.extractors.StreamWishExtractor
import eu.kanade.tachiyomi.animeextension.en.animekhor.extractors.VidHideExtractor
import eu.kanade.tachiyomi.animeextension.en.animekhor.extractors.VidaraExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.util.parallelCatchingFlatMapBlocking
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * AnimeKhor 180 — animekhor.org (WordPress "animestream"-family theme).
 *
 * Live-verified 2026-09-13 (session: animekhor-01). All selectors below were confirmed
 * against the live site (Cloudflare-protected; the app's inherited client + CloudflareInterceptor
 * handles the challenge on-device).
 *
 * Verified structures:
 *  - Catalog:  GET /anime/?page=N&order=popular|update  (+ genre[]/studio[]/status/type/sub/order filters)
 *  - Search:   GET /page/N/?s=QUERY — WordPress search returns mostly EPISODE posts on this site,
 *              so each episode result is mapped back to its series (/anime/<slug-before--episode>/).
 *  - Details:  h1.entry-title / div.thumb img / div.genxed a / .entry-content[itemprop=description] / div.spe span
 *  - Episodes: div.eplister > ul > li > a  (.epl-num / .epl-title / .epl-date, no .epl-sub on this site)
 *  - Mirrors:  select.mirror > option[data-index] — option value = base64 of an <iframe> HTML snippet
 *              (sometimes a bare URL); decode → extract src → dispatch to the matching extractor.
 *  - Hosters (live-verified where marked ✅):
 *      dailymotion.com ✅  ok.ru ✅  vidara.to ✅  abyssplayer ✅  streamwish (ported upstream logic)
 *      vidhide family: ahvsh.com / sbsonic.com (ported)   dood: d000d.com (ported)   rumble (ported)
 *    Skipped gracefully (broken / SPA-only): animeabc.xyz (broken upstream too), upns.live,
 *      p2pstream.vip, bysekoze.com, turbovidhls.com (no id in embed src), dead "DPlayer" options.
 */
class AnimeKhor : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "AnimeKhor 180"

    override val baseUrl = "https://animekhor.org"

    override val lang = "en"

    override val supportsLatest = true

    override val versionId = 1

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Helpers ===============================

    private fun apiHeadersBuilder() = headers.newBuilder().apply {
        add("Referer", "$baseUrl/")
    }

    private fun String.orNull() = this.takeIf { it.isNotBlank() }

    private fun String.toAbsoluteUrl(): String = when {
        startsWith("http") -> this
        startsWith("//") -> "https:$this"
        else -> baseUrl + this
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/anime/?page=$page&order=popular", apiHeadersBuilder().build())

    override fun popularAnimeParse(response: Response): AnimesPage {
        fetchFiltersIfNeeded()
        return parseCatalog(response)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/anime/?page=$page&order=update", apiHeadersBuilder().build())

    override fun latestUpdatesParse(response: Response): AnimesPage {
        fetchFiltersIfNeeded()
        return parseCatalog(response)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = AnimeKhorFilters.getSearchParameters(filters)
        return if (query.isNotEmpty()) {
            // WordPress search — returns mostly episode posts; mapped to series in the parse step.
            GET("$baseUrl/page/$page/?s=${query.trim()}", apiHeadersBuilder().build())
        } else {
            val url = buildString {
                append("$baseUrl/anime/?page=$page")
                if (params.genres.isNotEmpty()) append("&${params.genres}")
                if (params.studios.isNotEmpty()) append("&${params.studios}")
                append("&status=${params.status}")
                append("&type=${params.type.replace(" ", "%20")}")
                append("&sub=${params.sub}")
                append("&order=${params.order}")
            }
            GET(url, apiHeadersBuilder().build())
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        fetchFiltersIfNeeded()
        return parseSearch(response)
    }

    /**
     * Catalog parse for /anime/ pages (popular/latest/filter-browse): every article is a series.
     */
    private fun parseCatalog(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animes = document.select(catalogSelector()).map(::catalogAnimeFromElement)
        val hasNext = document.select(catalogNextPageSelector()).isNotEmpty()
        return AnimesPage(animes, hasNext)
    }

    private fun catalogSelector() = "div.listupd article a.tip"

    private fun catalogNextPageSelector() = "div.pagination a.next, div.hpage > a.r"

    private fun catalogAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.selectFirst("div.tt, div.ttl")?.ownText().orNull()
            ?: element.attr("title").orNull()
            ?: element.selectFirst("div.tt h2, div.ttl h2")?.text()
            ?: "Unknown"
        thumbnail_url = element.selectFirst("img")?.getImageUrl()
    }

    /**
     * Search parse: the live site's `?s=` results are mostly EPISODE posts (each article has
     * div.tt but with an empty ownText — the title lives in a child h2). Map each episode post
     * back to its series:
     *   https://animekhor.org/<slug>-episode-N-.../      → /anime/<slug>/
     *   https://animekhor.org/<slug>-episodes-N-to-M-... → /anime/<slug>/
     * Series posts (with /anime/ URLs) are kept as-is. Anything unmappable is skipped, and
     * duplicates (many episodes of the same series) are de-duplicated keeping first occurrence.
     */
    private fun parseSearch(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animes = ArrayList<SAnime>()
        val seen = HashSet<String>()

        document.select(catalogSelector()).forEach { element ->
            val rawUrl = element.attr("abs:href")
            val seriesUrl = when {
                rawUrl.contains("/anime/") -> rawUrl
                EPISODE_SLUG_REGEX.containsMatchIn(rawUrl) ->
                    "$baseUrl/anime/${EPISODE_SLUG_REGEX.find(rawUrl)!!.groupValues[1]}/"
                else -> null
            } ?: return@forEach

            if (seen.add(seriesUrl)) {
                animes += SAnime.create().apply {
                    setUrlWithoutDomain(seriesUrl)
                    title = cleanSearchTitle(element).orNull() ?: seriesUrl.substringAfterLast('/', "").removeSuffix("/")
                    thumbnail_url = element.selectFirst("img")?.getImageUrl()
                }
            }
        }
        // WordPress search pagination: /page/N/?s=… — next link exists → has next page.
        val hasNext = document.select(catalogNextPageSelector()).isNotEmpty() ||
            document.select("a[href*=/page/], div.pagination a").isNotEmpty()
        return AnimesPage(animes, hasNext)
    }

    private fun cleanSearchTitle(element: Element): String? {
        val raw = element.attr("title").orNull()
            ?: element.selectFirst("div.tt")?.text().orNull()
            ?: element.selectFirst("a[title]")?.attr("title")
            ?: return null
        // "Renegade Immortal Episode 158 Subtitles [ENGLISH + INDONESIAN]" → "Renegade Immortal"
        // "Eternal First God Episodes 81 to 90 ..." → "Eternal First God"
        return EPISODE_TITLE_TAIL_REGEX.replace(raw, "").trim().removeSurrounding("[", "]").trim()
            .takeIf { it.isNotBlank() }
    }

    // ============================== Filters ===============================

    private fun fetchFiltersIfNeeded() {
        if (AnimeKhorFilters.isInitialized()) return
        runBlocking {
            withContext(Dispatchers.IO) {
                runCatching {
                    val document = client.newCall(GET("$baseUrl/anime/", apiHeadersBuilder().build()))
                        .execute()
                        .asJsoup()
                    AnimeKhorFilters.initialize(document)
                }
            }
        }
    }

    override fun getFilterList(): AnimeFilterList = AnimeKhorFilters.FILTER_LIST

    // ============================ Anime Details ===========================

    override fun animeDetailsRequest(anime: SAnime): Request =
        GET(baseUrl + anime.url, apiHeadersBuilder().build())

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        return SAnime.create().apply {
            setUrlWithoutDomain(document.location().removePrefix(baseUrl).ifEmpty { animeDetailsPathFallback(document) })
            title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"

            val infos = document.selectFirst("div.info-content, div.right ul.data")

            genre = infos?.select("div.genxed > a")?.eachText()?.joinToString(", ")?.takeIf { it.isNotBlank() }
                ?: infos?.select("li:contains(Genre:) a")?.eachText()?.joinToString(", ")

            status = parseStatus(infos?.getInfo("Status"))

            artist = infos?.getInfo("Studio")
            author = infos?.getInfo("Fansub") ?: infos?.getInfo("Fansubber")

            description = buildString {
                document.selectFirst(".entry-content[itemprop=description], .desc")?.text()?.let {
                    if (it.isNotBlank()) append("$it\n\n")
                }
                document.selectFirst(".alter")?.text()?.takeIf(String::isNotBlank)?.let {
                    append("Alternative name(s): $it\n")
                }
                infos?.select("div.spe > span, li:has(b)")?.eachText()?.forEach { append("$it\n") }
            }.trim()

            thumbnail_url = document.selectFirst("div.thumb > img, div.limage > img")?.getImageUrl()
        }
    }

    private fun animeDetailsPathFallback(document: Document): String {
        // canonical link as a last resort
        val canonical = document.selectFirst("link[rel=canonical]")?.attr("href") ?: return "/"
        return canonical.removePrefix(baseUrl)
    }

    private fun parseStatus(statusString: String?): Int = when (statusString?.trim()?.lowercase()) {
        "completed" -> SAnime.COMPLETED
        "ongoing" -> SAnime.ONGOING
        "upcoming" -> SAnime.UNKNOWN
        "hiatus" -> SAnime.ON_HIATUS
        else -> SAnime.UNKNOWN
    }

    private fun Element.getInfo(text: String): String? = selectFirst("span:contains($text)")?.run {
        selectFirst("a")?.text().orNull() ?: ownText().orNull()
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request =
        GET(baseUrl + anime.url, apiHeadersBuilder().build())

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select("div.eplister > ul > li > a").map(::episodeFromElement)
    }

    private fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        val num = element.selectFirst(".epl-num")?.text()?.trim().orEmpty()
        val siteTitle = element.selectFirst(".epl-title")?.text()?.trim().orEmpty()
        name = if (siteTitle.isNotBlank() && !siteTitle.equals("Episode $num", ignoreCase = true)) {
            "Ep. $num - $siteTitle"
        } else {
            "Episode $num"
        }
        episode_number = num.toFloatOrNull() ?: 0f
        scanlator = element.selectFirst(".epl-sub")?.text()?.takeIf(String::isNotBlank)
        date_upload = element.selectFirst(".epl-date")?.text()?.toDate() ?: 0L
    }

    private fun String?.toDate(): Long = this?.let {
        runCatching { dateFormatter.parse(trim())?.time }.getOrNull()
    } ?: 0L

    private val dateFormatter by lazy { SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH) }

    // ============================ Video Links =============================
    // Follows the tolerant-dispatch pattern proven in AniKoto: fetch the episode page once,
    // decode every mirror option, then extract each hoster INDEPENDENTLY — one broken/dead
    // hoster never removes the others (dead embeds are common on this site, e.g. the
    // "DPlayer → Video Not Available" options).

    override fun hosterListParse(response: Response): List<eu.kanade.tachiyomi.animesource.model.Hoster> = emptyList()
    override fun videoListParse(response: Response): List<Video> = emptyList()
    override fun seasonListParse(response: Response): List<SAnime> = emptyList()

    /**
     * LEGACY flat pipeline (ext-lib-16 forks that haven't adopted the hoster pipeline call
     * this). Same tolerant extraction as the modern path, flattened to a plain video list.
     */
    override suspend fun getVideoList(episode: SEpisode): List<Video> =
        runCatching { extractEpisodeVideos(episode) }.getOrDefault(emptyList())

    private suspend fun extractEpisodeVideos(episode: SEpisode): List<Video> {
        val response = client.newCall(GET(baseUrl + episode.url, apiHeadersBuilder().build())).execute()
        response.use { resp ->
            val document = resp.asJsoup()
            val embeds = document.select("select.mirror > option[data-index], ul.mirror a[data-em]")
                .mapNotNull { it.toEmbedUrl() }
            if (embeds.isEmpty()) return emptyList()

            return videosFromEmbeds(embeds, episode)
        }
    }

    /**
     * Decodes a mirror element into an absolute embed URL.
     * - `option value=` / `a data-em=` carries either base64("<iframe …>") or base64("https://…")
     *   or rarely a plain URL (verified live on 2026-09-13).
     */
    private fun Element.toEmbedUrl(): String? {
        val encoded = when (tagName()) {
            "option" -> attr("value")
            "a" -> attr("data-em")
            else -> return null
        }
        if (encoded.isBlank()) return null

        val decoded: String = if (encoded.startsWith("http")) {
            encoded
        } else {
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
            String(bytes)
        }

        val doc = Jsoup.parse(decoded)
        val iframeSrc = doc.selectFirst("iframe[src~=.]")?.attr("abs:src")
            ?: doc.selectFirst("iframe[src~=.]")?.attr("src")
        val direct = doc.selectFirst("meta[itemprop=embedUrl]")?.attr("content")

        return (iframeSrc ?: direct ?: decoded.takeIf { it.startsWith("http") })
            ?.let { raw ->
                when {
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("http") -> raw
                    else -> null
                }
            }
    }

    private suspend fun videosFromEmbeds(embeds: List<String>, episode: SEpisode): List<Video> {
        val results = embeds.parallelCatchingFlatMapBlocking { embed ->
            extractFromEmbed(embed)
        }
        return sortVideos(results)
    }

    private suspend fun extractFromEmbed(url: String): List<Video> {
        val lower = url.lowercase()
        val host = runCatching { URI(url).host }.getOrDefault("")

        return runCatching {
            when {
                host.endsWith("dailymotion.com") ->
                    DailymotionExtractor(client).videosFromUrl(url)

                host == "ok.ru" || host.endsWith("ok.ru") ->
                    OkruExtractor(client, apiHeadersBuilder().build()).videosFromUrl(url)

                host.endsWith("rumble.com") ->
                    RumbleExtractor(client, apiHeadersBuilder().build()).videosFromUrl(url)

                "streamwish" in lower || host in STREAMWISH_DOMAINS ->
                    StreamWishExtractor(client, apiHeadersBuilder().build()).videosFromUrl(url) { q -> "StreamWish - $q" }

                host in VIDHIDE_DOMAINS ->
                    VidHideExtractor(client, apiHeadersBuilder().build()).videosFromUrl(url) { q -> "VidHide - $q" }

                host in DOOD_DOMAINS ->
                    DoodExtractor(client).videosFromUrl(url, "Doodstream")

                host.endsWith("vidara.to") ->
                    VidaraExtractor(client).videosFromUrl(url)

                host.endsWith("abyssplayer.com") || host.endsWith("abyss.to") ->
                    AbyssExtractor(client).videosFromUrl(url)

                // ★ Deferred (SPA players / broken): upns.live, p2pstream.vip, bysekoze.com,
                // turbovidhls.com, animeabc.xyz. See MEMORY/sites/site-analysis.md §hosters.
                else -> {
                    android.util.Log.i("AnimeKhor", "Skipping unsupported hoster: $url")
                    emptyList()
                }
            }
        }.getOrElse {
            android.util.Log.w("AnimeKhor", "Extraction failed for $url — ${it.message}")
            emptyList()
        }
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_ENTRIES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_QUALITY_KEY, newValue as String).commit()
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_SERVER_KEY
            title = "Preferred server"
            entries = PREF_SERVER_ENTRIES
            entryValues = PREF_SERVER_ENTRIES
            setDefaultValue(PREF_SERVER_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_SERVER_KEY, newValue as String).commit()
            }
        }.also(screen::addPreference)
    }

    private val preferredQuality: String
        get() = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT

    private val preferredServer: String
        get() = preferences.getString(PREF_SERVER_KEY, PREF_SERVER_DEFAULT) ?: PREF_SERVER_DEFAULT

    // =============================== Sorting ==============================

    private fun sortVideos(videos: List<Video>): List<Video> = videos.sortedWith(
        compareByDescending<Video> { it.videoTitle.contains(preferredServer, ignoreCase = true) }
            .thenByDescending { it.videoTitle.contains(preferredQuality, ignoreCase = true) }
            .thenByDescending { it.resolution ?: 0 },
    )

    // ============================= Utilities ==============================

    private fun Element.getImageUrl(): String? = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> attr("abs:src")
    }.takeIf(String::isNotBlank)?.substringBefore("?resize")

    companion object {
        // Matches the /anime/<slug> prefix of an episode post URL:
        //  /fog-hill-of-five-elements-episode-3-english-subtitles/  → fog-hill-of-five-elements
        //  /eternal-first-god-episodes-81-to-90-subtitles-english-indonesian/ → eternal-first-god
        private val EPISODE_SLUG_REGEX = Regex("""animekhor\.org/([a-z0-9-]+)-episode""")
        private val EPISODE_TITLE_TAIL_REGEX =
            Regex("""\s+Episodes?\s+\d+.*$""", RegexOption.IGNORE_CASE)

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")

        private const val PREF_SERVER_KEY = "preferred_server"
        private const val PREF_SERVER_DEFAULT = "Auto"
        private val PREF_SERVER_ENTRIES = arrayOf(
            "Auto", "Dailymotion", "StreamWish", "VidHide", "Doodstream", "Okru", "Vidara", "Abyss",
        )

        // Hosters that speak the vidhide platform protocol (live-verified mirror sets differ
        // per episode age; ahvsh/sbsonic appear on 2023-era uploads).
        private val VIDHIDE_DOMAINS = setOf("ahvsh.com", "sbsonic.com", "vhsix.com", "vidhide.com", "vidhidepro.com", "filemoon.sx")

        private val DOOD_DOMAINS = setOf("d000d.com", "dood.watch", "doodstream.com", "dood.to", "dood.la", "dood.yt", "ds2play.com", "dooood.com")

        private val STREAMWISH_DOMAINS = setOf("streamwish.to", "streamwish.com", "wishfast.top", "awish.pro", "streamwish.su")
    }
}
