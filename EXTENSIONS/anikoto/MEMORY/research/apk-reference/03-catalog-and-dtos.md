# 03 — Catalog Layer, DTOs & Filters

> Last updated: 2026-06-22 (session 10) · Status: VERIFIED by decompilation
> Per project rule §1: cross-check / understanding record. No code copied.

Covers the catalog-side logic (popular/latest/search/filters/details/episodes) and the data
models (DTOs + EpisodeMeta encoding + filters) from both reference APKs.

## 1. Catalog endpoints & parsing

### 1.1 `getPopularAnime(page)` → `GET <baseUrl>/most-viewed?page=<N>`
- HTML scraped with Jsoup. Selector for items: `div#list-items > div.item`.
- Per item: title link `a.name.d-title` (fallback `div.ani.poster.tip > a`); thumbnail `div.ani.poster.tip img[src]`.
- Slug extracted from link href: `substringBefore(substringAfter(href, "/watch/", ""), "/ep-", "")`.
- ★ **Discrepancy**: our `EXTENSIONS/anikoto/MEMORY/sites/catalog-and-episodes-analysis.md` documented `/filter?sort=most-viewed&page=N`. The reference uses a dedicated `/most-viewed?page=N` path. **Verify which is current with agent-browser** before finalizing our implementation.

### 1.2 `getLatestUpdates(page)` → `GET <baseUrl>/latest-updated?page=<N>`
- Same `parseAnimeList(doc)` parser as popular.

### 1.3 `getSearchAnime(page, query, filters)` → `GET <baseUrl>/filter?...&page=<N>`
- URL builder: `HttpUrl.parse("<baseUrl>/filter").newBuilder()`.
- If query non-blank: `addQueryParameter("keyword", query)`.
- For each filter:
  - `SortFilter` → `addQueryParameter("sort", filter.toQuery())` (null skipped)
  - `GenreFilter` → for each checked id: `addQueryParameter("genre[]", id)`
  - `TypeFilter` → for each checked id: `addQueryParameter("term_type[]", id)`
  - `StatusFilter` → for each checked id: `addQueryParameter("status[]", id)`
  - `LanguageFilter` → for each checked id: `addQueryParameter("language[]", id)`
- Always: `addQueryParameter("page", String.valueOf(page))`.
- Same `parseAnimeList(doc)` parser.
- ★ **Confirms** our `catalog-and-episodes-analysis.md` query param names (`sort`, `genre[]`, `term_type[]`, `status[]`, `language[]`, `page`). The `keyword` param for the query is now confirmed (we had it as the search path).

### 1.4 `parseAnimeList(doc)` — pagination
- `hasNextPage`:
  - Primary check: `doc.selectFirst("ul.pagination li:has(a[rel=next])")` exists → true.
  - Fallback: read active page from `ul.pagination li.active` text → int; read all `ul.pagination li.page-item a.page-link` texts → ints; `hasNextPage = any { it > activePage }`. Empty list → false.

### 1.5 `getAnimeDetails(anime)` → `GET <baseUrl>/watch/<slug>/ep-1`
- Parses with `parseAnimeDetails(doc, slug)`.
- Selectors (from `#w-info`):
  - Title: `h1.title.d-title` (text; or `data-jp` attr if `titleLang` pref == "jp")
  - Poster: `.poster img[src]`
  - Synopsis: `.synopsis .content` (fallback `.synopsis`)
  - Genre: `.bmeta a[href*='/genre/']` joined with ", "
  - Status: `.bmeta a[href*='/status/']` text → `parseStatus()`
  - Studio (→ `author` + `artist`): the `.bmeta .meta > div` whose text contains "Studios"; its `a` texts joined.
- `buildDescription(doc)`: enriches synopsis with metadata lines (`label: values` from each `.meta > div`), then appends synopsis.
- `parseStatus(text)`:
  - "finished airing" → 2 (COMPLETED)
  - "ongoing" / "currently airing" / "not yet aired" / "upcoming" → 1 (ONGOING)
  - default → 0 (UNKNOWN)
- ★ **Selector discrepancy**: our `catalog-and-episodes-analysis.md` documented `.binfo` + `.bmeta` + `.brating` for the details page. The reference uses `#w-info` + `.bmeta` + `.synopsis`. **The site may have two layouts** (e.g. `/anime/<slug>` vs `/watch/<slug>/ep-1`), OR the site layout changed. The reference always fetches `/watch/<slug>/ep-1` (the episode-1 watch page) for details, which has the `#w-info` layout. **Verify with agent-browser which layout `/watch/<slug>/ep-1` currently serves.**

### 1.6 `getEpisodeList(anime)` — the two-step flow

```
slug = anime.url   (the SAnime.url is just the slug, e.g. "one-piece")
1. GET <baseUrl>/watch/<slug>/ep-1  (default headers)
   doc = resp.asJsoup()
   animeId = doc.selectFirst("#watch-main").attr("data-id")
   if empty → return emptyList()

2. vrf = AnikotoRC4.encodeVrf(animeId)   // RC4("simple-hash", animeId) → Base64.NO_WRAP
   ajaxUrl = "<baseUrl>/ajax/episode/list/<animeId>?vrf=<URLEncode(vrf)>&style=default"
   resp = client.get(ajaxUrl, ajaxHeaders(slug))
   json = json.decodeFromStream(EpisodeListResponse.serializer(), resp.body.byteStream())
   // EpisodeListResponse = { status:Int, result:String(HTML) }
   if status != 200 or result empty → return emptyList()

3. epDoc = Jsoup.parse(json.result)
   for a in epDoc.select("ul.ep-range a, .ep-range a"):
     num       = a.attr("data-num")
     malId     = a.attr("data-mal")
     timestamp = a.attr("data-timestamp")
     dataIds   = a.attr("data-ids")
     hasSub    = a.attr("data-sub") == "1"
     hasDub    = a.attr("data-dub") == "1"
     title     = a.attr("title")  (fallback "Episode " + num)
     meta = EpisodeMeta(slug, num, malId, timestamp, dataIds, hasSub, hasDub, title)
     ep = SEpisode.create()
     ep.url            = meta.encode()    // pipe-delimited (see §3)
     ep.name           = title
     ep.episode_number = num.toFloatOrNull() ?: 0f
     ep.date_upload    = (timestamp.toLongOrNull() ?: 0L) * 1000   // seconds → ms
     scanlator = when {
       hasSub && hasDub → "Sub / Dub"
       hasSub           → "Sub"
       hasDub           → "Dub"
       else             → "Raw"
     }
     ep.scanlator = scanlator
     episodes.add(ep)
   return episodes.reversed()   // newest first (site is oldest-first)
```

★ **Confirms** our `catalog-and-episodes-analysis.md` episode-list flow (the `data-ids`, `data-num`, `data-sub`, `data-dub` attributes; the `scanlator` for sub/dub per rule §8; the reversal to newest-first). **New detail**: the `data-mal` and `data-timestamp` attributes are also captured (used by the v3 mapper API; v16.4 captures them but doesn't use them since mapper was dropped — they're still in EpisodeMeta for the encode/decode symmetry).

## 2. Filters (`AnikotoFiltersKt.getAnikotoFilters()`)

Returns `AnimeFilterList` of: `[SortFilter, GenreFilter, TypeFilter, StatusFilter, LanguageFilter, Separator, Header("Note: sub/dub filter here filters anime, not episodes.")]`

### 2.1 SortFilter (8 options) — extends `AnimeFilter.Sort`
| Display | Value |
|---|---|
| Default | `default` |
| Latest updated | `latest-updated` |
| Latest added | `latest-added` |
| Score | `score` |
| Name A-Z | `name-az` |
| Release date | `release-date` |
| Most viewed | `most-viewed` |
| Number of episodes | `number_of_episodes` |

### 2.2 GenreFilter (43 genres) — `AnimeFilter.Group<CheckBox>`
Numeric IDs (value → display): `1=Action, 2=Adventure, 538=Cars, 8=Comedy, 453=Dementia, 119=Demons, 62=Drama, 214=Ecchi, 3=Fantasy, 180=Game, 215=Harem, 70=Historical, 222=Horror, 74=Isekai, 404=Josei, 46=Kids, 203=Magic, 2310=Mahou Shoujo, 114=Martial Arts, 123=Mecha, 125=Military, 242=Music, 57=Mystery, 162=Parody, 136=Police, 73=Psychological, 28=Romance, 163=Samurai, 14=School, 12=Sci-Fi, 50=Seinen, 252=Shoujo, 235=Shoujo Ai, 15=Shounen, 233=Shounen Ai, 35=Slice of Life, 124=Space, 29=Sports, 16=Super Power, 9=Supernatural, 2316=Suspense, 54=Thriller, 32=Unknown, 58=Vampire`.

`toQueries()` returns the IDs of all checked boxes → each appended as `genre[]=<id>`.

### 2.3 TypeFilter (6) — `AnimeFilter.Group<CheckBox>`
TV, Movie, OVA, ONA, Special, Music (display == value). Appended as `term_type[]=<value>`.

### 2.4 StatusFilter (3) — `AnimeFilter.Group<CheckBox>`
| Display | Value |
|---|---|
| Finished Airing | `finished-airing` |
| Currently Airing | `currently-airing` |
| Not Yet Aired | `not-yet-aired` |

Appended as `status[]=<value>`.

### 2.5 LanguageFilter (2) — `AnimeFilter.Group<CheckBox>`
Sub → `sub`, Dub → `dub`. Appended as `language[]=<value>`.

★ Per the Header note: this filters **which anime appear** (anime that have a sub or dub stream), NOT the playback audio selection (that's the `preferredAudio` preference).

### 2.6 `TriStateCheckBox` — misleadingly named
Despite the name, this is a simple binary `AnimeFilter.CheckBox` (initial state `false`). It does NOT extend `AnimeFilter.TriState` and has no STATE_EXCLUDE. It's just a `CheckBox` that also carries an `id` field separate from the display `name`. The `id` is what `toQueries()` returns.

★ **For our extension**: ext-lib 16 makes `AnimeFilter.CheckBox` etc. abstract (per our session-08 finding). We already created concrete subclasses `CheckBoxVal` (stores a value string) + `CheckBoxGroup` for this. The reference's `TriStateCheckBox` is the same pattern. Adopt our `CheckBoxVal` approach.

## 3. EpisodeMeta — the pipe-delimited `SEpisode.url` payload (★ clever pattern)

**Not `@Serializable`.** A local data carrier used as `SEpisode.url` so `getHosterList` can recover all episode state without re-fetching the watch page.

| Property | Type | Purpose |
|---|---|---|
| `slug` | String | Anime slug |
| `epNum` | String | Episode number (string for flexibility) |
| `malId` | String | MAL ID (v3 mapper; v16.4 captures but unused) |
| `timestamp` | String | Episode publish timestamp (unix seconds) |
| `dataIds` | String | Comma-separated server data-IDs for stream resolution |
| `hasSub` | Boolean | Subtitle stream available |
| `hasDub` | Boolean | Dub stream available |
| `epTitle` | String | Episode display title |

**Encode format**:
```
<slug>/ep-<epNum>|<malId>|<timestamp>|<dataIds>|<hasSub?1:0>|<hasDub?1:0>|<epTitle>
```
- `epTitle` has literal `|` replaced with Unicode full-width `│` (U+2502) to avoid collisions; reversed on decode.
- `Companion.decode(encoded)` splits on `|`, rejoins indexes 6+ with `|` as epTitle (after `│`→`|` restore).

★ **For our extension**: adopt this pattern. It eliminates a re-fetch in `getHosterList`. Our `SEpisode.url` currently just holds the slug — upgrade it to carry the full `EpisodeMeta`.

## 4. DTOs (the `@Serializable` data models)

All live in one Kotlin source file (`AnikotoDto.kt`). 6 DTOs mapping 4 API endpoints + 1 local encoding. **No enums, no polymorphism, no custom serializers** — all flat data classes.

### 4.1 `EpisodeListResponse` — episode-list API envelope
```kotlin
@Serializable data class EpisodeListResponse(
    val status: Int = 0,
    val result: String = ""   // HTML payload (stringified)
)
```

### 4.2 `ServerListResponse` — server-list API envelope (identical shape)
```kotlin
@Serializable data class ServerListResponse(
    val status: Int = 0,
    val result: String = ""   // HTML payload (stringified)
)
```

### 4.3 `ServerResponse` + nested `ServerResult` + `SkipData` — per-server API
```kotlin
@Serializable data class ServerResponse(
    val status: Int = 0,
    val result: ServerResult? = null
)

@Serializable data class ServerResult(
    val url: String = "",                              // the iframe URL
    @SerialName("skip_data") val skipData: SkipData? = null   // ★ ONLY @SerialName override
)

@Serializable data class SkipData(
    val intro: List<Float> = emptyList(),   // seconds with fractional precision
    val outro: List<Float> = emptyList()
)
```
★ `skip_data` is the **only** `@SerialName` override in the entire DTO set (snake_case vs camelCase). The intro/outro floats are MPV skip-point format. **Not consumed by the player** in the current extension (the reference captures but doesn't wire them to mpv) — future enhancement.

### 4.4 `VidTubeSourcesResponse` + nested `VidTubeSources` + `VidTubeTrack` — VidTube API
```kotlin
@Serializable data class VidTubeSourcesResponse(
    val sources: VidTubeSources? = null,
    val tracks: List<VidTubeTrack> = emptyList()
)

@Serializable data class VidTubeSources(
    val file: String = ""   // the master m3u8 URL
)

@Serializable data class VidTubeTrack(
    val file: String = "",   // track URL (typically .vtt)
    val label: String = "",  // human-readable (e.g. "English")
    val kind: String = ""    // HTML5 <track kind> convention: "subtitles", "captions", etc.
)
```

### 4.5 Custom-serialization observations
1. Only `ServerResult.skipData` uses `@SerialName` (snake_case override). Everything else uses the Kotlin property name verbatim — the API is mostly camelCase-mirrored.
2. Nullable-result pattern: `ServerResponse.result` and `VidTubeSourcesResponse.sources` are nullable; the two `*ListResponse.result` fields are non-nullable String (default `""`).
3. Floats for skip timestamps (MPV skip-point convention).
4. Default-value elision: all DTOs use `shouldEncodeElementDefault(...) || value != default` — defaults not serialized.
5. `EpisodeMeta` is the odd one out — not a network DTO, custom pipe-delimited encoding.

★ **For our extension**: reimplement these 6 DTOs ourselves (they're ~40 LOC total). Use `@SerialName("skip_data")` for the one snake_case field. Our `EXTENSIONS/anikoto/MEMORY/sites/endpoints.md` already documented the response shapes — this confirms them.

## 5. What this means for OUR extension (catalog layer — already built in session 08)

Our session-08 `Anikoto.kt` already implements the catalog layer. Cross-check findings:

| Area | Our session-08 impl | Reference | Action |
|---|---|---|---|
| Popular endpoint | `/filter?sort=most-viewed&page=N` | `/most-viewed?page=N` | ⚠️ **verify live** which is current |
| Latest endpoint | `/filter?sort=latest-updated&page=N` | `/latest-updated?page=N` | ⚠️ **verify live** |
| Search params | `keyword`, `sort`, `genre[]`, `term_type[]`, `status[]`, `language[]`, `page` | same | ✓ confirmed |
| Details selectors | `.binfo`, `.bmeta`, `.brating` | `#w-info`, `.bmeta`, `.synopsis` | ⚠️ **verify live** — may be two layouts or a site change |
| Episode list flow | two-step (watch page → AJAX), `data-ids/num/sub/dub`, reversed, scanlator | same + captures `data-mal`, `data-timestamp` | ✓ confirmed; ★ upgrade `SEpisode.url` to `EpisodeMeta` encode |
| vrf | not implemented (we noted it) | RC4("simple-hash", animeId) → Base64.NO_WRAP | ★ **must implement** (server validates) |
| Filters | 43 genres, 6 types, 3 statuses, 2 langs, 8 sorts | same (exact same IDs/values) | ✓ confirmed |
| EpisodeMeta | not used (we store slug only) | pipe-delimited 8-field encode | ★ **adopt** (eliminates re-fetch) |

**Action items for our catalog layer** (before or during Stage 4):
1. **Verify live** with agent-browser: `/most-viewed` vs `/filter?sort=most-viewed`, and the details-page selectors (`#w-info` vs `.binfo`).
2. **Implement AnikotoRC4** (key `"simple-hash"`) for the episode-list `vrf` param — the server will reject without it.
3. **Upgrade `SEpisode.url`** to `EpisodeMeta` pipe-encoding (carries slug, epNum, malId, timestamp, dataIds, hasSub, hasDub, epTitle).
4. Keep our existing filter list (it matches the reference exactly).
