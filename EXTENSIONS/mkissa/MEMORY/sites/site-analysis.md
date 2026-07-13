# MKissa — Site Analysis

> Status: VERIFIED (2026-06-29) · Last updated: 2026-06-29 · Analyst: session 01

## 1. Base URL + domain

- **Live domain:** `https://mkissa.to` (confirmed via agent-browser — loads, serves real content)
- **No domain rotation observed** (single domain; no .com/.org/.ru alternates like animepahe)
- **CDN:** Cloudflare in front (`server: cloudflare`, `cf-ray` header) but the catalog + API are NOT behind a managed challenge (plain curl with a desktop UA gets HTTP 200)
- **Static assets:** served from `cdn.allanime.day/all/mk/_app/immutable/` (SvelteKit build output)

## 2. URL structure

| Page | URL | Method | Notes |
|---|---|---|---|
| Home | `https://mkissa.to/anime` | GET | Landing page: Popular section (top 20) + Latest Updates section |
| Popular (full) | `https://mkissa.to/popular?type=anime&range=1` | GET | `range=1` (Daily), `range=7` (Weekly), `range=30` (Monthly) |
| Latest | (no dedicated page — home page's "Latest Updates" section) | — | API-driven |
| Search | (no dedicated page — search via the API) | — | API-driven |
| Anime detail | `https://mkissa.to/anime/<_id>` | GET | `_id` = allanime ID (e.g. `Gcou36nB8su3KWXrr`) |
| Episode list | ON the detail page (SUB + DUB sections) | — | No separate episodes page; rendered from `availableEpisodesDetail` |
| Watch / embed | `https://mkissa.to/anime/<_id>/p-<N>-<sub\|dub>` | GET | ⚠️ **Cloudflare managed challenge** ("Just a moment...") — handled in Step 4 via WebView |

## 3. The real API: `api.allanime.day` (GraphQL)

MKissa is a **SvelteKit frontend** on the **`api.allanime.day` GraphQL API** (the same API `allanime` uses). The API accepts both GET+APQ (persisted queries) and POST+full-query. **We use POST with full query strings** (matches the proven allanime reference extension — no dependency on capturing/maintaining persisted-query hashes).

### Endpoints (all POST to `https://api.allanime.day/api`)

| Operation | GraphQL query | Variables |
|---|---|---|
| **Popular** | `queryPopular(type, size, dateRange, page)` | `{type:"anime", size:40, dateRange:7, page:N}` |
| **Latest** | `shows(search, limit, page, translationType, countryOrigin)` | `{search:{allowAdult:false,allowUnknown:false,sortBy:"Recent"}, limit:40, page:N, translationType:"sub"\|"dub", countryOrigin:"ALL"}` |
| **Search** | `shows(...)` (same as Latest) | `{search:{query:"<q>", allowAdult:false, allowUnknown:false, <filters>}, limit:40, page:N, translationType, countryOrigin}` |
| **Details** | `show(_id)` | `{_id:"<id>"}` → returns full metadata (name, description, genres, status, studios, score, availableEpisodes, etc.) |
| **Episodes** | `show(_id)` → `availableEpisodesDetail` | `{_id:"<id>"}` → returns `{sub:["12","11",...,"0"], dub:["10","8",...,"1"]}` (episode strings, DESCENDING) |

### API behavior notes
- **NOT behind a Cloudflare managed challenge** — plain curl with a desktop UA gets HTTP 200 from `api.allanime.day`.
- **No auth tokens / CSRF** required for the above queries.
- The `Referer: https://mkissa.to/` header is sent by the site's frontend (we replicate it in `headersBuilder()`).

## 4. Search mechanism

- **No autosuggest endpoint** — search is a single `shows` query with `search.query`.
- **Search + filters compose** — all filter fields go into the same `search` object (`sortBy`, `season`, `year`, `genres`, `types`).
- **Pagination:** `page` param (1-indexed), `limit` per page (we use 40). `shows.pageInfo.total` gives the total count.
- **Empty query:** returns all anime (sorted by "Recent" if no `sortBy` filter).

## 5. Server-list paths (for Step 4 — video playback, DEFERRED)

The user identified **4 servers** on the watch page (`/anime/<id>/p-<N>-<sub|dub>`):

| Server name | Notes |
|---|---|
| **Fm-Hls** | (likely Filemoon HLS) |
| **Uni** | (unknown — possibly a custom player) |
| **Mp4** | (direct MP4) |
| **Ok** | (likely OK.ru) |

⚠️ **The watch page sits behind a Cloudflare managed challenge** ("Just a moment..." — confirmed via agent-browser). The video extraction (Step 4) will need:
1. A `WebViewFetcher` (port from AnimePahe's 241-line version, origin `data:text/html,...`) to pass the challenge
2. Server discovery (parse the 4 server buttons)
3. Per-server extractors (Fm-Hls → FilemoonExtractor from `lib/`; Uni → TBD; Mp4 → direct; Ok → OkruExtractor from `lib/`)

This is documented for the follow-up video-playback session. **Not implemented in this session.**

## 6. Audio types + labels

- **SUB** (subbed) — `translationType: "sub"` in the API
- **DUB** (dubbed) — `translationType: "dub"` in the API
- **No HSUB (hardsub)** observed — the site has only Sub and Dub.
- The API returns separate episode lists for sub and dub (`availableEpisodesDetail.sub` / `.dub`). An episode may exist in sub but not dub (or vice versa).
- **Our implementation:** one `SEpisode` per unique episode number, with `scanlator` showing all available audio types ("Sub", "Dub", or "Sub • Dub") per rule §8. The episode URL uses the user's preferred audio type (or the first available if the preference isn't available for that episode).

## 7. CDN/WAF behavior

| Endpoint | CF managed challenge? | Plain curl works? |
|---|---|---|
| `mkissa.to/anime` (home) | No | ✅ Yes (HTTP 200) |
| `mkissa.to/anime/<id>` (detail) | No | ✅ Yes (HTTP 200) |
| `api.allanime.day/api` (GraphQL) | No | ✅ Yes (HTTP 200) |
| `mkissa.to/anime/<id>/p-<N>-sub` (watch) | ⚠️ **YES** ("Just a moment...") | ❌ No (returns the CF challenge page) |

**Implication:** The catalog + details + episodes layers (this session) work with plain OkHttp (the inherited `client` with CloudflareInterceptor handles any light CF). The video extraction layer (Step 4) will need a `WebViewFetcher` to pass the watch-page challenge.

## 8. PNG wrapping

**Not observed.** Video URLs (when extracted in Step 4) are expected to be standard m3u8/mp4 — no PNG disguise. If Step 4 reveals PNG wrapping, a `LocalProxyServer` will be needed (see AniKoto's pattern). Documented for the follow-up.

## 9. Identity fields confirmed

| Field | Value | Verified how |
|---|---|---|
| **Display name** | `MKissa 180` | Site `<title>` = "MKissa - Browse Anime - Discover Anime Series" + the "180" suffix convention |
| **Language** | `en` | Site UI is in English |
| **Is NSFW** | `false` | No 18+ warnings; the API sends `allowAdult: false` by default |
| **Domain** | `https://mkissa.to` | agent-browser confirmed live |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.mkissa180` | applicationId = namespace + applicationIdSuffix |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.mkissa.MKissa` | FULL path (no leading dot) — applicationId ≠ source package |
| **versionId** | `1` | STABLE — first release. Never bump after publish. |

## 10. Episode URL encoding (fork-compat)

```
/anime/<_id>/p-<N>-<translationType>#<episodeString>
```

- **Path** (`/anime/<id>/p-<N>-sub`): valid URL that resolves to `baseUrl` → no DNS error in legacy-pipeline forks.
- **Fragment** (`#<episodeString>`): carries the raw episode string (e.g. "0", "1", "12.5") for exact recovery in `getHosterList`.
- `<N>`: the episode number for display (integer if whole, else the float string).
- `<translationType>`: "sub" or "dub" — which audio track this episode entry points to.

Example: episode 5 sub of `Gcou36nB8su3KWXrr` → `/anime/Gcou36nB8su3KWXrr/p-5-sub#5`

## 11. Metadata enrichment potential

- **No MAL external links** on the detail page (checked via agent-browser — no `myanimelist.net/anime/` links).
- **AniList ID extractable** from the thumbnail URL: `https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx182300-...` → the `182300` is the AniList media ID.
- **Fallback:** Jikan title-search (`api.jikan.moe/v4/anime?q=<title>&limit=1`) if the AniList ID approach fails.
- The 3 metadata toggles (thumbnails/titles/descriptions) are in settings (default ON) but the actual `EpisodeMetadataFetcher` is deferred to a follow-up session (the user's current task is catalog + details + episodes only).

## 12. Open questions / future work

- **Step 4 (video playback):** implement `getHosterList` with WebViewFetcher for the CF challenge + 4 server extractors (Fm-Hls, Uni, Mp4, Ok). Documented above.
- **Episode metadata enrichment:** implement `EpisodeMetadataFetcher` using the AniList ID from the thumbnail URL (cleaner than Jikan title-search).
- **Per-extension keystore:** generate `mkissa-release.jks` when ready for a release build (Step 5).
- **Promo line:** optionally append "Thank the Confused_creature_180" to descriptions (like AniKoto) — ask the user before adding.
