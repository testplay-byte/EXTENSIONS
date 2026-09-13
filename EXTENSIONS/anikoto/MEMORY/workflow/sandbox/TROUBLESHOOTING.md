# TROUBLESHOOTING.md — Per-issue playbooks (from real incidents 51–54)

Each playbook = Symptoms → Diagnose → Fix → Verify. Facts referenced here live in
`REFERENCE.md`. If a playbook fails, fall back to the full chain probe in
`TROUBLESHOOTING.md` §1 and record findings in a session log.

---

## §1 "The extension broke" (browse/search/episodes/playback dead)

This is the mega-playbook. Run top-to-bottom; the first failing stage is your root cause.
Save a python replay script per run under `EXTENSIONS/anikoto/ANALYSIS/` (pattern:
`analyze-full-chain-v2.py`, sessions 52–54).

| # | Stage | Probe | Healthy looks like |
|---|---|---|---|
| 1 | Domains alive | `curl -s -o /dev/null -w '%{http_code}' https://anikototv.to/` (+5 mirrors) | 200. All dead → domain migration; hunt new domain (web search) |
| 2 | Catalog HTML | fetch `/most-viewed` or `/filter?...` | titles present; note if links absolute/relative |
| 3 | Watch page | `/watch/<slug>/ep-1` → `#watch-main` `data-id` | numeric id present |
| 4 | Episode list | `/ajax/episode/list/{id}?vrf=<RC4 simple-hash>` | JSON/HTML with episode `data-id`s |
| 5 | Server list | `/ajax/server/list?servers=…` | server entries; **note which hosts the iframes point to** (changed twice already) |
| 6 | Server iframe | `/ajax/server?get=…` | megaplay iframe URL; capture any `?s=` param — **do not drop it** |
| 7 | megaplay page | fetch iframe URL | page contains data-id |
| 8 | getSources | POST/GET `getSourcesNew` then `getSources` | `enc` blob (AES key/IV in REFERENCE §5) — or new shape! |
| 9 | Master m3u8 | GET decrypted master URL **with the same TLS as OkHttp/curl** | starts `#EXTM3U`. 403 openresty = TLS-gated default CDN → must use `s=tcdn` |
| 10 | Variant + segment | GET variant playlist → 1 segment | `#EXTM3U` … segment 200; segment on tiktokcdn-family hosts = PNG-wrapped (first 252 bytes → TS 0x47) |

**Common outcomes (history):**
- Stage 8 shape changed → update decrypt/parse (`MegaPlayDecrypt` in `AnikotoExtractors.kt`);
  check `newclient.min.js` version — key/IV rotate with it.
- Stage 9 403 with curl but 200 in Chrome → CDN rotation; the fix pattern is v16.11:
  loop `s` candidates `[iframe's own s, "tcdn", ""] × [getSourcesNew, getSources]`, accept the
  first whose **master verifies as fetchable**, WebView (Chrome TLS) as last resort.
- Stage 5 hosts changed (vidtube/vidwish → megaplay happened) → update dispatch, keep old
  branches for safety.
- Selector-level HTML changes → scrapling `find_similar` + `generate_css_selector`
  (`TOOLS.md` §2) to regenerate, port to Kotlin.
- Kiwi hosters empty → expected since 2026-09-13 (mapper is download-only now).

Verify the fix with a python sim for **2 different titles** (incl. one `.site`/`.top` rotation
case) before building.

## §2 In-app download fails / version shows old

- **Symptom:** install/update button errors, or extension list shows old `code`/`version`.
- **Diagnose:** curl `repo/index.min.json` → right values? Then curl every apk URL in the
  index (old AND new filenames) → all must be 200.
- **Root causes seen:**
  1. Old APK deleted on publish → 404 for cached clients (app caches index ≤24 h). Fix:
     re-add old APKs (staging copies exist under `/home/z/apk-staging/`, GitHub Releases,
     and git history of both branches). **Rule: never delete.**
  2. Index fields not bumped → re-do Phase 4 step 4.2.
  3. Nothing wrong → app's 24 h repo-check gate. Tell user: Settings → Browse →
     Extension repos → remove + re-add (instant refresh), or wait ≤24 h.
- **Verify:** raw index shows new code; every apk URL 200; signature identical.

## §3 Website doesn't download / looks stale

- **Symptom:** download button 404s, or site shows old version.
- **Diagnose (fresh browser context, cache-busted):** banner version? card badges?
  download `href`? → compare with raw index. Also confirm the Pages build for the latest
  main-branch commit says "built" (dashboard → Pages).
- **Root causes seen:** (1) deleted old APK → cached pages 404 (same as §2.1);
  (2) Pages rebuild window (~30 min) + browser cache — not a bug; site fetches index with
  `?t=Date.now()` + `no-cache` since session 53b, so a hard refresh suffices.
- **Verify:** all `main/apk/*.apk` 200; banner synced to index via `syncWhatsNew()`;
  390 px mobile no horizontal overflow (`scrollWidth == clientWidth`).

## §4 Wrong extension logo before install

- **Symptom:** extension list icon in-app doesn't match the installed app icon.
- **Cause:** `repo/icon/eu.kanade.tachiyomi.animeextension.en.anikoto180.png` wasn't the real
  APK launcher icon (was the 180 artwork; real = flower, md5 `b14f03…`).
- **Fix:** extract icon from the APK (`unzip` dex res / use the dev-source
  `mipmap-xxxhdpi/ic_launcher.png`), push to `repo/icon/<pkg>.png`.
- **Verify:** md5 of raw icon == md5 in APK == md5 of site asset.

## §5 Search/filters/metadata oddities

- Search & filters: `AnikotoFilters.kt` + `SmartSearch.kt`; check the catalog ajax params
  against `MEMORY/sites/endpoints.md`.
- Episode metadata (kitsu): `metadata/EpisodeMetadataFetcher.kt`; Cloudflare-related history
  in session 36/38 logs.
- Pagination/perf: session 42/51 logs.

## §6 Build/CI failures

- Local: `./gradlew assembleRelease` inside `EXTENSIONS/anikoto/DEV` (JDK/SDK setup history:
  session-05 log).
- CI: tag push `v16.x` → Actions → "Release" workflow. If R8/serialization errors → see
  session 47 (`fix-r8-serialization-and-settings`).
- After CI: verify DEX contains the new symbols (WORKFLOW.md Phase 3.5).

## Current open items (as of session 55, 2026-09-13)

- All four reported issues (playback, in-app download/version, website, icon) resolved and
  published through **v16.11** (sessions 52–54).
- Kiwi-Stream streaming hosters remain gone (site-side change; needs new resolver if wanted).
- megaplay CDN rotation may recur — §1 stage 9 pattern is the countermeasure; v16.11's
  candidate-loop design self-corrects as long as `tcdn` (or equivalent) keeps existing.
- scrapegraphai/scrapling: installed + researched, **awaiting user's go** for integration.

## §7 (session 56) "Only one server shows" — the `s=` selector class of bugs

- **Symptom:** e.g. ep-8 of a simulcast: site lists Vidstream-2 + HD-2; app shows only HD-2.
- **Root cause (proven live 2026-09-13):** all site servers → megaplay iframes; each iframe
  URL may or may not carry a `?s=<cdn>` selector. HD-2 shipped `?s=bcdn` (worked);
  Vidstream-2 shipped NONE → the extension's then-hardcoded fallback `tcdn` 403'd at the
  master (CDN rotation again) → that server died silently in extraction.
- **Fix shipped in v16.12:** dynamic candidate discovery (iframe `s` → page whitelist
  `"X"!==s` tokens → `s=` links → [bcdn, tcdn, ""]) × [getSourcesNew, getSources] with
  master-fetch verification per combo. Adding a new selector name in megaplay's own JS
  auto-includes it — no code change needed.
- **Related hardening:** unknown iframe hosts now attempt Flow A generically instead of
  being skipped; any `megaplay*` host matches Flow A.

## §8 (session 56) Wrong quality label (app shows 480p, site shows 360p)

- **Root cause:** megaplay master playlist NAME attributes can LIE
  (`RESOLUTION=640x360 NAME="480p"`). Old code preferred NAME.
- **Fix shipped in v16.12:** quality = RESOLUTION height first ("360p"), NAME fallback,
  else "auto". Matches what the site's own player (hls.js) displays.

## §9 (session 56) Smart search "blocked"

- Legacy engine scrapes google.com/search (udm=50) via WebView — verified live (scrapling
  stealth chromium) to hit **HTTP 429 "unusual traffic"** bot-walls; it will keep failing
  intermittently BY DESIGN of Google. v16.12 adds: engine picker (auto/gemini/google),
  Gemini API engine (key + model + test button in settings; free keys:
  aistudio.google.com/apikey), and SPECIFIC failure toasts for the legacy path
  (bot-check / consent page / JS wall / sign-in wall / timeout / unparsable answer) and
  for Gemini (invalid key / quota 429 / model 404 / server 5xx / network).

---

## §10 (session 58) Smart search "no anime title could be read" / Test-Connection HTTP 400

**Google engine extraction failure playbook:**
1. Ask the user for the raw response (or have them enable Settings → Smart Search →
   "Copy response" and paste the clipboard).
2. Replay it through the strategy set: S0 `[{[Title]}]` lenient parser → S1/S2 (cap 100,
   stop at `(`) → S2c → S3 → S4 → S5 → S6 → S6b (Japanese title) → S7 line scan.
3. Historical bug (fixed v16.12): S2/S7 caps measured the WHOLE match INCLUDING the
   parenthetical "(Japanese title: …)" → long answers never matched. Keep caps on the
   title portion only.
4. Validate any new regex offline: port to python and run against
   `/home/z/probe-s58/validate_new_extractor.py` cases (user's real failure + live bracket
   capture + regression capture).
5. If the answer has NO detectable structure: improve the bracket instruction compliance
   (short suffix on the clean query) — NEVER the whole prompt (v16.12 regression).

**Gemini Test-Connection HTTP 400 playbook:**
1. Reproduce the request with the internal test key (SECRETS.md) from any shell —
   remember the error precedence: 404 = model doesn't exist; 400 INVALID_ARGUMENT = bad
   payload; 400 FAILED_PRECONDITION (location) = payload is VALID, just geo-blocked.
2. `thinkingConfig.thinkingBudget` is ONLY valid on gemini-2.5* — the 3.x family rejects it
   with a bare INVALID_ARGUMENT that never mentions "thinking".
3. Custom model ids must be exact API names (e.g. `gemini-3.1-flash-lite`); the UI error now
   includes the model id + this hint.
