# AnimePahe — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. Items marked **[ANALYSIS]** must be determined during
> Step 1 (website analysis) — do NOT guess them. Follow
> [`../HOW_TO_BUILD_EXTENSION/README.md`](../HOW_TO_BUILD_EXTENSION/README.md) for the build procedure.

---

## Identity

> ⚠️ **This extension is NOT yet built.** The fields below are the initial plan / placeholders.
> Several must be **verified against the live site** during Step 1 analysis before being finalized.
> Changing `versionId` AFTER the extension is published orphans saved anime — pick it once and keep it.

| Field | Value | Notes |
|---|---|---|
| **Display name** | `AnimePahe` | Source ID = `MD5("<name> <lang>/<versionId>")` — pick the name carefully (case-sensitive). **[ANALYSIS]** confirm the site's own self-name casing. |
| **versionId** | `1` (planned, STABLE once published) | Start at 1. NEVER bump after publish (orphans saved anime). |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.animepahe` | Standard convention. |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.animepahe.Animepahe` | **[ANALYSIS]** decide class name casing (Animepahe vs AnimePahe — match Kotlin class conventions). Use FULL path if applicationId ≠ source package (see [`../HOW_TO_BUILD_EXTENSION/common-pitfalls.md`](../HOW_TO_BUILD_EXTENSION/common-pitfalls.md) §extClass). |
| **versionCode** | `1` (planned) | Bump per build. |
| **versionName** | `16.1` (planned) | Convention: `16.<versionCode>` for ext-lib 16. |
| **Target site** | `animepahe.ru` | **[ANALYSIS]** verify the CURRENT live domain — animepahe has rotated domains (.com, .org, .ru, .app). Confirm with a real browser before scraping. |
| **Language** | `en` | English UI. **[ANALYSIS]** confirm site language. |
| **Is NSFW** | `false` | **[ANALYSIS]** confirm. |
| **Signing key** | `animepahe-release.jks` (TBD) | Generate a NEW keystore for this extension (per-extension signing — see [`../HOW_TO_BUILD_EXTENSION/05-build-test-and-release.md`](../HOW_TO_BUILD_EXTENSION/05-build-test-and-release.md)). Do NOT reuse AniKoto's. |

## Build

> The Gradle project does NOT exist yet — it will be scaffolded in Step 5 (build). The commands
> below show what they WILL look like once `DEV/` is set up.

```bash
# Environment (every new shell)
source /home/z/my-project/.android-env.sh

cd /home/z/my-project/EXTENSIONS/animepahe/DEV   # (after scaffolding)

# Release APK (signed, R8 minified — for publishing)
./gradlew :src:en:animepahe:assembleRelease --no-daemon
# → src/en/animepahe/build/outputs/apk/release/aniyomi-en.animepahe-v16.1-release.apk

# Debug APK (for testing — no R8, easier logs)
./gradlew :src:en:animepahe:assembleDebug --no-daemon
# → src/en/animepahe/build/outputs/apk/debug/aniyomi-en.animepahe-v16.1-debug.apk
```

Before/after every build, follow `MEMORY/guides/04-build-checklist.md` (project-level — mandatory).

## Current status — ✅ ALL FEATURES WORKING (v16.10, build 10)

All 5 steps are complete. Video playback works (Kwik HLS extraction via JsUnpacker). The extension
is functional end-to-end — popular, search, filters, details, episodes, metadata enrichment, and
video playback all work. Ready for finalization (icon, release build).

- [x] **Step 1 — Analyze the website** — site analysis in `MEMORY/sites/site-analysis.md`
- [x] **Step 2 — Catalog** — popularAnime + latestUpdates (JSON API), searchAnime (JSON API + HTML browse), getFilterList (genre/theme/demographic/year/season), animeDetailsParse (HTML)
- [x] **Step 3 — Details + episodes** — getEpisodeList (JSON API + recursive pagination + multi-season renumbering + metadata enrichment)
- [x] **Step 4 — Playback** — getHosterList (Kwik HLS extraction via JsUnpacker) ✅ video playback confirmed working by user
- [x] **Step 5 — Build, test** — debug APK builds (351 KB). Release signing pending.

### Build verification (v16.10 debug)
- ✅ package: `eu.kanade.tachiyomi.animeextension.en.animepahe180`
- ✅ extClass: `eu.kanade.tachiyomi.animeextension.en.animepahe.AnimePahe` (full path)
- ✅ versionCode: 10, versionName: 16.10, versionId: 1 (STABLE)
- ✅ nsfw: 0
- ✅ "Stub!" count: 0 (stubs compileOnly, NOT in APK)
- ✅ AnimePahe + KwikExtractor + JsUnpacker + EpisodeMetadataFetcher + all DTOs in DEX
- ✅ Build: SUCCESSFUL, warning-free
- ✅ APK: `EXTENSIONS/animepahe/APK/aniyomi-en.animepahe180-v16.10-debug.apk` (351 KB)
- ✅ Video playback confirmed working on-device by the user

## Key file locations (relative to `EXTENSIONS/animepahe/`)

| Path | What | Status |
|---|---|---|
| `EXTENSION.md` | This file — identity, build, status | ✅ created (placeholders) |
| `DEV/` | Gradle project (source, stubs, build config, keystore) | ⏳ to scaffold in Step 5 |
| `APK/` | Built APKs (debug + release) | ⏳ empty |
| `ANALYSIS/` | Per-extension analysis scripts + chain analysis | ⏳ empty |
| `MEMORY/` | This extension's knowledge base | ✅ created (empty subfolders) |
| `MEMORY/sites/` | Target-site analysis (URLs, servers, audio types, CDN/WAF) | ⏳ to fill in Step 1 |
| `MEMORY/session-logs/` | One log per working session | ⏳ empty |
| `MEMORY/issues-resolutions/` | Resolved issues (symptom → cause → fix) | ⏳ empty |
| `MEMORY/modules/` | Code module docs (architecture, catalog, video, etc.) | ⏳ to fill as code is written |
| `MEMORY/research/` | Extension-specific research | ⏳ empty |
| `MEMORY/workflow/` | Numbered research workflow (01-07) | ✅ created (empty step dirs) |
| `MEMORY/TEMPORARY_MEMORY/` | Raw/unverified notes | ⏳ empty |

## Critical rules (project-level — see `MEMORY/guides/04-build-checklist.md`)

1. **Verify before trusting** (rule §1) — use a real browser (agent-browser), test ALL servers from ALL endpoints. Never trust a single API response.
2. **One change at a time** (rule §2) — verify each change before the next build. No bundled multi-fix changes.
3. **Don't force anything** (rule §2) — if you can't handle something, tell the user. Revert rather than break.
4. **Ask the user when stuck** — see [`../HOW_TO_BUILD_EXTENSION/00-philosophy-and-rules.md`](../HOW_TO_BUILD_EXTENSION/00-philosophy-and-rules.md) §when-to-ask.
5. **extClass** — full path if applicationId ≠ source package; otherwise `.ClassName`. (AniKoto uses full path.)
6. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
7. **versionCode** bumps per build; **versionId** stays STABLE once published.
8. **Video constructor**: ALL positional args, `initialized=false`.
9. **Use inherited `client`** (CloudflareInterceptor + cookieJar).
10. **ProGuard**: keep ALL extension classes + `$$serializer` classes.
