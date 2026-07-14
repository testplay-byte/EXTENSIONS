package eu.kanade.tachiyomi.animeextension.en.miruro

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeFilter.TriState
import java.util.Calendar

/**
 * Miruro filters — 8 categories, matching the site's browse filters.
 *
 * Verified against yuzono MiruroFilters.kt + the site's own filter UI.
 * All compose on the single `search/browse` pipe path.
 */
object MiruroFilters {

    open class QueryPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toQueryPart() = vals[state].second
    }

    open class TriStateFilterList(name: String, values: List<TriFilterVal>) : AnimeFilter.Group<TriState>(name, values)
    class TriFilterVal(name: String) : TriState(name)

    open class CheckBoxFilterList(name: String, values: List<CheckBox>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)
    private class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    private inline fun <reified R> AnimeFilterList.asQueryPart(): String =
        (this.filterIsInstance<R>().first() as QueryPartFilter).toQueryPart()

    private inline fun <reified R> AnimeFilterList.parseCheckbox(
        options: Array<Pair<String, String>>,
    ): List<String> = (this.filterIsInstance<R>().first() as CheckBoxFilterList).state
        .mapNotNull { checkbox ->
            if (checkbox.state) options.find { it.first == checkbox.name }?.second else null
        }

    private inline fun <reified R> AnimeFilterList.parseTriState(
        options: Array<Pair<String, String>>,
    ): Pair<List<String>, List<String>> {
        val state = (this.filterIsInstance<R>().first() as TriStateFilterList).state
        val included = mutableListOf<String>()
        val excluded = mutableListOf<String>()
        for (filter in state) {
            if (filter.isIgnored()) continue
            val value = options.find { it.first == filter.name }?.second ?: continue
            when (filter.state) {
                TriState.STATE_INCLUDE -> included.add(value)
                TriState.STATE_EXCLUDE -> excluded.add(value)
            }
        }
        return included to excluded
    }

    class SortFilter : QueryPartFilter("Sort", MiruroFiltersData.SORT)
    class GenreFilter : TriStateFilterList("Genres", MiruroFiltersData.GENRES.map { TriFilterVal(it.first) })
    class TagsFilter : TriStateFilterList("Tags", MiruroFiltersData.TAGS.map { TriFilterVal(it.first) })
    class FormatFilter : CheckBoxFilterList("Format", MiruroFiltersData.FORMATS.map { CheckBoxVal(it.first, false) })
    class YearFilter : QueryPartFilter("Year", MiruroFiltersData.YEARS)
    class SeasonFilter : QueryPartFilter("Season", MiruroFiltersData.SEASONS)
    class StatusFilter : QueryPartFilter("Status", MiruroFiltersData.STATUS)
    class DubLanguageFilter : QueryPartFilter("Dub Language", MiruroFiltersData.DUB_LANGUAGES)

    val FILTER_LIST get() = AnimeFilterList(
        SortFilter(),
        AnimeFilter.Separator(),
        GenreFilter(),
        TagsFilter(),
        FormatFilter(),
        AnimeFilter.Separator(),
        YearFilter(),
        SeasonFilter(),
        StatusFilter(),
        DubLanguageFilter(),
    )

    data class FilterSearchParams(
        val sort: String = "all",
        val genres: List<String> = emptyList(),
        val excludedGenres: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val excludedTags: List<String> = emptyList(),
        val year: String = "all",
        val season: String = "all",
        val status: String = "all",
        val formats: List<String> = emptyList(),
        val dubLanguage: String = "all",
    )

    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()
        val (includedGenres, excludedGenres) = filters.parseTriState<GenreFilter>(MiruroFiltersData.GENRES)
        val (includedTags, excludedTags) = filters.parseTriState<TagsFilter>(MiruroFiltersData.TAGS)
        return FilterSearchParams(
            sort = filters.asQueryPart<SortFilter>(),
            genres = includedGenres,
            excludedGenres = excludedGenres,
            tags = includedTags,
            excludedTags = excludedTags,
            year = filters.asQueryPart<YearFilter>(),
            season = filters.asQueryPart<SeasonFilter>(),
            status = filters.asQueryPart<StatusFilter>(),
            formats = filters.parseCheckbox<FormatFilter>(MiruroFiltersData.FORMATS),
            dubLanguage = filters.asQueryPart<DubLanguageFilter>(),
        )
    }

    private object MiruroFiltersData {
        val ALL = Pair("All", "all")

        val SORT = arrayOf(
            ALL,
            Pair("Trending", "TRENDING_DESC"),
            Pair("Popularity", "POPULARITY_DESC"),
            Pair("Average Score", "SCORE_DESC"),
            Pair("Favorites", "FAVOURITES_DESC"),
            Pair("Latest", "START_DATE_DESC"),
            Pair("Title A-Z", "TITLE_ROMAJI"),
            Pair("Title Z-A", "TITLE_ROMAJI_DESC"),
        )

        val GENRES = arrayOf(
            Pair("Action", "Action"),
            Pair("Adventure", "Adventure"),
            Pair("Comedy", "Comedy"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Fantasy", "Fantasy"),
            Pair("Horror", "Horror"),
            Pair("Mahou Shoujo", "Mahou Shoujo"),
            Pair("Mecha", "Mecha"),
            Pair("Music", "Music"),
            Pair("Mystery", "Mystery"),
            Pair("Psychological", "Psychological"),
            Pair("Romance", "Romance"),
            Pair("Sci-Fi", "Sci-Fi"),
            Pair("Slice of Life", "Slice of Life"),
            Pair("Sports", "Sports"),
            Pair("Supernatural", "Supernatural"),
            Pair("Thriller", "Thriller"),
        )

        val YEARS = arrayOf(ALL) + (Calendar.getInstance().get(Calendar.YEAR) downTo 1940).map {
            Pair(it.toString(), it.toString())
        }.toTypedArray()

        val SEASONS = arrayOf(
            ALL,
            Pair("Winter", "WINTER"),
            Pair("Spring", "SPRING"),
            Pair("Summer", "SUMMER"),
            Pair("Fall", "FALL"),
        )

        val STATUS = arrayOf(
            ALL,
            Pair("Airing", "RELEASING"),
            Pair("Finished", "FINISHED"),
            Pair("Not Yet Aired", "NOT_YET_RELEASED"),
            Pair("Hiatus", "HIATUS"),
            Pair("Cancelled", "CANCELLED"),
        )

        val FORMATS = arrayOf(
            Pair("TV", "TV"),
            Pair("TV Short", "TV_SHORT"),
            Pair("Movie", "MOVIE"),
            Pair("Special", "SPECIAL"),
            Pair("OVA", "OVA"),
            Pair("ONA", "ONA"),
            Pair("Music", "MUSIC"),
        )

        val DUB_LANGUAGES = arrayOf(
            ALL,
            Pair("English", "English"),
            Pair("Japanese", "Japanese"),
            Pair("Spanish", "Español"),
            Pair("Portuguese", "Português"),
            Pair("French", "Français"),
            Pair("German", "Deutsch"),
            Pair("Italian", "Italiano"),
            Pair("Korean", "한국어"),
            Pair("Chinese", "中文"),
            Pair("Arabic", "العربية"),
            Pair("Hindi", "हिन्दी"),
            Pair("Russian", "Русский"),
            Pair("Turkish", "Türkçe"),
            Pair("Thai", "ไทย"),
            Pair("Polish", "Polski"),
            Pair("Tagalog", "Tagalog"),
            Pair("Ukrainian", "Українська"),
        )

        // A curated subset of AniList tags (~80 most common). The full list is ~300;
        // users rarely filter by obscure tags. Expandable if needed.
        val TAGS = arrayOf(
            Pair("Achronological Order", "Achronological Order"),
            Pair("Aliens", "Aliens"),
            Pair("Alternate Universe", "Alternate Universe"),
            Pair("Amnesia", "Amnesia"),
            Pair("Ancient China", "Ancient China"),
            Pair("Angels", "Angels"),
            Pair("Anti-Hero", "Anti-Hero"),
            Pair("Archery", "Archery"),
            Pair("Artificial Intelligence", "Artificial Intelligence"),
            Pair("Assassins", "Assassins"),
            Pair("Augmented Reality", "Augmented Reality"),
            Pair("Aviation", "Aviation"),
            Pair("Badminton", "Badminton"),
            Pair("Baseball", "Baseball"),
            Pair("Basketball", "Basketball"),
            Pair("Battle Royale", "Battle Royale"),
            Pair("Body Swapping", "Body Swapping"),
            Pair("Boxing", "Boxing"),
            Pair("Boys Love", "Boys Love"),
            Pair("Bullying", "Bullying"),
            Pair("CGI", "CGI"),
            Pair("Card Battle", "Card Battle"),
            Pair("Crossover", "Crossover"),
            Pair("Cyberpunk", "Cyberpunk"),
            Pair("Cyborg", "Cyborg"),
            Pair("Cycling", "Cycling"),
            Pair("Dancing", "Dancing"),
            Pair("Death Game", "Death Game"),
            Pair("Delinquents", "Delinquents"),
            Pair("Demons", "Demons"),
            Pair("Detective", "Detective"),
            Pair("Dinosaurs", "Dinosaurs"),
            Pair("Dragons", "Dragons"),
            Pair("Drawing", "Drawing"),
            Pair("Drugs", "Drugs"),
            Pair("Dungeon", "Dungeon"),
            Pair("Dystopian", "Dystopian"),
            Pair("E-Sports", "E-Sports"),
            Pair("Elf", "Elf"),
            Pair("Espionage", "Espionage"),
            Pair("Fairy", "Fairy"),
            Pair("Fairy Tale", "Fairy Tale"),
            Pair("Female Protagonist", "Female Protagonist"),
            Pair("Food", "Food"),
            Pair("Football", "Football"),
            Pair("Found Family", "Found Family"),
            Pair("Full Color", "Full Color"),
            Pair("Gambling", "Gambling"),
            Pair("Gender Bending", "Gender Bending"),
            Pair("Ghost", "Ghost"),
            Pair("Gods", "Gods"),
            Pair("Gore", "Gore"),
            Pair("Guns", "Guns"),
            Pair("Gyaru", "Gyaru"),
            Pair("Henshin", "Henshin"),
            Pair("Historical", "Historical"),
            Pair("Idol", "Idol"),
            Pair("Isekai", "Isekai"),
            Pair("Iyashikei", "Iyashikei"),
            Pair("Josei", "Josei"),
            Pair("Kaiju", "Kaiju"),
            Pair("Kids", "Kids"),
            Pair("Magic", "Magic"),
            Pair("Mahou Shoujo", "Mahou Shoujo"),
            Pair("Martial Arts", "Martial Arts"),
            Pair("Mecha", "Mecha"),
            Pair("Military", "Military"),
            Pair("Monster Girl", "Monster Girl"),
            Pair("Nekomimi", "Nekomimi"),
            Pair("Ninja", "Ninja"),
            Pair("Noir", "Noir"),
            Pair("Office Lady", "Office Lady"),
            Pair("Orphan", "Orphan"),
            Pair("Otaku Culture", "Otaku Culture"),
            Pair("Pirates", "Pirates"),
            Pair("Police", "Police"),
            Pair("Politics", "Politics"),
            Pair("Post-Apocalyptic", "Post-Apocalyptic"),
            Pair("Reincarnation", "Reincarnation"),
            Pair("Robots", "Robots"),
            Pair("Royal Affairs", "Royal Affairs"),
            Pair("Rugby", "Rugby"),
            Pair("Samurai", "Samurai"),
            Pair("School", "School"),
            Pair("Seinen", "Seinen"),
            Pair("Shoujo", "Shoujo"),
            Pair("Shounen", "Shounen"),
            Pair("Space", "Space"),
            Pair("Space Opera", "Space Opera"),
            Pair("Steampunk", "Steampunk"),
            Pair("Super Power", "Super Power"),
            Pair("Superhero", "Superhero"),
            Pair("Survival", "Survival"),
            Pair("Swordplay", "Swordplay"),
            Pair("Teacher", "Teacher"),
            Pair("Tennis", "Tennis"),
            Pair("Time Loop", "Time Loop"),
            Pair("Time Manipulation", "Time Manipulation"),
            Pair("Vampire", "Vampire"),
            Pair("Video Games", "Video Games"),
            Pair("Villainess", "Villainess"),
            Pair("VTuber", "VTuber"),
            Pair("War", "War"),
            Pair("Werewolf", "Werewolf"),
            Pair("Witch", "Witch"),
            Pair("Yakuza", "Yakuza"),
            Pair("Yuri", "Yuri"),
            Pair("Zombie", "Zombie"),
        )
    }
}
