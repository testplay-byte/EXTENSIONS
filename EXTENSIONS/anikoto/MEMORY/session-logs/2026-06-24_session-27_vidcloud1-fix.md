# Session 27 — VidCloud-1 Fix: Universal getSources + Per-Stream Referer

> Date: 2026-06-24 · Session #: 27 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Implement VidCloud-1 properly (user's immediate priority), addressing 3 specific items from the user's test report:
1. **Part A**: Add `vidcloud-1` to the Preferred Server settings (was missing).
2. **Fix 1**: Switch to `getSources` universally (the user verified it works on all 3 hosts).
3. **Fix 2**: Dynamic per-stream Referer headers (the proxy was hardcoded to `Referer: https://vidtube.site/`, causing 403 on VidCloud-1 segments).

Also noted for later: Issue A (Vidstream-2 on Smoking EP5 → "Hosters list empty") and Issue B (VidCloud-2/auto-variant on Klutzy EP12 → 3 resolutions detected but playback failed).

## What was done

### A. Verified the user's technical claims (rule §1: verify before trusting)

Before implementing, I live-tested every claim:

**Fix 1 claim: "getSources works universally"** — VERIFIED ✅
- Tested `getSources?id=X&type=Y` (WITH the type param) on all 3 hosts using Wistoria EP5.
- All 3 hosts return correct audio-specific m3u8 hashes:
  - VidPlay-1 (vidtube.site): sub=1a1e84e3, hsub=c2b9eb43, dub=ec7821b9
  - HD-1/Vidstream-2 (megaplay.buzz): sub=31fcc9a2, hsub=e452e498, dub=e833e7bf
  - VidCloud-1 (vidwish.live): sub=31fcc9a2, dub=e833e7bf
- **Important nuance:** my session 26 concern (that vidtube.site needed `getSourcesNew` because `getSources` without type returned SUB) was based on testing WITHOUT the type param. WITH the type param, `getSources` respects the audio on all hosts. The user was right.
- Also verified: the getSources API doesn't care about the Referer header (works with any/no Referer). So the extension's `vidtubeApiHeaders()` (hardcoded `Referer: https://vidtube.site/`) is fine for the API call.

**Fix 2 claim: "hardcoded vidtube.site Referer causes 403 on VidCloud-1 segments"** — VERIFIED ✅
- Klutzy EP12 VidCloud-1: m3u8 is on `fxpy7.watching.onl`, but segments are on `x91rz.cloudvideo.lat` (a different host).
- Segment fetch with `Referer: https://vidtube.site/` → **HTTP 403** (Cloudflare blocked — the current bug)
- Segment fetch with `Referer: https://vidwish.live/` → **HTTP 200**, 3.3MB ✅
- Segment fetch with no Referer → HTTP 403
- VidCloud-1 segments are **NOT PNG-wrapped** (plain TS, starts with 0x47) — the `stripPngHeader` handles this gracefully (no-op).

### B. Fix 1: Unified getSources endpoint

In `AnikotoExtractors.resolveVidTube`, replaced the host-based endpoint split (session 26) with a single unified call:
```kotlin
val sourcesUrl = "https://$host/stream/getSources?id=$dataId&type=$audioType"
```
This works on all 3 hosts (vidtube.site, megaplay.buzz, vidwish.live) and respects the audio type. The old `getSourcesNew` is no longer used anywhere in the code. Simpler + correct.

### C. Fix 2: Per-stream Referer in AudioStream + LocalProxyServer

**The problem:** `Anikoto.getHosterList()` created ONE `segHeaders` with hardcoded `Referer: https://vidtube.site/` and passed it to `LocalProxyServer`. The proxy used these headers for ALL segment/subtitle fetches across ALL streams. VidCloud-1 segments (on `cloudvideo.lat`) require `Referer: https://vidwish.live/` — the hardcoded vidtube.site Referer caused 403.

**The fix (3 files):**

1. **`Models.kt`**: Added `referer: String` field to `AudioStream`. Each stream now carries its own Referer.

2. **`AnikotoExtractors.kt`**: Set the per-stream Referer in both extractors:
   - `resolveVidTube`: `streamReferer = "https://$host/"` (the iframe/player host — vidtube.site, megaplay.buzz, or vidwish.live)
   - `resolveKiwi`: `streamReferer = "https://vibeplayer.site/"` (the m3u8 host — matches the existing `kiwiHeaders()`)

3. **`LocalProxyServer.kt`**: Added `headersForStream(streamIndex)` helper that builds headers with the per-stream Referer. Modified:
   - `serveSegment`: uses `headersForStream(streamIndex)` for segment fetch
   - `serveSubtitle`: uses `headersForStream(streamIndex)` for subtitle fetch
   - `triggerPrefetch`: receives + uses per-stream headers for prefetch fetches
   - `fetchSegment`: now takes a `Headers` parameter (instead of using the global `segmentHeaders`)
   - Falls back to the constructor `segmentHeaders` if the stream has no referer (defensive).

### D. Part A: VidCloud-1 in Preferred Server settings

Added `"VidCloud-1"` to both `entries` and `entryValues` arrays in the `PREF_SERVER_KEY` preference:
```kotlin
entries = arrayOf("Auto", "VidPlay-1", "HD-1", "Vidstream-2", "VidCloud-1", "Kiwi-Stream")
entryValues = arrayOf("auto", "VidPlay-1", "HD-1", "Vidstream-2", "VidCloud-1", "Kiwi-Stream")
```
The `preferredServer` sort logic already handles arbitrary server names (it uses `contains(prefServer, true)`), so no additional changes were needed.

### E. Build v16.14 + verification

- versionCode=14, versionId=11 STABLE, versionName=16.14
- BUILD SUCCESSFUL in 27s
- All 11 checklist items pass: Stub! count=0, extClass=.Anikoto, versionId=11, icons 5 densities
- DEX strings verified: `/stream/getSources?id=` (unified, no getSourcesNew), `vidwish.live` (dispatch), `referer` (per-stream field)
- MD5: `56cad02451304e0866a0f22cae4e163e`

### F. Full end-to-end live verification (Klutzy EP12 VidCloud-1)

Simulated the complete v16.14 flow:
1. Episode → data-ids → server list → VidCloud-1 [sub]
2. Resolve → iframe on vidwish.live → per-stream Referer = `https://vidwish.live/`
3. getSources?id=175244&type=sub → m3u8 on fxpy7.watching.onl ✅
4. Master m3u8 (Referer=vidwish.live) → HTTP 200, 3 variants (1080p/720p/360p) ✅
5. Variant 1080p (Referer=vidwish.live) → HTTP 200, 175 segments ✅
6. Segment CDN: `x91rz.cloudvideo.lat`
7. **Segment fetch with per-stream Referer (vidwish.live) → HTTP 200, 3.3MB ✅ PLAYS**
8. OLD code (Referer=vidtube.site) → HTTP 403 ← this was the bug (now fixed)
9. Not PNG-wrapped (plain TS) — stripPngHeader handles correctly

### G. Regression check (Wistoria EP5, all servers × audio)

Verified the unified getSources doesn't regress VidPlay-1/HD-1/Vidstream-2. All return correct audio-specific hashes with the correct per-stream Referer. No regressions.

## Issue A note (Vidstream-2 on Smoking EP5 → "Hosters list empty")

Investigated but NOT fixed (user said "address later"). Root cause analysis:
- Vidstream-2 getSources API works → returns m3u8 at `cdn.mewstream.buzz`
- BUT `cdn.mewstream.buzz` is Cloudflare-blocked (403) from the sandbox IP
- If the user's IP is also blocked → m3u8 fetch fails → `resolveVidTube` throws → returns null
- VidCloud-1 on Smoking EP5: vidwish.live returns server-side error → null
- Both null → empty hosters list → "Hosters list empty, no available videos"

This is a CDN accessibility issue (cdn.mewstream.buzz), NOT a Referer issue. The per-stream Referer fix in v16.14 does NOT fix "Hosters list empty" — that error occurs at the m3u8 fetch stage (before segments). If the user's device can reach cdn.mewstream.buzz, Vidstream-2 should now work (with the correct megaplay.buzz Referer for segments). If not, the user needs to send the log from `Download/1118000/` to diagnose.

## Files changed

- `video/Models.kt` — added `referer: String` to `AudioStream`
- `video/AnikotoExtractors.kt` — unified getSources + set per-stream Referer in both extractors
- `video/LocalProxyServer.kt` — `headersForStream()` + per-stream headers in serveSegment/serveSubtitle/triggerPrefetch/fetchSegment
- `Anikoto.kt` — added VidCloud-1 to Preferred Server settings
- `build.gradle.kts` — versionCode 13→14 (versionId stays 11 STABLE)
- `AnikotoLog.kt` — EXTENSION_VERSION updated to v16.14

## Status at end of session

- ✅ Part A: VidCloud-1 added to Preferred Server settings
- ✅ Fix 1: Unified getSources?id=X&type=Y (works on all 3 hosts, respects audio type)
- ✅ Fix 2: Per-stream Referer (VidCloud-1 segments now fetch with vidwish.live Referer → 200 instead of 403)
- ✅ v16.14 built + all 11 checklist items pass
- ✅ Full end-to-end live verification: VidCloud-1 Klutzy EP12 → 3 variants, 175 segments, segment fetch 200 ✅
- ✅ No regressions (Wistoria EP5 all servers × audio verified)
- ⏳ Issue A (Vidstream-2 "Hosters list empty" on Smoking EP5): noted for later — likely cdn.mewstream.buzz CDN accessibility
- ⏳ User to test v16.14 on device

## Honest notes

- **The per-stream Referer is the critical fix.** Without it, VidCloud-1 segments 403'd. The getSources unification is a simplification (fewer code paths) but wasn't strictly necessary since session 26's host-based split already worked. However, the user's recommendation to unify was correct — `getSources?id=X&type=Y` works everywhere, making the code simpler and more maintainable.

- **VidCloud-1 segments are NOT PNG-wrapped.** This is different from VidPlay-1 (vidtube.site) segments which ARE PNG-wrapped. The `stripPngHeader` function is defensive (checks for the PNG magic bytes) so it correctly handles both cases — no change needed.

- **The getSources API doesn't check Referer.** I verified this explicitly: the API returns valid JSON with any Referer (or no Referer) on all 3 hosts. The Referer only matters for the m3u8 + segment fetch (CDN-level check). So the extension's `vidtubeApiHeaders()` (hardcoded vidtube.site Referer) is fine for the API call — I didn't need to change it.

- **Issue A (Vidstream-2 "Hosters list empty") is a separate problem.** It's about cdn.mewstream.buzz being inaccessible (Cloudflare block), not about the Referer. The per-stream Referer fix doesn't address this. The user should test v16.14 — if Vidstream-2 works on their device (different IP), then it was just a sandbox-side block. If it still fails, we need the log to diagnose.

## Next steps

User tests v16.14:
1. Uninstall old extension, install v16.14.
2. Set Preferred Server = VidCloud-1 (now in settings).
3. Test VidCloud-1 on Klutzy Class Monitor EP12 → should now play (3 resolutions, 175 segments).
4. Test VidPlay-1 on any episode → should still work (no regression).
5. Test Vidstream-2 on Smoking EP5 → if still "Hosters list empty", send the log.
6. Test audio switching + subtitles on VidCloud-1.
