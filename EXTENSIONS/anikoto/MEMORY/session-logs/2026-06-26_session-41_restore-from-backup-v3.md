# Session 41 — Restore from `anikoto-backup-2026-06-24-v3.zip` + Environment Verification

> Date: 2026-06-26 · Session #: 41 · Duration: ~medium · Timezone: Asia/Karachi
> Type: RESTORE / SETUP session (no code changes — environment + verification only)

## Goal

Restore the Anikoto Aniyomi-extension project from the v3 backup zip so work can resume.
Verify the build still works end-to-end. Provide the user with a project summary +
issues/improvements review.

## What was done

### 1. Unzipped backup → moved all top-level folders into `/home/z/my-project/`
Extracted `/home/z/my-project/upload/anikoto-backup-2026-06-24-v3.zip` to `/tmp/anikoto-extract/`,
then `mv`'d each top-level entry into the project root (none pre-existed, so no conflicts):

| Moved | Purpose |
|---|---|
| `MEMORY/` | Knowledge base (README, PROJECT_RULES, session-logs 01–38, guides, decisions, research, sites/anikoto, issues-resolutions, ext-lib) |
| `WORKSPACE/` | Dev workspace (WORKFLOW 7-step guide, DEV/ANIKOTO/ Gradle project + :stubs + analysis, APK/) |
| `REFERENCE_HUB/` | 6 read-only reference repos (aniyomi-app, anime-extensions-ref, ext-lib-aniyomiorg, ext-lib-keiyoushi, ext-lib-komikku-new, aniyomi-extensions-lib) |
| `APK/REFERENCE/` | 2 reference APKs (v3 + v16.4) — cross-check only |
| `PROJECT_INDEX.md`, `RESTORE.md`, `STARTUP_PROMPT.md`, `.android-env.sh` | Top-level guides |
| `worklog.md` | Shared multi-session worklog (~150KB, sessions 01–40) |

### 2. Read starting guides (per MEMORY/README.md §4 checklist)
- `MEMORY/README.md` — navigation map + two-tier memory model
- `MEMORY/PROJECT_RULES.md` — 9 non-negotiable rule sections
- `MEMORY/guides/03-android-sdk-install.md` — verified SDK install procedure
- `MEMORY/guides/04-build-checklist.md` — MANDATORY pre/post-build checklist (11 items)
- `MEMORY/session-logs/2026-06-24_session-{35,36,37,38}_*.md` — most recent progress
- `RESTORE.md`, `STARTUP_PROMPT.md`, `PROJECT_INDEX.md` — restore + status docs

### 3. Reinstalled JDK 17 + Android SDK (both missing — binaries don't survive backup)
- **JDK 17 (Temurin 17.0.13+11)** → `/home/z/my-project/.jdk/jdk-17.0.13+11/`
  - Downloaded from `api.adoptium.net` (182MB tarball), extracted, verified
  - `javac 17.0.13` / `openjdk version "17.0.13"` ✅
- **Android SDK** → `/home/z/my-project/ANDROID_SDK/`
  - cmdline-tools 12.0 (with the CRITICAL `cmdline-tools` → `latest` rename)
  - Accepted all 7 licenses
  - Installed `platform-tools` (v37.0.0, adb 1.0.41), `platforms;android-34`, `build-tools;34.0.0`
  - All 3 key binaries verified: `adb`, `android.jar`, `aapt2` ✅
- `.android-env.sh` already in place (sets `JAVA_HOME`, `ANDROID_HOME`, `PATH`) — sourced before every build

### 4. Built the extension to verify
```bash
source /home/z/my-project/.android-env.sh
cd WORKSPACE/DEV/ANIKOTO/DEVELOPMENT_CODE
./gradlew :src:en:anikoto:assembleDebug --no-daemon
```
- **BUILD SUCCESSFUL** (first build ~90s: Gradle 8.14.3 dependency resolution + Kotlin 2.2.21 compile + R8/proguard release-style minify on debug)
- APK: `src/en/anikoto/build/outputs/apk/debug/aniyomi-en.anikoto-v16.25-debug.apk` (157,724 bytes ≈ 158KB ✅ matches RESTORE.md expectation)
- MD5: `5eab92f8d629807d26b291e1599b8e04`

### 5. Ran the full 11-item build checklist (`guides/04`) — ALL PASS
| # | Check | Result |
|---|---|---|
| 1 | `extClass = ".Anikoto"` (not doubled) | ✅ manifest value = `.Anikoto` |
| 2 | Stubs in `:stubs` module, `compileOnly` | ✅ `stubs/build.gradle.kts` uses `compileOnly` |
| 3 | `versionCode` bumped (25), `versionId` STABLE (11) | ✅ both confirmed in build.gradle.kts + manifest |
| 4 | Manifest placeholders correct | ✅ `${extClass}`, `${nsfw}`, `${versionId}`, `usesCleartextTraffic=true`, `WRITE_EXTERNAL_STORAGE` |
| 5 | `settings.gradle.kts` includes `:stubs` + `:src:en:anikoto` | ✅ |
| 6 | APK filename + badging correct | ✅ `versionCode=25 versionName=16.25`, package `eu.kanade.tachiyomi.animeextension.en.anikoto` |
| 7 | extClass NOT doubled in manifest | ✅ `.Anikoto` |
| 8 | "Stub!" count = 0, no stub class defs in DEX | ✅ 0 "Stub!", 0 `AnimeHttpSource;` defs |
| 9 | Anikoto class IS in DEX | ✅ 570 refs to `anikoto/Anikoto` |
| 10 | 5 icon densities | ✅ mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi |
| 11 | Copy APK to both `WORKSPACE/APK/` + `WORKSPACE/DEV/ANIKOTO/APK/` | ✅ done |

DEX also confirmed presence of: `EpisodeMetadataFetcher` (1227 refs), `WebViewFetcher` (255 refs),
`EpisodeMetadataFetcher$JikanEpisode` class, and all 4 metadata URLs (`api.jikan.moe/v4/anime/`,
`graphql.anilist.co`, `anikage.cc/api/media/anime/`, `kitsu.app/api/edge/anime/`).

### 6. Comprehensive architecture survey (dispatched Explore agent — see worklog entry `7-survey`)
Mapped the full project: WORKSPACE tree, MEMORY knowledge base (8 research docs, 3 ADRs,
10 site-analysis docs, 3 issues-resolutions, 3 ext-lib docs, 4 guides), REFERENCE_HUB (6 repos),
all 11 extension source files (~2882 LOC), build system (settings/root/extension/stubs gradle +
libs.versions.toml), 27 stub files, and the 2 reference APKs.

## Current verified state

- ✅ Environment fully restored: JDK 17 + Android SDK + env script all functional
- ✅ Extension builds clean: `./gradlew :src:en:anikoto:assembleDebug` → 158KB APK
- ✅ All 11 build-checklist items pass
- ✅ APK copied to both canonical locations
- ✅ Source code matches session-38 state (v16.25, versionId=11 STABLE, all 4 metadata sources present)
- ✅ `MEMORY/TEMPORARY_MEMORY/` is clean (only README.md — no open threads)

## Issues found during restore (not code bugs — process gaps)

### Issue A — Session logs 39 & 40 are missing from the backup
- `STARTUP_PROMPT.md` and `RESTORE.md` both claim "session 40, v16.25" and reference sessions 37–40
- `MEMORY/session-logs/` only contains sessions 01–38 (latest = `2026-06-24_session-38_fix-anikage-cors-jikan.md`)
- `build.gradle.kts` line 14 comment confirms session 40 happened: `"bumped from 24 → 25 (session 40: improve settings descriptions + Kiwi-Stream mapper logging)"`
- **Impact:** violates PROJECT_RULES §4 ("Session logs are mandatory... zero context loss"). Whatever was changed in sessions 39–40 (settings descriptions + Kiwi-Stream mapper logging) is only reconstructable from the code itself, not from a log.
- **Fix:** when next modifying those areas, retroactively write the missing logs from git/code inspection, OR accept the gap and ensure session 41+ logs are always written before session end.

### Issue B — Backup `RESTORE.md` timezone note is stale
- `RESTORE.md` says `Timezone: America/Los_Angeles`
- Current environment timezone is `Asia/Karachi` (per Z.ai Code config)
- **Impact:** minor — only affects how relative dates in future session logs should be interpreted
- **Fix:** note the timezone change in this + future session logs (done above)

## Code/architecture observations (NOT bugs — candidate improvements for future sessions)

These are observations from reading the source, NOT verified failures. Each would need its own
session with verification before any change (per PROJECT_RULES §2 "one change at a time").

1. **`noCloudflareClient` is dead code** (`Anikoto.kt:94`) — declared `by lazy` but never referenced
   elsewhere. Either remove it or document why it's retained.
2. **`EpisodeMetadataFetcher.fetchString` is a blocking `fun` (not `suspend`)** — uses OkHttp
   `.execute()` synchronously. Works because callers wrap in `withContext(Dispatchers.IO)`, but
   making it `suspend` + `.executeAsync()` would be more idiomatic and prevent accidental main-thread calls.
3. **No Jikan rate-limit handling** — Jikan v4 free tier is ~3 req/s sustained / 60 req/min.
   `fetchJikanEpisodes` does a single OkHttp GET with no retry-on-429. Browsing many anime
   quickly could hit 429 → empty titles. A 1-retry-with-backoff on HTTP 429 would harden it.
4. **WebView single-lock serialization** (`WebViewFetcher.kt:42` `fetchLock`) — all `fetchText` /
   `fetchBytes` / `postJson` calls serialize through one lock. During episode-metadata fetch this
   means AniList POST + Kitsu GET run sequentially. For a single anime this is fine (~1s total),
   but it's the main metadata-latency bottleneck.
5. **No generic OkHttp→WebView fallback for non-WAF hosts** — `fetchString` (extractors) only
   falls back to WebView for hosts in `isWafBlockedHost()`. If a new CDN starts 403-ing OkHttp,
   the fetch fails silently. A "403-once → retry via WebView" generic fallback would be more
   resilient (at the cost of one extra failed OkHttp call).
6. **`LocalProxyServer.MAX_CACHE_ENTRIES = 200` is hardcoded** — fine for typical ~143-segment
   episodes, but very long content (movies, 1hr+ specials) could exceed it and evict needed
   segments. Making it a constructor arg or sizing from the playlist length would be safer.
7. **Override stubs (`hosterListParse`, `videoListParse`, `episodeListParse`, `seasonListParse`)
   return empty/throw** — correct for ext-lib v16 (we override the suspend `get*` methods
   directly), but a one-line `// ext-lib v16: suspend get* override replaces this` comment on
   each would help future readers.

None of these are urgent. The extension is in a stable, working state (v16.25, all features
user-confirmed per STARTUP_PROMPT.md §2).

## What's next

Awaiting user instructions. The extension is built and verified. Possible directions:
- Address any of the candidate improvements above (one per session, with verification)
- New feature work (user to specify)
- Site-structure changes if `anikototv.to` updates its API
- Reconstruct missing session logs 39–40 from code/git history

## Files touched this session

- Created: `MEMORY/session-logs/2026-06-26_session-41_restore-from-backup-v3.md` (this file)
- Created: `/home/z/my-project/.jdk/jdk-17.0.13+11/` (JDK 17 install)
- Created: `/home/z/my-project/ANDROID_SDK/` (Android SDK install)
- Built: `WORKSPACE/DEV/ANIKOTO/DEVELOPMENT_CODE/src/en/anikoto/build/outputs/apk/debug/aniyomi-en.anikoto-v16.25-debug.apk`
- Copied: APK → `WORKSPACE/APK/` + `WORKSPACE/DEV/ANIKOTO/APK/`
- Appended: worklog entry to `/home/z/my-project/worklog.md`
- No source code changes (restore + verify only)
