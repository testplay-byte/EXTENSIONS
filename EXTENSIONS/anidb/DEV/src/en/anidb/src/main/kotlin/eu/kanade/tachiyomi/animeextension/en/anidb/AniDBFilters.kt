package eu.kanade.tachiyomi.animeextension.en.anidb

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import java.util.Calendar

/**
 * AniDB filters — map to the /browse query parameters.
 *
 * All filters compose together on the /browse endpoint (unlike animepahe where
 * only ONE filter applies at a time). The browse URL is built as:
 *   /browse?type=<>&status=<>&season=<>&year=<>&genres=<>&sort=<>&page=N&q=<>t
 *
 * Genre + Theme use checkbox groups (multi-select). The site accepts a single
 * `genres` param — when multiple are checked we join with commas (verified:
 * the site's own filter form submits comma-separated values).
 *
 * Filter values (ids + slugs) verified from the live site's filter <select>
 * dropdowns (see MEMORY/sites/site-analysis.md §filters).
 */
object Filters {

    // ── Sort ───────────────────────────────────────────────────────────

    /** Sort order — maps to the `sort` query param. */
    class SortFilter : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Trending", "order_trending"),       // default (popular)
            Pair("Top Rated", "order_top"),
            Pair("Latest Updated", "order_updated"),  // latest
            Pair("Most Popular", "order_popular"),
            Pair("Most Favorited", "order_favorite"),
            Pair("Top Airing", "order_top_airing"),
            Pair("Title A-Z", "title"),
            Pair("Newest First", "aired_start"),
        ),
    )

    // ── Type ───────────────────────────────────────────────────────────

    /** Anime type — maps to the `type` query param. */
    class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Movie", "Movie"),
            Pair("Music", "Music"),
            Pair("ONA", "ONA"),
            Pair("OVA", "OVA"),
            Pair("Special", "Special"),
            Pair("TV", "TV"),
        ),
    )

    // ── Status ─────────────────────────────────────────────────────────

    /** Airing status — maps to the `status` query param. */
    class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Currently Airing", "Currently Airing"),
            Pair("Finished Airing", "Finished Airing"),
        ),
    )

    // ── Season ─────────────────────────────────────────────────────────

    /** Season — maps to the `season` query param (used WITH year). */
    class SeasonFilter : UriPartFilter(
        "Season",
        arrayOf(
            Pair("All", ""),
            Pair("Spring", "spring"),
            Pair("Summer", "summer"),
            Pair("Fall", "fall"),
            Pair("Winter", "winter"),
        ),
    )

    // ── Year ───────────────────────────────────────────────────────────

    /** Year — maps to the `year` query param. Range: current year back to 1977. */
    class YearFilter : UriPartFilter(
        "Year",
        YEARS,
    ) {
        companion object {
            private val CURRENT_YEAR by lazy { Calendar.getInstance().get(Calendar.YEAR) }
            private val YEARS = buildList {
                add(Pair("All", ""))
                addAll((CURRENT_YEAR downTo 1977).map { Pair(it.toString(), it.toString()) })
            }.toTypedArray()
        }
    }

    // ── Genres (checkbox group — multi-select) ─────────────────────────

    /** Genre checkbox — carries the site's genre/theme ID alongside the display name. */
    class IdCheckBox(val id: String, name: String) : AnimeFilter.CheckBox(name)

    /** Genres — 21 genres. Multi-select; selected IDs joined with commas. */
    class GenreFilter : AnimeFilter.Group<IdCheckBox>(
        "Genres",
        GENRES.map { IdCheckBox(it.second, it.first) }.toList(),
    )

    /** Themes — 24+ themes. Multi-select; selected IDs joined with commas. */
    class ThemeFilter : AnimeFilter.Group<IdCheckBox>(
        "Themes",
        THEMES.map { IdCheckBox(it.second, it.first) }.toList(),
    )

    // ── Base class for select-style filters ────────────────────────────

    /** Base class for select-style filters that map a display name → a URL value. */
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart(): String = vals[state].second
        fun isDefault(): Boolean = state == 0
    }

    // ── Filter data (verified from live site) ──────────────────────────

    /** 21 genres — (display name, id). Verified from /browse filter <select>. */
    private val GENRES = arrayOf(
        Pair("Action", "1"),
        Pair("Adventure", "3"),
        Pair("Avant Garde", "19"),
        Pair("Award Winning", "12"),
        Pair("Boys Love", "16"),
        Pair("Comedy", "5"),
        Pair("Drama", "2"),
        Pair("Ecchi", "13"),
        Pair("Erotica", "17"),
        Pair("Fantasy", "4"),
        Pair("Girls Love", "20"),
        Pair("Gourmet", "8"),
        Pair("Hentai", "15"),
        Pair("Horror", "21"),
        Pair("Mystery", "7"),
        Pair("Romance", "14"),
        Pair("Sci-Fi", "6"),
        Pair("Slice of Life", "9"),
        Pair("Sports", "11"),
        Pair("Supernatural", "10"),
        Pair("Suspense", "18"),
    )

    /** 24 themes — (display name, id). Verified from the home page nav dropdown. */
    private val THEMES = arrayOf(
        Pair("School", "6"),
        Pair("Adult Cast", "13"),
        Pair("Mecha", "23"),
        Pair("Historical", "24"),
        Pair("Harem", "12"),
        Pair("Military", "10"),
        Pair("Super Power", "1"),
        Pair("Isekai", "9"),
        Pair("Music", "19"),
        Pair("Mythology", "4"),
        Pair("Psychological", "20"),
        Pair("Parody", "3"),
        Pair("CGDCT", "35"),
        Pair("Space", "44"),
        Pair("Gore", "42"),
        Pair("Urban Fantasy", "27"),
        Pair("Gag Humor", "38"),
        Pair("Martial Arts", "39"),
        Pair("Anthropomorphic", "34"),
        Pair("Iyashikei", "47"),
        Pair("Strategy Game", "16"),
        Pair("Survival", "40"),
        Pair("Team Sports", "41"),
        Pair("Time Travel", "7"),
    )

    /** Build the filter list (display order matters). */
    fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Note: filters compose with search and each other"),
        AnimeFilter.Separator(),
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        SeasonFilter(),
        YearFilter(),
        AnimeFilter.Separator(),
        GenreFilter(),
        ThemeFilter(),
    )
}
