# Session 36 — Fix AniList + Anikage Cloudflare Block (WebView for Metadata API)

> Date: 2026-06-24 · Session #: 36 · Duration: ~short · Timezone: America/Los_Angeles

## Goal

Fix v16.20 where episode thumbnails/descriptions/titles were not showing. The user's log
showed `AniList ID for malId=59970 → null` — AniList GraphQL returned null, so Anikage was
never called (it needs the AniList ID), and only Kitsu fallback was used (which had sparse data).

## Root cause

Both **AniList** (`graphql.anilist.co`) and **Anikage.cc** are behind Cloudflare (verified:
`Server: cloudflare`, `CF-Ray` headers present). OkHttp's TLS fingerprint (Conscrypt) is
blocked by the same WAF that blocks cdn.mewstream.buzz. The v16.20 metadata fetcher used its
own simple OkHttpClient (no CloudflareInterceptor, no WebView fallback), so all AniList +
Anikage requests failed silently (postJson returned null).

The user's log confirmed: AniList ID → null → Anikage never called → only Kitsu (24 episodes,
but no thumbnails/descriptions for this anime) → fallback to anime banner for all episodes.

## The fix (v16.21)

### 1. Use the inherited `client` + WebViewFetcher for metadata

Changed `EpisodeMetadataFetcher` to:
- Accept a `WebViewFetcher?` parameter
- Use the inherited `client` (with CloudflareInterceptor + cookieJar) instead of its own simple client
- For Cloudflare-protected hosts (anilist.co, anikage.cc, kitsu.app), use the WebViewFetcher
  directly (Chrome's TLS) — same approach that fixed Vidstream-2

### 2. Added `isCloudflareHost()` check

```kotlin
private fun isCloudflareHost(url: String): Boolean {
    return url.contains("anilist.co") || url.contains("anikage.cc") || url.contains("kitsu.app")
}
```

For these hosts, `fetchString` and `postJson` skip OkHttp entirely and go straight to WebView.

### 3. Added `postJson` method to WebViewFetcher

AniList GraphQL requires POST requests. The existing `fetchText` only does GET. Added a new
`postJson(url, jsonBody)` method that executes a `fetch()` with `method: 'POST'` + `Content-Type:
application/json` + the JSON body via `evaluateJavascript`.

### 4. Added proper error logging

`postJson` and `fetchString` now log HTTP status codes and error messages instead of silently
returning null. This makes future diagnosis much easier.

## Verification

- BUILD SUCCESSFUL, all 11 checklist items pass
- DEX verified: `postJson` method, `isCloudflareHost` (anilist.co, anikage.cc), AniList +
  Anikage URLs all present
- versionId=11 STABLE
- MD5: `9f5b449dd9abaa009957f1f826f881a4`

## What should happen on device with v16.21

1. `getEpisodeList` calls `enrichEpisodesWithMetadata`
2. `metadataFetcher.fetch(malId, coverUrl)`:
   a. `fetchAniListId(malId)` → POST to graphql.anilist.co via WebView (Chrome TLS) → gets AniList ID
   b. `fetchAnikageEpisodes(anilistId)` → GET anikage.cc via WebView → gets thumbnails + descriptions + titles
   c. `fetchKitsuEpisodes(malId)` → GET kitsu.app via WebView → fallback data
   d. `fetchAniListBanner(anilistId)` → POST to graphql.anilist.co via WebView → gets banner URL
3. Merge: Anikage (primary) → Kitsu (fallback) → banner → anime cover
4. Enrich episodes with thumbnails + descriptions + source titles

The WebView is already initialized (from the video pipeline), so there's no additional
initialization cost. The metadata fetches are small text (m3u8-like), so they'll be fast (~200ms each).
