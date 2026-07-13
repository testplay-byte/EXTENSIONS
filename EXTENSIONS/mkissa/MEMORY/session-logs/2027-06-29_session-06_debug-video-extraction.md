# Session 06 — MKissa: Debug Video Extraction (Force Network + Logging)

> Date: 2027-06-29 · Extension: MKissa 180 (mkissa.to) · Session: 06 · Status: ⚠️ debugging — added extensive logging to diagnose "no tobeparsed" issue

## Goal

The user tested v16.5 and got "no available videos" immediately. The logcat showed:
```
MKissa  I  getHosterList: showId=2P7kFgthrEfRRkcdm, ep=9, type=sub
MKissa  W  MKissaExtractor: no tobeparsed in response
MKissa  W  getHosterList: no videos extracted
```

The API call was made but the response didn't contain `tobeparsed`. I needed to find out WHY the on-device response differs from the curl response (which DOES contain `tobeparsed`).

## What was done

### Root cause investigation

1. **Tested the exact API call via curl** (same showId=2P7kFgthrEfRRkcdm, ep=9, sub): the API returns `{"data":{"_m":"b7","tobeparsed":"AbyBpi..."}}` correctly. The `tobeparsed` field IS present.

2. **Identified the likely cause:** the on-device OkHttp client (from `network.client`) has a default cache control of `maxAge=10 min` (from ext-lib's `GET()` function). A stale cached response from a previous failed request could return an empty/error body that doesn't contain `tobeparsed`.

3. **Also identified:** `awaitSuccess()` throws `HttpException` on non-2xx responses, which the catch block would log as "FAILED". But the log showed "no tobeparsed" — meaning the response WAS 2xx but the body was wrong. Using `execute()` instead lets me log the response even on error.

### Fixes applied (v16.6)

1. **`CacheControl.FORCE_NETWORK`** on the stream API request — bypasses OkHttp's 10-min default cache. This ensures we always get a fresh response from the server.

2. **Replaced `awaitSuccess()` with `execute()`** in `fetchStreamData()` — allows logging the HTTP status code + response body even on non-2xx responses. `awaitSuccess()` would throw before we could inspect the body.

3. **Extensive logging added:**
   - `fetchStreamData: requesting <full URL>` — logs the exact URL being requested
   - `fetchStreamData: HTTP <code>, body length=<N>` — logs the HTTP status + body size
   - `fetchStreamData: body first 300 chars: <preview>` — logs the start of the response body (so we can see if it's JSON, HTML, or an error)
   - `fetchStreamData: non-2xx response (<code>) — body: <preview>` — logs error responses
   - `fetchStreamData: JSON parse failed — <error>` — logs JSON parsing exceptions
   - `MKissaExtractor: no tobeparsed in response (body length=<N>)` — now includes the body length
   - `MKissaExtractor: body preview: <first 300 chars>` — shows what the body actually contains

### Logcat filter for Android Studio

The user asked for the correct filter. The extension logs with tag `MKissa` (set in `MKissaLog.kt`). In Android Studio's Logcat panel:

**Filter:** `tag:MKissa`

Or in the command-line `adb logcat`:
```bash
adb logcat -s MKissa:*
```

This will show ALL log levels (I, W, E, D) from the MKissa extension. The `DatabaseUtils` errors in the user's log are from Android's external storage provider (unrelated to our extension — they're about a missing download folder).

### Build v16.6
- Debug APK: 243KB
- Build checklist ALL PASS: package=...en.mkissa180 v16.6, Stub! count=0
- Live on the webpage at `/api/apk?ext=mkissa&type=debug` (HTTP 200, 248326 bytes)

## What needs the user's testing

The user should:
1. Download v16.6 from the webpage
2. Install it in Aniyomi (uninstall v16.5 first — same package name)
3. Open Witch Hat Atelier (or any anime), tap an episode
4. Check logcat with filter `tag:MKissa` (or `adb logcat -s MKissa:*`)
5. Share the logs — especially the `fetchStreamData: HTTP <code>, body length=<N>` and `body first 300 chars` lines

This will tell us exactly what the API is returning on-device, and we can fix the root cause.
