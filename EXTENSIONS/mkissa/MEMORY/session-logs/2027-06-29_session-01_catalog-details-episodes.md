# Session 01 — MKissa Extension: Site Analysis + Catalog + Details + Episodes

> Date: 2027-06-29 · Extension: MKissa 180 (mkissa.to) · Session: 01 · Status: ✅ catalog + details + episodes working

## Goal

Build the MKissa extension for mkissa.to, focusing on the catalog + details + episodes layers (NOT video playback yet — that's a separate follow-up). The user provided the home page, a detail page (Tsue to Tsurugi no Wistoria S2), and an episode 5 link. The user identified 4 servers (Fm-Hls, Uni, Mp4, Ok) for the later video work.

## What was done

### Step 1 — Site analysis (agent-browser + curl)
- **Opened mkissa.to/anime** in agent-browser. Confirmed: SvelteKit SPA frontend, Cloudflare in front but NOT a managed challenge on the catalog. Title "MKissa - Browse Anime - Discover Anime Series".
- **Discovered the real API**: `api.allanime.day/api` (GraphQL). MKissa's frontend calls this via GET+APQ (persisted queries). Confirmed plain curl works (no CF block on the API).
- **Captured the 4 key GraphQL operations**: queryPopular, shows (search/latest), show (details), show.availableEpisodesDetail (episodes). Verified each via direct curl + POST with full query strings.
- **Detail page analysis** (Tsue to Tsurugi no Wistoria S2, `_id=Gcou36nB8su3KWXrr`): full metadata (name, englishName, nativeName, description, genres, status, studios, score, rating, type, season, airedStart, availableEpisodes, thumbnail, banner). No MAL external links (AniList ID extractable from thumbnail URL `bx182300-...`).
- **Episodes analysis**: `availableEpisodesDetail` returns `{sub:["12","11",...,"0"], dub:["10","8",...,"1"]}` (episode strings, descending). SUB has 13 eps (0-12), DUB has 9 (missing 0 and 9).
- **Watch page analysis** (episode 5 sub — `/anime/Gcou36nB8su3KWXrr/p-5-sub`): ⚠️ **Cloudflare managed challenge** ("Just a moment..."). Confirmed the user's warning. Documented for Step 4 (video playback) — will need WebViewFetcher.
- **Audio types**: SUB + DUB only (no HSUB). The 4 servers (Fm-Hls, Uni, Mp4, Ok) noted for Step 4.
- **Wrote `MEMORY/sites/site-analysis.md`** — complete, browser-verified.

### Step 2 — Scaffold + implement catalog
- **Scaffolded `EXTENSIONS/mkissa/`** from `_template` + copied animepahe's DEV build system. Renamed module from `animepahe` to `mkissa` (kotlin package, gradle module, settings, proguard).
- **Updated `build.gradle.kts`**: extName="MKissa 180", extClass="...en.mkissa.MKissa" (FULL path), applicationIdSuffix="en.mkissa180", versionCode=1, versionId=1, keystore="mkissa-release.jks" (not yet generated).
- **Generated a temporary AI icon** (purple/magenta cat + play button) via the image-generation skill. Cropped + resized to all 5 mipmap densities + `public/mkissa-icon.png`.
- **Wrote 6 source files**:
  - `MKissa.kt` — main source class (AnimeHttpSource + ConfigurableAnimeSource). Implements popularAnime/latestUpdates/searchAnime/animeDetailsParse/episodeListParse via GraphQL POST to `api.allanime.day/api`. Fork-compat episode.url encoding (`/anime/<id>/p-<N>-<sub|dub>#<episodeString>`). getHosterList stubbed (returns empty — Step 4 deferred).
  - `MKissaSettings.kt` — 2 categories (Video playback: quality + audio + title-style; Episode metadata: thumbnails + titles + descriptions, all default ON). Matches the AnimePahe pattern exactly. All dropdowns show "Currently: %s". Metadata toggles use "external sources" wording.
  - `MKissaFilters.kt` — 6 filters (Origin, Season, ReleaseYear, SortBy, Types, Genres with 43 genres). Ported from the allanime reference (same API). All values are the exact strings the API expects.
  - `MKissaDto.kt` — kotlinx.serialization DTOs (PopularResult, SearchResult, DetailsResult, EpisodesResult).
  - `MKissaQueries.kt` — 4 GraphQL query strings (POPULAR, SEARCH, DETAILS, EPISODES).
  - `MKissaLog.kt` — logcat-only logger (tag "MKissa").

### Step 3 — Episodes implementation
- **episodeListParse**: fetches `availableEpisodesDetail` (sub + dub lists). Builds a map of episodeNumber → available audio types. Emits ONE SEpisode per unique episode number with `scanlator` showing all available audio types ("Sub", "Dub", or "Sub • Dub") per rule §8. Ascending order. Fork-compat episode.url.
- **Episode URL**: `/anime/<id>/p-<N>-<translationType>#<episodeString>` — valid path (resolves to baseUrl, no DNS error in legacy forks) + fragment carries the raw episodeString.

### Build verification
- **Debug APK built successfully** in 34s: `aniyomi-en.mkissa180-v16.1-debug.apk` (160KB).
- **Build checklist ALL PASS**:
  - ✅ Package: `eu.kanade.tachiyomi.animeextension.en.mkissa180`, versionCode=1, versionName=16.1
  - ✅ extClass: `eu.kanade.tachiyomi.animeextension.en.mkissa.MKissa` (FULL path, no leading dot)
  - ✅ Stub! count = 0 (stubs are compileOnly, NOT in APK)
  - ✅ MKissa class in DEX (133 refs)
  - ✅ All 5 mipmap icon densities present
- **Copied to `EXTENSIONS/mkissa/APK/`**.

### Webpage integration
- **Added MKissa to `src/app/page.tsx`** EXTENSIONS array (status: "wip", availableBuilds: ['debug']).
- **Added `mkissa` to `KNOWN_EXTENSIONS`** in `src/app/api/apk/route.ts`.
- **Verified via agent-browser + VLM**: webpage shows 3 cards (AniKoto, AnimePahe, MKissa). MKissa card has purple icon, red "In Progress" badge, v16.1/Build 1/June 29 2027, only "Download Debug APK" button (no release — no keystore yet). No rendering errors.
- **APK endpoint works**: `GET /api/apk?ext=mkissa&type=debug` → HTTP 200, 163698 bytes. Release correctly returns 404 (no keystore).

### Documentation
- **Wrote `EXTENSIONS/mkissa/EXTENSION.md`** — full quick-ref (identity, build, status, key files, rules).
- **Wrote `EXTENSIONS/mkissa/MEMORY/sites/site-analysis.md`** — complete browser-verified site analysis.
- **Registered MKissa in `MEMORY/EXTENSIONS.md`** — status 🚧 In progress.

## What worked
- ✅ The `api.allanime.day` GraphQL API accepts POST with full query strings (no APQ hash management needed). All 4 operations (popular, search, details, episodes) return clean JSON.
- ✅ The inherited `client` (with CloudflareInterceptor) works for the API + catalog — no custom client needed.
- ✅ The build system copied cleanly from animepahe (just renamed package/module/extClass/keystore).
- ✅ The fork-compat episode.url encoding works (valid path + fragment metadata).
- ✅ The settings UI matches the AnimePahe pattern exactly (2 categories, "Currently: %s", "external sources" wording).

## What didn't work / deferred
- ⚠️ **Video playback (Step 4)** — DEFERRED by user request. `getHosterList` returns empty. The 4 servers (Fm-Hls, Uni, Mp4, Ok) + the Cloudflare managed challenge on the watch page will be handled in a follow-up session. Will need: WebViewFetcher (port from AnimePahe), server discovery, per-server extractors.
- ⚠️ **Episode metadata enrichment** — DEFERRED. The 3 toggles are in settings (default ON) but the `EpisodeMetadataFetcher` is not yet implemented. Will use the AniList ID extractable from the thumbnail URL (`bx<id>-...`).
- ⚠️ **Release APK** — DEFERRED. No keystore generated yet (mkissa-release.jks). Will generate in Step 5 when ready for release.
- ⚠️ **Icon** — temporary AI-generated icon. Replace with the user's real icon when provided.

## Key decisions
1. **Use POST with full GraphQL query strings** (not GET+APQ) — matches the proven allanime reference; no dependency on capturing/maintaining persisted-query hashes.
2. **One SEpisode per unique episode number** (not one per audio type) — scanlator shows all available audio types. Cleaner UX than emitting duplicate episode numbers for sub+dub.
3. **Title style preference** (romaji/english/native) — the API supports all three; user-selectable in settings. Default romaji.
4. **Weekly popular** (dateRange=7) as the default — matches the allanime reference; "popular" typically means "trending this week".

## What's next (Step 4 — video playback)
1. Generate `mkissa-release.jks` keystore (for the eventual release build).
2. Implement `WebViewFetcher` (port from AnimePahe's 241-line version, origin `data:text/html,...`).
3. Override `getHosterList(episode)`:
   - Decode the episode metadata from the fragment.
   - Fetch the watch page via WebViewFetcher (passes the CF managed challenge).
   - Parse the 4 server buttons (Fm-Hls, Uni, Mp4, Ok).
   - For each server: extract the video URL (Fm-Hls → FilemoonExtractor from `lib/`; Uni → TBD; Mp4 → direct; Ok → OkruExtractor from `lib/`).
   - Return `List<Hoster>` with pre-populated `videoList`.
4. Add server-toggle + preferred-server settings.
5. Implement `EpisodeMetadataFetcher` (using the AniList ID from the thumbnail URL).
6. Build release APK, run the full build checklist, register as ✅ stable.
