# ANIKOTO — Token Sharing & Dedup Analysis

> Last updated: 2026-06-22 · Status: VERIFIED

## What "token" means here

Multiple layers of tokens/IDs in the chain:
1. **`data-ids`** (base64 blob on episode `<a>`) — passed to `/ajax/server/list?servers=`. Decodes to another base64 blob (likely encrypted). One per episode.
2. **`data-link-id`** (base64 blob on server `<li>`) — passed to `/ajax/server?get=`. One per (server, audio_type) combination.
3. **Player token** (in the player URL path, e.g. `REFRZ3lMTDdlYU9OVEtuakk1SjFIQkg2TWVXWVVYRy9MakdLeThkZEZMRE9ZV3lTQVpTZ2RGaGlSb00rYzVrTg`) — the path component of `vidtube.site/stream/{token}/{audio}`. One per (server, anime, episode) — but SAME across audio types for VidPlay-1.
4. **`data-id`** (integer on the player HTML page, e.g. 138029) — passed to `getSourcesNew?id=`. One per (server, anime, episode, audio_type) — same across audio types for VidPlay-1, DIFFERENT for HD-1/Vidstream-2.
5. **m3u8 hash** (in the CDN URL, e.g. `1a1e84e365ac3f37c1b338fff210085a`) — DIFFERENT per audio type (different video files).

## Token sharing (rule §7 — "site shares tokens across audio types")

### VidPlay-1: SHARED player token + data-id, DIFFERENT m3u8
- Player token `REFRZ3lMTDdl...` is the SAME in the player URL across sub/hsub/dub (only the URL's last segment `/sub`, `/hsub`, `/dub` changes).
- `data-id` 138029 is the SAME across sub/hsub/dub.
- BUT the resolved m3u8 hashes DIFFER:
  - sub: `1a1e84e365ac3f37c1b338fff210085a`
  - hsub: `c2b9eb434a239894613fab6c30aa32cb`
  - dub: `ec7821b9ef81d2122c75136c1683ca94`
- **Conclusion:** sub/hsub/dub are GENUINELY DIFFERENT video files (different audio tracks) resolved through the same player token. NOT a dedup situation. The "token sharing" the user mentioned = the player token is reused, but the actual video stream is different per audio type.

### HD-1 vs Vidstream-2: IDENTICAL m3u8 (real dedup candidate)
- HD-1 (megaplay `s-5`) and Vidstream-2 (megaplay `s-2`) resolve to the SAME m3u8 URL:
  `https://9hjkrt.nekostream.site/4739d8dbd05dddb73604f6240b83ea68/31fcc9a246c274d4af00a9f7997c3799/master.m3u8`
- Same data-ids (176012/176261/176502 for sub/hsub/dub).
- **Conclusion:** HD-1 and Vidstream-2 are the SAME backend file exposed via two server entries. **Genuinely duplicate** — surface as ONE hoster in the extension, OR keep both but label clearly. Verify on another episode to confirm always identical.

### VidCloud-1: broken (can't dedup)
- getSourcesNew returns 404. No m3u8 to compare.

### Kiwi-Stream: completely separate chain
- Different CDN (`vibeplayer.site`), different m3u8 hashes (`e90424f29ec81ddc` for HSUB, `7295b246a3394d2f` for DUB).
- No overlap with the other 4 servers.

## Dedup strategy for the extension

**Surface these hosters to the user:**
1. **VidPlay-1** (vidtube.site) — all 3 audio types.
2. **HD-1** (megaplay.buzz `s-5`) — all 3 audio types. (OR merge with Vidstream-2.)
3. **Vidstream-2** (megaplay.buzz `s-2`) — all 3 audio types. (Skip if merged with HD-1.)
4. **Kiwi-Stream** (mewcdn.online → vibeplayer.site) — HSUB + DUB only.
5. ~~VidCloud-1~~ (vidwish.live) — **SKIP** (broken).

**Dedup decision (defer to Stage 2):**
- Option A: Keep HD-1 and Vidstream-2 separate (clear to user, more hoster choices if one breaks).
- Option B: Merge HD-1 + Vidstream-2 into one "HD-1 / Vidstream-2" hoster (less clutter, but if megaplay.buzz goes down both disappear).
- **Recommendation: Option A** (keep separate) — matches the site's UI, gives the user a fallback if one server path breaks.

## Rule §7 compliance

> Rule §7: "The site shares tokens across audio types — same video served under different labels. Handle deduplication."

**Verified interpretation for ANIKOTO:** the "token sharing" is at the PLAYER-TOKEN layer (VidPlay-1 reuses one player token across sub/hsub/dub), NOT at the video-stream layer. The actual m3u8 files are DIFFERENT per audio type (genuinely different audio tracks). So:
- ✅ We do NOT need to dedup sub/hsub/dub videos — they're different streams.
- ✅ We DO need to dedup HD-1 vs Vidstream-2 (identical m3u8) — but only if we want to reduce clutter.
- ✅ The player-token reuse means we can cache the `data-id` lookup across audio types for VidPlay-1 (small optimization) — but for HD-1/Vidstream-2 the data-ids differ per audio type, so don't cache across audio types. **Safe approach: always re-resolve per audio type.**
