# AnimeKhor 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. Deep context: `MEMORY/` (knowledge base),
> `MEMORY/sites/site-analysis.md` (live-verified site intel).
>
> **★ New AI agent?** Read the project-wide entry point first:
> `EXTENSIONS/anikoto/AGENT_ONBOARDING.md`, then this file.

---

## Identity (★ DO NOT CHANGE once published)

| Field | Value | Notes |
|---|---|---|
| **Display name** | `AnimeKhor 180` | Source ID = `MD5("animekhor 180/en/1")` |
| **versionId** | `1` (STABLE once published) | Bumping orphans saved anime. NEVER change after first release. |
| **Package (source)** | `eu.kanade.tachiyomi.animeextension.en.animekhor` | |
| **applicationId** | `...animeextension.en.animekhor180` | "180" publisher suffix (same pattern as anikoto180) |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.animekhor.AnimeKhor` | FULL path, no leading dot |
| **versionCode** | `1` | Bump per build |
| **versionName** | `16.1` | = `16.<extVersionCode>` (ext-lib 16 loader constraint) |
| **Target site** | `https://animekhor.org` | former animekhor.xyz 301-redirects here (verified 2026-09-13) |
| **Signing** | none yet — **debug APK only** | release keystore needed before any publish |
| **Status** | 🚧 BRANCH `ext/animekhor` — NOT merged to main, NOT published | user will test the debug APK first |

## Build (⚠️ GitHub Actions ONLY — never install the Android SDK in a sandbox)

```bash
# This extension lives on branch ext/animekhor (not merged to main).
# CI: release.yml has a "Build AnimeKhor (debug)" step → artifact test-build-apks-<tag>.
curl -s -X POST -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/testplay-byte/EXTENSIONS/actions/workflows/release.yml/dispatches \
  -d '{"ref":"ext/animekhor","inputs":{"tag":"v16.1-ak-test1","publish":"false"}}'
# → watch https://github.com/testplay-byte/EXTENSIONS/actions → artifact test-build-apks-v16.1-ak-test1
```

Before every push: **tree-sitter Kotlin parse** every touched `.kt` (recipe:
`EXTENSIONS/anikoto/MEMORY/workflow/sandbox/TOOLS.md` §5). No local gradle builds.

## Current status (session animekhor-01, 2026-09-13) — 🚧 first build, awaiting user test

- **Site analysis**: complete + live-verified through the page_reader channel (Cloudflare blocks
  direct sandbox access — see `MEMORY/sites/site-analysis.md` for the full method).
- **Catalog**: popular + latest + filter browse via `/anime/?page=&order=&…` (selectors verified).
- **Search**: WordPress `?s=` search mapped episode-posts → series (unique site quirk, verified).
- **Filters**: dynamic genres (218) + studios (119) from the /anime/ form; static status/type/sub/order.
- **Details + episodes**: verified selectors (`h1.entry-title`, `div.eplister`, dates "MMMM d, yyyy").
- **Video**: tolerant per-mirror dispatch (AniKoto pattern). 8 hosters wired:
  - ✅ live-verified chains: Dailymotion, Ok.ru, Vidara (`POST /api/stream`), Abyss (`datas` → enc-dec.app)
  - ported upstream logic (blocked from sandbox, battle-tested code): StreamWish, VidHide (ahvsh/sbsonic), Dood (d000d), Rumble
  - skipped gracefully: upns.live / p2pstream.vip / bysekoze.com (SPA players — future work),
    turbovidhls.com (no id in embed src), animeabc.xyz (broken upstream too)
- **Settings**: preferred quality + preferred server.

## Key file locations (relative to `EXTENSIONS/animekhor/`)

| Path | What |
|---|---|
| `DEV/` | Gradle project (independent — own settings.gradle.kts + stubs copy) |
| `DEV/src/en/animekhor/build.gradle.kts` | metadata + build config |
| `DEV/src/en/animekhor/src/main/kotlin/.../animekhor/AnimeKhor.kt` | main source class (all scraping) |
| `DEV/.../animekhor/AnimeKhorFilters.kt` | filters (dynamic genre/studio fetch) |
| `DEV/.../animekhor/extractors/` | 8 hoster extractors + PlaylistUtils-lite + vendored JsUnpacker |
| `DEV/src/en/animekhor/res/mipmap-*/ic_launcher.png` | official AnimeKhor icon (from aniyomiorg repo) |
| `MEMORY/sites/site-analysis.md` | ★ live-verified site intel (endpoints, hosters, quirks) |
| `MEMORY/session-logs/` | session history |

## Critical rules for THIS extension

1. **Branch discipline**: all work stays on `ext/animekhor` until the user explicitly approves a merge.
2. **Search mapping**: episode-post → series URL mapping is the core search design — if the site
   changes its episode slug pattern (`-episode-` / `-episodes-N-to-M`), update `EPISODE_SLUG_REGEX`.
3. **CF-protected**: NEVER assume a plain curl failure = endpoint broken. The app solves Cloudflare
   via its interceptor; verify through the page_reader channel or the device.
4. **Per-mirror tolerance**: dead embeds are common (e.g. "DPlayer → Video Not Available" options) —
   extraction failures must never remove other servers' videos.
5. **Deferred hosters** (upns/p2pstream/bysekoze/turbovid): see site-analysis §hosters for the
   research notes before attempting them.
