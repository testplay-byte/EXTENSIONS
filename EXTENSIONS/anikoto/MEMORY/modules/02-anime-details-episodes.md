# Module: Anime Details & Episode List

> Last updated: 2027-06-27 (session 51) · Status: VERIFIED
> Covers: anime detail page parsing, episode list extraction, EpisodeMeta encoding, and fork compatibility.

---

## Overview

This module handles two related functions:
1. **Anime Details** — Parse the watch page to extract anime metadata (title, synopsis, genres, etc.)
2. **Episode List** — Fetch the episode list via AJAX, parse it, and encode metadata into `SEpisode.url`

The episode URL encoding (`EpisodeMeta`) is critical — it stores server discovery data (MAL ID, timestamps, data-IDs) in the URL so the video pipeline can use it later without re-fetching.

---

## Anime Details

### Endpoint
`GET /watch/<slug>/ep-1`

### Parsing (`animeDetailsParse`)

Extracts from the watch page:
- **URL**: Slug (normalized from the request URL)
- **Title**: `h1.title` in `.binfo`
- **Thumbnail**: `.poster img` src
- **Description**: Built from synopsis + metadata (MAL score, type, premiered, aired, duration, studio, rating, alt titles)
- **Genres**: From `.bmeta div:contains(Genres) span a`
- **Status**: Parsed from "Status" metadata → `ONGOING` / `COMPLETED` / `UNKNOWN`
- **Update strategy**: `ONLY_FETCH_ONCE` if completed, `ALWAYS_UPDATE` if ongoing
- **★ Promo line**: `"Thank the Confused_creature_180"` appended at the end of the description

---

## Episode List

### Endpoint
`GET /ajax/episode/list/{animeId}?vrf={encodedVrf}&style=default`

Where:
- `animeId` = `data-id` from `#watch-main` element on the detail page
- `vrf` = RC4-encrypted, Base64-encoded, URL-encoded version of the animeId
  (Server doesn't validate, but we implement for safety — see `AnikotoRC4.kt`)

### Flow

```
getEpisodeList(anime)
    │
    ├── ★ session 51: webViewFetcher.warmUp() (background — pre-warms WebView for video playback)
    │
    ├── GET /watch/<slug>/ep-1 → extract animeId from #watch-main data-id
    │
    ├── GET /ajax/episode/list/<animeId>?vrf=<vrf>&style=default
    │
    ├── episodeListParse() → parse HTML response
    │   For each <a data-ids> element:
    │   - epNum = data-num
    │   - malId = data-mal
    │   - timestamp = data-timestamp
    │   - dataIds = data-ids
    │   - hasSub = data-sub == "1"
    │   - hasDub = data-dub == "1"
    │   - epTitle = span.d-title text (fallback: parent title attr)
    │   - Encode into EpisodeMeta → SEpisode.url
    │   - scanlator = "Sub / Dub" | "Sub" | "Dub" | "Raw"
    │   - episode_number = epNum.toFloat()
    │   - date_upload = timestamp * 1000
    │
    └── enrichEpisodesWithMetadata()  (see 04-episode-metadata.md)
```

---

## EpisodeMeta Encoding

### Why encode metadata in the URL?

`SEpisode.url` is the only field that persists between the episode list and video pipeline calls. The video pipeline (`getHosterList`) needs the MAL ID, timestamps, and server data-IDs to discover available servers — but it only receives the `SEpisode` object.

### Format (v16.27+, session 43)

```
/watch/<slug>/ep-<epNum>#<malId>|<timestamp>|<dataIds>|<subFlag>|<dubFlag>|<escapedTitle>
```

Example:
```
/watch/one-piece/ep-1#37510|1700000000|abc123,def456|1|1|The Beginning
```

**Key design decisions:**
- The path `/watch/<slug>/ep-<epNum>` is a valid URL — legacy forks that do `GET baseUrl + episode.url` get a real page, not a DNS error
- The fragment (`#...`) is stripped by HTTP clients before sending requests
- The `|` in titles is escaped to `│` (U+2502) to avoid collisions

### Old format (v16.25 and earlier, backward-compatible)

```
<slug>/ep-<epNum>|<malId>|<timestamp>|<dataIds>|<subFlag>|<dubFlag>|<title>
```

The `decode()` method handles both formats for backward compatibility (saved episodes in the user's DB).

### `extractUrlPath()` — Get clean URL path

Extracts just the URL path (no fragment, no metadata) from an encoded episode URL:
- New format: `/watch/slug/ep-N#fragment` → `/watch/slug/ep-N`
- Old format: `slug/ep-N|...` → `/watch/slug/ep-N`
- Used by `getEpisodeUrl()` for "Open in WebView" and deep links

---

## Fork Compatibility

### `getVideoList(episode)` override (session 43)

Forks that haven't adopted ext-lib-16's hoster pipeline call `getVideoList(episode)` instead of `getHosterList(episode)`. The default base class does `GET baseUrl + episode.url` — which fails if `episode.url` contains encoded metadata.

Our override delegates to `getHosterList()` + flattens the result:
```kotlin
override suspend fun getVideoList(episode: SEpisode): List<Video> {
    return try {
        val hosters = getHosterList(episode)
        hosters.flatMap { it.videoList ?: emptyList() }
    } catch (e: Exception) {
        emptyList()
    }
}
```

### `getEpisodeUrl(episode)` override

Returns the correct watch URL for "Open in WebView" and deep links:
```kotlin
override fun getEpisodeUrl(episode: SEpisode): String {
    val path = EpisodeMeta.extractUrlPath(episode.url)
    return "$baseUrl$path"
}
```

---

## Files

| File | Role |
|------|------|
| `Anikoto.kt` | `animeDetailsParse()`, `getEpisodeList()`, `episodeListParse()`, `enrichEpisodesWithMetadata()` |
| `EpisodeMeta.kt` | `encode()`, `decode()`, `extractUrlPath()` |
| `AnikotoRC4.kt` | `encodeVrf()` for the episode list AJAX call |
| `AnikotoDto.kt` | `EpisodeListResponse` DTO |
| `AnikotoLog.kt` | Logging throughout |

---

## How to Modify

| Change | Where | Risk |
|--------|-------|------|
| Add a new detail field | `animeDetailsParse()` | LOW |
| Change episode URL format | `EpisodeMeta.kt` + `decode()` callers | HIGH — affects saved episodes |
| Add episode metadata | `enrichEpisodesWithMetadata()` + `EpisodeMetadataFetcher` | LOW (isolated) |
| Fix scanlator display | `episodeListParse()` | LOW |
| Change promo line | `animeDetailsParse()` — the `append()` call | LOW |

---

## See Also

- **Episode metadata**: `EXTENSIONS/anikoto/MEMORY/modules/04-episode-metadata.md`
- **Video pipeline**: `EXTENSIONS/anikoto/MEMORY/modules/03-video-pipeline.md`
- **Fork compatibility fix**: `EXTENSIONS/anikoto/MEMORY/issues-resolutions/04-episode-url-dns-error-in-forks.md`
