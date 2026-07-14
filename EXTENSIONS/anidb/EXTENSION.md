# AniDB 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. For deep context, read `MEMORY/` (this folder's
> knowledge base) and the latest `MEMORY/session-logs/`.

---

## Identity (★ DO NOT CHANGE without an ADR)

| Field | Value | Notes |
|---|---|---|
| **Display name** | `AniDB 180` | Source ID = `MD5("anidb 180/en/1")` |
| **versionId** | `1` (STABLE) | Bumping orphans saved anime. NEVER change after publish. |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.anidb180` | applicationId = namespace + applicationIdSuffix |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.anidb.AniDB` | FULL path, no leading dot (applicationId ≠ source package) |
| **versionCode** | `1` | Bump per build |
| **versionName** | `16.1` | |
| **Target site** | `anidb.app` | Laravel + Alpine.js; JSON APIs for episodes/languages |
| **Is NSFW** | `true` | Site has Erotica + Hentai genres |
| **Signing key** | `anidb-release.jks` (NOT YET GENERATED) | Generate in Step 5 (release). Debug builds don't need it. |

## Build

```bash
# Environment (every new shell)
source /home/z/my-project/.android-env.sh

cd /home/z/my-project/EXTENSIONS/anidb/DEV

# Debug APK (for testing — no R8, no signing needed)
./gradlew :src:en:anidb:assembleDebug --no-daemon
# → src/en/anidb/build/outputs/apk/debug/aniyomi-en.anidb180-v16.1-debug.apk

# Release APK (signed, R8 — requires keystore; NOT YET GENERATED)
# keytool -genkeypair -keystore anidb-release.jks -alias anidb -keyalg RSA -keysize 2048 -validity 10000 -storepass $KEYSTORE_PASSWORD -keypass $KEYSTORE_PASSWORD -dname "CN=AniDB 180, O=Confused_Creature_180, C=US"
# ./gradlew :src:en:anidb:assembleRelease --no-daemon
```

Before/after every build, follow `MEMORY/guides/04-build-checklist.md` (project-level — mandatory).

## Current status (v16.1 Build 1) — 🚧 INITIAL BUILD (debug only)

### ✅ Working
- **Catalog**: popular (Trending), latest (Latest Updated), search (`/browse?q=` composes with filters + pagination)
- **Filters**: Sort (8 options), Type (7), Status (3), Season (5), Year (2026→1977), Genres (21 multi-select), Themes (24 multi-select) — all compose together
- **Details**: title, description, cover, type, status, season, aired, duration, studios, genres, themes
- **Episodes**: via JSON API (`/api/frontend/anime/<id>/episodes`) — full list in one response, filler flag marked
- **Video playback — 1 server (site's own HLS host)**:
  - Sub (Japanese) + Dub (English) via languages API → embed page → JW Player m3u8 → HLS quality variants
  - No token crypto, no PNG wrapping, no WebView needed
  - Qualities: 1080p + 720p (varies per anime; master.m3u8 parsed by PlaylistUtils)
- **Settings**: Preferred quality (1080p/720p/480p/360p), Preferred audio (Sub/Dub), Mark filler toggle
- **Cloudflare**: inherited `client` (CloudflareInterceptor + desktop UA) gets HTTP 200 — no Turnstile for OkHttp

### ⚠️ Needs on-device testing
- Video playback end-to-end (embed page fetch → m3u8 extraction → HLS playback)
- Cloudflare behavior on-device (should work via interceptor; no Turnstile expected)
- Pagination "Next" detection across different browse results

## Key file locations (relative to `EXTENSIONS/anidb/`)

| Path | What |
|---|---|
| `DEV/` | Gradle project (source, stubs module, build config) |
| `DEV/src/en/anidb/src/main/kotlin/.../anidb/AniDB.kt` | Main source class (catalog, details, episodes, video dispatch) |
| `DEV/src/en/anidb/src/main/kotlin/.../anidb/AniDBDto.kt` | kotlinx.serialization DTOs (EpisodesResponse, LanguagesResponse) |
| `DEV/src/en/anidb/src/main/kotlin/.../anidb/AniDBFilters.kt` | Filters (Sort, Type, Status, Season, Year, Genres, Themes) |
| `DEV/src/en/anidb/src/main/kotlin/.../anidb/AniDBSettings.kt` | Settings (quality, audio, filler marking) |
| `DEV/src/en/anidb/src/main/kotlin/.../anidb/AniDBLog.kt` | logcat-only logger |
| `DEV/src/en/anidb/src/main/kotlin/.../anidb/extractor/AniDBExtractor.kt` | Embed page → m3u8 extractor |
| `DEV/src/en/anidb/src/main/kotlin/.../anidb/extractor/PlaylistUtils.kt` | HLS master.m3u8 → Video list |
| `DEV/src/en/anidb/build.gradle.kts` | Build config + signing config |
| `DEV/common/proguard-rules.pro` | ProGuard rules (keep `...anidb.**` + `$$serializer`) |
| `DEV/src/en/anidb/res/mipmap-*/ic_launcher.png` | App icon (5 densities — temporary AI-generated) |
| `APK/` | Built APKs (debug copy) |
| `MEMORY/sites/site-analysis.md` | Site analysis (anidb.app) |
| `MEMORY/session-logs/` | Session logs |

## Critical build rules (DO NOT VIOLATE — see `MEMORY/guides/04-build-checklist.md`)

1. **extClass** = full path `eu.kanade.tachiyomi.animeextension.en.anidb.AniDB` (no leading dot).
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE at 1.
4. **Video constructor**: named args (`videoUrl`, `videoTitle`, `headers`, `subtitleTracks`).
5. **Use inherited `client`** (CloudflareInterceptor + cookieJar) — desktop Chrome UA.
6. **ProGuard**: keep ALL `...anidb.**` classes + `$$serializer` classes.
7. **One change at a time** (project rule §2).

## The video pipeline (1 server)

| Server | Extraction method | Status |
|---|---|---|
| **AniDB HLS** (hls.anidb.app) | embed page → regex m3u8 → PlaylistUtils HLS parser | ✅ Implemented (needs on-device test) |

Audio (sub/dub) is handled at the language level: the `/api/frontend/episode/<epId>/languages` API returns one embed URL per audio language (jpn=Sub, eng=Dub). Each is extracted separately.

## Icon preparation

★ Currently using a temporary AI-generated icon. When the user provides a real icon:
1. Auto-crop transparent borders: `img.getbbox()` → crop → resize to 1024×1024
2. Resize to all 5 mipmap densities (48/72/96/144/192)
3. Copy to `public/anidb-icon.png` for the webpage

See `HOW_TO_BUILD_EXTENSION/05-build-test-and-release.md` §5.1b for the complete Python script.
