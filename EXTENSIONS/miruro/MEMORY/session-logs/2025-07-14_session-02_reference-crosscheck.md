# Session 02 — Reference Cross-Check + Corrected Analysis

> Date: 2025-07-14 (America/Los_Angeles)
> Branch: `miruro`
> Task: User corrected my session-01 analysis — I missed servers (there are ~11 for sub/hsub, ~8 for dub) and didn't check the reference repositories. Re-analyze using the yuzono reference extension + the user's test episode URL.

## What the user said

> You did not properly check out the servers which were available because let me tell you there were quite a lot of servers available actually. ... ~11 servers for the sub and H sub versions and 8 servers for the dub version. ... Here is the episode which I tested it on: https://www.miruro.to/watch/185542/skeleton-knight-in-another-world-season-2?ep=1

## What I did

1. **Cloned the reference repo**: `yuzono/anime-extensions` → `/home/z/anime-extensions-ref`. Found an existing **`src/en/miruro`** extension (2234 lines) — a complete working reference for this exact site.
2. **Read the yuzono miruro extension thoroughly**:
   - `Miruro.kt` (2234 lines) — main source: pipe API, catalog, episodes, videos, 15 settings, AniLib fallback, cookie farming, debug interceptor.
   - `MiruroExtractor.kt` (379 lines) — XOR+gzip response decryption, proxy URL building (FNV-1a hash, vault01/02.ultracloud.cc), embed routing (MegaCloud/RapidCloud/OmniEmbed).
   - `MiruroDto.kt` — DTOs: AnimeMediaDto, SourcesResponseDto (streams+subtitles), StreamDto (type/url/quality/resolution/codec/audio/fansub/referer), ConfigResponseDto (streaming map + providerOrder + capabilities), StatusPageDto, CachedMirrorsDto.
   - `MiruroFilters.kt` — 8 filter categories: Sort, Genres (18 TriState), Tags (~300 TriState), Format (7 checkbox), Year, Season (4), Status (6), Dub Language (17).
   - `MiruroBrowserFingerprintInterceptor.kt` — Chrome 148 desktop header shaping (WAF custom-rule bypass).
   - `build.gradle` — extName='Miruro.tv', isNsfw=true, extVersionCode=5, deps: omniembedextractor, anilib, m3u8server, cloudflareinterceptor, megacloudextractor, rapidcloudextractor.
3. **Live-probed miruro.tv** with curl + full browser fingerprint headers:
   - Homepage → HTTP 403, `cf-mitigated: challenge`, "Just a moment..." managed challenge.
   - Pipe API (`/api/secure/pipe?e=...`) with full sec-ch-ua/sec-fetch headers → HTTP 403, `cf-mitigated: challenge`.
   - Discovered: Cloudflare has **upgraded** since the yuzono interceptor was written — it's now a managed challenge (not just the WAF block the yuzono comment describes). The extension needs BOTH the fingerprint interceptor AND WebView CF solving.

## Key findings (CORRECTED)

### What I got wrong in session 01
| My earlier claim | Reality (from yuzono) |
|---|---|
| "Miruro uses the public Consumet API" | ❌ Miruro has its OWN pipe API (`/api/secure/pipe`); public Consumet is dead |
| "3 servers: Default/Vidstream/Gogo" | ❌ **11 providers**: AnimePahe, Anikoto, AniDao, 9Anime, Moon, Zoro, Pewe, Nun, Bun, Twin, Cog (+Dune/Kuz) |
| "2 audio types: Sub + Dub (no HSUB)" | ❌ **4 audio types**: Sub, Dub, Soft Sub (ssub), Hard Sub (h-sub) — the user was right |
| "isNsfw = false" | ❌ **isNsfw = true** (yuzono sets it) |
| "Backend URL unknown, needs discovery" | ❌ The backend IS the site itself (`/api/secure/pipe` on miruro.tv) |

### Root cause of my errors
I analyzed the **open-source frontend repo** (`Miruro-no-Kuon`) which only shows the UI's 3 hardcoded source buttons (Default/Vidstream/Gogo in MediaSource.tsx). I did NOT check the **reference Aniyomi extensions repo** (yuzono) which contains a complete working extension revealing the true pipe API + 11 providers + 4 sub-types. I violated `PROJECT_RULES.md` §1 ("verify before trusting") and the `SHARED/README.md` guidance to use reference repos as cross-checks.

### The corrected architecture
- **Backend**: Miruro's own pipe API at `/api/secure/pipe?e=<base64url-json-payload>`. The `e=` param is base64url JSON: `{path, method, query, body, version:"0.2.0", timestamp}`.
- **Response crypto**: header `x-obfuscated: 2` → base64url decode → XOR with PIPE_KEY (16 bytes, cycled) → gunzip → JSON.
- **PIPE_KEY** = hex `71951034f8fbcf53d89db52ceb3dc22c`; **PROXY_KEY** = hex `a54d389c18527d9fd3e7f0643e27edbe` (both in the public frontend JS — obfuscation, not security).
- **Pipe paths**: `search/browse` (trending/recent/filtered/search), `search` (text), `info/<anilistId>` (details), `episodes` (episode list per provider+subType), `sources` (video streams for one episode).
- **11 providers** with per-provider capabilities (sub/dub/ssub/download/skipTimes/thumbnails) from a live config response.
- **4 audio types**: sub, dub, ssub (Soft Sub), h-sub (Hard Sub).
- **Video proxy**: vault01.ultracloud.cc / vault02.ultracloud.cc, selected by FNV-1a hash of `"$episodeId|$anilistId"` mod 2. URL = `{base}{xorEncode(url,PROXY_KEY)}~{xorEncode(referer,PROXY_KEY)}/pl.m3u8`.
- **Embed extractors**: MegaCloud (megacloud.tv/club), RapidCloud (rapid-cloud.co/scloud), OmniEmbed (everything else: kwik, mp4upload, doodstream...), M3u8Integration (HLS local proxy).
- **AniList fallback**: `graphql.anilist.co` via `aniyomi.lib.anilib` when the pipe API fails.
- **Cookie farming**: `ensureBaseVisit()` (GET / once) + `ensureWatchPageVisited(anilistId)` (GET /watch/<id> per anime) before pipe calls — Miruro's CF punishes bare API calls with no prior navigation.
- **15 settings**: mirror, provider, sub-type, stream type, quality, title style, include-all-sub-types, strip-html, merge-providers, episode-sort, description-truncation, show-provider-in-scanlator, include-all-providers, filler-display, filler-mark-mixed, include-nsfw.

## Stage Summary

- ★ Site analysis CORRECTED in `MEMORY/sites/site-analysis.md` — now verified against the yuzono reference extension + live probe.
- ★ EXTENSION.md updated with the corrected identity (isNsfw=true, Miruro.tv name), 11 providers, 4 sub-types, pipe API architecture, required libs, crypto keys.
- ★ The yuzono `src/en/miruro` extension is identified as the authoritative reference to adapt from (NOT copy — per project rules, build from understanding, cross-check only).
- ⏳ Awaiting user verification of the corrected analysis.
- ⏳ Next (after verification): Step 1.5 — analyze the `sources` pipe response shape + per-provider extraction flows (MegaCloud/RapidCloud/OmniEmbed) in detail, then build.

## Honest verification status

- ✅ Reference-verified (yuzono working extension): pipe API structure, crypto, 11 providers, 4 sub-types, 8 filters, proxy, embed extractors, settings, AniLib fallback.
- ✅ Live-verified: CF managed challenge on miruro.tv (cf-mitigated: challenge), the 4 official mirrors, miruro.com landing page.
- ❌ NOT live-verified: the actual `sources` response for the user's test episode (ep 1 of anilist 185542) — CF blocks curl/agent-browser. The yuzono extension is a working reference so the structure is trusted, but on-device verification will happen during Step 4 (playback).
