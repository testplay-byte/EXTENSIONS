# ANIKOTO — Server / Audio / Resolution Matrix (VERIFIED)

> Last updated: 2026-06-23 (session 12) · Status: ✅ VERIFIED by live Python analysis
> Test URL: `https://anikototv.to/watch/wistoria-wand-and-sword-season-2-dua04/ep-5`
> Test anime: Wistoria: Wand and Sword S2 (animeId=8737, malId=59983)
> Script: `python-prototypes/analyze-full-chain-v2.py` (full JSON at `/tmp/anikoto-chain-analysis-v2.json`)

## The complete verified matrix

| # | Server | Audio | Source | Player Host | Data-ID | Variants | Subtitles | Status |
|---|--------|-------|--------|-------------|---------|----------|-----------|--------|
| 1 | VidPlay-1 | SUB | primary | vidtube.site | 138029 | 360p/720p/1080p | 1 (English) | ✅ ok |
| 2 | HD-1 | SUB | primary | megaplay.buzz (s-5) | 176012 | 360p/720p/1080p | 1 (English) | ✅ ok |
| 3 | Vidstream-2 | SUB | primary | megaplay.buzz (s-2) | 176012 | 360p/720p/1080p | 1 (English) | ✅ ok |
| 4 | VidCloud-1 | SUB | primary | vidwish.live | 174447 | — | — | ❌ getSourcesNew returns "Error - Vidcloud" HTML |
| 5 | VidPlay-1 | HSUB | primary | vidtube.site | 138029 | 360p/720p/1080p | 1 (English) | ✅ ok |
| 6 | HD-1 | HSUB | primary | megaplay.buzz (s-5) | 176261 | 1080p/720p/360p | 0 | ✅ ok |
| 7 | Vidstream-2 | HSUB | primary | megaplay.buzz (s-2) | 176261 | 1080p/720p/360p | 0 | ✅ ok |
| 8 | VidPlay-1 | DUB | primary | vidtube.site | 138029 | 360p/720p/1080p | 1 (English) | ✅ ok |
| 9 | HD-1 | DUB | primary | megaplay.buzz (s-5) | 176502 | 360p/720p/1080p | 1 (English) | ✅ ok |
| 10 | Vidstream-2 | DUB | primary | megaplay.buzz (s-2) | 176502 | 360p/720p/1080p | 1 (English) | ✅ ok |
| 11 | VidCloud-1 | DUB | primary | vidwish.live | 174706 | — | — | ❌ getSourcesNew returns "Error - Vidcloud" HTML |
| 12 | Kiwi-Stream | H-SUB | mapper | mewcdn.online → vibeplayer.site | (base64 fragment) | 360p/720p/1080p | 0 | ✅ ok |
| 13 | Kiwi-Stream | A-DUB | mapper | mewcdn.online → vibeplayer.site | (base64 fragment) | 360p/720p/1080p | 0 | ✅ ok |

**Totals**: 13 combos tested → **11 ok, 2 broken (VidCloud-1)**. No VidCloud-1 HSUB exists (not in the server list's `hsub` group).

## Per-server summary (matches your description)

### VidPlay-1 ✅ (vidtube.site)
- **Audio types**: SUB, HSUB, DUB (all 3) ✓
- **Resolutions**: 1080p, 720p, 360p for all 3 audio types ✓
- **Data-ID pattern**: ONE data-id (138029) for ALL 3 audio types — the `type` param in `getSourcesNew` differentiates the audio
- **Subtitle tracks**: 1 English (captions) for all 3 audio types (even HSUB — unusual but confirmed)
- **CDN**: `mt.nekostream.site` (real segments) + `p1.ipstatp.com` (ad segments)
- **Extraction flow**: Primary (iframe → data-id → getSourcesNew → m3u8)
- **Note**: This is the ONLY server the reference APKs support (they check `iframeUrl.contains("vidtube.site")`)

### HD-1 ✅ (megaplay.buzz, path prefix `s-5`)
- **Audio types**: SUB, HSUB, DUB (all 3) ✓
- **Resolutions**: 1080p, 720p, 360p for all 3 audio types ✓
- **Data-ID pattern**: DIFFERENT data-id per audio type (176012=sub, 176261=hsub, 176502=dub)
- **Subtitle tracks**: 1 English (captions) for SUB + DUB; **0 for HSUB** (hardsub burns subs in)
- **CDN**: `9hjkrt.nekostream.site` (real segments) + `p16-ad-sg.ibyteimg.com` (ad segments)
- **HSUB bandwidth difference**: HSUB uses a DIFFERENT encode (lower bitrate: 300000/732631/1624859 vs 800000/2800000/5500000 for SUB/DUB)
- **Extraction flow**: Primary
- **Token sharing**: ≡ Vidstream-2 (identical data-ids + m3u8 URLs for all 3 audio types)

### Vidstream-2 ✅ (megaplay.buzz, path prefix `s-2`)
- **Audio types**: SUB, HSUB, DUB (all 3) ✓
- **Resolutions**: 1080p, 720p, 360p for all 3 audio types ✓
- **Data-ID pattern**: SAME as HD-1 (176012/176261/176502) — token sharing confirmed
- **Subtitle tracks**: Same as HD-1 (1 for SUB/DUB, 0 for HSUB)
- **CDN**: Same as HD-1 (`9hjkrt.nekostream.site` + `p16-ad-sg.ibyteimg.com`)
- **Extraction flow**: Primary
- **★ Dedup candidate**: Identical to HD-1 in every way except the megaplay.buzz path prefix (`s-2` vs `s-5`). Both resolve to the same data-id → same m3u8. Keeping both would produce duplicate videos.

### VidCloud-1 ❌ (vidwish.live) — BROKEN
- **Audio types in server list**: SUB, DUB (NO HSUB — not in the `hsub` group, matches your description) ✓
- **Status**: BROKEN — `getSourcesNew` on `vidwish.live` returns an HTML "Error - Vidcloud" page instead of JSON. Both SUB and DUB fail.
- **The iframe page DOES load** (data-id 174447 for SUB, 174706 for DUB are extractable), and the page has extras (`data-realid`, `data-mediaid`, `data-fileversion`, `cid`). But the API call fails.
- **Extraction flow**: Would be Primary (same as VidPlay-1/HD-1) but the API is broken.
- **Action**: Skip VidCloud-1 entirely in our extension. The reference APKs also skip it (via the `vidtube.site` host check — vidwish.live doesn't contain "vidtube.site").

### Kiwi-Stream ✅ (mewcdn.online → vibeplayer.site) — SPECIAL
- **Audio types**: H-SUB (mapper "sub") + A-DUB (mapper "dub") only — matches your description ✓
- **Resolutions**: 1080p, 720p, 360p for both audio types ✓
- **★ NOT in the primary server list** — comes from the **mapper.nekostream.site API** (`GET /api/mal/<malId>/<epNum>/<timestamp>`)
- **Mapper response**: `{"Kiwi-Stream-": {"sub": {"url": "<base64-token>"}, "dub": {"url": "<base64-token>"}}, "status": {"serves_from": "cache", ...}}`
- **Mapper "sub" = H-SUB, "dub" = A-DUB** (per your clarification — Kiwi's labels are different from the primary servers but the content is the same: hardsub and dubbed audio)
- **★ Different extraction flow**: NO `data-id`, NO `getSourcesNew` call. The iframe URL is `https://mewcdn.online/player/plyr.php#<base64-encoded-m3u8-url>`. Decode the base64 fragment after `#` → direct m3u8 URL on `vibeplayer.site/public/stream/<hash>/master.m3u8`.
- **CDN**: `vibeplayer.site` (master m3u8) → `p16-ad-sg.ibyteimg.com` (ALL segments — see ad-filtering note)
- **Subtitle tracks**: 0 (no getSourcesNew call → no tracks returned)
- **PNG wrapping**: ✅ confirmed (70-byte PNG header, IEND@62, cut@70, 0x47 TS sync) — same as all other servers
- **★ Reference APKs do NOT support Kiwi**: the `iframeUrl.contains("vidtube.site")` check rejects `mewcdn.online`. The v3 reference DOES call the mapper API but then rejects the Kiwi iframe. The v16.4 reference dropped the mapper API entirely.

## Audio type labels — the clarification

| Source | `data-type` / mapper key | Our display label | Content |
|--------|--------------------------|-------------------|---------|
| Primary server list | `sub` | **SUB** | Subtitled (Japanese audio + English subtitles track) |
| Primary server list | `hsub` | **HSUB** | Hardsub (subtitles burned into video, no subtitle track on HD-1/Vidstream-2; VidPlay-1 still has a track) |
| Primary server list | `dub` | **DUB** | Dubbed (English audio + English captions track) |
| Mapper (Kiwi) | `sub` | **H-SUB** | Same as HSUB (hardsub) — Kiwi just labels it differently |
| Mapper (Kiwi) | `dub` | **A-DUB** | Same as DUB (dubbed audio) — Kiwi just labels it differently |

★ **Per your clarification**: H-SUB ≡ HSUB, A-DUB ≡ DUB. The content is identical; only the label differs. You use "H-SUB" for all hardsub variants. Our extension should normalize these to a single label set (SUB/HSUB/DUB) to avoid confusing the user with 5 labels for 3 audio types.

## Resolution inventory (all 3 for every working server × audio)

| Quality | Bandwidth (SUB/DUB) | Bandwidth (HSUB on HD-1/Vidstream-2) | Resolution |
|---------|--------------------|---------------------------------------|------------|
| 1080p | 5,500,000 bps | 1,624,859 bps | 1920×1080 |
| 720p | 2,800,000 bps | 732,631 bps | 1280×720 |
| 360p | 800,000 bps | 300,000 bps | 640×360 |

★ **HSUB on HD-1/Vidstream-2 uses a DIFFERENT encode** (lower bitrate) — likely because hardsub burns subtitles into the video, requiring a re-encode at lower quality. VidPlay-1's HSUB uses the SAME bandwidth as SUB/DUB (different audio mix, same video encode).

## Data-ID patterns (the "safe approach")

Two distinct patterns confirmed:

### Pattern A: VidPlay-1 — ONE data-id for ALL audio types
- `data-id = 138029` for SUB, HSUB, and DUB
- The `type=sub|hsub|dub` param in `getSourcesNew` differentiates the audio
- **Implication**: only ONE iframe fetch needed; then 3 `getSourcesNew` calls with different `type` values
- This is the "safe approach" the user mentioned — fewer HTTP round-trips

### Pattern B: HD-1 / Vidstream-2 — DIFFERENT data-id per audio type
- `data-id = 176012` (SUB), `176261` (HSUB), `176502` (DUB)
- Each audio type has its own iframe page with its own data-id
- **Implication**: 3 iframe fetches needed (one per audio type), then 3 `getSourcesNew` calls
- HD-1 and Vidstream-2 share the SAME data-ids (token sharing — see below)

### Pattern C: Kiwi-Stream — NO data-id (base64 fragment)
- The iframe URL itself contains the m3u8 URL (base64-encoded in the URL fragment after `#`)
- NO `getSourcesNew` call needed — decode the fragment → direct m3u8 URL
- **Implication**: simplest flow (1 resolve + 1 base64 decode + 1 master m3u8 fetch)

## Token sharing (HD-1 ≡ Vidstream-2)

**CONFIRMED**: HD-1 and Vidstream-2 are the same stream with different display names.

| Audio | HD-1 data-id | Vidstream-2 data-id | Same? | HD-1 m3u8 | Vidstream-2 m3u8 | Same? |
|-------|-------------|--------------------|----|-----------|-------------------|-------|
| SUB | 176012 | 176012 | ✓ | `9hjkrt.nekostream.site/4739d8.../31fcc9a2...` | identical | ✓ |
| HSUB | 176261 | 176261 | ✓ | `9hjkrt.nekostream.site/4739d8.../e452e498...` | identical | ✓ |
| DUB | 176502 | 176502 | ✓ | `9hjkrt.nekostream.site/4739d8.../e833e7bf...` | identical | ✓ |

The only difference: the megaplay.buzz path prefix (`s-5` for HD-1, `s-2` for Vidstream-2) — but both resolve to the same data-id on megaplay.buzz, which returns the same m3u8 URL.

**★ Dedup strategy**: Keep only ONE of HD-1/Vidstream-2 in our extension. Recommendation: keep HD-1 (more common name) and drop Vidstream-2, OR keep both but label them clearly and let the user choose (the reference keeps both without dedup). Per ADR 03, we'll dedup by master-m3u8 URL to produce a cleaner quality sheet.

## PNG wrapping (universal)

**CONFIRMED on all working servers**: 70-byte PNG header before the real MPEG-TS data.

| Server | Segment host | PNG magic | IEND offset | Cut offset (IEND+8) | 0x47 TS sync at cut | Content-Type |
|--------|-------------|-----------|-------------|--------------------|--------------------|--------------|
| VidPlay-1 | mt.nekostream.site → (302) → p1.ipstatp.com | ✓ `89 50 4E 47` | 62 | 70 | ✓ | image/png |
| HD-1 | 9hjkrt.nekostream.site → (302) → p16-ad-sg.ibyteimg.com | ✓ `89 50 4E 47` | 62 | 70 | ✓ | image/png |
| Kiwi-Stream | p16-ad-sg.ibyteimg.com (direct, no redirect) | ✓ `89 50 4E 47` | 62 | 70 | ✓ | image/png |

The `stripPngHeader` algorithm (find IEND, cut at IEND+8, verify 0x47@cut) works universally. The 70-byte header is consistent across all servers and audio types.

## CDN / WAF behavior

### CDN hosts (3 distinct)
| CDN host | Role | Servers using it |
|----------|------|-----------------|
| `mt.nekostream.site` | Master m3u8 + real segments | VidPlay-1 |
| `9hjkrt.nekostream.site` | Master m3u8 + real segments | HD-1, Vidstream-2 |
| `vibeplayer.site` | Master m3u8 (direct) | Kiwi-Stream |

### Ad CDN hosts (2 distinct — ByteDance ad network)
| Ad CDN host | Servers using it for ads |
|-------------|------------------------|
| `p1.ipstatp.com` | VidPlay-1 |
| `p16-ad-sg.ibyteimg.com` | HD-1, Vidstream-2, AND Kiwi-Stream (ALL Kiwi segments!) |

### WAF / redirect behavior
- `mt.nekostream.site/segment/<token>` → **302 redirect** to `p1.ipstatp.com/obj/ad-site-i18n/<hash>` (openresty/nginx). OkHttp follows automatically.
- `9hjkrt.nekostream.site/segment/<token>` → **302 redirect** to `p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/<hash>`.
- Kiwi segments: NO redirect — direct fetch from `p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/<hash>`.
- Content-Type is always `image/png` (the CDN disguises MPEG-TS as PNG — consistent with the PNG header wrapping).
- No `cf_clearance` cookie needed. No anti-bot challenge on any endpoint with a normal browser UA.

## Ad filtering strategy (per-server)

**CRITICAL**: ad filtering by CDN host works for VidPlay-1/HD-1/Vidstream-2 but **NOT for Kiwi**.

| Server | Real segment host | Ad segment host | Filter strategy |
|--------|------------------|----------------|----------------|
| VidPlay-1 | mt.nekostream.site | p1.ipstatp.com | Keep `mt.nekostream.site`, drop `p1.ipstatp.com` |
| HD-1 | 9hjkrt.nekostream.site | p16-ad-sg.ibyteimg.com | Keep `9hjkrt.nekostream.site`, drop `p16-ad-sg.ibyteimg.com` |
| Vidstream-2 | 9hjkrt.nekostream.site | p16-ad-sg.ibyteimg.com | Same as HD-1 |
| Kiwi-Stream | p16-ad-sg.ibyteimg.com | (ALL segments on this host!) | ★ CANNOT filter by host — keep ALL segments |

### Kiwi ad situation (honest assessment)
- All 143 Kiwi segments are on `p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/` — the SAME host + path pattern used for ad segments on other servers.
- But the segments ARE valid PNG-wrapped MPEG-TS (0x47 TS sync at offset 70, correct durations ~7-15s, total ~24 min = correct episode length).
- **Cannot distinguish real from ad by host or URL pattern** — they're identical.
- **Options**: (a) Keep ALL Kiwi segments (if some are ads, they'll play — minor UX issue), (b) Use segment-duration heuristics (ads tend to be shorter, but overlap is too large for reliable filtering), (c) Accept that Kiwi may have unfiltered ads.
- **Recommendation**: Keep ALL Kiwi segments (option a). The episode plays correctly; any ads are short. This matches the reference's approach (no filtering).

### VidCloud-1 (broken — no filtering needed)
- `getSourcesNew` returns an HTML error page — no m3u8 to parse, no segments to filter.
- Skip entirely.

## Skip data (intro/outro)

All server-resolve responses include `skip_data: {intro: [start, end], outro: [start, end]}`.
- For this test episode: `intro: [0, 0]`, `outro: [0, 0]` — no skip data available.
- The reference captures but doesn't wire it to mpv. We'll do the same (future enhancement).

## Reference APK comparison (per your note: "they only support vidplay")

| Aspect | v3 reference | v16.4 reference | Our extension (planned) |
|--------|-------------|-----------------|------------------------|
| Primary server list | ✓ uses it | ✓ uses it | ✓ uses it |
| Mapper API (Kiwi) | ✓ calls it, but rejects Kiwi iframe (`!contains("vidtube.site")`) | ❌ dropped entirely | ✓ will use it (accept mewcdn.online) |
| VidPlay-1 | ✓ works | ✓ works | ✓ will support |
| HD-1 | ✗ rejected (megaplay.buzz ≠ vidtube.site) | ✗ rejected | ✓ will support |
| Vidstream-2 | ✗ rejected | ✗ rejected | ✓ will support (dedup with HD-1) |
| VidCloud-1 | ✗ rejected (vidwish.live ≠ vidtube.site) | ✗ rejected | ✗ skip (broken) |
| Kiwi-Stream | ✗ rejected (mewcdn.online ≠ vidtube.site) | ✗ not even fetched | ✓ will support (base64 fragment flow) |

★ **Our extension will be MORE complete than both references** — supporting 4 working servers (VidPlay-1, HD-1, Vidstream-2, Kiwi-Stream) × 3 audio types, vs the references' 1 server (VidPlay-1) × 3 audio types. This requires:
1. Accepting multiple player hosts (vidtube.site, megaplay.buzz, mewcdn.online) — not just `vidtube.site`
2. Implementing the Kiwi base64-fragment extraction flow (different from the primary getSourcesNew flow)
3. Calling the mapper.nekostream.site API for Kiwi discovery
4. Per-server ad filtering (host-based for primary, no filtering for Kiwi)
5. Deduping HD-1/Vidstream-2

## Python prototype scripts

- `python-prototypes/analyze-full-chain.py` — v1 (broken parser, primary servers only)
- `python-prototypes/analyze-full-chain-v2.py` — ★ v2 (fixed parser + Kiwi mapper path, all 5 servers)
- Full JSON output: `/tmp/anikoto-chain-analysis-v2.json` (reproducible by re-running the script)

## Honest notes

1. **The m3u8 URLs are time-limited** — re-fetching the same VidPlay-1 master m3u8 URL a few minutes later sometimes returned 0 segments (the variant file names changed or the token expired). Our extension must fetch the m3u8 fresh each time `getHosterList` is called (no long-term caching of m3u8 URLs).

2. **VidCloud-1's failure mode is definitive** — the `getSourcesNew` endpoint on `vidwish.live` returns a full HTML "Error - Vidcloud" page (not a JSON error). This is a server-side issue, not a client-side header/cookie problem. We tested with the exact same headers that work for vidtube.site/megaplay.buzz.

3. **Kiwi's mapper API response includes a cache hint**: `"status": {"serves_from": "cache", "cache_expires_in": "1 hours 33 minutes"}`. The mapper caches aggressively — re-calling it returns the same tokens. Our extension can cache the mapper response for ~1 hour.

4. **The `server` field in getSourcesNew is always `5`** — meaning unknown (possibly an internal server ID at the CDN level). Not useful for our extension.

5. **VidPlay-1's HSUB has subtitles** (1 English captions track) while HD-1/Vidstream-2's HSUB has 0 tracks. This is because VidPlay-1's HSUB is a different audio mix (same video encode, same subtitles) while HD-1's HSUB is a re-encoded hardsub (subs burned in, no separate track needed). Our extension should still offer the subtitle track when available.

6. **All 13 combos were tested with real HTTP requests** — no mocking, no guessing. The JSON at `/tmp/anikoto-chain-analysis-v2.json` contains the full raw data for every step of every chain.
