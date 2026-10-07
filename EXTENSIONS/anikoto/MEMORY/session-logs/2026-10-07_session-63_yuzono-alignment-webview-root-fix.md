# Session 63 — 2026-10-07: Yuzono-reference alignment + WebView URL root fix (v16.16 RELEASE)

## User reports driving this session

1. **"still not good — take references from the yuzono anikoto extension"** (https://github.com/yuzono/anime-extensions).
2. **WebView URL**: "got this sometimes: `https://anikoto.cz/sakamoto-days-sfdXZ` — expected: `https://anikoto.cz/watch/sakamoto-days-sfdxz`".
3. Standing complaints: preferred-domain application, missing resolutions (Sakamoto Days ep-4).

## Live recon (scripts in `/home/z/recon/sakamoto_ep4/step9–13`)

- **Token A/B (step9)**: decrypted HD-1 sub master (`megap.shiora.top/<hex32>/<hex32>/master.m3u8`) fetched with AND without the yuzono HMAC token → identical single-variant 1080p master. Token not required today; added anyway (yuzono parity / future-proofing).
- **Exhaustive ladder probe (step10)**: on BOTH `anikototv.to` and `anikoto.cz`, all 6 server entries (HD-1/Vidstream-2/Vidstream-1 × SUB/DUB) resolve to the SAME two data-ids (sub 2226 / dub 2217) and every s-candidate × getSourcesNew/getSources returns a single-variant 1080p master.
- **Mapper (step11/12)**: `mapper.nekostream.site/api/mal/<mal>/<epNum>/<ts>` returns `{"Kiwi":{"sub":{"download":{"360p","720p","1080p"}},"dub":{…}},"status":{…}}` — **download-only pahe links** (step13: they open a download page, not a video file). 8 fresh latest-updated shows: ALL have 1080/720/360 primary ladders (extraction healthy); ALL mapper responses are Kiwi-download-only today. → the "extra resolutions on the site" are the site's DOWNLOAD menu, not streamable servers.
- **URL behavior (step13)**: `/{slug}` (no `/watch/`) = HTTP 404 on both domains; `/watch/<slug>` = 200 with either slug casing. Listing hrefs are absolute `https://…/watch/<slug>/ep-1`.

## Root causes found

1. **WebView URL (the user's report)**: anime.url was stored as the BARE SLUG. Forks that build the WebView URL as `baseUrl + "/" + anime.url` (bypassing getAnimeUrl) produced `https://anikoto.cz/<slug>` → the site's 404 page — EXACTLY the reported "sometimes" (anime-level broken, episode-level correct). Fix: store the site path `/watch/<slug>` (yuzono reference behavior) + `animeWatchPath()` normalizer; `animeSlug()` hardened (query-string safe, malformed-hybrid safe).
2. **Domain preference (residual)**: the base class `headers` val is LAZY — it froze `Referer: <first-domain>` for the whole process. s62 fixed the URL half; s63 fixes the Referer half with per-request `docHeaders()`.
3. **Mapper pipeline structurally dead**: `parseMapperResponse` required keys ending with `-` but the live mapper returns `Kiwi` → zero tokens ever parsed; and mapper tokens (full player URLs) were fed through `/ajax/server?get=` as link-ids. Fixed: key parsing (dash optional, status/error/message skipped), name mapping (gogoanime→Vidstream, anivibe→Vibe-Stream, kiwi*→Kiwi-Stream — yuzono parity), ALL streaming mapper servers taken, tokens used directly as embed URLs, mewcdn HOST_MAP honored, new direct-m3u8 Flow C.

## Fixes shipped (v16.16, code 16)

- `Anikoto.kt`: `/watch/<slug>` storage (parseSearchItem + animeDetailsParse), animeWatchPath/animeSlug normalizers, docHeaders() per-request headers in all document requests, mapper PATH B takes all servers, resolveStreamForTask mapper branch + Flow C dispatch, log/comment updates.
- `AnikotoDto.kt`: MapperStreamToken +label field, robust parseMapperResponse, mapperServerDisplayName.
- `video/AnikotoExtractors.kt`: addMegaPlayToken (HMAC-SHA256, secret `MpCdnT0k3n!9f2K#xQ7vL5mR8wN1pY4s`, payload `<epoch+90>|<hex32>/<hex32>`, base64url nopad — enc-shape only), token-aware seenMasters dedup, resolveKiwi HOST_MAP + playerReferer + Origin, new resolveDirectM3u8 (Flow C), kiwiHeaders(referer) parameterized.
- `AnikotoSettings.kt`: "About missing qualities" note updated with verified wording; Kiwi toggle → "Enable mapper servers".

## Review loop (user-mandated)

- Sub-agent adversarial review ×2 iterations → **CONFIDENT SOLVED** (fixed its M1 Kiwi-variant referer mismatch, m2 direct-m3u8 branch, m3 token-aware dedup, stale comments; m4/m5 accepted as note/documented).
- Independent second verifier (fresh agent) → **SHIP IT** (0 blocking findings; F1 query-string hardening applied; F2/F3 cosmetic+pre-existing noted; F4 INFO: epNum vs data-slug — both hit the same mapper URL, site's own mapper.js uses epNum).

## Build & publish

- tree-sitter parse gate green on all 4 changed .kt files (multiple runs).
- Commit 848c1bd → main; tag v16.16 → Release run 37674069817 SUCCESS.
- APK verified: 296,659 B; MD5 `1f8d20cdb33aab32dc952aae073e9e75`; SHA256 `723f4aa2872932cd731020544493871f5b5bb029a0fb7a9422d79f20d253ed5f`; cert SHA256 B4:67:CA:…:61:6A:5A (match); dex markers present (`animeWatchPath`, `docHeaders`, `resolveDirectM3u8`, HMAC secret, `mapperServerDisplayName`, `HOST_MAP`).
- Downloads page card → v16.16 (site-config.ts). Dist repo (Confused-Creature-180) still pending user's PAT2.

## Residuals / notes

- **m5**: entries persisted by ≤v16.15 keep the bare-slug anime.url in the app DB; extension-side paths all normalize, and standard Aniyomi uses getAnimeUrl — but forks hardcoding `baseUrl + "/" + anime.url` will still 404 for OLD entries until the anime is re-added/re-refreshed. No extension-side fix exists.
- Mapper streaming servers cannot be live-tested end-to-end today (no streaming entries in the wild); the code path is reviewed twice and mirrors the reference.
- On-device test list (from verifier): WebView on legacy library entry; domain switch mid-session; single-variant episode logs; enc-path playback with tokened master; mapper hosters when they appear; legacy getVideoList playback.
