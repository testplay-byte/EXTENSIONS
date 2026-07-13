# ANIKOTO — Per-Server Deep Dive

> Last updated: 2026-06-22 · Status: VERIFIED (every chain tested end-to-end)
> Test episode: `https://anikototv.to/watch/wistoria-wand-and-sword-season-2-dua04/ep-5`

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

## Server 2: HD-1 ✅ WORKS

- **Player host:** `megaplay.buzz` (server path `s-5`)
- **Audio types:** sub, hsub, dub (all 3)
- **Qualities:** 1080p, 720p, 360p
- **Subtitles:** English VTT (sub + dub; none for hsub)
- **Skip data:** intro/outro (both [0,0])
- **CDN:** `9hjkrt.nekostream.site`
- **PNG wrapping:** ✅ YES (same 70-byte pattern)
- **Ad injection:** ✅ YES

### Chain (SUB):
```
data-link-id
  → GET /ajax/server?get={link_id}
  → {"url":"https://megaplay.buzz/stream/s-5/873700500/sub","skip_data":{...}}
  → GET https://megaplay.buzz/stream/s-5/873700500/sub
  → HTML with data-id="176012" (DIFFERENT per audio type!)
  → GET https://megaplay.buzz/stream/getSourcesNew?id=176012&type=sub
  → {"sources":{"file":"https://9hjkrt.nekostream.site/4739d8dbd05dddb73604f6240b83ea68/31fcc9a246c274d4af00a9f7997c3799/master.m3u8"},...}
  → master.m3u8 has variants: index-f1.m3u8 (1080), index-f2.m3u8 (720), index-f3.m3u8 (360)
```

**HSUB:** data-id=176261, no subtitles.
**DUB:** data-id=176502, has English VTT.

> ⚠️ **data-ids DIFFER per audio type** (unlike VidPlay-1). Use the "safe approach": always read the `data-link-id` from the audio-type-specific `<li>`, don't reuse data-ids across audio types.

---

## Server 3: Vidstream-2 ✅ WORKS

- **Player host:** `megaplay.buzz` (server path `s-2`)
- **Audio types:** sub, hsub, dub (all 3)
- **Qualities:** 1080p, 720p, 360p
- **Subtitles:** English VTT (sub + dub; none for hsub)
- **Skip data:** intro/outro (both [0,0])
- **CDN:** `9hjkrt.nekostream.site`
- **PNG wrapping:** ✅ YES
- **Ad injection:** ✅ YES

### Chain:
Identical to HD-1 but URL path is `s-2` instead of `s-5`. **Resolves to the SAME m3u8 URL** (`9hjkrt.nekostream.site/4739d8dbd05dddb73604f6240b83ea68/31fcc9a246c274d4af00a9f7997c3799/master.m3u8`) with the SAME data-ids (176012/176261/176502).

> ⚠️ **HD-1 and Vidstream-2 appear to be the SAME backend file** exposed via two server entries. **Dedup candidate** — surface as one hoster in the extension, OR label as `"HD-1 / Vidstream-2"`. (Verify on another episode — if always identical, dedup.)

---

## Server 4: VidCloud-1 ❌ BROKEN

- **Player host:** `vidwish.live` (server path `s-2`)
- **Audio types:** sub, dub (NO hsub)
- **Qualities:** — (never reached)
- **Subtitles:** — (never reached)
- **Skip data:** returned as [0,0] from `/ajax/server?get=...` (but useless since m3u8 fetch fails)
- **CDN:** — (never reached)
- **PNG wrapping:** — (never reached)

### Chain (SUB) — WHERE IT FAILS:
```
data-link-id
  → GET /ajax/server?get={link_id}    ✅ returns {"url":"https://vidwish.live/stream/s-2/873700500/sub","skip_data":{...}}
  → GET https://vidwish.live/stream/s-2/873700500/sub    ✅ HTML player page renders (data-id="174447", data-realid, data-mediaid, cid='5553', domain2_url='https://fxpy7.watching.onl/')
  → GET https://vidwish.live/stream/getSourcesNew?id=174447&type=sub    ❌ 404 HTML ERROR PAGE
    Response: <!DOCTYPE html>...<title>Error - Vidcloud</title>...<h1>Oops! Something went wrong</h1>...Error Code: 404
```

**Root cause:** the `getSourcesNew` endpoint on `vidwish.live` returns 404 for this episode's data-ids (174447 sub, 174706 dub). The player page itself loads fine, but the API that should return the m3u8 is broken. This is a server-side issue at vidwish.live.

**Both sub and dub fail identically.** No HSUB offered for VidCloud-1 (only sub+dub in the server list).

**Recommendation for the extension:** skip VidCloud-1 entirely (don't surface as a hoster), OR surface it but `videoListParse` returns an empty list. Decision deferred to Stage 2.

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
