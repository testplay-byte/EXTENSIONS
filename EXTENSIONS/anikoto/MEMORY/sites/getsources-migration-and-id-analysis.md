# ANIKOTO — getSources Migration & Extension ID Analysis

> Last updated: 2026-06-24 (session 27) · Status: ✅ VERIFIED (live-tested with Python + agent-browser)
> Test episodes: Klutzy Class Monitor EP12, Smoking Behind the Supermarket EP5, Wistoria S2 EP5

## 1. The getSourcesNew → getSources migration

The site has migrated the player API from `getSourcesNew` to `getSources`. The old
`getSourcesNew` endpoint is broken on two of the three player hosts. This is why
Vidstream-2 and VidCloud-1 stopped working.

### Verified per-host behavior (2026-06-24, re-verified session 27)

| Player host | Servers | `getSourcesNew?id=X&type=Y` | `getSources?id=X&type=Y` | data-id per audio? |
|---|---|---|---|---|
| `vidtube.site` | VidPlay-1 | ✅ 200 JSON (works) | ✅ 200 JSON (**respects type** — correct audio) | ❌ SAME data-id across sub/hsub/dub |
| `megaplay.buzz` | HD-1, Vidstream-2 | ❌ 404 Not Found | ✅ 200 JSON (respects type) | ✅ DIFFERENT data-id per audio |
| `vidwish.live` | VidCloud-1 | ❌ 200 HTML error page ("Error - Vidcloud") | ✅ 200 JSON (respects type) | ✅ DIFFERENT data-id per audio |

### ★ Session 27 update: unified to `getSources?id=X&type=Y` for ALL hosts

**Session 26** used a host-based split: `getSourcesNew?type=Y` for vidtube.site,
`getSources?id=X` (no type) for megaplay.buzz/vidwish.live. This was based on testing
`getSources?id=X` WITHOUT the type param (which always returned SUB on vidtube.site).

**Session 27** re-tested `getSources?id=X&type=Y` (WITH the type param) on all 3 hosts.
Result: **it works universally and respects the audio type on every host.** The
extension now uses a single unified call:
```kotlin
"https://$host/stream/getSources?id=$dataId&type=$audioType"
```
This simplifies the code (no host-based split) and is correct for all hosts.
`getSourcesNew` is no longer used anywhere.

### Key detail: data-id sharing on vidtube.site vs the others

- **vidtube.site (VidPlay-1):** the `data-id` is the **SAME** across sub/hsub/dub
  (e.g. 138029 for all 3). The audio type is selected by the `type` query parameter.
  Both `getSourcesNew?type=Y` and `getSources?type=Y` respect the type. → Use `getSources?type=Y`.

- **megaplay.buzz / vidwish.live:** the `data-id` is **DIFFERENT** per audio type
  (e.g. HD-1: sub=176012, hsub=176261, dub=176502). The audio is baked into the embed
  URL path (`/stream/s-2/{epId}/sub` vs `/hsub` vs `/dub`). The `type` param is also
  respected (redundant but harmless). → Use `getSources?id=X&type=Y`.

### The getSources API does NOT check Referer

Verified session 27: the getSources API returns valid JSON with ANY Referer (or no
Referer) on all 3 hosts. The Referer only matters for the **m3u8 + segment fetch**
(CDN-level check). The extension's `vidtubeApiHeaders()` (hardcoded
`Referer: https://vidtube.site/`) is fine for the API call — no change needed there.

### Response format (identical for both endpoints)

```json
{
  "sources": { "file": "https://<cdn>/anime/<hash1>/<hash2>/master.m3u8" },
  "tracks": [ { "file": "...", "label": "English", "kind": "captions" } ],
  "t": 1,
  "intro": { "start": 0, "end": 0 },
  "outro": { "start": 0, "end": 0 },
  "server": 4
}
```

The existing `VidTubeSourcesResponse` DTO works for both endpoints unchanged.

### CDN hosts (NEW — the old nekostream.site is gone for megaplay/vidwish)

| Player host | m3u8 CDN host | Segment CDN host | Notes |
|---|---|---|---|
| `vidtube.site` | `mt.nekostream.site` | `mt.nekostream.site` (same) | unchanged. Segments are PNG-wrapped. |
| `megaplay.buzz` | `cdn.mewstream.buzz` | `cdn.mewstream.buzz` (same) | NEW CDN. Cloudflare-protected. Sandbox IP blocked. |
| `vidwish.live` | `fxpy7.watching.onl` | `x91rz.cloudvideo.lat` (DIFFERENT!) | NEW. m3u8 and segments on DIFFERENT hosts. Segments NOT PNG-wrapped (plain TS). |

> ⚠️ The sandbox IP is Cloudflare-blocked from `cdn.mewstream.buzz` ("Sorry, you have
> been blocked"). This is an IP/ASN block on the sandbox's datacenter IP — the user's
> Android device (residential/mobile IP) is expected to work, same as the old
> `nekostream.site` CDN did. Cannot fully verify megaplay segment fetch from sandbox.
> `fxpy7.watching.onl` and `cloudvideo.lat` (vidwish) are NOT blocked from the sandbox.

### ★ Per-stream Referer (session 27 — CRITICAL for VidCloud-1)

**The CDN checks the Referer header on m3u8 + segment fetches.** Each player host
requires its OWN Referer — using the wrong one causes HTTP 403.

| Player host | Required Referer for m3u8/segments | Wrong Referer (old code) |
|---|---|---|
| `vidtube.site` (VidPlay-1) | `https://vidtube.site/` | (was correct — hardcoded) |
| `megaplay.buzz` (HD-1, Vidstream-2) | `https://megaplay.buzz/` | `https://vidtube.site/` → may 403 |
| `vidwish.live` (VidCloud-1) | `https://vidwish.live/` | `https://vidtube.site/` → **403 (confirmed)** |
| Kiwi-Stream | `https://vibeplayer.site/` | `https://vidtube.site/` → may 403 |

**Verified (Klutzy EP12 VidCloud-1):**
- Segment on `x91rz.cloudvideo.lat` with `Referer: https://vidtube.site/` → **HTTP 403**
- Same segment with `Referer: https://vidwish.live/` → **HTTP 200** (3.3MB, plain TS)

**The fix (session 27):** Each `AudioStream` now carries a `referer` field (set to
`https://$iframeHost/` by the extractors). The `LocalProxyServer` uses this per-stream
Referer via `headersForStream(streamIndex)` for all segment + subtitle fetches, instead
of the old hardcoded `Referer: https://vidtube.site/`.

### ★ cdn.mewstream.buzz Cloudflare WAF block (session 28 — Vidstream-2 fix)

**The problem:** `cdn.mewstream.buzz` (megaplay.buzz's CDN for HD-1/Vidstream-2) returns
**HTTP 403** to OkHttp. This is a Cloudflare WAF block — stricter than the other CDNs
(fxpy7.watching.onl / cloudvideo.lat for VidCloud-1, which return 200 to OkHttp).

Two contributing factors:
1. **Bot-signature User-Agent**: "Mozilla/5.0" alone is a known bot signature that triggers
   Cloudflare's WAF bot detection on stricter CDNs.
2. **No CloudflareInterceptor**: The extension used `noCloudflareClient` (clean OkHttp) for
   the extractors. The inherited `client` (from AnimeHttpSource) has the app's
   `CloudflareInterceptor` which handles 403/503 from Cloudflare by opening a WebView
   (Chrome engine, real TLS fingerprint) to obtain a `cf_clearance` cookie, then retries.

**The fix (session 28):**
1. Use the inherited `client` (with CloudflareInterceptor + cookieJar) for extractors + proxy.
   The interceptor only triggers on 403/503 from `Server: cloudflare` — VidCloud-1 (200) is
   unaffected. Non-breaking.
2. Replace "Mozilla/5.0" with a full Chrome mobile UA everywhere:
   `Mozilla/5.0 (Linux; Android 14; KB2001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36`
   Also added `Accept: */*` + `Accept-Language: en-US,en;q=0.9` to `segHeaders`/`kiwiHeaders`.

**How the CloudflareInterceptor works** (verified in
`SHARED/REFERENCE_HUB/aniyomi-app/.../CloudflareInterceptor.kt`):
```
shouldIntercept(response):
  response.code in [403, 503] && response.header("Server") in ["cloudflare-nginx", "cloudflare"]
intercept(chain, request, response):
  1. Remove old cf_clearance cookies for the domain
  2. Open WebView loading the original request URL (Chrome TLS fingerprint)
  3. Wait up to 30s for a NEW cf_clearance cookie
  4. If obtained → retry request (now with cookie) → 200
  5. If not → throw CloudflareBypassException
```

The cf_clearance cookie is cached in the cookieJar and reused for all subsequent requests
to the same domain (variant m3u8 + segment fetches). Only the first m3u8 fetch may trigger
the WebView (up to 30s delay); after that, everything is fast.

**Sandbox limitation:** The sandbox IP is Cloudflare-blocked from cdn.mewstream.buzz
regardless of headers or TLS fingerprint. The fix cannot be fully verified from the sandbox.
The user's device (residential IP) should work: the full Chrome UA may prevent the 403
entirely, or the CloudflareInterceptor will handle it via WebView.

### The megaplay.buzz /api documentation page

`GET https://megaplay.buzz/api` returns an HTML doc page titled "Anikoto video API —
HiAnime library & Anikoto". Key points:
- The embed URL is `https://megaplay.buzz/stream/s-2/{aniwatch-ep-id}/{language}`
  where `{aniwatch-ep-id}` = the `data-realid` from the player page, and
  `{language}` = `sub`|`dub`.
- "Direct Access to Embed Links are Disabled. Links only work as Embed on your Websites"
  → a Referer from an allowed domain (anikototv.to) is required.
- Also supports MAL/AniList-based embeds: `/stream/mal/{mal-id}/{ep-num}/{language}` and
  `/stream/ani/{anilist-id}/{ep-num}/{language}`. (Not needed for our extension — we
  already get the embed URL from anikototv.to's `/ajax/server?get=`.)
- Catalog API at `https://anikotoapi.site` (for discovering anime/episodes). Not needed
  for our extension.

### The fix (implemented session 26)

In `AnikotoExtractors.resolveVidTube`, select the endpoint by host:
- `vidtube.site` → `getSourcesNew?id=<data-id>&type=<audio>` (unchanged — type required)
- `megaplay.buzz`, `vidwish.live` → `getSources?id=<data-id>` (new — getSourcesNew broken)

In `Anikoto.resolveStreamForTask`, add `vidwish.live` to the dispatch (route to
`resolveVidTube`). Previously vidwish.live fell to the `else` branch and was skipped.

### VidCloud-1 episode-specific failures (server-side, not our bug)

Some episodes' VidCloud-1 returns an "Error - Vidcloud" HTML page even from the iframe
page itself (e.g. Smoking Behind the Supermarket EP5). This is a vidwish.live
server-side issue — that episode's data isn't available on vidwish.live. The extension
handles this gracefully (logs the error, skips the server). VidCloud-1 DOES work on
episodes where vidwish.live has the data (e.g. Klutzy Class Monitor EP12, Wistoria EP5).

---

## 2. Extension ID stability (Issue A)

### Root cause

The Aniyomi app links saved anime to extensions via the source `id`, which is:
```
id = MD5("name.lowercase()/lang/versionId")  // first 8 bytes, sign bit = 0
```
(Verified in `SHARED/REFERENCE_HUB/aniyomi-app/.../AnimeHttpSource.kt:58` and
`AnimeExtensionManager.getExtensionPackage(sourceId)`.)

The `versionId` doc says: *"Version id used to generate the source id. If the site
completely changes and urls are incompatible, you may increase this value and it'll be
considered as a new source."* (Default = 1.)

**Our bug:** the build checklist (item 3) required `versionId` to match `versionCode`
and be bumped on every rebuild. We bumped versionId 2→3→...→11 across sessions 16-25.
Each bump changed the source id → the app saw each build as a NEW source → saved anime
were orphaned.

### The fix

**Decouple `versionId` from `versionCode`:**
- `versionId` = STABLE. Set once, never bumped unless the site's URL structure breaks
  (domain change, URL scheme change). Keep at 11 (preserves any anime saved under v16.11).
- `versionCode` = bumped per APK build (12, 13, ...). This is the APK update signal
  (Android compares versionCode to detect updates).
- `versionName` = `"16.$versionCode"` (follows versionCode, for the ext-lib 16 version
  constraint).

The manifest meta-data `tachiyomi.animeextension.versionId` — verified the app does NOT
read this (AnimeExtensionLoader only reads `.class`, `.factory`, `.nsfw`, `.hasReadme`,
`.hasChangelog`). Kept for completeness, set to the stable versionId.

### Build checklist item 3 (CORRECTED)

Old (wrong): "extVersionId matches extVersionCode — bump both every rebuild."
New (correct):
- `extVersionCode` incremented per build ✓
- `extVersionId` = STABLE (do NOT bump with versionCode) ✓
- `override val versionId` in Anikoto.kt = STABLE (matches extVersionId) ✓
- `EXTENSION_VERSION` in AnikotoLog.kt = STABLE versionId ✓

---

## 3. Test commands (reusable)

```bash
# Full chain analysis (Python) — tests episode → servers → resolve → getSources
cd /home/z/my-project/EXTENSIONS/anikoto/analysis
python3 test_live_chain.py

# Quick getSources check for a specific host+data-id
python3 -c "
import requests; r=requests.get('https://<host>/stream/getSources?id=<data-id>',
  headers={'User-Agent':'Mozilla/5.0','Referer':'https://<host>/','X-Requested-With':'XMLHttpRequest'});
print(r.status_code, r.json())
"

# Browser-based network capture (when Python is Cloudflare-blocked)
agent-browser set headers '{"Referer":"https://anikototv.to/"}'
agent-browser open "https://megaplay.buzz/stream/s-2/<epId>/sub"
sleep 8
agent-browser network requests  # look for getSources / m3u8 / segment calls
```
