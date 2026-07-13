# ADR 03 — Best Method to Build Aniyomi Extensions (ext-lib 16)

> Date: 2026-06-22 (session 10) · Status: ACCEPTED · Supersedes: none
> Related: `MEMORY/decisions/01-use-aniyomiorg-extensions-lib-v16.md`, `MEMORY/decisions/02-workspace-folder-architecture.md`,
> `EXTENSIONS/anikoto/MEMORY/research/apk-reference/05-cross-check-lessons.md`, `MEMORY/research/05-keiyoushi-utils-core.md`

## Context

We are building Aniyomi anime extensions (ext-lib 16 era) from our own understanding and
research. After session 08 we had a working catalog-layer build (80 KB debug APK) but no video
extraction. In session 10 we analyzed two reference APKs (`anikoto-by-1118000-v3.apk` and
`anikoto-refrence-v16.4.apk`) to understand how a production Anikoto extension implements the full
pipeline (catalog → episodes → Hoster → video extraction → local proxy → PNG stripping).

The user asked: **"make sure that we use the best method to create the extensions."**

This ADR records the build-method decision informed by: (a) both reference APKs, (b) our
session-08 build experience, (c) our `MEMORY/research/05-keiyoushi-utils-core.md` toolkit
analysis, (d) our `EXTENSIONS/anikoto/MEMORY/sites/` live-site research.

## Decision

We adopt the following **12-point best method** for building Anikoto-style extensions on ext-lib 16:

### 1. Build system: AGP 8.13.2 + Gradle 8.14.3 + Kotlin 2.2.x + Java 17 + ext-lib v16 stubs from source
- AGP 9.x requires Gradle 9.x (not yet stable); AGP 8.13.2 + Gradle 8.14.3 is the stable ext-lib 16 target.
- Java 17 is required (the ext-lib v16 jar is Java 17 bytecode).
- **Stubs from source** (copy the ext-lib v16 `.kt` stub files into `src/main/kotlin/`) — bypasses all JitPack AAR / Kotlin-metadata compatibility issues. (Verified in session 08.)
- `kotlin.stdlib.default.dependency=false` in `gradle.properties` (don't bundle the stdlib — the host app provides it).

### 2. Toolkit: a minimal self-rolled `extensions.utils` (~700 LOC) — NOT keiyoushi.utils
- **`Source` abstract base** (extends `AnimeHttpSource` + `ConfigurableAnimeSource`) with: lazy `context`/`json`/`preferences`/`handler`, `displayToast()`, `migration` hook, and legacy-overrides that throw (forcing suspend `get*` implementations).
- **★ v16-correct signatures** on the legacy overrides: `videoListRequest(hoster: Hoster)` + `videoListParse(response: Response, hoster: Hoster)` — NOT the v14-broken `videoListRequest(episode)` / `videoListParse(response)`. (keiyoushi's Source has the broken sigs; the v3 reference's Source has the correct ones — copy the v3 pattern.)
- **JSON helpers** (5): `String.parseAs<T>`, `Response.parseAs<T>`, `T.toJsonString`, `T.toRequestBody`, `String.toJsonBody`.
- **Preferences** (9 funcs): `get/addEditTextPreference`, `get/addListPreference`, `get/addSetPreference`, `get/addSwitchPreference` + `PreferenceDelegate` (with `ClassCastException` catch for safety) + `LazyMutable`.
- **Collections** (2): `firstInstance<T>()`, `firstInstanceOrNull<T>()`.
- **Date** (1): `SimpleDateFormat.tryParse(date): Long` (0L on failure).
- **Format** (1): `Long.formatBytes(): String` (for debug logs).
- **Network** (1): `Response.asJsoup(): Document` (convenience).
- **UrlUtils** (1): `fixUrl(...)` (relative-URL resolution).
- **Do NOT pull in all of keiyoushi.utils** — it's 4× the code we need (GraphQL/Protobuf/NextJs/ReactFlight/crypto/url-utils we don't use). The v16.4 reference APK demonstrates that bundling keiyoushi.utils becomes unused bloat (75 files, 0 importers in the Anikoto class).

### 3. Dependencies: all `compileOnly` (host provides at runtime)
- `okhttp`, `jsoup`, `kotlinx-serialization-json`, `kotlinx-coroutines`, `rxjava`, `injekt-core`, `quickjs`, `androidx.preference` — all `compileOnly`.
- The host Aniyomi app provides these at runtime via parent-first classloading.
- Result: lean APK (our session-08 build is 80 KB). Compare: v3 reference 250 KB, v16.4 reference 1.1 MB (bloated).

### 4. Two HTTP clients
- `client` = `network.client.newBuilder().addInterceptor(fill-UA-Referer-when-missing).build()` — inherits Cloudflare interceptor + cookie jar + cache from the app. For the Cloudflare-protected catalog site.
- `noCloudflareClient` = fresh `OkHttpClient.Builder()` with explicit timeouts (15s/30s/15s/60s) + always-set browser headers (UA=Mozilla/5.0, Accept, Accept-Language, Referer). For the video CDN (vidtube.site) which isn't behind Cloudflare.
- **Why**: the app's Cloudflare interceptor mishandles non-CF sites; a clean client with proper Referer/UA is needed for CDN requests.

### 5. Catalog: HTML scraping with Jsoup + `EpisodeMeta` pipe-encoding + RC4 vrf
- `getPopularAnime` → `GET <baseUrl>/most-viewed?page=<N>` (verify live vs `/filter?sort=most-viewed`).
- `getLatestUpdates` → `GET <baseUrl>/latest-updated?page=<N>`.
- `getSearchAnime` → `GET <baseUrl>/filter?keyword=<q>&sort=<s>&genre[]=<g>&term_type[]=<t>&status[]=<st>&language[]=<l>&page=<N>`.
- `getAnimeDetails` → `GET <baseUrl>/watch/<slug>/ep-1`, parse `#w-info` + `.bmeta` + `.synopsis` (verify live vs `.binfo`/`.brating`).
- `getEpisodeList` → two-step: (1) `GET /watch/<slug>/ep-1` to extract `#watch-main[data-id]`, (2) `GET /ajax/episode/list/<animeId>?vrf=<RC4("simple-hash",animeId)>&style=default` → HTML fragment → parse `<a data-num data-mal data-timestamp data-ids data-sub data-dub title>`.
- **`EpisodeMeta` pipe-encoding** in `SEpisode.url`: `<slug>/ep-<epNum>|<malId>|<timestamp>|<dataIds>|<hasSub?1:0>|<hasDub?1:0>|<epTitle>` (escape `|`→`│` in epTitle). Eliminates re-fetch in `getHosterList`.
- **`AnikotoRC4`**: `encodeVrf(animeId) = Base64.NO_WRAP( rc4("simple-hash", animeId).getBytes(ISO_8859_1) )`. Textbook RC4, key `"simple-hash"`. (~15 LOC.)
- `SEpisode.scanlator` = "Sub" / "Dub" / "Sub / Dub" / "Raw" (per rule §8). Episode list reversed (newest first).

### 6. Video pipeline: parallel Hoster discovery + resolution + VidTube extractor
- **Discovery**: `GET /ajax/server/list?servers=<dataIds>` → HTML → `div.servers > div.type[data-type=sub|dub|hsub] > li[data-link-id]` → one `HosterTask` per (server × audioType).
- **Skip the mapper.nekostream.site API** (v3 used it as optional; v16.4 dropped it — third-party, unnecessary).
- **Parallel resolution**: `coroutineScope { tasks.map { async { resolveStreamForTask(...) } }.awaitAll() }.filterNotNull()`.
- **Per-task**: `GET /ajax/server?get=<URLEncode(token)>` → `{result:{url:<iframe>}}`. Reject non-vidtube iframes (implicit VidCloud-1 skip).
- **VidTube extractor** (4 steps, using `noCloudflareClient`):
  1. GET iframe → HTML → regex `data-id="(\d+)"`.
  2. GET `https://vidtube.site/stream/getSourcesNew?id=<dataId>&type=<audioType>` → `{sources:{file:<m3u8>}, tracks:[{file,label,kind}]}`. Build subtitles with language inference (English→eng, Spanish→spa, etc.).
  3. GET master m3u8 → parse `#EXT-X-STREAM-INF:` lines → `List<VariantInfo(url, bandwidth, quality, resolution)>`.
  4. For each variant: GET media m3u8 → `parseVariantSegments()` → `List<SegmentInfo(url, duration)>` (filter ads here).
- Build `AudioStream(audioType, audioLabel, hosterName, variants, subtitles)` per task.

### 7. Local proxy: index-based URL scheme + build-from-scratch m3u8 + PNG-strip + LRU cache + prefetch
- **Raw `java.net.ServerSocket` HTTP/1.1 server** on `127.0.0.1:0` (OS-assigned port). 3 daemon threads (accept / worker-pool / idle-monitor). Idle auto-stop after 600s.
- **Index-based URL scheme** (NOT URL-rewriting): `/variant/{audioType}/{quality}.m3u8`, `/seg/{audioType}/{quality}/{index}`, `/sub/{audioType}/{subIndex}`. Pre-load an in-memory `Playlist` via `setPlaylist()`.
- **m3u8 build-from-scratch**: emit a fresh media playlist from the in-memory `VariantData.segments`, with each segment URL pointing back at the proxy by index.
- **PNG-strip** (`stripPngHeader(byte[])`): two-pass — (1) detect PNG sig `89 50 4E 47`, scan for `IEND`, cut at IEND+8; (2) MPEG-TS alignment — scan up to 400 bytes for `0x47` at offset i AND `0x47` at i+188. Robust to any PNG header size.
- **LRU cache**: `ConcurrentHashMap<String, byte[]>` (50 entries). `fetching` map for concurrent-fetch dedup (busy-wait 15s).
- **Prefetch**: `prefetchCount%` of total segments ahead, max 5 concurrent, `prefetchGeneration: AtomicLong` (bump on quality switch → cancels old prefetches).
- `usesCleartextTraffic="true"` in the manifest (required for localhost HTTP on Android 9+ release).

### 8. Video objects: `initialized=true` + subtitleTracks
- `Video(videoUrl, title, resolution, subtitleTracks=subs, initialized=true)`.
- `videoUrl` = `http://127.0.0.1:PORT/variant/<audioType>/<quality>.m3u8`.
- `title` = `"<audioLabel> - <serverName> - <quality>"` (e.g. `"SUB - VidPlay-1 - 1080p"`).
- `initialized=true` → app's `HosterLoader` won't call `resolveVideo` lazily (videos are pre-resolved).

### 9. Sort + preferred-first
- `sortVideos(list)` = `sortedByDescending { title.startsWith(prefAudioLabel, true) }.thenByDescending { title.contains(prefQuality, true) }`.
- `prefAudioLabel` = "SUB" / "DUB" / "HSUB" (from `preferredAudio` pref: "SUB" / "A-DUB" / "H-SUB").
- `prefQuality` = "1080" / "720" / "480" / "360".
- First video gets `preferred=true` → player auto-selects.

### 10. Return: `Hoster.toHosterList(videos)` (single synthetic Hoster)
- Wrap all videos in one synthetic `Hoster(NO_HOSTER_LIST)`.
- `getVideoList(hoster)` returns `hoster.videoList`.
- `resolveVideo(video)` is a no-op except `activeProxyServer?.onQualitySwitch()`.

### 11. Release build: R8 minification + cleartext flag + signing
- `isMinifyEnabled=true` in the release build type (R8 strips unused stub classes).
- `android:usesCleartextTraffic="true"` in the manifest (for the localhost proxy).
- Proper signing key for distribution (debug key for development only).

### 12. Defensive coding: try/catch everywhere + displayToast feedback
- Every public suspend method wraps in `try { ... } catch (e) { loge(...); return emptyList()/null }`.
- Never throw to the app — degrades gracefully.
- `displayToast()` for user-visible feedback ("No playable streams found", "Ready to play - <title>").
- Extensive `logi`/`logw`/`loge` logging for logcat debugging.

## Rationale

This method is the synthesis of:
- **v3 reference** (250 KB, debug, clean): the readable implementation proving the architecture works.
- **v16.4 reference** (1.1 MB, release, obfuscated): the current-behavior reference (matches live site `anikototv.to`, dropped the mapper, refactored leaner).
- **Our session-08 build** (80 KB, debug, catalog-only): proving the build system + ext-lib stubs-from-source approach works.
- **keiyoushi/utils research**: confirming we only need ~700 LOC of utils, not the full 2500+ LOC toolkit.

The v3 reference's lean build config (no stdlib bundling, all deps compileOnly) is correct; the v16.4 reference's bloated build (3.3 MB single DEX with stdlib + Apache + unused keiyoushi) is an anti-pattern. Our 80 KB build is the leanest correct approach.

The index-based local proxy (vs URL-rewriting) is a superior design: hides upstream URLs, makes ad filtering + prefetch trivial. Adopt it.

The `EpisodeMeta` pipe-encoding pattern eliminates a re-fetch in `getHosterList` — adopt it.

The RC4 vrf with key `"simple-hash"` is server-validated — must implement it (our session-08 build skipped this, which is a runtime blocker).

## Consequences

- **Our session-08 `Anikoto.kt` needs upgrades before Stage 4**: implement `AnikotoRC4`, upgrade `SEpisode.url` to `EpisodeMeta` encoding, add the `Source` base class, split into two clients.
- **Stage 4 (video extraction)** is now fully specified: follow points 6-10. Estimated ~1500 LOC (Hoster flow + VidTube extractor + LocalProxyServer + RC4 + DTOs).
- **Our `extensions.utils` toolkit** (~700 LOC) becomes a reusable asset for future extensions (not just Anikoto).
- **Release builds** will require the cleartext flag + R8 + signing (not needed for debug testing).
- **Open live verifications** (§6 of `05-cross-check-lessons.md`) must be done with agent-browser before finalizing endpoints/selectors.

## Alternatives considered

1. **Use keiyoushi.utils fully** — rejected: 4× the code we need, the v16.4 reference shows it becomes unused bloat, and our extensions would inherit its v14-broken `Source` signatures (requiring deletes).
2. **Use the JitPack v16 AAR** (instead of stubs from source) — rejected: Kotlin metadata compatibility issues with Kotlin 2.2.x (verified in session 08). Stubs from source is more reliable.
3. **URL-rewriting local proxy** (like yuzono's `lib/m3u8server`) — rejected: the index-based approach is cleaner (hides upstream URLs, trivial ad filter + prefetch). The reference uses index-based.
4. **Keep the mapper.nekostream.site API** — rejected: third-party dependency, v16.4 dropped it, unnecessary (primary endpoint covers all servers).
5. **Bundle the Kotlin stdlib** — rejected: bloats the APK 40×, host provides it. v3 reference + our build both correctly disable it.

## Verification plan

Before declaring Stage 4 complete:
1. Implement points 5-10 (RC4 + EpisodeMeta + Hoster flow + VidTube extractor + LocalProxyServer + Video building + sort).
2. Build debug APK (`./gradlew :src:en:anikoto:assembleDebug`).
3. Verify the open live items (§6 of `05-cross-check-lessons.md`) with agent-browser.
4. User installs the APK in Aniyomi, tests: search → details → episode list → play → quality switch → subtitle selection.
5. Iterate on failures (one at a time, per rule §2).
