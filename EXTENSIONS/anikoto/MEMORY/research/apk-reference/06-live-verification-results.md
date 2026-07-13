# 06 — Live Verification Results (Session 11)

> Last updated: 2026-06-22 (session 11) · Status: VERIFIED against the live site (`https://anikototv.to`)
> Test anime: Solo Leveling Season 2 (`solo-leveling-season-2-arise-from-the-shadow-3eukp`, animeId=`7457`)
> Method: curl + python3 (RC4 implementation) + agent-browser-ready

This document records the live verification of the 7 open items from `05-cross-check-lessons.md` §6.
All 7 items are now RESOLVED. The reference APK's behavior is confirmed against the current live site.

## Summary table

| # | Item | Result | Impact on our implementation |
|---|---|---|---|
| 1 | `/most-viewed` vs `/filter?sort=most-viewed` | **BOTH work, identical content** (SEO alias) | Use either; the reference's `/most-viewed` is shorter |
| 2 | `/latest-updated` vs `/filter?sort=latest-updated` | **BOTH work, identical content** (SEO alias) | Use either; the reference's `/latest-updated` is shorter |
| 3 | Details-page selectors (`#w-info` vs `.binfo`) | **Both coexist** — `.binfo` is inside `#w-info`; all selectors valid | Adopt the reference's `#w-info`-prefixed selectors (superset, safer) |
| 4 | `vrf` param (RC4 `"simple-hash"`) | **Server does NOT validate** (all 3 cases return identical content) | Implement anyway (~15 LOC) to match reference + be safe against future validation |
| 5 | VidTube `getSourcesNew` `type` param | **CONFIRMED** — sub/hsub/dub return different m3u8 URLs (different hashes) | Implement exactly as reference: `type=sub|hsub|dub` |
| 6 | PNG header on segments | **CONFIRMED** — 70-byte PNG header (IEND@62, cut@70), MPEG-TS 0x47 sync follows | Implement `stripPngHeader` with IEND+8 algorithm (robust, matches) |
| 7 | Ad-segment discrimination | **CONFIRMED** — by CDN host: `mt.nekostream.site`=real, `p1.ipstatp.com`=ad (130 ads vs 12 real) | Filter by host in `parseVariantSegments` (keep `mt.nekostream.site`) |

## Detailed findings

### Item 1 & 2 — Popular + Latest endpoints (SEO aliases)

Both the dedicated paths AND the `/filter?sort=...` form return HTTP 200 with **identical item content** (40 items, same titles, same order). The only diff is the `<title>`/`<meta>` tags (e.g. "Most Viewed - Anikoto" vs the filter page title).

```
/most-viewed?page=1              → 40 items, 115487 bytes  (Solo Leveling S2, One Piece, ...)
/filter?sort=most-viewed&page=1  → 40 items, 115470 bytes  (identical items)
/latest-updated?page=1           → 40 items, 112750 bytes  (Farming Life S2, Nippon Sangoku, ...)
/filter?sort=latest-updated&page=1 → 40 items, 112717 bytes (identical items)
```

**Conclusion**: The dedicated paths (`/most-viewed`, `/latest-updated`) are SEO-friendly aliases for `/filter?sort=...`. Either works. The reference uses the dedicated paths (shorter URLs). Our session-08 used `/filter?sort=...`. Both are correct — **no change needed**, but we can switch to the dedicated paths to match the reference.

**Catalog selectors confirmed** (from `/most-viewed`):
- Container: `<div id="list-items" class="ani items">` ✓
- Items: `<div class="item ">` (trailing space; `div.item` works in Jsoup) ✓
- Title: `<a class="name d-title" href="..." data-jp="Japanese Title">English Title</a>` ✓
- Poster: `<div class="ani poster tip" data-tip="7457"> <a> <img src="..." alt="...">` ✓
- Pagination: `<ul class="pagination">` with `rel="next"` ✓
- Bonus: `data-tip` on the poster div = the animeId (matches `#watch-main[data-id]`)

### Item 3 — Details-page selectors (all coexist)

The `/watch/<slug>/ep-1` page has this structure:
```
#w-info
└── .binfo
    ├── .poster > span > img[src][alt][itemprop=image]   ← poster
    └── .info
        ├── h1.title.d-title[itemprop=name][data-jp]     ← title (data-jp = Japanese)
        ├── .names.font-italic.mb-2                       ← alt titles (semicolon-separated)
        ├── .meta.icons.mb-3                              ← rating (R), quality (HD), sub/dub icons
        ├── .synopsis.mb-3 > .shorting > .content         ← synopsis text
        └── .bmeta > .meta > div                          ← "Type: <span>TV</span>", genres, studios, etc.
```

- `#w-info` ✓ (reference's container — the outermost)
- `.binfo` ✓ (our research's container — inside `#w-info`)
- `.bmeta` ✓ (both — inside `.binfo > .info`)
- `.brating` ✓ (our research — elsewhere on the page, user rating)
- `.synopsis` ✓ (class is `class="synopsis mb-3"`; CSS `.synopsis` matches via whitespace-split)
- `.synopsis .content` ✓ (reference's selector for the synopsis text)
- `h1.title.d-title[data-jp]` ✓ (reference's title selector)
- `.poster img[src]` ✓ (reference's poster selector)

**Conclusion**: Both selector sets are valid. The reference's `#w-info`-prefixed selectors are a **superset** (more specific, safer). **Adopt the reference's selectors**: `#w-info h1.title.d-title`, `#w-info .poster img`, `#w-info .bmeta a[href*='/genre/']`, `.synopsis .content`.

### Item 4 — vrf param (server does NOT validate)

Implemented RC4 in Python (matching the reference's `AnikotoRC4` exactly: textbook RC4, key `"simple-hash"`, Base64.NO_WRAP, ISO_8859_1 bytes) and tested the episode-list endpoint with 3 cases:

| Case | vrf value | Response |
|---|---|---|
| Correct RC4 vrf | `INCt0g%3D%3D` (base64 `INCt0g==`) | `{"status":200,"result":"<HTML>"}` 9949 bytes |
| No vrf param | (empty) | `{"status":200,"result":"<HTML>"}` 9949 bytes (identical) |
| Bogus vrf | `BOGUS` | `{"status":200,"result":"<HTML>"}` 9949 bytes (identical) |

**The server does NOT validate the vrf param** — all three return identical content. This means our session-08 build (which noted but didn't implement vrf) would actually work for the episode-list endpoint.

**Conclusion**: Implement `AnikotoRC4` anyway (~15 LOC) to match the reference exactly and be safe against future server-side validation. The algorithm is confirmed: `Base64.NO_WRAP( RC4("simple-hash", animeId).getBytes(ISO_8859_1) )` then URL-encode.

**Episode list HTML structure confirmed** (from the `result` field):
```html
<ul class="ep-range" data-range="001-013">
  <li title="You Aren't E-rank, Are You" data-html="true">
    <a href="#" data-id="114623" data-num="1" data-slug="1" data-mal="58567"
       data-timestamp="1756930266" data-sub="1" data-dub="1"
       data-ids="<base64-blob>" class="active  ">
      <b>1</b>
      <span class="d-title" data-jp="Episode 1">You Aren't E-rank, Are You</span>
      <i></i>
    </a>
  </li>
</ul>
```
All expected attributes present: `data-id`, `data-num`, `data-slug`, `data-mal`, `data-timestamp`, `data-sub`, `data-dub`, `data-ids` ✓.

⚠️ **Minor parsing note**: the episode title is on the `<li title="...">` and `<span class="d-title">` inside the `<a>`, NOT on the `<a>` itself. The reference's `a.attr("title")` would return empty → fallback "Episode N". **Better**: use `a.selectFirst("span.d-title")` text, or the parent `<li>` title attr.

### Item 5 — VidTube `getSourcesNew` `type` param (confirmed different streams)

Resolved VidPlay-1 (sub) → iframe `https://vidtube.site/stream/<token>/sub` → extracted `data-id="7509"`. Tested `getSourcesNew` with all 3 audio types:

| `type` | master.m3u8 URL |
|---|---|
| `sub` | `https://mt.nekostream.site/6ac061d5e7e69e2e896da6ff24477d4f/master.m3u8` |
| `hsub` | `https://mt.nekostream.site/9cd4c4d4b5cec4bce00749e8f12463f8/master.m3u8` |
| `dub` | `https://mt.nekostream.site/20d7d3bfc5543b4573cdc9bc825ce96a/master.m3u8` |

**CONFIRMED**: each audio type returns a **different m3u8 URL** (different hash) → different audio mix. Each has 1 subtitle track.

**Server resolve response confirmed**:
```json
{
  "status": 200,
  "result": {
    "url": "https://vidtube.site/stream/<base64-token>/sub",
    "skip_data": { "intro": [0, 0], "outro": [0, 0] }
  }
}
```
The `url` contains `vidtube.site` ✓ (passes the reference's host check). `skip_data` has intro/outro arrays (currently [0,0] — no skip data for this episode).

**Master m3u8 confirmed** (3 variants):
```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,NAME="360p"
119769_360.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720,NAME="720p"
119769_720.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5500000,RESOLUTION=1920x1080,NAME="1080p"
119769_1080.m3u8
```
The reference parses `BANDWIDTH` and `NAME` (extracting resolution from NAME via regex `(\d{3,4})`). The master also has `RESOLUTION=WxH` which is more accurate — we could parse that instead.

### Item 6 — PNG header on segments (confirmed, ~70 bytes)

Fetched a real segment (after following the 302 redirect — see item 7). The segment is a PNG-wrapped MPEG-TS:

```
Total size: 745,672 bytes
First 8 bytes (hex): 89 50 4e 47 0d 0a 1a 0a  ← PNG signature ✓
PNG magic (89 50 4E 47)? True ✓
IEND marker at offset: 62
Cut offset (IEND + 8): 70   ← ★ 70-byte PNG header (matches our documentation!)
Byte at cut (offset 70): 0x47  ← MPEG-TS sync byte ✓
Payload after PNG header: 745,602 bytes = exactly 3,966 × 188 (TS packets) ✓
First 8 bytes of payload (hex): 47 40 11 10 00 42 f0 25  ← valid TS packet header (PID 0x0011)
```

**The PNG header is exactly 70 bytes** (IEND at offset 62, cut at IEND+8=70). This matches our `EXTENSIONS/anikoto/MEMORY/sites/png-wrapping.md` documentation ("70-byte PNG header").

The reference's `stripPngHeader` algorithm (IEND+8 cut, then 0x47@188 alignment scan) correctly identifies the 70-byte header. The 188-byte alignment refinement may not always kick in (the first 0x47@cut is the real sync, but 0x47@cut+188 may not align if there are coincidental 0x47 bytes in the payload) — the lenient fallback (return data from IEND+8 as-is) works fine since the payload starts with a valid 0x47 sync byte.

**Both real AND ad segments are PNG-wrapped** (the ad segment from `p1.ipstatp.com` also starts with `89 50 4E 47`). The PNG wrapping is universal. Ad filtering happens at the playlist level (by host), before any segment fetch.

### Item 7 — Ad-segment discrimination (by CDN host)

The 720p variant m3u8 has **142 total segments**: 12 real + 130 ad.

```
Segment URL hosts:
    130 https://p1.ipstatp.com         ← ad segments (ByteDance ad CDN)
     12 https://mt.nekostream.site      ← real segments
```

- **Real segments**: `https://mt.nekostream.site/segment/<long-token>` — these 302-redirect (openresty/nginx) to the actual segment data (which may be on `p1.ipstatp.com` or another CDN). OkHttp follows the redirect automatically.
- **Ad segments**: `https://p1.ipstatp.com/obj/ad-site-i18n/<hash>` — direct URLs to the ByteDance ad CDN (no redirect).

**Ad discrimination = filter by CDN host** at the playlist parsing stage:
- Keep only `mt.nekostream.site/segment/...` URLs (real segments)
- Drop all `p1.ipstatp.com/...` URLs (ad segments)
- Alternative: drop any URL whose path contains `/ad-site-i18n/` or whose host is not `mt.nekostream.site`

This matches our `EXTENSIONS/anikoto/MEMORY/sites/cdn-waf.md` and `video-flow.md` documentation ("Filter out ad segments (anything not on the CDN host)").

⚠️ **Important**: the `mt.nekostream.site/segment/<token>` URLs **302-redirect** to the actual data. The reference's `LocalProxyServer.fetchSegmentWithRetry` uses OkHttp which follows redirects by default. curl needs `-L`. The `Content-Type` of the redirect response is `image/png` (the CDN disguises the TS as PNG).

## Bonus findings

1. **`data-tip` on catalog cards = animeId**: `<div class="ani poster tip" data-tip="7457">` — the `data-tip` value matches `#watch-main[data-id]="7457"`. Could be used as a shortcut to get the animeId without fetching the watch page (but the reference fetches the watch page anyway for details).

2. **Segment URLs 302-redirect**: the `mt.nekostream.site/segment/<token>` URL returns `302 Found` (openresty) with a `Location` header pointing to the actual segment data. OkHttp follows automatically; curl needs `-L`. Our `endpoints.md` already noted this (`GET (302)`).

3. **`Content-Type: image/png`** on segment responses — the CDN disguises MPEG-TS as PNG (consistent with the PNG header wrapping).

4. **Episode title location**: on `<li title="...">` and `<span class="d-title" data-jp="...">` inside the `<a>`, NOT on the `<a>` itself. The reference's `a.attr("title")` returns empty → fallback. Better selector: `a.selectFirst("span.d-title")` text.

5. **`skip_data`**: the server resolve endpoint returns `skip_data: {intro: [start, end], outro: [start, end]}` (Float seconds). Currently [0,0] for the test episode (no skip data). The reference captures but doesn't wire it to mpv — future enhancement.

## Impact on our implementation (Stage 4)

All 7 open items are resolved. The implementation plan in `MEMORY/decisions/03-best-method-to-build-extensions.md` and `02-video-pipeline-and-proxy.md` §7 is confirmed correct. No changes needed to the plan — just proceed with implementation:

1. **RC4 vrf**: implement `AnikotoRC4` (key `"simple-hash"`) — confirmed algorithm, server doesn't currently validate but implement for safety.
2. **Catalog endpoints**: use `/most-viewed` + `/latest-updated` (dedicated paths, match reference) OR `/filter?sort=...` (both work).
3. **Details selectors**: use `#w-info`-prefixed selectors (reference's superset).
4. **Episode parse**: use `a.selectFirst("span.d-title")` for the title (better than `a.attr("title")`).
5. **VidTube flow**: `type=sub|hsub|dub` confirmed — implement exactly as reference.
6. **LocalProxyServer**: implement `stripPngHeader` with IEND+8 algorithm (confirmed 70-byte header).
7. **Ad filtering**: filter by host in `parseVariantSegments` — keep `mt.nekostream.site`, drop everything else.
8. **Segment fetch**: OkHttp follows the 302 redirect automatically (no special handling needed).
