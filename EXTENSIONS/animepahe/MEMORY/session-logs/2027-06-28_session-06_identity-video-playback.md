# Session 06 — AnimePahe: Identity change + Video playback (Kwik HLS) + Toast/Logging infra

> Date: 2027-06-28 · Session #: 06 (animepahe) · Timezone: America/Los_Angeles
> Type: FEATURE (video playback) + IDENTITY (name/package) + INFRASTRUCTURE (toast/logging)
> Follows: session 05 (popular/latest swap + multi-season renumbering — confirmed working by user)

## Goal

Four things requested by the user:
1. Rename to "AnimePahe 180" (name ends with "180")
2. Change applicationId to end with "180" (distinguish from other publishers)
3. Add proper logging, error handling, and toast notifications moving forward
4. Implement Step 4: video playback (like AniKoto, with proper optimization)

Plus: bump the build version on the webpage (was stuck at 1).

## What was done

### 1. Identity changes

- **build.gradle.kts**: extName → "AnimePahe 180", applicationIdSuffix → "en.animepahe180",
  extClass → full path "eu.kanade.tachiyomi.animeextension.en.animepahe.AnimePahe" (because
  applicationId ≠ source package now — same pattern as AniKoto). versionCode → 2, versionName → 16.2.
  versionId stays 1 (STABLE). archivesName → aniyomi-en.animepahe180-v16.2.
- **AnimePahe.kt**: name → "AnimePahe 180".
- **Verification**: package = eu.kanade.tachiyomi.animeextension.en.animepahe180 ✓, name = "AnimePahe 180" ✓,
  extClass = full path ✓, versionId = 1 ✓.
- **⚠️ Note:** the source ID changed from MD5("AnimePahe en/1") to MD5("animepahe 180/en/1"). Users
  on the old ...en.animepahe package must UNINSTALL before installing this (different package =
  different app — no direct update).

### 2. Toast + logging infrastructure

- Added `showToast(message)` helper in AnimePahe.kt — runs `Toast.makeText` on the main thread via
  `withContext(Dispatchers.Main)`, never throws. Pattern copied from AniKoto.
- All video extraction code uses `AnimepaheLog.i/d/w/e` at every step:
  - `i`: START, DONE (with count)
  - `d`: per-button progress (which quality is being resolved)
  - `w`: non-fatal issues (button has no data-src, kwik extraction returned null)
  - `e`: fatal errors (with exception)
- `videoListParse` wrapped in try-catch, returns `emptyList()` on error (never throws).

### 3. Step 4: Video playback (Kwik HLS)

Created `extractor/KwikExtractor.kt` + implemented `videoListParse` + `sortVideos` in AnimePahe.kt.

**The flow:**
1. The play page (`/play/<animeSession>/<episodeSession>`) contains `div#resolutionMenu > button`
   elements with `data-src` = kwik embed links + text = quality label.
2. Each kwik embed link (kwik.cx/e/<id>) contains a packed JS script (`eval(function(p,a,c,k,e,d){...})`).
3. The packed JS, when unpacked, reveals the m3u8 HLS stream URL.
4. KwikExtractor extracts the m3u8 URL via regex (the URL is visible in the packer's token list).
5. Returns a `Video(videoUrl = m3u8, videoTitle = "1080p (HLS)", headers = {Referer: kwik.cx})`.

**Fork compat:** the base class's `getVideoList(episode)` fetches `baseUrl + episode.url` (the path
part of the fork-compat encoding `/watch/<session>/ep-N#<episodeSession>`) → calls `videoListParse`.
So fork compat works automatically — no need to override `getVideoList(SEpisode)`.

**Settings:** added "Preferred quality" (1080p/720p/360p, default 1080p, "Currently: %s") to the
Video playback category. `sortVideos` sorts by the preferred quality.

### 4. Webpage build number bump

Updated the animepahe card on the webpage: name → "AnimePahe 180", version → "v16.2", build → 2,
tagline mentions video playback. Build number is now 2 (was stuck at 1 for sessions 01-05).

### 5. Documentation

Created `FEATURES/video-playback-kwik-hls.md` — complete implementation guide covering:
- The Kwik extraction flow (play page → kwik embed → packed JS → m3u8)
- KwikExtractor code (with the regex strategy)
- videoListParse implementation
- sortVideos (quality preference)
- Video playback settings
- Fork compatibility (EpisodeMeta encoding)
- ★ Error handling + logging conventions (never throw, log every step, toast for user-visible errors)
- ★ Video constructor (named args, initialized=false)
- Common issues (no buttons, null extraction, Cloudflare 403, HLS seeking)
- Reference implementations

Updated FEATURES/README.md index + "which extensions have which features" table (AnimePahe video
playback now ✅). Updated HOW_TO_BUILD_EXTENSION/README.md §2b feature table.

## What worked

- The Kwik HLS path is simpler than the stream (mp4) path — no Cloudflare bypass token decryption
  needed for playback. The m3u8 URL is extractable directly from the packed JS via regex.
- The base class's `getVideoList(episode)` handles the play-page fetch automatically — no need to
  override it. The fork-compat encoding makes the episode.url a valid path.
- Build is clean (one cosmetic deprecation warning, same as before).
- APK is 338 KB (smaller than session 05's 453 KB — the renumbering refactor + cleaner code).

## What didn't work / issues

- **First build failed:** `sort` overrides nothing — the ext-lib 16 method is `sortVideos()`, not
  `sort()`. Fixed.
- **Kwik extraction is UNVERIFIED off-device.** Cloudflare blocks my analysis browser, so I can't
  test the kwik.cx fetch or the packed-JS regex against the live site. The regex strategy:
  1. Try to find the m3u8 URL directly in the packed string (often visible as `https://...m3u8`)
  2. Fallback: extract the `const source='...'` value
  If neither works, the user will see "could not extract source URL" in logcat, and I'll need to
  inspect the actual kwik page HTML to update the regex.
- **Cloudflare on kwik.cx:** the inherited `client` has a CloudflareInterceptor that should handle
  kwik.cx. If it doesn't (403), the fallback is `webViewFetcher.fetchText(kwikUrl)` (Chrome's TLS).
  This fallback isn't wired into KwikExtractor yet — I'll add it if the user reports 403 errors.

## What's next

- **User tests the updated APK** (338 KB, v16.2, build 2). Test:
  1. Install (⚠️ must uninstall the old ...en.animepahe package first — different package name)
  2. Open an anime → episode list → tap an episode
  3. Should see a video picker with quality options (1080p/720p/360p HLS)
  4. Select a quality → video should play
  5. If it fails: `adb logcat -s Animepahe:*` shows exactly where
- **If video doesn't play**, the most likely culprits:
  - "no resolution buttons found" → play page HTML selectors changed (need to inspect)
  - "could not extract source URL" → kwik's packed JS format changed (need to update regex)
  - "OkHttp HTTP 403 for kwik.cx" → Cloudflare blocking (need to wire WebView fallback)
- **Settings:** check the "Video playback" category now shows "Preferred quality" (was a placeholder)

## Open questions

1. Does video play on-device? (The Kwik extraction is unverified off-device — needs real testing.)
2. If it fails, what does `adb logcat -s Animepahe:*` show?
3. Is the "Preferred quality" setting showing correctly in the settings screen?
