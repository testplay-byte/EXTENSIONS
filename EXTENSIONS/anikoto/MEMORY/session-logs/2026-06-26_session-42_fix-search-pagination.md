# Session 42 — Fix Search: Use Paginated /filter Endpoint (v16.26)

> Date: 2026-06-26 · Session #: 42 · Duration: ~medium · Timezone: Asia/Karachi
> Type: BUG FIX — search returned only ~5 results

## Goal

Fix the search page issue: **only the top 5 results show** when searching for an anime.
Per PROJECT_RULES §1 (verify before trusting) and §2 (one change at a time), analyze the
root cause, verify against the live site, make the minimal fix, build, and verify.

## Root cause analysis

### Step 1 — Read the search code

`Anikoto.kt:161-178` (before fix):
```kotlin
override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
    if (query.isNotBlank()) {
        GET("$baseUrl/ajax/anime/search?keyword=${URLEncoder.encode(query, "UTF-8")}", xhrHeaders())
        // ^ autosuggest endpoint — ignores `page` param, ignores filters
    } else {
        GET("$baseUrl/filter?${AnikotoFilters.buildQuery(filters)}&page=$page", headers)
        // ^ paginated filter endpoint (40 items/page, proper next-page links)
    }

override fun searchAnimeParse(response: Response): AnimesPage {
    val body = response.body.string()
    return if (body.trimStart().startsWith("{")) {
        val data = json.decodeFromString<SearchApiResponse>(body)
        val doc = Jsoup.parse(data.result.html)
        val items = doc.select("a.item").map { parseSearchItem(it) }
        AnimesPage(items, hasNextPage = false)  // ^ hardcoded false — no pagination possible
    } else {
        parseFilterResults(response)
    }
}
```

Two bugs:
1. When `query` is non-blank, the code hits `/ajax/anime/search?keyword=...` — the **autosuggest**
   endpoint, which returns a JSON blob with only ~5 HTML items (designed for the live-search
   dropdown, not the search page). It completely ignores the `page` parameter and all filters.
2. The JSON branch hardcodes `hasNextPage = false`, so even those 5 results can't be paginated.

### Step 2 — Verify against the live site (per rule §1)

```bash
# TEST 1: The autosuggest endpoint (current code path)
curl "https://anikototv.to/ajax/anime/search?keyword=naruto"
# → HTTP 500 {"status":500,"result":"Bad request"}
# The autosuggest endpoint is now COMPLETELY BROKEN on the live site.

# TEST 2: The paginated filter endpoint with keyword
curl "https://anikototv.to/filter?keyword=naruto&sort=default&page=1"
# → 110KB HTML, 26 results (div#list-items > div.item), same structure as popular/latest
# Page 2 returns "Error" page (correct — only 26 results exist for "naruto")

# TEST 3: Filter endpoint with empty keyword (browse behavior)
curl "https://anikototv.to/filter?keyword=&sort=default&page=1"
# → 30 items, hasNextPage=True (identical to no-keyword behavior)

# TEST 4: Pagination structure (popular page, many results)
curl "https://anikototv.to/most-viewed?page=1"
# → 30 items, <a class="page-link" rel="next" href="/most-viewed?page=2"> present
# The existing selector `a.page-link[rel=next]` works correctly.

# TEST 5: Search + filters TOGETHER
curl "https://anikototv.to/filter?keyword=naruto&genre[]=1&term_type[]=TV&sort=default&page=1"
# → 4 results (Boruto, Rock Lee spin-off, Naruto Shippuden, Naruto)
# The filter endpoint accepts keyword + all filters in the same request.
```

**Verified:** The `/filter?keyword=<query>&<filters>&page=<page>` endpoint is the correct
unified search/browse path. It returns 30 items/page (not 40 as the old endpoints doc said —
doc was slightly off), respects keyword + filters + sort together, and emits proper
Bootstrap `<a class="page-link" rel="next">` pagination links.

## The fix (v16.26)

### File: `Anikoto.kt`

**Before:**
```kotlin
override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
    if (query.isNotBlank()) {
        GET("$baseUrl/ajax/anime/search?keyword=${URLEncoder.encode(query, "UTF-8")}", xhrHeaders())
    } else {
        GET("$baseUrl/filter?${AnikotoFilters.buildQuery(filters)}&page=$page", headers)
    }

override fun searchAnimeParse(response: Response): AnimesPage {
    val body = response.body.string()
    return if (body.trimStart().startsWith("{")) {
        val data = json.decodeFromString<SearchApiResponse>(body)
        val doc = Jsoup.parse(data.result.html)
        val items = doc.select("a.item").map { parseSearchItem(it) }
        AnimesPage(items, hasNextPage = false)
    } else {
        parseFilterResults(response)
    }
}
```

**After:**
```kotlin
// Always use the paginated /filter endpoint (30 items/page, proper next-page links).
// The /ajax/anime/search autosuggest endpoint returns only ~5 results and is now
// returning HTTP 500 on the live site (verified 2026-06-26). The /filter endpoint
// accepts an optional `keyword` param that works for both empty and non-empty queries,
// AND respects all filters below — so search + filters work together. Verified live.
override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
    GET("$baseUrl/filter?keyword=${URLEncoder.encode(query, "UTF-8")}&${AnikotoFilters.buildQuery(filters)}&page=$page", headers)

override fun searchAnimeParse(response: Response): AnimesPage = parseFilterResults(response)
```

**Also removed** the now-dead `SearchApiResponse` and `SearchResult` DTOs (were only used
by the removed JSON branch).

### File: `AnikotoFilters.kt`

Updated the stale header text:
- **Before:** `"Search uses live autosuggest (ignores filters below)."` + `"Filters apply only when browsing with empty query."`
- **After:** `"Search uses the paginated /filter endpoint —"` + `"query + filters + sort all apply together."`

### File: `build.gradle.kts`
- `extVersionCode`: 25 → 26 (`extVersionId` stays STABLE at 11)

### File: `AnikotoLog.kt`
- `EXTENSION_VERSION`: `"v16.25..."` → `"v16.26 (ext-lib 16, versionId=11 STABLE)"`

## Verification

### Build
```
./gradlew :src:en:anikoto:assembleDebug --no-daemon
> BUILD SUCCESSFUL in 43s
APK: aniyomi-en.anikoto-v16.26-debug.apk (154,536 bytes — slightly smaller than v16.25's
     157,724 due to removed SearchApiResponse DTO + simplified searchAnimeParse)
MD5: 7d6cde2568b981a0fe1c1cce7205fbe1
```

### Build checklist (guides/04) — ALL PASS
| # | Check | Result |
|---|---|---|
| 1 | `extClass = ".Anikoto"` (not doubled) | ✅ |
| 2 | Stubs in `:stubs` module, `compileOnly` | ✅ |
| 3 | `versionCode=26`, `versionId=11` STABLE | ✅ |
| 4 | Manifest placeholders correct | ✅ |
| 5 | `settings.gradle.kts` includes `:stubs` + `:src:en:anikoto` | ✅ |
| 6 | APK badging: `versionCode=26 versionName=16.26` | ✅ |
| 7 | extClass NOT doubled in manifest | ✅ `.Anikoto` |
| 8 | "Stub!" count = 0 | ✅ 0 |
| 9 | Anikoto class in DEX | ✅ 450 refs |
| 10 | 5 icon densities | ✅ (carried over) |
| 11 | APK copied to both `WORKSPACE/APK/` + `WORKSPACE/DEV/ANIKOTO/APK/` | ✅ |

### DEX diff (v16.25 → v16.26)
- ✅ `/ajax/anime/search` GONE from DEX (old broken endpoint removed)
- ✅ `/filter?keyword=` PRESENT in DEX (new unified search endpoint)
- ✅ `SearchApiResponse` GONE from DEX (dead DTO removed)
- ✅ New filter header text present: `"Search uses the paginated /filter endpoint —"` +
  `"query + filters + sort all apply together."`

### Live behavior simulation (exact requests the extension now makes)
| Scenario | Result |
|---|---|
| Search 'naruto' page 1 | **26 results** (was 5 before), hasNextPage=False (correct — only 26 exist) |
| Search 'naruto' page 2 | 0 results, page title="Error" (correct — site returns error for non-existent pages, but extension won't request it since hasNextPage=False) |
| Browse (empty query) + sort=score page 1 | 30 results, hasNextPage=True |
| Search 'naruto' + genre=Action + type=TV | **4 results** (Boruto, Rock Lee spin-off, Naruto Shippuden, Naruto) — **filters now work with search!** (were ignored before) |

## What changed for the user

| Before (v16.25) | After (v16.26) |
|---|---|
| Search 'naruto' → 5 results | Search 'naruto' → **26 results** |
| Search ignored all filters (genre, type, status, etc.) | **Search + filters work together** (e.g., 'naruto' + Action + TV = 4 results) |
| Search had no pagination (hardcoded `hasNextPage = false`) | **Search paginates** via the standard `a.page-link[rel=next]` selector |
| Used the `/ajax/anime/search` autosuggest endpoint (now returns HTTP 500) | Uses the **`/filter?keyword=...` paginated endpoint** (same as browse) |

## What did NOT change

- `versionId` stays at 11 STABLE (saved anime preserved across update)
- Popular / Latest / filter-browse behavior unchanged (already used `/filter`)
- `parseSearchItem` selector unchanged (same `div#list-items > div.item` structure works for both browse and search)
- All other extension behavior (episodes, hosters, video pipeline, metadata) untouched

## Files changed

| File | Change |
|---|---|
| `Anikoto.kt` | `searchAnimeRequest` → always `/filter?keyword=&<filters>&page=`; `searchAnimeParse` → delegates to `parseFilterResults`; removed dead `SearchApiResponse` + `SearchResult` DTOs |
| `AnikotoFilters.kt` | Updated 2 stale header lines |
| `build.gradle.kts` | `extVersionCode` 25 → 26 |
| `AnikotoLog.kt` | `EXTENSION_VERSION` → `v16.26` |

## Status

- ✅ Build successful (v16.26, 154KB APK)
- ✅ All 11 checklist items pass
- ✅ DEX verified: old endpoint gone, new endpoint present, dead DTO removed
- ✅ Live-site simulation: search returns 26 results (was 5), filters work with search, pagination detected correctly
- ⏳ User to test on device: search for an anime, verify >5 results, verify filters apply, verify "Load more" / pagination works
