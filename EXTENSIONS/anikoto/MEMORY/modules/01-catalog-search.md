# Module: Catalog & Search

> Last updated: 2027-06-27 (session 51) · Status: VERIFIED
> Covers: popular, latest, search, filters, and catalog parsing.

---

## Overview

The catalog module handles three browsing modes: **Popular** (most-viewed), **Latest** (recently updated), and **Search** (keyword + filters). All three use the same HTML parser (`parseFilterResults`) but hit different endpoints.

---

## Endpoints

| Mode | Endpoint | Pagination |
|------|----------|------------|
| Popular | `GET /most-viewed?page=N` | 30 items/page |
| Latest | `GET /latest-updated?page=N` | 30 items/page |
| Search | `GET /filter?keyword=<q>&<filters>&page=N` | 30 items/page |

### Search endpoint details (★ session 42)

- The `/filter` endpoint accepts an optional `keyword` param — works for both empty and non-empty queries.
- Search + filters work together on the same request.
- The old `/ajax/anime/search` autosuggest endpoint is **broken** (HTTP 500) — never use it.
- Pagination uses `<a class="page-link" rel="next">` to detect next page.

---

## Files

### `Anikoto.kt` — Catalog methods

```kotlin
// Popular
override fun popularAnimeRequest(page: Int): Request
override fun popularAnimeParse(response: Response): AnimesPage

// Latest
override fun latestUpdatesRequest(page: Int): Request
override fun latestUpdatesParse(response: Response): AnimesPage

// Search
override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request
override fun searchAnimeParse(response: Response): AnimesPage

// Shared parser
private fun parseFilterResults(response: Response): AnimesPage
private fun parseSearchItem(el: Element): SAnime
```

### `AnikotoFilters.kt` — Filter definitions

- `AnikotoFilters.get()` → `AnimeFilterList` with 9 filters
- `AnikotoFilters.buildQuery(filters)` → URL query string

---

## Filter List

| Filter | Type | Values | Query Param |
|--------|------|--------|-------------|
| Genre | CheckBox group | 43 genres (Action → Vampire) | `genre[]` |
| Type | CheckBox group | TV, TV Short, Movie, ONA, OVA, Special, Music | `term_type[]` |
| Status | CheckBox group | Currently Airing, Finished Airing, Not Yet Aired | `status[]` |
| Language | CheckBox group | Sub, Dub | `language[]` |
| Season | CheckBox group | Spring, Summer, Fall, Winter | `season[]` |
| Year | CheckBox group (multi-select) | 2026 down to 1980 | `year[]` |
| Rating | CheckBox group | G, PG, PG-13, R, R+, Rx | `rating[]` |
| Source | CheckBox group | Manga, Original, Light Novel, Web Novel, etc. | `source[]` |
| Sort | Select dropdown | default, latest-updated, latest-added, score, name-az, release-date, most-viewed, number_of_episodes | `sort` |

### ★ Session 51: Major filter fix

All genre values were previously wrong (mapping to incorrect IDs). Re-verified against the live website's `<input name="genre[]" value="...">` form. Also fixed:
- Sort values now use slug format (`latest-updated` instead of `Latest Updated`)
- Year changed from single-select dropdown to multi-select checkboxes
- Added TV Short to type filter
- Added Source filter (new on website)
- Removed unnecessary header text

---

## Parsing Logic

### `parseFilterResults(response)`

1. Parse the response as JSoup document
2. Select `div#list-items > div.item` for each anime entry
3. Call `parseSearchItem()` on each element
4. Detect next page: `a.page-link[rel=next]` exists

### `parseSearchItem(el)`

Extracts from each item element:
- **URL**: From the link's `href`, normalized to just the slug (remove `/watch/` prefix and `/ep-N` suffix)
- **Title**: From `.name.d-title` link text
- **Thumbnail**: From `img` element's `abs:src`

The parser is flexible — handles both Popular/Latest layout (img in sibling div) and Search layout (img inside link).

---

## How to Modify

| Change | Where | Notes |
|--------|-------|-------|
| Add a filter | `AnikotoFilters.kt` | Add a new Filter class + update `buildQuery()` |
| Change items per page | N/A | Server-controlled (30) |
| Fix a parsing issue | `parseSearchItem()` in `Anikoto.kt` | Check CSS selectors against live site |
| Change search endpoint | `searchAnimeRequest()` | Currently uses `/filter?keyword=` |
| Add a new browsing mode | Add new `*Request`/`*Parse` overrides | Follow existing pattern |

---

## Testing

To verify catalog changes:
1. Build debug APK
2. In Aniyomi: browse Popular → verify items load with titles + thumbnails
3. Browse Latest → verify items load
4. Search for a keyword → verify results appear
5. Apply filters (e.g., genre + status) → verify they narrow results
6. Paginate → verify next page loads

---

## See Also

- **Filters reference**: `AnikotoFilters.kt` source code
- **Search fix (session 42)**: `EXTENSIONS/anikoto/MEMORY/session-logs/2026-06-26_session-42_*.md`
