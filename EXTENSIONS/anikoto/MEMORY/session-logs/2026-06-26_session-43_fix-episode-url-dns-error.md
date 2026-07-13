# Session 43 — Fix `episode.url` DNS Error in Forks (v16.27)

> Date: 2026-06-26 · Session #: 43 · Duration: ~medium · Timezone: Asia/Karachi
> Type: BUG FIX — DNS error when playing videos in forks (AnimeKhor etc.)
> Follows: session 42 (search fix, v16.26) — per rule §2 "one change at a time"

## Goal

Fix the DNS error that occurs when playing videos in Aniyomi forks (e.g., AnimeKhor)
that use the legacy video pipeline. The issue is documented in the user-provided
`episode-url-dns-error-in-forks.md` (saved to `upload/`).

Per rule §1 (verify before trusting): analyze the root cause against the actual ext-lib v16
runtime source, verify the fix logic with simulation + live tests, then build + verify.

## Root cause analysis

### Step 1 — Read the user's issue document

`upload/episode-url-dns-error-in-forks.md` describes:
- **Symptom:** clicking an episode in a fork immediately shows
  `Unable to resolve host "anikototv.towitch-hat-atelier-ikmut"` — the slug gets concatenated
  directly onto the domain (no `/` separator, no `/watch/` prefix).
- **Root cause:** `episode.url` stored `EpisodeMeta`-encoded data
  (`slug/ep-N|malId|ts|dataIds|sub|dub|title`) — NOT a valid URL path.
- **Why it only breaks in forks:** official Aniyomi calls `getHosterList(episode)` (new
  pipeline), which decodes `EpisodeMeta` directly without using `episode.url` as a URL.
  Forks call `getVideoList(episode)` (legacy pipeline), whose default does
  `GET(baseUrl + episode.url)` → malformed URL → DNS failure.

### Step 2 — Verify against the ext-lib v16 runtime source

Read `REFERENCE_HUB/aniyomi-app/source-api/.../AnimeHttpSource.kt` (the runtime, not stub):

```kotlin
// Line 425 — legacy getVideoList(episode) — the method forks call
override suspend fun getVideoList(episode: SEpisode): List<Video> {
    return fetchVideoList(episode).awaitSingle()
}

// Line 431 — fetchVideoList calls videoListRequest
override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
    return client.newCall(videoListRequest(episode))...
}

// Line 445 — videoListRequest does GET(baseUrl + episode.url) — THE BUG SOURCE
protected open fun videoListRequest(episode: SEpisode): Request {
    return GET(baseUrl + episode.url, headers)
}
```

**Verified:** the default legacy pipeline does `GET(baseUrl + episode.url)`. With our
`slug/ep-N|...` format, this produces `https://anikotv.toslug/ep-N|...` → DNS failure.

Also verified: the runtime has BOTH `getVideoList(hoster: Hoster)` (new, line 376) AND
`getVideoList(episode: SEpisode)` (legacy, line 425) — but our stubs only had the new one.
So we couldn't override the legacy method until we added it to the stubs.

### Step 3 — Verify the fix against the live site

```bash
# Test: does the /watch/ path work with a fragment?
curl "https://anikototv.to/watch/road-of-naruto-ggjw8/ep-1#57646|1718668800|token|1|0|Test"
# → HTTP 200, 80KB watch page (fragment ignored by server) ✅
```

The server honors the `/watch/` path and ignores the fragment — confirming the fragment-based
encoding approach works.

## The fix (3 layers, v16.27)

### Layer 1: `EpisodeMeta.kt` — URL-safe encode + backward-compat decode

**`encode()`** now produces a valid URL path with metadata in the fragment:
```kotlin
fun encode(): String {
    val escapedTitle = epTitle.replace("|", "│")
    val subFlag = if (hasSub) "1" else "0"
    val dubFlag = if (hasDub) "1" else "0"
    val fragment = "$malId|$timestamp|$dataIds|$subFlag|$dubFlag|$escapedTitle"
    return "/watch/$slug/ep-$epNum#$fragment"
}
// e.g., "/watch/witch-hat-atelier-ikmut/ep-1#57646|1718668800|token|1|0|Episode 1"
```

**`decode()`** handles BOTH formats:
- New: `/watch/slug/ep-N#fragment` → split on `#`, parse path for slug+epNum, parse fragment for the rest
- Old: `slug/ep-N|malId|ts|...` → split on `|`, first part is `slug/ep-N` (backward compat for saved episodes)

**New helper `extractUrlPath(encoded)`**: returns the `/watch/slug/ep-N` path (no fragment) from either format. Used by `getEpisodeUrl`.

### Layer 2: Stubs + `getVideoList(SEpisode)` override

**`stubs/AnimeSource.kt`:** Added `suspend fun getVideoList(episode: SEpisode): List<Video>` to the interface.

**`stubs/AnimeHttpSource.kt`:** Added:
- `override suspend fun getVideoList(episode: SEpisode): List<Video>` (open override)
- `protected open fun videoListRequest(episode: SEpisode): Request` (legacy request builder)
- `protected open fun videoListParse(response: Response): List<Video>` (legacy 1-arg parser)

**`Anikoto.kt`:** Added the override:
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
This delegates to `getHosterList` (which handles everything) and flattens the result into a
flat `List<Video>` (legacy format). Never throws — returns empty list on failure.

Also added the legacy `videoListParse(response: Response)` stub (returns `emptyList()` —
never called because `getVideoList(episode)` is overridden, but required by the abstract
method contract).

### Layer 3: `getEpisodeUrl` override

The default `getEpisodeUrl(episode)` returns `episode.url` as-is — a relative path (after
Layer 1) or encoded metadata (before). Neither is a full URL for WebView.

```kotlin
override fun getEpisodeUrl(episode: SEpisode): String {
    val path = EpisodeMeta.extractUrlPath(episode.url)
    return "$baseUrl$path"
}
```

**Skipped overrides (verified not needed):**
- `getAnimeUrl`: default returns `animeDetailsRequest(anime).url.toString()`, and our
  `animeDetailsRequest` returns `GET("$baseUrl/watch/${anime.url}/ep-1")` — already correct.
- `hosterListRequest`: our `getHosterList` never calls it (decodes EpisodeMeta directly).
  OkHttp strips fragments automatically, so the default would also work now.

## Verification

### Build
```
./gradlew :src:en:anikoto:assembleDebug --no-daemon
> BUILD SUCCESSFUL in 45s
APK: aniyomi-en.anikoto-v16.27-debug.apk (156,235 bytes)
MD5: 0cb340ddb010aee9b68e762b5d6b1661
```

### Build checklist (guides/04) — ALL PASS
| # | Check | Result |
|---|---|---|
| 1 | `extClass = ".Anikoto"` (not doubled) | ✅ |
| 2 | Stubs in `:stubs` module, `compileOnly` | ✅ |
| 3 | `versionCode=27`, `versionId=11` STABLE | ✅ |
| 4 | Manifest placeholders correct | ✅ |
| 5 | `settings.gradle.kts` includes `:stubs` + `:src:en:anikoto` | ✅ |
| 6 | APK badging: `versionCode=27 versionName=16.27` | ✅ |
| 7 | extClass NOT doubled in manifest | ✅ `.Anikoto` |
| 8 | "Stub!" count = 0 | ✅ 0 |
| 9 | Anikoto class in DEX | ✅ 465 refs |
| 10 | 5 icon densities | ✅ |
| 11 | APK copied to both `WORKSPACE/APK/` + `WORKSPACE/DEV/ANIKOTO/APK/` | ✅ |

### DEX diff (v16.26 → v16.27)
- ✅ `getVideoList:(Leu/kanade/tachiyomi/animesource/model/SEpisode` — new override method descriptor
- ✅ `getVideoList(episode) [legacy]: failed —` — error log string from the override
- ✅ `getEpisodeUrl` — 2 refs (override present)
- ✅ `extractUrlPath` — 3 refs (new helper)
- ✅ `/watch/` — new encode prefix present
- ✅ No old pipe-only format strings in encode

### Logic verification (Python simulation, 6 tests — all pass)
1. ✅ New format round-trip: encode → decode preserves all 8 fields
2. ✅ Backward compat: old v16.25 format still decodes correctly; `extractUrlPath` constructs correct URL from old format
3. ✅ Title with pipe: escaped to `│` in encode, un-escaped in decode
4. ✅ All 4 sub/dub flag combinations decode correctly
5. ✅ All URL constructions produce valid URLs (host = `anikotv.to`, not `anikotv.toslug`)
6. ✅ **Live test:** `GET https://anikototv.to/watch/road-of-naruto-ggjw8/ep-1#57646|...` → HTTP 200, watch page returned (fragment ignored by server)

## What changed for the user

| Before (v16.26) | After (v16.27) |
|---|---|
| `episode.url` = `slug/ep-N\|malId\|ts\|dataIds\|sub\|dub\|title` (NOT a URL path) | `episode.url` = `/watch/slug/ep-N#malId\|ts\|dataIds\|sub\|dub\|title` (valid URL path + fragment) |
| Forks using legacy pipeline: **DNS error** (`Unable to resolve host "anikotv.toslug..."`) | Forks using legacy pipeline: **`getVideoList(episode)` override delegates to `getHosterList`** → works |
| "Open in WebView" on an episode: `getEpisodeUrl` returned raw `episode.url` (garbage) | "Open in WebView": `getEpisodeUrl` returns full URL `https://anikotv.to/watch/slug/ep-N` |
| Old saved episodes (v16.25 format) | Still decode correctly (backward-compatible `decode()`) |

## What did NOT change

- `versionId` stays at 11 STABLE (saved anime preserved across update)
- `getHosterList` logic unchanged (still decodes EpisodeMeta from `episode.url`)
- `getEpisodeList` logic unchanged (now calls the new `encode()` which produces the new format)
- `enrichEpisodesWithMetadata` unchanged (calls `decode()` which handles both formats)
- Video pipeline (extractors, proxy, WebView fetcher) untouched
- `getAnimeUrl` not overridden (default works via our `animeDetailsRequest` override)
- `hosterListRequest` not overridden (our `getHosterList` never calls it; OkHttp strips fragments)

## Files changed

| File | Change |
|---|---|
| `EpisodeMeta.kt` | `encode()` → `/watch/slug/ep-N#fragment`; `decode()` → backward-compatible (old + new); added `extractUrlPath()` |
| `stubs/AnimeSource.kt` | Added `suspend fun getVideoList(episode: SEpisode): List<Video>` to interface |
| `stubs/AnimeHttpSource.kt` | Added `getVideoList(episode)`, `videoListRequest(episode)`, `videoListParse(response)` (legacy) |
| `Anikoto.kt` | Added `getVideoList(SEpisode)` override; `getEpisodeUrl` override; legacy `videoListParse(response)` stub |
| `build.gradle.kts` | `extVersionCode` 26 → 27 |
| `AnikotoLog.kt` | `EXTENSION_VERSION` → `v16.27` |
| `MEMORY/issues-resolutions/04-episode-url-dns-error-in-forks.md` | NEW — promoted from the user's upload to the mature issues-resolutions folder |

## Status

- ✅ Build successful (v16.27, 156KB APK)
- ✅ All 11 checklist items pass
- ✅ DEX verified: `getVideoList(SEpisode)` + `getEpisodeUrl` overrides + `extractUrlPath` helper + `/watch/` prefix all present
- ✅ Logic verified: 6 Python simulation tests pass (round-trip, backward compat, pipe escaping, sub/dub flags, URL validity, live URL test)
- ✅ Issue resolution promoted to `MEMORY/issues-resolutions/04-episode-url-dns-error-in-forks.md`
- ⏳ User to test on device: install in a fork (AnimeKhor etc.), remove + re-add an anime, click an episode — should play without DNS error. Old saved episodes (v16.25 format) should still work via backward-compat decode.
