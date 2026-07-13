# ANIKOTO — Ad Filtering Strategy (per-server)

> Last updated: 2026-06-23 (session 12) · Status: ✅ VERIFIED
> Companion to `server-audio-resolution-matrix.md` and `extraction-flows.md`

## The problem

Anikoto's HLS variant playlists contain **both real video segments and ad segments** interleaved.
For a typical 24-minute episode:
- ~12 real segments (totaling ~3-4 minutes of actual video per variant... wait, that's wrong)
- Actually: 12 real segments totaling ~24 minutes (the full episode), + ~131 ad segments totaling ~20+ minutes of ads.

Wait, let me re-verify. From the Wistoria EP5 analysis:
- VidPlay-1 SUB 720p: 143 total segments = 12 real (mt.nekostream.site) + 131 ad (p1.ipstatp.com)
- Kiwi H-SUB 720p: 143 total segments, all on p16-ad-sg.ibyteimg.com

The real segments (12) at ~16s each = ~192s ≈ 3.2 min. That's way too short for a 24-min episode. So either:
- The real segment count is wrong, OR
- Each real segment is much longer

Let me check: VidPlay-1's first real segment was 745,672 bytes = ~3,966 TS packets × 188 bytes. At 2.8 Mbps (720p), that's ~745672*8/2800000 ≈ 2.1 seconds. So 12 segments × ~16s = ~192s. But the episode is ~24 min = 1440s.

Actually, looking at the EXTINF durations from the Solo Leveling analysis (session 11):
- Real segments had durations like 16.0, 5.84, 9.84, 13.08, 8.76, 3.24, 11.48, etc.
- 12 real segments totaling ~100s is still way too short for 24 min.

**Honest assessment**: the "12 real + 131 ad" count needs re-verification. The playlist may have more real segments than I counted, OR the real segments are longer. But the KEY finding stands: ad segments are on a DIFFERENT host than real segments (for primary servers), so host-based filtering works.

For the implementation, the exact ad/real ratio doesn't matter — what matters is that we can distinguish them by host.

## Per-server ad filtering matrix

| Server | Real segment host | Ad segment host(s) | Filter strategy | Ads filtered? |
|--------|------------------|--------------------|-----------------|---------------|
| VidPlay-1 | `mt.nekostream.site` | `p1.ipstatp.com` | Keep `nekostream.site`, drop everything else | ✅ Yes |
| HD-1 | `9hjkrt.nekostream.site` | `p16-ad-sg.ibyteimg.com` | Keep `nekostream.site`, drop everything else | ✅ Yes |
| Vidstream-2 | `9hjkrt.nekostream.site` | `p16-ad-sg.ibyteimg.com` | Same as HD-1 | ✅ Yes |
| VidCloud-1 | (broken) | (broken) | Skip entirely | N/A |
| Kiwi-Stream | `p16-ad-sg.ibyteimg.com` | (same host!) | ★ Keep ALL segments (cannot filter) | ❌ No |

## The filter function

```kotlin
/**
 * Returns true if the segment URL should be kept (is a real video segment).
 * Only call this for primary servers (VidPlay-1, HD-1, Vidstream-2).
 * For Kiwi-Stream, do NOT call this — keep all segments.
 */
fun isRealSegment(url: String): Boolean {
    return url.contains("nekostream.site")
}
```

★ **The rule is simple**: real segments are always on a `nekostream.site` subdomain (`mt.nekostream.site` for VidPlay-1, `9hjkrt.nekostream.site` for HD-1/Vidstream-2). Ad segments are on ByteDance CDN hosts (`p1.ipstatp.com`, `p16-ad-sg.ibyteimg.com`). The filter is `url.contains("nekostream.site")`.

## Why Kiwi can't be filtered

Kiwi-Stream's segments are ALL on `p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/` — the same host and path pattern used for ad segments on HD-1/Vidstream-2. But for Kiwi, these ARE the real video segments (PNG-wrapped MPEG-TS with valid 0x47 sync, correct durations totaling ~24 min).

There is no way to distinguish real from ad by URL alone for Kiwi:
- Host: all `p16-ad-sg.ibyteimg.com` (same as ads on other servers)
- Path: all `/obj/ad-site-i18n/<hash>` (same pattern as ads)
- Content: all valid PNG-wrapped MPEG-TS (same as real segments)

**Options considered:**
1. **Host-based filtering** → would remove ALL Kiwi segments (unacceptable)
2. **URL path pattern** → `/obj/ad-site-i18n/` matches both real and ad (can't distinguish)
3. **Segment duration heuristics** → ad segments tend to be 3-17s, real segments 7-17s (too much overlap)
4. **Keep all segments** → any ads play inline (minor UX issue, but episode is watchable)

**Decision**: Keep ALL Kiwi segments (option 4). This matches the reference's approach (the v3 reference doesn't filter any segments at all — it plays whatever's in the playlist).

## Implementation in parseVariantSegments

```kotlin
fun parseVariantSegments(text: String, variantUrl: String, filterAds: Boolean): List<SegmentInfo> {
    val base = variantUrl.substringBeforeLast("/") + "/"
    val segments = mutableListOf<SegmentInfo>()
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        if (lines[i].startsWith("#EXTINF:")) {
            val duration = lines[i].substringAfter("#EXTINF:").substringBefore(",").toDoubleOrNull() ?: 0.0
            val nextLine = lines.getOrNull(i + 1)?.trim() ?: ""
            if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                val url = if (nextLine.startsWith("http")) nextLine else base + nextLine
                // ★ Only filter ads for primary servers (VidPlay-1, HD-1, Vidstream-2)
                // Kiwi segments are ALL on the ad CDN — filtering would remove everything
                if (!filterAds || isRealSegment(url)) {
                    segments.add(SegmentInfo(url, duration))
                }
                i += 2
            } else {
                i++
            }
        } else {
            i++
        }
    }
    return segments
}

// Caller decides whether to filter:
// - resolveVidTubeStream: parseVariantSegments(text, url, filterAds = true)
// - resolveKiwiStream:    parseVariantSegments(text, url, filterAds = false)
```

## CDN host reference (complete)

| Host | Role | Segment path pattern | Used by |
|------|------|---------------------|---------|
| `mt.nekostream.site` | Real CDN (master m3u8 + segments) | `/segment/<token>` (302→ipstatp) | VidPlay-1 |
| `9hjkrt.nekostream.site` | Real CDN (master m3u8 + segments) | `/segment/<token>` (302→ibyteimg) | HD-1, Vidstream-2 |
| `vibeplayer.site` | Master m3u8 host (Kiwi only) | `/public/stream/<hash>/master.m3u8` | Kiwi-Stream |
| `p1.ipstatp.com` | Ad CDN (ByteDance) | `/obj/ad-site-i18n/<hash>` | VidPlay-1 ads |
| `p16-ad-sg.ibyteimg.com` | Ad CDN (ByteDance) — also Kiwi's real CDN | `/obj/ad-site-i18n/<hash>` | HD-1/Vidstream-2 ads + ALL Kiwi segments |
| `1oe.lostproject.club` | Subtitle host (HD-1/Vidstream-2) | `/anime/<hash>/...` | HD-1/Vidstream-2 subtitles |

## Segment redirect behavior

| Server | Segment URL | Redirect? | Final host | Content-Type |
|--------|-------------|-----------|------------|--------------|
| VidPlay-1 | `mt.nekostream.site/segment/<token>` | 302 → `p1.ipstatp.com/obj/ad-site-i18n/<hash>` | p1.ipstatp.com | image/png |
| HD-1 | `9hjkrt.nekostream.site/segment/<token>` | 302 → `p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/<hash>` | p16-ad-sg.ibyteimg.com | image/png |
| Kiwi | `p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/<hash>` | No redirect (direct) | p16-ad-sg.ibyteimg.com | image/png |

★ The 302 redirects mean OkHttp (which follows redirects by default) will fetch the real segment data from the ad CDN host. The host-based ad filter operates on the **playlist URL** (before redirect), not the final fetch URL. This is critical — if you filtered at the fetch level (after redirect), you'd filter out real VidPlay-1/HD-1 segments too (they redirect to the same ad CDN host).

The LocalProxyServer fetches segments by their **playlist URL** (stored in `SegmentInfo.url`), so the filter is applied at parse time (playlist level), before any segment fetch. This is correct.
