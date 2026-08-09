# UniQuestream Site Analysis

> **Date:** 2027-07-10 · **Status:** VERIFIED (browser + API) · **Site:** anime.uniquestream.net
>
> Complete API-driven anime streaming site. Nuxt.js 3 SPA frontend with FastAPI/Python backend.
> **NO HTML scraping needed** — all data comes from REST API endpoints.

---

## 1. Site Identity

- **Domain:** `anime.uniquestream.net`
- **Name:** AnimeStream (by UniQuestream)
- **Tech stack:** Nuxt.js 3 (Vue 3) frontend + FastAPI/Python backend
- **Video player:** Shaka Player (Google) with HLS.js fallback
- **CDN:** Cloudflare (including Turnstile CAPTCHA on watch pages)
- **Media CDN:** `get2.mediacache.cc` (openresty/nginx, signed URLs with expiry)
- **Images:** `media.uniquestream.net` (posters, episode thumbnails)
- **Comments:** `comments.uniquestream.net` (separate service, Waline-based)
- **Auth:** Optional (JWT via `/api/v1/auth/refresh`), NOT required for video streaming
- **Ads:** Heavy ad infrastructure (adsterra, youradexchange, aclib). Ad-block wall on watch pages.
- **Anti-bot:** Cloudflare Turnstile (`site_key: 0x4AAAAAAB_Be1ca66EGQu0F`) on some pages
- **NSFW:** No

## 2. URL Structure

| Page | URL Pattern | Notes |
|---|---|---|
| Home | `/` | SSR'd with trending content |
| Popular | `/videos/popular` | SPA route (may redirect to ads on direct click) |
| Latest | `/videos/new` | SPA route |
| Trending | `/videos/trending` | SPA route |
| Top Rated | `/videos/highest-rated` | SPA route |
| Films | `/videos/movies` | SPA route |
| Schedule | `/schedule` | SPA route |
| Series Detail | `/series/{content_id}/{slug}` | SSR'd, content_id is the key |
| Watch Episode | `/watch/{episode_content_id}/{slug}` | SSR'd, episode_content_id is the key |
| Search | Frontend SPA, no URL change | Uses search API with debounce |

## 3. API Endpoints (VERIFIED)

### Base URL: `https://anime.uniquestream.net/api/v1`

### 3.1 Catalog Endpoints

| Endpoint | Method | Params | Returns | Used For |
|---|---|---|---|---|
| `/videos/popular` | GET | `?limit=N&page=N&slider=1` | Array of series | `popularAnimeParse` |
| `/videos/new` | GET | `?limit=N&page=N&slider=1` | Array of series | `latestUpdatesParse` |
| `/videos/trending` | GET | `?days=7&limit=N` | `{data: [...]}` (wrapped!) | Could use for latest |
| `/videos/highest-rated` | GET | `?limit=N` | Array of series | Alternative catalog |
| `/videos/movies` | GET | `?limit=N&sort=popular` | Array of movies | Not needed (type=show) |

**Note:** `slider=1` is used by the homepage carousel and returns full detail. Without it,
the browse endpoint returns items with null fields. For catalog, `slider` is not needed —
the `/videos/popular` and `/videos/new` endpoints return full detail regardless.

### 3.2 Search

| Endpoint | Method | Params | Returns |
|---|---|---|---|
| `/search` | GET | `?query={text}&t=all&limit=N&suggest=1` | `{series: [...], movies: [...], episodes: [...]}` |

**VERIFIED:** `t` param controls type filter: `all`, `show`, `movie`. Default returns all types.
Search works without authentication.

### 3.3 Series Detail

| Endpoint | Method | Params | Returns |
|---|---|---|---|
| `/series/{content_id}` | GET | — | Full series object with seasons, genres, audio info |

**Response fields:**
```json
{
  "content_id": "olTmHHez",
  "title": "One Piece",
  "description": "...",
  "images": [{"url": "...", "type": "poster_tall"}, {"url": "...", "type": "poster_wide"}],
  "seasons": [{"content_id": "dHTiu8rG", "title": "East Blue (1-61)", "season_number": 1, "season_seq_number": 1, "episode_count": 61, "mal_id": null}],
  "episode": {"content_id": "T5HGWmie", "title": "...", ...},
  "audio_locales": ["en-US", "ja-JP"],
  "subtitle_locales": ["en-US", "es-419", ...],
  "genre": [{"title": "Action", "name": "action"}, ...],
  "rating_avg": 5.0,
  "rating_count": 19
}
```

### 3.4 Episode List

| Endpoint | Method | Params | Returns |
|---|---|---|---|
| `/season/{season_content_id}/episodes` | GET | `?page=N&limit=N&order_by=asc` | Array of episode objects |

**Episode object:**
```json
{
  "content_id": "T5HGWmie",
  "title": "I'm Luffy! ...",
  "episode": "1",
  "episode_number": 1.0,
  "duration_ms": 1500147,
  "image": "https://media.uniquestream.net/...",
  "audio_locales": ["ja-JP"]
}
```

### 3.5 Video Stream URL (★ CRITICAL)

| Endpoint | Method | Params | Returns |
|---|---|---|---|
| `/episode/{episode_content_id}/media/dash/{audio_locale}` | GET | — | HLS stream URLs |

**Response:**
```json
{
  "title": "Episode Title",
  "content_id": "T5HGWmie",
  "media_id": "12b938bae920ab919225d2978fa9a610",
  "hls": {
    "locale": "ja-JP",
    "playlist": "https://get2.mediacache.cc/.../{media_id}_{audio}/master.m3u8?sign=...&expires=...",
    "hard_subs": [
      {"locale": "en-US", "playlist": "https://get2.mediacache.cc/.../{media_id}_{audio}/hard/en-US/master.m3u8?sign=...&expires=..."},
      {"locale": "es-419", "playlist": "..."},
      {"locale": "pt-BR", "playlist": "..."}
    ],
    "original": true
  },
  "has_local": true
}
```

**Key points:**
- Signed URLs expire in ~10 minutes. The extension MUST call this API at play-time (not cache).
- `hls.locale` = the actual audio locale of the returned stream.
- `hls.hard_subs` = hardcoded subtitle versions (video has subs burned in).
- When requesting `en-US` audio and it's available, `hls.locale` = `en-US` (DUB).
- When requesting `en-US` audio and it's NOT available, `hls.locale` = `ja-JP` (falls back to raw Japanese).
- The master.m3u8 contains separate video + audio playlists (standard HLS).
- **No authentication required** for this endpoint.

### 3.6 Other Endpoints

| Endpoint | Notes |
|---|---|
| `/genres` | Returns all genres with `name` and `title` |
| `/config` | Site config (ads, turnstile, premium) |
| `/stats/public` | Public stats |
| `/editorial/picks?limit=N` | Editorial picks |
| `/videos/recommendations/series/{id}?limit=N` | Recommendations |
| `/videos/episodes/recent?limit=N` | Recent episodes |
| `/browse?categories={genre,popular}&limit=N&slider=1` | Genre-based browse |
| `/schedule` | Airing schedule |

## 4. Audio/Video Architecture

### Audio Types
- **SUB** = Japanese audio (`ja-JP`) + hardsubs (e.g., `en-US` burned in)
- **DUB** = English audio (`en-US`) when available
- Multiple subtitle locales available: `en-US`, `es-419`, `es-ES`, `pt-BR`, `ar-SA`, `de-DE`, `fr-FR`, `it-IT`, `ru-RU`

### Video Format
- HLS (.m3u8) master playlist with separate video + audio tracks
- Video resolutions: available in master.m3u8 (1080p, 720p, etc.)
- Video CDN: `get2.mediacache.cc` (openresty, signed URLs)

### Hoster Model
- **Single CDN hoster** (`get2.mediacache.cc`) — no multi-hoster selection needed
- Sub vs Dub is handled by requesting different audio locales from the media API
- Hard-subs are separate playlists with subs burned into the video

## 5. Anti-Bot / WAF

- **Cloudflare Turnstile** on watch page (`site_key: 0x4AAAAAAB_Be1ca66EGQu0F`)
  - This is on the **frontend** (Nuxt page). The **API endpoints work without** Turnstile tokens.
  - The API returns signed stream URLs without any Turnstile verification.
  - **Conclusion:** Turnstile is for the web frontend only, not for API access. Extension should be fine.
- **Ad-block detection** wall on watch page (blocks video play in browser with adblocker).
  - Again, this is a frontend JS check, not an API restriction.

## 6. Pagination

- `/videos/popular` and `/videos/new`: `?page=N` (1-indexed). No total pages returned.
- `/season/{id}/episodes`: `?page=N&limit=N&order_by=asc|desc`
- Search: `?limit=N` only (no pagination in current API)

## 7. Series Types

- `type: "show"` = TV series (has seasons and episodes)
- `type: "movie"` = Movies (single episode, no seasons)
- **Extension should filter for `type: show` only** (or handle movies too if desired)

## 8. Image URLs

- Poster (tall): `https://media.uniquestream.net/anime/images/posters/480x720/{hash}.webp`
- Poster (wide): `https://media.uniquestream.net/anime/images/posters/1200x675/{hash}.webp`
- Episode thumb: `https://media.uniquestream.net/anime/images/episodes/320x180/{hash}.webp`
- Episode banner: `https://media.uniquestream.net/anime/images/episodes/1200x675/{hash}.webp`

## 9. Challenges for Extension

1. **Seasons:** The site has a seasons model. One series can have 17+ seasons (e.g., One Piece).
   - The extension needs to flatten all seasons' episodes into a single list,
     OR use Aniyomi's season support (`FetchType.Seasons`).
   - Since seasons can be numerous (17+), flattening is simpler and more user-friendly.

2. **Sub/Dub labeling:** Each episode has `audio_locales` showing which audio tracks exist.
   - If `en-US` is in `audio_locales`, the episode has DUB.
   - SUB is always available (ja-JP audio with hardsubs).
   - Use `SEpisode.scanlator` to show availability (per project rule §8).

3. **Single hoster with sub/dub variants:** The "hoster" is really the CDN, but sub/dub
   are different streams for the same episode. This maps to the Aniyomi model where
   each sub/dub variant is a separate `Video` with appropriate `videoTitle` labeling.

4. **No authentication needed:** All API endpoints work without login. This simplifies
   the extension significantly.

5. **Signed URLs expire:** The HLS URLs expire in ~10 minutes. The extension must
   fetch fresh URLs at play time (in `videoListParse` or `resolveVideo`).

---

Last updated: 2027-07-10
