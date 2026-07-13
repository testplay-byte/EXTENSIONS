# Module: Video Pipeline

> Last updated: 2027-06-27 (session 51) · Status: VERIFIED
> Covers: server discovery, stream extraction, local proxy, WebView fallback, and video output.

---

## Overview

The video pipeline is the core of the extension — it resolves an episode into playable video streams. The pipeline has 4 stages:

1. **Server Discovery** — find available servers (primary + mapper)
2. **Stream Resolution** — resolve each server to m3u8 variants
3. **Proxy Setup** — start local proxy to serve rewritten playlists
4. **Video Output** — build `Video`/`Hoster` objects for the player

---

## Architecture

```
getHosterList(episode)
    │
    ├── Decode EpisodeMeta from episode.url
    │
    ├── DISCOVERY (parallel)
    │   ├── PATH A: GET /ajax/server/list?servers=<dataIds>
    │   │   → Parse HTML → list of (server, audioType, linkId) tasks
    │   │
    │   └── PATH B: GET mapper.nekostream.site/api/mal/<malId>/<ep>/<ts>
    │       → Parse JSON → Kiwi-Stream tasks (gated by enableKiwi setting)
    │
    ├── RESOLUTION (parallel — coroutineScope + async)
    │   └── For each HosterTask:
    │       ├── GET /ajax/server?get=<linkId> → iframe URL
    │       └── Dispatch by host:
    │           ├── vidtube.site / megaplay.buzz / vidwish.live → resolveVidTube()
    │           └── mewcdn.online → resolveKiwi()
    │
    ├── PROXY SETUP
    │   └── LocalProxyServer(proxyFetchClient, segHeaders, webViewFetcher)
    │       .setPlaylist(Playlist(resolvedStreams))
    │       .start() → proxyBaseUrl
    │
    └── VIDEO OUTPUT
        └── For each stream × variant:
            Video(proxyUrl, title, resolution, ..., initialized=false)
            Grouped by serverName → List<Hoster>
            Sorted by preferredAudio → preferredQuality → resolution
```

---

## Files

### `Anikoto.kt` — Pipeline orchestrator

Key methods:
- `getHosterList(episode)` — Main entry point. Discovers servers, resolves streams, starts proxy.
- `resolveStreamForTask(task, slug)` — Resolves a single HosterTask to an AudioStream.
- `resolveVideo(video)` — Called on quality switch. Cancels prefetch, returns video unchanged.
- `getVideoList(episode)` — Legacy override for fork compatibility. Delegates to getHosterList + flattens.
- `sortVideosInternal(videos, markPreferred)` — Sorts by preferred audio → quality → resolution.

### `AnikotoExtractors.kt` — Stream extractors

Two extraction flows:

#### Flow A: `resolveVidTube(iframeUrl, audioType, hosterName)`
For VidPlay-1, HD-1, Vidstream-2, VidCloud-1.
1. GET iframe page → extract `data-id`
2. GET `https://<host>/stream/getSources?id=<dataId>&type=<audioType>` → master m3u8 URL + tracks
3. Parse master m3u8 → variants (quality, bandwidth, resolution)
4. For each variant: GET media playlist → parse segments (NO ad filtering)
5. Build AudioStream with per-stream Referer

#### Flow B: `resolveKiwi(iframeUrl, audioType, hosterName)`
For Kiwi-Stream.
1. Decode base64 fragment from iframe URL → direct m3u8 URL
2. Parse master m3u8 → variants
3. For each variant: GET media playlist → parse segments (no filtering)
4. Build AudioStream (H-SUB / A-DUB labels, vibeplayer.site Referer)

### `LocalProxyServer.kt` — Local HTTP proxy

- Raw `java.net.ServerSocket` on `127.0.0.1:0` (OS-assigned port)
- URL scheme:
  - `/variant/{streamIndex}/{quality}.m3u8` → build-from-scratch media playlist
  - `/seg/{streamIndex}/{quality}/{index}` → fetch upstream, strip PNG header, serve
  - `/sub/{streamIndex}/{subIndex}` → subtitle passthrough with proper headers
- LRU cache: 200 entries
- Prefetch: configurable % (default 10%), max 5 concurrent
- Idle timeout: 10 minutes
- Per-stream headers: each stream carries its own Referer (from iframe host)

### `WebViewFetcher.kt` — Chrome TLS bypass

Used for WAF-blocked CDNs where OkHttp's Conscrypt TLS is blocked.
- Creates a WebView, loads an origin page (megaplay.buzz)
- Executes JavaScript `fetch()` calls via `evaluateJavascript()`
- Methods: `fetchText(url)`, `fetchBytes(url)`, `postJson(url, body)`
- Chunked byte transfer: 700KB → ~933KB base64 (under 1MB IPC limit)
- Serialized fetches (no concurrent evaluateJavascript)
- 30s text timeout, 60s bytes timeout
- ★ session 51: `warmUp()` method for pre-warming during episode list fetch (hides 2–30s cold start from click-to-play)

### `Models.kt` — Data types

- `HosterTask` — one unit of work (server × audio combo)
- `AudioStream` — resolved stream with variants + subtitles + Referer
- `VariantData` — one HLS variant (quality + segments)
- `SegmentInfo` — one segment URL + duration
- `SubtitleData` — one subtitle track
- `Playlist` — in-memory playlist loaded into LocalProxyServer

---

## Supported Servers

| Server | Host | Flow | Method | Notes |
|--------|------|------|--------|-------|
| VidPlay-1 | vidtube.site | A | OkHttp | Primary server |
| HD-1 | megaplay.buzz | A | OkHttp → WebView fallback | CDN may be WAF-blocked |
| Vidstream-2 | megaplay.buzz | A | OkHttp → WebView fallback | Same as HD-1, different data-id |
| VidCloud-1 | vidwish.live | A | OkHttp | Per-stream Referer required |
| Kiwi-Stream | mewcdn.online | B | OkHttp | Toggleable via settings |

---

## Audio Types

| Site label | Audio type param | Display label | Available on |
|-----------|-----------------|---------------|-------------|
| Sub | `sub` | SUB | All servers |
| Hardsub | `hsub` | HSUB | Primary servers |
| Dub | `dub` | DUB | All servers |
| Kiwi sub | `sub` | H-SUB | Kiwi-Stream |
| Kiwi dub | `dub` | A-DUB | Kiwi-Stream |

---

## WAF-Blocked Hosts

These CDN hosts block OkHttp's TLS and require WebViewFetcher:
- `mewstream.buzz` (Vidstream-2/HD-1 CDN)
- `voltara.click` (segment CDN)
- `zaptrix.buzz` (segment CDN)

The code checks `isWafBlockedHost(url)` in both `AnikotoExtractors.fetchString()` and `LocalProxyServer.fetchSegment()`.

---

## PNG Stripping

Some CDN responses have a PNG image header prepended to the TS segment data. The proxy strips this:
1. Check for PNG magic bytes (89 50 4E 47)
2. Find IEND chunk
3. Scan for TS sync byte (0x47) at 188-byte intervals
4. Return data from the first TS sync byte onward

---

## Fork Compatibility

- `getVideoList(episode)` override delegates to `getHosterList()` + flattens
- Episode URL format: `/watch/<slug>/ep-<epNum>#<fragment>` (valid URL path, metadata in fragment)
- `getEpisodeUrl(episode)` constructs the correct watch URL from EpisodeMeta

---

## How to Modify

| Change | Where | Risk |
|--------|-------|------|
| Add a new server | `resolveStreamForTask()` + new extractor method | MEDIUM |
| Change quality sorting | `sortVideosInternal()` | LOW |
| Adjust proxy cache size | `LocalProxyServer.MAX_CACHE_ENTRIES` | LOW |
| Add WAF host | `isWafBlockedHost()` in both extractors + proxy | LOW |
| Change prefetch behavior | `LocalProxyServer.triggerPrefetch()` | MEDIUM |
| Disable ad filtering | Already done (filterAds=false everywhere) | N/A |

---

## See Also

- **Episode URL format**: `EXTENSIONS/anikoto/MEMORY/modules/02-anime-details-episodes.md`

---

## ★ Session 51: Performance Optimizations

Three changes to reduce first-video-play time:

### 1. WebView Pre-warming
- **Where**: `WebViewFetcher.warmUp()` called from `Anikoto.getEpisodeList()`
- **What**: Starts WebView initialization on a background thread when the user opens an anime's detail page
- **Impact**: Hides 2–30 second cold start from click-to-play
- **Risk**: None — `warmUp()` is non-blocking and idempotent; if it fails, `ensureWebView()` retries on first use

### 2. Parallel Variant Fetching
- **Where**: `AnikotoExtractors.resolveVidTube()` and `resolveKiwi()`
- **What**: Changed sequential `for` loop to `coroutineScope { map { async }.awaitAll() }` for variant m3u8 playlists
- **Impact**: Reduces 4×300ms = 1.2s to ~300ms (bounded by slowest variant)
- **Risk**: Low — slightly higher concurrent connections; each variant's errors are isolated via try-catch

### 3. Parallel PATH A + PATH B
- **Where**: `Anikoto.getHosterList()`
- **What**: Server list (/ajax/server/list) and mapper API now run concurrently via `coroutineScope { async { ... }; async { ... } }`
- **Impact**: Saves ~200–500ms (the time of whichever finishes first)
- **Risk**: None — they're independent API calls; failures in one don't affect the other
