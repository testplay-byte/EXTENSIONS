# AniDB — Site Analysis

> Status: VERIFIED (2026-07-14) · Last updated: 2026-07-14
> Analyst: anidb session 01

## 1. Base URL + domain

| Field | Value |
|---|---|
| **Site name** | AniDB (tagline: "Watch Anime Online Free in HD") |
| **Base URL** | `https://anidb.app` |
| **Home page** | `/home` |
| **Tech stack** | Laravel (backend) + Alpine.js + Tailwind CSS (frontend), JW Player 8.26.1 (video) |
| **Site type** | Server-rendered HTML (NOT a SPA) + JSON APIs for episodes/languages |
| **NSFW** | Yes — has Erotica + Hentai genres; NSFW titles in default browse |

The URL provided by the user (`https://everythingmoe.com/s/anidbstream`) is a review/aggregator page on everythingmoe.com — NOT the streaming site. The actual site is `anidb.app`, linked from the everythingmoe review page. The review describes it as "Same content as animepahe, but it's their own host."

### Domain stability
anidb.app appears to be a stable domain (no known rotation history like animepahe). The site is relatively new (review page says it was "ranked number 17" before climbing to #6).

## 2. URL structure

| Purpose | URL | Method | Response |
|---|---|---|---|
| Home / Popular | `/home` | GET | HTML (hero swiper + Fan Favorites + Top 10) |
| Browse (catalog) | `/browse?type=&status=&season=&year=&genres=&sort=&page=N` | GET | HTML (grid of `a.anime-card`) |
| A-Z index | `/az?letter=A` (or `0-9`) | GET | HTML |
| By genre | `/genres/<id>` | GET | HTML |
| By theme | `/themes/<id>` | GET | HTML |
| Schedule | `/schedule?date=<date>&tz=<tz>` | GET | HTML |
| **Anime detail = Watch page** | `/anime/<slug>-<numeric-id>` | GET | HTML (player + episode list on SAME page) |
| Episodes API | `/api/frontend/anime/<animeId>/episodes` | GET | JSON |
| Languages API | `/api/frontend/episode/<epId>/languages` | GET | JSON |
| Embed player | `/embed/<token>` | GET | HTML (JW Player) |
| Search (full page) | `/browse?q=<query>` | GET | HTML (same as browse) |
| Search (autosuggest) | `/search/suggestions?q=<query>` | GET | HTML (dropdown items) |
| Poster CDN | `https://cdn.xlsbox.com/poster/small/<ts>/<id>.jpg` | GET | image |
| Video CDN | `https://hls.anidb.app/stream/<token>/master.m3u8` | GET | HLS playlist |

### Detail URL pattern
`/anime/<slug>-<numeric-id>` — the numeric ID is the trailing number (e.g. `/anime/one-piece-3880` → animeId = `3880`). The animeId is also passed to Alpine.js `watchPage(3880, {...})` in the page's `x-data` attribute.

## 3. Search mechanism

- **Full search**: `/browse?q=<query>` — returns the same HTML grid as browse. Composes with ALL filters + pagination.
- **Autosuggest**: `/search/suggestions?q=<query>` — returns HTML dropdown items (NOT JSON). Used for live search in the nav bar.
- **Chosen approach**: full search via `/browse?q=` (supports pagination + filters). The autosuggest endpoint is not used (no pagination, limited results).
- **Pagination**: `?page=N` (standard, 1-indexed). "Next →" link in HTML when more pages exist.

## 4. Server-list paths

AniDB has **ONE video server** — the site's own HLS host (`hls.anidb.app`). There is no server-selection UI on the site. The everythingmoe review confirms: "their own host."

### Full resolve chain
```
1. anime detail page (/anime/<slug>-<id>)
       │  extract animeId from URL
       ▼
2. GET /api/frontend/anime/<animeId>/episodes
       │  → { episodes: [{ id, number, number2, filler }] }
       ▼
3. For each episode: GET /api/frontend/episode/<epId>/languages
       │  → { languages: [{ code, name, embed_url }] }
       │  (jpn = SUB, eng = DUB; no HSUB)
       ▼
4. For each language: GET <embed_url> (/embed/<token>)
       │  parse JW Player: sources: [{ file: '<m3u8 URL>', type: 'hls' }]
       │  → https://hls.anidb.app/stream/<token>/master.m3u8
       ▼
5. GET master.m3u8 → parse #EXT-X-STREAM-INF variants
       │  → one Video per quality (e.g. 1080p, 720p)
       ▼
   playable HLS (verified: HTTP 200, NO Referer required)
```

### Token crypto
**None.** The embed token is a plain string in the URL (no RC4 like AniKoto, no AES-GCM like MKissa). No JsUnpacker needed (the embed page is not packed JS — it's a clean inline `<script>` with the JW Player setup).

## 5. Audio types + labels

| Code | Name | Maps to | Notes |
|---|---|---|---|
| `jpn` | Japanese | **SUB** (subbed) | Always available |
| `eng` | English | **DUB** (dubbed) | Per-episode — not all anime have it |
| — | — | HSUB | ❌ Not present on this site |

### How audio is determined
The `/api/frontend/episode/<epId>/languages` API returns one entry per audio language. Each entry has a DIFFERENT `embed_url` → different m3u8 stream. Sub/dub is NOT determined by the m3u8 — it's determined by which language entry you pick.

### Verified examples
- One Piece ep1 → Japanese only (SUB only)
- Demon Slayer ep1 → English + Japanese (DUB + SUB)
- Black Clover ep1 → Japanese only (SUB only)

### Dedup
No dedup needed — each language has its own embed URL → its own m3u8. No token sharing across audio types (unlike AniKoto).

## 6. CDN / WAF

### Main site (anidb.app)
- **Cloudflare** protected (`server: cloudflare`, `cf-ray` headers).
- **Cloudflare Turnstile** challenge appears for **headless browsers** (agent-browser was blocked with "Just a moment...").
- **BUT: curl/OkHttp with a full desktop Chrome User-Agent gets HTTP 200 directly** — no challenge.
- The extension's inherited `client` (CloudflareInterceptor + desktop UA) will work. No WebView-based CF solving needed.

### Video CDN (hls.anidb.app)
- **NO Cloudflare** on the video CDN.
- **NO Referer required** — HTTP 200 with and without Referer header (verified via curl).
- Directly playable by mpv/ExoPlayer.

### Poster CDN (cdn.xlsbox.com)
- Standard CDN, no special headers.

## 7. PNG wrapping

**No.** Streams are standard `.m3u8` / `.ts` segments. No LocalProxyServer needed.

## 8. Identity fields confirmed

| Field | Value | Verified |
|---|---|---|
| Display name | `AniDB` | `<title>AniDB — Watch Anime Online Free in HD</title>` |
| Language | `en` | `<html lang="en">` |
| Is NSFW | `true` | Erotica + Hentai genres; NSFW titles in default browse |
| Domain | `anidb.app` | Confirmed live |
| Package | `eu.kanade.tachiyomi.animeextension.en.anidb180` | Convention (180 publisher suffix) |
| extClass | `eu.kanade.tachiyomi.animeextension.en.anidb.AniDB` | Full path (applicationId ≠ source package) |
| versionId | `1` | Start at 1, STABLE once published |

## 9. Catalog details

### Browse filters (all compose together on `/browse`)
| Filter | Param | Values |
|---|---|---|
| Sort | `sort` | Trending (default), Top Rated, Latest Updated, Most Popular, Most Favorited, Top Airing, Title A-Z, Newest First |
| Type | `type` | All, Movie, Music, ONA, OVA, Special, TV |
| Status | `status` | All, Currently Airing, Finished Airing |
| Season | `season` | All, Spring, Summer, Fall, Winter |
| Year | `year` | All, 2026 → 1977 |
| Genres | `genres` | 21 genres (comma-separated for multi-select) |
| Themes | `themes` | 24+ themes (comma-separated for multi-select) |

### Browse card structure
```html
<a class="anime-card block group" href="https://anidb.app/anime/<slug>-<id>" title="<title>">
  <img src="https://cdn.xlsbox.com/poster/small/<ts>/<id>.jpg" alt="<title>" loading="lazy">
  <span class="badge badge-orange">TV</span>      <!-- type -->
  <span class="badge badge-gray">8.7</span>        <!-- score -->
</a>
```

### Popular vs Latest
- **Popular** = `/browse?sort=order_trending` (Trending — the default)
- **Latest** = `/browse?sort=order_updated` (Latest Updated)

## 10. Episodes API

```
GET /api/frontend/anime/<animeId>/episodes
→ {
    "episodes": [
      { "id": 3512, "number": 1, "number2": null, "filler": false },
      { "id": 3513, "number": 2, "number2": null, "filler": false },
      ...
    ]
  }
```

- `id` — episode ID (used for the languages API)
- `number` — episode number (int)
- `number2` — secondary number (null for normal episodes; possibly for .5/recap)
- `filler` — boolean (filler flag)
- **No episode titles** in the API — just numbers.
- **No pagination** — the full episode list is returned in one response (One Piece = 1169 episodes in one response, 64KB).

## 11. Languages API

```
GET /api/frontend/episode/<epId>/languages
→ {
    "languages": [
      { "code": "eng", "name": "English", "embed_url": "https://anidb.app/embed/<token>" },
      { "code": "jpn", "name": "Japanese", "embed_url": "https://anidb.app/embed/<token>" }
    ]
  }
```

## 12. Embed page + player

The embed page (`/embed/<token>`) is a minimal HTML page with:
- JW Player 8.26.1 (from `ssl.p.jwpcdn.com`)
- A single HLS source in a `setup.sources` JS blob:
  ```js
  sources: [{ file: 'https://hls.anidb.app/stream/<token>/master.m3u8', type: 'hls' }]
  ```
- Quality-label builder supporting 144p → 2160p (4K), but actual qualities depend on the master.m3u8 variants.

### Master.m3u8 (verified — One Piece ep1)
```
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1261457,RESOLUTION=1920x1080 → 1080p
https://hls.anidb.app/stream/<token>/index-f1-v1-a1.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=662170,RESOLUTION=1280x720 → 720p
https://hls.anidb.app/stream/<token>/index-f2-v1-a1.m3u8
```
Codecs: H.264 (avc1) + AAC (mp4a). Quality count varies per episode/anime.

## 13. Comparison to our other extensions

| Aspect | AniKoto | AnimePahe | MKissa | **AniDB** |
|---|---|---|---|---|
| Video servers | 4 + Kiwi | 1 (Kwik) | 6 (3 working) | **1 (own host)** |
| Token crypto | RC4 | packed JS | AES-GCM + XOR | **None** |
| PNG wrapping | Yes | No | No | **No** |
| Cloudflare | Interceptor | Interceptor | Turnstile (WebView) | **Interceptor (no Turnstile for OkHttp)** |
| Audio types | SUB/HSUB/DUB | SUB/DUB | SUB/DUB | **SUB/DUB (no HSUB)** |
| Site type | HTML scrape | HTML + JSON | GraphQL SPA | **HTML + JSON (hybrid)** |

**AniDB is the simplest extension to build** — single server, no crypto, no PNG wrapping, clean JSON APIs, standard HLS, no WebView needed.

## 14. Open questions

None — all identity fields and technical details are confirmed. The site is straightforward to scrape.
