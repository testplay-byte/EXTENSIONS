# Session 02 — MKissa: Fix Details Query + Episode Metadata Enrichment

> Date: 2027-06-29 · Extension: MKissa 180 (mkissa.to) · Session: 02 · Status: ✅ catalog + details + episodes + metadata enrichment working

## Goal

The user reported that episode thumbnails/descriptions/titles don't show and there were "issues here and there with accessing". Fix the bugs + implement the deferred EpisodeMetadataFetcher.

## Bugs found + fixed

### Bug 1: DETAILS_QUERY GraphQL validation error (CRITICAL — caused details page to fail)
- **Symptom**: `animeDetailsParse` returned all-null fields. The details page would show no title, no description, no genres, no status.
- **Root cause**: My `DETAILS_QUERY` asked for subfields on `season`, `airedStart`, and `availableEpisodes` (e.g. `season { quarter year }`). But the GraphQL schema treats these as **scalar Object types** — they MUST be queried WITHOUT subselections. Querying with subselections causes `GRAPHQL_VALIDATION_FAILED` and the entire query returns `{data: {show: null}}`.
- **Discovery**: I tested all 5 API calls via curl (simulating exactly what the Kotlin code sends). Popular/latest/search/episodes all worked, but details returned nulls. The raw response showed the GraphQL errors.
- **Fix**: Changed `DETAILS_QUERY` to query `season`, `airedStart`, `availableEpisodes` as scalars (no `{ ... }`). The server returns the full nested JSON object as a scalar, which kotlinx.serialization decodes into the DTO's nested data classes. Verified the fix works — all detail fields now populate correctly.
- **Why the allanime reference didn't have this bug**: the reference queries these fields as scalars already (I introduced the bug by adding subselections when writing my own query).

### Bug 2: Double-`use` on Response in getEpisodeList
- **Symptom**: Would have caused "Response already closed" exceptions at runtime.
- **Root cause**: My `parseJson` extension calls `use { }` on the Response internally. But in `getEpisodeList`, I wrote `detailResponse.use { it.parseJson<>() }` — wrapping `parseJson` in another `use`, which would double-close the Response.
- **Fix**: Removed the outer `.use { }` — just call `detailResponse.parseJson<>()` directly (the internal `use` handles closing).

## Features implemented

### Episode metadata enrichment (the user's main complaint)
- **Created `metadata/EpisodeMetadataFetcher.kt`** — Anikage primary + Jikan fallback, OkHttp-only (no WebView needed since both APIs work with plain OkHttp).
- **Strategy**:
  1. Extract the **AniList media ID** from the anime's thumbnail URL via regex `bx(\d+)-` (e.g. `bx182300-...jpg` → `182300`). All mkissa.to thumbnails come from AniList, so this works for every anime.
  2. **Anikage** (`anikage.cc/api/media/anime/<anilistId>/episodes`) — PRIMARY. Returns per-episode `number`, `title`, `description`, `img` (thumbnail), `airDate`, `isFiller`. One call gives all 3 metadata types.
  3. **Jikan** (`api.jikan.moe/v4/anime?q=<title>&limit=1` → `mal_id` → `/episodes`) — FALLBACK for titles + air dates when Anikage has no data. Rate-limited to 1 req/sec (added `Thread.sleep(1100)` between search + episodes calls).
- **Merge priority**: Thumbnail = Anikage.img → anime cover (fallback). Title = Anikage.title → Jikan.title. Description = Anikage.description. Air date = Anikage.airDate → Jikan.aired.
- **Wired into `getEpisodeList(anime: SAnime)`** (override the suspend version):
  1. Fetch the detail page → extract thumbnail URL (for AniList ID) + title (for Jikan fallback).
  2. Fetch episodes (existing logic).
  3. Skip enrichment if ALL 3 toggles are OFF (zero API calls — fast).
  4. Enrich each episode with thumbnail (`preview_url`), description (`summary`), title (`name` = "EP N - title").
- **Respects 3 user toggles** (all default ON): Load episode thumbnails, Load episode titles, Load episode descriptions.
- **Never throws** — on any error, returns empty map, episodes load without enrichment.
- **Caches** by anilistId (or title) so subsequent episode-list loads are instant.

## Testing performed

### API calls (all verified via curl with exact variables the Kotlin code sends)
- ✅ Popular (size=40, dateRange=7, page=1) → 40 items, total=500
- ✅ Latest (sortBy=Recent, limit=40, page=1, translationType=sub) → 40 items, total=24588
- ✅ Search (query="wistoria") → 2 results
- ✅ Search with genre filter (Action+Fantasy, query="demon slayer") → 5 results
- ✅ Search with sort+origin (sortBy=Top, origin=JP) → 3 results
- ✅ Details (FIXED query) → full metadata (name, description, genres, status, studios, score, season, etc.)
- ✅ Episodes → sub:13 eps (0-12), dub:9 eps

### Metadata enrichment (verified for 5 popular anime)
- ✅ All 5 have AniList IDs extractable from thumbnail URL (`bx<id>-`)
- ✅ All 5 have Anikage data with episodes
- ✅ Every episode has title + thumbnail + description
- Tested: Tongari Boushi no Atelier (147105), Wistoria S2 (182300), Slime S4 (182205), Classroom of the Elite S4 (180745), Class de 2-banme (169580)

### Edge cases
- ✅ Pagination: page 1 vs page 2 return different 40-item sets (hasNext logic correct)
- ✅ Episode 0: correctly handled (EP 0 → Sub only), appears first in ascending order
- ✅ Sub/Dub mismatch: episodes 9, 11, 12 have Sub only (no Dub); episode 0 has Sub only; episodes 1-8, 10 have both
- ✅ Special characters in search: "re:zero" works correctly

### Build verification
- ✅ Debug APK: `aniyomi-en.mkissa180-v16.2-debug.apk` (186KB, up from 160KB — metadata fetcher added ~26K)
- ✅ Package: `eu.kanade.tachiyomi.animeextension.en.mkissa180`, versionCode=2, versionName=16.2
- ✅ extClass: FULL path (no leading dot)
- ✅ Stub! count = 0
- ✅ MKissa class in DEX (164 refs)
- ✅ EpisodeMetadataFetcher class in DEX (481 refs)
- ✅ All 5 mipmap icon densities present
- ✅ APK endpoint: `GET /api/apk?ext=mkissa&type=debug` → HTTP 200, 189588 bytes

## What's next (Step 4 — video playback, still deferred)
1. Generate `mkissa-release.jks` keystore.
2. Port `WebViewFetcher` from AnimePahe (for the CF managed challenge on the watch page).
3. Implement `getHosterList`: fetch watch page via WebViewFetcher, parse the 4 server buttons (Fm-Hls, Uni, Mp4, Ok), extract video URLs per server.
4. Add server-toggle + preferred-server settings.
5. Build release APK, run the full build checklist, register as ✅ stable.
