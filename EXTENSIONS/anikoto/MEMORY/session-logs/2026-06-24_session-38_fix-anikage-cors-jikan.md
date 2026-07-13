# Session 38 — Fix Anikage CORS + Add Jikan for Titles + Multi-Source Merge

> Date: 2026-06-24 · Session #: 38 · Duration: ~short · Timezone: America/Los_Angeles

## Root cause of v16.22 failure

The user's log showed:
- ✅ AniList ID found: `→ 177506 (streamingEpisodes=12)` — @SerialName fix worked!
- ❌ **Anikage failed**: `JS error for request 2: Failed to fetch` — **CORS error**
- ✅ Kitsu returned 12 episodes (but no titles/descriptions for this anime)
- ✅ AniList streamingEpisodes provided thumbnails (12 episodes)

The Anikage fetch failed because the WebView's `fetch()` API enforces CORS. The WebView is
loaded on `megaplay.buzz`, and Anikage.cc does NOT send `Access-Control-Allow-Origin` headers
(verified in session 36: CORS preflight returned HTTP 403 with no CORS headers). So the
browser blocks the cross-origin fetch with "Failed to fetch".

AniList works via WebView because it sends `Access-Control-Allow-Origin: *`. Kitsu works via
WebView for GET requests (simple requests don't require CORS preflight, and Kitsu's response
is apparently accessible despite no explicit CORS headers).

## Fixes (v16.23)

### Fix 1: Use OkHttp for Anikage.cc (not WebView — CORS blocks WebView fetch)

Removed `anikage.cc` from the `isCloudflareHost()` check. Anikage now uses OkHttp (the
inherited `client` with CloudflareInterceptor). If OkHttp gets 403 from Cloudflare:
- The CloudflareInterceptor opens a WebView loading the Anikage URL as the **MAIN PAGE**
  (via `webview.loadUrl(url)` — NOT via `fetch()`)
- The main page load is NOT subject to CORS (CORS only applies to `fetch()`/XHR)
- The interceptor obtains a cf_clearance cookie, then OkHttp retries with the cookie
- If there's no JS challenge (just a WAF rule), the interceptor would fail — but the inherited
  client with desktop Chrome UA might pass the WAF directly (like Python/OpenSSL does from sandbox)

### Fix 2: Added Jikan (MyAnimeList API) for episode titles

Per the user's multi-source priority request:
```
Title: Jikan → Anikage → Kitsu
```

Added `fetchJikanEpisodes(malId)`:
- `GET https://api.jikan.moe/v4/anime/{malId}/episodes` → JSON array
- Returns: episode number (mal_id), title (English), aired date
- Jikan is NOT behind Cloudflare — OkHttp works directly, no WebView needed
- Provides the best episode titles (English, from MAL's official data)

Updated merge priority:
- **Thumbnail**: Anikage → AniList streamingEpisodes → Kitsu → banner → anime cover
- **Title**: Jikan → Anikage → Kitsu
- **Synopsis**: Anikage → Kitsu
- **Air date**: Jikan → Anikage → Kitsu

### Fix 3: Updated logging

The log line now includes Jikan count:
```
EpisodeMetadataFetcher: fetched 12 episodes for malId=58822 in 3997ms (jikan=12, anikage=0, kitsu=12, banner=true)
```

This makes it easy to see which sources contributed data.

## Verification

- BUILD SUCCESSFUL, all 11 checklist items pass
- DEX verified: `https://api.jikan.moe/v4/anime/` URL present, `jikan=` log format present
- versionId=11 STABLE
- MD5: `fce610097490cac991139fff48933475`

## What should happen on device with v16.23

For "Possibly the Greatest Alchemist" (MAL 58822):
1. Jikan: `GET api.jikan.moe/v4/anime/58822/episodes` via OkHttp → 12 episodes with English titles
2. AniList: POST graphql.anilist.co via WebView → AniList ID + streamingEpisodes (thumbnails) + banner
3. Anikage: `GET anikage.cc/api/media/anime/{anilistId}/episodes` via OkHttp → thumbnails + descriptions
4. Kitsu: `GET kitsu.app/api/edge/anime/{kitsuId}/episodes` via WebView → fallback data
5. Merge: Jikan titles + Anikage thumbnails/descriptions + AniList thumbnails + Kitsu fallback
6. Episodes enriched with: thumbnails (TheTVDB/Crunchyroll), descriptions (TheTVDB), "EP N - title" (MAL titles)

Even if Anikage fails (OkHttp 403 + CloudflareInterceptor can't bypass), Jikan provides the
titles — so episodes will at least have "EP 1 - English Title" names.
