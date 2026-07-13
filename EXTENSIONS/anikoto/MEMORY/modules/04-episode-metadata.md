# Module: Episode Metadata Enrichment

> Last updated: 2027-06-27 (session 50) · Status: VERIFIED
> Covers: multi-source episode metadata fetching, merging, and display.

---

## Overview

The episode metadata module enriches episodes with thumbnails, titles, and descriptions from external sources (Jikan, AniList, Anikage, Kitsu). It's **completely isolated** from the rest of the extension — if it fails, episodes load without enrichment (no crash, no broken playback).

---

## Architecture

```
getEpisodeList()
    │
    ├── episodeListParse() → List<SEpisode>
    │
    └── enrichEpisodesWithMetadata()  ★ ISOLATED — try-catch, never throws
         │
         ├── Check: all toggles OFF? → skip entirely
         │
         ├── Extract malId from first episode's EpisodeMeta
         │
         └── EpisodeMetadataFetcher.fetch(malId, fallbackThumbnailUrl)
              │
              ├── [Cached?] → return cached result
              │
              ├── fetchJikanEpisodes(malId)           → titles + air dates
              ├── fetchAniListId(malId)                → AniList ID + banner + streamingEpisodes
              ├── fetchAnikageEpisodes(anilistId)      → thumbnails + titles + descriptions
              └── fetchKitsuEpisodes(malId)            → fallback for all fields
              │
              ├── mergeEpisodes() — priority merge
              │   Thumbnail: Anikage → AniList → Kitsu
              │   Title:     Jikan → Anikage → Kitsu
              │   Synopsis:  Anikage → Kitsu
              │
              └── applyFallbackThumbnail() — banner → anime cover for null thumbnails
```

---

## Files

### `metadata/EpisodeMetadataFetcher.kt`

The fetcher class. Key design:
- **Constructor**: `(client: OkHttpClient, json: Json, webViewFetcher: WebViewFetcher?)`
- **`fetch(malId, fallbackThumbnailUrl)`**: Main entry point. Returns `Map<Int, EpisodeMetadata>`.
- **Caching**: In-memory `Map<String, CachedData>` keyed by MAL ID. Once fetched, returns cached.
- **Never throws**: All errors caught, logged, return empty map.

### `Anikoto.kt` — `enrichEpisodesWithMetadata()`

The bridge between the fetcher and the episode list:
1. Checks if all 3 toggles are OFF → skip entirely (no API calls)
2. Extracts `malId` from the first episode's `EpisodeMeta`
3. Calls `metadataFetcher.fetch(malId, animeCoverUrl)`
4. For each episode, applies only the fields the user has enabled:
   - `loadThumbnails` → sets `ep.preview_url`
   - `loadDescriptions` → sets `ep.summary`
   - `loadTitles` → sets `ep.name` to `"EP N - title"`
5. Wrapped in try-catch — never throws

---

## Sources

### 1. Jikan (MyAnimeList) — Titles + Air Dates
- **Endpoint**: `GET https://api.jikan.moe/v4/anime/{malId}/episodes`
- **Method**: OkHttp (NOT behind Cloudflare)
- **Data**: Episode titles (English) + air dates
- **Priority**: HIGHEST for titles (Jikan → Anikage → Kitsu)

### 2. AniList — ID Lookup + Banner + Streaming Thumbnails
- **Endpoint**: `POST https://graphql.anilist.co` (GraphQL)
- **Method**: WebView `postJson()` (behind Cloudflare, needs Chrome TLS)
- **Data**:
  - AniList ID (used to query Anikage)
  - Banner image URL (fallback thumbnail)
  - `streamingEpisodes` list (Crunchyroll-synced thumbnails)
- **★ Critical**: `@SerialName("Media")` on the `media` field (case-sensitive!)

### 3. Anikage.cc (TheTVDB) — Primary Source
- **Endpoint**: `GET https://anikage.cc/api/media/anime/{anilistId}/episodes`
- **Method**: OkHttp with inherited client (CloudflareInterceptor handles 403)
- **★ NOT WebView**: Anikage.cc has CORS — `fetch()` in WebView fails. The CloudflareInterceptor loads the page as main document (not via JS fetch), so CORS doesn't apply.
- **Data**: Thumbnails, titles, descriptions, air dates
- **Priority**: HIGHEST for thumbnails and descriptions

### 4. Kitsu — Fallback Source
- **Endpoint**: `GET https://kitsu.app/api/edge/anime/{kitsuId}/episodes?page[limit]=20&sort=number`
- **Method**: WebView `fetchText()` (behind Cloudflare, but simple GET works despite no CORS headers)
- **ID Lookup**: `GET https://kitsu.app/api/edge/mappings?filter[externalSite]=myanimelist/anime&filter[externalId]={malId}&include=item`
- **Data**: Thumbnails, titles, descriptions, air dates
- **Pagination**: Up to 10 pages (200 episodes max)
- **Priority**: LOWEST fallback for all fields

---

## Merge Priority

| Field | Priority order |
|-------|---------------|
| Thumbnail | Anikage → AniList streamingEpisodes → Kitsu → banner → anime cover |
| Title | Jikan → Anikage → Kitsu |
| Description | Anikage → Kitsu |
| Air date | Jikan → Anikage → Kitsu |

**Important**: Null/blank values are skipped. If Anikage has no title but Jikan does, Jikan is used.

---

## Settings

| Setting | Key | Default | Effect |
|---------|-----|---------|--------|
| Load episode thumbnails | `pref_load_thumbnails` | ON | Sets `SEpisode.preview_url` |
| Load episode titles | `pref_load_titles` | ON | Sets `SEpisode.name` to `"EP N - title"` |
| Load episode descriptions | `pref_load_descriptions` | ON | Sets `SEpisode.summary` |

If ALL three are OFF, the fetcher is skipped entirely — zero API calls, zero latency.

---

## DTOs (in EpisodeMetadataFetcher.kt)

All DTOs are `private` to the fetcher class:
- `AniListMediaResponse` / `AniListMediaData` / `AniListMedia` / `AniListStreamingEpisode`
- `AnikageEpisode`
- `KitsuMappingResponse` / `MappingData` / `KitsuAnime`
- `KitsuEpisodesResponse` / `KitsuEpisode` / `KitsuEpisodeAttributes` / `KitsuImage` / `KitsuLinks`
- `JikanEpisodesResponse` / `JikanEpisodeData`

`JikanEpisode` is package-private (used by `enrichEpisodesWithMetadata()` for the `aired` field).

---

## How to Modify

| Change | Where | Risk |
|--------|-------|------|
| Add a new source | `EpisodeMetadataFetcher.kt` — add fetch method + update merge | LOW (isolated) |
| Change merge priority | `mergeEpisodes()` | LOW |
| Change a source's HTTP method | `fetchString()` / `postJson()` in fetcher | MEDIUM |
| Add a new toggle | `AnikotoSettings.kt` + `enrichEpisodesWithMetadata()` | LOW |
| Disable a source | Comment out the fetch call in `fetch()` | LOW |

---

## Testing

1. Build debug APK
2. Open an anime with a known MAL ID (e.g., One Piece)
3. Check episode list — titles should show "EP N - Episode Title"
4. Check episode thumbnails — should load from Anikage/Kitsu
5. Check episode descriptions — should show synopsis text
6. Disable all 3 toggles → episodes should load quickly without enrichment
7. Re-enable → enrichment should work again

---

## See Also

- **Architecture**: `EXTENSIONS/anikoto/MEMORY/modules/00-architecture.md`
- **Settings**: `EXTENSIONS/anikoto/MEMORY/modules/05-settings.md`
