# Architecture — AniKoto 180 Extension

> Last updated: 2027-06-27 (session 51) · Status: VERIFIED
> This document is the definitive architecture reference for the AniKoto 180 Aniyomi extension.

---

## Overview

AniKoto 180 is an Aniyomi anime extension (ext-lib 16 era) that scrapes anikototv.to. It's a single APK module that plugs into the Aniyomi/Animiru app and provides: catalog browsing, search, anime details, episode lists, video extraction, and episode metadata enrichment.

The extension is **self-sustained** — no backend server required. All logic runs on-device.

---

## Module Map

```
eu.kanade.tachiyomi.animeextension.en.anikoto/
│
├── Anikoto.kt              ★ MAIN SOURCE CLASS — orchestrates everything
│   Extends AnimeHttpSource + ConfigurableAnimeSource.
│   Implements: popular, latest, search (with smart search override), details, episodes, video pipeline.
│
├── AnikotoSettings.kt      ★ SETTINGS — all preferences + UI
│   Keys, defaults, typed getters, setupPreferenceScreen().
│   4 categories: Playback, Servers, Episode metadata, Smart Search.
│   Completely independent from Anikoto.kt.
│
├── AnikotoFilters.kt       ★ CATALOG FILTERS — genre, type, status, etc.
│   Filter definitions + query builder for /filter endpoint.
│   Independent — only used by searchAnimeRequest().
│
├── AnikotoDto.kt           ★ DTOs — serializable data transfer objects
│   All @Serializable classes for the Anikoto site API.
│   + parseMapperResponse() for Kiwi-Stream.
│
├── AnikotoLog.kt           ★ LOGGING — logcat-only (tag "Anikoto")
│   i(), d(), w(), e(), trunc() — all delegate to android.util.Log.
│   No file I/O, no permissions.
│
├── AnikotoRC4.kt           ★ RC4 ENCRYPTION — vrf parameter encoding
│   encodeVrf() for /ajax/episode/list/{animeId}?vrf=...
│   Server doesn't validate but we implement for safety.
│
├── EpisodeMeta.kt          ★ EPISODE URL ENCODING — metadata in SEpisode.url
│   Encodes: slug, epNum, malId, timestamp, dataIds, hasSub, hasDub, epTitle
│   Format: /watch/<slug>/ep-<epNum>#<fragment>
│   Backward-compatible decode() for old pipe-delimited format.
│
├── metadata/
│   └── EpisodeMetadataFetcher.kt  ★ EPISODE METADATA — multi-source enrichment
│       Sources: Jikan → AniList → Anikage → Kitsu
│       Provides: thumbnails, titles, descriptions for episodes.
│       Completely isolated — never throws, returns empty map on error.
│
├── smartsearch/
│   └── SmartSearch.kt             ★ SMART SEARCH — AI-powered search (session 51)
│       Resolves descriptive queries / misspellings to anime titles via Google AI Search.
│       Self-contained module — easily removable.
│       shouldTrigger(), stripPhrase(), resolve(), extractAnimeTitle(), cache.
│
└── video/
    ├── AnikotoExtractors.kt      ★ STREAM EXTRACTORS — resolve iframe → m3u8
    │   Flow A: resolveVidTube() — VidPlay-1, HD-1, Vidstream-2, VidCloud-1
    │   Flow B: resolveKiwi() — Kiwi-Stream (base64 fragment)
    │
    ├── LocalProxyServer.kt       ★ LOCAL PROXY — serves rewritten m3u8 + segments
    │   Port: OS-assigned (127.0.0.1:0)
    │   Features: PNG stripping, LRU cache (200), prefetch, idle timeout
    │
    ├── WebViewFetcher.kt         ★ WEBVIEW FETCHER — Chrome TLS bypass for WAF + Google AI
    │   Uses Android WebView (BoringSSL) to bypass Cloudflare WAF.
    │   fetchText(), fetchBytes(), postJson() — all via JS fetch() in WebView.
    │   warmUp() — pre-warms video WebView during episode list fetch.
    │   warmUpGoogleWebView() + fetchRenderedText() — separate Google WebView for smart search.
    │
    └── Models.kt                 ★ DATA MODELS — in-memory video pipeline types
        HosterTask, AudioStream, VariantData, SegmentInfo, SubtitleData, Playlist
```

---

## Data Flow

### 1. Catalog → Search → Details → Episodes → Video

```
User taps "Popular" / "Latest" / searches
         │
         ▼
  popularAnimeRequest() / latestUpdatesRequest() / searchAnimeRequest()
         │
         ▼
  GET /most-viewed?page=N  (popular)
  GET /latest-updated?page=N  (latest)
  GET /filter?keyword=...&genre[]=...&page=N  (search)
         │
         ▼
  parseFilterResults() → AnimesPage(List<SAnime>, hasNext)
         │
         ▼
  User taps an anime
         │
         ▼
  animeDetailsRequest() → GET /watch/<slug>/ep-1
         │
         ▼
  animeDetailsParse() → SAnime (title, description, genres, status, ...)
         │
         ▼
  getEpisodeList() → GET /ajax/episode/list/<animeId>?vrf=...
         │  ★ session 51: also calls webViewFetcher.warmUp() (pre-warms WebView for video playback)
         ▼
  episodeListParse() → List<SEpisode> (with EpisodeMeta encoded in .url)
         │  ┌──────────────────────────────┐
         └──│ enrichEpisodesWithMetadata() │ (optional, toggleable)
            │ Jikan + AniList + Anikage +  │
            │ Kitsu → thumbnails/titles/   │
            │ descriptions                  │
            └──────────────────────────────┘
         │
         ▼
  User taps an episode
         │
         ▼
  getHosterList() ──┬── PATH A: /ajax/server/list (VidPlay, HD, Vidstream, VidCloud)
                   └── PATH B: mapper.nekostream.site (Kiwi-Stream, if enabled)
         │  ★ session 51: PATH A and PATH B now run in PARALLEL (coroutineScope + async)
         ▼
  resolveStreamForTask() × N (parallel)
    ├── /ajax/server?get=<linkId> → iframe URL
    └── dispatch by host:
        ├── VidTube hosts → resolveVidTube() (getSources → m3u8 → variants)
        │   ★ session 51: variant playlists fetched in PARALLEL (async/awaitAll)
        └── mewcdn.online → resolveKiwi() (base64 decode → m3u8 → variants)
            ★ session 51: variant playlists fetched in PARALLEL (async/awaitAll)
         │
         ▼
  LocalProxyServer.start() → serves rewritten playlists + stripped segments
         │
         ▼
  Build Video objects (per-server Hosters) → return List<Hoster>
         │
         ▼
  mpv player loads proxied m3u8 → plays video
```

### 2. Settings Flow

```
User opens extension settings
         │
         ▼
  setupPreferenceScreen() → delegates to AnikotoSettings.setupPreferenceScreen()
         │
         ▼
  3 categories displayed:
    Playback: quality, audio, buffer, server
    Servers: Kiwi-Stream toggle
    Episode metadata: thumbnails, titles, descriptions
         │
         ▼
  User changes a setting → saved to SharedPreferences("source_$id")
         │
         ▼
  Next getHosterList() call reads settings via AnikotoSettings typed getters
```

---

## Key Design Decisions

| Decision | Rationale | Session |
|----------|-----------|---------|
| Package `...anikoto180` (not `...anikoto`) | Distinguishes from other publishers | 49 |
| extClass = full path (no leading dot) | Loader would prepend `...anikoto180.Anikoto` with relative | 49 |
| versionId = 11 STABLE | Bumping orphans saved anime (MD5("anikoto 180/en/11")) | 25 |
| Search via `/filter?keyword=` | `/ajax/anime/search` returns HTTP 500 | 42 |
| Episode URL = `/watch/slug/ep-N#fragment` | Prevents DNS errors in legacy-pipeline forks | 43 |
| Legacy `getVideoList(SEpisode)` override | Fork compatibility — delegates to getHosterList + flattens | 43 |
| WebViewFetcher for WAF CDNs | OkHttp's Conscrypt TLS blocked by Cloudflare | 30-31 |
| Per-stream Referer in AudioStream | VidCloud-1 segments 403 with wrong Referer | 27 |
| Logcat-only logging (no file I/O) | No WRITE_EXTERNAL_STORAGE needed | 46 |
| ProGuard keeps `$$serializer` classes | R8 stripped them → "type reference" crash | 47 |
| Settings in separate AnikotoSettings.kt | Settings UI independent from main source class | 50 |
| Pre-warm WebView during episode list fetch | Hides 2–30s cold start from click-to-play | 51 |
| Parallel variant playlist fetching | Reduces N×300ms to ~300ms per task | 51 |
| Parallel PATH A + PATH B server discovery | Saves 200–500ms per play | 51 |
| Filter values verified against live site form | Genre IDs were all wrong; re-verified from `<input>` elements | 51 |

---

## File Sizes (v16.9, session 51 — Build 7)

| File | Lines | Role |
|------|-------|------|
| Anikoto.kt | ~860 | Main source class (orchestrator + smart search wiring) |
| AnikotoSettings.kt | ~260 | Settings (keys + UI, 4 categories incl. Smart Search) |
| AnikotoExtractors.kt | ~375 | Stream extractors (parallel variants) |
| LocalProxyServer.kt | ~479 | Local HTTP proxy |
| WebViewFetcher.kt | ~480 | Chrome TLS fetcher (+ warmUp + Google WebView + fetchRenderedText) |
| EpisodeMetadataFetcher.kt | ~472 | Multi-source metadata |
| AnikotoDto.kt | ~107 | Serializable DTOs |
| AnikotoFilters.kt | ~140 | Catalog filters (43 genres + source) |
| EpisodeMeta.kt | ~134 | Episode URL encoding |
| AnikotoLog.kt | ~51 | Logging utility |
| AnikotoRC4.kt | ~55 | RC4 encryption |
| Models.kt | ~50 | Video pipeline data models |
| SmartSearch.kt | ~220 | Smart Search module (AI-powered search) |
| **Total** | **~3,683** | |

---

## How to Modify Safely

| Want to change... | Edit this file | Risk level |
|-------------------|---------------|------------|
| Add a setting | `AnikotoSettings.kt` | LOW — fully isolated |
| Change a filter | `AnikotoFilters.kt` | LOW — only used by search |
| Add a video server | `AnikotoExtractors.kt` + `Anikoto.kt` | MEDIUM — touches video pipeline |
| Change episode metadata sources | `EpisodeMetadataFetcher.kt` | LOW — isolated, never throws |
| Change proxy behavior | `LocalProxyServer.kt` | MEDIUM — affects video playback |
| Change catalog parsing | `Anikoto.kt` (parseSearchItem, etc.) | MEDIUM — affects all listings |
| Change episode URL format | `EpisodeMeta.kt` + `Anikoto.kt` | HIGH — affects saved episodes |
| Change package name | `build.gradle.kts` + extClass | HIGH — breaks updates |
| Change smart search AI prompt | `SmartSearch.kt` → `buildPrompt()` | LOW — isolated module |
| Remove smart search entirely | Delete `smartsearch/` + remove from `Anikoto.kt` + `AnikotoSettings.kt` | LOW — modular |

---

## See Also

- **Build checklist**: `MEMORY/guides/04-build-checklist.md`
- **Identity & versioning**: `EXTENSIONS/anikoto/MEMORY/sites/getsources-migration-and-id-analysis.md`
- **Fork compatibility**: `EXTENSIONS/anikoto/MEMORY/issues-resolutions/04-episode-url-dns-error-in-forks.md`
- **Module docs**: `EXTENSIONS/anikoto/MEMORY/modules/01-*.md` through `06-*.md`
