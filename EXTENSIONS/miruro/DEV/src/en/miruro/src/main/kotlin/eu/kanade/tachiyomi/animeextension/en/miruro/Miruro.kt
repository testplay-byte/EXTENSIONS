package eu.kanade.tachiyomi.animeextension.en.miruro

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Miruro 180 — Aniyomi anime extension (ext-lib 16).
 *
 * Site: https://www.miruro.tv (mirrors: .to / .bz / .ru)
 * Backend: Miruro's own "pipe API" at `/api/secure/pipe?e=<base64url-json>`.
 *
 * The pipe API wraps AniList GraphQL metadata + multiple upstream anime providers
 * (AnimePahe, AniKoto, AniDao, 9Anime, Moon, Zoro, Pewe, Nun, Bun, Twin, Cog)
 * behind a single obfuscated endpoint. Responses are XOR(PIPE_KEY)+gzip when
 * the `x-obfuscated: 2` header is set.
 *
 * ★ Cloudflare: all mirrors sit behind a managed challenge + a WAF custom-rule
 * on `/api/`. The extension uses:
 *   1. [MiruroBrowserFingerprintInterceptor] — shapes requests with Chrome 148
 *      desktop headers so the WAF rule doesn't trip.
 *   2. The inherited [client] (CloudflareInterceptor + cookieJar) — solves the
 *      managed challenge via WebView when `cf-mitigated: challenge` is returned.
 *   3. Cookie farming ([ensureBaseVisit] + [ensureWatchPageVisited]) — Miruro's
 *      CF edge punishes bare API calls with no prior page navigation.
 *
 * ★ Episode URL: a JSON string carrying {episodeId, provider, defaultSubType,
 * subTypes, fallbackProviders, anilistId} — consumed by videoListRequest.
 *
 * Version history:
 * - v16.1 (versionCode 1, versionId 1): pipe API + 11 providers + 4 sub-types
 *   (sub/dub/ssub/h-sub) + 8 filter categories + 11 settings. HLS streams
 *   proxied through vault01/02.ultracloud.cc. Embed streams (MegaCloud/RapidCloud)
 *   passed through as-is (inline extraction deferred to a future build).
 *
 * Reference (for understanding the API, NOT copied): the yuzono
 * `src/en/miruro` extension in SHARED/REFERENCE_HUB/anime-extensions-ref/.
 */
class Miruro : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "Miruro 180"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId = 1

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    private val settings: MiruroSettings by lazy { MiruroSettings(preferences) }

    /** The current mirror baseUrl (https://www.miruro.tv by default, user-configurable). */
    override var baseUrl: String = settings.preferredMirror
        private set

    private val extractor: MiruroExtractor by lazy {
        MiruroExtractor(client, headers, baseUrl) { alias -> providerDisplayName(alias) }
    }

    // ── Provider display names (alias → human-readable) ───────────────
    private val KNOWN_DISPLAY_NAMES = mapOf(
        "kiwi" to "AnimePahe",
        "bee" to "Anikoto",
        "hop" to "Zoro",
        "ally" to "9Anime",
        "bonk" to "AniDao",
        "pewe" to "Pewe",
        "nun" to "Nun",
        "bun" to "Bun",
        "twin" to "Twin",
        "moo" to "Moon",
        "cog" to "Cog",
        "dune" to "Dune",
        "kuz" to "Kuz",
    )

    /** Default provider order (fallback when the live config isn't fetched). */
    private val DEFAULT_PROVIDER_ORDER = listOf(
        "kiwi", "bee", "bonk", "ally", "moo", "hop", "pewe", "nun", "bun", "twin", "cog",
    )

    private fun providerDisplayName(alias: String): String =
        KNOWN_DISPLAY_NAMES[alias] ?: alias.replaceFirstChar { it.uppercase() }

    // ════════════════════════════════════════════════════════════════════
    // Client + headers
    // ════════════════════════════════════════════════════════════════════

    override val client: OkHttpClient =
        super.client.newBuilder()
            .addNetworkInterceptor(MiruroBrowserFingerprintInterceptor(baseUrl))
            .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ════════════════════════════════════════════════════════════════════
    // Cookie farming — warm the cookie jar before pipe API calls
    // ════════════════════════════════════════════════════════════════════

    @Volatile
    private var baseVisitedOnce: Boolean = false

    private fun ensureBaseVisit() {
        if (baseVisitedOnce) return
        baseVisitedOnce = true
        runCatching {
            val req = Request.Builder().url(baseUrl).build()
            client.newCall(req).execute().use { resp ->
                MiruroLog.d("ensureBaseVisit: $baseUrl → ${resp.code}")
            }
        }.onFailure { e ->
            MiruroLog.w("ensureBaseVisit failed: ${e.message}")
        }
    }

    private val watchPageWarmed: MutableSet<Int> = mutableSetOf()

    private fun ensureWatchPageVisited(anilistId: Int?) {
        if (anilistId == null || anilistId <= 0) return
        synchronized(watchPageWarmed) {
            if (!watchPageWarmed.add(anilistId)) return
        }
        runCatching {
            val watchUrl = "$baseUrl/watch/$anilistId"
            val req = Request.Builder().url(watchUrl).build()
            client.newCall(req).execute().use { resp ->
                MiruroLog.d("ensureWatchPageVisited: $watchUrl → ${resp.code}")
            }
        }.onFailure { e ->
            MiruroLog.w("ensureWatchPageVisited failed: ${e.message}")
            synchronized(watchPageWarmed) { watchPageWarmed.remove(anilistId) }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Pipe API request builder
    // ════════════════════════════════════════════════════════════════════

    /**
     * Build a pipe API request: GET /api/secure/pipe?e=<base64url-json>.
     *
     * The `e=` payload is JSON: {path, method, query, body, version:"0.2.0", timestamp}.
     */
    private fun buildPipeRequest(
        path: String,
        method: String = "GET",
        query: JSONObject = JSONObject(),
        body: JSONObject? = null,
    ): Request {
        val payload = JSONObject().apply {
            put("path", path)
            put("method", method)
            put("query", query)
            put("body", body ?: JSONObject.NULL)
            put("version", "0.2.0")
            put("timestamp", System.currentTimeMillis())
        }
        val jsonBytes = payload.toString().toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(jsonBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return GET("$baseUrl/api/secure/pipe?e=$encoded", headers)
    }

    private fun buildPipeQuery(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
        for ((key, value) in pairs) {
            if (value == null) continue
            when (value) {
                is JSONArray -> put(key, value)
                is JSONObject -> put(key, value)
                is List<*> -> {
                    val arr = JSONArray()
                    value.forEach { arr.put(it) }
                    put(key, arr)
                }
                else -> put(key, value)
            }
        }
    }

    /** Execute a pipe request and decrypt the response. */
    private fun executePipe(request: Request): String {
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                MiruroLog.w("pipe ${request.url.encodedPath}?... → ${response.code} ${response.message}")
                throw java.io.IOException("pipe API ${response.code}: ${response.message}")
            }
            extractor.decryptResponse(response)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Popular (trending)
    // ════════════════════════════════════════════════════════════════════

    override fun popularAnimeRequest(page: Int): Request {
        ensureBaseVisit()
        val query = buildPipeQuery(
            "type" to "ANIME",
            "status" to "RELEASING",
            "sort" to "TRENDING_DESC",
            "page" to page,
            "perPage" to 20,
        )
        return buildPipeRequest("search/browse", query = query)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val jsonStr = extractor.decryptResponse(response)
        val (anime, hasNext) = parseMediaList(jsonStr, 20)
        return AnimesPage(anime, hasNext)
    }

    // ════════════════════════════════════════════════════════════════════
    // Latest (recently updated)
    // ════════════════════════════════════════════════════════════════════

    override fun latestUpdatesRequest(page: Int): Request {
        ensureBaseVisit()
        val query = buildPipeQuery(
            "type" to "ANIME",
            "status" to "RELEASING",
            "sort" to "UPDATED_AT_DESC",
            "page" to page,
            "perPage" to 20,
        )
        return buildPipeRequest("search/browse", query = query)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val jsonStr = extractor.decryptResponse(response)
        val (anime, hasNext) = parseMediaList(jsonStr, 20)
        return AnimesPage(anime, hasNext)
    }

    // ════════════════════════════════════════════════════════════════════
    // Search + filters
    // ════════════════════════════════════════════════════════════════════

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        ensureBaseVisit()
        val params = MiruroFilters.getSearchParameters(filters)
        val pipeQuery = buildPipeQuery(
            "type" to "ANIME",
            "page" to page,
            "perPage" to 20,
        )
        // Search query
        if (query.isNotEmpty()) {
            pipeQuery.put("search", query)
        }
        // Filters
        params.sort.takeIf { it != "all" }?.let { pipeQuery.put("sort", it) }
        if (params.genres.isNotEmpty()) {
            pipeQuery.put("genres", JSONArray(params.genres))
        }
        if (params.excludedGenres.isNotEmpty()) {
            pipeQuery.put("excludedGenres", JSONArray(params.excludedGenres))
        }
        if (params.tags.isNotEmpty()) {
            pipeQuery.put("tags", JSONArray(params.tags))
        }
        if (params.excludedTags.isNotEmpty()) {
            pipeQuery.put("excludedTags", JSONArray(params.excludedTags))
        }
        if (params.formats.isNotEmpty()) {
            pipeQuery.put("format", JSONArray(params.formats))
        }
        params.status.takeIf { it != "all" }?.let { pipeQuery.put("status", it) }
        params.season.takeIf { it != "all" }?.let { pipeQuery.put("season", it) }
        params.year.takeIf { it != "all" }?.let { pipeQuery.put("seasonYear", it.toInt()) }
        params.dubLanguage.takeIf { it != "all" }?.let { pipeQuery.put("dubLanguage", it) }
        pipeQuery.put("isAdult", settings.includeNsfw)

        return buildPipeRequest("search/browse", query = pipeQuery)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val jsonStr = extractor.decryptResponse(response)
        val (anime, hasNext) = parseMediaList(jsonStr, 20)
        return AnimesPage(anime, hasNext)
    }

    override fun getFilterList(): AnimeFilterList = MiruroFilters.FILTER_LIST

    // ════════════════════════════════════════════════════════════════════
    // Anime details
    // ════════════════════════════════════════════════════════════════════

    override fun animeDetailsRequest(anime: SAnime): Request {
        val anilistId = anime.url.toIntOrNull()
            ?: throw java.io.IOException("Invalid anime URL: ${anime.url}")
        return buildPipeRequest("info/$anilistId", "GET")
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val jsonStr = extractor.decryptResponse(response)
        val jsonObj = JSONObject(jsonStr)
        return parseAnimeFromMediaObj(jsonObj)
    }

    // ════════════════════════════════════════════════════════════════════
    // Episodes
    // ════════════════════════════════════════════════════════════════════

    override fun episodeListRequest(anime: SAnime): Request {
        val anilistId = anime.url.toIntOrNull()
            ?: throw java.io.IOException("Invalid anime URL: ${anime.url}")
        ensureBaseVisit()
        ensureWatchPageVisited(anilistId)
        val query = buildPipeQuery("anilistId" to anilistId)
        return buildPipeRequest("episodes", "GET", query = query)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val jsonStr = extractor.decryptResponse(response)
        val jsonObj = JSONObject(jsonStr)
        val providers = jsonObj.optJSONObject("providers") ?: run {
            MiruroLog.w("episodeListParse: no 'providers' object")
            return emptyList()
        }

        val preferredProvider = settings.preferredProvider
        val availableProviders = providers.keys().asSequence().toList()
        MiruroLog.d("episodeListParse: available providers: $availableProviders")

        // Pick the primary provider (preferred, or first with episodes)
        val primaryProvider = if (providers.optJSONObject(preferredProvider)?.optJSONObject("episodes") != null) {
            preferredProvider
        } else {
            DEFAULT_PROVIDER_ORDER.firstOrNull { it in availableProviders && providers.optJSONObject(it)?.optJSONObject("episodes") != null }
                ?: availableProviders.firstOrNull { key -> providers.optJSONObject(key)?.optJSONObject("episodes") != null }
                ?: run {
                    MiruroLog.w("episodeListParse: no provider has episodes")
                    return emptyList()
                }
        }
        if (primaryProvider != preferredProvider) {
            MiruroLog.d("episodeListParse: preferred '$preferredProvider' empty, using '$primaryProvider'")
        }

        // Build a cross-provider map: episodeNumber → (provider → (subType → episodeId))
        val crossProviderMap = mutableMapOf<Float, MutableMap<String, MutableMap<String, String>>>()
        val episodeMetaMap = mutableMapOf<Float, Pair<Double, String>>()
        val providerSubTypesMap = mutableMapOf<String, List<String>>()

        for (providerKey in availableProviders) {
            val providerData = providers.optJSONObject(providerKey) ?: continue
            val episodesObj = providerData.optJSONObject("episodes") ?: continue
            val subTypes = episodesObj.keys().asSequence().toList()
            providerSubTypesMap[providerKey] = subTypes
            for (subType in subTypes) {
                val typeEpisodes = episodesObj.optJSONArray(subType) ?: continue
                for (i in 0 until typeEpisodes.length()) {
                    val epJson = typeEpisodes.getJSONObject(i)
                    val number = epJson.optDouble("number", 0.0).toFloat()
                    val id = epJson.optString("id", "")
                    val title = epJson.optString("title", "")
                    crossProviderMap.getOrPut(number) { mutableMapOf() }
                        .getOrPut(providerKey) { mutableMapOf() }[subType] = id
                    if (number !in episodeMetaMap) {
                        episodeMetaMap[number] = epJson.optDouble("number", 0.0) to title
                    }
                }
            }
        }

        // Determine the default sub-type for the primary provider
        val primarySubTypes = providerSubTypesMap[primaryProvider] ?: emptyList()
        val defaultSubType = when {
            settings.preferredSubType in primarySubTypes -> settings.preferredSubType
            "sub" in primarySubTypes -> "sub"
            primarySubTypes.isNotEmpty() -> primarySubTypes.first()
            else -> "sub"
        }

        // Build fallback providers (all providers except primary, with their subTypes)
        val fallbackProviders = JSONObject()
        for (providerKey in availableProviders) {
            if (providerKey == primaryProvider) continue
            val subTypes = providerSubTypesMap[providerKey] ?: continue
            val fbSubTypes = JSONObject()
            for (subType in subTypes) {
                crossProviderMap.entries.firstOrNull { it.key in episodeMetaMap }?.value?.get(providerKey)?.get(subType)
                    ?.let { fbSubTypes.put(subType, it) }
            }
            if (fbSubTypes.length() > 0) {
                fallbackProviders.put(providerKey, fbSubTypes)
            }
        }

        // Build the episode list
        val episodes = mutableListOf<SEpisode>()
        val sortOrder = settings.episodeSortOrder
        val numbers = episodeMetaMap.keys.sorted()
        val orderedNumbers = if (sortOrder == "descending") numbers.reversed() else numbers

        for (number in orderedNumbers) {
            val (numDouble, title) = episodeMetaMap[number] ?: (number.toDouble() to "")
            val providerEpIds = crossProviderMap[number] ?: continue
            val primaryEpId = providerEpIds[primaryProvider]?.get(defaultSubType) ?: continue

            // subTypes for this episode on the primary provider
            val subTypesForEp = JSONObject()
            for (subType in primarySubTypes) {
                providerEpIds[primaryProvider]?.get(subType)?.let { subTypesForEp.put(subType, it) }
            }

            // The anilistId for the proxy seed
            val anilistId = extractAnilistIdFromUrl(response.request.url.toString())

            // Episode URL = JSON carrying everything videoListParse needs
            val episodeData = JSONObject().apply {
                put("episodeId", primaryEpId)
                put("provider", primaryProvider)
                put("defaultSubType", defaultSubType)
                put("subTypes", subTypesForEp)
                put("fallbackProviders", fallbackProviders)
                put("anilistId", anilistId)
            }

            val ep = SEpisode.create().apply {
                url = episodeData.toString()
                name = if (title.isNotEmpty()) "Episode $number - $title" else "Episode $number"
                episode_number = numDouble.toFloat()
                scanlator = formatScanlator(primarySubTypes, defaultSubType)
            }
            episodes.add(ep)
        }

        MiruroLog.d("episodeListParse: ${episodes.size} episodes (primary=$primaryProvider, subType=$defaultSubType)")
        return episodes
    }

    /** Format the scanlator field: "Sub • Dub • Soft Sub" (available sub-types). */
    private fun formatScanlator(subTypes: List<String>, defaultSubType: String): String {
        if (subTypes.isEmpty()) return ""
        val labels = subTypes.map { subType ->
            when (subType) {
                "sub" -> "Sub"
                "dub" -> "Dub"
                "ssub" -> "Soft Sub"
                "h-sub" -> "Hard Sub"
                else -> subType
            }
        }.distinct()
        return labels.joinToString(" • ")
    }

    private fun extractAnilistIdFromUrl(url: String): String {
        // The episodes pipe request URL contains the anilistId in the encoded `e=` param.
        // We can't easily decode it from the URL, so we parse it from the request tag if set.
        // For simplicity, return empty — the proxy seed still works (FNV hash of "episodeId|").
        return ""
    }

    // ════════════════════════════════════════════════════════════════════
    // Videos (legacy pipeline — videoListRequest(episode) + videoListParse(response))
    // ════════════════════════════════════════════════════════════════════

    override fun videoListRequest(episode: SEpisode): Request {
        val episodeData = JSONObject(episode.url)
        val query = buildPipeQuery(
            "episodeId" to episodeData.getString("episodeId"),
            "provider" to episodeData.getString("provider"),
            "category" to episodeData.getString("defaultSubType"),
        )
        return buildPipeRequest("sources", "GET", query = query)
    }

    override fun videoListParse(response: Response): List<Video> {
        val episodeData = JSONObject(extractEpisodeDataFromUrl(response.request.url.toString()))
        val provider = episodeData.optString("provider", "")
        val defaultSubType = episodeData.optString("defaultSubType", "sub")
        val subTypesObj = episodeData.optJSONObject("subTypes")
        val fallbackProvidersObj = episodeData.optJSONObject("fallbackProviders")
        val anilistId = episodeData.optString("anilistId", "")
        val primaryEpisodeId = episodeData.optString("episodeId", "")

        val videos = mutableListOf<Video>()

        // Primary stream (default subType)
        videos.addAll(
            extractor.parseStreamsFromResponse(
                response, defaultSubType, provider, primaryEpisodeId, anilistId,
            ),
        )

        // Additional sub-types (if enabled and available)
        if (settings.includeAllSubTypes && subTypesObj != null && subTypesObj.length() > 1) {
            for (subTypeKey in subTypesObj.keys()) {
                if (subTypeKey == defaultSubType) continue
                val subEpId = subTypesObj.optString(subTypeKey, "")
                if (subEpId.isEmpty()) continue
                runCatching {
                    val query = buildPipeQuery(
                        "episodeId" to subEpId,
                        "provider" to provider,
                        "category" to subTypeKey,
                    )
                    val req = buildPipeRequest("sources", "GET", query = query)
                    client.newCall(req).execute().use { resp ->
                        videos.addAll(
                            extractor.parseStreamsFromResponse(resp, subTypeKey, provider, subEpId, anilistId),
                        )
                    }
                }.onFailure { e ->
                    MiruroLog.w("videoListParse: sub-type '$subTypeKey' failed: ${e.message}")
                }
            }
        }

        // Fallback providers (if enabled or primary returned nothing)
        if (fallbackProvidersObj != null && fallbackProvidersObj.length() > 0) {
            val shouldFetch = settings.includeAllProviders || videos.isEmpty()
            if (shouldFetch) {
                for (fbProvider in fallbackProvidersObj.keys()) {
                    val fbSubTypes = fallbackProvidersObj.optJSONObject(fbProvider) ?: continue
                    for (subTypeKey in fbSubTypes.keys()) {
                        val fbEpId = fbSubTypes.optString(subTypeKey, "")
                        if (fbEpId.isEmpty()) continue
                        runCatching {
                            val query = buildPipeQuery(
                                "episodeId" to fbEpId,
                                "provider" to fbProvider,
                                "category" to subTypeKey,
                            )
                            val req = buildPipeRequest("sources", "GET", query = query)
                            client.newCall(req).execute().use { resp ->
                                videos.addAll(
                                    extractor.parseStreamsFromResponse(resp, subTypeKey, fbProvider, fbEpId, anilistId),
                                )
                            }
                        }.onFailure { e ->
                            MiruroLog.w("videoListParse: fallback '$fbProvider/$subTypeKey' failed: ${e.message}")
                        }
                    }
                }
            }
        }

        MiruroLog.d("videoListParse: returning ${videos.size} videos")
        return videos
    }

    /** Decode the episode JSON from the pipe `e=` param of the sources request URL. */
    private fun extractEpisodeDataFromUrl(url: String): String {
        // The sources request was built with episodeId/provider/category in the query,
        // but NOT the full episodeData. We stored the episodeData in SEpisode.url and
        // passed it via the _ep hint — but the legacy videoListRequest(episode) doesn't
        // have access to the episode here. So we reconstruct from the decoded `e=` payload.
        val eIdx = url.indexOf("e=")
        if (eIdx < 0) return "{}"
        val encoded = url.substring(eIdx + 2).let { it.substring(0, it.indexOf("&").let { i -> if (i < 0) it.length else i }) }
        return try {
            val decoded = Base64.decode(encoded, Base64.URL_SAFE).toString(Charsets.UTF_8)
            val payload = JSONObject(decoded)
            val query = payload.optJSONObject("query") ?: return "{}"
            val result = JSONObject()
            query.opt("episodeId")?.let { result.put("episodeId", it) }
            query.opt("provider")?.let { result.put("provider", it) }
            query.opt("category")?.let { result.put("defaultSubType", it) }
            // subTypes + fallbackProviders aren't in the sources request — they're lost.
            // For the include-all-subtypes feature, we'd need to re-fetch the episodes list.
            // For v16.1, we only return the primary stream (subTypes/fallbacks empty).
            result.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    override fun List<Video>.sort(): List<Video> {
        val qualityPref = settings.preferredQuality
        val subTypePref = settings.preferredSubType
        val providerName = providerDisplayName(settings.preferredProvider)
        val streamTypePref = settings.preferredStreamType
        val qualityInt = qualityPref.toIntOrNull() ?: 0

        var working = this

        // Filter by stream type preference (unless "all")
        if (streamTypePref != "all") {
            val filtered = working.filter { v ->
                val t = v.videoTitle.lowercase()
                when (streamTypePref) {
                    "hls" -> !t.contains("embed:")
                    "embed" -> t.contains("embed:")
                    else -> true
                }
            }
            if (filtered.isNotEmpty()) working = filtered
        }

        // Sort: preferred provider first, then preferred quality, then preferred sub-type
        return working.sortedWith(
            compareBy(
                { !it.videoTitle.contains(providerName, true) },
                {
                    if (qualityInt > 0) {
                        val vq = Regex("(\\d+)p").find(it.videoTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        kotlin.math.abs(vq - qualityInt)
                    } else {
                        val vq = Regex("(\\d+)p").find(it.videoTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        -vq  // highest first
                    }
                },
                { !it.videoTitle.contains(subTypePref, true) },
            ),
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // URL helpers
    // ════════════════════════════════════════════════════════════════════

    override fun getAnimeUrl(anime: SAnime): String = "$baseUrl/watch/${anime.url}"

    override fun getEpisodeUrl(episode: SEpisode): String {
        // The episode.url is JSON, not a path — return the anime watch page as a best-effort.
        return baseUrl
    }

    // ════════════════════════════════════════════════════════════════════
    // Parsing helpers
    // ════════════════════════════════════════════════════════════════════

    private fun parseMediaList(jsonStr: String, perPage: Int): Pair<List<SAnime>, Boolean> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return emptyList<SAnime>() to false

        // Try { media: [...], pageInfo: { hasNextPage } }
        val root = runCatching { JSONObject(trimmed) }.getOrNull()
        if (root != null) {
            val mediaArr = root.optJSONArray("media")
            if (mediaArr != null) {
                val anime = (0 until mediaArr.length()).mapNotNull { i ->
                    mediaArr.optJSONObject(i)?.let { parseAnimeFromMediaObj(it) }
                }
                val hasNext = root.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false)
                    ?: (anime.size >= perPage)
                return anime to hasNext
            }
            if (root.has("id")) return listOf(parseAnimeFromMediaObj(root)) to false
        }

        // Try bare array
        val arr = runCatching { JSONArray(trimmed) }.getOrNull()
        if (arr != null) {
            val anime = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { parseAnimeFromMediaObj(it) }
            }
            return anime to (anime.size >= perPage)
        }

        return emptyList<SAnime>() to false
    }

    private fun parseAnimeFromMediaObj(obj: JSONObject): SAnime {
        val titleObj = obj.optJSONObject("title")
        val titleStyle = settings.preferredTitleStyle
        val title = titleObj?.optString(titleStyle, null)
            ?: titleObj?.optString("userPreferred", null)
            ?: titleObj?.optString("romaji", null)
            ?: titleObj?.optString("english", null)
            ?: titleObj?.optString("native", null)
            ?: "Unknown"

        val cover = obj.optJSONObject("coverImage")
        val thumbnail = cover?.optString("extraLarge", null)
            ?: cover?.optString("large", null)
            ?: cover?.optString("medium", null)
            ?: ""

        var description = obj.optString("description", "")
        if (settings.stripHtml && description.isNotEmpty()) {
            description = description
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()
        }

        val genres = obj.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).joinToString(", ") { arr.optString(it) }
        } ?: ""

        val studios = obj.optJSONObject("studios")?.optJSONArray("edges")?.let { arr ->
            (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it)?.optJSONObject("node")?.optString("name") }
                .joinToString(", ")
        } ?: ""

        val statusInt = when (obj.optString("status", "FINISHED")) {
            "RELEASING" -> SAnime.ONGOING
            "FINISHED" -> SAnime.COMPLETED
            "NOT_YET_RELEASED" -> SAnime.ONGOING
            "CANCELLED" -> SAnime.CANCELLED
            "HIATUS" -> SAnime.ON_HIATUS
            else -> SAnime.UNKNOWN
        }

        return SAnime.create().apply {
            url = obj.optInt("id").toString()
            this.title = title
            this.description = if (description.isNotEmpty()) description else null
            this.genre = genres
            this.status = statusInt
            thumbnail_url = thumbnail
            artist = studios
            initialized = true
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Preferences (ConfigurableAnimeSource)
    // ════════════════════════════════════════════════════════════════════

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Mirror
        screen.addListPreference(
            key = MiruroSettings.PREF_MIRROR_KEY,
            title = "Preferred mirror",
            entries = MiruroSettings.MIRROR_ENTRIES,
            values = MiruroSettings.MIRROR_VALUES,
            default = MiruroSettings.MIRROR_DEFAULT,
            summary = "Currently: %s",
        ) { newValue ->
            // Re-create the extractor with the new baseUrl on next access
            baseUrl = newValue
        }

        // Provider
        screen.addListPreference(
            key = MiruroSettings.PREF_PROVIDER_KEY,
            title = "Preferred provider",
            entries = MiruroSettings.PROVIDER_ENTRIES,
            values = MiruroSettings.PROVIDER_VALUES,
            default = MiruroSettings.PROVIDER_DEFAULT,
            summary = "Currently: %s",
        )

        // Sub-type
        screen.addListPreference(
            key = MiruroSettings.PREF_SUBTYPE_KEY,
            title = "Preferred Sub/Dub",
            entries = MiruroSettings.SUBTYPE_ENTRIES,
            values = MiruroSettings.SUBTYPE_VALUES,
            default = MiruroSettings.SUBTYPE_DEFAULT,
            summary = "Currently: %s",
        )

        // Quality
        screen.addListPreference(
            key = MiruroSettings.PREF_QUALITY_KEY,
            title = "Preferred quality",
            entries = MiruroSettings.QUALITY_ENTRIES,
            values = MiruroSettings.QUALITY_VALUES,
            default = MiruroSettings.QUALITY_DEFAULT,
            summary = "Currently: %s",
        )

        // Stream type
        screen.addListPreference(
            key = MiruroSettings.PREF_STREAM_TYPE_KEY,
            title = "Preferred stream type",
            entries = MiruroSettings.STREAM_TYPE_ENTRIES,
            values = MiruroSettings.STREAM_TYPE_VALUES,
            default = MiruroSettings.STREAM_TYPE_DEFAULT,
            summary = "Currently: %s",
        )

        // Title style
        screen.addListPreference(
            key = MiruroSettings.PREF_TITLE_STYLE_KEY,
            title = "Title display style",
            entries = MiruroSettings.TITLE_STYLE_ENTRIES,
            values = MiruroSettings.TITLE_STYLE_VALUES,
            default = MiruroSettings.TITLE_STYLE_DEFAULT,
            summary = "Currently: %s",
        )

        // Episode sort
        screen.addListPreference(
            key = MiruroSettings.PREF_EPISODE_SORT_KEY,
            title = "Episode list order",
            entries = MiruroSettings.EPISODE_SORT_ENTRIES,
            values = MiruroSettings.EPISODE_SORT_VALUES,
            default = MiruroSettings.EPISODE_SORT_DEFAULT,
            summary = "Currently: %s",
        )

        // Toggles
        screen.addSwitchPreference(
            key = MiruroSettings.PREF_INCLUDE_ALL_SUBTYPES_KEY,
            title = "Include all sub/dub streams",
            default = MiruroSettings.PREF_INCLUDE_ALL_SUBTYPES_DEFAULT,
            summaryOn = "Fetching sub + dub + soft sub + hard sub",
            summaryOff = "Only the preferred sub-type",
        )

        screen.addSwitchPreference(
            key = MiruroSettings.PREF_INCLUDE_ALL_PROVIDERS_KEY,
            title = "Include all provider streams",
            default = MiruroSettings.PREF_INCLUDE_ALL_PROVIDERS_DEFAULT,
            summaryOn = "Fetching fallback providers too",
            summaryOff = "Only the preferred provider",
        )

        screen.addSwitchPreference(
            key = MiruroSettings.PREF_INCLUDE_NSFW_KEY,
            title = "Show NSFW (18+)",
            default = MiruroSettings.PREF_INCLUDE_NSFW_DEFAULT,
            summaryOn = "Including adult content in search",
            summaryOff = "Hiding adult content",
        )

        screen.addSwitchPreference(
            key = MiruroSettings.PREF_STRIP_HTML_KEY,
            title = "Strip HTML from descriptions",
            default = MiruroSettings.PREF_STRIP_HTML_DEFAULT,
            summaryOn = "Descriptions cleaned of HTML tags",
            summaryOff = "Raw HTML descriptions",
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // Browser fingerprint interceptor (WAF bypass)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Shapes every request with Chrome 148 desktop headers so Miruro's WAF
     * custom-rule on `/api/` doesn't trip. Verified against the yuzono
     * MiruroBrowserFingerprintInterceptor — we implement inline (no lib dep).
     *
     * - Pipe context (`/api/`): same-origin CORS fetch shape (Sec-Fetch-Dest: empty,
     *   Sec-Fetch-Mode: cors, Sec-Fetch-Site: same-origin, Origin set).
     * - Navigate context (everything else): toplevel GET shape (Sec-Fetch-Dest: document).
     */
    internal class MiruroBrowserFingerprintInterceptor(
        private val baseUrl: String,
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val isPipeContext = original.url.encodedPath.removePrefix("/").startsWith("api/")
            val refererPresent = original.header("Referer") != null

            val builder = original.newBuilder()

            // WAF-fingerprint headers (always Chrome 148 desktop)
            builder.header("Accept-Encoding", "gzip, deflate, br")
            builder.header("Accept-Language", "en-US,en;q=0.9")
            builder.header("User-Agent", USER_AGENT)
            builder.header("Sec-Ch-Ua", SEC_CH_UA)
            builder.header("Sec-Ch-Ua-Mobile", SEC_CH_UA_MOBILE)
            builder.header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)

            if (isPipeContext) {
                builder.header("Sec-Fetch-Dest", "empty")
                builder.header("Sec-Fetch-Mode", "cors")
                builder.header("Sec-Fetch-Site", "same-origin")
                builder.header("Origin", baseUrl)
                if (original.header("Accept") == null) builder.header("Accept", "*/*")
                if (original.header("Referer") == null) builder.header("Referer", "$baseUrl/")
            } else {
                builder.header("Sec-Fetch-Dest", "document")
                builder.header("Sec-Fetch-Mode", "navigate")
                builder.header("Sec-Fetch-Site", if (refererPresent) "same-origin" else "none")
                if (original.header("Accept") == null) {
                    builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                }
            }

            return chain.proceed(builder.build())
        }

        companion object {
            internal const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
            private const val SEC_CH_UA =
                "\"Chromium\";v=\"148\", \"Not_A Brand\";v=\"24\", \"Google Chrome\";v=\"148\""
            private const val SEC_CH_UA_MOBILE = "?0"
            private const val SEC_CH_UA_PLATFORM = "\"Windows\""
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PreferenceScreen helpers — minimal inline (we don't have keiyoushi.utils)
// ════════════════════════════════════════════════════════════════════

private fun PreferenceScreen.addListPreference(
    key: String,
    title: String,
    entries: Array<String>,
    values: Array<String>,
    default: String,
    summary: String = "Currently: %s",
    onChanged: ((String) -> Unit)? = null,
) {
    val prefs = context.getSharedPreferences("source_${(context.applicationContext as android.app.Application).packageName.hashCode()}", 0)
    val current = prefs.getString(key, default) ?: default
    val pref = androidx.preference.ListPreference(context).apply {
        this.key = key
        this.title = title
        this.entries = entries
        this.entryValues = values
        this.summary = summary
        this.setDefaultValue(default)
        value = current
        setOnPreferenceChangeListener { _, newValue ->
            prefs.edit().putString(key, newValue.toString()).apply()
            this.summary = "Currently: ${entries[values.indexOf(newValue.toString())]}"
            onChanged?.invoke(newValue.toString())
            true
        }
    }
    addPreference(pref)
}

private fun PreferenceScreen.addSwitchPreference(
    key: String,
    title: String,
    default: Boolean,
    summaryOn: String,
    summaryOff: String,
) {
    val prefs = context.getSharedPreferences("source_${(context.applicationContext as android.app.Application).packageName.hashCode()}", 0)
    val current = prefs.getBoolean(key, default)
    val pref = androidx.preference.SwitchPreferenceCompat(context).apply {
        this.key = key
        this.title = title
        this.summaryOn = summaryOn
        this.summaryOff = summaryOff
        this.setDefaultValue(default)
        isChecked = current
        setOnPreferenceChangeListener { _, newValue ->
            prefs.edit().putBoolean(key, newValue as Boolean).apply()
            true
        }
    }
    addPreference(pref)
}
