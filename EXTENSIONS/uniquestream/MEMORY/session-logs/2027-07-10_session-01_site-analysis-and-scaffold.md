# Session 01 — Site Analysis & Scaffold

> **Date:** 2027-07-10 · **Branch:** `feat/uniquestream` · **Status:** Analysis complete, code written, not yet compiled

## What was done

### 1. Environment setup
- Read project MEMORY files (README, PROJECT_RULES, EXTENSIONS.md, build checklist, guide 02)
- Deleted JDK (316MB) + Android SDK (604MB) + .android-env.sh per user instruction
- Created `feat/uniquestream` branch
- Installed scraping tools: yt-dlp 2026.07.04, crawl4ai 0.9.2, playwright 1.57.0 + Chromium (ffprobe was already present)

### 2. Site analysis (anime.uniquestream.net)

**Key findings:**
- Nuxt.js 3 SPA with FastAPI/Python backend
- **Pure API-driven** — NO HTML scraping needed
- Cloudflare Turnstile on frontend but NOT required for API endpoints
- No authentication required for any API endpoint
- Video player: Shaka Player (Google), HLS format
- Media CDN: `get2.mediacache.cc` with signed URLs (expire ~10 min)

**API endpoints discovered (all verified via curl):**
| Endpoint | Purpose |
|---|---|
| `GET /api/v1/videos/popular?limit=20&page=N` | Popular catalog |
| `GET /api/v1/videos/new?limit=20&page=N` | Latest updates |
| `GET /api/v1/search?query=...&t=all&limit=25` | Search |
| `GET /api/v1/series/{contentId}` | Series detail + seasons |
| `GET /api/v1/season/{seasonId}/episodes?page=N&limit=100&order_by=asc` | Episode list |
| `GET /api/v1/episode/{episodeId}/media/dash/{audioLocale}` | **Stream URLs** (★ key endpoint) |
| `GET /api/v1/genres` | Genre list |

**Stream API response structure:**
- Returns signed HLS master.m3u8 URLs
- `hls.locale` = actual audio language of the stream
- `hls.hard_subs[]` = separate hard-subtitle variants per language
- SUB = ja-JP audio with hardsubs, DUB = en-US audio (when available)

### 3. Extension scaffold
- Copied template → `EXTENSIONS/uniquestream/`
- Created full Gradle project structure (copied from AniKoto, adapted)
- extClass = FULL path `eu.kanade.tachiyomi.animeextension.en.uniquestream.UniQuestream`
- applicationIdSuffix = `en.uniquestream180` (so app sees `...uniquestream180`)
- All stubs in `:stubs` module, compileOnly
- Placeholder keystore generated

### 4. Full Kotlin implementation
- Pure JSON API extension (no Jsoup parsing)
- `kotlinx.serialization` for JSON deserialization
- Inline DTOs with `@SerialName` for snake_case mapping
- Catalog: popular + latest + search (with type filter)
- Details: fetches series detail API
- Episodes: fetches all seasons' episodes (flattened), shows SUB/DUB via scanlator
- Video: pre-filled Hoster.videoList pattern (no extra API call at hoster level)
- DUB is lazy-resolved via `resolveVideo`
- Sorting: by preferred audio, then quality

## Architecture decisions

1. **Pure API, no HTML** — the site is fully API-driven, so no HTML parsing at all
2. **Pre-filled videoList on Hoster** — the media API returns all streams at once, so we use `Hoster(videoList = [...])` to avoid an extra request
3. **Lazy DUB resolution** — DUB variant uses `resolveVideo` to avoid calling the DUB API when user only wants SUB
4. **Seasons flattening** — all seasons' episodes combined into one list (simpler UX)
5. **No playlistutils** — the signed master.m3u8 URLs work directly with mpv; no need to parse quality variants at extension level

## What's next

1. Build the extension (requires JDK 17 + Android SDK — not available in this env)
2. Test on device: popular, latest, search, details, episodes, video playback
3. Handle edge cases: series with no DUB, movies, clips
4. Consider using playlistutils for quality variant extraction (1080p/720p labels)
5. Add preferences for audio/quality selection
6. Create proper app icon

## Files created/modified

- `EXTENSIONS/uniquestream/EXTENSION.md` — identity doc
- `EXTENSIONS/uniquestream/MEMORY/sites/2027-07-10_uniquestream-site-analysis.md` — complete API analysis
- `EXTENSIONS/uniquestream/DEV/` — full Gradle project
- `EXTENSIONS/uniquestream/DEV/src/en/uniquestream/src/.../UniQuestream.kt` — main source (full implementation)
- `MEMORY/EXTENSIONS.md` — registered UniQuestream in extensions registry
