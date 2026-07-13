# Session 07 — MKissa: Fix NEED_CAPTCHA + Preferred Server Setting

> Date: 2027-06-29 · Extension: MKissa 180 (mkissa.to) · Session: 07 · Status: ✅ NEED_CAPTCHA fix + preferred server

## Goal

Fix the `NEED_CAPTCHA` error that prevented video playback. The user's logcat showed:
```
fetchStreamData: HTTP 200, body length=165
fetchStreamData: body first 300 chars: {"errors":[{"message":"NEED_CAPTCHA",...}],"data":{"episode":null}}
```

The stream API returned a GraphQL error `NEED_CAPTCHA` instead of the encrypted `tobeparsed` payload. Also add a "Preferred server" setting (picked first, others as fallback).

## Root cause analysis

The `api.allanime.day` stream API returns `NEED_CAPTCHA` when the client hasn't solved the Cloudflare Turnstile on the `mkissa.to` watch page. The server tracks captcha clearance per IP/session.

**Evidence:**
1. Curl from my server (which never loaded the watch page) → works fine (returns `tobeparsed`)
2. On-device (which also never loaded the watch page) → returns `NEED_CAPTCHA`
3. The watch page returns 403 with `cf-mitigated: challenge` + `server: cloudflare` — a Cloudflare managed challenge
4. The Aniyomi app's `CloudflareInterceptor` detects 403 + `Server: cloudflare` and opens a WebView to solve the challenge → stores the `cf_clearance` cookie → the server records the clearance

**The fix:** Before calling the stream API, load the watch page URL via the inherited `client`. The CloudflareInterceptor will detect the 403, solve the Turnstile on-device via WebView, and the server will record the captcha clearance. Then retry the stream API — it will succeed.

## What was done

### 1. NEED_CAPTCHA fix (MKissaExtractor.kt)

Added a `solveCloudflare(watchPageUrl)` method that:
- Makes a GET request to the watch page URL (`https://mkissa.to/anime/<id>/p-<N>-sub`)
- Uses `CacheControl.FORCE_NETWORK` to bypass any stale cache
- The CloudflareInterceptor detects the 403 + `Server: cloudflare` → opens a WebView → solves the Turnstile
- The server records the captcha clearance for this IP/session

Modified `extractVideos()` to:
1. First attempt the stream API call
2. If the response contains `NEED_CAPTCHA`, call `solveCloudflare(watchPageUrl)` to trigger the CF interceptor
3. Retry the stream API call — it should now succeed

### 2. Preferred server setting (MKissaSettings.kt + MKissa.kt)

Added a "Preferred server" dropdown to the Video playback settings category:
- Options: Site Default, Fm-Hls, Uni, Mp4, Ok, Luf-Mp4
- Default: Site Default (uses the API's priority ordering)
- When a specific server is selected, videos from that server are sorted first (the player auto-selects the first video)

Updated `extractVideos()` to accept a `preferredServer` parameter and sort the result list:
- If `preferredServer` is set: videos from that server first, then by quality (1080p > 720p)
- If `preferredServer` is empty (Site Default): sort by quality only

Updated `getHosterList()` in MKissa.kt to pass `settings.preferredServer` + `baseUrl` to the extractor.

### 3. Improved logging

- `solveCloudflare: loading watch page: <url>` — logs the watch page URL being loaded
- `solveCloudflare: watch page HTTP <code>` — logs the response code (403 = challenge triggered, 200 = already cleared)
- `MKissaExtractor: NEED_CAPTCHA detected — loading watch page to solve Cloudflare Turnstile` — clear log of the fix being applied
- `MKissaExtractor: retrying stream API after Cloudflare bypass` — logs the retry
- `MKissaExtractor: total <N> videos from all servers (preferred=<server>)` — logs the final count + preferred server

### Build v16.7
- Debug APK: 245KB
- Build checklist ALL PASS: package=...en.mkissa180 v16.7, Stub! count=0
- Live on the webpage at `/api/apk?ext=mkissa&type=debug` (HTTP 200, 250603 bytes)

## Settings (3 categories, 7 preferences)
1. **Video playback**: Preferred quality, Preferred audio (Sub/Dub), Title style (Romaji/English/Native), **Preferred server** (NEW)
2. **Servers**: Enable/Disable servers (Fm-Hls, Uni, Mp4, Ok, Luf-Mp4)
3. **Episode metadata**: Load thumbnails, Load titles, Load descriptions

## What needs the user's testing
1. Download v16.7
2. Uninstall v16.6 in Aniyomi
3. Install v16.7, open an anime episode
4. The first play may take 5-10 seconds longer (the CloudflareInterceptor solves the Turnstile via WebView)
5. Check logcat (`tag:MKissa`) — should see: "NEED_CAPTCHA detected" → "loading watch page" → "retrying stream API" → "found N sources" → "total N videos"
6. Try changing the "Preferred server" setting and verify the selected server's videos appear first
