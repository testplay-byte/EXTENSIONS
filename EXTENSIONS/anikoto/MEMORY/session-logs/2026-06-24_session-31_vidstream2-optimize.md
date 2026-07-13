# Session 31 — Vidstream-2 Improvements: Optimized WebView + DNS Fallback Fix

> Date: 2026-06-24 · Session #: 31 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

The v16.17 WebView fallback was partially working (m3u8 fetches succeeded, 720p segments
eventually loaded) but had two critical issues:
1. **1080p segments ALL FAILED** with `UnknownHostException` for `g5vh.voltara.click` and
   `f4qh.zaptrix.buzz` — the segment CDN hosts. The fallback only triggered on "403", not DNS errors.
2. **720p segments were too slow** (33 seconds per 1.1MB segment) — the `String.fromCharCode` +
   `btoa` JS encoding was extremely slow for large binary data.

## Root cause analysis (from user's log: anikoto-2026-06-24_19-17-48.log)

### What worked in v16.17
- master m3u8 fetch via WebView: ✅ (~200ms, 2 variants detected: 720p, 1080p)
- 720p variant m3u8 fetch via WebView: ✅ (69 segments)
- 1080p variant m3u8 fetch via WebView: ✅ (69 segments)
- 720p segment 0: ✅ (1.1MB, but took 33 seconds!)
- 720p segment 1: ✅ (879KB, also slow)

### What failed in v16.17
- **1080p segments 0-5**: ALL FAILED with `UnknownHostException: Unable to resolve host "g5vh.voltara.click"`
  and `"f4qh.zaptrix.buzz"`. These are the actual segment CDN hosts — different from cdn.mewstream.buzz.
  The variant m3u8 (on cdn.mewstream.buzz) contains segment URLs pointing to these CDN edge hosts.
  OkHttp's DNS can't resolve them. The v16.17 fallback only checked for "403" in the error message,
  so DNS errors didn't trigger the WebView fallback.
- **Prefetch**: failed with "Software caused connection abort" — concurrent WebView evaluateJavascript
  calls interfere with each other.
- **Performance**: 33 seconds per segment is unusable for real-time playback (player needs 1 segment
  every ~10 seconds).

### The segment CDN host discovery

The variant m3u8 on cdn.mewstream.buzz contains segment URLs on DIFFERENT CDN edge hosts:
- `g5vh.voltara.click` (1080p segments)
- `f4qh.zaptrix.buzz` (other segments)

These hosts are either:
- Not resolvable by Android's Java DNS (but Chrome's DNS can resolve them — possibly DoH)
- OR the WAF on these hosts also blocks OkHttp's TLS

Either way, OkHttp can't fetch segments from these hosts, and the v16.17 fallback (403-only)
didn't cover DNS errors.

## The fix (v16.18) — three improvements

### Fix 1: Broaden WebView fallback to ALL errors for WAF-blocked hosts

**LocalProxyServer.fetchSegment**: instead of only falling back on "403", now falls back on
ANY exception for known WAF-blocked hosts. Added `isWafBlockedHost(url)` that checks for:
- `mewstream.buzz` (cdn.mewstream.buzz — m3u8 host)
- `voltara.click` (g5vh.voltara.click — 1080p segment host)
- `zaptrix.buzz` (f4qh.zaptrix.buzz — other segment host)

This fixes the UnknownHostException — segments on voltara.click and zaptrix.buzz now go
through the WebView (Chrome's DNS + TLS), which can resolve and fetch them.

**AnikotoExtractors.fetchString**: same broadening for m3u8 fetches.

### Fix 2: Skip OkHttp entirely for WAF-blocked hosts

In v16.17, the flow was: OkHttp tries → gets 403 (1-2s wasted) → falls back to WebView.
For WAF-blocked hosts, OkHttp ALWAYS fails. So now we skip OkHttp and go straight to WebView:
- `fetchSegment`: if `isWafBlockedHost(url)`, call `webViewFetcher.fetchBytes(url)` directly
- `fetchString`: if `isWafBlockedHost(url)`, call `webViewFetcher.fetchText(url)` directly

This saves 1-2 seconds per segment (no wasted OkHttp 403 attempt).

### Fix 3: Optimize WebView binary fetch performance

**WebViewFetcher.buildFetchBytesJs**: replaced the slow `String.fromCharCode` + `btoa` loop
with `FileReader.readAsDataURL` (native browser API, much faster):

**Old JS (v16.17, ~30s per 1MB segment):**
```javascript
var binary = '';
for (var j = 0; j < chunk.length; j += 32768) {
    binary += String.fromCharCode.apply(null, chunk.subarray(j, Math.min(j + 32768, chunk.length)));
}
Android.onChunk(id, i, numChunks, btoa(binary));
```

**New JS (v16.18, expected ~5-10s per 1MB segment):**
```javascript
var base64 = await new Promise(function(resolve) {
    var reader = new FileReader();
    reader.onload = function() { resolve(reader.result.split(',')[1]); };
    reader.readAsDataURL(new Blob([chunk]));
});
Android.onChunk(id, i, numChunks, base64);
```

`FileReader.readAsDataURL` is a native browser operation that converts bytes to base64
efficiently. The old approach used `String.fromCharCode` (slow JS string building) + `btoa`
(additional pass). The new approach does it in one native pass.

Also increased chunk size from 400KB to 700KB (fewer IPC calls, each under the ~1MB limit).

### Fix 4: Serialize concurrent WebView requests

Added `synchronized(fetchLock)` around all fetch operations. The v16.17 "Software caused
connection abort" prefetch error was likely caused by concurrent `evaluateJavascript` calls
interfering with each other. Now all fetches are serialized — one at a time through the WebView.

This means prefetch and segment-on-demand fetches won't conflict. The tradeoff: no parallelism
(all fetches are sequential). But since each fetch uses Chrome's network stack (which handles
HTTP/2 multiplexing internally), this shouldn't significantly impact throughput.

### Fix 5: Better logging with timing

Added timing logs to `fetchText` and `fetchBytes`:
```
WebViewFetcher: fetchText id=1 DONE in 234ms
WebViewFetcher: fetchBytes id=5 DONE in 8234ms size=1122736
```

This lets us see exactly how long each fetch takes, making future diagnosis easier.

## Verification

### Build verification (v16.18)
- BUILD SUCCESSFUL in 28s
- All 11 checklist items pass: Stub! count=0, extClass=.Anikoto, versionId=11 STABLE
- DEX verified: FileReader/readAsDataURL present, isWafBlockedHost (voltara.click, zaptrix.buzz,
  mewstream.buzz) present, 700KB chunk size present
- MD5: `96eb4ba858eead9c9d7c3706bc54a7e8`

### Regression check
- **VidCloud-1** (Klutzy EP12): NO REGRESSION ✅ — its CDN (fxpy7.watching.onl) is NOT a WAF
  host, so OkHttp is used directly (unchanged behavior, HTTP 200)
- VidPlay-1, Kiwi: not WAF hosts, unaffected

## What should happen on the user's device with v16.18

When playing Vidstream-2 (Smoking EP5):
1. getSources API (OkHttp) → m3u8 URL at cdn.mewstream.buzz ✅ (unchanged)
2. Master m3u8 fetch → `isWafBlockedHost` = true → WebView fetchText → ~200ms ✅
3. Variant m3u8 fetch → same → ~200ms ✅
4. Proxy serves variant playlist to player
5. Player requests segment 0 (on g5vh.voltara.click or cdn.mewstream.buzz)
6. `isWafBlockedHost` = true → **skip OkHttp, go straight to WebView**
7. WebView fetchBytes → fetch + FileReader.readAsDataURL + chunked IPC → ~5-10s (estimated)
8. Segment cached + served to player
9. Subsequent segments: same flow, but WebView already initialized → faster

**Key improvements over v16.17:**
- 1080p segments now work (DNS error → WebView fallback, not just 403)
- Segments should be ~3-5x faster (FileReader vs String.fromCharCode)
- No more "connection abort" from concurrent fetches (serialized)
- No wasted time on OkHttp 403 attempts for WAF hosts (skip directly to WebView)

## Honest notes

- **Performance is still the main concern.** Even with the FileReader optimization, the WebView
  fetch + IPC pipeline is inherently slower than OkHttp. The first segment may take ~5-10 seconds.
  With the 200-entry cache and serialized prefetch, the player should be able to build a buffer
  during playback. But the initial load will be slower than VidCloud-1.

- **The segment CDN hosts (voltara.click, zaptrix.buzz) are hardcoded.** If megaplay.buzz changes
  to new CDN hosts, the `isWafBlockedHost` check won't match. The fallback still covers ANY error
  for these hosts, but new hosts would need to be added. A more robust approach would be to fall
  back to WebView for ANY host that OkHttp can't reach, but that could break VidCloud-1/VidPlay-1
  if they have transient errors. The current approach (known WAF hosts only) is safer.

- **Cannot fully verify from sandbox.** The sandbox IP is blocked from cdn.mewstream.buzz and
  can't test the segment CDN hosts. The fix is based on the v16.17 log analysis + the optimization
  principles. The user needs to test on their device.

- **If segments are still too slow**, the next step would be to investigate Cronet (Chrome's
  network stack as a library) or a custom OkHttp SSLSocketFactory that matches Chrome's JA3.
  Both are complex but would give OkHttp-level performance with Chrome's TLS.

## Files changed

- `video/WebViewFetcher.kt` — optimized JS (FileReader.readAsDataURL), larger chunks (700KB),
  serialized fetches (synchronized fetchLock), timing logs, 60s timeout for bytes
- `video/AnikotoExtractors.kt` — added isWafBlockedHost(), skip OkHttp for WAF hosts, fallback on ANY error
- `video/LocalProxyServer.kt` — added isWafBlockedHost(), skip OkHttp for WAF hosts, fallback on ANY error
- `build.gradle.kts` — versionCode 17→18 (versionId stays 11 STABLE)
- `AnikotoLog.kt` — EXTENSION_VERSION updated to v16.18
