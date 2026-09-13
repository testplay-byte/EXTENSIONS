# AGENT_ONBOARDING.md — AniKoto 180 extension
> **Read this first.** Any AI agent (or human) can continue this project from anywhere, anytime,
> using only this repository. Nothing else is required except the user's tokens (see §7).

Last updated: session 57 (2026-09-13) — v16.13 test build era.

---

## 1. What this project is

A **custom Aniyomi anime extension** ("AniKoto 180") for anikototv.to and its mirrors.
It lives in this repo under `EXTENSIONS/anikoto/`:

| Path | What |
|---|---|
| `DEV/` | Gradle project — `DEV/src/en/anikoto/` holds ALL Kotlin sources + resources |
| `DEV/src/en/anikoto/build.gradle.kts` | Extension metadata: `extVersionCode`, `extVersionName`, `extClass` |
| `MEMORY/session-logs/` | One log per work session — **read the last 2–3 before doing anything** |
| `MEMORY/worklog.md` + repo-root `worklog.md` | Append-only cross-session worklogs |
| `MEMORY/modules/` | Per-module architecture docs (catalog, video, smart-search, settings…) |
| `MEMORY/sites/` | Site research (endpoints, servers, CDN quirks, PNG-wrapped segments) |
| `MEMORY/issues-resolutions/` | Post-mortems of past breakages |
| `MEMORY/workflow/sandbox/` | Mirror of the offline workflow folder (WORKFLOW/REFERENCE/TROUBLESHOOTING/TOOLS) |
| `EXTENSION.md`, `APK_INFO.md` | Extension metadata + APK facts |

## 2. Immutable identity (NEVER change)

- Package: `eu.kanade.tachiyomi.animeextension.en.anikoto180`
- Source ID: `178825880993122333` (derived from `extVersionId = 11` — also fixed)
- Signing cert SHA256: `B4:67:CA:…:6A:5A` (CI signs; must match dist repo's `repo.json`)
- Extension display version MUST start with `16.` (loader constraint)

## 3. GOLDEN RULES (violating these damaged past sessions)

1. **NEVER install the Android SDK in your sandbox** — too big, not enough space. No exceptions.
2. **All builds happen on GitHub Actions** (`testplay-byte/EXTENSIONS` → workflow `release.yml`):
   - **Test build (default mode now):** `workflow_dispatch` with `tag=v16.X`, `publish=false`
     → signed APKs appear ONLY as a workflow artifact. No tag, no GitHub Release, no site.
   - **Publish:** tag `v16.X` on a release commit (or dispatch `publish=true`) → builds + creates
     the GitHub Release. Distribution to the user's app happens in a SEPARATE repo (§6).
3. **No local compile** (no SDK) — before every push run a **tree-sitter Kotlin parse** on every
   changed `.kt` file (see TOOLS.md §5; venv recipe inside). CI compile errors cost 3 runs once.
   Also grep new call-sites for static-vs-instance access (past CI failures).
4. **Distribution repo is sacred**: `Confused-Creature-180/aniyomi-extensions` gets surgical
   commits only ("handle with care"). Never delete old APKs; never rewrite `index*.json` by hand
   except through the documented release flow.
5. **One version bump per build**: `extVersionCode += 1` (versionName auto-derives `16.<code>`).
   `extVersionId` stays 11 forever.
6. **Secrets never enter git**: user tokens live in sandbox-only `SECRETS.md` files; clone/push
   URLs embed tokens (fine), but never paste them into files, logs, or code.
7. **Verify behavior against live endpoints** (curl/python/scrapling) before writing Kotlin —
   the site rotates CDNs/domains; assumptions rot fast. Pace probes: the site AND Google
   rate-limit/CAPTCHA after ~8 rapid hits.

## 4. Where the logic lives (Kotlin map)

`DEV/src/en/anikoto/src/main/kotlin/eu/kanade/tachiyomi/animeextension/en/anikoto/`:

| File | Role |
|---|---|
| `Anikoto.kt` | Source class: catalog/details/episodes, smart-search wiring, clipboard debug copy |
| `AnikotoSettings.kt` | ALL preference keys/defaults/getters + the settings UI (4 categories) |
| `AnikotoRC4.kt` | vrf parameter (RC4 "simple-hash") |
| `video/AnikotoExtractors.kt` | megaplay chain: AES-256-CBC `enc` decrypt, `getSourcesNew/getSources`, `s=` CDN-candidate discovery (`{tcdn,bcdn}` + page-whitelist scan), master-m3u8 verification, RESOLUTION-first quality labels |
| `video/WebViewFetcher.kt` | WebView toolbox; `fetchRenderedText` = Google AI scraping with generation-token + **stability polling** (answers stream in late) |
| `video/LocalProxyServer.kt` | PNG-header strip (252 bytes) for wrapped TS segments |
| `smartsearch/SmartSearch.kt` | AI search: Gemini REST engine (thinking-disabled w/ reject-retry) + legacy Google engine (CLEAN query + 7-strategy extractor + block classifier); failures carry raw response in `Failure.detail` |
| `metadata/EpisodeMetadataFetcher.kt` | Thumbnails/titles/descriptions fetchers (3 toggles) |

Source-site chain of truth: `MEMORY/sites/*.md` + workflow mirror `REFERENCE.md` §5.

## 5. Test-build recipe (the current normal)

```bash
# 1. Edit code in DEV/, tree-sitter check every touched .kt
# 2. Bump extVersionCode in DEV/src/en/anikoto/build.gradle.kts (+1)
# 3. Commit & push (NO tag):
git -C <clone> commit -am "anikoto v16.X (TEST BUILD, no publish): <what/why>"
git -C <clone> push origin main
# 4. Dispatch the build (needs repo token in the clone URL):
curl -s -X POST -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/testplay-byte/EXTENSIONS/actions/workflows/release.yml/dispatches \
  -d '{"ref":"main","inputs":{"tag":"v16.X","publish":"false"}}'
# 5. Watch: https://github.com/testplay-byte/EXTENSIONS/actions — artifact test-build-apks-v16.X
```

The user tests the artifact APK directly (signature matches → in-place install).

## 6. Publishing to users (ONLY on explicit user go-ahead)

1. Tag `v16.X` (Actions builds + creates the GitHub Release), or dispatch `publish=true`.
2. Dist repo (`Confused-Creature-180/aniyomi-extensions`, branch `repo`): add
   `apk/aniyomi-en.anikoto180-v16.X-release.apk` + regenerate `index.json`/`index.min.json`
   (bump `code`/`version`/`apk` fields; keep format identical to official aniyomi repos).
3. Branch `main` (Pages site): mirror APK into `apk/`, `docs/js/app.js` auto-syncs the banner.
4. Full details: `MEMORY/workflow/sandbox/WORKFLOW.md` Phase 4 + session logs 53/54.

## 7. Secrets (sandbox-only; ask the user if missing)

- PAT1 — push to this repo (`testplay-byte/EXTENSIONS`)
- PAT2 — push to the dist repo (use sparingly!)
- ntfy topic `THE-TASK-IS-DONE` — POST one-line summary when a user-visible milestone completes
- User's Gemini test key (session 57, internal testing only — NEVER ship in the extension)
None of these are committed anywhere in git. `MEMORY/workflow/sandbox/README.md` points to the
sandbox-local `SECRETS.md` which must never be pushed.

## 8. Known live quirks (as of session 57)

- All 4 watch-page servers return megaplay iframes; only CDN selectors `{tcdn, bcdn}` are valid
  (`bcdn` is the OkHttp-friendly one; `tcdn`/default master is TLS-gated 403).
- Segments on `ibyteimg|tiktokcdn|ipstatp|yoot.akirax` are PNG-wrapped (strip 252 bytes).
- Master playlists can lie (`RESOLUTION=640x360 NAME="480p"`) — always trust RESOLUTION.
- Google AI Mode (udm=50) streams answers post-load; requires the stability-polling fetch;
  the legacy smart-search engine must send a CLEAN query (no prompt brackets).
- Gemini API is geo-blocked from some regions/datacenter IPs (400 FAILED_PRECONDITION
  "User location is not supported") — classify it explicitly in errors.
- App checks the dist repo ≤1×/24h; force refresh = remove+re-add the repo in the app.

## 9. Start-here checklist for a new session

1. Read the last 2–3 `MEMORY/session-logs/*.md` + tail of `worklog.md`.
2. Read `MEMORY/workflow/sandbox/REFERENCE.md` (facts) + `TROUBLESHOOTING.md` (if "it broke").
3. Confirm the live site still matches `MEMORY/sites/` (quick scrapling/curl probe).
4. Work in `DEV/`, follow §3 golden rules, log everything, append worklogs, ntfy on milestones.
