# ANIKOTO — Per-Server Deep Dive

> Last updated: 2026-06-23 (session 12) · Status: ✅ VERIFIED (every chain tested end-to-end with Python)
> Test episode: `https://anikototv.to/watch/wistoria-wand-and-sword-season-2-dua04/ep-5`
> Full analysis: `EXTENSIONS/anikoto/MEMORY/workflow/04_VIDEO_EXTRACTION_PLAYBACK/ANIKOTO/server-audio-resolution-matrix.md`
> Python script: `EXTENSIONS/anikoto/MEMORY/workflow/04_VIDEO_EXTRACTION_PLAYBACK/ANIKOTO/python-prototypes/analyze-full-chain-v2.py`

**Session-12 re-verification**: all 13 (server × audio) combos were tested with a Python script (`analyze-full-chain-v2.py`). Results match this document exactly. Additional findings:
- **Kiwi-Stream segments are ALL on `p16-ad-sg.ibyteimg.com`** (the ad CDN) — host-based ad filtering CANNOT work for Kiwi. Keep all segments. See `ad-filtering-strategy.md`.
- **HD-1 ≡ Vidstream-2 confirmed**: identical data-ids (176012/176261/176502) AND identical m3u8 URLs for all 3 audio types. Strong dedup candidate.
- **VidPlay-1 HSUB has subtitles** (1 English captions track) — unlike HD-1/Vidstream-2 HSUB which has 0 tracks. VidPlay-1's HSUB is a different audio mix (same video encode), not a re-encoded hardsub.
- **HSUB on HD-1/Vidstream-2 uses a different encode** (lower bitrate: 300k/732k/1624k vs 800k/2800k/5500k for SUB/DUB).
- **The reference APKs only support VidPlay-1** (they check `iframeUrl.contains("vidtube.site")` and reject megaplay.buzz/mewcdn.online). Our extension will support all 4 working servers.

## Server 1: VidPlay-1 ✅ WORKS

- **Player host:** `vidtube.site`
- **Audio types:** sub, hsub, dub (all 3)
- **Qualities:** 1080p, 720p, 360p
- **Subtitles:** English VTT (sub + dub; none for hsub)
- **Skip data:** intro/outro (both [0,0] for this ep)
- **CDN:** `mt.nekostream.site`
- **PNG wrapping:** ✅ YES (70-byte PNG header + TS)
- **Ad injection:** ✅ YES (132 ads vs 12 real segments in 1080p playlist)

### Chain (SUB):
```
data-link-id (from /ajax/server/list)
  → GET /ajax/server?get={link_id}
  → {"url":"https://vidtube.site/stream/REFRZ3lMTDdlYU9OVEtuakk1SjFIQkg2TWVXWVVYRy9MakdLeThkZEZMRE9ZV3lTQVpTZ2RGaGlSb00rYzVrTg/sub",
     "skip_data":{"intro":[0,0],"outro":[0,0]}}
  → GET https://vidtube.site/stream/{token}/sub
  → HTML with data-id="138029" (SAME across sub/hsub/dub)
  → GET https://vidtube.site/stream/getSourcesNew?id=138029&type=sub
  → {"sources":{"file":"https://mt.nekostream.site/1a1e84e365ac3f37c1b338fff210085a/master.m3u8"},
     "tracks":[{"file":".../English.vtt","label":"English","kind":"captions","default":true}],
     "t":1,"intro":{"start":0,"end":0},"outro":{"start":0,"end":0},"server":5}
  → GET https://mt.nekostream.site/1a1e84e365ac3f37c1b338fff210085a/master.m3u8
  → 3 variants: {epHash}1080.m3u8 / {epHash}720.m3u8 / {epHash}360.m3u8
  → GET https://mt.nekostream.site/1a1e84e365ac3f37c1b338fff210085a/{epHash}1080.m3u8
  → segments: https://mt.nekostream.site/segment/{token} (PNG-wrapped, mixed with ads)
```

**HSUB:** same chain, `type=hsub`, m3u8 hash `c2b9eb434a239894613fab6c30aa32cb`, NO subtitles.
**DUB:** same chain, `type=dub`, m3u8 hash `ec7821b9ef81d2122c75136c1683ca94`, has English VTT.

---

## Server 2: HD-1 ✅ WORKS (session 26: migrated to getSources)

- **Player host:** `megaplay.buzz` (server path `s-5`)
- **Audio types:** sub, hsub, dub (all 3)
- **Qualities:** 1080p, 720p, 360p
- **Subtitles:** English VTT (sub + dub; none for hsub)
- **Skip data:** intro/outro (both [0,0])
- **CDN:** `cdn.mewstream.buzz` (was `9hjkrt.nekostream.site` before session 26 migration)
- **PNG wrapping:** ✅ YES (same 70-byte pattern)
- **Ad injection:** ✅ YES

### Chain (SUB) — UPDATED session 26 (getSources, not getSourcesNew):
```
data-link-id
  → GET /ajax/server?get={link_id}
  → {"url":"https://megaplay.buzz/stream/s-5/{epId}/sub","skip_data":{...}}
  → GET https://megaplay.buzz/stream/s-5/{epId}/sub
  → HTML with data-id="176012" (DIFFERENT per audio type!)
  → GET https://megaplay.buzz/stream/getSources?id=176012    ★ (was getSourcesNew?type=sub → 404 before session 26)
  → {"sources":{"file":"https://cdn.mewstream.buzz/anime/{hash}/{hash}/master.m3u8"},...}
  → master.m3u8 has variants: index-f1.m3u8 (1080), index-f2.m3u8 (720), index-f3.m3u8 (360)
```

> ⚠️ **Session 26 migration:** megaplay.buzz migrated from `getSourcesNew` → `getSources`.
> `getSourcesNew?id=X&type=Y` now returns 404. `getSources?id=X` (no type param — data-id
> is audio-specific) returns valid JSON. CDN changed from nekostream.site to mewstream.buzz.
> See `getsources-migration-and-id-analysis.md` §1.

**HSUB:** data-id=176261, no subtitles.
**DUB:** data-id=176502, has English VTT.

> ⚠️ **data-ids DIFFER per audio type** (unlike VidPlay-1). Use the "safe approach": always read the `data-link-id` from the audio-type-specific `<li>`, don't reuse data-ids across audio types.

---

## Server 3: Vidstream-2 ✅ WORKS (session 26: migrated to getSources)

- **Player host:** `megaplay.buzz` (server path `s-2`)
- **Audio types:** sub, hsub, dub (all 3)
- **Qualities:** 1080p, 720p, 360p
- **Subtitles:** English VTT (sub + dub; none for hsub)
- **Skip data:** intro/outro (both [0,0])
- **CDN:** `cdn.mewstream.buzz` (was `9hjkrt.nekostream.site` before session 26 migration)
- **PNG wrapping:** ✅ YES
- **Ad injection:** ✅ YES

### Chain:
Identical to HD-1 but URL path is `s-2` instead of `s-5`. Uses `getSources?id=X` (session 26
fix — getSourcesNew 404'd on megaplay.buzz). **Resolves to the SAME m3u8 URL** as HD-1 with
the SAME data-ids (176012/176261/176502 on Wistoria). HD-1 ≡ Vidstream-2 confirmed.

> ⚠️ **HD-1 and Vidstream-2 appear to be the SAME backend file** exposed via two server entries. **Dedup candidate** — surface as one hoster in the extension, OR label as `"HD-1 / Vidstream-2"`. (Verify on another episode — if always identical, dedup.)

---

## Server 4: VidCloud-1 ✅ WORKS (fixed session 26 — uses getSources, not getSourcesNew)

- **Player host:** `vidwish.live` (server path `s-2`)
- **Audio types:** sub, dub (NO hsub on most episodes)
- **Qualities:** 1080p, 720p, 360p
- **Subtitles:** English VTT (sub + dub; varies per episode)
- **Skip data:** intro/outro (both [0,0] on tested eps)
- **CDN:** `fxpy7.watching.onl` (was unreachable before session 26 fix)
- **PNG wrapping:** ✅ YES (same 70-byte pattern — defensive strip handles it)
- **Ad injection:** ✅ YES (keep all segments — no filtering)

### Chain (SUB) — FIXED in session 26:
```
data-link-id
  → GET /ajax/server?get={link_id}    ✅ returns {"url":"https://vidwish.live/stream/s-2/{epId}/sub","skip_data":{...}}
  → GET https://vidwish.live/stream/s-2/{epId}/sub    ✅ HTML player page renders (data-id="174447", data-realid, data-mediaid, cid, domain2_url='https://fxpy7.watching.onl/')
  → GET https://vidwish.live/stream/getSources?id=174447    ✅ 200 JSON (★ was getSourcesNew → 404/error before session 26)
    Response: {"sources":{"file":"https://fxpy7.watching.onl/anime/{hash}/{hash}/master.m3u8"},"tracks":[...],"server":4}
  → GET https://fxpy7.watching.onl/anime/{hash}/{hash}/master.m3u8 → 3 variants
```

> ⚠️ **Session 26 migration:** vidwish.live migrated from `getSourcesNew` → `getSources`.
> The old `getSourcesNew?id=X&type=Y` returns an HTML error page. The new `getSources?id=X`
> (no type param — data-id is audio-specific) returns valid JSON. See
> `getsources-migration-and-id-analysis.md` §1.

> ⚠️ **Episode-specific failures:** some episodes' VidCloud-1 returns an "Error - Vidcloud"
> page even from the iframe page itself (e.g. Smoking Behind the Supermarket EP5). This is
> a vidwish.live server-side issue — that episode's data isn't available. The extension
> handles this gracefully (logs + skips). VidCloud-1 works on episodes where vidwish.live
> has the data (e.g. Klutzy Class Monitor EP12, Wistoria EP5).

> ⚠️ **data-ids DIFFER per audio type** on vidwish.live (sub=174447, dub=174706 on Wistoria).
> The data-id encodes the audio, so getSources?id=X returns the correct audio without a type param.

---

## Server 5: Kiwi-Stream ✅ WORKS (DIFFERENT CHAIN)

- **Player host:** `mewcdn.online` (player iframe) → `vibeplayer.site` (direct m3u8)
- **Audio types:** hsub (mapper "sub"), dub (mapper "dub") — NO pure SUB
- **Qualities:** 1080p, 720p, 360p
- **Subtitles:** — (no `tracks[]` in the resolve response; m3u8 may have embedded VTT — UNVERIFIED)
- **Skip data:** intro/outro (both [0,0]) — from `/ajax/server?get=...`
- **CDN:** `vibeplayer.site` (direct m3u8 host)
- **PNG wrapping:** ✅ YES (2.6MB segments, same PNG pattern)
- **Ad injection:** ✅ YES (ads at `p16-ad-sg.ibyteimg.com`)

### Chain (HSUB — mapper "sub"):
```
mapper.js calls: GET https://mapper.nekostream.site/api/mal/59983/5/1779613503
  → {"Kiwi-Stream-":{"sub":{"url":"{LINK_ID_B64}"},"dub":{"url":"{LINK_ID_B64}"}},"status":{...}}
  (the "sub" key = HSUB per mapper.js serverStructure.sub template which renders "H-SUB" label)

GET /ajax/server?get={kiwi_link_id}
  → {"url":"https://mewcdn.online/player/plyr.php#aHR0cHM6Ly92aWJlcGxheWVyLnNpdGUvcHVibGljL3N0cmVhbS9lOTA0MjRmMjllYzgxZGRjL21hc3Rlci5tM3U4#",
     "skip_data":{"intro":[0,0],"outro":[0,0]}}

DECODE the base64 fragment (aHR0c...):
  → https://vibeplayer.site/public/stream/e90424f29ec81ddc/master.m3u8

GET https://vibeplayer.site/public/stream/e90424f29ec81ddc/master.m3u8
  → 3 variants: 3165901080.m3u8 / 316590720.m3u8 / 316590360.m3u8
  → segments: vibeplayer.site or p16-ad-sg.ibyteimg.com (ads, PNG-wrapped)
```

**DUB:** mapper "dub" key, decodes to `https://vibeplayer.site/public/stream/7295b246a3394d2f/master.m3u8`.

> ⚠️ **Kiwi-Stream audio mapping:** mapper's `sub` = HSUB, mapper's `dub` = DUB. Label accordingly in the extension.

> ⚠️ **No `getSourcesNew` call for Kiwi** — the m3u8 is directly in the resolved URL's base64 fragment. Just decode it.
