# Implementation Plan: Episode Thumbnails & Descriptions (via Kitsu)

> Date: 2026-06-24 · Session #: 33 · Status: PLAN (ready for implementation)

## Overview

Add episode preview thumbnails and descriptions to the episode list, sourced from the
**Kitsu API** (kitsu.app). Both features are toggleable in the extension's settings screen.
This module is **completely separate** from the existing extension logic — it only enriches
`SEpisode` objects after the existing episode list is built. If it fails, the episodes still
load normally (just without thumbnails/descriptions).

## Data source: Kitsu API

### API flow (3 requests, all optional)

```
1. MAL → Kitsu mapping:  GET https://kitsu.app/api/edge/mappings
                          ?filter[externalSite]=myanimelist/anime
                          &filter[externalId]={malId}
                          &include=item
   → returns Kitsu anime ID + poster (cover) + banner (coverImage)
   (1 request per anime, cached)

2. Kitsu episodes:        GET https://kitsu.app/api/edge/anime/{kitsuId}/episodes
                          ?page[limit]=20&sort=number
   → returns per-episode: number, canonicalTitle, description, thumbnail{original}, airdate
   (1 request per anime, cached; paginated for >20 episodes)

3. (fallback) anime cover image used as thumbnail when episode thumbnail is missing
```

### Verified behavior (tested 2026-06-24)

| Anime | MAL ID | Kitsu mapping? | Episode thumbnails? | Episode descriptions? |
|-------|--------|:-:|:-:|:-:|
| Attack on Titan | 16498 | ✅ | ✅ (all) | ✅ (all) |
| Demon Slayer | 38000 | ✅ | ✅ | ✅ |
| Wistoria S2 | 59983 | ✅ | ❌ (empty) | ❌ (empty) |
| Smoking Behind Supermarket | 62076 | ❌ (not in Kitsu) | — | — |

- **Thumbnails**: `https://media.kitsu.app/episodes/thumbnails/{id}/original.jpg` — HTTP 200,
  ~75-80KB each, accessible without auth
- **Rate limits**: generous — 10 rapid requests all returned 200 (no 429). No auth required.
- **Pagination**: max 20 episodes per request. `links.next` for more.
- **Fallback**: when episode thumbnail is missing OR Kitsu has no mapping, use the anime's
  poster image (or banner) as the thumbnail — per user's instruction.

### Kitsu response shapes

**Mapping response** (`/mappings`):
```json
{
  "data": [{"id": "...", "relationships": {"item": {"data": {"type": "anime", "id": "7442"}}}}],
  "included": [{
    "id": "7442", "type": "anime",
    "attributes": {
      "canonicalTitle": "Attack on Titan",
      "posterImage": {"original": "https://media.kitsu.app/anime/poster_images/7442/original.jpg"},
      "coverImage": {"original": "https://media.kitsu.app/anime/cover_images/7442/original.png"}
    }
  }]
}
```

**Episodes response** (`/anime/{id}/episodes`):
```json
{
  "data": [{
    "id": "104938", "type": "episodes",
    "attributes": {
      "number": 1,
      "canonicalTitle": "To You Two Thousand Years Later",
      "description": "The Fall of Shiganshina, Part 1. After one hundred years...",
      "thumbnail": {"original": "https://media.kitsu.app/episodes/thumbnails/104938/original.jpg"},
      "airdate": "2013-04-06"
    }
  }],
  "meta": {"count": 25},
  "links": {"next": "...", "first": "..."}
}
```

## Architecture (separation from existing logic)

### New file: `EpisodeMetadataFetcher.kt`
Location: `.../anikoto/metadata/EpisodeMetadataFetcher.kt`

A self-contained class that:
- Takes a MAL ID + anime poster URL
- Returns a `Map<Int, EpisodeMetadata>` (episode number → metadata)
- Handles ALL Kitsu API communication, caching, error handling internally
- NEVER throws — on any error, returns an empty map (episodes still load without enrichment)
- Uses its own OkHttpClient (simple, no CloudflareInterceptor needed — Kitsu has no WAF)

```kotlin
package eu.kanade.tachiyomi.animeextension.en.anikoto.metadata

data class EpisodeMetadata(
    val title: String?,         // Kitsu canonicalTitle (may enrich episode name)
    val description: String?,   // Kitsu description (may be null/empty)
    val thumbnailUrl: String?,  // Kitsu episode thumbnail (may be null)
    val airdate: String?,       // Kitsu airdate (ISO date)
)

class EpisodeMetadataFetcher(
    private val client: OkHttpClient,   // simple client, no WAF needed for Kitsu
    private val json: Json,
) {
    // In-memory cache: malId → (kitsuId, posterUrl, bannerUrl, episodes map)
    private val cache = mutableMapOf<String, KitsuAnimeData>()
    
    suspend fun fetch(malId: String, fallbackThumbnailUrl: String?): Map<Int, EpisodeMetadata>
    // ↑ called from getEpisodeList, enriches SEpisodes
}
```

### Integration point: `Anikoto.kt` → `getEpisodeList()`

**Current flow (unchanged):**
```
getEpisodeList → episodeListParse → List<SEpisode> → return
```

**New flow (additive, non-breaking):**
```
getEpisodeList → episodeListParse → List<SEpisode>
  → if (prefEpisodeThumbnails || prefEpisodeDescriptions):
      EpisodeMetadataFetcher.fetch(malId, animePosterUrl)
      → enrich each SEpisode.preview_url + .summary (matched by episode number)
  → return enriched List<SEpisode>
```

The enrichment happens AFTER the episode list is fully built. If the fetcher fails or returns
empty, the episodes are returned as-is (current behavior). **Zero impact on existing logic.**

### New preferences (in setupPreferenceScreen)

```kotlin
// Episode metadata section (new)
SwitchPreference(screen.context).apply {
    key = PREF_EP_THUMBNAILS_KEY
    title = "Episode thumbnails"
    summary = "Show preview images from Kitsu. Falls back to anime cover if unavailable."
    setDefaultValue(true)  // ON by default
}.also(screen::addPreference)

SwitchPreference(screen.context).apply {
    key = PREF_EP_DESCRIPTIONS_KEY
    title = "Episode descriptions"
    summary = "Show episode descriptions from Kitsu. Adds ~1s to episode list loading."
    setDefaultValue(true)  // ON by default
}.also(screen::addPreference)
```

These control whether the fetcher runs at all. The Aniyomi app's built-in "Show episode
previews" / "Show episode summaries" toggles (per-anime, in overflow menu) control display
independently.

## Thumbnail fallback strategy

Per the user's instruction: "if kitsu does not have the thumbnail image of the episode then
we will just show the banner image as the thumbnail."

Priority for `SEpisode.preview_url`:
1. **Kitsu episode thumbnail** (episode-specific) — best
2. **Kitsu anime banner** (`coverImage.original`) — per user's instruction
3. **Kitsu anime poster** (`posterImage.original`) — if no banner
4. **Anikoto anime poster** (the `thumbnail_url` from `SAnime`) — if Kitsu has no mapping
5. **null** (no thumbnail) — if everything fails → app shows simple layout

This ensures episodes always have a thumbnail when possible, even if Kitsu has no episode-
specific image.

## Description strategy

Per the user's instruction: "leave the description empty or have a custom description like
no description."

Priority for `SEpisode.summary`:
1. **Kitsu episode description** (if non-empty) — best
2. **Custom placeholder** — `"No description available."` (only if user has descriptions ON
   but Kitsu has no description for this episode)
3. **null** — if user has descriptions OFF → app shows simple layout

Note: the custom placeholder only appears when the user has explicitly enabled descriptions
(setting = ON) but Kitsu has no data. This matches the user's instruction.

## Caching strategy

- **In-memory cache** (process lifetime): `malId → KitsuAnimeData(kitsuId, poster, banner, episodes map)`
- Prevents re-fetching when the user navigates away and back to the same anime
- Cache is cleared when the extension process restarts (acceptable — re-fetch is fast)
- No persistent cache (disk) — adds complexity, and the API is fast enough

## Error handling (defensive, non-breaking)

The `EpisodeMetadataFetcher.fetch()` method:
- Catches ALL exceptions internally (network, JSON parse, timeout, etc.)
- Logs warnings via `AnikotoLog` but NEVER throws
- Returns an empty map on any failure → episodes load without enrichment
- Uses a short timeout (5-10s) so it doesn't block episode list loading indefinitely
- Runs on `Dispatchers.IO` (already the case for `getEpisodeList`)

## Step-by-step implementation plan

### Step 1: Create the metadata package + DTOs
- New file: `metadata/EpisodeMetadataFetcher.kt`
- Kitsu DTOs (serializable data classes): `KitsuMappingResponse`, `KitsuEpisodesResponse`,
  `KitsuAnimeAttributes`, `KitsuEpisodeAttributes`
- `EpisodeMetadata` data class (title, description, thumbnailUrl, airdate)
- `KitsuAnimeData` internal cache class (kitsuId, posterUrl, bannerUrl, episodes map)

### Step 2: Implement the fetcher logic
- `fetch(malId, fallbackThumbnailUrl)`: Map<Int, EpisodeMetadata>
  1. Check cache → return if present
  2. GET mappings (MAL → Kitsu ID + poster + banner)
  3. GET episodes (paginated, all pages)
  4. Build episode metadata map (number → EpisodeMetadata)
  5. Cache + return
  6. On ANY error: log, return empty map
- Use a dedicated simple OkHttpClient (10s timeouts, no interceptors)

### Step 3: Add preferences
- `PREF_EP_THUMBNAILS_KEY` + `PREF_EP_DESCRIPTIONS_KEY` (SwitchPreference, default true)
- Read in `getEpisodeList` to decide whether to call the fetcher
- Add to `setupPreferenceScreen` in a new "Episode metadata" section

### Step 4: Integrate into getEpisodeList
- After `episodeListParse` returns the list, if either pref is ON:
  - Call `metadataFetcher.fetch(animeMalId, animePosterUrl)`
  - For each SEpisode, look up metadata by episode number
  - Set `preview_url` (if thumbnails pref ON) using the fallback chain
  - Set `summary` (if descriptions pref ON) using the description strategy
- Wrap in try-catch (defensive — never break episode loading)
- Add timing log: `"Episode metadata fetched in Xms"`

### Step 5: Build + verify
- versionCode bump (18 → 19), versionId stays 11 STABLE
- Build checklist (all 11 items)
- Regression: VidCloud-1, Vidstream-2, catalog all still work
- Test: episode list shows thumbnails + descriptions for AoT/Demon Slayer,
  falls back to poster for Wistoria, shows no thumbnails for Smoking (no Kitsu mapping)

## What NOT to change (isolation guarantees)

- `AnikotoExtractors.kt` — unchanged (video extraction)
- `LocalProxyServer.kt` — unchanged (proxy)
- `WebViewFetcher.kt` — unchanged (WAF bypass)
- `getHosterList()` — unchanged (server resolution)
- `resolveVideo()` — unchanged (video playback)
- EpisodeMeta / EpisodeMeta.encode() — unchanged (episode URL encoding)
- The Kitsu fetcher uses its OWN OkHttpClient — does NOT interfere with the inherited `client`
  (which has CloudflareInterceptor) or the WebViewFetcher

## Risks & mitigations

| Risk | Mitigation |
|------|-----------|
| Kitsu API down → episodes don't load | Fetcher catches all errors, returns empty map → episodes load without enrichment |
| Kitsu rate limit (unlikely) | 1 request per anime (cached), rate limit is generous |
| Slow Kitsu response → episode list hangs | 10s timeout on Kitsu client; fetcher runs after episodes are parsed |
| MAL ID not in Kitsu → no data | Falls back to anime poster as thumbnail; description = "No description available." |
| Kitsu episode numbers don't match Anikoto | Match by number; unmatched episodes use fallback thumbnail, no description |
| Memory: caching many anime | Cache is bounded by user browsing; cleared on process restart |

## Performance estimate

- **First episode list load** (per anime): +0.5-1.5s (2 Kitsu API calls: mapping + episodes)
- **Subsequent loads** (cached): +0ms (in-memory cache hit)
- **No Kitsu mapping**: +0.5s (1 failed mapping request, then fallback)
- The enrichment runs AFTER episodes are parsed, so the episode list appears quickly;
  the enrichment is a post-processing step
