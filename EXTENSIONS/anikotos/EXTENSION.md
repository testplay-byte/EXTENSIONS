# AniKoto S 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. AniKoto S is a **slimmed variant** of AniKoto 180 —
> same catalog/details/episodes/video pipeline, but **WITHOUT smart search** and **WITHOUT
> episode-metadata enrichment**. Built as a distinct extension (distinct package, extClass,
> source ID) so both can be installed side by side.

---

## Identity (★ DO NOT CHANGE without an ADR)

| Field | Value | Notes |
|---|---|---|
| **Display name** | `AniKoto S 180` | Source ID = `MD5("anikoto s 180/en/1")` — distinct from AniKoto 180 |
| **versionId** | `1` (STABLE) | Bumping orphans saved anime. NEVER change after publish. |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.anikotos180` | applicationId = namespace + `en.anikotos180` |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.anikotos.AnikotoS` | FULL path, no leading dot (applicationId ≠ source package) |
| **versionCode** | `1` | Bump per build |
| **versionName** | `16.1` | |
| **Target site** | `anikototv.to` | Same site as AniKoto 180 |
| **Signing key** | `anikotos-release.jks` (⚠️ not generated yet — debug only for now) | At `DEV/anikotos-release.jks` once generated |

## Build

```bash
# No local Android SDK needed — builds run in GitHub Actions.
# Locally (if SDK is set up): source .android-env.sh first.

cd EXTENSIONS/anikotos/DEV

# Debug APK (for testing — no keystore needed)
./gradlew :src:en:anikotos:assembleDebug --no-daemon
# → src/en/anikotos/build/outputs/apk/debug/aniyomi-en.anikotos180-v16.1-debug.apk

# Release APK (signed — will FAIL until anikotos-release.jks is generated + KEYSTORE_PASSWORD set)
./gradlew :src:en:anikotos:assembleRelease --no-daemon
```

CI builds a debug APK on every push to `main` (`.github/workflows/build.yml`) and on the
`anikoto-s` branch via `workflow_dispatch`. Release builds are triggered by a `v*` tag push
(`.github/workflows/release.yml`) — AniKoto S is built debug-only there until a keystore exists.

## What was removed vs AniKoto 180

AniKoto S is a copy of AniKoto 180 with these two features **completely removed**:

1. **Smart Search** (the AI-powered Google AI Search integration, session 51):
   - Deleted `smartsearch/SmartSearch.kt` (entire package)
   - Removed `smartSearch` field, `getSearchAnime()` override, `showToast()` helper,
     `smartSearchEnabled`/`smartSearchPhrase` getters, and the `getFilterList()` warm-up
   - Removed the "Smart Search" settings category + activation-phrase preference + keys
   - Removed the Google-WebView code from `video/WebViewFetcher.kt` (`warmUpGoogleWebView`,
     `ensureGoogleWebView`, `fetchRenderedText`, `destroyGoogleWebView`, `parseJsStringResult`,
     the `googleWebView`/`googleLock`/`googleGenCounter` fields, the `destroyGoogleWebView()`
     call in `destroy()`, and the now-unused `AtomicInteger` import)

2. **Episode-metadata enrichment** (the Anikage/AniList/Kitsu/Jikan thumbnail/title/description
   fetcher, sessions 35-39):
   - Deleted `metadata/EpisodeMetadataFetcher.kt` (entire package)
   - Removed `metadataFetcher` field, `enrichEpisodesWithMetadata()` function + its call in
     `getEpisodeList`, and the `loadThumbnails`/`loadTitles`/`loadDescriptions` getters
   - Removed the "Episode metadata" settings category + its 3 toggle preferences + keys
   - Removed the metadata-only `postJson()` method + `buildPostJsonJs()` from
     `video/WebViewFetcher.kt` (only EpisodeMetadataFetcher used them)

> **Note:** `EpisodeMeta.kt` is **NOT** metadata-fetching code — it is the URL-safe encoder for
> `SEpisode.url` (slug + epNum + malId + timestamp + dataIds + sub/dub flags). The video pipeline
> (`getHosterList`, `getVideoList`, `getEpisodeUrl`) depends on it, so it is KEPT.

## What is identical to AniKoto 180

- Catalog: popular (`/most-viewed`), latest (`/latest-updated`), search (`/filter?keyword=`),
  filters, details, episode list (RC4 vrf + `EpisodeMeta` encoding).
- Video servers: VidPlay-1 (OkHttp), HD-1 (WebView CDN), Vidstream-2 (WebView fallback),
  VidCloud-1 (per-stream Referer), Kiwi-Stream (toggleable, default ON).
- Audio/resolution: SUB / HSUB / DUB × 1080p / 720p / 360p.
- LocalProxyServer segment prefetching + WebView pre-warming in `getEpisodeList`.
- Fork compatibility: `getVideoList(SEpisode)` override + `/watch/slug/ep-N#fragment` URL format.
- Promo line: "Thank the Confused_creature_180" appended to every anime description.
- Logging: logcat-only (tag "AnikotoS"), no file I/O.
- Settings: 2 categories (Playback, Servers).

## Current status (v16.1 Build 1) — 🚧 IN PROGRESS

- First build of the slimmed variant. Debug-only (no release keystore yet).
- Build verified via GitHub Actions (CI green).
- On-device playback not yet tested (same caveat as all extensions — requires a real device).

## Key file locations (relative to `EXTENSIONS/anikotos/`)

| Path | What |
|---|---|
| `DEV/` | Gradle project (source, stubs module, build config) |
| `DEV/src/en/anikotos/src/main/kotlin/.../anikotos/AnikotoS.kt` | Main source class |
| `DEV/src/en/anikotos/src/main/kotlin/.../anikotos/video/` | Extractors, LocalProxyServer, WebViewFetcher, Models |
| `DEV/src/en/anikotos/build.gradle.kts` | Build config + signing config (anikotos-release.jks) |
| `DEV/common/proguard-rules.pro` | ProGuard rules (keep `...anikotos.**` + `$$serializer`) |
| `DEV/settings.gradle.kts` | `rootProject.name = "AnikotoS-Anime"`, `include(":src:en:anikotos")` |

## Critical build rules (DO NOT VIOLATE)

1. **extClass** = full path `eu.kanade.tachiyomi.animeextension.en.anikotos.AnikotoS` (no leading dot).
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE at 1.
4. **Video constructor**: ALL 14 positional args, `initialized=false`.
5. **Use inherited `client`** (has CloudflareInterceptor + cookieJar).
6. **WebViewFetcher** required for WAF-blocked CDNs.
7. **ProGuard**: keep ALL `...anikotos.**` classes + `$$serializer` classes.
