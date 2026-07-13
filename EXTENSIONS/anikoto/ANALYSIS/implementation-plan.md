# ANIKOTO — Stage 4 Implementation Plan (Final)

> Last updated: 2026-06-23 (session 12) · Status: ✅ READY TO IMPLEMENT
> Prerequisites: all analysis complete (see references below). Stage 4 unblocked.

This document is the **single source of truth** for implementing Stage 4 (video extraction).
It gives the file layout, the implementation order, and the exact spec for each piece — so
the actual coding is mechanical and smooth.

## References (all verified)

| What | Where |
|------|-------|
| Server × audio × resolution matrix | `server-audio-resolution-matrix.md` |
| Extraction flows (Flow A primary, Flow B Kiwi) | `extraction-flows.md` |
| Ad filtering strategy (per-server) | `ad-filtering-strategy.md` |
| Best-method ADR (12 points) | `MEMORY/decisions/03-best-method-to-build-extensions.md` |
| Video pipeline + proxy + crypto (APK analysis) | `EXTENSIONS/anikoto/EXTENSIONS/anikoto/MEMORY/research/apk-reference/02-video-pipeline-and-proxy.md` |
| Catalog + DTOs + filters | `EXTENSIONS/anikoto/EXTENSIONS/anikoto/MEMORY/research/apk-reference/03-catalog-and-dtos.md` |
| Live verification (7 items) | `EXTENSIONS/anikoto/EXTENSIONS/anikoto/MEMORY/research/apk-reference/06-live-verification-results.md` |
| Python prototype (reproducible) | `python-prototypes/analyze-full-chain-v2.py` |
| Raw analysis JSON | `anikoto-chain-analysis-v2.json` |
| Site research (source of truth) | `EXTENSIONS/anikoto/MEMORY/sites/` |

## What we're building (the goal)

A complete video-extraction layer for the Anikoto extension that:
- Supports **4 working servers**: VidPlay-1, HD-1, Vidstream-2, Kiwi-Stream
- Supports **3 audio types**: SUB, HSUB, DUB (H-SUB / A-DUB normalized to HSUB / DUB)
- Supports **3 resolutions**: 1080p, 720p, 360p
- **Skips VidCloud-1** (broken — getSourcesNew returns HTML error)
- **Dedups HD-1/Vidstream-2** (identical m3u8 — keep one, or both with clear labels)
- Runs a **local proxy** to strip PNG headers + filter ads + prefetch
- Produces **~27 Videos** (4 servers × 3 audio × 3 resolutions, minus Vidstream-2 dedup)

## File layout (the target structure)

```
src/en/anikoto/src/main/kotlin/eu/kanade/tachiyomi/animeextension/en/anikoto/
├── Anikoto.kt                          ← main source class (extends Source)
├── AnikotoRC4.kt                       ← RC4 vrf crypto (key "simple-hash")
├── EpisodeMeta.kt                      ← pipe-delimited SEpisode.url encoding
├── AnikotoDto.kt                       ← @Serializable DTOs (6 classes)
├── AnikotoFilters.kt                   ← catalog filters (already built in session 08)
└── video/
    ├── HosterTask.kt                   ← data class (label, token, audioType, source)
    ├── AudioStream.kt                  ← data class (audioType, audioLabel, hosterName, variants, subtitles)
    ├── VariantData.kt                  ← data class (quality, bandwidth, resolution, segments)
    ├── SegmentInfo.kt                  ← data class (url, duration)
    ├── SubtitleData.kt                 ← data class (url, label, lang)
    ├── VidTubeExtractor.kt             ← Flow A: iframe → data-id → getSourcesNew → m3u8
    ├── KiwiExtractor.kt                ← Flow B: base64 fragment → direct m3u8
    ├── LocalProxyServer.kt             ← index-based HTTP proxy (PNG strip + cache + prefetch)
    └── VideoSorter.kt                  ← sort + preferred-first logic
```

Plus the custom toolkit (shared across extensions):
```
src/main/kotlin/EXTENSIONS/utils/
├── Source.kt                           ← base class (v16-correct sigs)
├── Json.kt                             ← parseAs, toJsonString, etc.
├── Preferences.kt                      ← pref builders + PreferenceDelegate
├── LazyMutable.kt                      ← lazy + reassignable delegate
├── Collections.kt                      ← firstInstance
├── Date.kt                             ← tryParse
├── Format.kt                           ← formatBytes
├── Network.kt                          ← asJsoup
└── UrlUtils.kt                         ← fixUrl
```

## Implementation order (9 steps, one change at a time per rule §2)

### Step 1 — Foundation: EpisodeMeta + AnikotoRC4 + DTOs
**Files**: `EpisodeMeta.kt`, `AnikotoRC4.kt`, `AnikotoDto.kt`
**What**:
- `EpisodeMeta`: 8-field data class + `encode()` / `Companion.decode()` (pipe-delimited, `|`→`│` escape). Fields: slug, epNum, malId, timestamp, dataIds, hasSub, hasDub, epTitle.
- `AnikotoRC4`: `object` with `encodeVrf(animeId)` + private `rc4(key, input)`. Key `"simple-hash"`, textbook RC4, Base64.NO_WRAP, ISO_8859_1 bytes.
- `AnikotoDto.kt`: 6 `@Serializable` data classes: `EpisodeListResponse`, `ServerListResponse`, `ServerResponse` (+ `ServerResult`, `SkipData`), `VidTubeSourcesResponse` (+ `VidTubeSources`, `VidTubeTrack`). Plus `parseMapperResponse(JsonObject): List<MapperStreamToken>` + `MapperStreamToken` data class.
**Verify**: compiles, no runtime test yet.
**Spec source**: `EXTENSIONS/anikoto/EXTENSIONS/anikoto/MEMORY/research/apk-reference/03-catalog-and-dtos.md` §3-4, `02-video-pipeline-and-proxy.md` §3-4.

### Step 2 — Toolkit: EXTENSIONS/utils (Source base + helpers)
**Files**: `Source.kt`, `Json.kt`, `Preferences.kt`, `LazyMutable.kt`, `Collections.kt`, `Date.kt`, `Format.kt`, `Network.kt`, `UrlUtils.kt`
**What**: the minimal self-rolled toolkit (~700 LOC). `Source` extends `AnimeHttpSource` + `ConfigurableAnimeSource` with lazy `context`/`json`/`preferences`/`handler`, `displayToast()`, `migration` hook, and legacy-overrides that throw (v16-correct `videoListRequest(hoster)` / `videoListParse(response, hoster)` sigs).
**Verify**: compiles, `Anikoto` can extend `Source` instead of `AnimeHttpSource`.
**Spec source**: `EXTENSIONS/anikoto/EXTENSIONS/anikoto/MEMORY/research/apk-reference/04-toolkit-and-utils.md`.

### Step 3 — Upgrade Anikoto.kt: extend Source, add RC4 vrf, EpisodeMeta encoding
**Files**: `Anikoto.kt` (modify)
**What**:
- Change `: AnimeHttpSource()` → `: Source()`.
- In `getEpisodeList`: compute `vrf = AnikotoRC4.encodeVrf(animeId)` (currently we send empty vrf — works but implement for safety).
- Store `EpisodeMeta(slug, epNum, malId, timestamp, dataIds, hasSub, hasDub, epTitle).encode()` as `SEpisode.url` (currently we store just the slug).
- Add the two-client split: `client` (from `network.client`) + `noCloudflareClient` (lazy, fresh OkHttpClient).
- Add the header helpers: `ajaxHeaders(slug)`, `vidtubePageHeaders()`, `vidtubeApiHeaders()`, `kiwiHeaders()`, `segHeaders(host)`.
**Verify**: catalog layer still works (build + user tests search/details/episodes).
**Spec source**: `EXTENSIONS/anikoto/EXTENSIONS/anikoto/MEMORY/research/apk-reference/02-video-pipeline-and-proxy.md` §2, `06-live-verification-results.md` item 4.

### Step 4 — Video data classes
**Files**: `video/HosterTask.kt`, `video/AudioStream.kt`, `video/VariantData.kt`, `video/SegmentInfo.kt`, `video/SubtitleData.kt`
**What**: simple data classes for the in-memory playlist model.
**Verify**: compiles.
**Spec source**: `extraction-flows.md`, `02-video-pipeline-and-proxy.md` §5.1.

### Step 5 — VidTube extractor (Flow A: VidPlay-1, HD-1, Vidstream-2)
**Files**: `video/VidTubeExtractor.kt`
**What**:
- `suspend fun resolve(iframeUrl, audioType, hosterName): AudioStream?`
- Step 1: GET iframe page → regex `data-id="(\d+)"` → dataId.
- Step 2: GET `https://<host>/stream/getSourcesNew?id=<dataId>&type=<audioType>` → `VidTubeSourcesResponse`.
- Step 3: GET master m3u8 → parse `#EXT-X-STREAM-INF` → `List<VariantInfo>`.
- Step 4: for each variant, GET media m3u8 → `parseVariantSegments(text, url, filterAds=true)` → `List<SegmentInfo>` (keep only `nekostream.site` URLs).
- Step 5: build subtitles from tracks (infer lang from label: English→eng, etc.).
- Return `AudioStream(audioType, audioLabel, hosterName, variants, subtitles)`.
**Verify**: run the Python prototype output against the Kotlin output for Wistoria EP5 VidPlay-1 SUB — must match.
**Spec source**: `extraction-flows.md` §Flow A, `ad-filtering-strategy.md`.

### Step 6 — Kiwi extractor (Flow B: base64 fragment)
**Files**: `video/KiwiExtractor.kt`
**What**:
- `suspend fun resolve(iframeUrl, audioType, hosterName): AudioStream?`
- Step 1: extract base64 fragment after `#` → decode → direct m3u8 URL on `vibeplayer.site`.
- Step 2: GET master m3u8 (Referer: vibeplayer.site) → parse variants.
- Step 3: for each variant, GET media m3u8 → `parseVariantSegments(text, url, filterAds=false)` (★ NO ad filtering for Kiwi — all segments on p16-ad-sg.ibyteimg.com).
- Return `AudioStream(audioType, "H-SUB" or "A-DUB", hosterName, variants, emptyList())`.
**Verify**: run against Kiwi H-SUB for Wistoria EP5 — must match Python prototype.
**Spec source**: `extraction-flows.md` §Flow B, `ad-filtering-strategy.md` §Why Kiwi can't be filtered.

### Step 7 — Discovery + parallel resolution (getHosterList)
**Files**: `Anikoto.kt` (modify — implement `getHosterList` override)
**What**:
- Discovery A (primary): `GET /ajax/server/list?servers=<dataIds>` → parse `div.servers > div.type[data-type] > li[data-link-id]` → `List<HosterTask>` (one per server × audio).
- Discovery B (mapper): `GET https://mapper.nekostream.site/api/mal/<malId>/<epNum>/<timestamp>` → `parseMapperResponse` → Kiwi `HosterTask`s (mapper "sub"=H-SUB, "dub"=A-DUB). Wrap in try/catch (mapper is best-effort).
- Parallel resolution: `coroutineScope { tasks.map { async { resolveStreamForTask(it) } }.awaitAll() }.filterNotNull()`.
- `resolveStreamForTask`: resolve `/ajax/server?get=<token>` → iframe URL → dispatch by host:
  - `vidtube.site` or `megaplay.buzz` → `VidTubeExtractor.resolve()`
  - `mewcdn.online` → `KiwiExtractor.resolve()`
  - `vidwish.live` (VidCloud-1) → return null (skip, broken)
  - else → loge + return null
- Dedup: if keeping both HD-1 and Vidstream-2, dedup by master m3u8 URL (keep first, drop duplicate).
**Verify**: build, run `getHosterList` for Wistoria EP5 → expect 9-11 AudioStreams (4 servers × 3 audio minus VidCloud-1's 2 broken, minus Vidstream-2 dedup × 3).
**Spec source**: `extraction-flows.md` §Implementation plan, `02-video-pipeline-and-proxy.md` §1, `tokens-and-dedup.md` §Dedup strategy.

### Step 8 — LocalProxyServer (index-based proxy + PNG strip + cache + prefetch)
**Files**: `video/LocalProxyServer.kt`
**What** (per ADR 03 point 7 + `02-video-pipeline-and-proxy.md` §5):
- Raw `java.net.ServerSocket` on `127.0.0.1:0` (OS-assigned port). 3 daemon threads (accept / worker-pool / idle-monitor). Idle auto-stop after 600s.
- Index-based URL scheme: `/variant/{audioType}/{quality}.m3u8`, `/seg/{audioType}/{quality}/{index}`, `/sub/{audioType}/{subIndex}`.
- `setPlaylist(Playlist)` — pre-loaded in-memory `List<AudioStream>` → `List<VariantData>` → `List<SegmentInfo>`.
- `serveVariantPlaylist`: build-from-scratch m3u8 (each segment URL = `http://127.0.0.1:PORT/seg/{a}/{q}/{i}`).
- `serveSegment`: fetch upstream segment (follow 302), `stripPngHeader(bytes)`, cache (LRU 50), serve as `video/MP2T`.
- `stripPngHeader(bytes)`: detect PNG sig `89 50 4E 47`, find `IEND`, cut at IEND+8, verify `0x47`@cut. (~30 LOC)
- LRU cache: `ConcurrentHashMap<String, ByteArray>` (50 entries), `fetching` map for concurrent-fetch dedup.
- Prefetch: `prefetchCount%` ahead, max 5 concurrent, `prefetchGeneration: AtomicLong` (bump on quality switch → cancel old).
- `onQualitySwitch()`: bump prefetchGeneration.
- `getSubtitleTracks(audioType)`: return `List<Track>` for mpv `sub-add`.
**Verify**: unit-ish test — feed a small playlist, request `/variant/...`, confirm m3u8 output; request `/seg/...`, confirm PNG-stripped output starts with 0x47.
**Spec source**: `02-video-pipeline-and-proxy.md` §5, `ad-filtering-strategy.md` §Segment redirect behavior.

### Step 9 — Video building + sort + return (complete getHosterList)
**Files**: `Anikoto.kt` (modify — finish `getHosterList`), `video/VideoSorter.kt`
**What**:
- After resolving all AudioStreams: create `LocalProxyServer`, `setPlaylist(Playlist(streams))`, `start()`, `Companion.swapProxyServer(server)`.
- For each AudioStream × VariantData: build `Video(videoUrl = "${server.baseUrl}/variant/${audioType}/${quality}.m3u8", title = "${hosterName} - ${quality}", resolution, subtitleTracks, initialized = true)`.
- `sortVideos`: `compareByDescending { title.startsWith(prefAudioLabel, true) }.thenByDescending { title.contains(prefQuality, true) }`. Mark first as `preferred=true`.
- Return `Hoster.toHosterList(sortedVideos)`.
- `resolveVideo(video)`: no-op except `activeProxyServer?.onQualitySwitch()`; return video unchanged.
- `getVideoList(hoster)`: return `hoster.videoList`.
**Verify**: build debug APK, user installs + tests full playback (search → episode → play → quality switch → subtitles).
**Spec source**: `02-video-pipeline-and-proxy.md` §1 (Video building), `03-catalog-and-dtos.md` §2 (sort), ADR 03 points 8-10.

## The complete server handling matrix (final)

| Server | Player host | Flow | Audio types | Resolutions | Ad filter | Action |
|--------|-------------|------|-------------|-------------|-----------|--------|
| VidPlay-1 | vidtube.site | A | SUB, HSUB, DUB | 1080p/720p/360p | Keep `nekostream.site` | ✅ Support |
| HD-1 | megaplay.buzz (s-5) | A | SUB, HSUB, DUB | 1080p/720p/360p | Keep `nekostream.site` | ✅ Support |
| Vidstream-2 | megaplay.buzz (s-2) | A | SUB, HSUB, DUB | 1080p/720p/360p | Keep `nekostream.site` | ✅ Support (dedup with HD-1 — keep both, label clearly) |
| VidCloud-1 | vidwish.live | (A) | SUB, DUB (no HSUB) | — | — | ❌ Skip (getSourcesNew returns HTML error) |
| Kiwi-Stream | mewcdn.online → vibeplayer.site | B | H-SUB, A-DUB | 1080p/720p/360p | ★ Keep ALL (can't filter) | ✅ Support |

## Decision: HD-1 vs Vidstream-2 dedup

**Decision: Keep BOTH, no dedup.** Rationale:
- They're identical streams (same data-ids, same m3u8), so keeping both produces duplicate Videos.
- BUT the megaplay.buzz path differs (`s-5` vs `s-2`) — if one path breaks, the other may still work.
- The site's own UI shows both as separate server options.
- The user can pick either; the duplicate is a minor UX issue, not a correctness issue.
- The reference APKs also keep both (they just reject both via the vidtube.site check).

If the duplicate Videos bother the user during testing, we can add dedup-by-m3u8-URL in `sortVideos` (keep first, drop duplicates) — a 3-line change.

## Manifest change (for the local proxy)

Add to `common/AndroidManifest.xml` (for release builds):
```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```
Needed for `http://127.0.0.1:PORT/...` local proxy on Android 9+ release. Debug builds allow cleartext by default.

## Build & test checklist (after each step)

1. `source /home/z/my-project/.android-env.sh`
2. `cd EXTENSIONS/anikoto/DEV`
3. `./gradlew :src:en:anikoto:assembleDebug --no-daemon` → must be BUILD SUCCESSFUL
4. Copy APK to `EXTENSIONS/anikoto/APK/` (consolidated APK folder)
5. User installs in Aniyomi (Untrusted extensions enabled), tests the new functionality
6. Iterate on failures (one at a time, per rule §2)

## Honest notes

- **Step 8 (LocalProxyServer) is the hardest** — ~500 LOC of raw socket HTTP + threading + cache. The reference's `LocalProxyServer.java` is 1236 lines; ours will be leaner (~500) since we skip the mapper path and simplify. Budget the most time here.
- **Steps 5-6 (extractors) are the most important to get right** — they're the difference between 1 server (reference) and 4 servers (ours). The Python prototype (`analyze-full-chain-v2.py`) is the regression reference — run it before and after porting to Kotlin to confirm identical output.
- **Step 3 (upgrade Anikoto.kt) is the riskiest for regression** — it changes the existing catalog layer. Build + test the catalog after step 3 before proceeding to steps 4-9.
- **No new research is needed** — all endpoints, selectors, crypto, ad-filtering, and server behaviors are verified. This plan is complete.

## Status

- ✅ All analysis complete (sessions 06-12).
- ✅ All 5 servers tested (4 work, 1 broken).
- ✅ All 3 audio types verified per server.
- ✅ All 3 resolutions verified per audio type.
- ✅ Both extraction flows documented (Flow A primary, Flow B Kiwi).
- ✅ Ad filtering strategy per-server documented.
- ✅ Python prototype reproduces the full chain (regression reference).
- ✅ Implementation plan complete (9 steps, file layout, spec sources).
- ⏳ **Ready to implement** — start with Step 1.
