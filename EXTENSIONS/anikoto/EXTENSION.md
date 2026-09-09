# AniKoto 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. For deep context, read `MEMORY/` (this folder's
> knowledge base) and the latest `MEMORY/session-logs/`.

---

## Identity (★ DO NOT CHANGE without an ADR)

| Field | Value | Notes |
|---|---|---|
| **Display name** | `AniKoto 180` | Source ID = `MD5("anikoto 180/en/11")` |
| **versionId** | `11` (STABLE) | Bumping orphans saved anime. NEVER change. |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.anikoto180` | Distinguishes from other publishers (s49) |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` | FULL path, no leading dot (applicationId ≠ source package) |
| **versionCode** | `10` | Bump per build |
| **versionName** | `16.10` | |
| **Target site** | `anikototv.to` | |
| **Signing key** | `anikoto-release.jks` (SHA-256 `b467ca64...`, alias `anikoto`) | At `DEV/anikoto-release.jks` — keep secure |

## Build

```bash
# Environment (every new shell)
source /home/z/my-project/.android-env.sh

cd /home/z/my-project/EXTENSIONS/anikoto/DEV

# Release APK (signed, R8 minified — for publishing)
./gradlew :src:en:anikoto:assembleRelease --no-daemon
# → src/en/anikoto/build/outputs/apk/release/aniyomi-en.anikoto180-v16.10-release.apk

# Debug APK (for testing — no R8, easier logs)
./gradlew :src:en:anikoto:assembleDebug --no-daemon
# → src/en/anikoto/build/outputs/apk/debug/aniyomi-en.anikoto180-v16.10-debug.apk
```

Before/after every build, follow `MEMORY/guides/04-build-checklist.md` (project-level — mandatory).

## Current status (v16.10 Build 10, session 52) — ✅ ALL FEATURES WORKING

- **Playback fix (s52)**: megaplay.buzz encrypted its getSources response ("enc" AES-256-CBC blob — playback was 100% broken on megaplay servers). Extension now tries `getSourcesNew` (plaintext, all hosts) first, then `getSources` + AES-256-CBC decrypt of the `enc` blob (`video/MegaPlayDecrypt.kt`, key/IV extracted from megaplay's own `newclient.min.js`). Mirror hosts (megap.shiora.site / megap.mikora.top / s1.akirax.buzz) are WAF-free; segments moved to tiktokcdn with a 252-byte PNG prefix (existing stripper handles it).

- **Preferred domain (s52)**: user-selectable baseUrl — 6 official/verified domains (anikototv.to, anikoto.cz, anikoto.me, anikoto.net, anikototv.se, anikototv.com) in Settings → Playback. Source ID is domain-independent (no orphaned anime); episode URLs are relative so saved episodes follow the domain.
- **Catalog**: popular, latest, search (paginated `/filter?keyword=`, 30/page, filters work with search), filters, details, episode list. Cover images load.
- **Filters** (s51): all 43 genre values verified, sort uses slug format, Year = multi-select checkboxes, Source filter (18 types) added, TV_SHORT type added.
- **Video servers** (all 4 + 1 toggleable): VidPlay-1 (OkHttp), HD-1 (WebView CDN), Vidstream-2 (WebView fallback for WAF), VidCloud-1 (per-stream Referer), Kiwi-Stream (toggleable, default ON).
- **Audio/resolution**: SUB / HSUB / DUB × 1080p / 720p / 360p.
- **Performance** (s51): WebView pre-warming (2-30s saved), parallel variant fetching, parallel PATH A+B — first play 5-10s.
- **Smart Search** (s51): AI-powered via Google AI Search (`smartsearch/` package). Activation phrase (default `?`), OFF by default, modular/removable.
- **Episode metadata** (s34-38): multi-source enrichment — thumbnails (Anikage→AniList→Kitsu→banner→cover), titles (Jikan→Anikage→Kitsu), descriptions (Anikage→Kitsu).
- **Settings**: 4 categories (Playback, Servers, Episode metadata, Smart Search); all dropdowns show "Currently: %s".
- **Fork compatibility**: `getVideoList(SEpisode)` override + `/watch/slug/ep-N#fragment` episode.url format (no DNS errors in legacy-pipeline forks).
- **Promo line**: "Thank the Confused_creature_180" appended to every anime description.
- **Logging**: logcat-only (tag "Anikoto"), no file I/O, no permissions.
- **R8 release builds**: proguard rules keep `$$serializer` classes (prevents serialization crash).
- **Signed release APK**: reproducible (268,142 bytes).

## Key file locations (relative to `EXTENSIONS/anikoto/`)

| Path | What |
|---|---|
| `DEV/` | Gradle project (source, stubs module, build config, keystore) |
| `DEV/src/en/anikoto/src/main/kotlin/.../anikoto/Anikoto.kt` | Main source class |
| `DEV/src/en/anikoto/src/main/kotlin/.../anikoto/video/` | Extractors, MegaPlayDecrypt (enc AES), LocalProxyServer, WebViewFetcher, Models |
| `DEV/src/en/anikoto/src/main/kotlin/.../anikoto/metadata/` | EpisodeMetadataFetcher |
| `DEV/src/en/anikoto/src/main/kotlin/.../anikoto/smartsearch/` | SmartSearch (AI search module) |
| `DEV/src/en/anikoto/build.gradle.kts` | Build config + signing config |
| `DEV/common/proguard-rules.pro` | ProGuard rules (keep `$$serializer`) |
| `DEV/anikoto-release.jks` | Signing keystore (⚠️ sensitive, in .gitignore) |
| `APK/` | Built APKs (debug + release copies) |
| `ANALYSIS/` | Python analysis scripts + chain analysis JSON |
| `MEMORY/` | This extension's knowledge base (see `MEMORY/README.md`) |
| `MEMORY/session-logs/` | Sessions 01-52 |
| `MEMORY/sites/` | Site analysis (anikototv.to: endpoints, servers, audio types, CDN/WAF) |
| `MEMORY/issues-resolutions/` | 4 resolved issues (extclass doubling, stub crash, versionId, episode URL DNS) |
| `MEMORY/modules/` | 7 module docs (00-06: architecture, catalog, details, video, metadata, settings, smart-search) |
| `MEMORY/research/` | AniKoto-specific research (apk-reference analysis, episode metadata plans) |
| `MEMORY/workflow/` | Numbered research workflow (01-07: research→architecture→catalog→video→prefs→build→release) |
| `APK_INFO.md` | Full APK info sheet for current release |

## Critical build rules (DO NOT VIOLATE — see `MEMORY/guides/04-build-checklist.md`)

1. **extClass** = full path `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` (no leading dot).
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE at 11.
4. **Video constructor**: ALL 14 positional args, `initialized=false`.
5. **Use inherited `client`** (has CloudflareInterceptor + cookieJar).
6. **WebViewFetcher** required for WAF-blocked CDNs — don't remove `isWafBlockedHost()`.
7. **Full desktop Chrome UA** — `Chrome/120.0.0.0` (desktop, not mobile).
8. **Per-stream Referer** — each AudioStream has a `referer` field.
9. **ProGuard**: keep ALL `...anikoto.**` classes + `$$serializer` classes.
10. **One change at a time** (project rule §2) — verify each change before the next build.
11. **Megaplay sources (s52)**: use `getSourcesNew` first; `getSources` may return the `enc` AES blob — decrypt via `MegaPlayDecrypt` (constants from megaplay's `newclient.min.js`).
