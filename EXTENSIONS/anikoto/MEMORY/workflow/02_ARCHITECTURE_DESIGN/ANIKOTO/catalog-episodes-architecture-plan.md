# ANIKOTO — Stage 2 Architecture Plan (Catalog + Episodes)

> Last updated: 2026-06-22 · Status: PLAN (ready for Stage 2 implementation)
> Companion to `catalog-and-episodes-analysis.md` (the verified research).
> This is the implementation blueprint for the catalog + episodes layer (Stage 3 of WORKFLOW).

> **Note:** This plan covers ONLY the catalog + episodes layer (WORKFLOW Step 03:
> `03_CATALOG_EPISODES_MANAGEMENT`). The video-extraction layer (Step 04) is documented in
> `site-analysis.md` + `servers.md` + `png-wrapping.md` and will get its own Step 04 plan.

---

## 0. Build target

- **Package:** `eu.kanade.tachiyomi.animeextension.en.anikoto`
- **Class:** `Anikoto : AnimeHttpSource(), ConfigurableAnimeSource` (extend `AnimeHttpSource` directly — NOT `ParsedAnimeHttpSource` which is deprecated on v16)
- **`baseUrl`:** `https://anikototv.to`
- **`lang`:** `en`
- **`supportsLatest`:** `true` (try `/filter?sort=latest-updated`; fallback to `false` if broken)
- **`versionId`:** `1` (bump if URLs change)
- **ext-lib:** `v16` (per ADR-01)

---

## 1. Headers + client

```kotlin
override fun headersBuilder() = super.headersBuilder()
    .set("Referer", "$baseUrl/")
    .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .set("Accept-Language", "en-US,en;q=0.9")

// For XHR endpoints, add X-Requested-With per-request (not in default headers)
// No custom OkHttpClient needed — network.client handles Cloudflare.
// No rate limiting needed (site is not aggressive).
// No lib interceptor modules needed (no Cloudflare challenge, no cookie forcing).
```

---

## 2. Catalog: Popular (`getPopularAnime`)

### Strategy (with fallback)
```kotlin
override fun popularAnimeRequest(page: Int): Request {
    // Try the /filter endpoint with sort=most-viewed. If it returns the SEO landing
    // (detected by checking for "Popular searches" text), fall back to the homepage's
    // hardcoded trending list.
    val url = "$baseUrl/filter?sort=most-viewed&page=$page"
    return GET(url, headers)
}

override fun popularAnimeParse(response: Response): AnimesPage {
    val doc = response.asJsoup()
    // Detect the SEO-landing fallback (no real results)
    val isLanding = doc.text().contains("Popular searches")
    val items = if (isLanding) {
        // Parse the homepage's "Popular searches" items (10 hardcoded)
        doc.select("a.item[href*=/watch/]").map { parseSearchItem(it) }
    } else {
        // Parse real /filter results (card structure TBD in Stage 2 — verify with agent-browser)
        doc.select("a.item[href*=/watch/]").map { parseSearchItem(it) }
    }
    // Pagination: /filter has 293 pages, but if landing, no pagination
    val hasNext = !isLanding && doc.select("a.page-link[rel=next]").isNotEmpty()
    return AnimesPage(items, hasNext)
}
```

### ⚠️ Open verification (Stage 2 must resolve)
- Does `/filter?sort=most-viewed&page=1` return real results via curl/OkHttp, or always the landing page?
- If always landing: `getPopularAnime` returns the 10 hardcoded items, `hasNextPage = false`. Document this limitation.
- If works with session: pre-fetch homepage to get `cf_clearance`/session cookie, then call `/filter`.

---

## 3. Catalog: Latest (`getLatestUpdates`)

### Strategy
```kotlin
override fun latestUpdatesRequest(page: Int): Request {
    return GET("$baseUrl/filter?sort=latest-updated&page=$page", headers)
}
// latestUpdatesParse = same as popularAnimeParse (reuse)
```

If `/filter` is broken, set `supportsLatest = false` and throw `UnsupportedOperationException` in `latestUpdatesRequest/Parse`.

---

## 4. Catalog: Search (`getSearchAnime`)

### Strategy (VERIFIED — live autosuggest)
```kotlin
override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
    // If query is non-empty: use the live autosuggest API (VERIFIED working, returns ~10 items)
    // If query is empty: use /filter with the filter params (for browse-by-filter — may be broken)
    return if (query.isNotBlank()) {
        GET("$baseUrl/ajax/anime/search?keyword=${URLEncoder.encode(query, "UTF-8")}",
            headers.newBuilder()
                .set("Referer", "$baseUrl/")
                .set("X-Requested-With", "XMLHttpRequest")
                .build())
    } else {
        // Build /filter URL from filters (see §5 below)
        GET("$baseUrl/filter?${buildFilterQuery(filters)}&page=$page", headers)
    }
}

override fun searchAnimeParse(response: Response): AnimesPage {
    val body = response.body.string()
    return if (body.trimStart().startsWith("{")) {
        // JSON response from /ajax/anime/search
        val data = json.decodeFromString<SearchApiResponse>(body)
        val doc = Jsoup.parse(data.result.html)
        val items = doc.select("a.item").map { parseSearchItem(it) }
        AnimesPage(items, hasNextPage = false)  // live search has no pagination
    } else {
        // HTML response from /filter
        val doc = Jsoup.parse(body)
        val items = doc.select("a.item[href*=/watch/]").map { parseSearchItem(it) }
        val hasNext = doc.select("a.page-link[rel=next]").isNotEmpty()
        AnimesPage(items, hasNext)
    }
}

private fun parseSearchItem(el: Element): SAnime = SAnime.create().apply {
    url = el.attr("href").substringAfter(baseUrl)  // /watch/{slug}
    title = el.selectFirst(".name")?.text() ?: "Unknown"
    thumbnail_url = el.selectFirst("img")?.attr("src")
    // data-jp (Japanese title) available for title-language preference (Stage 5)
}

@Serializable data class SearchApiResponse(val status: Int, val result: SearchResult)
@Serializable data class SearchResult(val html: String, val linkMore: String? = null)
```

---

## 5. Catalog: Filters (`getFilterList`)

### Full filter list (mapped from /filter form)
```kotlin
override fun getFilterList(): AnimeFilterList = AnimeFilterList(
    AnimeFilter.Header("Search uses live autosuggest (ignores filters)."),
    AnimeFilter.Header("Filters apply only when searching with empty query (browse mode)."),
    AnimeFilter.Header("If /filter is broken, browse mode returns no results."),
    GenreFilter(),
    TypeFilter(),
    StatusFilter(),
    LanguageFilter(),
    SeasonFilter(),
    YearFilter(),
    RatingFilter(),
    SortFilter(),
)

// Genre: 43 checkboxes (parse from /filter page at runtime OR hardcode)
class GenreFilter : AnimeFilter.CheckBoxGroup("Genres", GENRES.map { CheckBox(it.first, it.second) })
private val GENRES = listOf(
    "Action" to "1", "Adventure" to "2", "Sports" to "3", "Avant Garde" to "538",
    "Drama" to "8", "Comedy" to "28", "Fantasy" to "9", "Horror" to "14",
    "Harem" to "12", "Magic" to "16", "School" to "54", "Sci-Fi" to "46",
    "Slice of Life" to "35", "Supernatural" to "32", "Mecha" to "180",
    "Music" to "57", "Historical" to "58", "Romance" to "74", "Thriller" to "73",
    "Vampire" to "163", "Police" to "136", "Ecchi" to "119",
    // NOTE: verify ALL 43 IDs from /filter page in Stage 2 — the above is partial.
)

class TypeFilter : AnimeFilter.CheckBoxGroup("Type", listOf(
    CheckBox("TV", "TV"), CheckBox("Movie", "Movie"), CheckBox("ONA", "ONA"),
    CheckBox("OVA", "OVA"), CheckBox("Special", "Special"), CheckBox("Music", "Music"),
))

class StatusFilter : AnimeFilter.CheckBoxGroup("Status", listOf(
    CheckBox("Finished Airing", "finished-airing"),
    CheckBox("Currently Airing", "currently-airing"),
    CheckBox("Not Yet Aired", "not-yet-aired"),
))

class LanguageFilter : AnimeFilter.CheckBoxGroup("Language", listOf(
    CheckBox("Sub", "sub"), CheckBox("Dub", "dub"),
))

class SeasonFilter : AnimeFilter.CheckBoxGroup("Season", listOf(
    CheckBox("Spring", "spring"), CheckBox("Summer", "summer"),
    CheckBox("Fall", "fall"), CheckBox("Winter", "winter"),
))

class YearFilter : AnimeFilter.Select<String>("Year", (2026 downTo 1980).map { it.toString() to it.toString() })

class RatingFilter : AnimeFilter.CheckBoxGroup("Rating", listOf(
    CheckBox("G", "G"), CheckBox("PG", "PG"), CheckBox("PG-13", "PG-13"),
    CheckBox("R", "R"), CheckBox("R+", "R+"), CheckBox("Rx", "Rx"),
))

class SortFilter : AnimeFilter.Select<String>("Sort", listOf(
    "Default" to "default", "Latest Updated" to "latest-updated",
    "Latest Added" to "latest-added", "Score" to "score",
    "Name A-Z" to "name-az", "Release Date" to "release-date",
    "Most Viewed" to "most-viewed", "Number of Episodes" to "number_of_episodes",
))

private fun buildFilterQuery(filters: AnimeFilterList): String {
    val params = mutableListOf<String>()
    filters.filterIsInstance<AnimeFilter.CheckBoxGroup<String>>().forEach { group ->
        group.state.filter { it.state }.forEach { cb ->
            params.add("${group.name.lowercase()}[]=${URLEncoder.encode(cb.value, "UTF-8")}")
        }
    }
    // ... handle Select filters (sort, year)
    return params.joinToString("&")
}
```

> ⚠️ **Genre IDs must be verified in Stage 2** — parse the `/filter` page's genre checkboxes at runtime (auto-updates) OR hardcode the full 43 from `/tmp/filter-default.html`.

---

## 6. Anime details (`getAnimeDetails` / `animeDetailsParse`)

### Strategy
```kotlin
// Override animeDetailsRequest to use /watch/{slug} (NOT /anime/{slug} which 404s)
override fun animeDetailsRequest(anime: SAnime): Request {
    return GET("$baseUrl${anime.url}", headers)  // anime.url = /watch/{slug}
}

override fun animeDetailsParse(response: Response): SAnime {
    val doc = response.asJsoup()
    return SAnime.create().apply {
        val binfo = doc.selectFirst("div.binfo") ?: return@apply
        title = binfo.selectFirst("h1.title")?.text()?.trim() ?: ""
        thumbnail_url = binfo.selectFirst("div.poster img")?.attr("src")
        // Alternative titles (Japanese, etc.)
        val altNames = binfo.selectFirst("div.names")?.text()
        // Synopsis
        val synopsis = binfo.selectFirst("div.synopsis div.content")?.text()?.trim()
        // Metadata from bmeta
        val bmeta = doc.selectFirst("div.bmeta")
        val metaRows = bmeta?.select("div.meta > div")?.associate {
            val label = it.ownText().removeSuffix(":").trim()
            val value = it.select("span").text().trim()
            label to value
        } ?: emptyMap()
        val type = metaRows["Type"] ?: ""
        val premiered = metaRows["Premiered"] ?: ""
        val aired = metaRows["Aired"] ?: ""
        val status = metaRows["Status"] ?: ""
        val genres = bmeta?.select("div:contains(Genres) span a")?.eachText()?.joinToString(", ") ?: ""
        val malScore = metaRows["MAL"] ?: ""
        val duration = metaRows["Duration"] ?: ""
        val studios = bmeta?.select("div:contains(Studios) span a")?.eachText()?.joinToString(", ") ?: ""
        // Rating from binfo icons
        val rating = binfo.selectFirst("i.rating")?.text() ?: ""
        // Build description (synopsis + enriched metadata)
        description = buildString {
            synopsis?.let { append(it) }
            if (malScore.isNotBlank()) append("\n\nMAL Score: $malScore")
            if (type.isNotBlank()) append("\nType: $type")
            if (premiered.isNotBlank()) append("\nPremiered: $premiered")
            if (aired.isNotBlank()) append("\nAired: $aired")
            if (duration.isNotBlank()) append("\nDuration: $duration")
            if (studios.isNotBlank()) append("\nStudio: $studios")
            if (rating.isNotBlank()) append("\nRating: $rating")
            altNames?.takeIf { it.isNotBlank() }?.let { append("\n\nAlt titles: $it") }
        }
        genre = genres
        status = when {
            status.contains("Currently Airing", true) -> SAnime.ONGOING
            status.contains("Finished Airing", true) -> SAnime.COMPLETED
            status.contains("Not Yet Aired", true) -> SAnime.UNKNOWN
            else -> SAnime.UNKNOWN
        }
        update_strategy = if (status == SAnime.COMPLETED) AnimeUpdateStrategy.ONLY_UPDATE_ONCE
                          else AnimeUpdateStrategy.ALWAYS_UPDATE
        initialized = true
    }
}
```

---

## 7. Episode list (`getEpisodeList` / `episodeListParse`)

### Strategy
```kotlin
// episodeListRequest: fetch /watch/{slug} to get animeId, then call /ajax/episode/list/{animeId}
override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
    // Step 1: fetch the detail page to extract animeId (data-id)
    val detailResponse = client.newCall(animeDetailsRequest(anime)).awaitSuccess()
    val detailDoc = detailResponse.asJsoup()
    val animeId = detailDoc.selectFirst("[data-id]")?.attr("data-id")
        ?: throw Exception("Could not find animeId on detail page")
    // Step 2: fetch the episode list
    val epResponse = client.newCall(
        GET("$baseUrl/ajax/episode/list/$animeId?vrf=",
            headers.newBuilder()
                .set("Referer", "$baseUrl${anime.url}")
                .set("X-Requested-With", "XMLHttpRequest")
                .build())
    ).awaitSuccess()
    return episodeListParse(epResponse)
}

override fun episodeListParse(response: Response): List<SEpisode> {
    val data = response.parseAs<EpisodeListResponse>()
    val doc = Jsoup.parse(data.result)
    val episodes = doc.select("a[data-ids]").map { el ->
        SEpisode.create().apply {
            // ★ Store the data-ids blob as the url — hosterListRequest uses it
            url = el.attr("data-ids")
            episode_number = el.attr("data-num").toFloatOrNull() ?: 0f
            name = el.selectFirst(".name")?.text()?.takeIf { it.isNotBlank() }
                ?: "Episode ${el.attr("data-num")}"
            // ★ Rule §8: sub/dub in scanlator, NOT in name
            val sub = el.attr("data-sub") == "1"
            val dub = el.attr("data-dub") == "1"
            scanlator = listOfNotNull(
                "SUB".takeIf { sub },
                "DUB".takeIf { dub },
            ).joinToString(" • ").ifEmpty { null }
            // Note: HSUB added after hosterListParse (not available here)
            date_upload = 0L
            // Filler flag
            fillermark = el.classNames().any { it.contains("filler", true) }
        }
    }
    // Reverse: site is oldest-first, Aniyomi wants newest-first
    return episodes.reversed()
}

@Serializable data class EpisodeListResponse(val status: Int, val result: String)
```

### HSUB scanlator completion (heuristic)
After fetching the first episode's server list (in `hosterListParse`), check for `<div data-type="hsub">`. If present, the anime has HSUB — but `scanlator` is already set on the episode list. **Decision:** leave HSUB out of `scanlator` (it's a heuristic anyway) and rely on the Video-level `videoTitle` (`"HSUB - 1080p - ..."`) to communicate audio type at the picker. This is acceptable per rule §8 (the scanlator is for episode-level availability; per-video labeling handles the rest).

---

## 8. Hoster list + video list (STUB — Step 04 plan)

```kotlin
// hosterListParse: see site-analysis.md §0 (steps 4-5)
// videoListParse(response, hoster): see site-analysis.md §0 (steps 6-7)
// resolveVideo: see png-wrapping.md + video-flow.md (m3u8server rewrite)
// These are Step 04 — documented in the Step 04 plan (TBD).
```

---

## 9. Open verification items (MUST resolve before coding)

| # | Item | How to verify | Impact |
|---|---|---|---|
| 1 | Does `/filter?sort=most-viewed` return real results (vs SEO landing)? | Test with agent-browser (maintains session). Try with `cf_clearance` cookie. | If broken: `getPopularAnime` returns 10 hardcoded items, no pagination. |
| 2 | Does `/filter?{filterParams}` work for browse-by-filter (empty query)? | Same as above. | If broken: filters are decorative; browse mode returns no results. |
| 3 | Does `/ajax/anime/search` honor filter params (`?keyword=x&genre[]=1`)? | Append filter params to the search call, check if results are filtered. | If no: filters ignored in search mode too. |
| 4 | Non-empty `vrf` param — which anime have it? Test 5+ anime. | Fetch `/ajax/episode/list/{id}?vrf=` for several anime. | If some need vrf: reverse-engineer `o(this.Ee)` from main.js. |
| 5 | Episode pagination — does `/ajax/episode/list/{id}` paginate for 1000+ episode anime? | Test with One Piece. | If paginated: handle multi-page episode fetch. |
| 6 | Full genre ID list (43 genres) — verify all IDs. | Parse `/filter` page's genre checkboxes. | Build the complete GenreFilter. |
| 7 | HSUB availability consistency — does it vary per episode within an anime? | Compare server lists for EP1 vs EP5 of a multi-season anime. | If varies: scanlator heuristic is imperfect (acceptable). |
| 8 | Episode `data-timestamp` — is it the same across episodes (per anime)? | Compare `data-timestamp` for all episodes. | If same: the mapper API call uses one timestamp per anime (simpler). |

---

## 10. Build order (once verification is done)

1. **Scaffold** the extension (`cp -r EXTENSIONS/_template EXTENSIONS/anikoto`, set up gradle, aniyomi-lib v16, Java 17).
2. **Implement catalog**: headers, popularAnime (with fallback), searchAnime (live API), getFilterList.
3. **Implement details**: animeDetailsParse (`.binfo` + `.bmeta`).
4. **Implement episodes**: episodeListParse (`/ajax/episode/list/{id}`), scanlator strategy.
5. **Build + smoke test** the catalog layer (search → details → episode list displays correctly).
6. **THEN move to Step 04** (video extraction: hoster list, video list, resolveVideo, m3u8server).

> Do NOT start Step 04 until the catalog layer is verified working (search returns results, details render, episode list shows with correct scanlator).
