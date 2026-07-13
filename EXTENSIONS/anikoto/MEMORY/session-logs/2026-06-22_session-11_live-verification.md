# Session 11 — Live Verification of the 7 Open Items

> Date: 2026-06-22 · Session #: 11 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Verify the 7 open live-verification items identified in session 10's `05-cross-check-lessons.md` §6,
against the live site `https://anikototv.to`. These were the prerequisites for finalizing our
Stage 4 (video extraction) implementation.

## What was done

### Method
Used curl + python3 (for the RC4 vrf implementation) to test each item directly against the live
site. Test anime: Solo Leveling Season 2 (`solo-leveling-season-2-arise-from-the-shadow-3eukp`,
animeId=`7457`). Followed the full video chain: catalog → watch page → episode list → server list →
server resolve → VidTube iframe → getSourcesNew → master m3u8 → variant m3u8 → segment fetch.

### Items 1 & 2 — Popular + Latest endpoints
- `/most-viewed?page=1` → 40 items, 115487 bytes (Solo Leveling S2, One Piece, ...)
- `/filter?sort=most-viewed&page=1` → 40 items, 115470 bytes (identical items)
- `/latest-updated?page=1` → 40 items, 112750 bytes (Farming Life S2, Nippon Sangoku, ...)
- `/filter?sort=latest-updated&page=1` → 40 items, 112717 bytes (identical items)
- **Result**: both forms work and return identical content (the dedicated paths are SEO-friendly
  aliases). The only diff is `<title>`/`<meta>` tags. Either form is correct.
- **Catalog selectors confirmed**: `div#list-items > div.item` + `a.name.d-title` + `div.ani.poster.tip img` + `data-jp` attr + `ul.pagination` with `rel="next"`.

### Item 3 — Details-page selectors
- Fetched `/watch/solo-leveling-season-2-arise-from-the-shadow-3eukp/ep-1` (81889 bytes).
- **All selectors coexist**: `#w-info` (outermost) > `.binfo` > `.poster img` + `.info` > `h1.title.d-title[data-jp]` + `.synopsis.mb-3 > .shorting > .content` + `.bmeta`. Also `.brating` elsewhere.
- **Result**: the reference's `#w-info`-prefixed selectors are a superset of our `.binfo`/`.bmeta`/`.brating`. Adopt the reference's selectors.
- Note: `.synopsis` class is actually `class="synopsis mb-3"` but CSS `.synopsis` matches via whitespace-split.

### Item 4 — vrf param (RC4 "simple-hash")
- Implemented RC4 in Python (textbook KSA+PRGA, key `"simple-hash"`, Base64.NO_WRAP, ISO_8859_1 bytes) — matching the reference's `AnikotoRC4` exactly.
- For animeId `7457`: vrf = `INCt0g==` (base64) → URL-encoded `INCt0g%3D%3D`.
- Tested `/ajax/episode/list/7457?vrf=...&style=default` with 3 cases:
  - Correct RC4 vrf → `status:200`, 9949 bytes
  - No vrf param → `status:200`, 9949 bytes (identical)
  - Bogus vrf → `status:200`, 9949 bytes (identical)
- **Result**: the server does NOT validate the vrf param. Our session-08 build (which skipped vrf) would actually work. But implement RC4 anyway (~15 LOC) to match the reference and be safe against future validation.
- **Episode HTML structure confirmed**: `<ul class="ep-range"><li title="..."><a data-id data-num data-slug data-mal data-timestamp data-sub data-dub data-ids><b>{n}</b><span class="d-title" data-jp="...">{title}</span></a></li>`.
- Minor note: the episode title is on `<li title>` and `<span class="d-title">`, NOT on the `<a>` itself. Use `a.selectFirst("span.d-title")` text (better than the reference's `a.attr("title")`).

### Item 5 — VidTube getSourcesNew type param
- Extracted `data-ids` from episode 1 → called `/ajax/server/list` → got 3 groups (sub/hsub/dub) × servers (VidPlay-1, HD-1, Vidstream-2, VidCloud-1).
- Resolved VidPlay-1 (sub) → iframe `https://vidtube.site/stream/<token>/sub` → extracted `data-id="7509"`.
- Tested `getSourcesNew?id=7509&type={sub,hsub,dub}`:
  - sub → `mt.nekostream.site/6ac061d5.../master.m3u8`
  - hsub → `mt.nekostream.site/9cd4c4d4.../master.m3u8`
  - dub → `mt.nekostream.site/20d7d3bf.../master.m3u8`
- **Result**: CONFIRMED — each audio type returns a different m3u8 URL (different hash). Each has 1 subtitle track.
- Master m3u8 has 3 variants (360p/720p/1080p with BANDWIDTH + RESOLUTION + NAME).

### Item 6 — PNG header on segments
- Fetched the 720p variant m3u8 → 142 segments (12 real on `mt.nekostream.site`, 130 ad on `p1.ipstatp.com`).
- Fetched a real segment (after following the 302 redirect — see item 7):
  - First 8 bytes: `89 50 4e 47 0d 0a 1a 0a` (PNG signature) ✓
  - IEND at offset 62, cut at IEND+8 = 70 → **70-byte PNG header** (matches our documentation)
  - Byte at offset 70: `0x47` (MPEG-TS sync byte) ✓
  - Payload: 745,602 bytes = exactly 3,966 × 188 (TS packets) ✓
- **Result**: CONFIRMED. The reference's `stripPngHeader` (IEND+8 algorithm) correctly strips the 70-byte header.
- **Bonus**: the ad segment (from `p1.ipstatp.com`) ALSO has a PNG header — wrapping is universal. Ad filtering happens at the playlist level (by host), before any segment fetch.

### Item 7 — Ad-segment discrimination
- 720p variant m3u8: 142 total segments = 12 real + 130 ad.
- Real: `https://mt.nekostream.site/segment/<long-token>` (302-redirects to actual data on `p1.ipstatp.com`)
- Ad: `https://p1.ipstatp.com/obj/ad-site-i18n/<hash>` (direct, ByteDance ad CDN)
- **Result**: CONFIRMED — ad discrimination is by CDN host. Keep `mt.nekostream.site`, drop `p1.ipstatp.com`. The reference's `parseVariantSegments` filters at the playlist level.
- The `mt.nekostream.site/segment/<token>` URLs 302-redirect (openresty); OkHttp follows automatically, curl needs `-L`. Content-Type is `image/png` (disguise).

## Key findings / decisions

1. **All 7 items resolved.** The reference APK's behavior is confirmed against the current live site. Our `MEMORY/sites/anikoto/` research was already correct; the reference confirms + refines it.
2. **The vrf param is not currently validated** by the server. This means our session-08 build would work for episode-list without vrf. But implement it for safety (~15 LOC).
3. **Both real and ad segments are PNG-wrapped.** The wrapping is universal. Ad filtering is by CDN host at the playlist level, not at the segment-fetch level.
4. **Real segment URLs 302-redirect** to the actual data (possibly on `p1.ipstatp.com`, the same host as direct ad URLs). OkHttp follows automatically. The host-based ad filter works because it operates on the playlist URLs (before redirect), not the final fetch URLs.
5. **The episode title is NOT on the `<a>` element** — it's on `<li title>` and `<span class="d-title">`. Use `a.selectFirst("span.d-title")` text (better than the reference's `a.attr("title")`).
6. **`data-tip` on catalog cards = animeId** — could be used as a shortcut, but the reference fetches the watch page anyway.

## Files created / changed

- `MEMORY/research/apk-reference/06-live-verification-results.md` — NEW (full verification results)
- `MEMORY/research/apk-reference/05-cross-check-lessons.md` — updated §6 (all items marked resolved)
- `MEMORY/research/apk-reference/README.md` — updated (file 06 added)
- `MEMORY/sites/anikoto/endpoints.md` — updated (popular/latest aliases confirmed, vrf non-validation noted, selectors confirmed)
- `MEMORY/sites/anikoto/png-wrapping.md` — updated (session-11 confirmation + reference algorithm note)
- `MEMORY/session-logs/2026-06-22_session-11_live-verification.md` — this log

## Status at end of session

- ✅ All 7 open live-verification items resolved against `https://anikototv.to`.
- ✅ Catalog endpoints (popular/latest/search) confirmed — both dedicated paths and `/filter?sort=...` work.
- ✅ Details-page selectors confirmed — `#w-info`-prefixed selectors adopted.
- ✅ RC4 vrf algorithm confirmed (key `"simple-hash"`) — server doesn't validate but implement for safety.
- ✅ VidTube `type` param confirmed — sub/hsub/dub return different m3u8 URLs.
- ✅ PNG header confirmed — 70 bytes (IEND@62, cut@70), MPEG-TS 0x47 sync follows.
- ✅ Ad discrimination confirmed — by CDN host (`mt.nekostream.site`=real, `p1.ipstatp.com`=ad).
- ✅ Stage 4 (video extraction) is now **unblocked** — the full implementation plan in ADR 03 + `02-video-pipeline-and-proxy.md` §7 is confirmed correct.

## Next steps (resume point)

**Stage 4 implementation** — follow ADR 03 (`MEMORY/decisions/03-best-method-to-build-extensions.md`) points 5-10:
1. Upgrade session-08 `Anikoto.kt`: implement `AnikotoRC4`, upgrade `SEpisode.url` to `EpisodeMeta` pipe-encoding, add `Source` base class, split two clients.
2. Implement the Hoster flow: `getHosterList` (discovery + parallel resolution + VidTube extractor).
3. Implement `LocalProxyServer`: index-based URL scheme, build-from-scratch m3u8, `stripPngHeader` (IEND+8), LRU cache, prefetch, idle auto-stop.
4. Build `Video` objects (`initialized=true`), sort + prefer, return `Hoster.toHosterList`.
5. Build debug APK, user tests in Aniyomi, iterate (one fix at a time per rule §2).

## Honest notes

- **The live verification was efficient** — curl + python3 (for RC4) was sufficient for all 7 items. No need for a full headless browser (agent-browser) since the site has no JS challenge on the endpoints we tested. The reference's `noCloudflareClient` approach (clean OkHttpClient with proper headers) works fine.
- **The most surprising finding** was that the vrf param is not validated. This means the site either (a) never validated it, (b) removed validation, or (c) validates only for certain edge cases. Either way, implementing it is the safe choice.
- **The second surprising finding** was that real segment URLs 302-redirect to `p1.ipstatp.com` (the same host as direct ad URLs). This means the host-based ad filter works at the playlist level (before redirect), not at the fetch level. If someone mistakenly filtered at the fetch level (after redirect), they'd filter out real segments too. The reference correctly filters at the playlist level.
- **No code was written this session** — pure verification. But the findings unblock Stage 4 and confirm our implementation plan is correct. The next session can proceed directly to implementation.
