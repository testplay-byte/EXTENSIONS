# Session 03 — Build, Release, and Deploy

> Date: 2025-07-14 (America/Los_Angeles)
> Branch: `miruro` → merged to `main` → tagged `v1.5.0`
> Task: User verified the corrected analysis and approved building. Build the Miruro 180 extension, ship it via GitHub Actions, and deploy to the live download page.

## What I did

### 1. Built the extension (6 Kotlin source files, ~1,700 lines)
- `Miruro.kt` (969 lines) — main source: pipe API client (base64url JSON `e=` param), catalog (popular/latest/search), 8 filter categories, details, episodes (cross-provider map, 4 sub-types in scanlator), videos (legacy pipeline + include-all-subtypes + fallback-providers), sortVideos, 11 settings, MiruroBrowserFingerprintInterceptor (Chrome 148 headers), cookie farming
- `MiruroExtractor.kt` (290 lines) — pipe response decryption (XOR(PIPE_KEY)+gzip), proxy URL building (vault01/02.ultracloud.cc + FNV-1a + XOR(PROXY_KEY)), HLS wrapping, embed pass-through
- `MiruroDto.kt` (220 lines) — kotlinx.serialization DTOs
- `MiruroFilters.kt` (280 lines) — 8 filter categories
- `MiruroSettings.kt` (124 lines) — 11 preferences
- `MiruroLog.kt` (23 lines) — logcat logger

### 2. Scaffolded the Gradle project
Copied mkissa's DEV structure (stubs module, build files, proguard rules, gradle wrapper, libs.versions.toml) and adapted for miruro: package `...en.miruro180`, extClass full path, versionCode 1, versionId 1, isNsfw true, versionName "16.1".

### 3. Generated a proper app icon
AI-generated purple-to-cyan play/iris motif. Processed into 5 mipmap densities (48/72/96/144/192) + webpage icon (256).

### 4. First CI build → 3 compile errors → fixed
- ext-lib 16 requires `seasonListParse` + `hosterListParse` (abstract) → added stubs that throw UnsupportedOperationException
- `sort()` should be `sortVideos()` → renamed
- `const val MIRROR_DEFAULT = MIRROR_VALUES[0]` — const val can't reference runtime array → changed to explicit arrayOf() + string literal

### 5. Second CI build → ✅ SUCCESS (all 4 extensions compiled on the miruro branch)

### 6. Merged miruro → main (resolved 5 conflicts)
Main had moved on since I branched — anidb + reanime extensions + mkissa v16.19/v16.20 were added in parallel. Resolved conflicts in:
- `.github/workflows/build.yml` — kept all 6 extension build steps
- `.github/workflows/release.yml` — kept all 6 extension release steps
- `MEMORY/EXTENSIONS.md` — kept all 6 rows in the registry
- `src/lib/site-config.ts` — kept all 6 extension cards
- `worklog.md` — kept both HEAD (anidb/reanime/mkissa entries) and miruro (session 01-02) entries

### 7. Tagged v1.5.0 → Release workflow → ✅ SUCCESS
Release v1.5.0 published with 6 APK assets:
- aniyomi-en.anikoto180-v16.9-release.apk (261.9 KB, signed)
- aniyomi-en.animepahe180-v16.10-release.apk (256.1 KB, signed)
- aniyomi-en.mkissa180-v16.17-debug.apk (281.8 KB)
- aniyomi-en.anidb180-v16.1-debug.apk (113.6 KB)
- aniyomi-en.reanime180-v16.1-debug.apk (131.5 KB)
- **aniyomi-en.miruro180-v16.1-debug.apk (192.6 KB)** ← NEW

### 8. Pages redeploy → ✅ SUCCESS
The release workflow triggered a Pages redeploy, which fetched the latest release APKs into `/EXTENSIONS/downloads/` (same-origin, so `<a download>` works).

### 9. Verified the live download page
- URL: https://testplay-byte.github.io/EXTENSIONS/
- 6 extension cards rendered (AniKoto, AnimePahe, MKissa, AniDB, Re:ANIME, Miruro)
- Miruro download link: `https://testplay-byte.github.io/EXTENSIONS/downloads/aniyomi-en.miruro180-v16.1-debug.apk`
- APK download verified: HTTP 200, 197,244 bytes, content-type `application/vnd.android.package-archive`

## Stage Summary

- ★ Miruro 180 extension fully built and shipped (v16.1, build 1, debug APK 192.6 KB).
- ★ Live on the download page: https://testplay-byte.github.io/EXTENSIONS/ — 6 extensions, Miruro card with working download button.
- ★ GitHub Release v1.5.0: https://github.com/testplay-byte/EXTENSIONS/releases/tag/v1.5.0
- ★ All 6 extensions compile in CI (Build workflow passes on main).
- ★ Merge conflicts with parallel anidb/reanime work resolved cleanly — no impact on the other extensions.
- ⏳ Needs on-device testing (cannot verify video playback in sandbox — CF blocks the API; the user will test the APK).

## Honest status

- ✅ Code complete, CI green, release published, live page verified.
- ✅ Download link works (HTTP 200, correct content-type, correct size).
- ❌ NOT live-verified on-device: the pipe API is CF-protected (can't test from sandbox). The user needs to install the APK and test:
  - Catalog (popular/latest/search/filters) — should work (pipe API + AniList fallback)
  - Details + episodes — should work (pipe API returns AniList-rich metadata + per-provider episode lists)
  - Video playback (HLS streams) — should work (proxied through vault01/02.ultracloud.cc)
  - Video playback (embed streams) — passed through as-is for v16.1 (MegaCloud/RapidCloud inline extraction deferred to a future build)
  - Cloudflare bypass — needs on-device WebView (the inherited CloudflareInterceptor + browser-fingerprint interceptor should handle it, but CF's managed challenge evolves)
