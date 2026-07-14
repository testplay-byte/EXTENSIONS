# Session 01 — Site Analysis (Step 1)

> Date: 2025-07-14 (America/Los_Angeles)
> Branch: `miruro` (created from `main`)
> Task: Analyze https://everythingmoe.com/s/miruro and the actual target site miruro.to — homepage, latest, search, filters, details, player, servers, qualities, audio types. Document + present to user for verification.

## What I did

1. **Created branch `miruro`** from `main` in `/home/z/EXTENSIONS`.
2. **Scaffolded `EXTENSIONS/miruro/`** from `_template/` (DEV/, APK/, ANALYSIS/, MEMORY/{sites,session-logs,issues-resolutions,modules,research,workflow,TEMPORARY_MEMORY}).
3. **Analyzed `everythingmoe.com/s/miruro`** with agent-browser:
   - Confirmed it is an **aggregator INFO page** about Miruro (not the streaming site).
   - Extracted: nav links → `miruro.to` (primary), `.ru`, `.bz`, `.tv` (mirrors), `miruro.com` (mirrors list), GitHub `Miruro-no-kuon/Miruro-no-Kuon`.
   - Extracted feature tags: "Multiple sources & language, Modern interface, Long track record, Open source, Content depends on the websites it scrapes".
   - Extracted user reviews confirming: sub + dub, "soft and hard sub", AniList linking, ad-blocker recommended.
4. **Attempted to load `miruro.to`** (and all 4 mirrors) in agent-browser:
   - All 4 mirrors return Cloudflare "Just a moment..." Managed Challenge + Turnstile.
   - Headless agent-browser CANNOT pass the Turnstile (CF detects headless even after issuing `cf_clearance`).
   - `--headed` mode unavailable in this sandbox (no display).
   - curl with desktop Chrome UA → HTTP 403 challenge page on all mirrors.
   - **Conclusion:** live API verification requires on-device WebView (same as MKissa).
5. **Cloned the open-source Miruro repo** (`github.com/Miruro-no-kuon/Miruro-no-Kuon`) to `/home/z/miruro-source`:
   - React 18 + Vite 5 SPA, styled-components, Apollo Client, Vidstack player.
   - `src/hooks/useApi.ts` — the complete Consumet API layer (12 endpoints).
   - `src/hooks/animeInterface.ts` — full data shapes (Anime/Episode/Paging).
   - `src/hooks/useFilters.ts` — 6 filter categories (Genres/Year/Season/Format/Status/Sort).
   - `src/components/Watch/Video/MediaSource.tsx` — the Sub/Dub × Default/Vidstream/Gogo grid.
   - `src/components/Watch/Video/Player.tsx` — Vidstack player, aniskip.com skip times.
   - `.env.example` — `VITE_BACKEND_URL="https://public-miruro-consumet-api.vercel.app/"`.
6. **Verified the public Consumet API is DEAD** — `public-miruro-consumet-api.vercel.app` returns `DEPLOYMENT_NOT_FOUND` (HTTP 404). Known public Consumet instances (api.consumet.org, heroku) are also dead. The live backend URL is baked into miruro.to's CF-protected JS bundle.
7. **Probed `miruro.com`** (NOT CF-protected, HTTP 200, 110 KB HTML) — static "official domains" landing page confirming the 4 mirrors + features (FHD, Subbed & Dubbed, AniList Sync).
8. **Wrote `MEMORY/sites/site-analysis.md`** — complete 11-section analysis with verification status per item.
9. **Wrote `EXTENSION.md`** — identity, build, status quick-ref.

## Key findings

- **Miruro is a thin React frontend over the Consumet API** (`meta/anilist` provider). It is NOT a direct scraper of one anime site — it aggregates gogoanime/9anime sources + AniList metadata via Consumet.
- **2 audio types (SUB + DUB)** — NOT 3. No HSUB. The `dub=true` param on `episodes/{animeId}` switches audio.
- **3 sources × 2 audio = 6 combinations**: Default / Vidstream / Gogo, each for sub and dub.
- **6 filter categories**: Genres (17 options), Year (1940→next), Season (4), Format (7), Status (4), Sort (12).
- **Player = Vidstack** (HLS + MP4). Qualities: default/1080p/720p/360p.
- **Cloudflare Turnstile** on all 4 mirrors — extension needs WebView solver (MKissa approach).
- **AniList metadata comes free** from the backend (no separate enricher needed).
- **Skip times** via aniskip.com (optional, defer).
- **Open item:** the live backend URL (public default is dead) — needs on-device discovery or user-provided URL.

## Stage Summary

- ★ Step 1 (site analysis) COMPLETE and documented in `MEMORY/sites/site-analysis.md`.
- ★ Identity proposed: `Miruro 180`, package `...en.miruro180`, extClass `...en.miruro.Miruro`, versionId 1.
- ★ Architecture proposed: CF bypass (WebView Turnstile) → discover backend URL → call Consumet API directly → 3 hosters × sub/dub.
- ⏳ Awaiting user verification of the analysis (servers, audio types, filters, identity, backend URL approach).
- ⏳ Next (after verification): Step 1.5 — analyze video stream capture methods (the `watch/{episodeId}` response shape, CDN domains, Referer requirements — needs on-device or user-provided backend URL).

## Honest verification status

- ✅ Source-code verified: API structure, filters, audio types, sources, player, data shapes.
- ✅ Verified: CF Turnstile on all mirrors (curl + agent-browser).
- ✅ Verified: public Consumet API is dead.
- ❌ NOT live-verified: the actual API responses, video CDN domains, Referer requirements, qualities returned — all blocked by CF in headless. Needs on-device WebView (same constraint as MKissa).
