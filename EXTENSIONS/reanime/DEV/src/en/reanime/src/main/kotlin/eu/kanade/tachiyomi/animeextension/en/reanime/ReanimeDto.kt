package eu.kanade.tachiyomi.animeextension.en.reanime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the reanime.to REST API (`/api/v1/`).
 *
 * All responses are JSON. Field names match the API's snake_case naming.
 */

// ── Search results: GET /api/v1/search?q=&limit=&offset= ─────────────

@Serializable
data class SearchResponse(
    val limit: Int = 0,
    val offset: Int = 0,
    val processing_ms: Int = 0,
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
)

@Serializable
data class SearchResult(
    val anime_id: String,
    val title: Title,
    val cover_image: CoverImage? = null,
    val format: String = "",        // TV | MOVIE | OVA | ONA | SPECIAL
    val status: String = "",        // Finished | Releasing | Not Yet Aired
    val genres: List<String> = emptyList(),
    val season: String = "",        // FALL | WINTER | SPRING | SUMMER
    val season_year: Int? = null,
    val episodes: Int? = null,
    val duration: String = "",
    val subbed: Int = 0,
    val dubbed: Int = 0,
    val average_score: Int? = null,
    val popularity: Int? = null,
    val rating: String = "",
    val can_watch: Boolean = false,
    val can_request: Boolean = false,
)

@Serializable
data class Title(
    val english: String? = null,
    val native: String? = null,
    val romaji: String? = null,
)

@Serializable
data class CoverImage(
    val color: String = "",
    val extra_large: String = "",
    val large: String = "",
    val medium: String = "",
)

// ── Episodes: GET /api/v1/anime/<anime_id>/episodes?limit=2000 ────────

@Serializable
data class EpisodesResponse(
    val data: List<Episode> = emptyList(),
)

@Serializable
data class Episode(
    val episodeId: String = "",          // "ep-1", "ep-2", etc.
    val episode_number: Int = 0,
    val title: String = "",
    val title_japanese: String = "",
    val title_romanji: String = "",
    val description: String = "",
    val duration: Int = 0,
    val aired: String = "",
    val is_filler: Boolean = false,
    val is_recap: Boolean = false,
    val site: String = "",               // "MyAnimeList"
    val thumbnail: String = "",
    val url: String = "",
    val updated_at: String = "",
)

// ── Video sources: GET /api/flix/<anilist_id>/<ep> ────────────────────

@Serializable
data class FlixResponse(
    val success: Boolean = false,
    val servers: List<FlixServer> = emptyList(),
)

@Serializable
data class FlixServer(
    @SerialName("\$id") val id: String = "",      // "hd1-<code>-sub"
    val serverName: String = "",                    // "HD-1", "HD-2"
    val dataLink: String = "",                      // "https://flixcloud.cc/e/<code>?v=<N>"
    val dataType: String = "",                      // "sub" | "dub"
    val continue: Boolean = false,
    val softsub: Boolean = false,
)
