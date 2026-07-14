package eu.kanade.tachiyomi.animeextension.en.anidb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the AniDB JSON APIs.
 *
 * Endpoints (verified via curl + agent-browser — see MEMORY/sites/site-analysis.md):
 *  - GET /api/frontend/anime/<animeId>/episodes → [EpisodesResponse]
 *  - GET /api/frontend/episode/<epId>/languages → [LanguagesResponse]
 *
 * Both return clean JSON (not wrapped in HTML). No pagination on episodes —
 * the full list is returned in one response.
 */

/** Response from /api/frontend/anime/<animeId>/episodes */
@Serializable
data class EpisodesResponse(
    val episodes: List<EpisodeDto> = emptyList(),
)

/** A single episode in the episode-list response. */
@Serializable
data class EpisodeDto(
    val id: Int,
    val number: Int,
    /** Secondary number — null for normal episodes; may be set for .5/recap episodes. */
    @SerialName("number2") val number2: Float? = null,
    /** Whether this episode is marked as filler on the site. */
    val filler: Boolean = false,
)

/** Response from /api/frontend/episode/<epId>/languages */
@Serializable
data class LanguagesResponse(
    val languages: List<LanguageDto> = emptyList(),
)

/**
 * A single audio language for an episode.
 *
 * AniDB serves sub/dub as separate language entries, each pointing at a
 * DIFFERENT embed URL (which resolves to a different m3u8 stream).
 *
 * Verified examples:
 *  - {code:"jpn", name:"Japanese", embed_url:"/embed/<token>"}  → SUB
 *  - {code:"eng", name:"English",  embed_url:"/embed/<token>"}  → DUB
 *
 * No HSUB (hardsub) language type exists on this site.
 */
@Serializable
data class LanguageDto(
    /** ISO 639-2 language code: "jpn" (Japanese/SUB), "eng" (English/DUB). */
    val code: String,
    /** Human-readable language name: "Japanese", "English". */
    val name: String,
    /** Embed player URL: https://anidb.app/embed/<token> */
    @SerialName("embed_url") val embedUrl: String,
)
