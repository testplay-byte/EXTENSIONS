# AnimePahe — Site Analysis

> Status: VERIFIED via reference extension (2027-06-28) · Last updated: 2027-06-28
> Analyst: session 52 (Z.ai Code)
>
> ⚠️ **Note on verification:** animepahe.pw is behind Cloudflare's managed challenge, which blocked
> the analysis browser (agent-browser / headless Chromium). The site structure below was verified by
> reading the reference animepahe extension at `SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/animepahe/`
> (reading reference code for understanding is allowed; we write our own implementation). The API
> endpoints + response shapes will be re-verified on-device when the debug APK is tested (the
> on-device WebView can pass Cloudflare).

---

## 1. Base URL + domain

- **Primary domain:** `https://animepahe.pw` (user-specified)
- **Alternative domains (known):** `animepahe.com`, `animepahe.org` (the reference extension offers these as a user preference)
- **Domain rotation:** animepahe has a history of rotating domains (.com, .org, .ru, .app, .pw). The current live one per the user is `.pw`.
- **Anti-bot:** Cloudflare (managed challenge / Turnstile) + DDoS-Guard. Blocks raw HTTP clients (curl gets 403). The on-device extension handles this via a Cloudflare/DDoS-Guard interceptor + WebView bypass (same approach as AniKoto).

## 2. URL structure

| Purpose | URL | Method | Response |
|---|---|---|---|
| **Popular (airing)** | `/api?m=airing&page=N` | GET | JSON |
| **Search** | `/api?m=search&q=<query>` | GET | JSON (single page, no pagination) |
| **Browse by genre** | `/anime/genre/<slug>` | GET | HTML |
| **Browse by theme** | `/anime/theme/<slug>` | GET | HTML |
| **Browse by demographic** | `/anime/demographic/<slug>` | GET | HTML |
| **Browse by season** | `/anime/season/<season>-<year>` (e.g. `/anime/season/spring-2024`) | GET | HTML |
| **Anime detail (by id)** | `/a/<animeId>` | GET | HTML (redirects to `/anime/<session>`) |
| **Anime detail (by session)** | `/anime/<session>` | GET | HTML |
| **Episode list** | `/api?m=release&id=<session>&sort=episode_asc&page=N` | GET | JSON (paginated) |
| **Episode play page** | `/play/<animeSession>/<episodeSession>` | GET | HTML (video extraction — Step 4) |

**Important quirk:** animepahe does NOT provide permanent URLs to anime. The `/a/<id>` URL redirects to a session-based `/anime/<session>` URL. The session changes, so the extension must:
1. Store the animeId (`/a/<id>`) in `SAnime.url`.
2. On details/episodes request, fetch `/a/<id>` (follows redirect) and extract the session from the final URL.
3. Use the session for the episode-list API call.

## 3. Search mechanism

- **Search is a JSON API:** `GET /api?m=search&q=<query>` — returns ALL matches on a single page (no pagination).
- **Filters + search do NOT combine:** if a query is present, search is used; if no query, the selected filter (genre/theme/demographic/season) browse page is used instead.
- **No autosuggest** — the single search API is the only search path.
- **Browse pages are HTML** (not API): `/anime/genre/<slug>` etc. return an HTML index of `<a>` links.

## 4. API response shapes (JSON)

### Popular (`/api?m=airing&page=N`)
```json
{
  "current_page": 1,
  "last_page": 5,
  "data": [
    { "anime_title": "...", "snapshot": "https://...", "anime_id": 12345, "fansub": "..." }
  ]
}
```

### Search (`/api?m=search&q=<query>`)
```json
{
  "current_page": 1,
  "last_page": 1,
  "data": [
    { "title": "...", "poster": "https://...", "id": 12345 }
  ]
}
```

### Episodes (`/api?m=release&id=<session>&sort=episode_asc&page=N`)
```json
{
  "current_page": 1,
  "last_page": 3,
  "data": [
    { "created_at": "2024-01-15 12:00:00", "session": "abc123def", "episode": 1.0 }
  ]
}
```

**Pagination:** episodes are paginated (e.g. 30/page). The extension must **recursively fetch all pages** to build the complete episode list. Compare `current_page < last_page` to decide.

## 5. Filter values (extracted from the reference Filters.kt)

### Genres (`/anime/genre/<slug>`)
Action, Adventure, Avant Garde, Award Winning, Boys Love, Comedy, Drama, Ecchi, Erotica, Fantasy, Girls Love, Gourmet, Hentai, Horror, Mystery, Romance, Sci-Fi, Slice of Life, Sports, Supernatural, Suspense (22 values; slugs are kebab-case, e.g. `slice-of-life`).

### Themes (`/anime/theme/<slug>`)
Adult Cast, Anthropomorphic, CGDCT, Childcare, Combat Sports, Crossdressing, Delinquents, Detective, Educational, Gag Humor, Gore, Harem, High Stakes Game, Historical, Idols (Female), Idols (Male), Isekai, Iyashikei, Love Polygon, Love Status Quo, Magical Sex Shift, Mahou Shoujo, Martial Arts, Mecha, Medical, Military, Music, Mythology, Organized Crime, Otaku Culture, Parody, Performing Arts, Pets, Psychological, Racing, Reincarnation, Reverse Harem, Romantic Subtext, Samurai, School, Showbiz, Space, Strategy Game, Super Power, Survival, Team Sports, Time Travel, Urban Fantasy, Vampire, Video Game, Villainess, Visual Arts, Workplace (52 values; kebab-case slugs).

### Demographic (`/anime/demographic/<slug>`)
Shounen, Shoujo, Seinen, Josei, Kids (5 values).

### Season (`/anime/season/<season>-<year>`)
- **Season:** Spring, Summer, Fall, Winter (slugs: `spring`, `summer`, `fall`, `winter`)
- **Year:** `<select>` + current year down to 1968

**Filter behavior:** only ONE filter is applied at a time (the reference checks them in order: genre → demographic → theme → season). If multiple are set, the first non-default one wins. If none are set and no query, fall back to popular.

## 6. Anime detail page (HTML structure)

The detail page at `/anime/<session>` (reached via `/a/<id>` redirect) has:

| Field | Selector |
|---|---|
| **Title** | `div.title-wrapper > h1 > span` |
| **Cover/poster** | `div.anime-poster a` (the `href` is the full-size image) |
| **Studios** | `div.col-sm-4.anime-info p:contains(Studios:)` → strip `"Studios: "` prefix |
| **Status** | `div.col-sm-4.anime-info p:contains(Status:) a` → "Currently Airing" = ONGOING, "Finished Airing" = COMPLETED |
| **Genres** | `div.anime-genre ul li` (multiple) |
| **Demographic** | `div.col-sm-4.anime-info p:contains(Demographic:) a` |
| **Theme** | `div.col-sm-4.anime-info p:contains(Theme:) a` |
| **Synopsis** | `div.anime-summary` |
| **Synonyms** | `div.col-sm-4.anime-info p:contains(Synonyms:)` |
| **Japanese** | `div.col-sm-4.anime-info p:contains(Japanese:)` |
| **Aired** | `div.col-sm-4.anime-info p:contains(Aired:)` |
| **Season** | `div.col-sm-4.anime-info p:contains(Season:)` |
| **External Links** | `div.col-sm-4.anime-info p:contains(External Links:) a` |
| **Related anime** | `div.anime-content div.anime-relation .mx-n1` (each has `h5 > a` with href + title, `img` with `data-src` for thumbnail) |
| **Recommendations** | `div.anime-content div.anime-recommendation .mx-n1` |

**Status mapping:** "Currently Airing" → `SAnime.ONGOING`; "Finished Airing" → `SAnime.COMPLETED`; else → `SAnime.UNKNOWN`.

## 7. Episode list mechanism

- **AJAX JSON API:** `GET /api?m=release&id=<session>&sort=episode_asc&page=N`
- **Session retrieval:** the `session` comes from fetching `/a/<animeId>` and reading the last path segment of the redirected URL (`/anime/<session>`).
- **Pagination:** recursive — fetch page 1, if `current_page < last_page`, fetch page 2, etc. until all pages collected.
- **Episode fields:** `created_at` (date), `session` (the episode session, used in `/play/<animeSession>/<episodeSession>`), `episode` (float — can be 1.0, 1.5, etc.).
- **Episode URL:** `/play/<animeSession>/<episodeSession>` — used for video extraction (Step 4, deferred).
- **Ordering:** the API returns ascending; the reference reverses to descending then the app re-sorts. We'll follow the fork-compat EpisodeMeta encoding from our guide (`/watch/<slug>/ep-N#<meta>`).

## 8. Audio types

- animepahe has **sub and dub** (audio `jpn` = sub, `eng` = dub). NOT the SUB/HSUB/DUB triad AniKoto has.
- Audio type is a property of the **video** (resolved in Step 4 via the Kwik extractor), not the episode. Episodes don't have per-audio variants in the list — the audio choice happens at video selection.
- So for Step 3 (episodes), we don't need the `scanlator` field for sub/dub (unlike AniKoto). We'll just emit one `SEpisode` per episode number.

## 9. CDN / WAF

- **Cloudflare** (managed challenge / Turnstile) + **DDoS-Guard** — blocks raw HTTP.
- The on-device extension needs a `CloudflareInterceptor` (or `DdosGuardInterceptor`) that:
  1. Detects 403/challenge responses (checks `Server: ddos-guard` or `cf-ray` header).
  2. Falls back to WebView to solve the challenge + capture the clearance cookie.
  3. Retries the original request with the new cookie.
- This is the SAME pattern AniKoto uses (see `EXTENSIONS/HOW_TO_BUILD_EXTENSION/reference-anikoto-solutions.md` §waf-blocked-cdn).
- **For Steps 2-3 (catalog/details/episodes):** the inherited `client` (with our Cloudflare interceptor) handles it. The API + HTML requests will pass once the interceptor solves the challenge.
- **Full CDN/server analysis is Step 4** (deferred per the user's request — video extraction only).

## 10. Identity fields confirmed

| Field | Value | Verified? |
|---|---|---|
| Display name | `AnimePahe` | ✅ (reference + site self-name) |
| Language | `en` | ✅ |
| Is NSFW | `false` | ✅ (animepahe is a general anime site; has an Erotica/Hentai genre filter but the site itself isn't 18+ gated) |
| Domain | `https://animepahe.pw` | ✅ (user-specified) |
| Package | `eu.kanade.tachiyomi.animeextension.en.animepahe` | ✅ |
| extClass | `.AnimePahe` (leading dot OK — applicationId will == source package) | ✅ |
| versionId | `1` (start, STABLE) | ✅ |
| supportsLatest | `false` (animepahe has NO latest page) | ✅ |

## 11. Open questions for the user

1. **Domain preference:** default `animepahe.pw`, with `.com`/`.org` as alternatives in settings (like the reference)? Or just `.pw`?
2. **Promo line:** should we append a credit line to descriptions (AniKoto appends "Thank the Confused_creature_180")? If yes, what text?
3. **Episode metadata enrichment:** defer (like AniKoto did initially) or implement now (thumbnails/titles from MAL/AniList)? Recommend: defer to after release.

## 12. Reference

- Reference extension (read-only, for understanding): `SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/animepahe/`
  - `AnimePahe.kt` — main source class (URLs, parsing, API calls)
  - `Filters.kt` — all filter values (genres, themes, demographics, seasons, years)
  - `dto/AnimePaheDto.kt` — JSON response shapes
  - `DdosGuardInterceptor.kt` — Cloudflare/DDoS-Guard bypass
  - `extractor/KwikExtractor.kt` — Kwik video extractor (Step 4)
  - `extractor/CloudflareBypass.kt` — WebView-based CF bypass (Step 4)
- API documentation (community): https://gist.github.com/Ellivers/f7716b6b6895802058c367963f3a2c51 (referenced in the source)
