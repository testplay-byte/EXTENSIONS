package eu.kanade.tachiyomi.animeextension.en.mkissa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────────────────────────────────────
// Popular
// ──────────────────────────────────────────────────────────────────────────────

@Serializable
data class PopularResult(
    val data: PopularData,
) {
    @Serializable
    data class PopularData(
        val queryPopular: QueryPopular,
    ) {
        @Serializable
        data class QueryPopular(
            val total: Int? = null,
            val recommendations: List<Recommendation> = emptyList(),
        ) {
            @Serializable
            data class Recommendation(
                val anyCard: Card? = null,
            ) {
                @Serializable
                data class Card(
                    @SerialName("_id") val id: String,
                    val name: String,
                    val thumbnail: String? = null,
                    val englishName: String? = null,
                    val nativeName: String? = null,
                    val slugTime: String? = null,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Search / Latest (shows query)
// ──────────────────────────────────────────────────────────────────────────────

@Serializable
data class SearchResult(
    val data: SearchData,
) {
    @Serializable
    data class SearchData(
        val shows: Shows,
    ) {
        @Serializable
        data class Shows(
            val pageInfo: PageInfo = PageInfo(),
            val edges: List<Edge> = emptyList(),
        ) {
            @Serializable
            data class PageInfo(val total: Int? = null)

            @Serializable
            data class Edge(
                @SerialName("_id") val id: String,
                val name: String,
                val thumbnail: String? = null,
                val englishName: String? = null,
                val nativeName: String? = null,
                val slugTime: String? = null,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Details (show query — full metadata)
// ──────────────────────────────────────────────────────────────────────────────

@Serializable
data class DetailsResult(
    val data: DetailsData,
) {
    @Serializable
    data class DetailsData(
        val show: Show,
    ) {
        @Serializable
        data class Show(
            @SerialName("_id") val id: String,
            val name: String,
            val englishName: String? = null,
            val nativeName: String? = null,
            val thumbnail: String? = null,
            val banner: String? = null,
            val description: String? = null,
            val type: String? = null,
            val season: Season? = null,
            val score: Float? = null,
            val averageScore: Int? = null,
            val rating: String? = null,
            val genres: List<String>? = null,
            val status: String? = null,
            val studios: List<String>? = null,
            val airedStart: AirDate? = null,
            val availableEpisodes: AvailableEpisodes? = null,
            val episodeDuration: String? = null,
            val episodeCount: String? = null,
        ) {
            @Serializable
            data class Season(val quarter: String? = null, val year: Int? = null)

            @Serializable
            data class AirDate(val year: Int? = null, val month: Int? = null, val date: Int? = null)

            @Serializable
            data class AvailableEpisodes(val sub: Int = 0, val dub: Int = 0, val raw: Int = 0)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Episodes (availableEpisodesDetail — list of episode strings)
// ──────────────────────────────────────────────────────────────────────────────

@Serializable
data class EpisodesResult(
    val data: EpisodesData,
) {
    @Serializable
    data class EpisodesData(
        val show: Show,
    ) {
        @Serializable
        data class Show(
            @SerialName("_id") val id: String,
            val availableEpisodesDetail: AvailableEpsDetail,
        )

        @Serializable
        data class AvailableEpsDetail(
            val sub: List<String> = emptyList(),
            val dub: List<String> = emptyList(),
            val raw: List<String> = emptyList(),
        )
    }
}
