# Session 61 — WebView URL fix + quality-list robustness (v16.14 RELEASE)

> Date: 2026-10-07 · Trigger: user bug report after testing the v16.13 test build
> Build: v16.14 (extVersionCode 14, extVersionId 11 UNCHANGED) — RELEASE on the build repo
> (tag → GitHub Release → Pages downloads page). Distribution repo (Confused-Creature-180/
> aniyomi-extensions) deliberately NOT touched — user updates it after their own testing
> (they will supply PAT2 in a later step of this session's agreed workflow).
> Sandbox: fresh restore (build repo re-cloned with user-supplied PAT1; dist repo cloned
> read-only; tree-sitter + curl_cffi + pycryptodome reinstalled; git identity re-set).

## 1. User's asks (this session)

1. **URL / URL-linking bug**: "when I try to open up the content in web view, it opens up a
   URL which apparently is not proper. Like it opens up kind of a random URL, which is not
   the actual one for that specific content."
2. **Quality bug**: "resolving video sources gives me only a 1080p resolution for some of
   the videos" — look into it, handle better, improve properly.
3. Fix → **update the downloads page** → ntfy `TASK808DONE` → user tests → THEN the
   distribution repo gets updated (PAT2 to be provided by user).

## 2. Diagnosis (all live-verified 2026-10-07, paced curl_cffi probes, artifacts /home/z/recon/)

### Issue 1 — WebView "random URL" — CONFIRMED root cause + fix
- The extension stores `anime.url` as the BARE SLUG (e.g. `beyblade-x-aj6fn`) — normalized
  in `parseSearchItem`/`animeDetailsParse` since the v16.27-era restructure.
- Aniyomi's "Open in WebView" on the anime details screen calls
  `AnimeHttpSource.getAnimeUrl(anime)`, whose DEFAULT implementation is `baseUrl + anime.url`
  → `https://anikototv.to/<slug>` — which is NOT a real page.
- Live probe: `GET /<slug>` → **404 "Error" page** on BOTH anikototv.to and anikoto.cz;
  `GET /watch/<slug>` → 200 with `#watch-main` (full watch page works without an ep suffix).
- The extension never overrode `getAnimeUrl` (only `getEpisodeUrl` for episodes, session 43,
  which was already correct). Classic latent bug — the ep-level URL was fixed in v16.27 but
  the anime-level half was missed.
- ALSO hardened: `parseSearchItem` used `href.substringAfter(baseUrl)` — Kotlin returns the
  ORIGINAL string when the delimiter is absent, so if the site ever serves listing links
  from a different domain than our request domain, `anime.url` becomes a malformed hybrid
  ("https://other…watch/…") → details requests AND WebView break with garbage URLs.
  (Mirrors verified self-linking consistently TODAY — this is armor, not a live bug.)

### Issue 2 — "only 1080p for some videos" — CONFIRMED as site-side single-variant masters
- Replicated the extension's ENTIRE extraction pipeline live (watch page → episode list
  RC4 vrf → server list → /ajax/server?get → megaplay iframe → getSourcesNew/getSources
  (enc AES blob decrypted with MegaPlayDecrypt constants — still valid) → master → variants).
- Server lineups (per-show, dynamic): e.g. Exiled Heavy Knight ep-12 = HD-1, Vidstream-2,
  Vidstream-1, HD-2 — all 4 sub entries → SAME iframe/data-id (megaplay
  stream/s-2/831149/sub, data-id 179714); beyblade ep-1 = Vidstream-2 + Vidstream-1.
- Beyblade X ep-1: master has EXACTLY ONE real variant (1080p) on **both mirrors**, across
  **all server entries**, **all s-candidates (bcdn/tcdn/default)** and **both endpoints** —
  24 combinations checked. Yu-Gi-Oh Go Rush ep-1 = 720p-only. Most shows = [1080, 720, 360].
  → The site's own player shows the same single quality. It is the ingested source material.
- Probe-methodology trap (recorded!): naive `RESOLUTION=` regexes over master text count the
  `#EXT-X-I-FRAME-STREAM-INF` line too (beyblade "looks like" 1080+360; real count is 1).
  Real-variant counting must skip lines starting `#EXT-X-I-FRAME-STREAM-INF:`.
- Kiwi mapper (PATH B) now returns ONLY `download` links (pahe.nekostream.site HTML pages,
  JS-driven, no direct files) — no streaming URLs. Extension already handles this (0 tokens).
- megaplay CDN landscape re-verified: s=bcdn → ncdn.imgnex.top, s=tcdn → megap.shiora.top,
  default → fetch.nexabloom.top (Exiled ep-12) — ALL now 200 to plain OkHttp-like TLS
  (master + variants + segments). The session-54/60 "default master 403s OkHttp" pattern is
  GONE as of today; the candidate loop handles both worlds anyway.
- Real residual risk the extension COULD fix: session-60's loop accepted a candidate after
  "master AND ≥1 variant" verified — a TRANSIENT failure of the 720p/360p variant playlists
  would permanently narrow that resolution's quality list to 1080p-only, and the loop never
  re-tried other candidates. (Not directly reproducible in the sandbox today, but it is the
  only in-extension path to "single quality on a multi-quality episode".)

## 3. Fixes shipped (commit 7f8fcdc, 3 files, +109/−10)

**Anikoto.kt**:
- `getAnimeUrl(anime)` override → `"$baseUrl/watch/${animeSlug(anime.url)}"` (fixes the
  404/random-URL WebView bug; /watch/<slug> verified 200).
- New `animeSlug()` normalizer (bare slug | "/watch/<slug>" | "/watch/<slug>/ep-N" | full
  URL → bare slug) used at EVERY anime-url entry point: `animeDetailsRequest`,
  `getEpisodeList`, `getAnimeUrl`, `parseSearchItem` — tolerant of old persisted shapes.
- `parseSearchItem` slug extraction made domain-independent (no more substringAfter(baseUrl)
  trap; falls back gracefully for hrefs without /watch/).

**AnikotoExtractors.kt**:
- Candidate loop partial-load robustness: a candidate that loads only SOME of the master's
  listed variants is remembered as best-partial and remaining candidates are tried; the
  fullest result wins. Fast paths unchanged — all-variants-loaded candidates (including
  1==1 single-variant masters) still accept immediately with zero extra requests.
- Self-explanatory log when a master ships a single variant (points at source limitation).

**build.gradle.kts**: extVersionCode 13 → 14 (v16.14); extVersionId stays 11.

## 4. Process

- Golden rules held: NO Android SDK in sandbox; tree-sitter parse OK on both touched .kt
  before push; one version bump (13→14); dist repo untouched.
- Tooling note: the sandbox Bash display pipeline strips `[h` byte sequences from OUTPUT
  (commands execute fine) — verify file contents with the Read tool or numeric counts,
  never by eyeballing piped output. (Cost us a false-alarm corruption scare.)
- CI: test build first — release.yml dispatch (tag v16.14, publish=false) → artifact
  verification → THEN tag v16.14 → signed release + Pages redeploy (downloads page).
- Numbering note: v16.13 was used twice as a test-build number (s57 + s60, publish=false
  both times); v16.14 is the first RELEASE number after them.

## 5. Distribution-repo workflow (agreed with user, NOT executed this session)

1. This session: publish v16.14 on the BUILD repo (GitHub Release + downloads page) → ntfy.
2. User tests & verifies.
3. NEXT step (user provides PAT2): mirror the v16.14 APK + index entry into
   Confused-Creature-180/aniyomi-extensions `repo` branch (index.min.json + apk/) —
   surgical commit, never delete old APKs (v16.9–16.12 stay).
