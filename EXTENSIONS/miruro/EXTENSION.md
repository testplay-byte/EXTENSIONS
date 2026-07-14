# Miruro 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. For deep context, read `MEMORY/` (this folder's
> knowledge base) and the latest `MEMORY/session-logs/`.

---

## Identity (★ DO NOT CHANGE without an ADR)

| Field | Value | Notes |
|---|---|---|
| **Display name** | `Miruro 180` (internal `name` = `"Miruro.tv"`) | Our "180" convention; site self-names "Miruro.tv" |
| **versionId** | `1` (STABLE) | Bumping orphans saved anime. NEVER change after publish. |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.miruro180` | applicationId = namespace + applicationIdSuffix |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.miruro.Miruro` | FULL path, no leading dot (applicationId ≠ source package) |
| **versionCode** | `1` (initial) | Bump per build |
| **versionName** | `16.1` | Convention: `16.<versionCode>` |
| **Target site** | `https://www.miruro.tv` | Primary mirror (`.to`/`.bz`/`.ru` are fallbacks) |
| **Backend** | Miruro's own pipe API: `/api/secure/pipe?e=<base64url-json>` | XOR(PIPE_KEY)+gzip responses; NOT the public Consumet API (dead) |
| **Is NSFW** | `true` | yuzono reference sets isNsfw=true (Ecchi + isAdult filter) |
| **Signing key** | `miruro-release.jks` (NOT YET GENERATED) | Generate in Step 5 (release). |

## Reference extension (★ READ FIRST when coding)

**`SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/miruro/`** (cloned from `yuzono/anime-extensions`) —
a 2234-line working extension for this exact site. Files:
- `Miruro.kt` — main source (pipe API, catalog, episodes, videos, 15 settings, AniLib fallback)
- `MiruroExtractor.kt` — response decryption (XOR+gzip), proxy URL building, embed routing
- `MiruroDto.kt` — kotlinx.serialization DTOs (SourcesResponseDto, StreamDto, ConfigResponseDto)
- `MiruroFilters.kt` — 8 filter categories (Sort/Genres/Tags/Format/Year/Season/Status/DubLanguage)
- `MiruroBrowserFingerprintInterceptor.kt` — Chrome 148 desktop header shaping (WAF bypass)
- `MiruroUrlActivity.kt` — deep-link share-to-Aniyomi handler

> **Per `PROJECT_RULES.md`**: the reference is a **cross-check only, never a copy source**. We adapt
> its architecture to our ext-lib v16 + project conventions (stubs module, 180 namespace).

## Build

```bash
source /home/z/my-project/.android-env.sh
cd /home/z/my-project/EXTENSIONS/miruro/DEV

# Debug APK
./gradlew :src:en:miruro:assembleDebug --no-daemon
# → src/en/miruro/build/outputs/apk/debug/aniyomi-en.miruro180-v16.1-debug.apk

# Release APK (signed — requires keystore; NOT YET GENERATED)
# keytool -genkeypair -keystore miruro-release.jks -alias miruro -keyalg RSA -keysize 2048 -validity 10000 -storepass $KEYSTORE_PASSWORD -keypass $KEYSTORE_PASSWORD -dname "CN=Miruro 180, O=Confused_Creature_180, C=US"
# ./gradlew :src:en:miruro:assembleRelease --no-daemon
```

Before/after every build, follow `MEMORY/guides/04-build-checklist.md` (mandatory).

## Current status (v16.1 Build 1) — ✅ BUILT, SHIPPED, LIVE (needs on-device testing)

- [x] **Step 1 — Analyze the website** — `MEMORY/sites/site-analysis.md` (CORRECTED after yuzono cross-check)
- [x] **User verification** of the corrected analysis (11 providers, 4 sub-types confirmed)
- [x] **Step 1.5 — Video stream capture analysis** (pipe `sources` response, vault proxy, embed routing)
- [x] **Step 2 — Catalog** (pipe `search/browse` + 8 filter categories)
- [x] **Step 3 — Details + episodes** (pipe `info` + `episodes`, 4 sub-types in scanlator)
- [x] **Step 4 — Playback** (11 providers × 4 sub-types, HLS via vault proxy, embed pass-through)
- [x] **Step 5 — Build, test, release** — v16.1 debug APK built, release v1.5.0 published, live on download page

### Release status
- **GitHub Release**: https://github.com/testplay-byte/EXTENSIONS/releases/tag/v1.5.0
- **APK**: `aniyomi-en.miruro180-v16.1-debug.apk` (192.6 KB, debug)
- **Download page**: https://testplay-byte.github.io/EXTENSIONS/ — Miruro card with working download button
- **CI**: all 6 extensions compile (Build workflow passes on main)

### Key facts (verified from yuzono reference + live probe)
- **Backend**: Miruro's own pipe API (`/api/secure/pipe?e=<base64url-json>`) — NOT Consumet
- **Response crypto**: `x-obfuscated: 2` → base64url → XOR(PIPE_KEY) → gunzip → JSON
- **11 providers**: AnimePahe, Anikoto, AniDao, 9Anime, Moon, Zoro, Pewe, Nun, Bun, Twin, Cog (+Dune/Kuz)
- **4 audio types**: Sub, Dub, Soft Sub (ssub), Hard Sub (h-sub)
- **8 filter categories**: Sort, Genres (18), Tags (~300), Format (7), Year, Season (4), Status (6), Dub Language (17)
- **Stream types**: hls (m3u8) + embed (MegaCloud/RapidCloud/OmniEmbed)
- **Qualities**: 1080p/720p/480p/360p (+ Highest Available)
- **Proxy**: vault01/02.ultracloud.cc (FNV-1a hash selection, XOR(PROXY_KEY) obfuscation)
- **CF protection**: managed challenge + WAF rule → needs CloudflareInterceptor + MiruroBrowserFingerprintInterceptor + cookie farming
- **AniList fallback**: graphql.anilist.co via `aniyomi.lib.anilib` when pipe fails

## The 11 providers (servers)

| # | Alias | Display name | Default capabilities | Type |
|---|---|---|---|---|
| 1 | `kiwi` | AnimePahe | Sub, Download | native (DEFAULT) |
| 2 | `bee` | Anikoto | Sub, Soft Sub | native |
| 3 | `bonk` | AniDao | Soft Sub, Download | native |
| 4 | `ally` | 9Anime | Sub, Download | native |
| 5 | `moo` | Moon | Sub, Download | native |
| 6 | `hop` | Zoro | Soft Sub | native |
| 7 | `pewe` | Pewe | Hard Sub | native |
| 8 | `nun` | Nun | Hard Sub | embed |
| 9 | `bun` | Bun | Soft Sub | embed |
| 10 | `twin` | Twin | Soft Sub | embed |
| 11 | `cog` | Cog | Sub / Hard Sub | embed |

(+ `dune` → Dune, `kuz` → Kuz may appear in live config.) Capabilities per provider come from the
live `config` response (`ConfigResponseDto.streaming` map).

## Required libs (from yuzono `build.gradle`)

```
lib:omniembedextractor   — generic embed extractor (kwik, mp4upload, doodstream, ...)
lib:anilib               — AniList GraphQL client (fallback for catalog/details)
lib:m3u8server           — local m3u8 proxy for header-gated CDNs
lib:cloudflareinterceptor — CF managed challenge solver (WebView)
lib:megacloudextractor   — Zoro-style MegaCloud embed extractor
lib:rapidcloudextractor  — Zoro/ChillX-family RapidCloud embed extractor
```

## Critical build rules (project-level — see `MEMORY/guides/04-build-checklist.md`)

1. **extClass** = full path `eu.kanade.tachiyomi.animeextension.en.miruro.Miruro` (no leading dot).
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE at 1.
4. **Video constructor**: ALL 14 named args, `initialized = false`.
5. **Use inherited `client`** + add `MiruroBrowserFingerprintInterceptor` (WAF bypass).
6. **ProGuard**: keep ALL `...miruro.**` classes + `$$serializer` classes.
7. **One change at a time** (project rule §2).
8. **Cookie farming**: `ensureBaseVisit()` + `ensureWatchPageVisited(anilistId)` before pipe calls.

## Crypto keys (from yuzono `Miruro.kt` companion — also in miruro.tv frontend JS)

```
PIPE_KEY  = hex "71951034f8fbcf53d89db52ceb3dc22c"  (XOR key for pipe response decryption)
PROXY_KEY = hex "a54d389c18527d9fd3e7f0643e27edbe"  (XOR key for proxy URL obfuscation)
```
