# Episode Metadata Enrichment — Implementation Guide

> **How to implement episode thumbnails, descriptions, and titles using the multi-source method
> (Jikan + AniList + Anikage + Kitsu).** This is the technique used by AniKoto (sessions 35-38) and
> AnimePahe (session 02). Follow this guide to add it to ANY new extension.
>
> **Location:** `EXTENSIONS/<name>/DEV/src/.../metadata/EpisodeMetadataFetcher.kt` + `WebViewFetcher.kt`

---

## What this feature does

Enriches the episode list with:
- **Thumbnails** — episode preview images (shown next to each episode)
- **Titles** — real episode titles like "EP 1 - Asteroid Blues" (instead of just "Episode 1")
- **Descriptions** — episode synopsis/summary

Without enrichment, episodes show just numbers ("Episode 1", "Episode 2"). With enrichment, they
look like a professional streaming app.

## The 4-source merge strategy

Different APIs have different coverage. We fetch from ALL of them and merge with priority:

| Source | What it provides | API | Behind Cloudflare? | Fetch method |
|---|---|---|---|---|
| **Jikan (MAL)** | Episode TITLES + air dates | `api.jikan.moe/v4/anime/<malId>/episodes` | No | OkHttp direct |
| **AniList** | MAL→AniList ID lookup + banner + streamingEpisodes thumbnails | `graphql.anilist.co` (GraphQL POST) | Sends CORS `*` | OkHttp direct (fallback: WebView) |
| **Anikage.cc** | Thumbnails + descriptions + titles (TheTVDB data) | `anikage.cc/api/media/anime/<anilistId>/episodes` | Yes (403 without browser UA) | OkHttp with browser UA (inherited client) |
| **Kitsu** | Thumbnails + descriptions + titles (fallback) | `kitsu.app/api/edge/...` | Sometimes unreliable | OkHttp direct (fallback: WebView) |

**Merge priority:**
- **Thumbnail:** Anikage → AniList streamingEpisodes → Kitsu → banner → anime cover
- **Title:** Jikan → Anikage → Kitsu
- **Description:** Anikage → Kitsu

## ★ Critical: OkHttp-first, WebView-fallback (NOT WebView-first)

**AniKoto used WebView-first for AniList/Kitsu** because its WebView origin was `megaplay.buzz` (a
light-Cloudflare video CDN that loads easily). **This does NOT work for sites behind a HARD
Cloudflare managed challenge** (like animepahe.pw's Turnstile).

### Why WebView-first fails on hard-Cloudflare sites

1. WebViewFetcher loads the extension's site as origin (e.g. `animepahe.pw`)
2. Cloudflare shows the challenge page ("Just a moment...")
3. `onPageFinished` fires on the CHALLENGE page
4. The challenge page has **CSP: `default-src 'none'`** — blocks ALL cross-origin `fetch()` calls
5. `fetch('https://graphql.anilist.co', ...)` is blocked by CSP → AniList/Kitsu return null
6. Result: only Jikan (OkHttp direct) works → titles only, no thumbnails/descriptions

### The fix: OkHttp-first + data:URL-origin WebView fallback

```kotlin
private fun fetchString(url: String): String? {
    // 1. Try OkHttp FIRST (works for AniList + Anikage + Jikan on most devices)
    try {
        val req = Request.Builder().url(url).headers(apiHeaders).build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) return resp.body?.string()
        }
    } catch (e: Exception) { /* fall through to WebView */ }

    // 2. Fall back to WebView with a CLEAN origin (data: URL — no CSP)
    if (webViewFetcher != null) {
        return try { webViewFetcher.fetchText(url) } catch (e: Exception) { null }
    }
    return null
}
```

**WebViewFetcher origin:** `data:text/html,<html><body></body></html>` — a blank page with NO CSP
restrictions. The null origin works for CORS (AniList sends `Access-Control-Allow-Origin: *`).

### When to use which approach

| Site's Cloudflare level | WebView origin | Fetch strategy |
|---|---|---|
| **None / light** (AniKoto's megaplay.buzz) | The site itself | WebView-first is OK |
| **Hard** (animepahe.pw Turnstile) | `data:text/html,...` (blank) | **OkHttp-first, WebView-fallback** |
| **Unknown** | `data:text/html,...` (blank) | **OkHttp-first, WebView-fallback** (safest) |

**Recommendation:** Always use OkHttp-first + data:URL-origin WebView fallback. It works for ALL
sites, regardless of Cloudflare level. AniKoto's WebView-first was an optimization for its specific
light-Cloudflare situation — don't copy it blindly.

## Required files

```
EXTENSIONS/<name>/DEV/src/.../<name>/
├── <Name>.kt                          ← main source (getEpisodeList override + MAL ID extraction)
├── <Name>Log.kt                       ← logcat-only logger
├── WebViewFetcher.kt                  ← Chrome TLS fallback (origin: data:text/html)
├── <Name>Settings.kt                  ← settings UI (3 metadata toggles)
└── metadata/
    └── EpisodeMetadataFetcher.kt      ← 4-source merge fetcher
```

## Implementation steps

### 1. Create the logger

```kotlin
object <Name>Log {
    private const val TAG = "<Name>"
    fun i(msg: String) = Log.i(TAG, msg)
    fun d(msg: String) = Log.d(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, t: Throwable? = null) = if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
    fun trunc(s: String, maxLen: Int = 60): String =
        if (s.length <= maxLen) s else s.substring(0, maxLen) + "…(${s.length})"
}
```

### 2. Create the WebViewFetcher (with data: URL origin)

Port from `EXTENSIONS/animepahe/DEV/src/.../WebViewFetcher.kt`. Key points:
- Origin: `data:text/html,<html><body></body></html>` (NOT the extension's site)
- Methods: `warmUp()`, `fetchText(url)`, `postJson(url, body)`
- Uses Chrome's TLS (BoringSSL) to bypass OkHttp TLS fingerprint blocking

### 3. Create the EpisodeMetadataFetcher

Port from `EXTENSIONS/animepahe/DEV/src/.../metadata/EpisodeMetadataFetcher.kt`. Key points:
- 4 sources: Jikan (titles), AniList (ID lookup + thumbnails), Anikage (thumbnails+desc+titles), Kitsu (fallback)
- **OkHttp-first, WebView-fallback** for ALL sources
- Never throws (returns empty map on error)
- Caches per MAL ID
- DTOs use `@SerialName` for snake_case JSON + `@EncodeDefault` for optional fields

### 4. Create the settings (3 metadata toggles)

```kotlin
class <Name>Settings(private val prefs: SharedPreferences) {
    val loadThumbnails: Boolean get() = prefs.getBoolean(PREF_LOAD_THUMBNAILS_KEY, true)
    val loadTitles: Boolean get() = prefs.getBoolean(PREF_LOAD_TITLES_KEY, true)
    val loadDescriptions: Boolean get() = prefs.getBoolean(PREF_LOAD_DESCRIPTIONS_KEY, true)

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Category: Episode metadata — 3 toggles, all default ON
        // ★ summaryOn wording: "Fetching <thing> from external sources"
        //   (do NOT name specific sources like "MAL / AniList / Kitsu" —
        //   just say "external sources". This matches AniKoto's convention.)
    }
}
```

**Exact summary wording (copy these verbatim — they match AniKoto):**

| Toggle | title | summaryOn | summaryOff |
|---|---|---|---|
| Thumbnails | Load episode thumbnails | `Fetching preview images from external sources` | `Episode thumbnails disabled (faster episode list loading)` |
| Titles | Load episode titles | `Fetching episode titles from external sources` | `Using default episode numbers only` |
| Descriptions | Load episode descriptions | `Fetching episode descriptions from external sources` | `Episode descriptions disabled` |

★ **Convention:** NEVER name specific external sources (MAL, AniList, Kitsu, etc.) in the user-facing
summary text. Just say "external sources". The user doesn't need to know which APIs are used — that's
an implementation detail. Naming sources also makes the text longer and harder to read.

### 5. Wire into the main source class

Implement `ConfigurableAnimeSource` + override `getEpisodeList`:

```kotlin
class <Name> : AnimeHttpSource(), ConfigurableAnimeSource {
    // ... lazy fields: preferences, settings, webViewFetcher, metadataFetcher ...

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        // 1. Pre-warm WebView (for potential fallback fetches)
        webViewFetcher.warmUp()

        // 2. Fetch detail page → extract MAL ID from external links
        val detailResponse = client.newCall(animeDetailsRequest(anime)).execute()
        val detailDoc = detailResponse.use { it.asJsoup() }
        val malId = extractMalId(detailDoc)  // parse myanimelist.net/anime/<id> from external links
        val animeCoverUrl = detailDoc.selectFirst("<cover-selector>")?.attr("href")

        // 3. Fetch episodes (your site's episode API/HTML)
        val episodes = fetchEpisodes(...)

        // 4. Enrich with metadata (respects 3 toggles)
        enrichEpisodesWithMetadata(episodes, malId, animeCoverUrl)

        return episodes.reversed()  // descending (newest first)
    }

    private suspend fun enrichEpisodesWithMetadata(
        episodes: List<SEpisode>, malId: String?, animeCoverUrl: String?,
    ) {
        if (episodes.isEmpty()) return
        val loadThumbnails = settings.loadThumbnails
        val loadTitles = settings.loadTitles
        val loadDescriptions = settings.loadDescriptions

        // Skip if all toggles OFF (zero API calls — fast)
        if (!loadThumbnails && !loadTitles && !loadDescriptions) return
        if (malId.isNullOrBlank()) return

        try {
            val metadata = metadataFetcher.fetch(malId, animeCoverUrl)
            for (ep in episodes) {
                val epNum = ep.episode_number.toInt()
                val epMeta = metadata[epNum] ?: continue

                if (loadThumbnails) ep.preview_url = epMeta.thumbnailUrl
                if (loadDescriptions) ep.summary = epMeta.description
                if (loadTitles) {
                    epMeta.title?.takeIf { it.isNotBlank() }?.let {
                        ep.name = "EP $epNum - $it"  // ★ format: "EP N - title" (NOT "Episode N - title")
                    }
                }
            }
        } catch (e: Exception) {
            <Name>Log.e("enrichEpisodesWithMetadata: failed — ${e.message}", e)
        }
    }

    private fun extractMalId(doc: Document): String? {
        // Parse external links for myanimelist.net/anime/<id>
        val links = doc.select("<external-links-selector> a")
        for (link in links) {
            val match = Regex("myanimelist\\.net/anime/(\\d+)").find(link.attr("abs:href"))
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        settings.setupPreferenceScreen(screen)
    }
}
```

## ★ Title format: "EP N - title" (NOT "Episode N - title")

The episode name format MUST be `EP $epNum - $sourceTitle` (matching AniKoto). Do NOT use
`"Episode $epNum - $title"`. The "EP" prefix is the established convention.

## How to get the MAL ID

The MAL ID is the key that unlocks ALL metadata. How to get it depends on the site:

| Method | How | When to use |
|---|---|---|
| **External links** | Parse `myanimelist.net/anime/<id>` from the detail page's external links section | Best — if the site has MAL links (animepahe does) |
| **data-mal attribute** | Some sites embed `data-mal="<id>"` in the detail page HTML | AniKoto uses this |
| **Title search** | If no MAL link, search Jikan by title: `api.jikan.moe/v4/anime?q=<title>&limit=1` | Fallback — less reliable (may match wrong anime) |

**If the site has NO MAL link and no data-mal attribute:** use the title-search fallback. The first
Jikan result's `mal_id` is usually correct for well-known anime.

## ★ AniList ID extraction (alternative to MAL ID — MKissa pattern)

If the site uses **AniList-hosted thumbnails** (the thumbnail URL contains `anilist.co/file/`), you
can extract the **AniList media ID** directly from the URL — no MAL link needed.

### The pattern

AniList thumbnail URLs look like:
```
https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx182300-IYkq5KrkQq1V.jpg
                                                    ↑↑↑↑↑↑↑↑↑
                                                    the AniList media ID (182300)
```

The ID is the number after `bx` and before the `-` separator. Extract it with:
```kotlin
fun extractAnilistId(thumbnailUrl: String?): String? {
    if (thumbnailUrl.isNullOrBlank()) return null
    return Regex("""bx(\d+)-""").find(thumbnailUrl)?.groupValues?.get(1)
}
```

### Why this is better than MAL ID (when available)

1. **No external links needed** — the ID is right in the thumbnail URL.
2. **Direct Anikage query** — Anikage takes the AniList ID directly (no MAL→AniList lookup needed).
3. **One API call** — Anikage returns title + description + thumbnail + airDate in a single call.
4. **OkHttp-only** — Anikage (`anikage.cc`) and Jikan (`api.jikan.moe`) are NOT behind Cloudflare, so no WebView fallback needed.

### The Anikage + Jikan approach (MKissa's fetcher)

```
1. Extract AniList ID from thumbnail URL (regex bx(\d+)-)
2. Query Anikage: GET https://anikage.cc/api/media/anime/<anilistId>/episodes
   → Returns: [{number, title, description, img, airDate, isFiller}, ...]
   → This ONE call gives all 3 metadata types (thumbnail + title + description)
3. Fallback: Jikan title-search (if Anikage has no data)
   → GET https://api.jikan.moe/v4/anime?q=<title>&limit=1 → mal_id
   → GET https://api.jikan.moe/v4/anime/<mal_id>/episodes → titles + air dates
```

**Reference implementation:** `EXTENSIONS/mkissa/DEV/src/.../metadata/EpisodeMetadataFetcher.kt`

### When to use which approach

| Site provides | Recommended approach | Fetcher complexity |
|---|---|---|
| MAL external links | MAL ID → Jikan + AniList + Anikage + Kitsu (4-source merge) | Complex (AniKoto/AnimePahe pattern) |
| AniList-hosted thumbnails | AniList ID → Anikage + Jikan (2-source) | **Simple** (MKissa pattern) |
| Neither | Jikan title-search → MAL ID → 4-source merge | Complex + less reliable |

**Recommendation:** if the site uses AniList thumbnails (many anime sites do — they're the standard
cover image source), use the AniList ID approach. It's simpler, faster (one API call), and doesn't
need WebView.

## Settings structure

| Category | Preferences | Default |
|---|---|---|
| **Episode metadata** | Load episode thumbnails (switch) | ON |
| | Load episode titles (switch) | ON |
| | Load episode descriptions (switch) | ON |
| **Video playback** | (placeholder — add quality/audio/server in Step 4) | — |

All toggles default ON. If ALL are OFF, the fetcher is skipped entirely (zero API calls — fast
episode list loading). Each toggle independently controls its field.

★ **Summary wording convention:** `summaryOn` = `"Fetching <thing> from external sources"` (NEVER
name specific APIs like MAL/AniList/Kitsu). `summaryOff` = a short description of the disabled
state. See the exact wording table in §4 above.

## Common issues + fixes

### Issue: Thumbnails + descriptions missing, but titles work
- **Cause:** AniList/Anikage/Kitsu fetches failing. Jikan (OkHttp direct) works → titles only.
- **Fix:** Use OkHttp-first (not WebView-first). Verify AniList returns 200 with curl. Check that
  the inherited `client` (with CloudflareInterceptor) is passed to the fetcher — not a raw OkHttpClient.

### Issue: WebView fetches all fail (CSP block)
- **Cause:** WebView origin is the extension's site, which shows a Cloudflare challenge page with
  strict CSP (`default-src 'none'`) that blocks all cross-origin fetch().
- **Fix:** Change WebView origin to `data:text/html,<html><body></body></html>` (blank page, no CSP).

### Issue: No metadata at all (not even titles)
- **Cause:** MAL ID not found — `extractMalId` returned null.
- **Fix:** Check the detail page's HTML for MAL external links. If none exist, implement title-search
  fallback via Jikan's search API.

### Issue: Wrong title format ("Episode 1 - title" instead of "EP 1 - title")
- **Cause:** Used `"Episode $epNum - $title"` instead of `"EP $epNum - $title"`.
- **Fix:** Change to `ep.name = "EP $epNum - $sourceTitle"`.

### Issue: `$$serializer` classes stripped in release build (R8)
- **Cause:** ProGuard/R8 minification strips kotlinx.serialization serializers.
- **Fix:** ProGuard rules keep ALL extension classes + `$$serializer` classes (see Step 5 §5.4).

## Verification

After implementing, test on-device:
1. Open an anime → episode list loads
2. Episodes show **thumbnails** (preview images) — if MAL ID found + AniList/Anikage has data
3. Episode names show as **"EP N - title"** (not "Episode N") — if Jikan has data
4. Tapping an episode shows a **description** — if Anikage/Kitsu has data
5. Settings → "Episode metadata" → toggle off thumbnails → episode list loads faster
6. `adb logcat -s <Name>:*` shows "enriched N/M episodes" (not "no MAL link found")

## Reference implementations

- **AnimePahe** (current, OkHttp-first): `EXTENSIONS/animepahe/DEV/src/.../metadata/EpisodeMetadataFetcher.kt`
- **AniKoto** (WebView-first, for light-Cloudflare sites): `EXTENSIONS/anikoto/DEV/src/.../metadata/EpisodeMetadataFetcher.kt`
- **AniKoto's research**: `EXTENSIONS/anikoto/MEMORY/research/episode-metadata-kitsu-implementation-plan.md`
