# Session 08 — MKissa: Fix Okru + Filemoon 405 + Uni Documentation

> Date: 2027-06-29 · Extension: MKissa 180 (mkissa.to) · Session: 08 · Status: ✅ Okru fixed, Filemoon graceful, Uni documented

## Goal

The user tested v16.7 and video playback worked (1 video from Mp4Upload played correctly). But only 1 of 4 servers produced videos. The logcat showed:
- **Uni**: 0 videos (not implemented)
- **Ok**: 0 videos (extractor failed silently)
- **Mp4**: 1 video ✅ (working)
- **Fm-Hls**: HTTP 405 error (Filemoon playback API rejected the request)

## Root cause analysis + fixes

### 1. Ok.ru extractor (FIXED)

**Root cause:** The `data-options` attribute on ok.ru uses HTML entities (`&quot;`). After decoding, the JSON has escaped quotes (`\"`). My original regex `substringAfter("ondemandHls\":\"")` was looking for the wrong escape pattern — the actual data has `ondemandHls\":\"` (backslash-escaped).

**Fix:** Rewrote the Okru extractor with:
- Proper HTML entity decoding (`&quot;` → `"`, `&amp;` → `&`)
- Two regex patterns to handle both `ondemandHls\":\"` and `ondemandHls":"` formats
- A fallback direct-MP4 parser that finds all `{name:"quality",url:"mp4url"}` entries
- Quality label mapping (full→1080p, hd→720p, sd→480p, etc.)
- Replaced `awaitSuccess()` with `execute()` so 403/405 responses don't crash
- Extensive logging at each step

**Verified via curl:** The ok.ru embed page HAS the `data-options` attribute with `ondemandHls` containing a valid HLS URL (`https://vd470.okcdn.ru/expires/.../type/2/mid/.../id/...`). The fix should work on-device.

### 2. Filemoon (Fm-Hls) extractor (GRACEFUL HANDLING)

**Root cause:** The Filemoon playback API (`/api/videos/<id>/embed/playback`) returns HTTP 405 "method not allowed" from my server's IP. This is likely because:
- The API requires on-device Cloudflare clearance (cf_clearance cookie from solving the Turnstile)
- Or the API has rate limiting / IP-based restrictions

**Fix:** Replaced `awaitSuccess()` with `execute()` so the 405 doesn't throw an exception. Added clear logging:
- `extractFilemoon: playback API returned HTTP 405 — body: {"error":"method not allowed"}`
- `extractFilemoon: the Filemoon playback API may require on-device Cloudflare clearance. Skipping Fm-Hls.`

The extractor no longer crashes — it logs a clear warning and returns emptyList. On-device, the inherited client's CloudflareInterceptor may solve the Turnstile when loading the embed page, which could make the playback API work. This needs on-device testing.

### 3. Uni server (DOCUMENTED — needs WebViewResolver)

**Root cause:** The Uni player at `allanime.uns.bio/#<hash>` is a custom JS SPA. It fetches video data from an internal API that we can't easily replicate off-device. The player JS mentions `vimeocdn.com` and `fero...` — it's a custom player, not a standard host.

**Status:** Documented with clear logging. Returns emptyList with:
- `extractUni: Uni server uses a custom JS player (allanime.uns.bio/#n5fhqw) — needs WebViewResolver (not yet implemented)`

**Future implementation:** needs a WebViewResolver pattern — load the Uni player page in a WebView, intercept the network request that fetches the video URL, return it as a Video. This is a significant feature (similar to AniKoto's WebViewFetcher for WAF-blocked CDNs).

### 4. Luf-Mp4 (internal) — not present for this episode

The API returned only 4 sources (Uni, Ok, Mp4, Fm-Hls) for this episode. Luf-Mp4 is an internal hoster that doesn't appear for all episodes — it's priority-based and may only appear for certain anime. No fix needed — it's expected behavior.

## Server status summary (after v16.8)

| Server | Status | Notes |
|---|---|---|
| **Mp4** | ✅ Working | JsUnpacker — 1 video per episode |
| **Ok** | ✅ Fixed (v16.8) | data-options HLS extraction — should work on-device |
| **Fm-Hls** | ⚠️ Needs on-device CF | Playback API returns 405 off-device; may work on-device with CF interceptor |
| **Uni** | ⚠️ Needs WebViewResolver | Custom JS player — future implementation |
| **Luf-Mp4** | ✅ Implemented | Internal clock.json — only appears for some episodes |

## Build v16.8
- Debug APK: 246KB
- Build checklist ALL PASS: Stub! count=0
- Live on the webpage at `/api/apk?ext=mkissa&type=debug` (HTTP 200, 251328 bytes)

## What needs on-device testing
1. **Ok.ru** — should now work (HLS URL extraction fixed). Test with Witch Hat Atelier ep 6.
2. **Fm-Hls** — may work on-device if the CF interceptor solves the Turnstile when loading the embed page.
3. **Mp4** — already working.
4. **Uni** — still not implemented (needs WebViewResolver).
5. **Luf-Mp4** — may appear for other anime.

The user should test v16.8 and check logcat (`tag:MKissa`) for the extraction results of each server.
