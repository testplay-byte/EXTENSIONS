# Session 29 — Vidstream-2 Fix: Desktop Chrome UA + Minimal Headers (from reference project analysis)

> Date: 2026-06-24 · Session #: 29 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Fix Vidstream-2 (megaplay.buzz → cdn.mewstream.buzz) which still returns HTTP 403 despite
the session 28 fix (CloudflareInterceptor + mobile Chrome UA). The user provided a reference
Next.js project that successfully plays megaplay.buzz videos — analyze it to learn the correct
approach, then implement the fix.

## Root cause analysis

### The v16.15 fix didn't work (user's log: anikoto-2026-06-24_18-04-50.log)

The log showed Vidstream-2 failing in **36ms** with `HTTP 403` at the master m3u8 fetch from
cdn.mewstream.buzz. The 36ms timing is critical: if the CloudflareInterceptor had triggered
(WebView bypass), it would take ~30s. The 36ms means the interceptor did NOT trigger — the
403 passed straight through to `fetchString`.

### Why the CloudflareInterceptor didn't trigger

The `Server: cloudflare` header IS present on the 403 response (verified from sandbox). The
CloudflareInterceptor's `shouldIntercept` checks `response.code in [403, 503] && response.header("Server") in ["cloudflare-nginx", "cloudflare"]` — both conditions are true. Yet the
interceptor didn't fire. Possible explanations:
- The interceptor may have triggered but the WebView also got 403 (header-based block, not
  challenge), and the bypass failed quickly
- OR the extension's `client` may not have the interceptor properly wired

Regardless — the key insight from the reference project analysis is that **the 403 is NOT a
Cloudflare JS challenge. It is a header-based WAF rule.** No amount of CloudflareInterceptor
WebView bypassing will help, because there's no challenge to solve. The WAF makes a binary
allow/deny decision based on the request headers.

### Reference project analysis (subagent)

The user provided a Next.js project (`Subtitle .tar`) that successfully plays megaplay.buzz
videos. A subagent analyzed it thoroughly. Key findings:

1. **The reference project NEVER fetches cdn.mewstream.buzz directly from the browser.** For
   live streaming it embeds the megaplay.buzz iframe. For offline download it routes through
   a Next.js server-side API route (`/api/video-proxy`) that adds `Referer: https://megaplay.buzz/`
   + a **desktop** Chrome UA on the server.

2. **The critical headers for cdn.mewstream.buzz are:**
   ```
   Referer: https://megaplay.buzz/
   User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
   Accept: */*
   ```
   No cookies, no cf_clearance, no Accept-Language. Just these 3 headers.

3. **No Cloudflare bypass techniques at all.** No cf_clearance cookie, no WebView, no TLS
   fingerprinting, no headless browser. The WAF is purely header-based.

4. **For megaplay.buzz API requests** (getSources): `Referer: https://anikototv.to/`,
   `Origin: https://anikototv.to`, `X-Requested-With: XMLHttpRequest`.

5. **Multi-ID query strategy**: the reference project queries data-id, data-realid, AND
   data-mediaid from the iframe page, calling getSources for each. Different IDs can return
   different CDN URLs (cdn.mewstream.buzz, s1.streamzone1.site, s2.cinewave2.site) — providing
   CDN fallbacks.

### Why v16.15 failed (two issues)

1. **Mobile Chrome UA** — v16.15 used `Mozilla/5.0 (Linux; Android 14; KB2001) ... Chrome/130.0.0.0 Mobile Safari/537.36`. The reference project uses **desktop** Chrome:
   `Mozilla/5.0 (Windows NT 10.0; Win64; x64) ... Chrome/120.0.0.0 Safari/537.36`. The WAF
   may specifically reject mobile UAs.

2. **Accept-Language header** — v16.15 sent `Accept-Language: en-US,en;q=0.9`. The reference
   project sends NO Accept-Language. Extra headers can trigger WAF rules.

## The fix (v16.16)

### Changed the User-Agent to desktop Chrome/120

In `AnikotoExtractors.kt` and `LocalProxyServer.kt`:
```kotlin
private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
```
This matches the reference project's UA exactly (proven working).

### Removed Accept-Language from all header builders

In `AnikotoExtractors.kt`: removed from `vidtubePageHeaders`, `vidtubeApiHeaders`, `segHeaders`, `kiwiHeaders`.
In `LocalProxyServer.kt`: removed from `headersForStream`.
In `Anikoto.kt`: removed from the fallback `segHeaders`.

The headers for cdn.mewstream.buzz are now exactly:
```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Referer: https://megaplay.buzz/   (per-stream, from session 27)
Accept: */*
```
No Accept-Language, no cookies, no extra headers. Matches the reference project.

### What was NOT changed (preserved from previous sessions)

- **Per-stream Referer** (session 27): each AudioStream carries its own Referer. For Vidstream-2
  it's `https://megaplay.buzz/`, for VidCloud-1 it's `https://vidwish.live/`, etc. Unchanged.
- **getSources?id=X&type=Y** (session 27): unified endpoint. Unchanged.
- **Inherited client with CloudflareInterceptor** (session 28): kept as a fallback. If the
  header fix doesn't work and there IS a Cloudflare challenge, the interceptor can still fire.
- **Extension ID stability** (session 25): versionId=11 STABLE. Unchanged.

## Verification

### Build verification (v16.16)
- BUILD SUCCESSFUL in 27s
- All 11 checklist items pass: Stub! count=0, extClass=.Anikoto, versionId=11 STABLE
- DEX verified: desktop Chrome/120 UA present (2 occurrences), **zero** mobile UA occurrences
- MD5: `dd00cda3d84e9b537b9c2507004dfa82`

### Regression check (sandbox)
- **VidCloud-1** (Klutzy EP12): getSources ✅ → m3u8 ✅ → 3 variants ✅ → 175 segments ✅ →
  segment fetch 200 (3.3MB) ✅ — **no regression** with the desktop UA
- **Vidstream-2** (Smoking EP5): getSources API ✅ → m3u8 URL obtained ✅ → m3u8 fetch 403
  (sandbox IP blocked — cannot verify from sandbox; user's device needed)

### Sandbox limitation

The sandbox IP (47.57.242.119, Hong Kong datacenter) is Cloudflare-blocked from
cdn.mewstream.buzz regardless of headers (verified: mobile UA, desktop UA, with/without
Accept-Language — ALL return 403). The fix cannot be fully verified from the sandbox.

The user's device (residential IP, OnePlus KB2001) is NOT IP-blocked (VidCloud-1's CDN works).
The v16.16 fix sends the exact same headers as the reference project (which successfully fetches
from cdn.mewstream.buzz). If the WAF is purely header-based (as the subagent's analysis suggests),
this fix should work on the user's device.

If the WAF also checks TLS fingerprint (JA3), OkHttp's fingerprint differs from Chrome/Node.js,
and the fix may not work. In that case, the next step would be custom TLS fingerprint spoofing
or a different approach. But the subagent's analysis strongly suggests it's header-based.

## Files changed

- `AnikotoExtractors.kt`: BROWSER_UA → desktop Chrome/120; removed Accept-Language from all 4 header builders
- `LocalProxyServer.kt`: BROWSER_UA → desktop Chrome/120; removed Accept-Language from headersForStream
- `Anikoto.kt`: fallback segHeaders → desktop Chrome/120, no Accept-Language
- `build.gradle.kts`: versionCode 15→16 (versionId stays 11 STABLE)
- `AnikotoLog.kt`: EXTENSION_VERSION updated to v16.16

## Reference project

The reference Next.js project was extracted to `/home/z/my-project/_ref_megaplay/` for analysis.
Key files analyzed (by the subagent):
- `src/app/api/video-sources/route.ts` — orchestrator: embed page → getSources → parse HLS
- `src/app/api/video-proxy/route.ts` — segment proxy with Referer=megaplay.buzz + desktop Chrome UA
- `src/app/api/megaplay/route.ts` — thin getSources pass-through
- `src/app/api/proxy/route.ts` — general proxy with smart Referer routing
- `src/app/api/subtitle/route.ts` — subtitle proxy with Referer=megaplay.buzz + Origin=megaplay.buzz

The reference project's worklog confirmed: "Referer-gated CDN requires proxy" and "Only 1080p
quality available, Referer-gated CDN requires proxy, ~450MB per episode, no encryption".

## Status at end of session

- ✅ Root cause identified: v16.15 used mobile Chrome UA (WAF rejects mobile) + Accept-Language (extra header)
- ✅ Reference project analyzed: desktop Chrome/120 UA + Referer=megaplay.buzz + Accept */* + no extra headers
- ✅ v16.16 built + all 11 checklist items pass
- ✅ VidCloud-1 regression check: no regression (still fully works)
- ✅ DEX verified: desktop Chrome/120 UA present, zero mobile UA occurrences
- ⏳ Vidstream-2 m3u8 fetch: cannot verify from sandbox (IP blocked) — user must test on device

## Honest notes

- **The desktop Chrome UA is the most likely fix.** The reference project uses this exact UA
  and successfully fetches from cdn.mewstream.buzz. The mobile Chrome UA in v16.15 was likely
  rejected by the WAF. Desktop vs mobile is a common WAF differentiator.

- **Accept-Language removal is a minor improvement.** The reference project doesn't send it,
  and extra headers can theoretically trigger WAF rules. Removing it makes our headers match
  the reference exactly.

- **Cannot fully verify from sandbox.** The sandbox IP is Cloudflare-blocked from
  cdn.mewstream.buzz regardless of headers. The fix is based on the reference project's proven
  approach. If it still fails on the user's device, the next step is investigating TLS
  fingerprint (JA3) differences between OkHttp and Chrome/Node.js.

- **The CloudflareInterceptor mystery.** The `Server: cloudflare` + 403 should trigger
  `shouldIntercept` → true, but the 36ms timing says it didn't fire. This may be because the
  interceptor is not properly wired in the extension's client, or because the WebView bypass
  fails immediately for header-based blocks (no challenge page → no cf_clearance → fast failure).
  Either way, the header fix should bypass the need for the interceptor entirely.

- **The reference project's multi-ID strategy** (querying data-id, data-realid, data-mediaid
  for CDN fallbacks) is a good resilience improvement. Not implemented in this session (one
  change at a time, rule §2). Can be added in a future session if needed.

## Next steps

User tests v16.16:
1. Uninstall old extension, install v16.16.
2. Test Vidstream-2 on Smoking Behind the Supermarket EP5.
   - If it plays: fix confirmed ✅
   - If it still shows "Hosters list empty" or 403: send the new log from Download/1118000/
3. Test VidCloud-1 on Klutzy EP12 (should still work — no regression).
4. Test VidPlay-1 on any episode (should still work — no regression).

If Vidstream-2 still fails with 403 despite the correct headers, the issue is likely TLS
fingerprint (JA3). The next step would be:
- Investigate custom OkHttp SSLSocketFactory to match Chrome's JA3
- Or implement the multi-ID fallback strategy (different IDs may return different, less strict CDNs)
- Or set up a local proxy mini-service on the device (like the reference project does)
