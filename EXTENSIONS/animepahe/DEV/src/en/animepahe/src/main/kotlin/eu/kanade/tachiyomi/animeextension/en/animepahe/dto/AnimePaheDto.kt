package eu.kanade.tachiyomi.animeextension.en.animepahe.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Generic API response envelope for animepahe's `/api` endpoints.
 * All list endpoints (airing, search, release) return this shape with different `data` item types.
 *
 * Verified from the reference extension + community API docs:
 * https://gist.github.com/Ellivers/f7716b6b6895802058c367963f3a2c51
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ResponseDto<T>(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    @EncodeDefault @SerialName("data") val items: List<T> = emptyList(),
)

/** Item in the popular/airing list (`/api?m=airing`). */
@Serializable
data class AiringAnimeDto(
    @SerialName("anime_title") val title: String,
    val snapshot: String,            // cover/thumbnail URL
    @SerialName("anime_id") val id: Int,
    val fansub: String = "",         // the fansub group (mapped to SAnime.artist)
)

/** Item in the search results (`/api?m=search`). */
@Serializable
data class SearchResultDto(
    val title: String,
    val poster: String,              // cover URL
    val id: Int,
)

/** Item in the episode list (`/api?m=release&id=...`). */
@Serializable
data class EpisodeDto(
    @SerialName("created_at") val createdAt: String,   // e.g. "2024-01-15 12:00:00"
    val session: String,                               // episode session — used in /play/<animeSession>/<episodeSession>
    @SerialName("episode") val episodeNumber: Float,   // can be 1.0, 1.5, etc.
)
