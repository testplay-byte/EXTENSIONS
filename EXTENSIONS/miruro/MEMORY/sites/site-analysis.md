# Miruro — Site Analysis (CORRECTED)

> Status: **VERIFIED via reference extension source + live probe**
> Last updated: 2025-07-14 (America/Los_Angeles) — corrected after studying the yuzono `src/en/miruro` reference extension
> Analyst: miruro branch, session 01 (corrected) + session 02 (reference cross-check)
>
> ⚠️ **This document supersedes the earlier analysis.** The first analysis incorrectly assumed
> Miruro uses the public Consumet API and has only 3 servers (Default/Vidstream/Gogo) + 2 audio
> types (Sub/Dub). Both were wrong. The corrected facts (below) come from the **yuzono
> `src/en/miruro` reference extension** (`SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/miruro/`),
> which is a working, maintained extension for this exact site, cross-checked against a live probe.

---

## 0. TL;DR — what Miruro ACTUALLY is

**Miruro** (`miruro.tv`, with mirrors `.to`/`.bz`/`.ru`) is a **React+Vite SPA** that streams anime
through its **OWN custom backend** (the "pipe API" at `/api/secure/pipe`), NOT the public Consumet
API. The pipe API:

- Wraps AniList GraphQL metadata (anime info, search, filters) + multiple upstream anime providers
  (AnimePahe, AniKoto, Zoro, 9Anime, AniDao, Moon, etc.) behind a single obfuscated endpoint.
- Encodes the request as a base64url JSON payload in the `?e=` query param.
- Returns responses that are **XOR-obfuscated + gzip-compressed** (header `x-obfuscated: 2` →
  base64url decode → XOR with a hardcoded `PIPE_KEY` → gunzip → JSON).
- Streams video through **proxy servers** (`vault01.ultracloud.cc` / `vault02.ultracloud.cc`,
  selected by FNV-1a hash) that wrap the upstream m3u8/MP4 URL + Referer.

**There are 11 provider "servers"** (AnimePahe, AniKoto, AniDao, 9Anime, Moon, Zoro, Pewe, Nun, Bun,
Twin, Cog) — each with per-episode **sub-type** availability across **4 audio types: Sub, Dub, Soft
Sub (ssub), Hard Sub (h-sub)**. The live site returns a config telling the extension which providers
are visible and their capabilities (sub/dub/ssub/download).

- **Open source (frontend)**: `github.com/Miruro-no-kuon/Miruro-no-Kuon` — but the **backend is
  private** (the pipe API lives on miruro.tv itself).
- **Reference extension**: `yuzono/anime-extensions` → `src/en/miruro/` — a 2234-line working
  extension. **This is the authoritative reference** for the API structure, crypto, and server list.

---

## 1. Base URL + mirrors

| Domain | Role | CF protection | Notes |
|---|---|---|---|
| `miruro.tv` | **Primary** (yuzono default) | Cloudflare managed challenge + WAF rule on `/api/` | Live probe (2025-07-14): homepage → 403 challenge; pipe API → 403 `cf-mitigated: challenge` |
| `miruro.to` | Mirror | Cloudflare managed challenge | Same as .tv |
| `miruro.bz` | Mirror | Cloudflare managed challenge | Same |
| `miruro.ru` | Mirror | Cloudflare managed challenge | Same |
| `miruro.com` | Static "official domains" landing page | None (HTTP 200) | Lists the 4 mirrors + features |

**VERIFIED (live probe, 2025-07-14):**
- curl `https://www.miruro.tv/` with Chrome 148 UA → HTTP 403, `cf-mitigated: challenge`,
  `server: cloudflare`, body = "Just a moment..." managed challenge page.
- curl `https://www.miruro.tv/api/secure/pipe?e=...` with full browser fingerprint headers
  (sec-ch-ua, sec-fetch-*, Origin, Referer) → HTTP 403, `cf-mitigated: challenge`.
- ⚠️ **Evolution note:** the yuzono `MiruroBrowserFingerprintInterceptor` comment (written earlier)
  says miruro.tv used a "WAF custom-rule block" (NOT a managed challenge) and that curl with a bare
  Chrome UA returned 200 OK. **This is no longer true** — Cloudflare has since upgraded to a managed
  challenge. The extension therefore needs BOTH the browser-fingerprint interceptor (for the pipe API
  header shape) AND WebView-based `cf_clearance` solving (for the managed challenge).

**Chosen `baseUrl`**: `https://www.miruro.tv` (matches yuzono default). The extension exposes a
mirror preference (`.tv` / `.to` / `.bz` / `.ru`) in settings, fetched dynamically from the
`status.miruro.com/api` status page.

---

## 2. The pipe API — Miruro's custom backend

### Endpoint
```
GET https://www.miruro.tv/api/secure/pipe?e=<base64url-json-payload>
```

### The `e=` payload (VERIFIED from `Miruro.kt:buildPipeRequest`, line 2091)
```json
{
  "path": "search/browse" | "search" | "info/<anilistId>" | "episodes" | "sources",
  "method": "GET",
  "query": { ... },          // type, sort, genres, page, perPage, anilistId, episodeId, provider, category...
  "body": null,
  "version": "0.2.0",
  "timestamp": <epoch_ms>
}
```
The JSON is UTF-8 → base64url (no padding) → placed in `?e=`.

### Response decryption (VERIFIED from `MiruroExtractor.decryptResponse`, line 199)
1. Read response header `x-obfuscated`:
   - `"2"` → obfuscated (proceed to decrypt)
   - anything else (incl. missing) → plain JSON, return as-is
2. `Base64.decode(body, URL_SAFE)` → bytes
3. XOR each byte with `PIPE_KEY` (cycled): `data[i] ^= PIPE_KEY[i % PIPE_KEY.size]`
4. `GZIPInputStream(data)` → UTF-8 string → JSON

### Crypto keys (VERIFIED from `Miruro.kt` companion, line 721-722)
```
PIPE_KEY  = hex "71951034f8fbcf53d89db52ceb3dc22c"  (16 bytes — XOR key for pipe response decryption)
PROXY_KEY = hex "a54d389c18527d9fd3e7f0643e27edbe"  (16 bytes — XOR key for proxy URL obfuscation)
```
> These are hardcoded in the miruro.tv frontend JS (`env2.js`). They are NOT secret (they ship in the
> public frontend) — they're obfuscation, not security.

### Pipe paths (VERIFIED)

| Path | Purpose | Query params |
|---|---|---|
| `search/browse` | Trending, Recent, filtered browse, search-with-filters | `type`, `sort`, `genres[]`, `excludedGenres[]`, `tags[]`, `excludedTags[]`, `format[]`, `status`, `season`, `seasonYear`, `dubLanguage`, `isAdult`, `page`, `perPage` |
| `search` | Plain text search | `query`, `limit`, `offset` (bare JSON array response) |
| `info/<anilistId>` | Anime details (full AniList metadata) | — |
| `episodes` | Episode list (per-provider, per-sub-type) | `anilistId` |
| `sources` | Video stream list for one episode | `episodeId`, `provider`, `category` (sub/dub/ssub/h-sub) |

> AniList GraphQL (`graphql.anilist.co`) is used as a **synchronous fallback** when the pipe API
> fails — the extension's `popularAnime`/`latestUpdates`/`searchAnime`/`animeDetails` all try the
> pipe first, then fall back to AniLib (the `aniyomi.lib.anilib` helper).

---

## 3. Catalog — popular, latest, search, filters

### Popular (trending)
`search/browse` with `type=ANIME`, `status=RELEASING`, `sort=TRENDING_DESC`, `page`, `perPage`.

### Latest (recent)
`search/browse` with `type=ANIME`, `status=RELEASING`, `sort=UPDATED_AT_DESC`, `page`, `perPage`.

### Search
`search/browse` with the user's `query` + filter params (search and filters compose on the same
endpoint, same as AniList). Pagination via `page`/`perPage` (default 20).

### Filters (8 categories — VERIFIED from `MiruroFilters.kt`)

| Filter | Type | Options |
|---|---|---|
| **Sort** | Select | All, Trending, Popularity, Average Score, Favorites, Latest, Title A-Z, Title Z-A |
| **Genres** (18) | TriState (include/exclude) | Action, Adventure, Comedy, Drama, Ecchi, Fantasy, Horror, Mahou Shoujo, Mecha, Music, Mystery, Psychological, Romance, Sci-Fi, Slice of Life, Sports, Supernatural, Thriller |
| **Tags** (~300) | TriState (include/exclude) | Full AniList tag list (Achromatic, Aliens, Isekai, Zombies, ...) — see `MiruroFiltersData.TAGS` |
| **Format** (7) | Checkbox (multi) | TV, TV Short, Movie, Special, OVA, ONA, Music |
| **Year** | Select | All, 1940 → next year |
| **Season** (4) | Select | All, Winter, Spring, Summer, Fall |
| **Status** (6) | Select | All, Airing (RELEASING), Finished, Not Yet Aired, Hiatus, Cancelled |
| **Dub Language** (17) | Select | All, English, Japanese, Spanish, Portuguese, French, German, Italian, Korean, Chinese, Arabic, Hindi, Russian, Turkish, Thai, Polish, Tagalog, Ukrainian |

> The Dub Language filter is notable — it filters by the language of the dubbed audio track.

---

## 4. The 11 providers (servers) — ★ the corrected server list

**VERIFIED from `Miruro.kt` companion (line 729-757) + `ConfigResponseDto` (line 128).**

The live site returns a **config** (`streaming` map + `providerOrder`) listing all available
providers and their capabilities. The extension ships hardcoded fallback defaults (used before the
config fetch completes, or if it fails):

| # | Alias | Display name | Default capabilities (fallback) | Relationship |
|---|---|---|---|---|
| 1 | `kiwi` | **AnimePahe** | Sub, Download | native (DEFAULT preferred provider) |
| 2 | `bee` | **Anikoto** | Sub, Soft Sub | native |
| 3 | `bonk` | **AniDao** | Soft Sub, Download | native |
| 4 | `ally` | **9Anime** | Sub, Download | native |
| 5 | `moo` | **Moon** | Sub, Download | native |
| 6 | `hop` | **Zoro** | Soft Sub | native |
| 7 | `pewe` | **Pewe** | Hard Sub | native |
| 8 | `nun` | **Nun** | Hard Sub | embed |
| 9 | `bun` | **Bun** | Soft Sub | embed |
| 10 | `twin` | **Twin** | Soft Sub | embed |
| 11 | `cog` | **Cog** | Sub (Hard Sub per fallback config) | embed |

Plus 2 more known aliases in `KNOWN_DISPLAY_NAMES` (not in the default order, may appear in the live
config): `dune` → **Dune**, `kuz` → **Kuz**.

### Capabilities per provider (from `ConfigResponseDto.ProviderCapabilitiesDto`)
Each provider declares: `sub` (bool), `dub` (bool), `ssub` (Soft Sub, bool), `download` (bool),
`skipTimes` (bool), `thumbnails` (bool). The live config determines which providers offer which
audio types — this is why the user saw ~11 sub/hsub servers and ~8 dub servers (not all providers
offer dub).

### How providers are selected
1. The extension fetches the config (async, cached in prefs) to get the live `providerOrder` +
   per-provider capabilities.
2. `episodeListParse` returns episodes keyed by the preferred provider, with fallback provider
   episode IDs stored in `SEpisode.url` (as JSON) for later `videoListParse`.
3. `videoListParse` fetches `sources` for the primary (provider, subType), then optionally fetches
   additional sub-types + fallback providers in parallel (controlled by the "Include all sub/dub
   streams" and "Include all provider streams" settings).

---

## 5. Audio types — ★ 4 types, not 2

**VERIFIED from `Miruro.kt` (line 844) + `MiruroExtractor.parseStreamsFromResponse` (line 259).**

| Sub-type key | Display label | Meaning |
|---|---|---|
| `sub` | **Sub** | Subtitled (soft subtitles, default) |
| `dub` | **Dub** | Dubbed audio (English/other language audio) |
| `ssub` | **Soft Sub** | Soft sub (subtitles as a separate track, selectable) |
| `h-sub` | **Hard Sub** | Hard subs (burned into the video) |

```kotlin
val SCANLATOR_SUB_TYPES = setOf("sub", "dub", "ssub", "h-sub")
val SUB_TYPE_DISPLAY_ORDER = listOf("sub", "dub", "ssub", "h-sub")
```

The user's preference dropdown offers Sub / Dub / Soft Sub (h-sub is included in the scanlator field
but not the preference dropdown — it's fetched via the "Include all sub/dub streams" toggle).

> This **corrects my earlier analysis** which claimed "SUB + DUB only, no HSUB". The site does have
> Hard Sub — the user was right.

---

## 6. Video pipeline — stream types, qualities, proxies

### Stream types (VERIFIED from `StreamDto.type`, `MiruroExtractor` line 300)
Each stream returned by the `sources` pipe path has a `type`:
- `"hls"` → the URL is an m3u8 playlist (may be proxied through vault01/02.ultracloud.cc)
- `"embed"` → the URL is an embed page (needs extraction: MegaCloud / RapidCloud / OmniEmbed)

### Qualities (VERIFIED from `StreamDto.quality` + preference `PREF_QUALITY`)
- The `quality` field is a string like `"1080"`, `"720"`, `"480"`, `"360"` (or empty).
- Each stream also has `resolution{width,height}`, `codec`, `audio`, `fansub` fields.
- User preference: Highest Available (0) / 1080p / 720p / 480p / 360p.

### Proxy servers (VERIFIED from `MiruroExtractor` line 71-142)
Miruro's frontend wraps provider stream URLs through one of two proxy servers:
- `https://vault01.ultracloud.cc/` (PROXY_A)
- `https://vault02.ultracloud.cc/` (PROXY_B)

Selection is deterministic via **FNV-1a 32-bit hash** of `"$episodeId|$anilistId"` mod 2 (even→A,
odd→B). The proxy URL format:
```
{proxyBase}{xorEncode(streamUrl, PROXY_KEY)}~{xorEncode(referer, PROXY_KEY)}/pl.m3u8
```
where `xorEncode` = XOR with PROXY_KEY (cycled) → base64url (no padding). This bypasses CORS and
per-stream Referer requirements — the proxy adds the correct Referer upstream.

### Embed extractors (VERIFIED from `build.gradle` deps + `MiruroExtractor` line 344-378)
Embed URLs are pre-routed by host:
- **MegaCloud** (`megacloud.tv`, `megacloud.club`) → `MegaCloudExtractor` (Zoro-style)
- **RapidCloud** (`rapid-cloud.co`, `scloud`) → `RapidCloudExtractor` (Zoro/ChillX family)
- **Everything else** → `OmniEmbedExtractor` (generic — handles kwik, mp4upload, doodstream, etc.)
- HLS streams → `M3u8Integration` (m3u8server local proxy for header-gated CDNs)

### Required libs (from `build.gradle`)
```
implementation(project(':lib:omniembedextractor'))
implementation(project(':lib:anilib'))
implementation(project(':lib:m3u8server'))
implementation(project(':lib:cloudflareinterceptor'))
implementation(project(':lib:megacloudextractor'))
implementation(project(':lib:rapidcloudextractor'))
```

---

## 7. Episode + video URL structure

### Episode URL (the `SEpisode.url` — VERIFIED from `videoListRequest` line 1688)
The episode URL is a **JSON string** (not a path) carrying all data needed for `videoListParse`:
```json
{
  "episodeId": "<provider-episode-id>",
  "provider": "kiwi",
  "defaultSubType": "sub",
  "subTypes": { "sub": "...", "dub": "...", "ssub": "...", "h-sub": "..." },
  "fallbackProviders": { "bee": { "sub": "...", "dub": "..." }, ... },
  "anilistId": "185542"
}
```
This is how the extension avoids re-fetching the episode list when fetching videos for a different
sub-type or fallback provider.

### Anime URL (the `SAnime.url`)
Just the AniList ID (e.g. `"185542"`). The watch page on the site is
`https://www.miruro.tv/watch/185542/<slug>?ep=<N>` — the extension's `getAnimeUrl` returns
`$baseUrl/watch/${anime.url}`.

### The test episode the user provided
`https://www.miruro.to/watch/185542/skeleton-knight-in-another-world-season-2?ep=1`
- AniList ID: `185542` (Skeleton Knight in Another World Season 2)
- Episode: 1
- This URL is on `miruro.to` (mirror); the extension uses `miruro.tv` by default but the structure is
  identical across mirrors.

---

## 8. Cloudflare / WAF — what the extension needs

**VERIFIED (live probe + yuzono `MiruroBrowserFingerprintInterceptor`):**

1. **`MiruroBrowserFingerprintInterceptor`** (network interceptor) — shapes every request with
   Chrome 148 desktop headers so the WAF custom-rule doesn't trip:
   - `User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) ... Chrome/148.0.0.0 Safari/537.36`
   - `Sec-Ch-Ua: "Chromium";v="148", "Not_A Brand";v="24", "Google Chrome";v="148"`
   - `Sec-Ch-Ua-Mobile: ?0`, `Sec-Ch-Ua-Platform: "Windows"`
   - `Accept-Encoding: gzip, deflate, br` (NOT zstd — zstd-jni isn't packaged in the APK)
   - `Accept-Language: en-US,en;q=0.9`
   - For pipe API (`/api/`): `Sec-Fetch-Dest: empty`, `Sec-Fetch-Mode: cors`,
     `Sec-Fetch-Site: same-origin`, `Origin: https://www.miruro.tv`, `Referer: https://www.miruro.tv/`
   - For navigation (warm-up GETs): `Sec-Fetch-Dest: document`, `Sec-Fetch-Mode: navigate`,
     `Sec-Fetch-Site: none` (cold tab) or `same-origin` (with Referer)

2. **Inherited `CloudflareInterceptor`** (from `AnimeHttpSource.client`) — handles the managed
   challenge via WebView when `cf-mitigated: challenge` is returned. Solves once, caches
   `cf_clearance` per host. **Now required** (the WAF-only situation the yuzono comment describes has
   evolved into a full managed challenge).

3. **Cookie farming** (VERIFIED from `Miruro.kt` line 102-148):
   - `ensureBaseVisit()` — process-once `GET baseUrl` to warm cookies before the first pipe call.
   - `ensureWatchPageVisited(anilistId)` — per-anime `GET /watch/<id>` before the episodes pipe call.
   - Both are idempotent and never re-throw (a failed warm-up must not block the real fetch).
   - Miruro's CF edge "punishes request sequences that don't resemble a real browser navigating the
     site: a bare `/api/secure/pipe?e=...` call with no prior page hit is a bot signal."

---

## 9. Identity fields (CORRECTED — matches yuzono)

| Field | Value | Source |
|---|---|---|
| **Display name** | `Miruro.tv` | yuzono `extName` (NOT "Miruro 180" — the site self-names "Miruro.tv") |
| **versionId** | `1` (STABLE) | Start at 1. |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.miruro180` | Our convention (180 suffix, like AniKoto/AnimePahe/MKissa). yuzono uses `...en.miruro` — we diverge for our own namespace. |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.miruro.Miruro` | FULL path (applicationId ≠ source package). |
| **versionCode** | `1` (initial) | Bump per build. |
| **versionName** | `16.1` | Convention `16.<versionCode>`. |
| **Target site** | `https://www.miruro.tv` | Primary mirror (yuzono default). |
| **Language** | `en` | English UI. |
| **Is NSFW** | **`true`** | ★ yuzono sets `isNsfw = true` — **corrects my earlier `false` guess**. The site hosts some 18+ content (Ecchi genre + the `isAdult` filter param). |
| **Signing key** | `miruro-release.jks` (TBD) | Generate in Step 5. |

> **Naming decision:** our project uses the "180" suffix convention (AniKoto 180, AnimePahe 180,
> MKissa 180). The display name should be **`Miruro 180`** (our convention) while the internal
> `override val name` can stay `"Miruro.tv"` to match the site. Confirm with the user.

---

## 10. Settings (15 preferences — VERIFIED from `Miruro.kt` companion)

| Preference | Key | Options | Default |
|---|---|---|---|
| Preferred mirror | `preferred_mirror` | miruro.tv, miruro.to, miruro.bz, miruro.ru | miruro.tv |
| Preferred Provider | `preferred_provider` | 11 providers (dynamic from config) | kiwi (AnimePahe) |
| Preferred Sub/Dub | `preferred_sub_type` | Sub, Dub, Soft Sub | sub |
| Preferred Stream Type | `preferred_stream_type` | HLS, Embed, All | hls |
| Preferred Quality | `preferred_quality` | Highest Available, 1080p, 720p, 480p, 360p | Highest Available |
| Title Display Style | `preferred_title_style` | User Preferred, Romaji, English, Native | userPreferred |
| Include all sub/dub streams | `include_all_sub_types` | bool | true |
| Strip HTML from descriptions | `strip_html_descriptions` | bool | true |
| Merge episodes across providers | `merge_across_providers` | bool | true |
| Episode List Order | `episode_sort_order` | Descending (Newest First), Ascending | descending |
| Description Truncation | `description_truncation` | No Limit, 750/500/300/150/75 chars | No Limit |
| Show provider names in scanlator | `show_provider_in_scanlator` | bool | false |
| Include all provider streams | `include_all_providers` | bool | false |
| Filler Episode Handling | `filler_display_mode` | Mark in scanlator, Hide, Show all | mark |
| Also mark mixed-canon episodes | `filler_mark_mixed` | bool | true |
| Show NSFW | `include_nsfw` | bool | false |

---

## 11. Verification status summary (corrected)

| Item | Status | How verified |
|---|---|---|
| Miruro has its own pipe API (NOT Consumet) | ✅ VERIFIED | yuzono `Miruro.kt:buildPipeRequest` + live curl |
| Pipe API: `/api/secure/pipe?e=<base64url-json>` | ✅ VERIFIED | yuzono `buildPipeRequest` line 2091 |
| Response: XOR(PIPE_KEY) + gzip | ✅ VERIFIED | yuzono `MiruroExtractor.decryptResponse` line 199 |
| PIPE_KEY + PROXY_KEY values | ✅ VERIFIED | yuzono `Miruro.kt` line 721-722 |
| 11 providers (AnimePahe/Anikoto/AniDao/9Anime/Moon/Zoro/Pewe/Nun/Bun/Twin/Cog) | ✅ VERIFIED | yuzono `DEFAULT_PROVIDER_VALUES` line 742 |
| 4 audio types (sub/dub/ssub/h-sub) | ✅ VERIFIED | yuzono `SCANLATOR_SUB_TYPES` line 844 |
| 8 filter categories (Sort/Genres/Tags/Format/Year/Season/Status/DubLanguage) | ✅ VERIFIED | yuzono `MiruroFilters.FILTER_LIST` |
| Proxy servers vault01/02.ultracloud.cc + FNV-1a selection | ✅ VERIFIED | yuzono `MiruroExtractor` line 71-142 |
| Embed extractors (MegaCloud/RapidCloud/OmniEmbed/M3u8Integration) | ✅ VERIFIED | yuzono `build.gradle` + `MiruroExtractor` line 344 |
| Cloudflare managed challenge (live) | ✅ VERIFIED | live curl: `cf-mitigated: challenge` on .tv |
| isNsfw = true | ✅ VERIFIED | yuzono `build.gradle` |
| AniList GraphQL fallback | ✅ VERIFIED | yuzono `Miruro.kt` (AniLib usage) |
| The test episode URL structure (`/watch/<anilistId>/<slug>?ep=<N>`) | ✅ VERIFIED | yuzono `getAnimeUrl` + live URL |
| Exact live `sources` response for ep 1 of 185542 | ❌ NOT LIVE-VERIFIED | CF blocks curl; needs on-device WebView. But the yuzono extension is a working reference, so the structure is trusted. |

---

## 12. Open questions for the user

1. **Display name**: `Miruro 180` (our convention) or `Miruro.tv` (matches yuzono + site self-name)?
   Recommendation: `Miruro 180` for consistency with our other extensions, with `override val name = "Miruro.tv"`.
2. **NSFW**: yuzono sets `isNsfw = true`. Keep that? (The site does host Ecchi + has an `isAdult`
   filter, so `true` is defensible. Aniyomi will then hide it behind the NSFW toggle.)
3. **Should I follow the yuzono extension's architecture closely** (pipe API + XOR/gzip + proxy +
   4 sub-types + 11 providers + AniLib fallback + 15 settings)? It's a proven, working design.
   My recommendation: **yes** — adapt yuzono's code to our ext-lib v16 + project conventions
   (stubs module, build system, the 180 namespace), rather than reinventing it.
4. **The yuzono extension uses ext-lib features** (`getPopularAnime`/`getLatestUpdates`/`getSearchAnime`
   suspend overrides, `parallelCatchingFlatMapBlocking`, `keiyoushi.utils` preferences). Our ext-lib
   v16 stubs may or may not expose all of these. I'll verify against
   `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` during Step 2 and adapt where needed.

---

## 13. What I got wrong (and corrected)

| My earlier claim | Reality (from yuzono reference) |
|---|---|
| "Miruro uses the public Consumet API" | ❌ Miruro has its OWN pipe API; the public Consumet API is dead anyway |
| "3 servers: Default/Vidstream/Gogo" | ❌ **11 providers**: AnimePahe, Anikoto, AniDao, 9Anime, Moon, Zoro, Pewe, Nun, Bun, Twin, Cog (+ Dune/Kuz possible) |
| "2 audio types: Sub + Dub (no HSUB)" | ❌ **4 audio types**: Sub, Dub, Soft Sub (ssub), Hard Sub (h-sub) |
| "isNsfw = false" | ❌ **isNsfw = true** (yuzono sets it) |
| "Needs a WebView Turnstile solver (like MKissa)" | ✅ Confirmed — but ALSO needs the browser-fingerprint interceptor (the WAF rule is separate from the managed challenge) |
| "Backend URL is unknown / needs discovery" | ❌ The backend IS the site itself (`/api/secure/pipe` on miruro.tv) — no separate backend URL to discover |

The root cause of my errors: I analyzed the **open-source frontend repo** (which only shows the UI's
3 hardcoded source buttons) but did NOT check the **reference Aniyomi extensions repo** (yuzono),
which contains a complete working extension that reveals the true pipe API + 11 providers + 4
sub-types. I should have checked the reference repos first, per the project's `PROJECT_RULES.md` §1
("verify before trusting") and the `SHARED/README.md` guidance.

---
