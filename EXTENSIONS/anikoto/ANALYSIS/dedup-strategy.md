# ANIKOTO — Dedup Strategy

> Last updated: 2026-06-23 (session 12) · Status: ✅ DECIDED
> Companion to `tokens-and-dedup.md` (MEMORY) and `server-audio-resolution-matrix.md`

## The dedup situation

| Pair | Same data-id? | Same m3u8? | Dedup needed? |
|------|---------------|------------|---------------|
| VidPlay-1 SUB vs HSUB vs DUB | ✓ same (138029) | ✗ different hashes | ❌ No — genuinely different audio streams |
| HD-1 SUB vs HSUB vs DUB | ✗ different (176012/176261/176502) | ✗ different | ❌ No — genuinely different |
| Vidstream-2 SUB vs HSUB vs DUB | ✗ different (shares HD-1's IDs) | ✗ different | ❌ No — genuinely different |
| **HD-1 vs Vidstream-2** (same audio) | ✓ identical | ✓ identical | ⚠️ Yes — duplicate stream |

## Decision: keep both HD-1 and Vidstream-2 (no dedup)

**Rationale:**
1. They're identical streams (same data-ids, same m3u8 URLs) — keeping both produces duplicate Videos.
2. BUT the megaplay.buzz path differs (`s-5` vs `s-2`) — if one path breaks, the other may still work (resilience).
3. The site's own UI shows both as separate server options.
4. The reference APKs also keep both (they just reject both via the `vidtube.site` check).
5. The duplicate is a minor UX issue (user sees "HD-1 - 1080p" and "Vidstream-2 - 1080p" next to each other), not a correctness issue.

## Fallback: if duplicates bother the user

Add dedup-by-m3u8-URL in `sortVideos` (3-line change):
```kotlin
fun sortVideos(videos: List<Video>): List<Video> {
    val seen = mutableSetOf<String>()
    return videos
        .filter { seen.add(it.videoUrl.substringAfter("/variant/").substringBefore(".m3u8")) }
        // ... existing sort logic
}
```
This keeps the first occurrence of each (audioType, quality) pair and drops duplicates. Enable only if the user requests it during testing.

## What we do NOT dedup

- **SUB / HSUB / DUB** are genuinely different audio streams (different m3u8 hashes, different audio tracks). Never dedup across audio types.
- **1080p / 720p / 360p** are genuinely different video encodes. Never dedup across resolutions.
- **VidPlay-1 vs HD-1/Vidstream-2 vs Kiwi** use different CDNs and different m3u8 URLs. Never dedup across servers (they're fallbacks if one CDN goes down).

## Rule §7 compliance

> Rule §7: "The site shares tokens across audio types — same video served under different labels. Handle deduplication."

**Verified interpretation**: the "token sharing" is at the PLAYER-TOKEN layer (VidPlay-1 reuses one player token across sub/hsub/dub), NOT at the video-stream layer. The actual m3u8 files are DIFFERENT per audio type. So:
- ✅ We do NOT dedup sub/hsub/dub videos — they're different streams.
- ✅ We DO recognize HD-1 ≡ Vidstream-2 as duplicates, but keep both for resilience.
- ✅ The "safe approach" (always re-resolve per audio type) is correct — don't cache data-ids across audio types (HD-1/Vidstream-2 differ per audio type).
