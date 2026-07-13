package eu.kanade.tachiyomi.animeextension.en.mkissa.metadata

import eu.kanade.tachiyomi.animeextension.en.mkissa.MKissaLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Episode metadata enrichment for MKissa.
 *
 * ★ Strategy: Anikage primary + Jikan fallback (OkHttp-only, no WebView needed).
 *
 * Sources:
 * 1. **Anikage** (`anikage.cc/api/media/anime/<anilistId>/episodes`) — PRIMARY.
 *    Gives per-episode `number`, `title`, `description`, `img` (thumbnail), `airDate`, `isFiller`.
 *    Works with plain OkHttp (not behind Cloudflare). Takes the AniList media ID directly.
 * 2. **Jikan** (`api.jikan.moe/v4/anime?q=<title>&limit=1` → `mal_id` → `/episodes`) — FALLBACK
 *    for episode titles + air dates when Anikage has no data (e.g. new/unpopular anime).
 *
 * The AniList media ID is extracted from the anime's thumbnail URL:
 * `https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx182300-...jpg` → `182300`.
 * (All mkissa.to thumbnails come from AniList, so this works for every anime.)
 *
 * Merge priority:
 * - Thumbnail: Anikage.img → anime cover (fallback)
 * - Title:     Anikage.title → Jikan.title
 * - Description: Anikage.description
 * - Air date:  Anikage.airDate → Jikan.aired
 *
 * Never throws — on any error, returns an empty map. Episodes load without enrichment.
 */
class EpisodeMetadataFetcher(
    private val client: OkHttpClient,
    private val json: Json,
) {
    data class EpisodeMetadata(
        val title: String?,
        val description: String?,
        val thumbnailUrl: String?,
        val airDate: String?,
    )

    private data class CachedData(
        val episodes: Map<Int, EpisodeMetadata>,
        val coverUrl: String?,
    )

    private val cache = mutableMapOf<String, CachedData>()

    private val apiHeaders = Headers.Builder()
        .set("User-Agent", BROWSER_UA)
        .set("Accept", "application/json, */*")
        .build()

    /**
     * Fetch episode metadata for an anime.
     *
     * @param anilistId The AniList media ID (extracted from the thumbnail URL). Nullable.
     * @param animeTitle The anime's title (for Jikan title-search fallback).
     * @param fallbackThumbnailUrl The anime's cover image URL (used as fallback for episode thumbnails).
     * @return Map<episodeNumber(Int), EpisodeMetadata>. Empty on any error.
     */
    suspend fun fetch(
        anilistId: String?,
        animeTitle: String,
        fallbackThumbnailUrl: String?,
    ): Map<Int, EpisodeMetadata> {
        if (anilistId.isNullOrBlank() && animeTitle.isBlank()) return emptyMap()

        val cacheKey = anilistId ?: "title:$animeTitle"
        synchronized(cache) {
            cache[cacheKey]?.let { return applyFallbackThumbnail(it.episodes, it.coverUrl, fallbackThumbnailUrl) }
        }

        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                MKissaLog.d("EpisodeMetadataFetcher: fetching for anilistId=$anilistId, title=$animeTitle")

                // ── Primary: Anikage (by AniList ID) ──────────────────────
                val anikageEpisodes = if (!anilistId.isNullOrBlank()) {
                    fetchAnikage(anilistId)
                } else {
                    emptyMap()
                }

                // ── Fallback: Jikan (by title search → mal_id → episodes) ─
                val jikanEpisodes = if (anilistId.isNullOrBlank() || anikageEpisodes.isEmpty()) {
                    fetchJikan(animeTitle)
                } else {
                    emptyMap()
                }

                // ── Merge: Anikage primary, Jikan fills gaps ──────────────
                val merged = mutableMapOf<Int, EpisodeMetadata>()
                val allKeys = anikageEpisodes.keys + jikanEpisodes.keys
                for (epNum in allKeys) {
                    val anikage = anikageEpisodes[epNum]
                    val jikan = jikanEpisodes[epNum]
                    merged[epNum] = EpisodeMetadata(
                        title = anikage?.title ?: jikan?.title,
                        description = anikage?.description,
                        thumbnailUrl = anikage?.thumbnailUrl,
                        airDate = anikage?.airDate ?: jikan?.airDate,
                    )
                }

                val elapsed = System.currentTimeMillis() - startTime
                MKissaLog.i("EpisodeMetadataFetcher: enriched ${merged.size} episodes in ${elapsed}ms (anikage=${anikageEpisodes.size}, jikan=${jikanEpisodes.size})")

                synchronized(cache) {
                    cache[cacheKey] = CachedData(merged, fallbackThumbnailUrl)
                }
                applyFallbackThumbnail(merged, fallbackThumbnailUrl, fallbackThumbnailUrl)
            } catch (e: Exception) {
                MKissaLog.e("EpisodeMetadataFetcher: FAILED — ${e.message}", e)
                emptyMap()
            }
        }
    }

    /** Apply the anime cover as a fallback thumbnail for episodes that have no thumbnail. */
    private fun applyFallbackThumbnail(
        episodes: Map<Int, EpisodeMetadata>,
        storedCover: String?,
        currentCover: String?,
    ): Map<Int, EpisodeMetadata> {
        val cover = currentCover ?: storedCover ?: return episodes
        return episodes.mapValues { (_, meta) ->
            if (meta.thumbnailUrl.isNullOrBlank()) meta.copy(thumbnailUrl = cover) else meta
        }
    }

    // ── Anikage ──────────────────────────────────────────────────────────

    @Serializable
    private data class AnikageEpisode(
        val number: Int,
        val title: String? = null,
        val description: String? = null,
        val img: String? = null,
        @SerialName("airDate") val airDateStr: String? = null,
        val isFiller: Boolean = false,
    )

    private fun fetchAnikage(anilistId: String): Map<Int, EpisodeMetadata> {
        return try {
            val url = "https://anikage.cc/api/media/anime/$anilistId/episodes"
            val req = Request.Builder().url(url).headers(apiHeaders).build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    MKissaLog.d("Anikage: HTTP ${resp.code} for anilistId=$anilistId")
                    return emptyMap()
                }
                resp.body?.string().orEmpty()
            }
            val episodes = json.decodeFromString<List<AnikageEpisode>>(body)
            episodes.associate { ep ->
                ep.number to EpisodeMetadata(
                    title = ep.title,
                    description = ep.description,
                    thumbnailUrl = ep.img,
                    airDate = ep.airDateStr,
                )
            }
        } catch (e: Exception) {
            MKissaLog.d("Anikage: failed for anilistId=$anilistId — ${e.message}")
            emptyMap()
        }
    }

    // ── Jikan ────────────────────────────────────────────────────────────

    @Serializable
    private data class JikanSearchResult(
        val data: List<JikanAnime> = emptyList(),
    ) {
        @Serializable
        data class JikanAnime(
            @SerialName("mal_id") val malId: Int,
            val title: String? = null,
        )
    }

    @Serializable
    private data class JikanEpisodesResult(
        val data: List<JikanEpisode> = emptyList(),
    ) {
        @Serializable
        data class JikanEpisode(
            @SerialName("mal_id") val malId: Int,
            val title: String? = null,
            @SerialName("aired") val airedStr: String? = null,
            val filler: Boolean = false,
            val recap: Boolean = false,
        )
    }

    private fun fetchJikan(animeTitle: String): Map<Int, EpisodeMetadata> {
        if (animeTitle.isBlank()) return emptyMap()
        return try {
            // Step 1: search by title → get mal_id
            val searchUrl = "https://api.jikan.moe/v4/anime?q=${java.net.URLEncoder.encode(animeTitle, "UTF-8")}&limit=1&sfw=true"
            val searchReq = Request.Builder().url(searchUrl).headers(apiHeaders).build()
            val searchBody = client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    MKissaLog.d("Jikan search: HTTP ${resp.code} for title=$animeTitle")
                    return emptyMap()
                }
                resp.body?.string().orEmpty()
            }
            val searchResult = json.decodeFromString<JikanSearchResult>(searchBody)
            val malId = searchResult.data.firstOrNull()?.malId ?: run {
                MKissaLog.d("Jikan search: no results for title=$animeTitle")
                return emptyMap()
            }

            // Step 2: fetch episodes for the mal_id
            Thread.sleep(1100) // Jikan rate limit: 1 req/sec (3 req/sec for authenticated, but we're unauth)
            val epsUrl = "https://api.jikan.moe/v4/anime/$malId/episodes"
            val epsReq = Request.Builder().url(epsUrl).headers(apiHeaders).build()
            val epsBody = client.newCall(epsReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    MKissaLog.d("Jikan episodes: HTTP ${resp.code} for malId=$malId")
                    return emptyMap()
                }
                resp.body?.string().orEmpty()
            }
            val epsResult = json.decodeFromString<JikanEpisodesResult>(epsBody)
            epsResult.data.associate { ep ->
                ep.malId to EpisodeMetadata(
                    title = ep.title,
                    description = null, // Jikan doesn't provide episode synopses
                    thumbnailUrl = null, // Jikan doesn't provide episode thumbnails
                    airDate = ep.airedStr,
                )
            }
        } catch (e: Exception) {
            MKissaLog.d("Jikan: failed for title=$animeTitle — ${e.message}")
            emptyMap()
        }
    }

    companion object {
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /**
         * Extract the AniList media ID from a thumbnail URL.
         * Example: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx182300-IYkq5KrkQq1V.jpg"
         *          → "182300"
         * Returns null if the URL doesn't match the AniList pattern.
         */
        fun extractAnilistId(thumbnailUrl: String?): String? {
            if (thumbnailUrl.isNullOrBlank()) return null
            val match = Regex("""bx(\d+)-""").find(thumbnailUrl)
            return match?.groupValues?.get(1)
        }
    }
}
