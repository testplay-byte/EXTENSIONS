package eu.kanade.tachiyomi.animeextension.en.animepahe.metadata

import eu.kanade.tachiyomi.animeextension.en.animepahe.AnimepaheLog
import eu.kanade.tachiyomi.animeextension.en.animepahe.WebViewFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull

/**
 * ★ Ported from AniKoto's EpisodeMetadataFetcher (sessions 35-38).
 *
 * Fetches episode metadata (thumbnails + descriptions + titles) from multiple sources:
 *
 * Sources (priority order):
 * 1. Jikan (MyAnimeList API) — episode TITLES + air dates. NOT behind Cloudflare (OkHttp works).
 * 2. AniList — MAL ID → AniList ID + banner + streamingEpisodes thumbnails. Behind Cloudflare (WebView).
 * 3. Anikage.cc (TheTVDB) — thumbnails + descriptions + titles. NOT behind Cloudflare (OkHttp works).
 * 4. Kitsu — thumbnails + descriptions + titles. Behind Cloudflare (WebView).
 *
 * Merge priority:
 * - Thumbnail: Anikage → AniList streamingEpisodes → Kitsu → banner → anime cover
 * - Title:     Jikan → Anikage → Kitsu
 * - Description: Anikage → Kitsu
 *
 * Never throws — on any error, returns an empty map, episodes load without enrichment.
 *
 * @param client The inherited OkHttpClient (has CloudflareInterceptor for the main site).
 *               Used for Jikan + Anikage (non-Cloudflare hosts).
 * @param json Json parser instance.
 * @param webViewFetcher Used for AniList + Kitsu (Cloudflare-protected hosts that block OkHttp).
 */
class EpisodeMetadataFetcher(
    private val client: OkHttpClient,
    private val json: Json,
    private val webViewFetcher: WebViewFetcher? = null,
) {
    data class EpisodeMetadata(
        val title: String?,
        val description: String?,
        val thumbnailUrl: String?,
        val airdate: String?,
    )

    private data class CachedData(
        val episodes: Map<Int, EpisodeMetadata>,
        val bannerUrl: String?,
    )

    private val cache = mutableMapOf<String, CachedData>()
    private val anilistStreamingCache = mutableMapOf<String, List<AniListStreamingEpisode>>()
    private val anilistBannerCache = mutableMapOf<String, String?>()
    private val apiHeaders = Headers.Builder()
        .set("User-Agent", BROWSER_UA)
        .set("Accept", "application/json, application/vnd.api+json, text/html, */*")
        .build()

    /**
     * Fetch episode metadata for an anime.
     *
     * @param malId The MyAnimeList anime ID (extracted from animepahe's detail page external links)
     * @param fallbackThumbnailUrl The anime's cover image URL (used if no episode thumbnails)
     * @return Map<episodeNumber, EpisodeMetadata>. Empty on any error.
     */
    suspend fun fetch(malId: String, fallbackThumbnailUrl: String?): Map<Int, EpisodeMetadata> {
        if (malId.isBlank()) return emptyMap()

        synchronized(cache) {
            cache[malId]?.let { return applyFallbackThumbnail(it.episodes, it.bannerUrl, fallbackThumbnailUrl) }
        }

        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                AnimepaheLog.d("EpisodeMetadataFetcher: fetching for malId=$malId")

                val jikanEps = fetchJikanEpisodes(malId)
                val anilistId = fetchAniListId(malId)
                val anikageEps = if (anilistId != null) fetchAnikageEpisodes(anilistId) else emptyMap()
                val kitsuEps = fetchKitsuEpisodes(malId)
                val anilistStreaming = anilistStreamingCache[malId] ?: emptyList()
                val bannerUrl = anilistBannerCache[malId]

                val merged = mergeEpisodes(anikageEps, kitsuEps, anilistStreaming, jikanEps)

                val cached = CachedData(merged, bannerUrl)
                synchronized(cache) { cache[malId] = cached }

                val result = applyFallbackThumbnail(merged, bannerUrl, fallbackThumbnailUrl)
                val elapsed = System.currentTimeMillis() - startTime
                AnimepaheLog.i("EpisodeMetadataFetcher: fetched ${result.size} episodes for malId=$malId in ${elapsed}ms (jikan=${jikanEps.size}, anikage=${anikageEps.size}, kitsu=${kitsuEps.size}, banner=${bannerUrl != null})")
                result
            } catch (e: Exception) {
                AnimepaheLog.w("EpisodeMetadataFetcher: failed for malId=$malId — ${e.message}. Episodes will load without enrichment.")
                synchronized(cache) { cache[malId] = CachedData(emptyMap(), null) }
                emptyMap()
            }
        }
    }

    private fun applyFallbackThumbnail(
        episodes: Map<Int, EpisodeMetadata>,
        bannerUrl: String?,
        animeCoverUrl: String?,
    ): Map<Int, EpisodeMetadata> {
        if (episodes.isEmpty()) return episodes
        return episodes.mapValues { (_, meta) ->
            val thumb = meta.thumbnailUrl ?: bannerUrl ?: animeCoverUrl
            meta.copy(thumbnailUrl = thumb)
        }
    }

    private fun mergeEpisodes(
        anikage: Map<Int, EpisodeMetadata>,
        kitsu: Map<Int, EpisodeMetadata>,
        anilistStreaming: List<AniListStreamingEpisode>,
        jikan: Map<Int, JikanEpisode>,
    ): Map<Int, EpisodeMetadata> {
        val anilistByNum = anilistStreaming.mapIndexedNotNull { idx, ep ->
            (idx + 1) to ep
        }.toMap()

        val allKeys = anikage.keys + kitsu.keys + anilistByNum.keys + jikan.keys
        return allKeys.associateWith { num ->
            val ak = anikage[num]
            val al = anilistByNum[num]
            val k = kitsu[num]
            val jk = jikan[num]
            EpisodeMetadata(
                title = jk?.title ?: ak?.title ?: k?.title,
                description = ak?.description ?: k?.description,
                thumbnailUrl = ak?.thumbnailUrl ?: al?.thumbnail ?: k?.thumbnailUrl,
                airdate = jk?.aired ?: ak?.airdate ?: k?.airdate,
            )
        }
    }

    // ── AniList: MAL ID → AniList ID + banner + streamingEpisodes ──────────

    private fun fetchAniListId(malId: String): String? {
        val query = "query { Media(idMal: $malId, type: ANIME) { id bannerImage streamingEpisodes { title thumbnail } } }"
        val body = """{"query":"$query"}"""
        val respBody = postJson("https://graphql.anilist.co", body) ?: return null
        return try {
            val resp = json.decodeFromString(AniListMediaResponse.serializer(), respBody)
            val media = resp.data?.media
            val id = media?.id
            AnimepaheLog.d("EpisodeMetadataFetcher: AniList ID for malId=$malId → $id (streamingEpisodes=${media?.streamingEpisodes?.size ?: 0})")
            if (id != null) {
                anilistStreamingCache[malId] = media?.streamingEpisodes ?: emptyList()
                anilistBannerCache[malId] = media?.bannerImage
            }
            id?.toString()
        } catch (e: Exception) {
            AnimepaheLog.d("EpisodeMetadataFetcher: AniList ID parse failed — ${e.message}")
            null
        }
    }

    // ── Anikage.cc (TheTVDB) — primary source ────────────────────────────────

    private fun fetchAnikageEpisodes(anilistId: String): Map<Int, EpisodeMetadata> {
        val url = "https://anikage.cc/api/media/anime/$anilistId/episodes"
        val body = fetchString(url) ?: return emptyMap()
        val episodes = try {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AnikageEpisode.serializer()), body)
        } catch (e: Exception) {
            AnimepaheLog.d("EpisodeMetadataFetcher: Anikage parse failed — ${e.message}")
            return emptyMap()
        }
        val result = mutableMapOf<Int, EpisodeMetadata>()
        for (ep in episodes) {
            val num = ep.number ?: continue
            result[num] = EpisodeMetadata(
                title = ep.title?.takeIf { it.isNotBlank() },
                description = ep.description?.takeIf { it.isNotBlank() }?.let { stripHtml(it) },
                thumbnailUrl = ep.img?.takeIf { it.isNotBlank() },
                airdate = ep.airDate?.takeIf { it.isNotBlank() },
            )
        }
        AnimepaheLog.d("EpisodeMetadataFetcher: Anikage returned ${result.size} episodes")
        return result
    }

    // ── Kitsu — fallback source ──────────────────────────────────────────────

    private fun fetchKitsuEpisodes(malId: String): Map<Int, EpisodeMetadata> {
        val kitsuId = fetchKitsuId(malId) ?: return emptyMap()
        val result = mutableMapOf<Int, EpisodeMetadata>()
        var nextUrl: String? = "https://kitsu.app/api/edge/anime/$kitsuId/episodes?page[limit]=20&sort=number"
        var pageCount = 0
        val maxPages = 10

        while (nextUrl != null && pageCount < maxPages) {
            pageCount++
            val body = fetchString(nextUrl!!) ?: break
            val response = try {
                json.decodeFromString(KitsuEpisodesResponse.serializer(), body)
            } catch (e: Exception) {
                break
            }
            for (ep in response.data) {
                val attrs = ep.attributes ?: continue
                val number = attrs.number ?: continue
                val thumbUrl = attrs.thumbnail?.original
                result[number] = EpisodeMetadata(
                    title = attrs.canonicalTitle?.takeIf { it.isNotBlank() },
                    description = attrs.description?.takeIf { it.isNotBlank() }?.let { stripHtml(it) },
                    thumbnailUrl = thumbUrl?.takeIf { it.isNotBlank() },
                    airdate = attrs.airdate?.takeIf { it.isNotBlank() },
                )
            }
            nextUrl = response.links?.next
        }
        AnimepaheLog.d("EpisodeMetadataFetcher: Kitsu returned ${result.size} episodes")
        return result
    }

    private fun fetchKitsuId(malId: String): String? {
        val url = "https://kitsu.app/api/edge/mappings" +
            "?filter[externalSite]=myanimelist/anime" +
            "&filter[externalId]=$malId" +
            "&include=item"
        val body = fetchString(url) ?: return null
        val response = try {
            json.decodeFromString(KitsuMappingResponse.serializer(), body)
        } catch (e: Exception) {
            return null
        }
        val anime = response.included?.firstOrNull { it.type == "anime" }
        return anime?.id
    }

    // ── Jikan (MyAnimeList) — episode titles + air dates ─────────────────────

    private fun fetchJikanEpisodes(malId: String): Map<Int, JikanEpisode> {
        val url = "https://api.jikan.moe/v4/anime/$malId/episodes"
        val body = fetchString(url) ?: return emptyMap()
        val response = try {
            json.decodeFromString(JikanEpisodesResponse.serializer(), body)
        } catch (e: Exception) {
            AnimepaheLog.d("EpisodeMetadataFetcher: Jikan parse failed — ${e.message}")
            return emptyMap()
        }
        val result = mutableMapOf<Int, JikanEpisode>()
        for (ep in response.data) {
            val num = ep.malId ?: continue
            result[num] = JikanEpisode(
                title = ep.title?.takeIf { it.isNotBlank() },
                aired = ep.aired?.takeIf { it.isNotBlank() },
            )
        }
        AnimepaheLog.d("EpisodeMetadataFetcher: Jikan returned ${result.size} episodes")
        return result
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────
    // ★ Strategy: try OkHttp FIRST (faster, simpler). Fall back to WebView (Chrome TLS)
    // only if OkHttp returns 403/blocked. In practice, OkHttp works for AniList + Anikage
    // on most devices — the WebView is a safety net for TLS-fingerprint blocking.
    //
    // ★ Why not WebView-first (like AniKoto): AniKoto loads megaplay.buzz as the WebView
    // origin — a light-Cloudflare CDN. AnimePahe's site (animepahe.pw) is behind a HARD
    // Cloudflare managed challenge (Turnstile). Loading it as the WebView origin shows the
    // challenge page, whose strict CSP (`default-src 'none'`) blocks ALL cross-origin fetch().
    // So WebView-first FAILS for animepahe. OkHttp-first + data:URL-origin WebView fallback works.

    private fun fetchString(url: String): String? {
        // Try OkHttp first
        try {
            val req = Request.Builder().url(url).headers(apiHeaders).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    return resp.body?.string()
                }
                AnimepaheLog.d("EpisodeMetadataFetcher: OkHttp HTTP ${resp.code} for ${AnimepaheLog.trunc(url, 80)}")
            }
        } catch (e: Exception) {
            AnimepaheLog.d("EpisodeMetadataFetcher: OkHttp fetch failed for ${AnimepaheLog.trunc(url, 60)} — ${e.message}")
        }

        // Fall back to WebView (Chrome TLS) if available
        if (webViewFetcher != null) {
            return try {
                AnimepaheLog.d("EpisodeMetadataFetcher: falling back to WebView for ${AnimepaheLog.trunc(url, 60)}")
                webViewFetcher.fetchText(url)
            } catch (e: Exception) {
                AnimepaheLog.d("EpisodeMetadataFetcher: WebView fetch failed for ${AnimepaheLog.trunc(url, 60)} — ${e.message}")
                null
            }
        }
        return null
    }

    private fun postJson(url: String, jsonBody: String): String? {
        // Try OkHttp first
        try {
            val body = okhttp3.RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                jsonBody,
            )
            val req = Request.Builder().url(url).headers(apiHeaders).post(body).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    return resp.body?.string()
                }
                AnimepaheLog.d("EpisodeMetadataFetcher: OkHttp POST HTTP ${resp.code} for ${AnimepaheLog.trunc(url, 60)}")
            }
        } catch (e: Exception) {
            AnimepaheLog.d("EpisodeMetadataFetcher: OkHttp POST failed for ${AnimepaheLog.trunc(url, 60)} — ${e.message}")
        }

        // Fall back to WebView (Chrome TLS) if available
        if (webViewFetcher != null) {
            return try {
                AnimepaheLog.d("EpisodeMetadataFetcher: falling back to WebView POST for ${AnimepaheLog.trunc(url, 60)}")
                webViewFetcher.postJson(url, jsonBody)
            } catch (e: Exception) {
                AnimepaheLog.d("EpisodeMetadataFetcher: WebView POST failed — ${e.message}")
                null
            }
        }
        return null
    }

    private fun stripHtml(text: String): String =
        text.replace(Regex("<[^>]+>"), "").trim()

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Serializable
    private data class AniListMediaResponse(val data: AniListMediaData? = null)

    @Serializable
    private data class AniListMediaData(@SerialName("Media") val media: AniListMedia? = null)

    @Serializable
    private data class AniListMedia(
        val id: Int? = null,
        @SerialName("bannerImage") val bannerImage: String? = null,
        @SerialName("streamingEpisodes") val streamingEpisodes: List<AniListStreamingEpisode>? = null,
    )

    @Serializable
    private data class AniListStreamingEpisode(
        val title: String? = null,
        val thumbnail: String? = null,
    )

    @Serializable
    private data class AnikageEpisode(
        val number: Int? = null,
        val title: String? = null,
        val description: String? = null,
        val img: String? = null,
        @SerialName("airDate") val airDate: String? = null,
    )

    @Serializable
    private data class KitsuMappingResponse(
        val data: List<MappingData> = emptyList(),
        val included: List<KitsuAnime>? = null,
    )

    @Serializable
    private data class MappingData(val id: String, val type: String)

    @Serializable
    private data class KitsuAnime(val id: String, val type: String)

    @Serializable
    private data class KitsuEpisodesResponse(
        val data: List<KitsuEpisode> = emptyList(),
        val links: KitsuLinks? = null,
    )

    @Serializable
    private data class KitsuEpisode(
        val id: String,
        val type: String,
        val attributes: KitsuEpisodeAttributes? = null,
    )

    @Serializable
    private data class KitsuEpisodeAttributes(
        val number: Int? = null,
        @SerialName("canonicalTitle") val canonicalTitle: String? = null,
        val description: String? = null,
        val thumbnail: KitsuImage? = null,
        val airdate: String? = null,
    )

    @Serializable
    private data class KitsuImage(val original: String? = null)

    @Serializable
    private data class KitsuLinks(val next: String? = null)

    data class JikanEpisode(val title: String?, val aired: String?)

    @Serializable
    private data class JikanEpisodesResponse(val data: List<JikanEpisodeData> = emptyList())

    @Serializable
    private data class JikanEpisodeData(
        @SerialName("mal_id") val malId: Int? = null,
        val title: String? = null,
        val aired: String? = null,
    )

    companion object {
        private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
