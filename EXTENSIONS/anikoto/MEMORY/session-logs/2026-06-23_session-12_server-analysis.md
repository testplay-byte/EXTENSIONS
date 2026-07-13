# Session 12 — Comprehensive Server/Audio/Resolution Analysis + Implementation Plan

> Date: 2026-06-23 · Session #: 12 · Duration: ~long · Timezone: America/Los_Angeles

## Goal

Before implementing Stage 4 (video extraction), do a comprehensive live analysis of the test URL
`https://anikototv.to/watch/wistoria-wand-and-sword-season-2-dua04/ep-5` to verify all available
video servers, audio versions, and resolutions. Write Python test scripts, document findings in
the WORKFLOW + DEV folders, and create a proper implementation plan for smooth future development.

## What was done

### A. Wrote a Python analysis script (`analyze-full-chain-v2.py`)
- Walks the complete video chain: watch page → animeId → episode list → server list → (per server×audio) resolve → iframe → data-id → getSourcesNew → master m3u8 → variants.
- Covers ALL 5 servers: VidPlay-1, HD-1, Vidstream-2, VidCloud-1 (from primary `/ajax/server/list`) + Kiwi-Stream (from `mapper.nekostream.site` API).
- Implements the RC4 vrf (key `"simple-hash"`) matching the reference's `AnikotoRC4` exactly.
- Handles BOTH extraction flows: Flow A (primary: iframe → data-id → getSourcesNew) and Flow B (Kiwi: base64 fragment → direct m3u8).
- Outputs a full JSON report (`/tmp/anikoto-chain-analysis-v2.json`) for regression reference.

### B. Ran the analysis on Wistoria S2 EP5 — all 13 (server × audio) combos tested

**Results (the complete verified matrix):**

| # | Server | Audio | Source | Player Host | Data-ID | Variants | Subtitles | Status |
|---|--------|-------|--------|-------------|---------|----------|-----------|--------|
| 1 | VidPlay-1 | SUB | primary | vidtube.site | 138029 | 360/720/1080p | 1 EN | ✅ ok |
| 2 | HD-1 | SUB | primary | megaplay.buzz (s-5) | 176012 | 360/720/1080p | 1 EN | ✅ ok |
| 3 | Vidstream-2 | SUB | primary | megaplay.buzz (s-2) | 176012 | 360/720/1080p | 1 EN | ✅ ok |
| 4 | VidCloud-1 | SUB | primary | vidwish.live | 174447 | — | — | ❌ getSourcesNew returns HTML error |
| 5 | VidPlay-1 | HSUB | primary | vidtube.site | 138029 | 360/720/1080p | 1 EN | ✅ ok |
| 6 | HD-1 | HSUB | primary | megaplay.buzz (s-5) | 176261 | 1080/720/360p | 0 | ✅ ok |
| 7 | Vidstream-2 | HSUB | primary | megaplay.buzz (s-2) | 176261 | 1080/720/360p | 0 | ✅ ok |
| 8 | VidPlay-1 | DUB | primary | vidtube.site | 138029 | 360/720/1080p | 1 EN | ✅ ok |
| 9 | HD-1 | DUB | primary | megaplay.buzz (s-5) | 176502 | 360/720/1080p | 1 EN | ✅ ok |
| 10 | Vidstream-2 | DUB | primary | megaplay.buzz (s-2) | 176502 | 360/720/1080p | 1 EN | ✅ ok |
| 11 | VidCloud-1 | DUB | primary | vidwish.live | 174706 | — | — | ❌ getSourcesNew returns HTML error |
| 12 | Kiwi-Stream | H-SUB | mapper | mewcdn.online → vibeplayer.site | (base64) | 360/720/1080p | 0 | ✅ ok |
| 13 | Kiwi-Stream | A-DUB | mapper | mewcdn.online → vibeplayer.site | (base64) | 360/720/1080p | 0 | ✅ ok |

**Totals**: 13 combos → 11 ok, 2 broken (VidCloud-1). No VidCloud-1 HSUB exists (not in server list).

### C. Verified the key technical details

1. **getSourcesNew endpoint**: confirmed `https://<player_host>/stream/getSourcesNew?id=<data-id>&type=<sub|hsub|dub>`. Returns `{sources:{file:<m3u8>}, tracks:[{file,label,kind}], server:5}`.

2. **Servers that work / fail**:
   - ✅ VidPlay-1 (vidtube.site), HD-1 (megaplay.buzz s-5), Vidstream-2 (megaplay.buzz s-2), Kiwi-Stream (mewcdn.online → vibeplayer.site)
   - ❌ VidCloud-1 (vidwish.live) — getSourcesNew returns HTML "Error - Vidcloud" page (server-side broken, not a client issue)

3. **3 audio types + labels**: SUB, HSUB, DUB (primary); H-SUB, A-DUB (Kiwi mapper). Per user: H-SUB ≡ HSUB, A-DUB ≡ DUB. Normalize to SUB/HSUB/DUB.

4. **Per-audio data-id patterns (the "safe approach")**:
   - VidPlay-1: ONE data-id (138029) for ALL 3 audio types — `type` param differentiates.
   - HD-1/Vidstream-2: DIFFERENT data-id per audio type (176012/176261/176502) — must re-fetch iframe per audio.
   - Kiwi: NO data-id — base64 fragment in iframe URL is the direct m3u8.

5. **Token sharing**: HD-1 ≡ Vidstream-2 — identical data-ids AND identical m3u8 URLs for all 3 audio types. Dedup candidate (decided to keep both for resilience).

6. **PNG wrapping**: confirmed 70-byte PNG header (IEND@62, cut@70, 0x47 TS sync) on ALL working servers including Kiwi. Both real AND ad segments are PNG-wrapped.

7. **CDN/WAF behavior**:
   - Real CDNs: `mt.nekostream.site` (VidPlay-1), `9hjkrt.nekostream.site` (HD-1/Vidstream-2), `vibeplayer.site` (Kiwi master).
   - Ad CDNs: `p1.ipstatp.com` (VidPlay-1 ads), `p16-ad-sg.ibyteimg.com` (HD-1/Vidstream-2 ads + ALL Kiwi segments).
   - Real segment URLs 302-redirect to ad CDN hosts (OkHttp follows automatically).
   - Ad filtering: by host — keep `nekostream.site`, drop everything else. ★ Kiwi CANNOT be filtered (all segments on ad CDN).

8. **Kiwi-Stream is a completely different flow**: NO `data-id`, NO `getSourcesNew`. Iframe URL is `https://mewcdn.online/player/plyr.php#<base64-encoded-m3u8-url>`. Decode the base64 fragment → direct m3u8 on `vibeplayer.site`. The reference APKs reject Kiwi (their `iframeUrl.contains("vidtube.site")` check rejects mewcdn.online).

### D. Cross-checked against the 2 reference APKs
- Both references ONLY support VidPlay-1 (they check `iframeUrl.contains("vidtube.site")` and reject megaplay.buzz, mewcdn.online, vidwish.live).
- v3 reference calls the mapper API but rejects the Kiwi iframe. v16.4 dropped the mapper entirely.
- **Our extension will support 4 servers** (VidPlay-1, HD-1, Vidstream-2, Kiwi-Stream) — 3× more than the references.

### E. Documented everything in WORKFLOW + DEV folders

**`WORKSPACE/WORKFLOW/04_VIDEO_EXTRACTION_PLAYBACK/ANIKOTO/`** (7 files):
- `server-audio-resolution-matrix.md` — ★ the complete 13-combo matrix + per-server summary
- `extraction-flows.md` — Flow A (primary) + Flow B (Kiwi) with Kotlin pseudocode
- `ad-filtering-strategy.md` — per-server ad filtering (host-based for primary, none for Kiwi)
- `dedup-strategy.md` — HD-1/Vidstream-2 dedup decision (keep both)
- `implementation-plan.md` — ★ the 9-step implementation plan with file layout
- `python-prototypes/analyze-full-chain-v2.py` — the reusable Python analysis script
- `python-prototypes/analyze-full-chain.py` — v1 (broken parser, kept for history)

**`WORKSPACE/DEV/ANIKOTO/analysis/`** (mirror copies for dev-time reference):
- All 5 markdown docs + the Python script + the raw JSON output

### F. Updated MEMORY site files
- `MEMORY/sites/anikoto/servers.md` — added session-12 confirmation header
- `MEMORY/sites/anikoto/audio-types.md` — updated header
- `MEMORY/sites/anikoto/tokens-and-dedup.md` — updated header with HD-1≡Vidstream-2 confirmation

## Key findings / decisions

1. **4 working servers, 1 broken**: VidPlay-1, HD-1, Vidstream-2, Kiwi-Stream work; VidCloud-1 is broken (skip).
2. **All 3 audio types (SUB/HSUB/DUB) available on all 4 working servers**, except Kiwi only has H-SUB + A-DUB (no pure SUB). H-SUB ≡ HSUB, A-DUB ≡ DUB.
3. **All 3 resolutions (1080p/720p/360p) available on all working server × audio combos**.
4. **Two extraction flows**: Flow A (primary: iframe → data-id → getSourcesNew → m3u8) for VidPlay-1/HD-1/Vidstream-2; Flow B (Kiwi: base64 fragment → direct m3u8) for Kiwi-Stream.
5. **Ad filtering by host** works for primary servers (keep `nekostream.site`), but NOT for Kiwi (all segments on ad CDN — keep all).
6. **HD-1 ≡ Vidstream-2** (identical data-ids + m3u8). Decision: keep both for resilience, no dedup.
7. **Our extension supports 4 servers vs the references' 1** (VidPlay-1). This requires accepting multiple player hosts (vidtube.site, megaplay.buzz, mewcdn.online) and implementing the Kiwi base64-fragment flow.
8. **Implementation plan is complete** — 9 steps, file layout, spec sources. No new research needed.

## Files created / changed

**WORKFLOW/04_VIDEO_EXTRACTION_PLAYBACK/ANIKOTO/**:
- `python-prototypes/analyze-full-chain.py` (v1, broken parser)
- `python-prototypes/analyze-full-chain-v2.py` (★ v2, fixed, all 5 servers)
- `server-audio-resolution-matrix.md` (★ the matrix)
- `extraction-flows.md` (Flow A + Flow B)
- `ad-filtering-strategy.md` (per-server filtering)
- `dedup-strategy.md` (HD-1/Vidstream-2 decision)
- `implementation-plan.md` (★ the 9-step plan)

**DEV/ANIKOTO/analysis/**: mirror copies of all above + `anikoto-chain-analysis-v2.json`

**MEMORY/sites/anikoto/**:
- `servers.md` — session-12 confirmation header
- `audio-types.md` — updated header
- `tokens-and-dedup.md` — updated header

**MEMORY/session-logs/**: `2026-06-23_session-12_server-analysis.md` (this log)

## Status at end of session

- ✅ All 5 servers analyzed and visible (4 work, 1 broken).
- ✅ All 3 audio types verified per server (SUB/HSUB/DUB; Kiwi's H-SUB/A-DUB normalized).
- ✅ All 3 resolutions verified per audio type (1080p/720p/360p).
- ✅ Both extraction flows documented (Flow A primary, Flow B Kiwi).
- ✅ Ad filtering strategy per-server documented (host-based for primary, none for Kiwi).
- ✅ Dedup strategy decided (keep HD-1 + Vidstream-2 both, no dedup).
- ✅ Python prototype reproduces the full chain (regression reference for the Kotlin port).
- ✅ Implementation plan complete (9 steps, file layout, spec sources).
- ✅ All docs in WORKFLOW + DEV folders for easy reference during development.
- ⏳ **Ready to implement Stage 4** — start with Step 1 (EpisodeMeta + AnikotoRC4 + DTOs).

## Next steps (resume point)

Follow `implementation-plan.md`:
1. **Step 1**: EpisodeMeta + AnikotoRC4 + DTOs (foundation files)
2. **Step 2**: extensions/utils toolkit (Source base + helpers)
3. **Step 3**: upgrade Anikoto.kt (extend Source, add vrf, EpisodeMeta encoding, two-client split)
4. **Step 4**: video data classes
5. **Step 5**: VidTubeExtractor (Flow A)
6. **Step 6**: KiwiExtractor (Flow B)
7. **Step 7**: discovery + parallel resolution (getHosterList)
8. **Step 8**: LocalProxyServer (index-based proxy + PNG strip + cache + prefetch)
9. **Step 9**: video building + sort + return

Build + test after each step (one change at a time per rule §2).

## Honest notes

- **The Python script took 2 iterations** — v1 had a broken regex that labeled all combos as "sub" and missed Kiwi entirely (Kiwi isn't in the primary server list). v2 fixed both: proper per-type-block parsing + mapper API call for Kiwi.
- **All 13 combos were tested with real HTTP requests** — no mocking, no guessing. The JSON at `/tmp/anikoto-chain-analysis-v2.json` (mirrored to `DEV/ANIKOTO/analysis/`) contains the full raw data for every step.
- **The most surprising finding was Kiwi's flow** — completely different from the other servers (base64 fragment in the URL, no getSourcesNew call). This is why the reference APKs only support VidPlay-1 — their `vidtube.site` host check is too narrow. Our extension will be more complete by accepting mewcdn.online and implementing the base64 decode.
- **The second surprise was that ALL Kiwi segments are on the ad CDN host** — host-based ad filtering would remove everything. The decision to keep all Kiwi segments is a pragmatic tradeoff (some ads may play, but the episode is watchable).
- **No code was written for the extension this session** — pure analysis + documentation. But the implementation plan is now complete enough that the actual coding should be mechanical and smooth.
- **The v1 Python script is kept** (not deleted) per the `04_VIDEO_EXTRACTION_PLAYBACK/README.md` convention — useful as a regression reference and to show the evolution of understanding.
