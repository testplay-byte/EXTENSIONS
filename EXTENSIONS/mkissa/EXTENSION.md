# MKissa 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. For deep context, read `MEMORY/` (this folder's
> knowledge base) and the latest `MEMORY/session-logs/`.

---

## Identity (★ DO NOT CHANGE without an ADR)

| Field | Value | Notes |
|---|---|---|
| **Display name** | `MKissa 180` | Source ID = `MD5("mkissa 180/en/1")` |
| **versionId** | `1` (STABLE) | Bumping orphans saved anime. NEVER change after publish. |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.mkissa180` | applicationId = namespace + applicationIdSuffix |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.mkissa.MKissa` | FULL path, no leading dot (applicationId ≠ source package) |
| **versionCode** | `17` | Bump per build |
| **versionName** | `16.17` | |
| **Target site** | `mkissa.to` | SvelteKit frontend on the `api.allanime.day` GraphQL API |
| **API endpoint** | `https://api.allanime.day/api` | GraphQL (POST with full query strings) |
| **Signing key** | `mkissa-release.jks` (NOT YET GENERATED) | Generate in Step 5 (release). Debug builds don't need it. |

## Build

```bash
# Environment (every new shell)
source /home/z/my-project/.android-env.sh

cd /home/z/my-project/EXTENSIONS/mkissa/DEV

# Debug APK (for testing — no R8, no signing needed)
./gradlew :src:en:mkissa:assembleDebug --no-daemon
# → src/en/mkissa/build/outputs/apk/debug/aniyomi-en.mkissa180-v16.17-debug.apk (~275KB)

# Release APK (signed, R8 — requires keystore; NOT YET GENERATED)
# keytool -genkeypair -keystore mkissa-release.jks -alias mkissa -keyalg RSA -keysize 2048 -validity 10000 -storepass $KEYSTORE_PASSWORD -keypass $KEYSTORE_PASSWORD -dname "CN=MKissa 180, O=Confused_Creature_180, C=US"
# ./gradlew :src:en:mkissa:assembleRelease --no-daemon
```

Before/after every build, follow `MEMORY/guides/04-build-checklist.md` (project-level — mandatory).

## Current status (v16.17 Build 17) — 🚧 CATALOG + DETAILS + EPISODES + METADATA + PARTIAL VIDEO PLAYBACK

### ✅ Working
- **Catalog**: popular (Daily, 40/page), latest (sortBy=Recent), search (with 6 filter categories). Pagination works.
- **Details**: full metadata (title, description, genres, status, studios, score, type, season).
- **Episodes**: sub+dub via scanlator field, descending order (Aniyomi displays ascending), fork-compat URL encoding.
- **Episode metadata enrichment**: thumbnails + titles + descriptions via Anikage + Jikan. AniList ID extracted from thumbnail URL.
- **Settings**: 3 categories (Video playback, Servers, Episode metadata). 6 real servers in toggle list. Preferred server dropdown.
- **Video playback — 3 of 6 servers working**:
  - **Ok.ru** ✅ — HLS extraction (6 videos, multiple qualities)
  - **Mp4Upload** ✅ — JsUnpacker (1 video)
  - **Vn-Hls** ✅ — vidnest.io POST /dl (2 direct MP4s)

### ⚠️ In Progress (3 servers)
- **Fm-Hls (Filemoon)**: uses `loadAndExtractVideo` — loads embed frame in WebView, auto-clicks play buttons, polls video.src. Ad blocking via shouldOverrideUrlLoading. May work on-device.
- **Uni**: uses `loadAndExtractVideo` with ad blocking — loads player page, blocks ad redirects, auto-clicks play (up to 4 times), polls video.src. The API response is AES-CBC encrypted; the player JS decrypts it internally.
- **Luf-Mp4 (internal)**: clock.json endpoint at blog.allanime.day returns "error" off-device. Requires cf_clearance cookie (shared with mkissa.to via Cloudflare). Should work on-device after Turnstile is solved.

### Cloudflare Turnstile
- The watch page (`/anime/<id>/p-<N>-sub`) is behind a Cloudflare Turnstile challenge
- The stream API returns `NEED_CAPTCHA` when the cf_clearance cookie is missing/expired
- `solveCloudflare` uses two stages: OkHttp CloudflareInterceptor → WebView native MotionEvent (auto-clicks the Turnstile checkbox)
- The cf_clearance cookie is shared across mkissa.to + blog.allanime.day (same CF zone)

### Dub support
- Dub/sub is handled at the episode level via `preferredAudio` setting (sub/dub)
- Changes `translationType` in the stream API → different sources returned for sub vs dub

## Key file locations (relative to `EXTENSIONS/mkissa/`)

| Path | What |
|---|---|
| `DEV/` | Gradle project (source, stubs module, build config) |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/MKissa.kt` | Main source class (550+ lines) |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/MKissaSettings.kt` | Settings (3 categories, 6 servers) |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/MKissaFilters.kt` | Filters (6 categories) |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/MKissaDto.kt` | kotlinx.serialization DTOs |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/MKissaQueries.kt` | GraphQL query strings |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/MKissaLog.kt` | logcat-only logger |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/extractor/MKissaExtractor.kt` | Video extractor (AES-GCM + XOR + per-server dispatch) |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/extractor/WebViewFetcher.kt` | WebView (warmUp, fetchText, fetchRenderedText, solveCloudflareTurnstile, loadAndExtractVideo) |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/extractor/PlaylistUtils.kt` | HLS m3u8 parser |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/extractor/jsunpacker/` | JsUnpacker + Unbaser (for Mp4Upload) |
| `DEV/src/en/mkissa/src/main/kotlin/.../mkissa/metadata/EpisodeMetadataFetcher.kt` | Anikage + Jikan metadata fetcher |
| `DEV/src/en/mkissa/build.gradle.kts` | Build config + signing config |
| `DEV/common/proguard-rules.pro` | ProGuard rules (keep `...mkissa.**` + `$$serializer`) |
| `DEV/src/en/mkissa/res/mipmap-*/ic_launcher.png` | App icon (5 densities — temporary AI-generated) |
| `APK/` | Built APKs (debug copy) |
| `MEMORY/sites/site-analysis.md` | Site analysis (mkissa.to + api.allanime.day) |
| `MEMORY/session-logs/` | Session logs (17 sessions) |

## Critical build rules (DO NOT VIOLATE — see `MEMORY/guides/04-build-checklist.md`)

1. **extClass** = full path `eu.kanade.tachiyomi.animeextension.en.mkissa.MKissa` (no leading dot).
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK. (Verified: "Stub!" count = 0.)
3. **versionCode** bumps per build; **versionId** stays STABLE at 1.
4. **Video constructor**: ALL 14 named args, `initialized = false`.
5. **Use inherited `client`** (has CloudflareInterceptor + cookieJar).
6. **WebViewFetcher** always created (needed for CF bypass + Fm-Hls + Uni).
7. **ProGuard**: keep ALL `...mkissa.**` classes + `$$serializer` classes.
8. **One change at a time** (project rule §2).

## The 6 servers (from the API)

| # | Server name | Extraction method | Status |
|---|---|---|---|
| 1 | **Mp4** | JsUnpacker (mp4upload.com embed → packed JS → MP4 URL) | ✅ Working |
| 2 | **Ok** | Ok.ru data-options → HLS URL → PlaylistUtils | ✅ Working (6 videos) |
| 3 | **Fm-Hls** | Filemoon embed → loadAndExtractVideo (auto-click play) | ⚠️ In progress |
| 4 | **Uni** | allanime.uns.bio player → loadAndExtractVideo (ad blocking + auto-click) | ⚠️ In progress |
| 5 | **Vn-Hls** | vidnest.io POST /dl → direct MP4 URLs | ✅ Working (2 videos) |
| 6 | **Luf-Mp4** | Internal /apivtwo/clock.json (needs cf_clearance) | ⚠️ CF-dependent |

## Icon preparation

★ Currently using a temporary AI-generated icon. When the user provides a real icon:
1. Auto-crop transparent borders: `img.getbbox()` → crop → resize to 1024×1024
2. Resize to all 5 mipmap densities (48/72/96/144/192)
3. Copy to `public/mkissa-icon.png` for the webpage

See `HOW_TO_BUILD_EXTENSION/05-build-test-and-release.md` §5.1b for the complete Python script.
