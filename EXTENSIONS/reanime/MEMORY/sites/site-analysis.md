# reanime.to — Site Analysis (Step 1)

> Browser-verified analysis of reanime.to for the Re:ANIME 180 Aniyomi extension.
> All findings below were captured live with agent-browser (real Chromium, Cloudflare
> bypassed via realistic UA + Turnstile auto-solve on a fresh browser context).
> Date: 2026-07-14. Timezone: America/Los_Angeles.
>
> **Status: AWAITING USER VERIFICATION** — this is the Step-1 overview the user will
> check before Step 8 (video-stream capture) begins.

---

## 1. Site identity

| Field | Value | Verified? |
|---|---|---|
| **Site** | `reanime.to` | ✅ live (HTTP 200) |
| **Frontend** | SvelteKit (SSR) — `/assets/immutable/`, `entry_start`, `nodes_*` | ✅ |
| **Backend API** | `https://reanime.to/api/v1/` (REST) | ✅ |
| **Video host** | `flixcloud.cc` (SvelteKit + Cloudflare) — FlixHQ-style | ✅ |
| **Thumbnail CDN** | `fetch.flixcloud.cc/thumbnail/<uuid>` | ✅ |
| **Cover image CDN** | `s4.anilist.co/file/anilistcdn/...` (AniList CDN) | ✅ |
| **Protection** | Cloudflare Turnstile on `reanime.to` AND `flixcloud.cc` | ✅ |
| **Ad network** | `luugy.com` (pop/redirect ads — dock "Search" button hijacks to aliexpress) | ⚠️ |

### Cloudflare behaviour (important for the extension)
- `reanime.to` shows the "Just a moment…" Turnstile interstitial on first load.
- A realistic desktop Chrome User-Agent lets the Turnstile **auto-solve** within ~10–13s
  on a **fresh browser context**. Reusing a context after the `cf_clearance` cookie
  expires (~a few minutes) re-triggers the challenge and it does NOT auto-solve on reload.
- **All `/api/v1/` and `/api/flix/` endpoints are Cloudflare-protected.** A fetch with an
  expired `cf_clearance` returns `403` + the challenge HTML.
- `flixcloud.cc` is independently Cloudflare-protected and also challenges direct visits.
- → The extension will need **WebView-based Cloudflare bypass** (same pattern as MKissa:
  native `MotionEvent.dispatchTouchEvent()` on the Turnstile, then reuse `cf_clearance`
  with OkHttp). This is a known, solved pattern in this project.

---

## 2. URL routing

| Route | Purpose |
|---|---|
| `/home` | Homepage (hero carousel + Latest Episodes + New on Site + Upcoming) |
| `/anime/<slug>-<6char-id>` | Anime **details** page (metadata, related, "Watch Now" button) |
| `/watch/<slug>-<6char-id>?ep=<N>&lang=<sub\|dub>` | **Player** page (episode list + video iframe) |
| `/search` (modal) | Command-palette search (`⌘S`) — ANIME / USERS tabs |
| `/schedule` | Schedule page (nav button — not yet deeply analyzed) |

- The **6-char id** is alphanumeric, lowercase (e.g. `wve5ef`, `ah9gxe`, `bjfend`).
- The `<slug>` is the title slugified (e.g. `mobile-suit-gundam-the-witch-from-mercury`).
- `anime_id` in the API = `<slug>-<6char-id>` (e.g. `naruto-bjfend`).

---

## 3. Homepage sections (all SSR'd)

The homepage renders 4 sections server-side. Only 4 client-side API calls are made on
load (all global, not section-specific):

| Section | Content | Source |
|---|---|---|
| **Hero carousel** | Featured anime (One Piece, Slime S4, Mushoku S3, Bleach, Black Clover, …) | SSR |
| **Latest Episodes** | Recent episode cards; **All / Sub / Dub tabs** | SSR |
| **New on Site** | Newly-added anime | SSR |
| **Upcoming** | Upcoming anime | SSR |

### Client-side API calls on homepage
| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /api/v1/user` | 401 (auth) | Current user |
| `GET /api/v1/community/unread-count` | 200 | Community unread count |
| `GET /api/v1/top/anime?period=today&limit=10` | 200 | Today's top anime |
| `GET /api/v1/schedule?tz=UTC&year=2026&month=7` | 200 | Schedule |

### Latest Episodes card fields (DOM)
Each card shows: title, score (e.g. 8.2), content rating (PG-13), episode counts
formatted `14 / 12 / ?` (= subbed / dubbed / total-unknown), format (TV/ONA), duration (24m),
aired date, and a Watchlist manager.

---

## 4. Catalog / Search API (★ the public catalog endpoint)

### `GET /api/v1/search` — PUBLIC (no auth required) ✅

This single endpoint powers both **text search** and the **popular list**.

**Query params (verified working):**
| Param | Works? | Example | Notes |
|---|---|---|---|
| `q` | ✅ | `q=naruto` | Empty/absent `q` → returns popular/trending list |
| `limit` | ✅ | `limit=20` | Page size |
| `offset` | ✅ | `offset=0` | Pagination |
| `year` | ✅ | `year=2024` | Filters by season_year |
| `season` | ✅ | `season=FALL` | FALL/WINTER/SPRING/SUMMER (combine with `year`) |
| `format` | ✅ | `format=MOVIE` | TV / MOVIE / OVA / ONA / SPECIAL |
| `genres` / `with_genres` / `tags` / `genres[]` | ❌ IGNORED | — | Returns default popular list |
| `sort` (popularity/score/latest/recent/new) | ❌ IGNORED | — | Returns default popular list |
| `order` | ❌ IGNORED | — | |
| `status` | (likely ✅, not yet confirmed) | — | |

> **Key constraint:** genre filtering and sorting are NOT supported by the public search
> endpoint. Browse/catalog/anime/latest/recent endpoints all return **401 (auth-required)**.
> So without authentication, the extension can offer: text search + year/season/format
> filters + popular list (empty `q`). Genre filters + sort would need user login.

### Search result shape
```jsonc
{
  "limit": 20, "offset": 0, "processing_ms": 1, "query": "naruto",
  "results": [
    {
      "anime_id": "naruto-bjfend",            // ← slug-id used in /anime/ and /watch/ URLs
      "title": { "english": "Naruto", "native": "NARUTO -ナルト-", "romaji": "NARUTO" },
      "cover_image": {
        "color": "#e47850",                    // dominant color
        "extra_large": "https://s4.anilist.co/.../large/bx20-...jpg",
        "large":   ".../medium/...",
        "medium":  ".../small/..."
      },
      "format": "TV",                          // TV | MOVIE | OVA | ONA | SPECIAL
      "status": "Finished",                    // Finished | Releasing | Not Yet Aired
      "genres": ["Action","Adventure","Comedy","Drama","Fantasy","Supernatural"],
      "season": "FALL",                        // FALL | WINTER | SPRING | SUMMER
      "season_year": 2002,
      "episodes": 220,                         // total episode count
      "duration": "23m",
      "subbed": 220,                           // ← subbed episode count
      "dubbed": 220,                           // ← dubbed episode count
      "average_score": 80,                     // 0-100
      "popularity": 680571,
      "rating": "PG-13 - Teens 13 or older",
      "can_watch": false,                      // whether streamable on reanime
      "can_request": false                     // whether requestable
    }
  ]
}
```

### Popular list
`GET /api/v1/search?limit=N` (no `q`) returns the popular/trending list:
Attack on Titan, Demon Slayer, Death Note, … (score-ordered).

---

## 5. Details page (`/anime/<slug>-<6id>`)

SSR'd (no public details-specific API call observed client-side).

### Metadata fields (verified from DOM)
| Field | Example |
|---|---|
| Title (English) | Mobile Suit Gundam: The Witch from Mercury |
| Title (Romaji) | Kidou Senshi Gundam: Suisei no Majo |
| Alternative Titles | G-Witch, + multi-lang (Indonesian, Traditional Chinese, Russian, Thai) |
| Type | ANIME (also: MOVIE, OVA, ONA, SPECIAL) |
| Episodes | 12 |
| Duration | 24 min |
| Status | Finished |
| Start Date | 2022-10-02 |
| Season | Fall 2022 |
| Synopsis | Full description (Read More expand) |
| Genres | Action, Drama, Mecha, Sci-Fi |
| Studios | Sunrise, Bandai Namco Filmworks, Sotsu, Mainichi Broadcasting System |
| Stats: Subbed | 12 |
| Stats: Dubbed | 12 |
| Related Seasons & Series | (large list, 120 entries for Gundam) |

Buttons: **Watch Now** → `/watch/<slug>?ep=1&lang=sub`, Add to List, Share.

> The details page exposes **anilist_id + mal_id** indirectly (via the player page's
> `/api/v1/downloads/check?anilist_id=X&mal_id=Y&episode=N` call). These IDs are needed
> for the video-sources endpoint (see §7).

---

## 6. Episodes API

### `GET /api/v1/anime/<anime_id>/episodes?limit=2000` — ✅ works (CF-protected)

Returns the full episode list for an anime.

```jsonc
{
  "data": [
    {
      "episodeId": "ep-1",                  // ← "ep-<N>" format
      "episode_number": 1,
      "title": "Episode 1",
      "title_japanese": "",
      "title_romanji": "Episode 1",
      "description": "",
      "duration": 24,
      "aired": "2022-10-02T00:00:00Z",
      "is_filler": false,
      "is_recap": false,
      "site": "MyAnimeList",               // metadata source
      "thumbnail": "",                      // (empty here; fetched separately)
      "url": "",                            // (empty)
      "updated_at": "2026-07-14T..."
    }
  ]
}
```

- Episode thumbnails come from a **separate** endpoint: `GET /api/thumbnails/<anilist_id>`
  (returns thumbnail data; actual images hosted on `fetch.flixcloud.cc/thumbnail/<uuid>`).
- Episode metadata is sourced from **MyAnimeList** (the `site` field).

### Other player-page endpoints (verified)
| Endpoint | Purpose |
|---|---|
| `GET /api/v1/anime/<anime_id>/rating` | User rating |
| `GET /api/v1/anime/comments/<anime_id>/<ep>?page=1&sort=new` | Episode comments |
| `GET /api/v1/downloads/check?anilist_id=X&mal_id=Y&episode=N` | Download availability (★ reveals anilist_id + mal_id) |
| `GET /api/v1/anime/<anime_id>/recommendations` | Recommendations |
| `GET /watch/<anime_id>/__data.json` | SvelteKit SSR data blob (contains anilist_id, mal_id, etc.) |

---

## 7. Video sources API (★ the server list)

### `GET /api/flix/<anilist_id>/<episode_number>` — ✅ works (CF-protected)

Returns the list of available servers + audio types for an episode.

```jsonc
{
  "success": true,
  "servers": [
    {
      "$id": "hd1-<code>-sub",               // <serverName>-<code>-<dataType>
      "serverName": "HD-1",
      "dataLink": "https://flixcloud.cc/e/<code>?v=1",   // ← the embed URL
      "dataType": "sub",                      // "sub" | "dub"
      "continue": false,
      "softsub": false                        // ← softsub flag (false = hardsub default)
    },
    {
      "$id": "hd1-<code>-dub",
      "serverName": "HD-1",
      "dataLink": "https://flixcloud.cc/e/<code>?v=1",
      "dataType": "dub",
      "continue": false,
      "softsub": false
    },
    {
      "$id": "hd2-<code>-sub",
      "serverName": "HD-2",
      "dataLink": "https://flixcloud.cc/e/<code>?v=2",
      "dataType": "sub",
      "continue": false,
      "softsub": false
    },
    {
      "$id": "hd2-<code>-dub",
      "serverName": "HD-2",
      "dataLink": "https://flixcloud.cc/e/<code>?v=2",
      "dataType": "dub",
      "continue": false,
      "softsub": false
    }
  ]
}
```

### Servers observed (Mobile Suit Gundam: Witch from Mercury, ep 1)
| Server | dataType | dataLink | softsub |
|---|---|---|---|
| **HD-1** | sub | `flixcloud.cc/e/<code>?v=1` | false |
| **HD-1** | dub | `flixcloud.cc/e/<code>?v=1` | false |
| **HD-2** | sub | `flixcloud.cc/e/<code>?v=2` | false |
| **HD-2** | dub | `flixcloud.cc/e/<code>?v=2` | false |

> **Server variety across anime:** only HD-1 + HD-2 were observed for this title.
> Other anime may expose additional servers (HD-3, or different hosts) — **needs
> verification in Step 8** (cf_clearance was too short-lived to sample more titles
> in this session). The `/api/flix/` response is the authoritative server list per
> episode, so the extension should read servers dynamically from it.

---

## 8. Audio versions (sub / dub / hsub)

| Audio type | Present? | Evidence |
|---|---|---|
| **sub** | ✅ | `dataType:"sub"` in `/api/flix/`; `lang=sub` URL param; Stats "Subbed 12"; homepage "Sub" tab |
| **dub** | ✅ | `dataType:"dub"` in `/api/flix/`; `lang=dub` URL param; Stats "Dubbed 12"; homepage "Dub" tab |
| **hsub** (hardsub) | ❌ (not a separate type) | No `hsub` dataType, no Hsub tab, no Hsub in Stats. "sub" IS the hardsub default (`softsub:false`). |
| **softsub** | ⚠️ player toggle, not a dataType | `skI`/`skO` (softsub Inner/Outer) params on the flixcloud embed URL; `softsub` flag in `/api/flix/` |

**Conclusion: 2 audio types — sub and dub.** "sub" is hardsubbed by default (`softsub:false`);
softsub is a player-level toggle (`skI`/`skO`), not a separate audio track. There is no
separate "hsub" type.

> Dub/sub selection: the player page URL uses `?lang=sub` or `?lang=dub`. The `/api/flix/`
> response returns both sub + dub server entries for the same episode, so the extension
> can expose both as separate `Video` objects (labelled SUB / DUB).

---

## 9. Player page + video delivery (★ Step-8 preview)

### Player page (`/watch/<slug>?ep=<N>&lang=<sub|dub>`)
- Episode list sidebar (1–N + specials), with episode search + toggle view.
- Server switch buttons: "Switch to HD-1 server" / "Switch to HD-2 server".
- Auto Play / Auto Next toggles, Theater Mode, Watch2Gether.
- Video plays in a **cross-origin iframe**:
  `https://flixcloud.cc/e/<code>?v=<N>&autoPlay=true&skI=false&skO=false&kuudere_ts=<ms-timestamp>`

### flixcloud.cc player internals
- Also a SvelteKit app, also Cloudflare-protected.
- Page title = the source filename, e.g.:
  - `[Erai-raws] Tensei Shitara Slime Datta Ken 4th Season - 14 [1080p CR WEB-DL AVC AAC][MultiSub][14DC8D35].mkv`
  - `[Anime Time] Mobile Suit Gundam The Witch From Mercury - 001 - The Witch And The Bride.mkv`
  - → Sources are **MKV releases** (Erai-raws, Anime Time) remuxed to HLS by flixcloud.
- **Source quality: 1080p** (from the `[1080p]` tag in the Erai-raws filename).
- The player's SSR data (`/e/<code>/__data.json`) exposes (field names are partly obfuscated):
  `video_id`, `video_title`, `audio_type`, `default_audio_track`, `aid`, `subtitles`,
  `available_fonts`, `extracted_fonts`, `intro_chapter`, `outro_chapter`, `chapters`,
  `skipIntro`, `skipOutro`, `thumbnails_vtt`, `is_iframe`, `isHost`, `isLive`, `premium`,
  `obfuscated_crypto_data`, `obfuscation_seed`, `w_payload`, `iframe_domain`,
  `player_settings`, `start_at`, `autoplay`.

### Stream API (★ the HLS source)
- The player fetches: `GET https://flixcloud.cc/api/m3u8/<24-hex-token>` (200)
  - The 24-hex token looks like a MongoDB ObjectId (e.g. `5592861f7dc41378fb6ef295`).
  - **The token is SINGLE-USE.** Re-fetching returns `410 {"error":"invalid_or_used_token"}`.
  - Two m3u8 requests were observed per play (likely master + variant, or sub + dub audio).
- The token is derived at runtime by the player JS from `obfuscated_crypto_data` +
  `obfuscation_seed` + `w_payload` (obfuscated). **This is the same architecture pattern
  as AniKoto's RC4 decryption** — the real stream URL is encrypted and decrypted in JS.

### Qualities
- **Source: 1080p** (verified from filename).
- Exact HLS variant qualities (1080p / 720p / 480p / 360p?) — **to be confirmed in Step 8**
  by capturing a fresh `m3u8` master playlist response (the player self-clears to
  `about:blank` when opened directly in headless, so capturing requires either a
  fetch-interceptor injection or on-device WebView network interception — same
  `interceptVideoUrl` approach used for MKissa v16.18).

### Step-8 capture strategy (preliminary)
1. Solve Cloudflare on `reanime.to` (WebView + native MotionEvent on Turnstile) → get `cf_clearance`.
2. `GET /api/v1/search?q=<title>` → `anime_id`.
3. `GET /watch/<anime_id>/__data.json` → `anilist_id`, `mal_id`.
4. `GET /api/v1/anime/<anime_id>/episodes?limit=2000` → episode list.
5. `GET /api/flix/<anilist_id>/<ep>` → server list (HD-1/HD-2 × sub/dub).
6. Load `flixcloud.cc/e/<code>?v=<N>` in WebView → **intercept** `https://flixcloud.cc/api/m3u8/<token>`
   via `shouldInterceptRequest` (the `interceptVideoUrl` pattern from MKissa v16.18) →
   captures the master HLS playlist (with all qualities) before the single-use token is
   consumed by the player JS.
7. Fallback: JS monkey-patch `fetch`/`XHR` in the flixcloud page to scan the m3u8 response
   for `.m3u8`/`.mp4` URLs.

---

## 10. Endpoint summary (all under `https://reanime.to` unless noted)

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /api/v1/search?q=&limit=&offset=&year=&season=&format=` | public | Search + popular + filters |
| `GET /api/v1/anime/<anime_id>/episodes?limit=2000` | CF | Episode list |
| `GET /api/flix/<anilist_id>/<ep>` | CF | **Server list** (HD-1/HD-2 × sub/dub) |
| `GET /api/v1/downloads/check?anilist_id=&mal_id=&episode=` | CF | Download check (★ reveals anilist_id + mal_id) |
| `GET /api/thumbnails/<anilist_id>` | CF | Episode thumbnails |
| `GET /api/v1/anime/<anime_id>/rating` | CF | Rating |
| `GET /api/v1/anime/comments/<anime_id>/<ep>?page=&sort=` | CF | Comments |
| `GET /api/v1/anime/<anime_id>/recommendations` | CF | Recommendations |
| `GET /api/v1/top/anime?period=today&limit=10` | CF | Top anime |
| `GET /api/v1/schedule?tz=&year=&month=` | CF | Schedule |
| `GET /api/v1/community/unread-count` | CF | Community |
| `GET /api/v1/user` | 401 | Current user (auth) |
| `GET /api/v1/anime?limit=` | 401 | Browse (auth) |
| `GET /api/v1/browse` / `/catalog` / `/latest` / `/recent` / `/popular` / `/trending` / `/new` / `/upcoming` | 401 | All auth-gated |
| `GET /watch/<anime_id>/__data.json` | CF | SvelteKit SSR data (anilist_id, mal_id) |
| `GET https://flixcloud.cc/e/<code>?v=<N>` | CF | Video embed page |
| `GET https://flixcloud.cc/api/m3u8/<24hex>` | CF | **HLS master playlist** (single-use token) |
| `GET https://fetch.flixcloud.cc/thumbnail/<uuid>` | public | Episode thumbnail images |

---

## 11. Open questions for Step 8 (video-stream analysis)

1. **Exact HLS qualities** — capture a fresh `m3u8` master to list 1080p/720p/480p/360p variants.
2. **Server variety across titles** — sample more anime (movies, long-runners like One Piece)
   to see if any expose HD-3 or alternative hosts beyond HD-1/HD-2.
3. **m3u8 token derivation** — confirm whether the token comes from the SSR `__data.json`
   or is generated by player JS from `obfuscated_crypto_data` (affects whether we can
   construct it without running the player JS).
4. **Softsub toggle** — does `skI=true`/`skO=true` change the stream (separate subtitle
   track) or just the player UI?
5. **Subtitle tracks** — the player exposes `subtitles` + `available_fonts`; confirm
   whether subtitle URLs are in the m3u8 (WebVTT) or separate.

These will be resolved in Step 8 (after the user verifies this overview).
