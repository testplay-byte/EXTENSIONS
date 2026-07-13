# Session 03 — AnimePahe: Fix Episode Metadata (OkHttp-first + title format + docs)

> Date: 2027-06-28 · Session #: 03 (animepahe) · Timezone: America/Los_Angeles
> Type: BUGFIX (episode metadata enrichment not working properly)
> Follows: session 02 (metadata enrichment + settings + logo)

## Goal

Fix the episode metadata enrichment. The user reported:
1. No episode cover images (thumbnails)
2. No episode descriptions
3. Episode title format wrong ("Episode N - title" instead of "EP N - title" like AniKoto)

The user also requested updating the documentation so future extensions don't repeat these mistakes.

## Root cause analysis

### Why thumbnails + descriptions were missing (but titles worked)

I tested all 4 metadata APIs with curl to diagnose:

| API | curl result | Verdict |
|---|---|---|
| **AniList GraphQL** | ✅ 200 + full data + CORS headers (`access-control-allow-origin: *`) | OkHttp works directly — NO WebView needed |
| **Jikan (MAL)** | ✅ 200 | OkHttp works (already confirmed — titles were showing) |
| **Anikage.cc** | ✅ 200 with browser UA (403 with curl's default UA) | OkHttp works with the browser UA that apiHeaders already has |
| **Kitsu** | ⚠️ empty/unreliable response | Unreliable — may be down or blocking |

**Root cause:** My `EpisodeMetadataFetcher` used **WebView-first** for AniList/Kitsu (copied from AniKoto's pattern). But AniKoto's WebView origin is `megaplay.buzz` (a light-Cloudflare CDN that loads easily). AnimePahe's WebView origin was `animepahe.pw` — which shows a **Cloudflare managed challenge page** (Turnstile). That challenge page has a strict **CSP: `default-src 'none'`** that **blocks ALL cross-origin `fetch()` calls**. So even though AniList sends CORS headers, the fetch never fires from the challenge page context.

Result: only Jikan (OkHttp direct, no WebView) worked → titles only, no thumbnails, no descriptions.

### Why the title format was wrong

I used `ep.name = "Episode $epNum - $sourceTitle"` instead of AniKoto's convention `ep.name = "EP $epNum - $sourceTitle"`. Simple copy error.

## What was done

### 1. Fixed EpisodeMetadataFetcher: OkHttp-first, WebView-fallback

Changed `fetchString` and `postJson` to:
1. **Try OkHttp first** (using the inherited `client` with CloudflareInterceptor + browser UA)
2. **Fall back to WebView** only if OkHttp returns non-200 or throws

This is the OPPOSITE of AniKoto's WebView-first approach. AniKoto's approach works for light-Cloudflare sites but FAILS for hard-Cloudflare sites (like animepahe). OkHttp-first works for ALL sites.

Removed the `isCloudflareHost` check entirely — OkHttp is tried first for ALL hosts unconditionally.

### 2. Fixed WebViewFetcher origin: data:text/html (no CSP)

Changed the origin URL from `https://animepahe.pw/` to `data:text/html,<html><body></body></html>`.

A blank data: URL page has **no CSP restrictions** — cross-origin fetch() to any CORS-enabled API works freely. AniList sends `Access-Control-Allow-Origin: *`, so the null-origin fetch works.

This is critical: loading the extension's own site as the WebView origin FAILS on hard-Cloudflare sites because the challenge page's CSP blocks everything.

### 3. Fixed title format: "EP N - title"

Changed `ep.name = "Episode $epNum - $sourceTitle"` to `ep.name = "EP $epNum - $sourceTitle"` to match AniKoto's convention exactly.

### 4. Updated documentation

Created a new guide: `EXTENSIONS/HOW_TO_BUILD_EXTENSION/episode-metadata-enrichment.md` — the complete implementation guide for episode metadata enrichment. Covers:
- The 4-source merge strategy (Jikan + AniList + Anikage + Kitsu)
- ★ The OkHttp-first, WebView-fallback pattern (with the CSP issue explanation)
- The data:URL-origin WebView approach (why NOT to use the extension's site as origin)
- The title format convention ("EP N - title")
- The settings structure (3 toggles, all default ON)
- How to get the MAL ID (external links, data-mal attribute, title-search fallback)
- Common issues + fixes (thumbnails missing, CSP block, wrong title format, R8 serialization)
- Verification checklist

Updated `reference-anikoto-solutions.md` with 3 new entries:
- "thumbnails + descriptions missing (only titles work)" — the CSP/WebView-first issue + fix
- "title format wrong" — the "EP" vs "Episode" convention
- "no metadata at all" — the MAL ID extraction issue

Updated the main `README.md` to link the new guide in the reference resources table + file index.

### 5. Build + verification

- Build: SUCCESSFUL, warning-free. APK = 395 KB (same size — no new classes, just logic changes).
- APK copied to `EXTENSIONS/animepahe/APK/`.
- Dev server serves the fresh APK (395 KB, HTTP 200).

## What worked

- The curl tests immediately revealed the root cause — AniList works with plain HTTP (no WebView needed). The WebView-first approach was the problem, not the APIs.
- The OkHttp-first pattern is simpler AND more reliable — fewer moving parts, no CSP issues.
- Documenting the CSP issue in the guide ensures future extensions won't repeat this mistake.

## What didn't work / issues

- **MAL ID extraction still unverified.** The enrichment depends on finding a MAL external link on the detail page. With the OkHttp-first fix, if the MAL ID IS found, all 4 sources should now work (AniList + Anikage via OkHttp, Kitsu via OkHttp). But if the detail page DOESN'T have a MAL link, enrichment still skips. The user needs to test on-device + check `adb logcat -s Animepahe:*` for "extractMalId: found MAL ID" vs "no MAL link found".
- **Kitsu unreliable** — curl returned empty responses. This is a Kitsu-side issue (possibly rate limiting or downtime). The fetcher handles it gracefully (returns empty map → other sources still work).

## What's next

- **User tests the updated debug APK** (395 KB). With the OkHttp-first fix:
  - If MAL ID is found → thumbnails (AniList + Anikage), titles (Jikan), descriptions (Anikage) should all show
  - Episode titles should be "EP N - title" (not "Episode N - title")
- **If metadata still doesn't load:** capture `adb logcat -s Animepahe:*` and look for:
  - "extractMalId: no MAL link found" → need alternative MAL ID source (title-search via Jikan)
  - "EpisodeMetadataFetcher: OkHttp HTTP 403 for anilist.co" → TLS fingerprint blocked (need WebView)
  - "enriched 0/N episodes" → metadata fetched but no matching episode numbers
- **Step 4 (video playback)** after metadata is confirmed working.

## Open questions for the user

1. Does the episode list now show thumbnails + descriptions (not just titles)?
2. Are the episode titles now "EP N - title" format?
3. If still no thumbnails: check `adb logcat -s Animepahe:*` — what does "extractMalId" say?
