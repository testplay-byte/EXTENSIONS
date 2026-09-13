# REFERENCE.md — Hard facts, identifiers, URLs, paths

> Ground-truth data verified live in sessions 51–54 (latest full verification: 2026-09-13).
> If any fact here contradicts live checks, **the live check wins** — then update this file.

---

## 1. Extension identity

| Field | Value | Rule |
|---|---|---|
| Name | AniKoto 180 | |
| Package | `eu.kanade.tachiyomi.animeextension.en.anikoto180` | NEVER rename again (orphaned libraries) |
| Source ID | `178825880993122333` | NEVER change (derived from `extVersionId = 11`, which stays fixed) |
| `extVersionCode` | **12** (v16.12) | Bump by +1 on every release (★ exception s58: user may reuse a test-build number — test numbers are NOT reserved) |
| Display version | `16.12` | `extVersionName` (grep build.gradle.kts) |
| Signing cert SHA256 | `B4:67:CA:…:6A:5A` | Must equal repo.json `signingKeyFingerprint`; identical cert ⇒ in-place update, no uninstall |
| Logcat tag | `Anikoto` | `adb logcat -s Anikoto` for user log captures |

## 2. Release history (what's out there)

| Ver | code | Fix | APK bytes | sha256 (first…last) |
|---|---|---|---|---|
| 16.9 | 9 | baseline | 268,142 | — |
| 16.10 | 10 | megaplay AES decrypt (`MegaPlayDecrypt`, key `i?LMTAx0Q6,:}50U` / IV `W0;27ToaUpl_P%'c`) + `getSourcesNew` fallback + 6-domain preferred-domain setting | 270,953 | `aab6ead4bf41…86ce4` |
| 16.11 | 11 | megaplay CDN rotation: pass `s=` selector (`tcdn` candidates loop) + **master-m3u8 fetch verification** (`fetchAndVerifySources`) + WebView last resort | 273,044 | `c735f4927ee6…b0346` |
| 16.12 | 12 | s56: server-tolerant dispatch + RESOLUTION-based quality labels; s57/58: Smart Search v2 — `[{[Title]}]` bracket convention on BOTH engines (lenient S0 + S1–S7), Test-connection fix (thinkingConfig only for gemini-2.5*), model list (3.1 Flash Lite default/top), defaults (Smart Search ON, engine google), "Copy response" toggle | 286,691 | `e014d92dfbbf…dfeee1` |

- v16.11: dev commit `c1ed16a`, tag `v16.11`, Actions run **34755207560 SUCCESS**,
  dist repo `repo` branch `feff049`, `main` branch `e4f160d`, distro GitHub Release `v16.11` (id 387885432).
- v16.12 (RELEASE, session 58): dev commit `1153651`, tag `v16.12`, Actions run **34767453979 SUCCESS**,
  dist repo `repo` branch `0ff5f25`, `main` branch `8cb181d`, distro GitHub Release `v16.12`.
  ⚠️ Devices on the v16.13 TEST build (code 13) never get 16.12 in-app (13 > 12) — they sideload it.
- **All previous APKs stay published** (see TROUBLESHOOTING §2 — never delete old APKs).

## 3. Repos & paths

### Dev repo (code lives here)
- Remote: `https://github.com/testplay-byte/EXTENSIONS` — auth PAT **PAT1** (see SECRETS.md)
- Local: `/home/z/extensions-repo` (branch `main`)
- Extension: `EXTENSIONS/anikoto/` → `DEV/` (Gradle project, `./gradlew`),
  `DEV/src/en/anikoto/` (Kotlin sources + `res/` icons), `MEMORY/` (session logs, sites research,
  issues-resolutions), `ANALYSIS/`, `EXTENSION.md`, `APK_INFO.md`
- Key Kotlin files (`DEV/src/en/anikoto/src/main/kotlin/eu/kanade/tachiyomi/animeextension/en/anikoto/`):
  `Anikoto.kt` (catalog/details/episodes) · `AnikotoDto.kt` · `AnikotoFilters.kt` ·
  `AnikotoSettings.kt` (preferred domain) · `AnikotoRC4.kt` (vrf) ·
  `video/AnikotoExtractors.kt` (megaplay chain, `MegaPlayDecrypt`, `fetchAndVerifySources`) ·
  `video/LocalProxyServer.kt` (PNG-strip proxy) · `video/WebViewFetcher.kt` ·
  `smartsearch/SmartSearch.kt` · `metadata/EpisodeMetadataFetcher.kt`
- Build: **GitHub Actions ONLY** (`release.yml`) — tag push `v16.x` (release) or workflow_dispatch
  with `publish=false` (test build → artifact only). NEVER install the Android SDK in a sandbox.
  Local sanity tool: tree-sitter Kotlin parse (TOOLS.md §5).
- Version bump location: `DEV/src/en/anikoto/build.gradle.kts` (`extVersionCode`, `extVersionName`)

### Distribution repo (publish target — "handle with care", minimal diffs)
- Remote: `https://github.com/Confused-Creature-180/aniyomi-extensions` — auth PAT **PAT2**
- Local: `/home/z/aniyomi-extensions-180`
- **`repo` branch** = the real Aniyomi extension repo:
  - `index.json` + `index.min.json` (identical data; app reads min)
  - `repo.json` (`{meta:{name,website,signingKeyFingerprint}}` — matches official aniyomiorg shape)
  - `apk/aniyomi-en.anikoto180-v16.{9,10,11}-release.apk` (ALL kept)
  - `icon/eu.kanade.tachiyomi.animeextension.en.anikoto180.png` (= real APK launcher icon,
    md5 `b14f03…` — the flower, NOT the 180 artwork)
  - User adds in app: `https://raw.githubusercontent.com/Confused-Creature-180/aniyomi-extensions/repo`
    (app appends `/index.min.json`)
- **`main` branch** = GitHub Pages site + direct downloads:
  - `docs/` → https://confused-creature-180.github.io/aniyomi-extensions/
  - `docs/js/app.js` — `REPO_BASE` points at raw repo-branch; index fetched with `?t=Date.now()`
    + `cache:'no-cache'` (never shows stale release); `syncWhatsNew()` auto-syncs banner
    version/download link from index data
  - `apk/` — all release APKs mirrored (site "download" buttons + banner)
  - `dev/` — stale v16.9-era source copy, **intentionally untouched** ("don't mess with this repo")
  - GitHub Releases: `v16.10` (id 385571039), `v16.11` (id 387885432) with signed APK assets
- Staging: `/home/z/apk-staging/` (downloaded release APKs + release metadata JSONs)

## 4. Aniyomi app behavior (verified from aniyorg/aniyomi source, session 53c)

- Repo check gated: **≤ 1× per 24 h** (`last_ext_check` + 24 h). Force refresh: Settings →
  Browse → Extension repos → remove + re-add the repo (guaranteed fresh index), or wait ≤24 h.
- APK URL: `{indexBase}/apk/{apk-field}` · Icon URL: `{indexBase}/icon/{package}.png`
- `index.min.json` schema (verified field-identical to official aniyomiorg repo branch):
  `{ name, pkg, apk, lang, code, version, nsfw, sources:[{name, lang, id, baseUrl}] }`
- Update installs in place iff signing cert matches (it does — see §1).

## 5. Source-site chain (anikototv → playable stream)

- Domains: `anikototv.to` (+ mirrors `.cz` `.me` `.net` `anikototv.se` `anikototv.com`)
  — all were 200 OK on 2026-09-13; preferred-domain setting holds 6 options
- Catalog/episode list HTML structure: unchanged; links now **absolute** (parser handles both)
- Watch page → `#watch-main` `data-id` → `GET /ajax/episode/list/{id}?vrf=<RC4 "simple-hash">`
  → episode `data-id`s → `GET /ajax/server/list?servers=…` → all 4 servers now return
  **megaplay iframes** (vidtube/vidwish gone) → `GET /ajax/server?get=…` → iframe
  `https://megaplay.buzz/stream/s-<sid>/<epid>/<sub|dub>` — **HD-1 ships `?s=tcdn`**
- megaplay iframe page → data-id → `getSourcesNew` / `getSources` → AES-256-CBC `enc` blob
  (key/IV above, from `newclient.min.js?v=4.7`) → master m3u8 + tracks
- ⚠️ **Default CDN master is TLS-gated (403 to OkHttp/curl)** — MUST request with the `s=`
  param (site's player appends page `s=` to every getSources call). `s=tcdn` →
  `megap.shiora.site/.top` (rotates), fetchable. v16.11 loops candidates
  `[iframe's own s, "tcdn", ""] × [getSourcesNew, getSources]` and accepts the first combo
  whose **master starts with `#EXTM3U`**.
- Segments on `ibyteimg.com|tiktokcdn.com|ipstatp.com|yoot.akirax.buzz` are **PNG-wrapped**:
  strip first **252 bytes** → MPEG-TS (0x47). Extension proxy `stripPngHeader` handles it.
- `mapper.nekostream.site` (Kiwi PATH B): 200 but **download-only now** (no `url` key) →
  Kiwi streaming hosters are gone; code degrades gracefully.

## 6. Sandbox environment

| Path | What |
|---|---|
| `/home/z/extensions-repo` | dev repo clone |
| `/home/z/aniyomi-extensions-180` | dist repo clone |
| `/home/z/apk-staging` | release APK staging + release JSONs |
| `/home/z/anikoto-workflow` | **this workflow folder** |
| `/home/z/scrape-venv` | isolated venv: scrapling 0.4.15 + scrapegraphai 2.2.4 (see TOOLS.md) |
| `/home/z/.venv` | main pinned venv (uv-managed) — **don't touch** |
| `python3` | 3.12.14 · `uv 0.12.5` at /usr/local/bin/uv |

## 7. Notification

- Task-complete ping: `POST https://ntfy.sh/THE-TASK-IS-DONE` (body = one-line summary).
  Every completed user-visible milestone gets one.

## 8. Memory conventions (dev repo)

- Session log per work session: `EXTENSIONS/anikoto/MEMORY/session-logs/YYYY-MM-DD_session-NN_<slug>.md`
  (session 55 = this workflow/tooling session)
- Append (never overwrite) to `EXTENSIONS/anikoto/MEMORY/worklog.md` **and** repo-root `worklog.md`
- Cross-cutting lessons live in `EXTENSIONS/anikoto/MEMORY/issues-resolutions/*.md`
- Site research: `EXTENSIONS/anikoto/MEMORY/sites/*.md` (endpoints, servers, cdn-waf, png-wrapping…)

## 9. Session 56 additions (v16.12 TEST BUILD — not published)

- **megaplay CDN selectors**: current valid set is exactly **{tcdn, bcdn}** (named by the
  player's own inline bypass-check `"tcdn"!==s&&"bcdn"!==s` in the iframe HTML). As of
  2026-09-13: `tcdn`/default → `fetch.nexabloom.top` master **403** to OkHttp;
  **`bcdn` → `ncdn.imgnex.top` master 200** (OkHttp-friendly). The extension now
  auto-discovers candidates: iframe's own `s` → `"X"!==s` whitelist tokens → `s=` links →
  known fallbacks [bcdn, tcdn, ""], deduped, capped at 6.
- **Mislabeled quality**: megaplay masters can ship `RESOLUTION=640x360 NAME="480p"`.
  Extension now derives quality from RESOLUTION height first (matches the site's hls.js
  display), NAME only as fallback, else "auto".
- **Dispatch hardening**: any `megaplay*` host matches Flow A; UNKNOWN hosts now attempt
  generic Flow A instead of being skipped silently.
- **Smart Search engines**: `auto` (default; Gemini if key set, else Google scrape → also
  cross-fallback with both reasons reported), `gemini` (REST
  `generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, `x-goog-api-key`
  header, model selectable, defaults gemini-2.5-flash; test button in settings),
  `google` (legacy WebView scrape, now classifies bot-check/consent/JS-wall/sign-in/timeout).
- **v16.12 = TEST BUILD** (code 12): exists only as Actions artifact
  `test-build-apks-v16.12` (run 34760414152) + `/home/z/apk-staging/test-build-v16.12/`.
  sha256 `4398474a98462f1e…`, 280,558 bytes, signature `B4:67:CA:…:6A:5A` (in-place install).
  NO tag, NO release, NO dist-repo/index change. Publish later = tag `v16.12` + Phase 4.
- release.yml now supports `workflow_dispatch` input `publish=false` → signed APKs as
  workflow artifact only (no release, no Pages redeploy). Test-build recipe:
  `gh workflow run release.yml -f tag=v16.X -f publish=false` or the API dispatch.

## 10. Session 57 additions (v16.13 TEST BUILD — not published)

- **Smart Search settings UI (user-spec overhaul)**: metadata toggles have NO descriptions;
  toggle heading is "Smart Search" + summary "Search spelling correction and smarter description
  searching"; engine picker = exactly two options (`Google Gemini API` / `Google AI Search`, no
  descriptions); Gemini key field has no dialog text and a 1-line masked summary ("Not set" /
  "••••1234"); model picker = Gemini 3.8 Flash / **3.5 Flash-Lite (default+recommended)** /
  3.1 Flash-Lite / Custom model ID (free-text `pref_gemini_custom_model`); "Test connection"
  (was "Test Gemini connection"); bottom details = usage how-to only (no model details).
- **Conditional visibility**: engine=google hides key/model/custom/test prefs entirely
  (`applyEngineVisibility()` in AnikotoSettings, listeners post re-apply to main handler).
- **Engine/model migration**: stored `auto` → gemini (if key set) else google, materialized on
  settings-screen build + dynamic in the getter; pre-3.x model ids → moved to the custom field.
- **Legacy Google engine root cause (found via scrapling stealth capture)**: v16.12 sent the
  WHOLE LLM prompt (`buildPrompt`, brackets included) as the literal Google query → Google AI
  Mode conversation UI → extractor's markers ("Search Results"/"AI Overview") never present →
  "no anime title could be read". v16.13 sends a CLEAN query (`"<query> anime"`).
- **Extractor**: 7 strategies (is-titled / describing-is / "Would you like to know more about X"
  / Did-you-mean / quoted incl. single-word capitalized / MAL+AniList slug title-case /
  title-like line scan) over junk+echo-cleaned text, marker regions first. Validated 7/7 on a
  real stealth capture + reconstructed A-layout + synthetic cases (see
  /home/z/probe-s56/validate_extractor.py; pitfalls fixed live: "See your Search history" is
  sidebar UI not a footer — must NOT be a cut marker; title-like lines must survive echo
  filtering so exact-title answers aren't eaten).
- **WebView stability polling**: AI Mode answers STREAM in after onPageFinished;
  fetchRenderedText now re-grabs every 2s keeping the LONGEST text until stable (or deadline,
  default 25s) instead of returning the first grab.
- **Failure debugging**: Failure.detail now carries the rendered text (≤20k chars) for Google /
  error body (600) for Gemini; Anikoto.kt copies detail to the CLIPBOARD on failure and the
  toast appends "(raw response copied to clipboard)".
- **Gemini requests**: thinkingConfig.thinkingBudget=0 first (fast), auto-retry WITHOUT it when
  the model 400s on "thinking"; maxOutputTokens 512→2048; thought parts filtered in parsing;
  400 "User location is not supported" → explicit geo-block message.
- **Prompt (LLM-validated via z-ai + user key context)**: added exact-title anchoring sentence
  ("If the query is already an anime title or close to one…") — fixed "frieren"→"My Hero
  Academia" hallucination on a proxy model; stable on re-runs.
- **Model IDs verified via web docs (ai.google.dev/Vertex)**: gemini-3.8-flash,
  gemini-3.5-flash-lite, gemini-3.1-flash-lite. Sandbox could NOT live-call Gemini: egress IP
  is Hong Kong → Gemini API 400 FAILED_PRECONDITION "User location is not supported" (the
  error shape itself proves the user key AUTHENTICATES — location check happens after auth).
- **v16.13 = code 13**, commit `47c0b0e`, NO tag/release; artifact-only Actions dispatch.
- Sandbox Google probing got IP-CAPTCHA-walled after ~8 udm=50 fetches in 30 min — go easy.
- v16.13 build verified: Actions run 34764034178 (workflow_dispatch tag=v16.13 publish=false)
  SUCCESS on FIRST run (tree-sitter pre-check worked — zero compile iterations).
  Artifact `test-build-apks-v16.13` (id 10320012606) →
  `/home/z/apk-staging/test-build-v16.13/aniyomi-en.anikoto180-v16.13-release.apk`,
  285,456 bytes, sha256 `7a67a3a1a53821ed…c60e8e070`. DEX markers verified (all model ids,
  new settings strings, clipboard message, extractor regexes). Same CI cert → in-place update.
  NOTE: repo also has a "Build (CI)" push-triggered workflow (debug-apks artifact) — harmless,
  unrelated to Release.
