# 05 — Cross-Check Lessons & Best-Method Synthesis

> Last updated: 2026-06-22 (session 10) · Status: VERIFIED
> Per project rule §1: these are LESSONS LEARNED from understanding the reference APKs.
> We build our own implementation following these techniques (written ourselves, not copied).

This document distills what we LEARNED from analyzing both reference APKs, cross-checked against
our own live-site research (`EXTENSIONS/anikoto/MEMORY/sites/`) and our session-08 build. Each lesson cites
the decompiled evidence and notes whether it confirms, corrects, or extends our prior research.

## 1. What the reference CONFIRMED about our live-site research

Our `EXTENSIONS/anikoto/MEMORY/sites/` analysis (done via agent-browser against the live site in session 06-07) was **substantially correct**. The reference APK confirms:

| Our research finding | Reference confirmation |
|---|---|
| 5 servers (VidPlay-1, HD-1, Vidstream-2, VidCloud-1, Kiwi-Stream) | ✓ all 5 appear in the server-list HTML |
| 3 audio types (SUB, HSUB, DUB) | ✓ `data-type="sub|hsub|dub"` confirmed |
| VidCloud-1 is broken | ✓ implicitly filtered (`iframeUrl.contains("vidtube.site")` rejects non-vidtube hosts → VidCloud-1's vidwish.live iframe dies) |
| HD-1 ≡ Vidstream-2 (same m3u8) | ✓ reference does NOT dedup (both produce Videos) — confirms they're equivalent but the reference leaves both |
| `/ajax/server?get=<link_id>` → player URL | ✓ confirmed (token = `data-link-id`) |
| `/ajax/episode/list/<animeId>?vrf=` → episode HTML | ✓ confirmed + ★ vrf algorithm now known (RC4) |
| `/ajax/server/list?servers=<dataIds>` → server HTML | ✓ confirmed |
| `getSourcesNew?id=<file_id>&type=<audio>` → m3u8 + subs | ✓ confirmed (`type` param = audio type) |
| PNG wrapping (70-byte header before MPEG-TS) | ✓ confirmed + ★ exact strip algorithm now known (IEND+8 + 0x47@188) |
| Ad injection (132 ad vs 12 real segments) | ✓ ads filtered upstream in `parseVariantSegments` (the proxy receives a clean playlist) |
| Episode `<a data-ids data-num data-sub data-dub>` | ✓ confirmed + `data-mal`, `data-timestamp` also captured |
| `SEpisode.scanlator` for sub/dub (rule §8) | ✓ confirmed ("Sub", "Dub", "Sub / Dub", "Raw") |
| Episode list reversed (newest first) | ✓ confirmed |

## 2. What the reference CORRECTED / extended

| Area | Our prior understanding | Reference reality | Action |
|---|---|---|---|
| **baseUrl** | `https://anikototv.to` (our live research) | v3 used `anikoto.cz`; v16.4 uses `anikototv.to` | ✓ our research is current; v3 is stale. Always use `anikototv.to`. |
| **vrf algorithm** | "needs vrf param" (unknown algorithm) | RC4(key=`"simple-hash"`, animeId) → Base64.NO_WRAP → URLEncode | ★ implement `AnikotoRC4` |
| **PNG strip** | "70-byte header, needs m3u8server" | Two-pass: PNG-sig check → IEND scan → cut at IEND+8 → 0x47@188 alignment | ★ implement `stripPngHeader` (robust, not hardcoded to 70) |
| **Popular endpoint** | `/filter?sort=most-viewed&page=N` | `/most-viewed?page=N` (dedicated path) | ⚠️ **verify live** which is current |
| **Latest endpoint** | `/filter?sort=latest-updated&page=N` | `/latest-updated?page=N` | ⚠️ **verify live** |
| **Details selectors** | `.binfo`, `.bmeta`, `.brating` | `#w-info`, `.bmeta`, `.synopsis` | ⚠️ **verify live** — may be two layouts or site change |
| **EpisodeMeta** | not used (slug only in SEpisode.url) | pipe-delimited 8-field encode (slug, epNum, malId, timestamp, dataIds, hasSub, hasDub, epTitle) | ★ adopt (eliminates re-fetch in getHosterList) |
| **Two-client split** | single client | `client` (Cloudflare, for catalog site) + `noCloudflareClient` (clean, for vidtube.site) | ★ adopt (CF interceptor interferes with vidtube) |
| **Mapper API** | not in our research | v3 used `mapper.nekostream.site`; v16.4 dropped it | skip (third-party, unnecessary) |
| **LocalProxyServer URL scheme** | anticipated URL-rewriting | index-based (`/variant/{a}/{q}.m3u8`, `/seg/{a}/{q}/{i}`) with in-memory Playlist | ★ adopt index-based (cleaner, hides upstream URLs, trivial ad filter + prefetch) |
| **`initialized=true` on Videos** | not considered | set on all Videos → app's HosterLoader skips `resolveVideo` | ★ adopt (videos are pre-resolved) |
| **`usesCleartextTraffic="true"`** | not considered | required for release builds with localhost HTTP proxy | ★ add to our manifest for release |

## 3. Techniques worth adopting (the "best practices" from the reference)

### 3.1 The two-client split
- `client` = `network.client.newBuilder().addInterceptor(fill-UA-Referer-when-missing).build()` — inherits Cloudflare + cookies + cache from the app. For the catalog site.
- `noCloudflareClient` = fresh `OkHttpClient.Builder()` with explicit timeouts + always-set browser headers. For the video CDN (vidtube.site) which isn't behind Cloudflare.
- **Why**: the app's Cloudflare interceptor mishandles non-CF sites; a clean client with proper Referer/UA is needed for CDN requests.

### 3.2 The `ajaxHeaders(slug)` pattern
- Inherits base UA/Referer, then overrides Referer to `<baseUrl>/watch/<slug>/ep-1` + adds `X-Requested-With: XMLHttpRequest`.
- Mimics browser behavior: AJAX calls originate from the open episode page. The server may validate Referer.

### 3.3 EpisodeMeta pipe-encoding in `SEpisode.url`
- Pack all episode state (slug, epNum, malId, timestamp, dataIds, hasSub, hasDub, epTitle) into `SEpisode.url` as a pipe-delimited string.
- `getHosterList` decodes it → no re-fetch of the watch page needed.
- Escape `|` in epTitle → `│` (U+2502).
- **Why**: saves one HTTP round-trip per episode-play. Significant when the user browses episodes.

### 3.4 Parallel Hoster resolution
```kotlin
coroutineScope {
    tasks.map { task ->
        async { resolveStreamForTask(task.label, task.token, task.audioType, meta.slug) }
    }.awaitAll()
}.filterNotNull()
```
- Each (server × audioType) task is independent → resolve in parallel.
- `filterNotNull()` drops failures (e.g. VidCloud-1, dead servers).
- Clean, idiomatic, no manual thread management.

### 3.5 The index-based local proxy (vs URL-rewriting)
- Pre-parse upstream m3u8 → `List<SegmentInfo(url, duration)>` (filtering ads here).
- Build an in-memory `Playlist` → `AudioStream` → `VariantData` → `SegmentInfo` tree.
- Proxy serves `/variant/{audioType}/{quality}.m3u8` (build-from-scratch) and `/seg/{audioType}/{quality}/{index}` (fetch + PNG-strip + cache).
- **Why index-based**: (a) upstream URLs never leak, (b) ad filtering is trivial (just omit ad SegmentInfo), (c) prefetch is trivial (walk indices), (d) no URL-encoding/decoding complexity.

### 3.6 The PNG-strip two-pass algorithm
- Pass 1: detect PNG sig (`89 50 4E 47`), scan for `IEND`, cut at IEND+8.
- Pass 2: MPEG-TS alignment — scan up to 400 bytes for `0x47` at offset i AND `0x47` at i+188.
- **Why two-pass**: the IEND cut may land a few bytes before the first TS sync; the alignment pass trims to the real packet boundary. Robust to any PNG header size.

### 3.7 LRU segment cache + generation-cancellable prefetch
- `ConcurrentHashMap<String, byte[]>` (50 entries, LRU eviction).
- `fetching` map for concurrent-fetch dedup (busy-wait 15s for in-flight fetch).
- Prefetch: `prefetchCount%` of total segments ahead, max 5 concurrent, `prefetchGeneration: AtomicLong` — bump on quality switch → cancels old prefetches.
- **Why**: smooth playback on slow CDNs; quality switch doesn't waste bandwidth on the old quality.

### 3.8 `initialized=true` + `Hoster.toHosterList(videos)` hybrid
- Build all `Video` objects with `initialized=true` → app's `HosterLoader` won't call `resolveVideo` lazily (videos are pre-resolved).
- Wrap all videos in a single synthetic `Hoster(NO_HOSTER_LIST)` via `Hoster.toHosterList(videos)`.
- `getVideoList(hoster)` just returns `hoster.videoList`.
- `resolveVideo(video)` is a no-op except `activeProxyServer?.onQualitySwitch()`.
- **Why**: the videos already point at the local proxy; no further resolution needed. The Hoster wrapper satisfies the ext-lib 16 API.

### 3.9 Sort + preferred-first
```kotlin
sortVideos(list) = list.sortedWith(
    compareByDescending<Video> { it.videoTitle.startsWith(prefAudioLabel, true) }
        .thenByDescending { it.videoTitle.contains(prefQuality, true) }
)
list[0].preferred = true
```
- Pref audio ("SUB"/"DUB"/"HSUB") match at title start → first.
- Pref quality ("1080"/"720"/"480"/"360") substring match → first within audio bucket.
- First video gets `preferred=true` → player auto-selects.

### 3.10 Defensive `try/catch` everywhere + `displayToast` feedback
- Every public suspend method wraps in `try { ... } catch (e) { loge(...); return emptyList()/null }`.
- Never throws to the app — degrades gracefully to "no episodes" / "no videos".
- `displayToast` for user-visible feedback ("Anikoto: No playable streams found", "Anikoto: Ready to play - <title>").
- **Why**: a crash in the extension would break the whole Aniyomi app; defensive catches + toasts are user-friendly.

## 4. Anti-patterns to AVOID (lessons from the reference's mistakes)

### 4.1 Don't bundle the Kotlin stdlib (v16.4's 3.3 MB bloat)
v16.4 bundles the full Kotlin stdlib (`kotlin/*.kotlin_builtins` + stdlib classes) → 3.3 MB single DEX. v3 doesn't (`kotlin.stdlib.default.dependency=false` effective) → 250 KB. The host Aniyomi app provides the stdlib at runtime.
- **Our build**: keep `kotlin.stdlib.default.dependency=false` in `gradle.properties`. ✓ already done (our APK is 80 KB).

### 4.2 Don't bundle unused libraries (v16.4's Apache + keiyoushi bloat)
v16.4 bundles Apache Commons (lang3 + text, 722 files) + keiyoushi.utils (75 files) but the `Anikoto` class imports NEITHER. Pure bloat from build-config mistakes (R8 not stripping, or transitive deps not marked `compileOnly`).
- **Our build**: all deps `compileOnly` (host provides them). ✓ already done.

### 4.3 Don't use debug builds for release (v3's `debuggable=true`)
v3 is a debug build (`android:debuggable="true"`). Debug builds are slower (no R8) and insecure. v16.4 is a proper release build (R8-obfuscated, signed).
- **Our build**: use the `release` build type with R8 (`isMinifyEnabled=true`) for distribution. Debug only for development. (Our session-08 build was debug — fine for testing, switch to release for distribution.)

### 4.4 Don't forget `usesCleartextTraffic="true"` for localhost proxies (release)
v3 (debug) didn't need it (debug allows cleartext). v16.4 (release) has it — required for the `http://127.0.0.1:PORT/...` local proxy on Android 9+.
- **Our build**: add `android:usesCleartextTraffic="true"` to the manifest for release builds. (Our debug build works without it; release won't.)

### 4.5 Don't rely on the mapper.nekostream.site third-party API
v3 used it as an optional discovery path; v16.4 dropped it. Third-party dependencies are fragile (the service could disappear, change its API, or rate-limit).
- **Our build**: skip the mapper entirely. The primary `/ajax/server/list` endpoint covers all 5 servers. ✓ match v16.4.

### 4.6 Don't skip the `vrf` param (server validates it)
The episode-list AJAX requires `?vrf=<RC4("simple-hash", animeId)>`. Without it, the server returns an error. Our session-08 build noted but didn't implement vrf — **this is a blocker for episode-list to work at runtime**.
- **Our build**: implement `AnikotoRC4` (key `"simple-hash"`) before user testing.

## 5. The "best method" to build extensions (synthesis)

Informed by both reference APKs + our session-08 build + the keiyoushi/utils research, the best method to build Anikoto-style extensions is:

1. **Build system**: AGP 8.13.2 + Gradle 8.14.3 + Kotlin 2.2.x + Java 17 + ext-lib v16 stubs from source (not JitPack AAR). ✓ our session-08 setup is correct.
2. **Toolkit**: a minimal self-rolled `extensions.utils` (~700 LOC) — `Source` base (v16-correct sigs) + JSON×5 + Preferences×9 + `PreferenceDelegate` (with ClassCastException catch) + `LazyMutable` + `Collections`×2 + `Date`×1 + `Format`×1 + `NetworkKt.asJsoup` + `UrlUtils.fixUrl`. Do NOT pull in all of keiyoushi.utils.
3. **Dependencies**: all `compileOnly` (host provides). `kotlin.stdlib.default.dependency=false`. ✓ our setup is correct.
4. **Two HTTP clients**: `client` (from `network.client`, for the Cloudflare-protected catalog site) + `noCloudflareClient` (clean, for the video CDN).
5. **Catalog**: HTML scraping with Jsoup. `EpisodeMeta` pipe-encoding in `SEpisode.url`. RC4 vrf for the episode-list AJAX.
6. **Video pipeline**: parallel Hoster discovery + resolution → VidTube extractor (iframe → getSourcesNew → master m3u8 → variant m3u8s) → `AudioStream`/`VariantData`/`SegmentInfo` in-memory model.
7. **Local proxy**: index-based URL scheme, build-from-scratch m3u8, PNG-strip (IEND+8 + 0x47@188), LRU cache (50), prefetch (configurable %, generation-cancellable), idle auto-stop (600s).
8. **Video objects**: `initialized=true`, title = `"<audioLabel> - <serverName> - <quality>"`, subtitleTracks from proxy.
9. **Sort + prefer**: audio match (startsWith) then quality match (contains); first video `preferred=true`.
10. **Return**: `Hoster.toHosterList(videos)` (single synthetic Hoster).
11. **Release build**: R8 minification, `usesCleartextTraffic=true`, proper signing.
12. **Defensive coding**: try/catch everywhere, `displayToast` feedback, never throw to the app.

See `MEMORY/decisions/03-best-method-to-build-extensions.md` for the formal ADR.

## 6. Open verification items — ✅ ALL RESOLVED (session 11)

All 7 items were verified live against `https://anikototv.to` in session 11. Full results in
`06-live-verification-results.md`. Summary:

1. **`/most-viewed` vs `/filter?sort=most-viewed`** → ✅ BOTH work, identical content (SEO alias). Use either.
2. **`/latest-updated` vs `/filter?sort=latest-updated`** → ✅ BOTH work, identical content (SEO alias). Use either.
3. **Details-page selectors** → ✅ `#w-info` + `.binfo` + `.bmeta` + `.brating` + `.synopsis` ALL coexist on `/watch/<slug>/ep-1`. Adopt reference's `#w-info`-prefixed selectors (superset).
4. **`vrf` param** → ✅ Server does NOT validate (all 3 cases return identical content). Implement RC4 anyway for safety.
5. **VidTube `type` param** → ✅ CONFIRMED: sub/hsub/dub return different m3u8 URLs (different hashes).
6. **PNG header** → ✅ CONFIRMED: 70-byte PNG header (IEND@62, cut@70), MPEG-TS 0x47 sync follows. Both real AND ad segments are PNG-wrapped.
7. **Ad-segment discrimination** → ✅ CONFIRMED: by CDN host (`mt.nekostream.site`=real, `p1.ipstatp.com`=ad). 12 real vs 130 ad in test playlist. Real segment URLs 302-redirect to actual data.

**Stage 4 is now unblocked** — proceed with implementation following ADR 03.
