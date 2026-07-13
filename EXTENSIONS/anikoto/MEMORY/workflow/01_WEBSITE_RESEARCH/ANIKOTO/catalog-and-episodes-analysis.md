# ANIKOTO — Catalog & Episodes Analysis (Stage 1, continued)

> Last updated: 2026-06-22 · Status: VERIFIED (every claim tested against the live site)
> Companion to `site-analysis.md` (the video chain). This doc covers the CATALOG side:
> popular/latest/search/filters/details/episodes.

---

## 0. TL;DR — catalog architecture

```
Popular/Latest:  ❌ NO dedicated popular/latest browse pages. The homepage is a static SEO landing.
                  "Trending" sidebar (10 hardcoded popular-search links) is the closest thing.
                  → Implement getPopularAnime + getLatestUpdates via the FILTER endpoint with
                    specific sort params (sort=most-viewed for popular, sort=latest-updated for latest).

Search:          Two-tier:
                  1. Live autosuggest: GET /ajax/anime/search?keyword={q} → JSON {result:{html, linkMore}}
                     Returns up to ~10 quick-match items (poster, title, jp title, rating, score, type, year).
                     THIS is what Aniyomi's searchAnime should use (fast, returns enough for a search).
                  2. Full results: GET /filter?keyword={q}&{filters}&page={n}
                     ❌ BROKEN — redirects to homepage when fetched via curl (returns the SEO landing
                     page with "Popular searches" instead of results). The server may require a
                     session cookie or specific referrer to serve real results. UNVERIFIED.
                     → Use the live autosuggest for search; skip the full /filter results page.

Filters:         GET /filter (the filter form page) exposes ALL filter options as checkboxes/selects:
                  genre[] (43 genres), season[] (4), year[] (1980-2026), term_type[] (6: TV/Movie/ONA/OVA/Special/Music),
                  status[] (3), language[] (sub/dub), rating[] (6), source[] (18), sort (8 options),
                  ep_min/ep_max (episode count range), exclude_watchlist, remember_me.
                  → Map these to AnimeFilterList (Header/Sort/CheckBox/Select/TriState filters).
                  → The filter VALUES submit to /filter?{params} which is currently broken for full
                     results (see Search above). WORKAROUND: build the filter query string and submit
                     to /ajax/anime/search?keyword={q} with the filter params appended — UNVERIFIED
                     whether the search API honors filter params. If not, filters may be non-functional
                     for browsing (only keyword search works). NEEDS VERIFICATION in Stage 2.

Anime details:   GET /watch/{slug} (NO /ep-N suffix) → HTML with:
                  - <div class="binfo">: poster, <h1 class="title d-title" data-jp="...">, alternative
                    titles (.names, semicolon-separated), rating/quality/sub/dub icons, synopsis.
                  - <div class="bmeta">: two <div class="meta"> blocks:
                    Block 1: Type, Premiered (season+year), Aired (date range), Status, Genres.
                    Block 2: MAL Score, Duration, Episodes count, Studios, Producers.
                  - <div class="brating">: user rating (score/10 + review count).
                  - <div id="ani-seasons">: seasons (loaded via /api/seasons/{animeId}).
                  - <div id="w-episodes">: episode list (loaded via /ajax/episode/list/{animeId}).
                  - <aside class="sidebar">: "Trending" sidebar (10 popular anime, hardcoded).
                  NOTE: /anime/{slug} returns 404 — use /watch/{slug} for details.

Episode list:    GET /ajax/episode/list/{animeId}?vrf={vrf} → JSON {status, result: HTML}
                  HTML: <a href="#" data-id data-num data-slug data-mal data-timestamp
                       data-sub="1|0" data-dub="1|0" data-ids="{SERVERS_B64}">
                       <b>{num}</b> <span class="name">{name}</span> ... </a>
                  - data-ids = the base64 blob passed to /ajax/server/list?servers=
                  - data-sub/data-dub = audio availability flags (1=available, 0=not)
                  - data-mal + data-timestamp = used for the Kiwi-Stream mapper API call
                  - data-num = episode number; data-slug = episode slug (usually same as num)
                  - HSUB availability is NOT flagged here — must check the server list for hsub <li> items
                  - Episode name: inside <span class="name"> (may be empty for some anime)
```

---

## 1. Homepage & Popular/Latest

### What the homepage actually is
`GET https://anikototv.to/` → 25 KB static HTML. It's an **SEO landing page**, NOT a browse page:
- `<h2>AniKoto - Stream Anime Online for FREE</h2>` (marketing headline)
- `<h3>Is AniKoto secure to use?</h3>` / `<h3>Why choose AniKoto...</h3>` (FAQ-style)
- "Popular searches" section: 10 hardcoded links (Solo Leveling S2, One Piece, Sakamoto Days, Naruto Shippuden, Solo Leveling, Blue Lock S2, Bleach, Shangri-La Frontier S2, Dandadan, Possibly the Greatest Alchemist).
- NO carousel, NO trending grid, NO latest-updates grid in the static HTML.
- NO XHR calls to load popular/latest content on the homepage.

### "Trending" sidebar (on detail pages)
Every `/watch/{slug}` page has an `<aside class="sidebar">` with a "Trending" section containing the SAME 10 hardcoded popular-search links. This is not a dynamic "trending now" feed — it's a static list.

### Implication for getPopularAnime / getLatestUpdates
**The site has NO dedicated popular/latest endpoints.** Options:
1. **Use `/filter?sort=most-viewed&page={n}`** for popular — but `/filter` is currently broken for full results (returns SEO landing via curl). NEEDS VERIFICATION in Stage 2 whether it works with a session cookie or in-app.
2. **Use `/filter?sort=latest-updated&page={n}`** for latest — same caveat.
3. **Fallback:** `supportsLatest = false` and `getPopularAnime` returns the 10 hardcoded "Popular searches" items (poor UX but functional).
4. **Best option:** in Stage 2, test `/filter?sort=most-viewed` with agent-browser (which maintains a session) to see if real results render. If yes, replicate the session/cookie in the extension. If no, use the fallback.

> ⚠️ **OPEN VERIFICATION ITEM:** does `/filter?sort=most-viewed` return real results with a session cookie? Test in Stage 2. If broken, `supportsLatest = false` + popular = hardcoded list.

---

## 2. Search (the live autosuggest API)

### The working search endpoint
```
GET https://anikototv.to/ajax/anime/search?keyword={query}
Headers: Referer: https://anikototv.to/, X-Requested-With: XMLHttpRequest
```

### Response (VERIFIED)
```json
{
  "status": 200,
  "result": {
    "html": "<div class=\"scaff items\">  <a class=\"item\" href=\"https://anikototv.to/watch/{slug}\">
               <div class=\"poster\"><span><img src=\"{poster_url}\"/></span></div>
               <div class=\"info\">
                 <div class=\"name d-title\" data-jp=\"{japanese_title}\">{english_title}</div>
                 <div class=\"meta\">
                   <span class=\"dot rating\">{rating}</span>
                   <span class=\"dot text-gray2\"><i class=\"fas fa-star\"></i> {score}</span>
                   <span class=\"dot\">{type}</span> <span class=\"dot\">{year}</span>
                 </div>
               </div>
             </a>  ...</div>",
    "linkMore": "/filter?keyword={query}"
  }
}
```

### Result item fields (from the HTML)
| Field | Source | Example | Maps to SAnime |
|---|---|---|---|
| `url` | `<a class="item" href="...">` | `/watch/wistoria-...-dua04` | `SAnime.url` (strip the domain, store path only) |
| `title` | `<div class="name d-title">{text}` | `Wistoria: Wand and Sword Season 2` | `SAnime.title` |
| Japanese title | `data-jp` attr on `.name` | `Tsue to Tsurugi no Wistoria Season 2` | (optional — could append to description or use for title-language pref) |
| Poster | `<img src="...">` | `https://cdn.anipixcdn.co/thumbnail/{hash}.jpg` | `SAnime.thumbnail_url` |
| Rating | `<span class="dot rating">` | `PG-13` | (display only) |
| Score | `<span class="dot text-gray2">` (after the star icon) | `8.12` | (display only, could prepend to description) |
| Type | `<span class="dot">` (3rd) | `TV` | (display only) |
| Year | `<span class="dot">` (4th) | `2026` | (display only) |

### Aniyomi `searchAnimeParse` strategy
```kotlin
override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
    // NOTE: filters from the UI are handled separately (see §3 below)
    // The live search API only takes a keyword. For filter-only browsing (no keyword),
    // fall back to /filter?{filterParams}&page={page} (if it works) OR return empty.
    return GET("$baseUrl/ajax/anime/search?keyword=${URLEncoder.encode(query, "UTF-8")}",
               headers = headers.newBuilder()
                   .set("Referer", "$baseUrl/")
                   .set("X-Requested-With", "XMLHttpRequest")
                   .build())
}

override fun searchAnimeParse(response: Response): AnimesPage {
    val data = response.parseAs<SearchResponse>()  // {status, result: {html, linkMore}}
    val doc = Jsoup.parse(data.result.html)
    val animes = doc.select("a.item").map { el ->
        SAnime.create().apply {
            url = el.attr("href").substringAfter(baseUrl)  // path only
            title = el.selectFirst(".name")?.text() ?: ""
            thumbnail_url = el.selectFirst("img")?.attr("src")
            // data-jp (Japanese title) available at el.selectFirst(".name")?.attr("data-jp")
        }
    }
    // The live search returns ~10 items max, no pagination — nextPage = null
    return AnimesPage(animes, hasNextPage = false)
}
```

> ⚠️ **The live search has NO pagination** — it returns ~10 quick matches. For a "more results" experience, the user clicks "linkMore" which goes to `/filter?keyword=...` (currently broken via curl). For Aniyomi, returning 10 items with `hasNextPage = false` is acceptable (users refine the query).

---

## 3. Filters (the /filter form)

### Full filter inventory (VERIFIED from the /filter page HTML)
All filters submit as GET query params to `/filter`:

| Filter | Param name | Type | Values |
|---|---|---|---|
| Genre | `genre[]` | checkbox (43 options) | 1=Action, 2=Adventure, 3=Sports, 538=Avant Garde, 8=Drama, ... (negate with `-` prefix to exclude) |
| Season | `season[]` | checkbox | fall, summer, spring, winter |
| Year | `year[]` | checkbox | 1980–2026 |
| Type | `term_type[]` | checkbox | TV, Movie, ONA, OVA, Special, Music |
| Status | `status[]` | checkbox | finished-airing, currently-airing, not-yet-aired |
| Language | `language[]` | checkbox | sub, dub |
| Rating | `rating[]` | checkbox | G, PG, PG-13, R, R+, Rx |
| Source | `source[]` | checkbox | manga, original, light_novel, other, video_game, visual_novel, novel, web_novel, unknown, web_manga, game, 4-koma_manga, book, picture_book, mixed_media, card_game, music, radio |
| Sort | `sort` | select | default, latest-updated, latest-added, score, name-az, release-date, most-viewed, number_of_episodes |
| Episode range | `ep_min`, `ep_max` | number inputs | e.g. ep_min=12&ep_max=24 |
| Exclude watchlist | `exclude_watchlist` | checkbox | 1 |
| Remember | `remember_me` | checkbox | 1 |
| Keyword | `keyword` | text | (the search box) |
| Page | `page` | number | 1–293 (293 pages total) |

### Genre ID → name mapping (full list, VERIFIED)
The genre checkbox labels aren't in the static HTML's `<option>` (they're in a `<ul class="c4">` menu on the homepage). From the homepage genre menu:
```
1=Action, 2=Adventure, 3=Sports, 538=Avant Garde, 8=Drama, 453=Accounting, 119=Ecchi,
62=Iyashikei, 214=Gore, 180=Mecha, 215=Medical, 70=Mythology, 222=Reincarnation,
74=Romance, 404=Love Polygon, 46=Sci-Fi, 203=Space, 2310=Strategy Game, 114=Subtle Drama,
123=Cyberpunk, 125=Deleted Scene, 242=Survival, 57=Music, 162=Time Travel, 136=Police,
73=Thriller, 28=Comedy, 163=Vampire, 14=Horror, 12=Harem, 50=Adult Cast, 252=Visual Arts,
235=CGDCT, 15=Kids, 233=Gourmet, 35=Slice of Life, 124=Delinquents, 29=Crime, 16=Magic,
9=Fantasy, 2316=Organize & Manage, 54=School, 32=Supernatural, 58=Historical, ...
```
(All 43 genre IDs are in `/tmp/filter-default.html`. The extension should parse the `/filter` page's genre checkboxes at runtime to build the filter list — this auto-updates if the site adds genres.)

### Aniyomi `AnimeFilterList` mapping
```kotlin
override fun getFilterList(): AnimeFilterList = AnimeFilterList(
    AnimeFilter.Header("Note: filters work only when browsing /filter (may require session)"),
    AnimeFilter.Header("Search by keyword uses the live autosuggest (ignores filters)"),
    GenreFilter(),          // CheckBox filter, 43 options
    SeasonFilter(),         // CheckBox filter, 4 options
    YearFilter(),           // CheckBox filter, 1980-2026 (or a Select for compactness)
    TypeFilter(),           // CheckBox filter, 6 options
    StatusFilter(),         // CheckBox filter, 3 options
    LanguageFilter(),       // CheckBox filter, 2 options (sub/dub)
    RatingFilter(),         // CheckBox filter, 6 options
    SortFilter(),           // Select filter, 8 options
    // Source filter omitted (18 options, rarely used)
    // ep_min/ep_max omitted (niche)
)
```

### ⚠️ CRITICAL: filters vs search interaction
- **The live search API (`/ajax/anime/search`) ONLY accepts `keyword`** — it ignores genre/year/type/etc params.
- **The full `/filter?{all params}` endpoint is currently broken via curl** (returns the SEO landing page).
- **This means:** if the user sets filters WITHOUT a keyword, the extension can't browse (no working endpoint). If the user sets filters WITH a keyword, only the keyword is honored (filters ignored).

**Stage 2 must verify:** does `/filter?genre[]=1&sort=most-viewed` work with a session cookie (agent-browser maintains one)? If yes, the extension can browse with filters by first hitting the homepage to get a session cookie, then calling `/filter`. If no, filters are decorative only (and `supportsLatest = false`).

---

## 4. Anime details (`/watch/{slug}`)

### Endpoint
```
GET https://anikototv.to/watch/{slug}
```
- **NO `/ep-N` suffix** — that's the episode watch page (which also contains the detail).
- The anime detail + episode list + server list are ALL on the `/watch/{slug}` page (the `/ep-N` variant just pre-selects an episode).
- `/anime/{slug}` returns 404 — DON'T use it. Use `/watch/{slug}`.

### Detail structure (VERIFIED)
```html
<div class="binfo">
  <div class="poster"><span><img itemprop="image" src="{poster_url}" alt="{title}"/></span></div>
  <div class="info">
    <h1 itemprop="name" class="title d-title" data-jp="{japanese_title}">{english_title}</h1>
    <div class="names font-italic mb-2">{alt_titles_semicolon_separated}</div>
    <div class="meta icons mb-3">
      <i class="rating">{age_rating}</i>        <!-- e.g. PG-13 -->
      <i class="quality">{quality}</i>           <!-- e.g. HD -->
      <i class="sub fas fa-closed-captioning"></i>  <!-- sub available icon -->
      <i class="dub fas fa-microphone"></i>         <!-- dub available icon -->
    </div>
    <div class="synopsis mb-3"><div class="shorting"><div class="content">{synopsis}</div></div></div>
  </div>
</div>
<div class="bmeta">
  <div class="meta">
    <div>Type: <span>{type}</span></div>                          <!-- TV/Movie/ONA/OVA/Special/Music -->
    <div>Premiered: <span><a href="/filter?season[]={season}&year[]={year}">{SEASON} {YEAR}</a></span></div>
    <div>Aired: <span>{aired_date_range}</span></div>             <!-- e.g. "Apr 12, 2026 to ?" -->
    <div>Status: <span><a href="/status/{status-slug}">{status}</a></span></div>  <!-- Currently Airing / Finished Airing / Not Yet Aired -->
    <div>Genres: <span><a href="/genre/{slug}">{Genre}</a>, ...</span></div>
  </div>
  <div class="meta">
    <div>MAL: <span>{mal_score}</span></div>                      <!-- e.g. 8.12 -->
    <div>Duration: <span>{duration}</span></div>                  <!-- e.g. "24 min" -->
    <div>Episodes: <span>{episode_count}</span></div>             <!-- e.g. 12 -->
    <div>Studios: <span><a href="/studio/{slug}">{Studio}</a>, ...</span></div>
    <div>Producers: <span><a href="/producer/{slug}">{Producer}</a>, ...</span></div>
  </div>
</div>
<div class="brating">
  <div class="rating" id="w-rating" data-id="{animeId}" data-score="{score}">
    <div class="score"><span class="value">{score}/10</span> <span class="by">{review_count} reviews</span></div>
  </div>
</div>
<div id="ani-seasons"></div>  <!-- filled by /api/seasons/{animeId} -->
```

### SAnime field mapping
| SAnime field | Source | Example |
|---|---|---|
| `url` | from search/episode list (the `/watch/{slug}` path) | `/watch/wistoria-...-dua04` |
| `title` | `<h1 class="title d-title">` text | `Wistoria: Wand and Sword Season 2` |
| `description` | `<div class="synopsis"> .content` text + appended metadata (score, studios) | `Second season of...` |
| `genre` | `<div>Genres:` → all `<a>` texts, comma-separated | `Action, Adventure, Fantasy, School, Shounen, Drama` |
| `status` | `<div>Status:` → text → map to SAnime constants | `Currently Airing` → `SAnime.ONGOING` |
| `thumbnail_url` | `<div class="poster"> <img src="...">` | `https://cdn.anipixcdn.co/thumbnail/{hash}.jpg` |
| `background_url` | (not available) | — |
| `artist`/`author` | (studios/producers go in description, not these fields) | — |
| `update_strategy` | infer from status: ongoing → `ALWAYS_UPDATE`, completed → `ONLY_UPDATE_ONCE` | `AnimeUpdateStrategy.ALWAYS_UPDATE` |
| `initialized` | `true` (after details fetch) | `true` |

### Status mapping
| Site status | SAnime constant |
|---|---|
| Currently Airing | `SAnime.ONGOING` (1) |
| Finished Airing | `SAnime.COMPLETED` (2) |
| Not Yet Aired | `SAnime.UNKNOWN` (0) (no good mapping) |

### Description enrichment (optional but nice)
Append to the synopsis: `\n\nMAL Score: {score}\nType: {type}\nAired: {aired}\nStudio: {studios}\nDuration: {duration}`. This surfaces metadata the user would otherwise not see in Aniyomi.

---

## 5. Episode list

### Endpoint
```
GET https://anikototv.to/ajax/episode/list/{animeId}?vrf={vrf}
Headers: Referer: https://anikototv.to/watch/{slug}, X-Requested-With: XMLHttpRequest
```
- `{animeId}` from the watch page's `data-id` attribute (e.g. `8737`).
- `{vrf}` is usually empty (`?vrf=`) for most anime. For some, it's an obfuscated token. The `o(this.Ee)` function in main.js generates it from the animeId — likely a base64-encoded + hashed id. **If non-empty vrf is encountered, need to reverse-engineer `o()`** — defer to Stage 2.

### Response (VERIFIED)
```json
{
  "status": 200,
  "result": "<ul class='ep-range'>
    <li><a href='#' data-id='{epId}' data-num='{num}' data-slug='{slug}' data-mal='{malId}'
           data-timestamp='{unixTs}' data-sub='1|0' data-dub='1|0'
           data-ids='{SERVERS_B64}'
           class='active  ' >
       <b>{num}</b>
       <span class='name'>{episode_name}</span>
       <span class='{filler|normal}'>{filler_flag}</span>
       </a></li>
    ...
  </ul>"
}
```

### Per-episode attributes
| Attr | Meaning | Use |
|---|---|---|
| `data-id` | Episode internal ID (e.g. 132084) | (internal, not directly used) |
| `data-num` | Episode number (1, 2, 3...) | `SEpisode.episode_number` |
| `data-slug` | Episode slug (usually = num) | (used in URL: /ep-{slug}) |
| `data-mal` | MAL anime ID (e.g. 59983) | Used for Kiwi-Stream mapper API |
| `data-timestamp` | Unix timestamp (e.g. 1778410371) | Used for Kiwi-Stream mapper API |
| `data-sub` | SUB available (1/0) | `SEpisode.scanlator` (rule §8) |
| `data-dub` | DUB available (1/0) | `SEpisode.scanlator` (rule §8) |
| `data-ids` | Base64-encoded server-list param | Passed to `/ajax/server/list?servers=` |
| `<span class="name">` | Episode name (may be empty) | `SEpisode.name` (fallback to `Episode {num}`) |
| `class="active"` | Currently-selected episode | (ignore) |
| filler class | Filler episode flag | `SEpisode.fillermark = true` if filler |

### SEpisode mapping (rule §8 — scanlator for sub/dub)
```kotlin
override fun episodeListParse(response: Response): List<SEpisode> {
    val data = response.parseAs<EpisodeListResponse>()
    val doc = Jsoup.parse(data.result)
    return doc.select("a[data-ids]").map { el ->
        SEpisode.create().apply {
            url = el.attr("data-ids")  // store the servers param directly (not a URL path)
            // NOTE: SEpisode.url is what gets passed to episodeListRequest + then to hosterListRequest
            episode_number = el.attr("data-num").toFloatOrNull() ?: 0f
            name = el.selectFirst(".name")?.text()?.takeIf { it.isNotBlank() }
                ?: "Episode ${el.attr("data-num")}"
            // ★ Rule §8: sub/dub availability in scanlator (NOT in name)
            val sub = el.attr("data-sub") == "1"
            val dub = el.attr("data-dub") == "1"
            // HSUB availability not flagged here — determined later in hosterListParse
            // For now, set scanlator to sub/dub; hsub added after hoster list fetch
            scanlator = listOfNotNull(
                "SUB".takeIf { sub },
                "DUB".takeIf { dub },
            ).joinToString(" • ").ifEmpty { null }
            date_upload = 0L  // no date in the episode list HTML
        }
    }.reversed()  // site lists oldest-first; Aniyomi wants newest-first (or ascending?)
}
```

### HSUB availability (rule §8 — complete the scanlator)
HSUB is NOT flagged on the episode `<a>`. It must be inferred from the server list:
- In `hosterListParse`, check if `<div data-type="hsub">` has any `<li>` items.
- If yes, update the episode's scanlator to include `HSUB`.

But `hosterListParse` is called per-episode (at play time), so updating `scanlator` there is too late (the episode list is already displayed). **Alternative:** in `episodeListParse`, after building the list, fetch the server list for the FIRST episode, check for HSUB, and assume HSUB availability is consistent across episodes (usually is). Set `scanlator = "SUB • HSUB • DUB"` if HSUB present in the first episode's server list.

> ⚠️ This is a heuristic. If HSUB availability varies per episode, the scanlator will be wrong for some. Acceptable tradeoff (the user sees correct availability at the video-picker level regardless, because each Video's `videoTitle` includes the audio type).

### Episode ordering
- The site lists episodes oldest-first (EP1, EP2, ...).
- Aniyomi expects newest-first (so the latest episode is at the top of the list). **Reverse the list** in `episodeListParse`.

### Episode pagination
- The episode list HTML has NO `data-page` attributes for the test anime (11 episodes fit on one page).
- For long anime (100+ episodes), there may be pagination. **UNVERIFIED** — need to test with a long anime (e.g. One Piece) in Stage 2. If paginated, the episode list HTML would have `data-page` attrs or a pagination control; the extension would need to fetch additional pages.

---

## 6. Seasons / franchise (`/api/seasons/{animeId}`)

### Endpoint
```
GET https://anikototv.to/api/seasons/{animeId}
Headers: Referer: https://anikototv.to/watch/{slug}
```
Returns JSON `{status, result: HTML}` with a "seasons" swiper (each season is a link to another `/watch/{slug}`). This is for navigating between seasons of the same franchise.

### Implication for the extension
- Aniyomi's `SAnime.fetch_type` defaults to `FetchType.Episodes`.
- ANIKOTO doesn't use the ext-lib 16 `FetchType.Seasons` flow (seasons are separate anime entries, not nested).
- **Don't implement `seasonListParse`** — throw `UnsupportedOperationException` (per the ext-lib 16 contract). Each season is its own `SAnime` entry discovered via search.

---

## 7. Implementation plan (Stage 2 preview — catalog + episodes)

### Build decisions (to finalize in Stage 2)
1. **`getPopularAnime`**: try `/filter?sort=most-viewed&page={n}` with a session cookie. If broken, return the 10 hardcoded "Popular searches" items (fetch homepage, parse the `.item` links).
2. **`getLatestUpdates`**: try `/filter?sort=latest-updated&page={n}`. If broken, `supportsLatest = false`.
3. **`searchAnime`**: use `/ajax/anime/search?keyword={q}` (VERIFIED working). Return ~10 items, `hasNextPage = false`.
4. **`getFilterList`**: expose genre/season/year/type/status/language/rating/sort filters. Document that filters only work with `/filter` browsing (which may be broken).
5. **`animeDetailsParse`**: parse `/watch/{slug}` HTML — extract from `.binfo` + `.bmeta` + `.brating`. Enrich description with score/studios.
6. **`episodeListParse`**: call `/ajax/episode/list/{animeId}?vrf=`, parse `<a data-ids>`, reverse list, set `scanlator` from `data-sub`/`data-dub` + HSUB heuristic.
7. **`animeDetailsRequest`**: override to fetch `/watch/{slug}` (NOT `/anime/{slug}` which 404s).
8. **`episodeListRequest`**: override to call `/ajax/episode/list/{animeId}` — need to extract `animeId` from the SAnime's stored url (or re-fetch the detail page to get `data-id`). **Decision:** store `animeId` in `SAnime.url` as `/watch/{slug}#{animeId}` OR fetch the detail page first in `episodeListRequest` to extract `data-id`. Latter is cleaner (one extra request but no URL hacking).

### Open verification items for Stage 2
1. **Does `/filter?sort=most-viewed` work with a session cookie?** Test with agent-browser (maintains cookies).
2. **Non-empty `vrf` param** — test an anime where `vrf` is non-empty. Reverse-engineer `o(this.Ee)` if needed.
3. **Episode pagination** — test with One Piece (1000+ episodes). Does `/ajax/episode/list/{id}` paginate?
4. **Does `/ajax/anime/search` honor filter params** (e.g. `?keyword=naruto&genre[]=1`)? If yes, filters work in search. If no, filters are browse-only.
5. **HSUB availability consistency** — does HSUB availability vary per episode within the same anime? (Affects the scanlator heuristic.)

---

## 8. Related docs

- `site-analysis.md` — the video chain (Stage 1 part 1).
- `endpoints.md` — full endpoint inventory (includes the catalog endpoints).
- `audio-types.md` — the 3 audio types + scanlator strategy.
- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` — `AnimeHttpSource` abstract methods (popularAnimeRequest, etc.).
- `MEMORY/guides/02-how-to-create-a-new-extension.md` §5 — SAnime/SEpisode filling.
- `MEMORY/PROJECT_RULES.md` §7 (3 audio types), §8 (scanlator for sub/dub).
