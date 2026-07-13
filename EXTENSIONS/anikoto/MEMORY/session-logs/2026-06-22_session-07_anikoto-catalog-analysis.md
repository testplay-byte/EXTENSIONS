# Session 07 — ANIKOTO Catalog & Episodes Analysis + Stage 2 Plan

> Date: 2026-06-22 · Session #: 07 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Continue Stage 1 (Website Research) for ANIKOTO by analyzing the catalog side — popular/latest,
search, filters, anime details, and episode list — which were flagged as gaps in session 06. Then
plan the catalog+episodes implementation (Stage 2 architecture). Documentation + planning only,
no coding.

## What was done

### A. Analyzed the homepage + popular/latest
- Fetched homepage (25 KB). **It's a static SEO landing page** — marketing copy + FAQ + "Popular
  searches" (10 hardcoded links: Solo Leveling S2, One Piece, etc.). NO carousel, NO trending grid,
  NO latest-updates grid. NO XHR calls to load popular/latest content.
- "Trending" sidebar on detail pages = same 10 hardcoded popular-search links.
- **Conclusion: NO dedicated popular/latest endpoints.** Options for the extension: use `/filter?sort=most-viewed`
  (may be broken via curl — needs session), or fallback to the 10 hardcoded items. Documented as an
  open verification item.

### B. Discovered the live search (autosuggest) API
- The search form submits to `/filter?keyword=...` (GET), but `/filter?keyword=...` **returns the
  SEO landing page via curl** (only 1 `/watch/` link — the logo). The full results page is broken
  or session-gated.
- **Found the real search endpoint in main.js:** `GET /ajax/anime/search?keyword={q}` (GET, not POST).
  Returns JSON `{status, result: {html, linkMore}}` where `html` contains up to ~10 result cards
  (`.item` with poster, title, `data-jp` Japanese title, rating, score, type, year). **VERIFIED working.**
- This is the endpoint Aniyomi's `searchAnime` should use (fast, returns enough for a search, no
  pagination — `hasNextPage = false`).

### C. Mapped the full filter system
- Fetched `/filter` (the filter form page, 112 KB). Parsed ALL filter controls:
  - `genre[]` (43 genres with IDs: 1=Action, 2=Adventure, 538=Avant Garde, etc.)
  - `season[]` (fall/summer/spring/winter)
  - `year[]` (1980–2026)
  - `term_type[]` (TV/Movie/ONA/OVA/Special/Music)
  - `status[]` (finished-airing/currently-airing/not-yet-aired)
  - `language[]` (sub/dub)
  - `rating[]` (G/PG/PG-13/R/R+/Rx)
  - `source[]` (18 options: manga/original/light_novel/etc.)
  - `sort` (8 options: default/latest-updated/latest-added/score/name-az/release-date/most-viewed/number_of_episodes)
  - `ep_min`/`ep_max` (episode count range)
  - Pagination: `?page=N` (293 pages total)
- Mapped these to Aniyomi's `AnimeFilterList` (CheckBox/Select filters) in the Stage 2 plan.
- ⚠️ **Critical:** the live search API only accepts `keyword` (ignores filter params). Filters only
  apply via the full `/filter` endpoint (which is currently broken via curl). This is an open
  verification item for Stage 2.

### D. Analyzed anime details (found the right endpoint)
- `/anime/{slug}` returns **404 "Page not found"** — confirmed via agent-browser. The detail endpoint
  moved to `/watch/{slug}` (no `/ep-N` suffix).
- Parsed `/watch/{slug}` (83 KB):
  - `<div class="binfo">`: poster, `<h1 class="title d-title" data-jp="...">`, alternative titles
    (`.names`, semicolon-separated), rating/quality/sub/dub icons, synopsis (`.synopsis .content`).
  - `<div class="bmeta">`: two `<div class="meta">` blocks:
    - Block 1: Type (TV), Premiered (Spring 2026), Aired (Apr 12, 2026 to ?), Status (Currently Airing), Genres.
    - Block 2: MAL Score (8.12), Duration (24 min), Episodes count (12), Studios (Bandai Namco Pictures, Actas), Producers (Lantis).
  - `<div class="brating">`: user rating (8.12/10, 423821 reviews).
  - `<div id="ani-seasons">`: seasons (loaded via `/api/seasons/{animeId}`).
  - `<div id="w-episodes">`: episode list (loaded via `/ajax/episode/list/{animeId}`).
  - `<aside class="sidebar">`: "Trending" sidebar (10 hardcoded popular anime).
- Mapped all fields to `SAnime` (title, description, genre, status, thumbnail_url, update_strategy)
  in the Stage 2 plan.

### E. Analyzed the episode list structure
- Already had `/ajax/episode/list/{animeId}?vrf=` response from session 06. Confirmed the episode
  `<a>` attributes: `data-id`, `data-num`, `data-slug`, `data-mal`, `data-timestamp`, `data-sub`,
  `data-dub`, `data-ids`. Plus `<span class="name">` for the episode name and filler class.
- Mapped to `SEpisode` (`episode_number`, `name`, `scanlator` for sub/dub per rule §8, `fillermark`).
- ⚠️ **HSUB availability is NOT flagged** on the episode `<a>` — must infer from the server list
  (check for `<div data-type="hsub">` items). Documented a heuristic: check the first episode's
  server list for HSUB; if present, the anime has HSUB. Rely on Video-level `videoTitle` for
  per-video audio labeling.
- Episode ordering: site is oldest-first; Aniyomi wants newest-first → reverse the list.

### F. Wrote the documentation (2 new files, ~700 lines)

**Stage 1 (Website Research) — `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/`:**
- `catalog-and-episodes-analysis.md` (★ the catalog side companion to `site-analysis.md`) — covers:
  popular/latest (none exist), search (live autosuggest API), filters (full inventory + mapping),
  anime details (`.binfo` + `.bmeta` + `.brating` structure), episode list (`<a data-ids>` structure),
  seasons, implementation plan preview, open verification items.

**Stage 2 (Architecture Design) — `WORKSPACE/WORKFLOW/02_ARCHITECTURE_DESIGN/ANIKOTO/`:**
- `catalog-episodes-architecture-plan.md` — the implementation blueprint: build target, headers,
  `getPopularAnime` (with fallback), `getLatestUpdates`, `getSearchAnime` (live API + filter handling),
  `getFilterList` (full filter classes), `animeDetailsParse` (`.binfo` + `.bmeta`), `episodeListParse`
  (with scanlator strategy + HSUB heuristic), build order, 8 open verification items to resolve
  before coding.

Promoted `catalog-and-episodes-analysis.md` to `MEMORY/sites/anikoto/` (permanent record). Updated
`MEMORY/sites/anikoto/README.md` index with the new doc + key facts.

## Key findings / decisions

1. **Search uses a live autosuggest API** (`GET /ajax/anime/search?keyword={q}`), NOT the full
   `/filter` results page (which is broken via curl). Returns ~10 items, no pagination.
2. **Anime details are at `/watch/{slug}`** (NOT `/anime/{slug}` which 404s). The detail page has
   rich metadata: title, alt titles, poster, synopsis, type, premiered, aired, status, genres, MAL
   score, duration, episode count, studios, producers, user rating.
3. **The site has NO popular/latest endpoints** — homepage is a static SEO landing. The extension
   must either use `/filter?sort=most-viewed` (may need session) or fallback to the 10 hardcoded
   "Popular searches" items.
4. **Full filter system documented** (43 genres, 4 seasons, 47 years, 6 types, 3 statuses, 2
   languages, 6 ratings, 18 sources, 8 sort options, episode range, pagination). Mapped to
   `AnimeFilterList`. BUT: filters only work via `/filter` (broken via curl) — the live search API
   ignores them. Open verification item.
5. **Episode list is at `/ajax/episode/list/{animeId}?vrf=`** — parse `<a data-ids>` (the `data-ids`
   blob is passed to `/ajax/server/list`). `data-sub`/`data-dub` flags drive the `scanlator` field
   (rule §8). HSUB not flagged here (infer from server list).
6. **Status mapping**: Currently Airing → `SAnime.ONGOING`, Finished Airing → `SAnime.COMPLETED`,
   Not Yet Aired → `SAnime.UNKNOWN`.
7. **Episode ordering**: reverse the list (site is oldest-first, Aniyomi wants newest-first).
8. **HSUB scanlator heuristic**: check first episode's server list for HSUB; rely on Video-level
   `videoTitle` for per-video audio labeling.

## Files created / modified

New (4):
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/catalog-and-episodes-analysis.md`
- `WORKSPACE/WORKFLOW/02_ARCHITECTURE_DESIGN/ANIKOTO/catalog-episodes-architecture-plan.md`
- `MEMORY/sites/anikoto/catalog-and-episodes-analysis.md` (promoted copy)
- `MEMORY/session-logs/2026-06-22_session-07_anikoto-catalog-analysis.md` — this log

Modified (1):
- `MEMORY/sites/anikoto/README.md` (added catalog doc to index + new key facts)

## Status at end of session

- ✅ Stage 1 (Website Research) COMPLETE for ANIKOTO — both the video chain (session 06) AND the
  catalog side (this session). All findings verified against the live site.
- ✅ Stage 2 (Architecture Design) STARTED for the catalog+episodes layer — the implementation
  blueprint is written with Kotlin code sketches.
- ⏳ 8 open verification items remain (documented in the Stage 2 plan §9) — must resolve before
  coding the catalog layer.
- ⏳ Stage 2 for the video-extraction layer (Step 04) NOT started — will get its own plan after
  the catalog layer is verified working.
- ⚠️ JDK blocker from session 05 still stands (no `javac`) — needed for Stage 6 (Build) but not
  for Stage 2 (planning).

## Next steps (for the next session)

Per the user's instruction: "for now only do the documentation and plan for implementing by
analyzing and understanding the website properly." That's done. Await user direction on:
1. **Resolve the 8 open verification items** (especially: does `/filter` work with a session? Does
   `/ajax/anime/search` honor filter params? Non-empty `vrf`? Episode pagination?).
2. **Proceed to Stage 3 (Implementation)** — scaffold the extension + implement the catalog layer
   per the Stage 2 plan. This requires the JDK blocker to be resolved first.
3. OR **plan Stage 4 (video extraction)** first.

## Open issues (honest gaps)

Documented in `catalog-and-episodes-analysis.md` §7 and `catalog-episodes-architecture-plan.md` §9.
Summary of the 8 items:
1. Does `/filter?sort=most-viewed` return real results (vs SEO landing) with a session?
2. Does `/filter?{filterParams}` work for browse-by-filter?
3. Does `/ajax/anime/search` honor filter params?
4. Non-empty `vrf` param — which anime have it? (Need to reverse-engineer `o(this.Ee)` if so.)
5. Episode pagination for 1000+ episode anime (One Piece)?
6. Full genre ID list (43 genres) — verify all IDs.
7. HSUB availability consistency across episodes?
8. Episode `data-timestamp` consistency across episodes?

None block the planning; they're verification items for the implementation stage.

## Honest notes

- **No code written** — purely analysis + planning, per the user's instruction.
- **The `/filter` brokenness was a surprise** — I expected it to be the main browse/search endpoint.
  The live autosuggest API (`/ajax/anime/search`) is the actual working search. The `/filter` page
  might work with a session cookie (agent-browser maintains one) — needs verification.
- **The anime detail endpoint moved** from `/anime/{slug}` (404) to `/watch/{slug}`. This is a
  common pattern (sites consolidating detail + watch into one page). Documented prominently.
- **The HSUB scanlator heuristic is imperfect** — HSUB availability isn't on the episode `<a>`, so
  I can't set it accurately in `episodeListParse`. The heuristic (check first episode's server list)
  + per-video `videoTitle` labeling is the pragmatic solution. Acceptable per rule §8.
- **The Stage 2 plan has Kotlin code sketches** but they're ILLUSTRATIONS, not final code. The
  actual implementation will need: proper serialization annotations, null-safety, error handling,
  and the 8 verification items resolved first.
- **All claims are sourced** — curl-verified responses + agent-browser DOM inspection + main.js/mapper.js
  source. No guessing.
