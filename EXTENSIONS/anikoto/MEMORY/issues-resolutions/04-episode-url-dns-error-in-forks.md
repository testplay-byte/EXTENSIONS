# Issue 04 — `episode.url` Format Causes DNS Errors in Forks

> Date: 2026-06-26 (session 43) · Status: VERIFIED · Fixed in: Anikoto v16.27
> Cross-extension issue: applies to ALL Aniyomi extensions on ext-lib 16 that store
> non-URL data in `SEpisode.url`.

## Symptom

When clicking an episode in a fork (e.g., AnimeKhor) that uses the legacy video pipeline,
the app immediately shows:

```
PlatformException(BRIDGE_ERROR,
  Unable to resolve host "anikototv.towitch-hat-atelier-ikmut":
    No address associated with hostname,
  Method: Aniyomi.getVideoList)
```

- **Immediate** — no loading spinner, no network activity
- The hostname in the error is `baseUrl` concatenated directly with the anime slug
- Missing `/` separator and `/watch/` prefix between domain and slug
- The method is always `getVideoList` (the legacy flow), never `getHosterList`

## Root cause

### The two video pipelines in Aniyomi

The Aniyomi app has **two** video loading pipelines (verified in
`SHARED/REFERENCE_HUB/aniyomi-app/source-api/.../AnimeHttpSource.kt`):

| Pipeline | Method | Used by | Default URL construction |
|---|---|---|---|
| **New (ext-lib 16)** | `getHosterList(episode)` → `getVideoList(hoster)` | Official Aniyomi, updated forks | `hosterListRequest`: `GET(baseUrl + episode.url)` |
| **Legacy (pre-16)** | `getVideoList(episode)` | Older forks | `videoListRequest`: `GET(baseUrl + episode.url)` (line 446) |

**Both pipelines construct the request URL the same way: `baseUrl + episode.url`.**

### What went wrong

Our extension stored `EpisodeMeta`-encoded data in `episode.url` to avoid re-fetching the
watch page when the user opens an episode. The v16.25 format was:

```
slug/ep-<epNum>|<malId>|<timestamp>|<dataIds>|<subFlag>|<dubFlag>|<epTitle>
```

This is NOT a valid URL path — it's missing:
1. Leading `/` separator
2. `/watch/` prefix
3. Contains `|` pipe characters (not URL-safe)

When the legacy pipeline does `baseUrl + episode.url`:
```
"https://anikototv.to" + "witch-hat-atelier-ikmut/ep-1|57646|..."
= "https://anikototv.towitch-hat-atelier-ikmut/ep-1|..."  ← DNS FAILURE
```

The slug `witch-hat-atelier-ikmut` gets concatenated directly onto the domain `anikototv.to`,
creating the invalid hostname `anikototv.towitch-hat-atelier-ikmut`.

### Why it worked in official Aniyomi but not forks

Our extension overrides `getHosterList(episode)` — the new pipeline method. Inside that
override, we decode `EpisodeMeta` from `episode.url` directly (no HTTP request made using
the URL). Official Aniyomi calls `getHosterList` first, so the malformed `episode.url` is
never used as a URL.

But **forks that use the legacy pipeline** call `getVideoList(episode)` instead. The default
base class implementation constructs the request as `GET(baseUrl + episode.url)`, which
produces the malformed URL → DNS error → immediate crash.

## The fix (3 layers, v16.27)

### Layer 1: Store `episode.url` as a valid URL path

**File:** `EpisodeMeta.kt`

Changed `encode()` to produce a valid URL path with metadata in the **fragment** (`#`):

```kotlin
// NEW (v16.27): valid URL path + metadata in fragment
fun encode(): String {
    val escapedTitle = epTitle.replace("|", "│")
    val subFlag = if (hasSub) "1" else "0"
    val dubFlag = if (hasDub) "1" else "0"
    val fragment = "$malId|$timestamp|$dataIds|$subFlag|$dubFlag|$escapedTitle"
    return "/watch/$slug/ep-$epNum#$fragment"
}
// e.g., "/watch/witch-hat-atelier-ikmut/ep-1#57646|1718668800|token|1|0|Episode 1"
```

**Why the fragment (`#`)?**
- HTTP clients (OkHttp) strip the fragment before sending requests — the server only sees the clean path
- The fragment preserves our metadata for decoding in `getHosterList`
- `baseUrl + episode.url` now produces: `https://anikototv.to/watch/witch-hat-atelier-ikmut/ep-1#...`
- The host is correctly `anikototv.to`, path is `/watch/witch-hat-atelier-ikmut/ep-1` ✅
- **Verified live:** the server returns HTTP 200 with the watch page (fragment ignored)

**Backward compatibility:** `decode()` handles BOTH old and new formats — saved episodes
from v16.25 (old pipe-delimited format) still decode correctly after updating to v16.27.

**New helper:** `extractUrlPath(encoded)` — returns the `/watch/slug/ep-N` path (without
fragment) from either format. Used by `getEpisodeUrl` and available for `hosterListRequest`.

### Layer 2: Override `getVideoList(SEpisode)` — required for forks

**Files:** `stubs/AnimeSource.kt`, `stubs/AnimeHttpSource.kt`, `Anikoto.kt`

Added `getVideoList(episode: SEpisode)` to both stubs (the ext-lib v16 runtime has it at
`AnimeHttpSource.kt:425`, but our stubs didn't — so we couldn't override it before).

The override delegates to `getHosterList` + flattens the result:

```kotlin
override suspend fun getVideoList(episode: SEpisode): List<Video> {
    return try {
        val hosters = getHosterList(episode)
        if (hosters.isEmpty()) return emptyList()
        hosters.flatMap { it.videoList ?: emptyList() }
    } catch (e: Exception) {
        AnikotoLog.e("getVideoList(episode) [legacy]: failed — ${e.message}", e)
        emptyList()
    }
}
```

This bypasses the default `GET(baseUrl + episode.url)` code path entirely. Forks using the
legacy pipeline now get the same video list as the new pipeline.

Also added the legacy `videoListParse(response: Response): List<Video>` stub + override
(returns `emptyList()` — never called because `getVideoList(episode)` is overridden, but
required to satisfy the abstract method contract at compile time).

### Layer 3: Override `getEpisodeUrl` — for "Open in WebView" + deep links

**File:** `Anikoto.kt`

The default `getEpisodeUrl(episode)` returns `episode.url` as-is — which is a relative path
(after Layer 1) or encoded metadata (before). Neither is a full URL that a WebView can load.

```kotlin
override fun getEpisodeUrl(episode: SEpisode): String {
    val path = EpisodeMeta.extractUrlPath(episode.url)
    return "$baseUrl$path"
}
```

**Note on `getAnimeUrl`:** the default returns `animeDetailsRequest(anime).url.toString()`,
and our `animeDetailsRequest` already returns `GET("$baseUrl/watch/${anime.url}/ep-1")` —
so `getAnimeUrl` already produces the correct full URL. No override needed.

**Note on `hosterListRequest`:** our `getHosterList` override never calls it (we decode
`EpisodeMeta` directly). OkHttp strips fragments automatically, so even the default
`GET(baseUrl + episode.url)` would now produce a valid request. No override needed.

## Verification

### Build checklist (guides/04) — ALL PASS
- ✅ BUILD SUCCESSFUL (v16.27, 156,235 bytes, MD5 `0cb340ddb010aee9b68e762b5d6b1661`)
- ✅ `extClass = ".Anikoto"` (not doubled)
- ✅ `versionId = 11` STABLE
- ✅ `Stub!` count = 0
- ✅ Anikoto class in DEX (465 refs)
- ✅ `getVideoList(SEpisode)` override in DEX (method descriptor + log string present)
- ✅ `getEpisodeUrl` override in DEX (2 refs)
- ✅ `extractUrlPath` helper in DEX (3 refs)
- ✅ `/watch/` prefix present in DEX (new encode format)

### Logic verification (Python simulation, 6 tests)
1. ✅ New format round-trip: encode → decode preserves all 8 fields
2. ✅ Backward compat: old v16.25 format still decodes correctly; `extractUrlPath` constructs correct URL from old format
3. ✅ Title with pipe: escaped to `│` in encode, un-escaped in decode
4. ✅ All 4 sub/dub flag combinations decode correctly
5. ✅ All URL constructions produce valid URLs (host = `anikotv.to`, not `anikotv.toslug`)
6. ✅ **Live test:** `GET https://anikototv.to/watch/road-of-naruto-ggjw8/ep-1#57646|...` → HTTP 200, watch page returned (fragment ignored by server)

### DEX verification
- `getVideoList:(Leu/kanade/tachiyomi/animesource/model/SEpisode` — the new override method descriptor
- `getVideoList(episode) [legacy]: failed —` — the error log string from the override
- `extractUrlPath` — the new helper method
- `/watch/` — the new encode prefix

## Prevention rules for future extensions

### Rule 1: `SEpisode.url` MUST be a valid URL path

```kotlin
// ✅ GOOD: Valid URL path (with optional fragment for metadata)
episode.url = "/watch/naruto-shippuden/ep-1"
episode.url = "/watch/naruto/ep-1#metadata-here"  // Fragment is safe

// ❌ BAD: Not a URL path
episode.url = "naruto/ep-1|12345|..."    // Missing leading / and /watch/
episode.url = "12345"                      // Just an ID
episode.url = "slug|metadata|more"         // Pipe-delimited data
```

### Rule 2: If you need to carry metadata, use the fragment

The URL fragment (`#...`) is stripped by HTTP clients before sending requests.
It's the perfect place to store metadata that only the extension needs:

```kotlin
episode.url = "/watch/slug/ep-1#malId|timestamp|dataIds|sub|dub|title"
```

### Rule 3: ALWAYS override `getVideoList(SEpisode)` for fork compatibility

Even if you use the hoster pipeline, add a legacy override that delegates to
`getHosterList()`. This costs nothing and prevents the #1 fork compatibility issue:

```kotlin
override suspend fun getVideoList(episode: SEpisode): List<Video> {
    return try {
        getHosterList(episode).flatMap { it.videoList ?: emptyList() }
    } catch (_: Exception) { emptyList() }
}
```

You also need to add `getVideoList(episode: SEpisode)` to your stubs (both `AnimeSource`
interface and `AnimeHttpSource` open override) — the ext-lib v16 stubs don't include it
by default, even though the runtime has it.

### Rule 4: ALWAYS override `getEpisodeUrl` if `episode.url` isn't a full URL

The default returns `episode.url` as-is. If you store a relative path or encoded data,
override it to return a full URL:

```kotlin
override fun getEpisodeUrl(episode: SEpisode): String {
    return "$baseUrl${EpisodeMeta.extractUrlPath(episode.url)}"
}
```

### Rule 5: Test in at least one fork before releasing

Official Aniyomi uses the hoster pipeline, so it may mask issues that forks expose.
Always test in at least one fork that uses the legacy pipeline.

### Rule 6: `SAnime.url` follows the same convention

`SAnime.url` should be a path that works with `baseUrl`. Our extension stores just the
slug (e.g., `naruto-shippuden`) and constructs the full URL in `animeDetailsRequest` —
this is safe because `getAnimeUrl` delegates to `animeDetailsRequest`.

## File changes (v16.27)

| File | Change |
|---|---|
| `EpisodeMeta.kt` | `encode()` → `/watch/slug/ep-N#fragment`; `decode()` → backward-compatible (handles old + new); added `extractUrlPath()` |
| `stubs/AnimeSource.kt` | Added `suspend fun getVideoList(episode: SEpisode): List<Video>` to interface |
| `stubs/AnimeHttpSource.kt` | Added `override suspend fun getVideoList(episode: SEpisode)`, `videoListRequest(episode)`, `videoListParse(response)` (legacy 1-arg) |
| `Anikoto.kt` | Added `getVideoList(SEpisode)` override (delegates to `getHosterList` + flatten); Added `getEpisodeUrl` override; Added legacy `videoListParse(response)` stub |
| `build.gradle.kts` | `extVersionCode` 26 → 27 |
| `AnikotoLog.kt` | `EXTENSION_VERSION` → `v16.27` |
