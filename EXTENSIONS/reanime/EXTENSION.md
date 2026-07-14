# Re:ANIME 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. For deep context, read `MEMORY/` (this folder's
> knowledge base) and the latest `MEMORY/session-logs/`.

---

## Identity (★ DO NOT CHANGE without an ADR — fields marked [ANALYSIS] are provisional)

| Field | Value | Notes |
|---|---|---|
| **Display name** | `Re:ANIME 180` | "180" suffix matches AniKoto/AnimePahe/MKissa convention |
| **versionId** | `1` (STABLE) | Bumping orphans saved anime. NEVER change after publish. |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.reanime180` | applicationId = namespace + applicationIdSuffix |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.reanime.Reanime` | FULL path, no leading dot (applicationId ≠ source package) |
| **versionCode** | `1` (initial) | Bump per build |
| **versionName** | `16.1` | Must start with "16." (ext-lib v16 loader rule) |
| **Target site** | `reanime.to` | SvelteKit frontend on `https://reanime.to/api/v1/` REST API |
| **Video host** | `flixcloud.cc` | FlixHQ-style; HLS via single-use `/api/m3u8/<token>` |
| **Signing key** | `reanime-release.jks` (NOT YET GENERATED) | Generate in Step 5 (release). Debug builds don't need it. |

## Build

```bash
# Environment (every new shell)
source /home/z/my-project/.android-env.sh

cd /home/z/my-project/EXTENSIONS/reanime/DEV

# Debug APK (for testing — no R8, no signing needed)
./gradlew :src:en:reanime:assembleDebug --no-daemon
# → src/en/reanime/build/outputs/apk/debug/aniyomi-en.reanime180-v16.1-debug.apk

# Release APK (signed, R8 — requires keystore; NOT YET GENERATED)
# keytool -genkeypair -keystore reanime-release.jks -alias reanime -keyalg RSA -keysize 2048 -validity 10000 -storepass $KEYSTORE_PASSWORD -keypass $KEYSTORE_PASSWORD -dname "CN=Re:ANIME 180, O=Confused_Creature_180, C=US"
# ./gradlew :src:en:reanime:assembleRelease --no-daemon
```

Before/after every build, follow `MEMORY/guides/04-build-checklist.md` (project-level — mandatory).

## Current status — 🚧 STEP 1 (site analysis) — AWAITING USER VERIFICATION

### ✅ Analyzed & verified (see `MEMORY/sites/site-analysis.md`)
- **Site**: reanime.to — SvelteKit SSR frontend + `api/v1/` REST backend, Cloudflare Turnstile protected.
- **Catalog/Search**: `GET /api/v1/search?q=&limit=&offset=&year=&season=&format=` (PUBLIC).
  Empty `q` → popular list. `genres`/`sort` IGNORED. Browse endpoints → 401 (auth-gated).
- **Details**: SSR'd (`/anime/<slug>-<6id>`). Metadata: title, alt-titles, synopsis, genres,
  type, episodes, duration, status, dates, season, studios, subbed/dubbed counts, related.
- **Episodes**: `GET /api/v1/anime/<anime_id>/episodes?limit=2000` → `{data:[{episodeId:"ep-N", episode_number, title, aired, duration, is_filler, is_recap, site:"MyAnimeList", ...}]}`.
- **Video sources**: `GET /api/flix/<anilist_id>/<ep>` → `{success, servers:[{$id, serverName, dataLink, dataType, softsub}]}`.
- **Servers observed**: HD-1, HD-2 (× sub/dub). dataLink → `https://flixcloud.cc/e/<code>?v=<N>`.
- **Audio types**: **sub, dub** (NO separate hsub — "sub" is hardsub default, `softsub:false`;
  softsub is a player toggle via `skI`/`skO`).
- **Stream**: flixcloud.cc HLS via `GET /api/m3u8/<24hex-single-use-token>`; player uses
  `obfuscated_crypto_data` + `obfuscation_seed` (AniKoto-RC4-style architecture).
- **Source quality**: 1080p (from MKV filenames, e.g. `[Erai-raws] ... [1080p CR WEB-DL]`).

### ⚠️ To be confirmed in Step 8 (video-stream capture)
- Exact HLS variant qualities (1080p/720p/480p/360p).
- Server variety across more titles (HD-3? alternative hosts?).
- m3u8 token derivation (SSR data vs player-JS crypto).
- Subtitle track delivery (WebVTT in m3u8 vs separate).

### Cloudflare
- `reanime.to` AND `flixcloud.cc` are both Cloudflare-protected (Turnstile).
- `cf_clearance` is short-lived (~minutes). All `/api/v1/` + `/api/flix/` endpoints CF-gated.
- Bypass: WebView + native `MotionEvent.dispatchTouchEvent()` (same pattern as MKissa).

## Key file locations (relative to `EXTENSIONS/reanime/`)

| Path | What |
|---|---|
| `DEV/` | Gradle project (source, stubs module, build config) — TO BE CREATED in Step 9 |
| `DEV/src/en/reanime/src/main/kotlin/.../reanime/Reanime.kt` | Main source class — TO BE CREATED |
| `MEMORY/sites/site-analysis.md` | ★ Site analysis (Step 1) — complete, awaiting verification |
| `MEMORY/session-logs/` | Session logs — TO BE WRITTEN |

## Critical build rules (DO NOT VIOLATE — see `MEMORY/guides/04-build-checklist.md`)

1. **extClass** = full path `eu.kanade.tachiyomi.animeextension.en.reanime.Reanime` (no leading dot).
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE at 1.
4. **Video constructor**: ALL 14 named args, `initialized = false`.
5. **Use inherited `client`** (has CloudflareInterceptor + cookieJar).
6. **WebViewFetcher** needed (CF bypass on reanime.to + flixcloud.cc).
7. **ProGuard**: keep ALL `...reanime.**` classes + `$$serializer` classes.
8. **One change at a time** (project rule §2).

## The servers (from `/api/flix/<anilist_id>/<ep>`)

| # | Server name | dataType | dataLink | Status |
|---|---|---|---|---|
| 1 | **HD-1** | sub | `flixcloud.cc/e/<code>?v=1` | ✅ analyzed |
| 2 | **HD-1** | dub | `flixcloud.cc/e/<code>?v=1` | ✅ analyzed |
| 3 | **HD-2** | sub | `flixcloud.cc/e/<code>?v=2` | ✅ analyzed |
| 4 | **HD-2** | dub | `flixcloud.cc/e/<code>?v=2` | ✅ analyzed |

> Server list is read dynamically from `/api/flix/` per episode — the extension should
> not hardcode servers. More servers (HD-3, etc.) may appear for other titles (Step 8).

## Audio versions

| Type | Present | Notes |
|---|---|---|
| **sub** | ✅ | Hardsub default (`softsub:false`); softsub toggle via `skI`/`skO` |
| **dub** | ✅ | English dub |
| **hsub** | ❌ | Not a separate type (sub IS hardsub) |
