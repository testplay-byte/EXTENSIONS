# Session 62 — Preferred-domain logic fix + thin-ladder richness scan (v16.15 RELEASE)

> Date: 2026-10-07 · Trigger: user bug report after testing the v16.14 release
> Build: v16.15 (extVersionCode 15, extVersionId 11 UNCHANGED) — RELEASE on the build repo
> (tag → GitHub Release → Pages downloads page). Distribution repo (Confused-Creature-180/
> aniyomi-extensions) still NOT touched — user supplies PAT2 after their testing, same
> workflow as session 61.
> Live recon artifacts: /home/z/recon/sakamoto_ep4/ (step1_servers.py … step8_survey.py,
> servers.json, resolve_results.json, watch_ep4.html, iframe dumps).

## 1. User's asks (this session)

1. **Preferred domain not applied**: "if i change the preferred domain it does not get
   applied. it still shows and opens the results from the other one this seems like a
   logical issue so look into it and improve it."
2. **Missing resolutions on a specific episode**: Sakamoto Days ep-4
   (https://anikototv.to/watch/sakamoto-days-sfdxz/ep-4) — "when I tried to resolve this
   specific episode it only showed me two resolution even though the others are available."
3. "good luck and take your time to improve them" — improve each and everything properly.

## 2. Diagnosis (all live-verified 2026-10-07, paced probes, artifacts /home/z/recon/sakamoto_ep4/)

### Issue 1 — preferred domain never applied — CONFIRMED root cause + fix
- `Anikoto.kt` declared `override val baseUrl: String by lazy { preferences.getString(...) }`.
  `by lazy` evaluates ONCE and caches for the WHOLE PROCESS: after the first request (or
  WebView open) the old domain was frozen in. Changing the setting had NO effect until the
  app was force-stopped; even then only a fresh process re-read it.
- All OTHER settings already used typed getters that re-read on every access — which is
  exactly why ONLY the domain appeared "stuck" (the user's own framing: "this seems like a
  logical issue" — correct).
- The settings WRITE side was verified healthy: ListPreference key `pref_domain` on the
  source's own preference screen (file `source_<id>`), same file/key the source reads.
- FIX: `baseUrl` is now a LIVE GETTER — `get() = try { settings.preferredDomain… } catch …
  DEFAULT_BASE_URL`. A domain switch applies to the very next request (browse, details,
  episodes, AJAX, WebView URLs). No restart needed.
- Side-verification: all 6 mirror domains in the picker return HTTP 200 today
  (anikototv.to / .cz / .me / .net / anikototv.se / anikototv.com) — the list is healthy.
- Domain-independence of stored data re-checked: anime.url = bare slug, episode.url =
  relative path + fragment; getAnimeUrl/getEpisodeUrl/animeDetailsRequest all build from the
  CURRENT baseUrl → switching domains fully migrates saved items.

### Issue 2 — "only two resolutions" on Sakamoto Days ep-4 — SOURCE LIMITATION, with hard evidence
Replicated the extension's full pipeline in Python (RC4 vrf → episode list → server list →
ajax/server?get= → megaplay iframe → data-id → getSourcesNew/getSources incl. MegaPlayDecrypt
AES-256-CBC → master m3u8 → variant playlists):
- ep-4 servers: SUB {HD-1, Vidstream-2, Vidstream-1}, DUB {HD-1, Vidstream-2, Vidstream-1}
  — ALL six resolve to megaplay.buzz (Vidstream-1 now uses the /videojs/ vidstack player
  path but the SAME data-id + getSources API).
- data-ids: 2226 (sub), 2217 (dub). getSources payloads are minimal `{tracks,intro,outro,
  server,enc}` — NO per-quality map; the master m3u8 is the only quality source.
- EVERY s-candidate × BOTH endpoints (tcdn→megap.shiora.top, bcdn & default→
  fetch.nexabloom.top) returns a SINGLE-VARIANT master. RAW master text (saved):
  exactly ONE `#EXT-X-STREAM-INF` (1920x1080) + an `#EXT-X-I-FRAME-STREAM-INF` trick-play
  track (NOT a playable quality). Same for sub and dub → the app's "two resolutions" are
  "SUB - 1080p" + "DUB - 1080p" (correct behavior).
- Platform context: Dorohedoro S2, Dr Stone SF3, Kusunoki, Kill Blue, Tensura S4, Gals —
  ALL still list 1080/720/360 ladders, so extraction is NOT broken. Sakamoto Days ep-1/4/8
  and Beyblade X are 1080p-only at source (whole-show encoding choice, not per-CDN).
- "The others are available": the site's other qualities for such shows live in the
  Download Options modal = Kiwi mapper `download` entries (pahe.nekostream.site short links
  → file host pages) — file downloads, NOT streams; not usable as Aniyomi videos. Kiwi
  mapper for Sakamoto has NO streaming entries on any probed episode (sub/dub download-only).
- NOTE (recon tooling): fetch.nexabloom.top masters now fetch fine with plain OkHttp-style
  requests — the session-54 "default CDN master 403s non-browser TLS" pattern has eased;
  the WebView fallback simply doesn't trigger anymore. Candidate ORDER (bcdn first) remains
  valid and self-correcting.

## 3. Code changes (commit 12a6d69, tree-sitter parse OK on all 3 .kt files)

1. **Anikoto.kt** — `baseUrl` `by lazy` → live getter (try/catch → DEFAULT_BASE_URL;
   failed lazy retries on next access). Comment documents root cause + fix.
2. **video/AnikotoExtractors.kt** — THIN-LADDER RICHNESS SCAN in resolveVidTube:
   - a full candidate win with ≤ THIN_LADDER_MAX (2) variants no longer `break`s the
     s-candidate scan — remaining candidates are probed and the RICHEST full result wins
     (ties keep the first = the site player's own CDN choice); healthy (>2) ladders still
     break immediately → zero extra requests for fully-working episodes;
   - `seenMasters` set skips candidates resolving to an ALREADY-VERIFIED master URL
     (different s= values frequently share one master URL) — extra cost ≈ 1 getSources
     call per remaining candidate;
   - single-variant explanatory log updated with the sakamoto evidence.
3. **AnikotoSettings.kt** — Playback category: new non-selectable "About missing qualities"
   note (source limitation, download-only pahe qualities are not streams, Sub/Dub are
   separate entries); Preferred server list gains current live name "Vidstream-1"
   (lineup verified 2026-10-07: HD-1 / Vidstream-2 / Vidstream-1), legacy names kept.
4. **build.gradle.kts** — extVersionCode 14 → 15 (versionName 16.15), session-62 changelog.

## 4. Delivery

- Pushed 12a6d69 → main; tagged **v16.15** → Release run 37655288629 (Build & publish signed APKs).
- Downloads page: src/lib/site-config.ts AniKoto card v16.14 → v16.15, features
  "Instant domain switching". (Deploy Pages refreshes /downloads/ to the latest release assets.)
- ntfy TASK808DONE sent after release assets verified.
- Dist repo (PAT2) still pending — user will provide after testing (same as v16.14).

## 5. Pitfalls / learnings

- `by lazy` on an override-able source property is a TRAP for user-selectable values: it
  turns "apply immediately" into "apply after force-stop". Prefer live getters for ANY
  preference that feeds request URLs.
- A master playlist can be genuinely single-variant at source; the I-FRAME-STREAM-INF line
  must never be counted as a quality (it starts with `#EXT-X-I-FRAME-…`, which the
  `#EXT-X-STREAM-INF:` prefix check correctly rejects).
- When the first full-win candidate has a thin ladder, later candidates can be richer —
  but probe cost must be bounded (seen-master dedup + thin-only continuation).
- megaplay getSources (`enc` blobs) carry NO quality metadata — master m3u8 is ground truth.
