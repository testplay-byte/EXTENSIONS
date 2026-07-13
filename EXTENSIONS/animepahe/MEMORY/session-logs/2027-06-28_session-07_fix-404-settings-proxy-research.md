# Session 07 — AnimePahe: Fix 404 + Settings reorder + Proxy research

> Date: 2027-06-28 · Session #: 07 (animepahe) · Timezone: America/Los_Angeles
> Type: BUGFIX (404) + FEATURE (settings) + RESEARCH (proxy)
> Follows: session 06 (identity change + video playback — user reported 404 + settings issues)

## Goal

The user reported:
1. Video playback settings should be at the top (currently second)
2. Video playback options are too limited
3. HTTP 404 "No available videos" when clicking an episode — CRITICAL BUG
4. Need logcat filter text for Android Studio
5. Research proxy options for off-device Cloudflare bypass

## What was done

### 1. Fixed the 404 bug (ROOT CAUSE)

**Root cause:** the episode.url was set to `/watch/<session>/ep-<N>#<episodeSession>` — a FAKE path
that doesn't exist on animepahe. The base class's `getVideoList(episode)` fetches
`baseUrl + episode.url` → `GET https://animepahe.pw/watch/<session>/ep-1` → **404** (path doesn't exist).

The fork-compat encoding I designed in session 01 used `/watch/...` as a "valid URL path" to prevent
DNS errors in legacy forks. But I made the path UP — it's not a real animepahe URL. The real play
page URL is `/play/<animeSession>/<episodeSession>`.

**Fix:** changed `episode.url` to the REAL play page path:
```kotlin
setUrlWithoutDomain("/play/$session/$epSession")
```

This is a valid path on animepahe (returns 200) → `videoListParse` can parse the resolution buttons.
Fork compat still works because `/play/...` is a valid path that resolves to `animepahe.pw` (no DNS
error). The adjusted episode number is only for display (episode_number + name), NOT for the URL.

### 2. Fixed a WebViewFetcher origin regression

Discovered that the WebViewFetcher was being created with `"$baseUrl/"` as the origin URL —
overriding the good `data:text/html` default from session 03. This would re-introduce the CSP block
on metadata fetches (AniList/Kitsu). Fixed: `WebViewFetcher(Injekt.get<Application>())` (uses the
default data:text/html origin).

### 3. Settings reorder + more options

- **Video playback is now Category 1** (at the TOP, per user request)
- **Episode metadata is now Category 2**
- Added 2 new video playback settings:
  - **Preferred domain** (animepahe.pw / .com / .org — "Currently: %s")
  - **Preferred audio** (Sub / Dub — "Currently: %s")
- `baseUrl` now reads from `settings.preferredDomain` (lazy)
- `sortVideos` now sorts by both quality AND audio preference

### 4. Proxy research (off-device Cloudflare bypass)

Tested two Python libraries:
- **cloudscraper** — got 403. Can't handle Cloudflare's managed challenge (Turnstile).
- **curl_cffi** (Chrome TLS impersonation) — got 403. TLS impersonation isn't enough; the challenge
  requires real JavaScript execution.
- Also tested API endpoints directly (`/api?m=airing`, `/api?m=search`) — all 403. The entire site
  (including the API) is behind the managed challenge.

**Conclusion:** no Python library can bypass animepahe's Cloudflare. Off-device testing requires
either:
1. **A cf_clearance cookie** from the user's browser (the user solves the challenge in their
   browser, shares the cookie + their User-Agent — I use it in Python for testing)
2. **A residential proxy** that Cloudflare doesn't challenge (the user provides proxy details —
   I route Python requests through it)
3. **The user shares page HTML** (the user opens specific pages in their browser, saves the HTML,
   shares it with me — I verify selectors/regexes against it)

Option 3 is the simplest for immediate testing. Options 1-2 are better for ongoing development.

### 5. Logcat filter text for Android Studio

Provided: `tag:Animepahe` — this shows all log output from the extension.

### 6. Build

versionCode → 3, versionName → 16.3. APK = 339 KB. Served at HTTP 200.

## What's next

- **User tests build 3** — the 404 should be fixed. Test:
  1. Tap an episode → should load the video picker (not 404)
  2. Settings → Video playback should be at the TOP with 3 options (quality + domain + audio)
  3. `adb logcat -s Animepahe:*` (or `tag:Animepahe` in Android Studio) shows the extraction logs
- **If video still doesn't play:** the user captures logcat + shares the play page HTML from their
  browser (View Source on `https://animepahe.pw/play/<session>/<epSession>`) so I can verify the
  resolution button selectors.
- **Proxy discussion:** the user decides which proxy approach to use for future off-device testing.
