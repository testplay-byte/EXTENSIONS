# Session 01 — AnimePahe: Steps 1-3 (popular, search, filters, details, episodes)

> Date: 2027-06-28 · Session #: 01 (animepahe) · Timezone: America/Los_Angeles
> Type: IMPLEMENTATION (Steps 1-3 of the build guide)
> Follows: setup (animepahe scaffold + HOW_TO_BUILD_EXTENSION guide created)

## Goal

Build the AnimePahe extension for https://animepahe.pw — implement popular, latest, filters, search,
anime details, and episodes (Steps 1-3 of the build guide). Video extraction (Step 4) deferred per
the user's request.

## What was done

### Step 1 — Website analysis

- **Attempted browser analysis:** loaded https://animepahe.pw/ in agent-browser. The site is behind
  **Cloudflare's managed challenge** ("Just a moment... / Performing security verification"). Tried:
  waiting (5s, 8s, 20s, 25s), reloading, clicking the Turnstile "Verify you are human" checkbox,
  setting realistic desktop Chrome headers. **None passed** — Cloudflare detected the headless
  Chromium (Ray ID changed each attempt = each try rejected). curl also returns 403.
- **Reported the blocker to the user** per the build guide (00-philosophy-and-rules.md §when-to-ask).
- **Workaround:** read the reference animepahe extension at
  `SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/animepahe/` (reading reference code for
  understanding is allowed; we write our own implementation). This revealed the complete site
  structure: JSON API endpoints, DTO shapes, filter values, HTML selectors, the non-permanent-URL
  quirk, and the Cloudflare+DDoS-Guard anti-bot layer.
- **Wrote site analysis:** `MEMORY/sites/site-analysis.md` — documents all URLs, API response
  shapes, filter values (22 genres, 52 themes, 5 demographics, 4 seasons), HTML selectors, identity
  fields, and the Cloudflare limitation. Marked verification status honestly.

### Step 2 — Catalog (popular, search, filters, details)

- **Scaffolded the Gradle project** by copying AniKoto's `DEV/` build system (settings.gradle.kts,
  build.gradle.kts, gradle/, common/, stubs/, gradlew) and adapting:
  - `settings.gradle.kts`: rootProject.name = "Animepahe-Anime", module = `:src:en:animepahe`
  - Module `build.gradle.kts`: extName="AnimePahe", extClass=".AnimePahe", versionCode=1,
    versionId=1, applicationIdSuffix="en.animepahe", archivesName="aniyomi-en.animepahe-v16.1"
  - `proguard-rules.pro`: keep `...en.animepahe.**` classes + `$$serializer` classes
  - Renamed the kotlin package `anikoto` → `animepahe` (with `dto/` subpackage)
- **Wrote the DTOs** (`dto/AnimePaheDto.kt`): `ResponseDto<T>` (generic envelope with current_page,
  last_page, data), `AiringAnimeDto`, `SearchResultDto`, `EpisodeDto`. Used `@SerialName` for
  snake_case JSON fields + `@EncodeDefault` for the optional `data` list.
- **Wrote the filters** (`Filters.kt`): GenresFilter (22), ThemeFilter (52), DemographicFilter (5),
  YearFilter (1968→current year), SeasonFilter (4). All slugs verified from the reference. Only ONE
  filter applied at a time (genre → demographic → theme → season order).
- **Wrote the main source class** (`AnimePahe.kt`):
  - `popularAnimeRequest`/`Parse`: GET `/api?m=airing&page=N` → parse `ResponseDto<AiringAnimeDto>`
  - `searchAnimeRequest`/`Parse`: if query → GET `/api?m=search&q=...` (JSON); else → filter browse
    page (HTML at `/anime/genre/<slug>` etc.). Search is single-page; browse pages return `<a>` links.
  - `latestUpdatesRequest`/`Parse`: throw UnsupportedOperationException (supportsLatest = false)
  - `animeDetailsRequest`: override — if URL is `/a/<id>`, fetch it (follows redirect to
    `/anime/<session>`); this handles animepahe's non-permanent-URL quirk.
  - `animeDetailsParse`: parse HTML — title, studios (author), status, cover, genres, synopsis +
    synonyms/Japanese/aired/season.
  - `getFilterList`: GenresFilter, DemographicFilter, ThemeFilter, YearFilter, SeasonFilter (with
    separators).

### Step 3 — Episodes

- `episodeListRequest`: resolve the anime session via `fetchSession(animeId)` (synchronous — fetches
  `/a/<id>`, follows redirect, reads the session from the final URL's last path segment). Then build
  the API request: `GET /api?m=release&id=<session>&sort=episode_asc&page=1`.
- `episodeListParse`: parse page 1, then **recursively fetch all subsequent pages** (synchronous
  `client.newCall().execute()`) until `currentPage >= lastPage`. Each episode: episode_number from
  the API (real number, not overwritten), name = "Episode <N>", date_upload from `created_at`.
- **Fork-compat EpisodeMeta encoding:** `episode.url = "/watch/<animeSession>/ep-<N>#<episodeSession>"`
  — valid URL path (resolves to baseUrl, no DNS error in legacy forks) + episodeSession in the
  fragment (for Step 4 video extraction). Per the build guide's fork-compat recommendation.
- `seasonListParse`: return emptyList() (animepahe has no season concept).
- `hosterListParse`/`videoListParse`: return emptyList() (Step 4 deferred — stubs satisfy abstract
  method requirements).

### Build verification

- First build failed: missing `hosterListParse` override (abstract in ext-lib 16) + missing
  `AnimeFilter`/`Hoster` imports. Fixed both.
- Second build: SUCCESSFUL, one warning (`@EncodeDefault` needs opt-in). Fixed with `@OptIn(ExperimentalSerializationApi::class)`.
- Final build: SUCCESSFUL, warning-free. APK = `aniyomi-en.animepahe-v16.1-debug.apk` (196 KB).
- **Build checklist verified:**
  - ✅ package: `eu.kanade.tachiyomi.animeextension.en.animepahe`
  - ✅ extClass: `.AnimePahe` (leading dot OK — applicationId == source package)
  - ✅ versionCode: 1, versionName: 16.1, versionId: 1 (STABLE)
  - ✅ nsfw: 0
  - ✅ "Stub!" count: 0 (stubs compileOnly, NOT in APK)
  - ✅ AnimePahe class + 4 DTO `$$serializer` classes in DEX (classes2.dex + classes3.dex)
  - ✅ uses-feature: tachiyomi.animeextension

## What worked

- Copying AniKoto's build system and adapting it — fast, proven, no build-config surprises.
- Using the reference extension for site-structure understanding (since Cloudflare blocked my browser).
- Fork-compat EpisodeMeta encoding from the build guide — applied cleanly.
- The multi-dex APK correctly contains all extension classes + serializers.

## What didn't work / issues

- **Cloudflare blocked the analysis browser** — the biggest issue. agent-browser (headless Chromium)
  cannot pass animepahe.pw's Cloudflare managed challenge. Reported to the user. Worked around by
  reading the reference extension. **The extension itself will work on-device** (WebView solves
  Cloudflare), but API responses could NOT be pre-verified off-device.
- **API responses not live-verified** — because Cloudflare blocked curl + browser, I could not test
  the actual JSON responses. The DTOs + parsing are based on the reference extension's verified
  shapes. There's a small risk the API has changed since the reference was last updated. The user
  should test the debug APK on-device to confirm.

## What's next

- **User tests the debug APK** on a device/emulator with Aniyomi/Animiru. Confirm:
  - Popular tab loads (JSON API)
  - Search works (try "one piece", "naruto")
  - Filters work (try a genre, a theme, a season)
  - Anime details page loads (title, cover, synopsis, genres, status)
  - Episode list loads (all episodes, ascending, with dates)
- **If something breaks:** capture `adb logcat -s AnimePahe:*` and report. Likely culprits:
  - Cloudflare interceptor not solving the challenge (need a custom DdosGuardInterceptor like the reference)
  - API response shape changed (DTO field names)
  - HTML selectors changed (details page)
- **Step 4 (playback)** when the user is ready — implement Kwik extractor + Cloudflare bypass.

## Open questions for the user

1. The debug APK is at `EXTENSIONS/animepahe/APK/aniyomi-en.animepahe-v16.1-debug.apk` (196 KB).
   Ready to test on-device?
2. Cloudflare may require a custom DdosGuardInterceptor (like the reference has). If the inherited
   CloudflareInterceptor doesn't pass, I'll port one. OK?
3. Promo line in descriptions (AniKoto appends "Thank the Confused_creature_180")? Skip for now?
