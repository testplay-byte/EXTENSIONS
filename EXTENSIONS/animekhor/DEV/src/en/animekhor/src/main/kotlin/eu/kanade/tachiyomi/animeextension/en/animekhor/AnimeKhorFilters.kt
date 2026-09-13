package eu.kanade.tachiyomi.animeextension.en.animekhor

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import org.jsoup.nodes.Document

/**
 * Filters for animekhor.org — live-verified 2026-09-13 against the /anime/ filter form:
 *   <form action="https://animekhor.org/anime" method="GET">
 *     genre[] (218), studio[] (119) — fetched dynamically from the /anime/ page
 *     status (radio: ""/ongoing/completed/upcoming/hiatus)
 *     type   (radio: ""/tv/ova/movie/"live action"/special/bd/ona/music/comic)
 *     sub    (radio: ""/sub/dub/raw)
 *     order  (radio: ""/title/titlereverse/update/latest/popular/rating)
 *
 * NOTE: filters are ignored by the site when a search text is used (WordPress ?s= search) —
 * same caveat as the upstream animestream theme.
 */
object AnimeKhorFilters {

    // Dynamic lists (fetched from the /anime/ page on first catalog/search use)
    @Volatile
    private var genres: List<Pair<String, String>> = emptyList() // (slug, label)

    @Volatile
    private var studios: List<Pair<String, String>> = emptyList()

    fun isInitialized(): Boolean = genres.isNotEmpty()

    /**
     * Extracts the genre/studio dropdowns from the /anime/ page:
     *   <span class="sec1"><div class="filter ..."><button>Genre…</button>
     *     <ul class="dropdown-menu …"><li><input type="checkbox" name="genre[]" value="action"><label>Action</label></li>…
     */
    fun initialize(document: Document) {
        runCatching {
            val parsedGenres = extractGroup(document, "genre[]")
            val parsedStudios = extractGroup(document, "studio[]")
            synchronized(this) {
                if (genres.isEmpty() && parsedGenres.isNotEmpty()) genres = parsedGenres
                if (studios.isEmpty() && parsedStudios.isNotEmpty()) studios = parsedStudios
            }
        }
    }

    private fun extractGroup(document: Document, name: String): List<Pair<String, String>> =
        document.select("input[name=\"$name\"]").mapNotNull { input ->
            val value = input.attr("value")
            val label = document.selectFirst("label[for=\"${input.attr("id")}\"]")?.text()
                ?: input.nextElementSibling()?.text()
                ?: return@mapNotNull null
            value to label
        }.distinct()

    data class FilterSearchParams(
        val genres: String,
        val studios: String,
        val status: String,
        val type: String,
        val sub: String,
        val order: String,
    )

    fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        var genres = ""
        var studios = ""
        var status = ""
        var type = ""
        var sub = ""
        var order = ""

        filters.forEach { filter ->
            when (filter) {
                is CheckboxGroupFilter -> {
                    val slugs = filter.state.filter { it.state }.mapNotNull { it.slug }
                    if (slugs.isNotEmpty()) {
                        val query = slugs.joinToString("&") { "${filter.paramName}[]=$it" }
                        if (filter.paramName == "genre") genres = query else studios = query
                    }
                }
                is UrlSelectFilter -> {
                    val value = filter.urlValue()
                    when (filter.paramName) {
                        "status" -> status = value
                        "type" -> type = value
                        "sub" -> sub = value
                        "order" -> order = value
                    }
                }
                else -> {}
            }
        }
        return FilterSearchParams(genres, studios, status, type, sub, order)
    }

    // ── Filter classes ───────────────────────────────────────────────────

    private class Checkbox(val slug: String?, name: String) : AnimeFilter.CheckBox(name)

    /**
     * A group of checkboxes sharing one URL parameter (genre[] / studio[]).
     */
    private class CheckboxGroupFilter(
        val paramName: String,
        name: String,
        entries: List<Pair<String, String>>, // (urlSlug, label)
    ) : AnimeFilter.Group<Checkbox>(name, entries.map { Checkbox(it.first, it.second) })

    /**
     * A single-select filter whose selection maps 1:1 to the site's URL value.
     * State 0 is always the site default ("All" / "").
     */
    private class UrlSelectFilter(
        val paramName: String,
        name: String,
        private val urlValues: Array<String>,
        labels: Array<String>,
    ) : AnimeFilter.Select<String>(name, labels) {
        fun urlValue(): String = urlValues[state]
    }

    private fun select(
        param: String,
        name: String,
        pairs: List<Pair<String, String>>, // (urlValue, label)
    ) = UrlSelectFilter(param, name, pairs.map { it.first }.toTypedArray(), pairs.map { it.second }.toTypedArray())

    // Recomputed on every access so dynamically fetched genres/studios appear once loaded.
    val FILTER_LIST: AnimeFilterList
        get() = AnimeFilterList(
            AnimeFilter.Header("NOTE: filters are ignored if search text is used"),
            CheckboxGroupFilter("genre", "Genres", genres),
            CheckboxGroupFilter("studio", "Studios", studios),
            AnimeFilter.Separator(),
            select(
                "status",
                "Status",
                listOf("" to "All", "ongoing" to "Ongoing", "completed" to "Completed", "upcoming" to "Upcoming", "hiatus" to "Hiatus"),
            ),
            select(
                "type",
                "Type",
                listOf(
                    "" to "All", "tv" to "TV Series", "ova" to "OVA", "movie" to "Movie",
                    "live action" to "Live Action", "special" to "Special", "bd" to "BD",
                    "ona" to "ONA", "music" to "Music", "comic" to "Comic",
                ),
            ),
            select("sub", "Audio / Text", listOf("" to "All", "sub" to "Sub", "dub" to "Dub", "raw" to "RAW")),
            select(
                "order",
                "Order",
                listOf(
                    "" to "Default", "title" to "A-Z", "titlereverse" to "Z-A", "update" to "Latest Update",
                    "latest" to "Latest Added", "popular" to "Popular", "rating" to "Rating",
                ),
            ),
        )
}
