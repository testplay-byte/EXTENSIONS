package eu.kanade.tachiyomi.animeextension.en.reanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Filters for the Re:ANIME extension.
 *
 * reanime.to's `/api/v1/search` endpoint supports:
 * - `year` — filters by season_year (verified working)
 * - `season` — FALL | WINTER | SPRING | SUMMER (verified working)
 * - `format` — TV | MOVIE | OVA | ONA | SPECIAL (verified working)
 *
 * NOTE: `genres` and `sort` are NOT supported by the public search API
 * (they're ignored — returns the default popular list). Browse/catalog
 * endpoints are auth-gated (401). So we only offer year/season/format.
 */
object ReanimeFilters {

    private val SEASONS = arrayOf("Any", "WINTER", "SPRING", "SUMMER", "FALL")
    private val FORMATS = arrayOf("Any", "TV", "MOVIE", "OVA", "ONA", "SPECIAL")

    /** Year list: current year down to 1950. */
    private val YEARS: Array<String> = run {
        val currentYear = 2026
        val list = mutableListOf("Any")
        for (y in currentYear downTo 1950) list.add(y.toString())
        list.toTypedArray()
    }

    class YearFilter : AnimeFilter.List<String>("Year", YEARS)
    class SeasonFilter : AnimeFilter.List<String>("Season", SEASONS)
    class FormatFilter : AnimeFilter.List<String>("Format", FORMATS)

    val FILTER_LIST = AnimeFilterList(
        AnimeFilter.Header("Note: Genre and sort filters are not supported by reanime.to's public API."),
        YearFilter(),
        SeasonFilter(),
        FormatFilter(),
    )

    /** Extract filter parameters from the filter list. */
    data class SearchParams(
        val year: String = "",
        val season: String = "",
        val format: String = "",
    )

    @Suppress("UNCHECKED_CAST")
    fun getSearchParams(filters: AnimeFilterList): SearchParams {
        var year = ""
        var season = ""
        var format = ""
        filters.forEach { filter ->
            when (filter) {
                is YearFilter -> {
                    val selected = filter.values[filter.state]
                    if (selected != "Any") year = selected
                }
                is SeasonFilter -> {
                    val selected = filter.values[filter.state]
                    if (selected != "Any") season = selected
                }
                is FormatFilter -> {
                    val selected = filter.values[filter.state]
                    if (selected != "Any") format = selected
                }
                else -> {}
            }
        }
        return SearchParams(year = year, season = season, format = format)
    }
}
