package eu.kanade.tachiyomi.animeextension.en.anikotos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * All serializable DTOs for the AnikotoS site API.
 * Per MEMORY/research/apk-reference/03-catalog-and-dtos.md §4.
 */

// ── Episode list: /ajax/episode/list/{animeId}?vrf=..&style=default ──────────
@Serializable
data class EpisodeListResponse(
    val status: Int = 0,
    val result: String = "",
)

// ── Server list: /ajax/server/list?servers=<data-ids> ────────────────────────
@Serializable
data class ServerListResponse(
    val status: Int = 0,
    val result: String = "",
)

// ── Server resolve: /ajax/server?get=<link-id> ───────────────────────────────
@Serializable
data class ServerResponse(
    val status: Int = 0,
    val result: ServerResult? = null,
)

@Serializable
data class ServerResult(
    val url: String = "",
    @SerialName("skip_data") val skipData: SkipData? = null,
)

@Serializable
data class SkipData(
    val intro: List<Float> = emptyList(),
    val outro: List<Float> = emptyList(),
)

// ── VidTube sources: /stream/getSourcesNew?id=<data-id>&type=<audio> ─────────
@Serializable
data class VidTubeSourcesResponse(
    val sources: VidTubeSources? = null,
    val tracks: List<VidTubeTrack> = emptyList(),
)

@Serializable
data class VidTubeSources(
    val file: String = "",
)

@Serializable
data class VidTubeTrack(
    val file: String = "",
    val label: String = "",
    val kind: String = "",
)
