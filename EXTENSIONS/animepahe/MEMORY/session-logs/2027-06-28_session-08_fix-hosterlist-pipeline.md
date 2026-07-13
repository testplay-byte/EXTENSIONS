# Session 08 — AnimePahe: Fix getHosterList (the REAL video playback bug)

> Date: 2027-06-28 · Session #: 08 (animepahe) · Timezone: America/Los_Angeles
> Type: BUGFIX (critical — video playback never worked)
> Follows: session 07 (404 fix + settings reorder — user reported "same error" + shared HTML + cookies + logcat)

## Goal

The user reported that clicking an episode still gave "No available videos". They shared:
1. The play page HTML (showing the real resolution button structure)
2. cf_clearance cookies for animepahe.pw + kwik.cx
3. Their User-Agent
4. A logcat capture (showing ZERO Animepahe logs)

## Root cause analysis

The logcat was the key clue: **ZERO `Animepahe` tag logs appeared when clicking an episode**. This
meant my `videoListParse` was never called.

**The real bug:** the app (Animiru) uses the **ext-lib 16 NEW pipeline**:
1. `getHosterList(episode)` → base class fetches episode.url → calls `hosterListParse(response)`
2. `hosterListParse` returns `List<Hoster>` → the app shows these as "servers"
3. For each Hoster: `getVideoList(hoster)` → resolves the hoster's URL to Videos

My `hosterListParse` returned `emptyList()` → no hosters → "No available videos". My `videoListParse`
was dead code — never called in the new pipeline.

**The fix:** override `getHosterList(episode)` (the suspend version) to do the REAL extraction:
fetch the play page, parse resolution buttons, resolve kwik links, return Hosters with pre-populated
`videoList`. This is the same pattern AniKoto uses.

## What was done

### 1. Override getHosterList(episode)

```kotlin
override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
    // 1. Fetch the play page (episode.url = /play/<session>/<epSession>)
    // 2. Parse div#resolutionMenu > button elements
    // 3. For each: extract data-src (kwik URL) + data-resolution + data-audio
    // 4. Resolve each kwik link via KwikExtractor → create a Video
    // 5. Return listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = videos))
}
```

The `NO_HOSTER_LIST` constant tells the app "don't show a server picker — show the videos directly".

### 2. Use data-resolution + data-audio for quality labels

From the user-provided HTML, each button has:
- `data-src="https://kwik.cx/e/..."` — the kwik embed URL
- `data-resolution="1080"` — the resolution number
- `data-audio="jpn"` — the audio language (jpn=sub, eng=dub)
- `data-fansub="SubsPlease"` — the fansub group

Quality label now: `"1080p (Sub)"` or `"1080p (Dub)"` (from data-resolution + data-audio), instead
of parsing the button text (which includes the fansub name "SubsPlease · 1080p").

### 3. KwikExtractor: WebView fallback for Cloudflare

Updated KwikExtractor to accept a `webViewFetcher` parameter. If OkHttp gets 403 from kwik.cx,
falls back to `webViewFetcher.fetchText(kwikUrl)` (Chrome's TLS). This is the same OkHttp-first +
WebView-fallback pattern used for metadata enrichment.

### 4. Toast notifications

Added user-visible toast messages:
- "No video sources found for this episode" (no resolution buttons)
- "Failed to extract video sources (Kwik extraction failed)" (all kwik resolutions failed)
- "Failed to load videos: <error>" (exception during extraction)

### 5. cf_clearance cookie test (off-device)

Tried using the user-provided cf_clearance cookies (animepahe.pw + kwik.cx) with curl_cffi + the
user's User-Agent. **Got 403** — Cloudflare validates cf_clearance against the **IP address** that
solved the challenge. My server has a different IP → the cookie is rejected. This is a fundamental
Cloudflare security measure — no Python library can bypass it without being on the same IP.

**Conclusion:** off-device testing with cf_clearance cookies is NOT possible from my server. The
on-device extension (with its CloudflareInterceptor using WebView) CAN solve the challenge because
it runs on the user's device/IP. Future off-device testing would require option B (residential proxy
from the user's IP) or option C (sharing HTML, which worked perfectly this session).

### 6. Build

versionCode → 4, versionName → 16.4. APK = 340 KB. Served at HTTP 200.

## What worked

- The user-provided play page HTML was the breakthrough — it showed me the exact button structure
  (data-resolution, data-audio attributes) and confirmed the selectors.
- The logcat (showing zero Animepahe logs) immediately pointed to "my code isn't running" → which
  led to discovering the getHosterList vs videoListParse pipeline issue.
- The `Hoster.NO_HOSTER_LIST` constant + pre-populated `videoList` is the correct ext-lib 16 pattern
  for "just show the videos, no server picker".

## What didn't work / issues

- **cf_clearance cookies failed** (IP mismatch) — documented honestly.
- **First build failed:** `toHosterList()` unresolved reference (it's inside Hoster's companion
  object). Fixed: used `listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = videos))`.
- **Kwik extraction still unverified off-device** — I can't test the kwik.cx fetch or the packed-JS
  regex because of Cloudflare. The on-device test will tell us if it works. If it fails, the logcat
  will show exactly where: "OkHttp HTTP 403" (Cloudflare) or "no eval(function(...)) found" (regex).

## What's next

- **User tests build 4** — the getHosterList override should make video extraction work. Test:
  1. Click an episode → should show "Loading..." → then a video quality picker
  2. `adb logcat -s Animepahe:*` (or filter `Animepahe` in Android Studio) should now show:
     - `getHosterList: START`
     - `getHosterList: found 3 resolution buttons`
     - `getHosterList: resolving 1080p (Sub): https://kwik.cx/e/...`
     - `KwikExtractor: fetching https://kwik.cx/e/...`
     - `KwikExtractor: resolved 1080p (Sub) → https://...m3u8`
     - `getHosterList: DONE — 3/3 videos extracted`
  3. Select a quality → video should play
- **If it fails**, the logcat will show exactly where:
  - `getHosterList: FAILED — <error>` → play page fetch failed (Cloudflare?)
  - `no resolution buttons found` → selector mismatch (unlikely — verified against user HTML)
  - `KwikExtractor: OkHttp HTTP 403` → kwik.cx Cloudflare (WebView fallback should kick in)
  - `could not extract m3u8 URL` → packed JS regex needs updating (need to see the kwik page HTML)
