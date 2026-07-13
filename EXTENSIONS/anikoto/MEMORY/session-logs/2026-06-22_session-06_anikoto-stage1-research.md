# Session 06 — ANIKOTO Stage 1 (Website Research)

> Date: 2026-06-22 · Session #: 06 · Duration: ~long · Timezone: America/Los_Angeles

## Goal

Stage 1 of the WORKFLOW — analyze the target site `https://anikototv.to` thoroughly before any
architecture or coding. Trace the COMPLETE video chain from homepage to playable stream for EVERY
server on test episode `https://anikototv.to/watch/wistoria-wand-and-sword-season-2-dua04/ep-5`.
Discover everything: which endpoint lists servers, how tokens resolve, how to get from player URL
to m3u8, the 3 audio types, qualities, subtitles, skip data, segment obfuscation, which servers work
vs fail. Verify EVERY finding against the live site.

## What was done

### A. Site connectivity + structure probe
- Both `anikototv.to` and `anikoto.cz` respond (Cloudflare-fronted, no challenge).
- Fetched test episode HTML (83 KB). Found `data-id="8737"` (anime ID), `/ajax/episode` endpoint
  hints, server-related class names, `<div id="w-servers">` (empty in static HTML — populated via JS).
- Fetched `main.js` (81 KB) + `mapper.js` (4.4 KB).
- **mapper.js revealed:** an external API `https://mapper.nekostream.site/api/mal/{malId}/{epNum}/{timestamp}`
  injects Kiwi-Stream (and potentially other) servers. `mapper.js`'s `serverStructure` confirms
  Kiwi's "sub" key = H-SUB, "dub" key = A-DUB (matches user's clarification).
- **main.js revealed:** the 2 key endpoints — `ajax/server/list?servers={ids}` (server list) +
  `ajax/server?get={linkId}` (resolve link-id → player URL + skip_data). Plus `ajax/episode/list/{id}`
  (episode list).

### B. Used agent-browser to capture live network requests
- Opened the test episode page → captured XHR calls:
  - `GET /ajax/episode/list/8737?vrf=` (vrf empty for this anime)
  - `GET /ajax/server/list?servers=bWEzSjhmSjRJZ0ZHY3p5Q...` (base64-encoded `data-ids` param)
  - `GET https://mapper.nekostream.site/api/mal/59983/5/1779613503` (Kiwi injection)
- The `servers` param decodes to another base64 blob (double-encoded, likely encrypted) — passed
  through unchanged (no client-side decryption needed).

### C. Traced the full chain for ALL 5 servers
Fetched the server-list response, parsed all 3 audio-type sections (sub/hsub/dub), extracted every
`data-link-id`. Called `ajax/server?get={link_id}` for each. Then for each player URL, fetched the
player HTML to get `data-id`, called `getSourcesNew?id={data_id}&type={audio}` to get the m3u8.
Fetched the master m3u8 + a media playlist + the first real segment. Inspected segment bytes.

**Results (all verified):**

| Server | Player host | Audio types | Qualities | Subs | Skip | PNG wrap | Status |
|---|---|---|---|---|---|---|---|
| VidPlay-1 | vidtube.site | sub/hsub/dub | 1080/720/360 | EN VTT (sub,dub) | [0,0] | ✅ 70-byte | ✅ WORKS |
| HD-1 | megaplay.buzz `s-5` | sub/hsub/dub | 1080/720/360 | EN VTT (sub,dub) | [0,0] | ✅ 70-byte | ✅ WORKS |
| Vidstream-2 | megaplay.buzz `s-2` | sub/hsub/dub | 1080/720/360 | EN VTT (sub,dub) | [0,0] | ✅ 70-byte | ✅ WORKS |
| VidCloud-1 | vidwish.live `s-2` | sub/dub (no hsub) | — | — | — | — | ❌ BROKEN |
| Kiwi-Stream | mewcdn.online → vibeplayer.site | hsub+dub (mapper sub/dub) | 1080/720/360 | — | [0,0] | ✅ 2.6MB | ✅ WORKS |

### D. Key discoveries (all verified against live site)

1. **The key API endpoint is `getSourcesNew`** (the user's hint confirmed):
   `GET https://{player_host}/stream/getSourcesNew?id={file_id}&type={audio}` returns
   `{sources:{file:m3u8}, tracks:[...], intro, outro, server}`. Works on vidtube.site + megaplay.buzz.
2. **VidCloud-1 is broken** — `getSourcesNew` on vidwish.live returns a 404 HTML error page (not JSON).
   The player page itself renders (data-id=174447), but the API that should return the m3u8 is
   broken server-side. Both sub + dub fail identically. No HSUB offered.
3. **All working servers use PNG wrapping** — every segment at `{cdn}/segment/{token}` is a 70-byte
   PNG header (IHDR+IDAT+IEND) + real MPEG-TS data. TS sync byte 0x47 at offset 70. Verified on
   VidPlay-1, HD-1, Vidstream-2, Kiwi-Stream. **We need `lib/m3u8server` to strip it.**
4. **Ad injection in media playlists** — 132 ad segments vs 12 real segments for the test episode.
   Ads on `ipstatp.com` / `ibyteimg.com`. Must filter by CDN host.
5. **3 audio types confirmed** (SUB, HSUB, DUB) — HSUB is distinct (no VTT, different m3u8 hash).
   Kiwi's mapper "sub" = HSUB, "dub" = DUB (per mapper.js `serverStructure`).
6. **Token sharing** — VidPlay-1 reuses the SAME player token + data-id across sub/hsub/dub (only
   the URL's last segment changes), BUT the m3u8 hashes DIFFER → genuinely different video files.
   HD-1 and Vidstream-2 resolve to the SAME m3u8 → real dedup candidate.
7. **The "safe approach" for data-ids** — VidPlay-1 data-id is shared across audio types, but
   HD-1/Vidstream-2 data-ids DIFFER per audio type (176012 sub, 176261 hsub, 176502 dub). So always
   re-resolve per audio type using the audio-specific `data-link-id` (never reuse data-ids across
   audio types).
8. **Kiwi-Stream is a different chain** — no `getSourcesNew` call. The `ajax/server?get=...` response
   URL is `https://mewcdn.online/player/plyr.php#{base64}` where the base64 decodes to a direct m3u8
   URL on `vibeplayer.site`.
9. **No WAF/anti-bot challenge** anywhere — normal browser UA + Referer is sufficient. No `cf_clearance`
   needed. No lib interceptor modules required for the basic chain.
10. **Skip data** returned in two places: `skip_data` in `/ajax/server?get=...` response (intro/outro
    arrays) AND `intro`/`outro` objects in `getSourcesNew` response. Both [0,0] for this episode
    (community skip data not available). The main.js `Mr` function reads `t.result.skip_data` → that's
    the authoritative source.
11. **Subtitles** — SUB + DUB have English VTT tracks (in `getSourcesNew.tracks[]`). HSUB has none
    (hardsubbed). Kiwi: no tracks in the resolve response (m3u8 may have embedded VTT — unverified).

### E. Documentation written (8 files, ~70 KB total)

In `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/` (working notes):
- `site-analysis.md` (29 KB) — the master document: TL;DR chain, all 5 servers, audio types, token
  sharing, PNG wrapping, CDN WAF, broken server, what's NOT verified, Stage 2 implications.
- `endpoints.md` — full endpoint inventory with params + responses + required headers.
- `audio-types.md` — the 3 audio types + per-server availability + Kiwi mapping + scanlator strategy.
- `servers.md` — per-server deep dive (chains, where VidCloud-1 fails, HD-1≡Vidstream-2).
- `video-flow.md` — compact 10-step chain diagram (Stage A→D).
- `tokens-and-dedup.md` — token sharing analysis + dedup strategy.
- `png-wrapping.md` — the 70-byte PNG structure + how to strip it (extend m3u8server AutoDetector).
- `cdn-waf.md` — CDN/WAF behavior + required headers + ad filtering.

Promoted copies to `MEMORY/sites/anikoto/` (permanent record) + an index `README.md`.

Also created `WORKSPACE/DEV/ANIKOTO/{APK,DEVELOPMENT_CODE}/` (with local.properties) ready for Stage 2.

## Key findings / decisions

1. **Stage 1 is complete.** The full video chain is traced and verified for all 5 servers (4 working,
   1 broken). Every claim is sourced (file/line or curl-verified).
2. **The extension will need `:lib:m3u8server`** (port from yuzono with v16 Video-ctor fix) — required
   for PNG header stripping + ad segment filtering.
3. **The extension will need `:lib:playlistutils`** — for parsing the master m3u8 into per-quality
   Videos (after m3u8server rewrites segments).
4. **VidCloud-1 will be skipped** (or surface with empty videoList) — it's broken server-side.
5. **HD-1 vs Vidstream-2:** keep separate (more hoster choices if megaplay.buzz's `s-5`/`s-2` paths
   break independently) — recommendation, deferred to Stage 2.
6. **Kiwi-Stream requires the external mapper API call** — the extension must replicate
   `mapper.nekostream.site/api/mal/{malId}/{epNum}/{timestamp}` to get Kiwi link-ids.
7. **No anti-bot interceptor needed** — the basic chain works with `network.client` + per-endpoint
   Referer headers. Simplifies the build.

## Files created / modified

New (17):
- `WORKSPACE/DEV/ANIKOTO/{APK,DEVELOPMENT_CODE}/` (scaffold for Stage 2; local.properties copied)
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/site-analysis.md` (★ master doc)
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/endpoints.md`
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/audio-types.md`
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/servers.md`
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/video-flow.md`
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/tokens-and-dedup.md`
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/png-wrapping.md`
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/cdn-waf.md`
- `MEMORY/sites/anikoto/{README,site-analysis,endpoints,audio-types,servers,video-flow,tokens-and-dedup,png-wrapping,cdn-waf}.md` (promoted permanent record)
- `MEMORY/session-logs/2026-06-22_session-06_anikoto-stage1-research.md` — this log

## Status at end of session

- ✅ Stage 1 (Website Research) COMPLETE for ANIKOTO. Full video chain verified for all 5 servers.
- ✅ All findings documented in `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/ANIKOTO/` (working) +
  `MEMORY/sites/anikoto/` (permanent).
- ✅ `WORKSPACE/DEV/ANIKOTO/` scaffold ready for Stage 2 (Architecture Design).
- ⏳ Stage 2 not started — await user direction to proceed.
- ⚠️ JDK blocker from session 05 still stands (no `javac`) — needed for Stage 6 (Build & Test) but
  NOT for Stage 2 (architecture/design on paper).

## Next steps (for the next session)

Per the WORKFLOW, Stage 2 is **Architecture Design**:
1. Decide: standalone extension (no multisrc theme fits ANIKOTO's custom flow).
2. List `lib/` modules to port: `m3u8server` (★ required for PNG + ads), `playlistutils` (m3u8
   parsing), possibly `cryptoaes` (if `data-ids` decryption needed — likely NOT, we pass through).
3. Plan the `m3u8server` AutoDetector extension (PNG → find IEND → skip 70 bytes).
4. Scaffold `WORKSPACE/DEV/ANIKOTO/DEVELOPMENT_CODE/` per `MEMORY/guides/01-build-setup-for-ext-lib-16.md`.
5. Resolve the JDK blocker (user to provide JDK 17/21) before any actual build.

**Do NOT proceed to Stage 2 until the user confirms** — per the user's instruction: "Don't proceed
to the next stage until the full video chain is documented and verified for every server." That's done.

## Open issues (honest gaps to verify later)

Documented in `site-analysis.md` §12. Summary:
1. `vrf` param was empty for this anime — need to test an anime where it's non-empty.
2. Search results HTML structure (`/filter?keyword=`) not fully parsed — re-verify with agent-browser.
3. Anime detail page (`/anime/{slug}`) returned "Error" title — may need proper headers/cookies.
4. Pagination not tested.
5. Kiwi-Stream subtitles (m3u8 may have embedded VTT) — unverified.
6. HD-1 ≡ Vidstream-2 dedup — verify on another episode.
7. `domain2_url` on vidwish (`fxpy7.watching.onl/`) — unexplored (VidCloud broken anyway).
8. `data-ids` double-base64 — likely encrypted, but we pass through unchanged (no decryption needed).

None of these block Stage 2; they're refinement items.

## Honest notes

- **Used curl for the bulk of the analysis** (faster, scriptable). Used agent-browser only for the
  initial network capture (to discover `getSourcesNew` + the `servers` param) and one segment fetch
  (to verify WAF behavior). The user's hint that "player pages do NOT require JavaScript execution"
  was correct — the entire chain is curl-able.
- **Every claim in the docs is sourced** — either a file/line reference (main.js, mapper.js) or a
  curl-verified response (saved to /tmp during analysis). No guessing.
- **The PNG wrapping discovery is significant** — it means we MUST port `lib/m3u8server` (not
  optional). This adds complexity to Stage 2 but it's a known, solved problem (yuzono's m3u8server
  + AutoDetector handle exactly this).
- **VidCloud-1 being broken is a feature, not a bug, for our testing** — it lets us verify the
  extension's graceful-failure handling (the app should mark its videos as ERROR and fall back to
  the next hoster, per `MEMORY/research/01-...md` §6).
- **I did NOT start coding** — per the user's instruction "Don't start coding — this is research
  only." Stage 2 (Architecture) is also design-only; Stage 3+ is where coding begins.
- **The token-sharing finding refines rule §7's interpretation** for this site: the "sharing" is at
  the player-token layer (VidPlay-1 reuses one token), NOT the video-stream layer. sub/hsub/dub are
  genuinely different streams. The real dedup is HD-1≡Vidstream-2.
