package eu.kanade.tachiyomi.animeextension.en.miruro

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Miruro DTOs — the data shapes returned by the pipe API.
 *
 * Verified against the yuzono reference extension (MiruroDto.kt) + the live
 * site's frontend TypeScript interfaces. See MEMORY/sites/site-analysis.md.
 */

internal val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

// ════════════════════════════════════════════════════════════════════
// Anime (catalog + details)
// ════════════════════════════════════════════════════════════════════

@Serializable
data class AnimeMediaDto(
    val id: Int = 0,
    @SerialName("idMal") val malId: Int? = null,
    val title: TitleDto? = null,
    val coverImage: CoverImageDto? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val studios: StudiosDto? = null,
    val season: String? = null,
    @SerialName("seasonYear") val year: Int? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val isAdult: Boolean = false,
) {
    @Serializable
    data class TitleDto(
        val userPreferred: String? = null,
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
    )

    @Serializable
    data class CoverImageDto(
        val extraLarge: String? = null,
        val large: String? = null,
        val medium: String? = null,
    )

    @Serializable
    data class StudiosDto(
        val edges: List<StudioEdgeDto>? = null,
    )

    @Serializable
    data class StudioEdgeDto(
        val isMain: Boolean = false,
        val node: StudioNodeDto? = null,
    )

    @Serializable
    data class StudioNodeDto(
        val name: String? = null,
    )
}

/** Pipe search/browse response — either {media:[...], pageInfo:{hasNextPage}} or a bare array. */
@Serializable
data class BrowseResponseDto(
    val media: List<AnimeMediaDto> = emptyList(),
    val pageInfo: PageInfoDto? = null,
)

@Serializable
data class PageInfoDto(
    val currentPage: Int = 0,
    val hasNextPage: Boolean = false,
    val totalPages: Int = 0,
    val totalResults: Int = 0,
)

// ════════════════════════════════════════════════════════════════════
// Episodes
// ════════════════════════════════════════════════════════════════════

/**
 * The episodes pipe response.
 *
 * Shape: `{ "providers": { "<alias>": { "episodes": { "sub": [...], "dub": [...], "ssub": [...], "h-sub": [...] } } } }`
 * where each episode is `{ "id": "...", "number": 1.0, "title": "..." }`.
 */
@Serializable
data class EpisodesResponseDto(
    val providers: Map<String, ProviderEpisodesDto> = emptyMap(),
)

@Serializable
data class ProviderEpisodesDto(
    val episodes: Map<String, List<EpisodeDto>> = emptyMap(),
)

@Serializable
data class EpisodeDto(
    val id: String = "",
    val number: Double = 0.0,
    val title: String = "",
    val description: String? = null,
    val image: String? = null,
    val airDate: String? = null,
    val isFiller: Boolean = false,
)

// ════════════════════════════════════════════════════════════════════
// Sources (video streams)
// ════════════════════════════════════════════════════════════════════

/**
 * The sources pipe response.
 *
 * May be a direct `{ streams: [...], subtitles: [...] }` OR a nested
 * `{ <something>: { streams: [...], subtitles: [...] } }` (the frontend
 * wraps responses under a key sometimes). [parse] handles both.
 */
@Serializable
data class SourcesResponseDto(
    val streams: List<StreamDto> = emptyList(),
    val subtitles: List<SubtitleDto> = emptyList(),
) {
    @Serializable
    data class NestedWrapper(
        val streams: List<StreamDto> = emptyList(),
        val subtitles: List<SubtitleDto> = emptyList(),
    )

    companion object {
        fun parse(json: String): SourcesResponseDto {
            val element = try {
                Json.parseToJsonElement(json).jsonObject
            } catch (e: Exception) {
                return SourcesResponseDto()
            }

            val directStreams = element["streams"]
            if (directStreams != null) {
                return jsonParser.decodeFromString<SourcesResponseDto>(json)
            }

            for (entry in element.entries) {
                val value = entry.value
                if (value is JsonObject && value.containsKey("streams")) {
                    return jsonParser.decodeFromString<NestedWrapper>(value.toString()).let {
                        SourcesResponseDto(streams = it.streams, subtitles = it.subtitles)
                    }
                }
            }

            return SourcesResponseDto()
        }
    }
}

@Serializable
data class StreamDto(
    val type: String = "",          // "hls" | "embed"
    val url: String = "",
    val quality: String = "",        // "1080", "720", "480", "360", ""
    val resolution: ResolutionDto? = null,
    val codec: String = "",
    val audio: String = "",
    val fansub: String = "",
    val referer: String = "https://kwik.cx/",
    val isActive: Boolean = true,
)

@Serializable
data class ResolutionDto(
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class SubtitleDto(
    val url: String = "",
    val label: String = "",
    val language: String = "",
)

// ════════════════════════════════════════════════════════════════════
// Config (provider capabilities + mirror list)
// ════════════════════════════════════════════════════════════════════

@Serializable
data class ConfigResponseDto(
    val streaming: Map<String, ProviderConfigDto> = emptyMap(),
    val providerOrder: List<String> = emptyList(),
    val meta: MetaConfigDto? = null,
) {
    @Serializable
    data class ProviderConfigDto(
        val capabilities: ProviderCapabilitiesDto = ProviderCapabilitiesDto(),
        val parent: String? = null,
        val relationship: String? = null,
        val visible: Boolean = true,
        val player: String = "native",
        val fallback: Int? = null,
        val cors: Boolean = false,
    )

    @Serializable
    data class ProviderCapabilitiesDto(
        val sub: Boolean = false,
        val dub: Boolean = false,
        val ssub: Boolean = false,
        val download: Boolean = false,
        @SerialName("skip_times") val skipTimes: Boolean = false,
        val thumbnails: Boolean = false,
    )

    @Serializable
    data class MetaConfigDto(
        val anilist: AnilistConfigDto? = null,
    ) {
        @Serializable
        data class AnilistConfigDto(
            val graphql: String = "https://graphql.anilist.co",
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// Status page (mirror list)
// ════════════════════════════════════════════════════════════════════

@Serializable
data class StatusPageDto(
    val publicGroupList: List<StatusPageGroupDto> = emptyList(),
) {
    @Serializable
    data class StatusPageGroupDto(
        val name: String = "",
        val monitorList: List<StatusMonitorDto> = emptyList(),
    )

    @Serializable
    data class StatusMonitorDto(
        val id: Int = 0,
        val name: String = "",
    )
}
