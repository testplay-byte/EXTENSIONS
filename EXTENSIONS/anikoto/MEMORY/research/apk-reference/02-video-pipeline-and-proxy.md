# 02 — Video Pipeline & Local Proxy & Crypto (the Heart)

> Last updated: 2026-06-22 (session 10) · Status: VERIFIED by decompilation (v3 clean + v16.4 cross-check)
> Per project rule §1: cross-check / understanding record. No code copied.

This document covers the **ext-lib 16 Hoster video pipeline** as implemented by both reference
APKs — the most valuable part of the analysis, since video extraction is the open task on our
own extension (end of session 08).

## 1. The end-to-end Hoster flow (v3 + v16.4 identical unless noted)

```
SEpisode.url  (= pipe-delimited EpisodeMeta, see 03-catalog-and-dtos.md §3)
   │
   ▼  getHosterList(episode)
   │
   ├─ EpisodeMeta.decode(episode.url) → {slug, epNum, malId, timestamp, dataIds, hasSub, hasDub, epTitle}
   │
   ├─ [Discovery A — PRIMARY] (both v3 + v16.4)
   │    GET <baseUrl>/ajax/server/list?servers=<dataIds>
   │    headers: ajaxHeaders(slug)  (UA=Mozilla/5.0, Referer=<baseUrl>/watch/<slug>/ep-1, X-Requested-With=XMLHttpRequest)
   │    → ServerListResponse { status:Int, result:String(HTML) }
   │    Jsoup.parse(result) → div.servers > div.type[data-type="sub|dub|hsub"] > li[data-link-id="<token>"]<ServerName>
   │    → for each: HosterTask(label="<SUB|DUB|HSUB> - <ServerName>", token=data-link-id, audioType=data-type, source="primary")
   │
   ├─ [Discovery B — MAPPER] (★ v3 ONLY; v16.4 DROPPED this)
   │    if malId+epNum+timestamp all non-empty:
   │      GET https://mapper.nekostream.site/api/mal/<malId>/<epNum>/<timestamp>
   │      headers: ajaxHeaders(slug)
   │      → JSON: { "status":..., "<ServerName>-": { "sub": {"url":"..."}, "dub": {"url":"..."} }, ... }
   │      parseMapperResponse(jsonObj) → List<MapperStreamToken(serverName, audio="sub|dub", token=url)>
   │      → for each: HosterTask(label="H-SUB - <srv>" or "A-DUB - <srv>", token=url, audioType=audio, source="mapper")
   │
   ├─ [PARALLEL RESOLUTION]  coroutineScope { tasks.map { async { resolveStreamForTask(...) } }.awaitAll() }.filterNotNull()
   │
   │   resolveStreamForTask(label, token, audioType, slug):
   │     GET <baseUrl>/ajax/server?get=<URLEncode(token)>     headers: ajaxHeaders(slug)
   │     → ServerResponse { status, result: { url:<iframeUrl>, skip_data:{intro:[Float], outro:[Float]} } }
   │     if status!=200 or result.url empty → return null
   │     if !result.url.contains("vidtube.site") → return null   ← ★ IMPLICIT non-VidTube skip (this is how VidCloud-1 dies)
   │     return resolveVidTubeStream(iframeUrl, audioType, label)
   │
   │   resolveVidTubeStream(iframeUrl, type, hosterName):   (uses noCloudflareClient — see §2)
   │     1. GET iframeUrl (vidtubePageHeaders) → HTML;  regex data-id="(\d+)" → dataId
   │     2. GET https://vidtube.site/stream/getSourcesNew?id=<dataId>&type=<type> (vidtubeApiHeaders)
   │        → VidTubeSourcesResponse { sources:{file:<masterM3u8Url>}, tracks:[{file,label,kind}] }
   │        build subtitles: for each track with http file + non-empty label → SubtitleData(file, label, lang)
   │          lang inferred from label substring: English→eng, Spanish→spa, French→fra, German→deu,
   │          Portuguese→por, Japanese→jpn, else→und
   │     3. GET masterM3u8Url (Headers: Referer=https://vidtube.site/, UA=Mozilla/5.0) → master playlist text
   │        parse #EXT-X-STREAM-INF: lines → List<VariantInfo(url, bandwidth, quality, resolution)>
   │        (resolution parsed from NAME="1080p" via regex (\d{3,4}))
   │     4. For each VariantInfo: GET variant.url → media playlist text
   │        parseVariantSegments(body, variantUrl) → List<SegmentInfo(url, duration)>
   │        (walks #EXTINF:<dur>,<url> pairs; resolves relative URLs against variantUrl's dir)
   │     5. return AudioStream(audioType=type, audioLabel=SUB|DUB|HSUB, hosterName=label,
   │                           variants=[VariantData(quality, bandwidth, resolution, segments)], subtitles)
   │
   ├─ [PROXY SETUP]
   │    server = LocalProxyServer(noCloudflareClient, Headers.of("Referer","https://vidtube.site/","User-Agent","Mozilla/5.0"))
   │    server.playlist = Playlist(resolvedStreams)
   │    server.prefetchCount = prefetchBuffer pref (10-100, default 10)
   │    server.start()    ← binds 127.0.0.1:0 (OS-assigned port), starts accept+idle threads
   │    Companion.swapProxyServer(server)   ← synchronized; stops previous activeProxyServer
   │
   ├─ [VIDEO BUILDING]
   │    for stream in resolvedStreams:
   │      subs = server.getSubtitleTracks(stream.audioType)   ← List<Track> for mpv sub-add
   │      for variant in stream.variants:
   │        videoUrl = "<server.baseUrl>/variant/<audioType>/<quality>.m3u8"
   │        title    = stream.hosterName + " - " + variant.quality   (e.g. "SUB - VidPlay-1 - 1080p")
   │        allVideos.add(Video(videoUrl, title, resolution, subtitleTracks=subs, initialized=true))
   │            ★ initialized=true → app's HosterLoader will NOT call resolveVideo lazily
   │
   ├─ [SORT + PREFER]
   │    sorted = sortVideos(allVideos)
   │      = sortedByDescending { title.startsWith(prefAudioLabel, true) }   // prefAudio="SUB"|"A-DUB"|"H-SUB" → label "SUB"|"DUB"|"HSUB"
   │           .thenByDescending { title.contains(prefQuality, true) }      // prefQuality="1080"|"720"|"480"|"360"
   │    sorted[0].preferred = true    ← player auto-selects
   │
   └─ [RETURN]  Hoster.toHosterList(sorted)
       (single synthetic NO_HOSTER_LIST Hoster wrapping all videos; getVideoList(hoster) just returns hoster.videoList)

resolveVideo(video):  ← essentially a no-op (videos are pre-resolved)
   activeProxyServer?.onQualitySwitch()   ← cancels in-flight prefetch (bumps prefetchGeneration)
   return video
```

### 1.1 Server handling — the 5 servers (VidPlay-1, HD-1, Vidstream-2, VidCloud-1, Kiwi-Stream)

**There is NO explicit per-server skip or dedup.** All servers are treated uniformly:
- Each `<li data-link-id>` in the primary server-list HTML → one HosterTask per audio type.
- `resolveStreamForTask` calls `/ajax/server?get=<token>` → returns `{result:{url:<iframe>}}`.
- The **only filter**: `iframeUrl.contains("vidtube.site")`. If false → `return null`.

**Practical effect (cross-checked against `EXTENSIONS/anikoto/MEMORY/sites/servers.md`):**
- VidPlay-1, HD-1, Vidstream-2, Kiwi-Stream → their iframes all contain `vidtube.site` → all resolve.
- **VidCloud-1** → iframe points to a non-vidtube host (vidwish.live) → **implicitly filtered out**. ✓ matches our live finding that VidCloud-1 is broken.
- **HD-1 / Vidstream-2 dedup**: NOT performed. Both produce Videos with different `hosterName` prefixes. (Matches our `tokens-and-dedup.md` finding that they share m3u8 — the extension doesn't dedup, leaving duplicate-quality options for the user.)

★ **For our extension**: we can either (a) replicate this implicit filtering (simplest, matches reference), or (b) add explicit dedup by master-m3u8 URL (cleaner quality sheet). The reference does (a).

### 1.2 Audio types — SUB / HSUB / DUB

Three audio types, produced **per server** based on the `data-type` attribute on each `<div class="type">`:

| `data-type` | label prefix | `audioType` passed to VidTube | `audioLabel` in AudioStream |
|---|---|---|---|
| `sub` | `SUB - ` | `sub` | `SUB` |
| `hsub` | `HSUB - ` | `hsub` | `HSUB` (hardsub) |
| `dub` | `DUB - ` | `dub` | `DUB` |

The `type` parameter is passed to `getSourcesNew?id=...&type=<audioType>` so VidTube returns the correct audio mix. Mapper API (v3 only) produces only `sub`/`dub` (no `hsub`), with different label prefixes (`H-SUB - `, `A-DUB - `).

**Video labeling**: final title = `stream.hosterName + " - " + variant.quality` where `hosterName` already includes the audio prefix → e.g. `"SUB - VidPlay-1 - 1080p"`.

`SEpisode.scanlator` is set during episode-list parse (see `03-catalog-and-dtos.md`) to `"Sub"`, `"Dub"`, `"Sub / Dub"`, or `"Raw"` — this is the per-episode availability indicator, NOT the per-video label. ✓ matches project rule §8.

## 2. The two HTTP clients (★ important design point)

| Client | Built from | Used for | Why |
|---|---|---|---|
| `client` (default) | `network.client.newBuilder().addInterceptor(fill-UA-Referer-when-missing).build()` | All `anikoto.cz`/`anikototv.to` requests (catalog, AJAX, mapper) | Inherits Cloudflare interceptor + cookie jar + cache from the app's network client |
| `noCloudflareClient` (lazy) | `OkHttpClient.Builder()` from scratch, 15s/30s/15s/60s timeouts, always sets UA=Mozilla/5.0 + Accept + Accept-Language + Referer=vidtube.site | ALL `vidtube.site` requests (iframe page, getSourcesNew, master m3u8, variant m3u8s) + LocalProxyServer segment fetches | vidtube.site is NOT behind Cloudflare; the main client's CF interceptor would interfere. Needs clean browser-like headers. |

★ **For our extension**: replicate this two-client split. The main client handles the catalog site (Cloudflare-protected); a separate clean client handles the video CDN.

### Headers reference

| Header set | UA | Referer | X-Requested-With | Accept | Used for |
|---|---|---|---|---|---|
| `headersBuilder()` (default) | `Mozilla/5.0` | `<baseUrl>/` | — | — | inherited base |
| `ajaxHeaders(slug)` | (inherited) | `<baseUrl>/watch/<slug>/ep-1` | `XMLHttpRequest` | — | all `/ajax/...` on the catalog site |
| `vidtubePageHeaders()` | `Mozilla/5.0` | `https://vidtube.site/` | — | `text/html,application/xhtml+xml,...` | VidTube iframe HTML page |
| `vidtubeApiHeaders()` | `Mozilla/5.0` | `https://vidtube.site/` | `XMLHttpRequest` | `*/*` | VidTube `getSourcesNew` API |
| segment headers (on LocalProxyServer) | `Mozilla/5.0` | `https://vidtube.site/` | — | — | master m3u8, variant m3u8, segments |

## 3. AnikotoRC4 — the `vrf` parameter crypto

**File**: `AnikotoRC4.java` (57 lines, IDENTICAL in v3 and v16.4)

- **Key**: `"simple-hash"` (11 ASCII bytes: `73 69 6D 70 6C 65 2D 68 61 73 68`) — hardcoded, `const val`.
- **Algorithm**: textbook standard RC4 (KSA + PRGA, `int[256]` S-box to avoid signed-byte issues). No drop bytes, no IV, no S-box modification.
- **Wide-char quirk**: operates on Java `char` (16-bit) per character, but each output char stays in 0–255 (XOR of two bytes). `.getBytes(ISO_8859_1)` maps each char 1:1 to a byte — **using UTF-8 here would corrupt the output**.
- **Public API**:
  - `encodeVrf(animeId: String): String` = `Base64.NO_WRAP( rc4("simple-hash", animeId).getBytes(ISO_8859_1) )`
  - `rc4(key: String, input: String): String` — generic symmetric (can decrypt too).
- **Usage**: called EXACTLY ONCE, in `getEpisodeList`, to compute the `vrf` query param for `/ajax/episode/list/<animeId>?vrf=..<&style=default>`. The result is URL-encoded before appending.
- **NOT used for**: `/ajax/server/list`, `/ajax/server?get=`, mapper API, VidTube API, segment data. Only the episode-list vrf.
- **Cross-check**: `EXTENSIONS/anikoto/MEMORY/sites/endpoints.md` notes the `vrf` param but didn't have the algorithm — ★ now confirmed: RC4(key="simple-hash", animeId) → Base64.NO_WRAP → URLEncode.

★ **For our extension**: reimplement RC4 + this exact key (the server-side validates it). It's ~15 lines of Kotlin.

## 4. MapperStreamToken + parseMapperResponse (v3 only — dropped in v16.4)

**Files**: `MapperStreamToken.java` (88 lines, pure data class) + `AnikotoDtoKt.parseMapperResponse` (in `AnikotoDtoKt.java`)

- `MapperStreamToken(serverName: String, audio: String, token: String)` — NOT `@Serializable`. Just a data carrier.
  - `serverName`: server label with trailing `-` stripped (e.g. from JSON key `"VidPlay-"` → `"VidPlay"`).
  - `audio`: `"sub"` or `"dub"` (lowercase; NOT HSUB).
  - `token`: despite the name, this is a **URL** (the mapper-discovered iframe/embed URL), NOT an opaque token.

- `parseMapperResponse(obj: JsonObject): List<MapperStreamToken>`:
  - Iterates each top-level key in the JSON object.
  - Skips `"status"`.
  - For keys ending in `"-"` (e.g. `"VidPlay-"`, `"HD-"`): strips the `-` → serverName.
  - Looks up `obj[serverName].sub` and `obj[serverName].dub`, extracts `.url` from each (defensively).
  - Emits one `MapperStreamToken` per non-null audio track.

- **Expected JSON shape**:
  ```json
  {
    "status": "...",
    "VidPlay-": { "sub": { "url": "https://..." }, "dub": { "url": "https://..." } },
    "HD-":      { "sub": { "url": "https://..." } }
  }
  ```

- **Why v16.4 dropped it**: unknown. Possibly the mapper.nekostream.site service became unreliable, or the author chose to simplify (the primary `/ajax/server/list` endpoint already covers all 5 servers). ★ **For our extension**: skip the mapper entirely (match v16.4). It's a third-party dependency we don't need.

## 5. LocalProxyServer — the PNG-stripping + prefetch local HTTP server

**File**: `LocalProxyServer.java` (1236 lines in v3, 1235 in v16.4 — near-identical)

### 5.1 What it is
- **Raw `java.net.ServerSocket` HTTP/1.1 server** (NOT NanoHTTPD, NOT OkHttp MockWebServer). Hand-rolled request-line parser + response serializers.
- **Bind**: `127.0.0.1` only (loopback, never exposed).
- **Port**: **dynamic, OS-assigned** (`new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))`). Read via `getPort()` after `start()`, surfaced as `getBaseUrl()` → `"http://127.0.0.1:" + port`.
- **3 daemon threads**: `AnikotoProxy-Accept` (accept loop, SO_TIMEOUT=120s per socket), `AnikotoProxy-Worker` (cached thread pool, one short-lived worker per request), `AnikotoProxy-IdleMonitor` (5s sleep, auto-stops after 600s idle).
- **Constants**: `IDLE_TIMEOUT_MS=600_000` (10 min), `MAX_CACHE_ENTRIES=50`, `SOCKET_READ_TIMEOUT_MS=120_000`.

### 5.2 The URL scheme (★ index-based, NOT URL-rewriting)

| Route | URL format | Serves |
|---|---|---|
| Variant playlist | `http://127.0.0.1:PORT/variant/{audioType}/{quality}.m3u8` | A media playlist (m3u8 text) |
| Segment | `http://127.0.0.1:PORT/seg/{audioType}/{quality}/{index}` | A raw `video/MP2T` segment (PNG-stripped) |
| Subtitle | `http://127.0.0.1:PORT/sub/{audioType}/{subIndex}` | A `text/vtt` subtitle |

**Critical design choice**: the proxy is **session-bound and index-addressed**, NOT URL-rewriting. The extension pre-loads an in-memory `Playlist` object (via `setPlaylist()`) containing fully-parsed `AudioStream` → `VariantData` → `SegmentInfo` structures. The proxy never fetches/parses an upstream m3u8 — it **builds one from scratch** from its in-memory state.

**Benefits over URL-rewriting proxies** (like yuzono's `lib/m3u8server`):
1. Upstream URLs never leak to the player.
2. Ad filtering is trivial — just don't insert ad `SegmentInfo` entries (filtering happens upstream in `parseVariantSegments`).
3. Prefetching is trivial — walk the in-memory segments list by index.

**Drawback**: stateful coupling — the proxy must be told the playlist via `setPlaylist()` before serving.

### 5.3 The m3u8 generation (build-from-scratch)

`serveVariantPlaylist(audioType, quality, output)` looks up the `AudioStream` by `audioType`, then `VariantData` by `quality`, then emits a fresh m3u8:
```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:<max(segment.durations)+1, int-truncated>
#EXT-X-MEDIA-SEQUENCE:0
#EXTINF:<dur>,
http://127.0.0.1:PORT/seg/{audioType}/{quality}/0
#EXTINF:<dur>,
http://127.0.0.1:PORT/seg/{audioType}/{quality}/1
...
#EXT-X-ENDLIST
```
Each segment URL is a **positional index** pointing back at the proxy. No master playlist is served — the master/quality selection happens at the Aniyomi `Video` layer (one Video per VariantData).

### 5.4 ★ PNG header stripping — `stripPngHeader(byte[])` (the key technique)

Called for every fetched segment. **Two-pass algorithm, robust to any PNG size (NOT hardcoded to 70 bytes):**

```
function stripPngHeader(data: byte[]) -> byte[]:
  if data.length < 8: return data

  # Pass 1a: detect PNG signature (89 50 4E 47)
  if NOT (data[0]==0x89 && data[1]=='P' && data[2]=='N' && data[3]=='G'):
    return data   # not a PNG — pass through untouched

  # Pass 1b: scan for IEND chunk marker (49 45 4E 44)
  cut = -1
  for i2 in 0 .. data.length-4:
    if data[i2..i2+3] == "IEND":
      cut = i2 + 8   # skip "IEND" (4 bytes) + CRC (4 bytes)
      break
  if cut < 0 OR cut >= data.length: return data

  stripped = data[cut .. data.length)   # everything after IEND chunk

  # Pass 2: MPEG-TS packet alignment refinement
  # MPEG-TS packets are 188 bytes, each starting with sync byte 0x47.
  # The IEND+8 cut may land a few bytes before the first 0x47 sync.
  # Scan up to min(len-188, 400) bytes for a 0x47 at offset i3
  # AND another 0x47 exactly 188 bytes later (proves alignment).
  scan_limit = min(stripped.length - 188, 400)
  for i3 in 0 .. scan_limit:
    if stripped[i3] == 0x47 && stripped[i3 + 188] == 0x47:
      return stripped[i3 .. stripped.length)   # packet-aligned trim
  return stripped   # no clean alignment — serve as-is (lenient)
```

**Key constants**:
- PNG magic: `89 50 4E 47`
- IEND marker: `49 45 4E 44` ("IEND")
- Cut offset = IEND position + 8 (IEND 4 bytes + CRC 4 bytes)
- MPEG-TS sync byte: `0x47` (71)
- MPEG-TS packet size: 188 bytes
- Max sync scan window: `min(len-188, 400)` bytes

**Cross-check**: `EXTENSIONS/anikoto/MEMORY/sites/png-wrapping.md` documented the 70-byte PNG header but not the strip algorithm. ★ Now confirmed: the header is a minimal fake PNG (signature + IHDR + optional IDAT + IEND), and the strip finds IEND dynamically (robust to variable header size) then aligns to the first MPEG-TS sync byte.

### 5.5 Segment caching + prefetch

- **LRU cache**: `ConcurrentHashMap<String, byte[]>`, max 50 entries, eviction = remove index 0 from `cacheOrder` synchronized list.
- **Cache key**: `"{audioType}/{quality}/{index}"`.
- **Concurrent-fetch dedup**: `ConcurrentHashMap<String, Boolean> fetching`. If a request arrives while a fetch is in-flight, the requester busy-waits up to 15s (50ms increments) for the cache to populate.
- **`fetchSegmentWithRetry(url)`**: 2 attempts, 500ms sleep between. Throws on non-2xx or empty body. Full-body `bytes()` read (NOT streaming — entire segment buffered, then PNG-stripped, then cached, then served).
- **Prefetch**: triggered after every successful segment serve. End index = `min(currentIndex + max(prefetchCount% * size / 100, 1), size-1)`. **Caps at 5 prefetch tasks per trigger**. Each prefetch task checks `prefetchGeneration` (AtomicLong) — if it changed (quality switch or stop), the task aborts.
- **Quality switch**: `onQualitySwitch()` bumps `prefetchGeneration` → cancels in-flight prefetches for the old quality.

### 5.6 Lifecycle
- **NOT a singleton itself**, but the extension holds it in `private static volatile LocalProxyServer activeProxyServer` and `Companion.swapProxyServer(newServer)` is `synchronized` — stops the old before storing the new. Effectively one active proxy per process.
- **`start()`**: idempotent; opens ServerSocket(0), starts accept + idle threads.
- **`stop()`**: idempotent via `running.getAndSet(false)`; closes ServerSocket, interrupts accept thread, `executor.shutdownNow()`, clears caches, bumps prefetchGeneration.
- **Idle auto-stop**: after 600s (10 min) of no activity, the idle monitor calls `stop()`. Prevents leaks.

## 6. Complete endpoint inventory (cross-checked against `EXTENSIONS/anikoto/MEMORY/sites/endpoints.md`)

| # | Method | URL | Headers | Purpose | Status in our research |
|---|---|---|---|---|---|
| 1 | GET | `<baseUrl>/most-viewed?page=<N>` | default | popular list HTML | ✓ matches (our `catalog-and-episodes-analysis.md` had `/filter?sort=most-viewed` — the reference uses the dedicated `/most-viewed` path; ★ verify which is current) |
| 2 | GET | `<baseUrl>/latest-updated?page=<N>` | default | latest list HTML | ✓ matches |
| 3 | GET | `<baseUrl>/filter?keyword=<q>&sort=<s>&genre[]=<g>&term_type[]=<t>&status[]=<st>&language[]=<l>&page=<N>` | default | search/filter HTML | ✓ matches (confirms query param names) |
| 4 | GET | `<baseUrl>/watch/<slug>/ep-1` | default | anime details page (also reveals `#watch-main[data-id]`=animeId) | ✓ matches |
| 5 | GET | `<baseUrl>/ajax/episode/list/<animeId>?vrf=<URLEncode(RC4("simple-hash",animeId))>&style=default` | ajaxHeaders(slug) | episode list JSON `{status, result=HTML}` | ✓ matches (★ vrf algorithm now confirmed) |
| 6 | GET | `https://mapper.nekostream.site/api/mal/<malId>/<epNum>/<timestamp>` | ajaxHeaders(slug) | mapper JSON (v3 only, dropped in v16.4) | ⚠️ not in our research (third-party; skip) |
| 7 | GET | `<baseUrl>/ajax/server/list?servers=<dataIds>` | ajaxHeaders(slug) | server-list JSON `{status, result=HTML}` | ✓ matches |
| 8 | GET | `<baseUrl>/ajax/server?get=<URLEncode(token)>` | ajaxHeaders(slug) | per-server JSON `{status, result:{url, skip_data:{intro,outro}}}` | ✓ matches (★ skip_data confirmed) |
| 9 | GET | `<iframeUrl>` (vidtube.site) | vidtubePageHeaders | iframe HTML; regex `data-id="(\d+)"` | ✓ matches |
| 10 | GET | `https://vidtube.site/stream/getSourcesNew?id=<dataId>&type=<sub|dub|hsub>` | vidtubeApiHeaders | VidTube sources JSON `{sources:{file}, tracks:[{file,label,kind}]}` | ✓ matches (★ `type` param confirmed) |
| 11 | GET | `<masterM3u8Url>` | Referer=vidtube.site, UA=Mozilla/5.0 | HLS master playlist | ✓ matches |
| 12 | GET | `<variantUrl>` | same | HLS media playlist | ✓ matches |
| 13 | (local) | `http://127.0.0.1:PORT/variant/{audioType}/{quality}.m3u8` | — | served by LocalProxyServer | (our `png-wrapping.md` anticipated this) |
| 14 | (local) | `http://127.0.0.1:PORT/seg/{audioType}/{quality}/{index}` | — | served by LocalProxyServer (PNG-stripped) | (our `png-wrapping.md` anticipated this) |

★ **One discrepancy to verify live**: the reference uses `/most-viewed?page=N` for popular, but our `catalog-and-episodes-analysis.md` documented `/filter?sort=most-viewed&page=N`. The site may support both, OR the dedicated `/most-viewed` path may be the current one. **Verify with agent-browser before finalizing our popular-anime implementation.**

## 7. What this means for OUR extension (Stage 4 — video extraction)

The reference confirms our `EXTENSIONS/anikoto/MEMORY/sites/` research was **substantially correct**. The implementation plan for our extension's video layer:

1. **RC4 vrf**: implement `AnikotoRC4` with key `"simple-hash"` for the episode-list `vrf` param. (~15 LOC)
2. **Two clients**: `client` (from `network.client`, for the catalog site) + `noCloudflareClient` (clean, for vidtube.site). Replicate the header sets from §2.
3. **Skip the mapper API** (match v16.4 — it's a third-party dependency we don't need).
4. **Hoster discovery**: `/ajax/server/list?servers=<dataIds>` → parse HTML → HosterTask per (server × audioType).
5. **Parallel resolution**: `coroutineScope { tasks.map { async { resolveStreamForTask(...) } }.awaitAll() }.filterNotNull()`.
6. **VidTube extractor**: 4-step (iframe → getSourcesNew → master m3u8 → variant m3u8s). Manual HLS parsing (no m3u8 lib). Subtitle language inference from label.
7. **LocalProxyServer**: implement our own (~250 LOC for the core, ~500 with prefetch/cache). Index-based URL scheme. PNG-strip with the IEND+8 + 0x47@188 algorithm. LRU cache (50), prefetch (configurable %), idle auto-stop (600s), `usesCleartextTraffic=true` in manifest.
8. **Video building**: `initialized=true`, title = `"<audioLabel> - <serverName> - <quality>"`, subtitleTracks from proxy.
9. **Sort**: `compareByDescending(startsWith(prefAudioLabel)).thenByDescending(contains(prefQuality))`, mark first as `preferred=true`.
10. **Return**: `Hoster.toHosterList(videos)` (single synthetic Hoster).
11. **resolveVideo**: no-op except `activeProxyServer?.onQualitySwitch()`.

See `decisions/03-best-method-to-build-extensions.md` for the full ADR.
