# AnimeKhor.org — Site Analysis

> Status: VERIFIED (2026-09-13) · Analyst: session animekhor-01
> Method: Cloudflare blocks ALL direct sandbox access (curl 403 challenge, patchright headless
> AND headful+Turnstile-click 403, datacenter IP). Live verification done through the
> **z-ai page_reader** function (in-house reader, its egress passes Cloudflare) for HTML
> pages, plus **plain curl to the video hosters** (most are NOT CF-protected).

## 1. Base URL + domain

- **Canonical: `https://animekhor.org`** — verified live (page_reader + jina reader both render it).
- `https://animekhor.xyz` (the base URL in the 2024-era official extension) → **301 → animekhor.org**.
- Both domains on Cloudflare (104.21.x / 172.67.x).
- Site calls itself **"AnimeKhor"**; banner: "New Domain AnimeKhor animekhor.org From animekhor.xyz".
- Content: Chinese anime/donghua + some anime, English subbed (+ Indonesian subs), tagline
  "Watch Latest Chinese Anime/Donghua English Subbed or Dubbed For Free Online".

## 2. URL structure (all verified live)

| Page | URL | Notes |
|---|---|---|
| Anime list (catalog base) | `/anime/?page=N&order=popular\|update` | 20 articles/page; `div.hpage > a.r` = next |
| Filter browse | `/anime/?page=N&genre[]=&studio[]=&status=&type=&sub=&order=` | same article markup; **filters ignored by site when `?s=` used** |
| WordPress search | `/page/N/?s=QUERY` | ⚠️ returns mostly EPISODE posts (see §3) |
| Series details | `/anime/<slug>/` | e.g. `/anime/fog-hill-of-five-elements/` |
| Episode/watch | `/<slug>-episode-N-english-subtitles/` | NO `/anime/` prefix; also `-episodes-N-to-M-` ranged posts |
| Images | `https://i0/i1/i2.wp.com/animekhor.org/wp-content/uploads/...` | Jetpack CDN; `?resize=` suffix should be stripped |

## 3. Search mechanism (the big quirk)

The live `?s=` search returns **episode posts, not series** (e.g. "naruto" → 29 episode posts,
0 series). WordPress has no custom "anime" post type here (`/wp-json/wp/v2/types` lists only
`post` + standard) and `/anime/?title=` is ignored (tested).

**Design used in the extension**: map every episode-result back to its series:

```
https://animekhor.org/<slug>-episode-N-.../       →  https://animekhor.org/anime/<slug>/
https://animekhor.org/<slug>-episodes-N-to-M-...  →  https://animekhor.org/anime/<slug>/
```

- Verified live: `/anime/renegade-immortal/` and `/anime/tales-of-herding-gods/` (derived from
  search results) both → 200 series pages.
- Regex: `animekhor\.org/([a-z0-9-]+)-episode` (cut before `-episode` covers both forms).
- Title: `a[title]` attr, tail stripped with `\s+Episodes?\s+\d+.*$`
  ("Renegade Immortal Episode 158 Subtitles [ENGLISH + INDONESIAN]" → "Renegade Immortal").
- De-dup by series URL (search returns many episodes of the same series). Results deduped,
  unmappable items skipped (movies without episode markers would be — none seen in tests).
- Result articles: `article.bs > div.bsx > a.tip` — series results carry `div.tt` with an
  ownText title; episode results have `div.tt` with ONLY an h2 child (ownText empty — do NOT
  use the upstream `!!` non-null ownText there; fall back to `a[title]`).

## 4. Server-list paths (single path, verified)

Episode page → `select.mirror > option[data-index]` (no `ul.mirror a[data-em]` on this site).
Each option's `value` = **base64** of either an `<iframe …>` HTML snippet or a bare URL string
(some are uppercase `<IFRAME SRC=…>`) — decode with Base64.DEFAULT, parse with Jsoup, take
`iframe[src]` (fallback `meta[itemprop=embedUrl]`, fallback: decoded string is itself a URL).

Live hoster sets (differ per episode age — old episodes keep legacy embeds, new episodes use new ones):

### Episode "Fog Hill of Five Elements" ep 3 (June 2023 upload) — legacy set
| Label | Embed | Verdict |
|---|---|---|
| Ads Free Player (new) | `//justtesting.animeabc.xyz/embed/…` | broken upstream too — skip |
| ok.ru《Ads Free》 | `//ok.ru/videoembed/5104872720992` | ✅ data-options extraction verified |
| Player | `//animeabc.xyz/e/…` | fingerprint redirect loop — skip |
| StreamWish | `https://streamwish.to/e/…` | ported (guard page from sandbox; upstream logic) |
| StreamHide | `https://ahvsh.com/e/…` | ported (vidhide family; host refuses datacenter IP) |
| StreamSB | `https://sbsonic.com/e/…` | ported (vidhide family; same) |
| Doodstream | `https://d000d.com/e/…` | ported (embed 200 but this file deleted — "Video not found") |

### Episode "Tales of Herding Gods" ep 100 (fresh upload) — modern set
| Label | Embed | Verdict |
|---|---|---|
| VidPlayer [MULTI SUB] | `dailymotion.com/embed/video/<id>` | ✅ metadata API verified (m3u8 + subs) |
| ok.ru《Ads Free》 ×2 | ok.ru embeds | extractor fine; THIS video geo/restriction-blocked (per-video issue) |
| RumblePlayer | `rumble.com/embed/v7da5t0/` | ported (hls-vod pattern; embed 403 from sandbox) |
| DPlayer | `<h2>Video Not Available</h2>` | dead option — per-mirror tolerance required |
| Player [MULTI SUB] | `turbovidhls.com/t/` | **no video id in the iframe src** — cannot resolve, skip |
| CloudPlayer | `animekhor.upns.live/#dtoav3` | SPA player; `/api/v1/player?t=` → "Token is invalid" (needs deeper RE) — deferred |
| FilePlayer | `animekhor.p2pstream.vip/#8cw3uk` | same SPA family as upns — deferred |
| DaraPlayer | `vidara.to/e/<id>` | ✅ **fully verified**: `POST https://vidara.to/api/stream {"filecode":id,"device":"web"}` → `{streaming_url: master.m3u8, subtitles[]}` |
| VGPlayer | `bysekoze.com/e/<id>` | Vite SPA ("Byse Frontend") — deferred |
| AbyssPlayer | `player.abyssplayer.com/<id>` | ✅ **fully verified**: page has `datas="<b64>"` → `POST enc-dec.app/api/dec-abyss` → sources 480p/720p mp4s |

## 5. Audio types

Site is sub-focused: badges show `Sub` (occasionally dub for select shows; episode posts carry
"[ENGLISH + INDONESIAN]" subtitle language markers in the title). Videos are labeled by the
MIRROR NAME + quality (the site has no per-video sub/dub distinction) — extension labels
mirror-quality only. No HSUB concept on this site.

## 6. CDN/WAF

- **Main site: Cloudflare MANAGED CHALLENGE** (`cf-mitigated: challenge`, Turnstile checkbox) on
  EVERY path (HTML, admin-ajax, wp-json, feed, sitemap). Direct sandbox curl = 403 always.
  The Aniyomi app's inherited `client` (CloudflareInterceptor/WebView) solves this on-device —
  same model as anikototv.to.
- Video hosters: most do NOT CF-challenge from the sandbox (dailymotion/ok.ru/vidara/abyss/d000d
  answered); ahvsh.com + sbsonic.com refuse datacenter IPs at TCP level; rumble 403s;
  streamwish serves a JS "Loading…" guard page (sets cookie via `/main.js` — obfuscated;
  upstream solves via synchrony deobfuscator, we defer = graceful skip on guard).

## 7. PNG wrapping

Not seen on any hoster sampled. No LocalProxyServer needed.

## 8. Identity fields (confirmed)

| Field | Value |
|---|---|
| Name | "AnimeKhor" (site) → extension: "AnimeKhor 180" (publisher convention) |
| Language | en |
| NSFW | false (site has an "Adult" genre tag but is not an NSFW site; official ext also false) |
| Domain | https://animekhor.org |
| Package | eu.kanade.tachiyomi.animeextension.en.animekhor (source) / …animekhor180 (applicationId) |
| extClass | eu.kanade.tachiyomi.animeextension.en.animekhor.AnimeKhor |
| versionId | 1 |

## 9. Open questions / deferred

1. **SPA hosters** (upns.live, p2pstream.vip, bysekoze.com): need JS-bundle RE of their player
   APIs (`/api/v1/player` token flow). upns returns "Token is invalid" without the right token
   derivation. UKIKU fork solves upns via WebView resource-listening (`&dl=1` + capture
   master.m3u8) — portable later using AniKoto's WebViewFetcher pattern.
2. **StreamWish guard**: if streamwish proves broken on-device (possible — guard page), next
   iteration adds the synchrony-style deobfuscation or a WebView fetch (AniKoto's WebViewFetcher).
3. **Rumble**: not verifiable from this sandbox (403). Upstream logic is a simple derived URL —
   if it fails on-device, likely a referer issue — test on device.
4. Filters were parsed from the live form but the FILTER UI behavior on-device (dynamic fetch)
   is untested until the user runs the debug APK.

## 10. Research artifacts

- `/home/z/probe-ak/` (sandbox-only, not committed): page_reader captures
  (`pr-*.json`, `ep3-reader.html`, `ep100-reader.html`, `pr-ak-*.json`), official 2024 source
  (`official-src/`), yuzono current source (`yuzono-src/`, `yuzono-libs/`), Turnstile solver
  (`solve_cf.py` — proves the challenge can't be passed from this datacenter IP).
- Official 2024 extension source: aniyomiorg/aniyomi-extensions@42159cc `src/en/animekhor/`
  (Apache-2.0) — historical reference.
- Current maintained implementation: yuzono/anime-extensions `src/en/animekhor/` (master,
  mass-bumped 2026-08-21) — reference for theme + hoster routing (theirs handles the OLD
  hoster set; ours adds the modern verified hosters).
