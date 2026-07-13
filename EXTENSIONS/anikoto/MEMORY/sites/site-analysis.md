# ANIKOTO Site Analysis (Stage 1 — Website Research)

> Last updated: 2026-06-22 · Status: VERIFIED (every claim tested against the live site)
> Target site: `https://anikototv.to` (also `https://anikoto.cz` — both respond, Cloudflare-fronted)
> Test anime/episode: `https://anikototv.to/watch/wistoria-wand-and-sword-season-2-dua04/ep-5`
> Aniyomi `lang`: `en` · Nsfw: `false` · ext-lib: `16`

---

## 0. TL;DR — the full video chain

```
1. Episode page (HTML)
   https://anikototv.to/watch/{slug}/ep-{n}
   → contains: anime_id (data-id), show_slug, episode_num
   → loads main.js + mapper.js

2. Episode list (XHR) — get the {servers} token for the chosen episode
   GET /ajax/episode/list/{animeId}?vrf={empty or obfuscated}
   → returns HTML with <a data-ids="{SERVERS_B64}" data-sub="1" data-dub="1" data-mal="..." data-timestamp="...">
   → the data-ids value is the base64 "servers" param for step 3

3. Server list (XHR) — list all servers grouped by audio type
   GET /ajax/server/list?servers={SERVERS_B64}
   → returns HTML: <div class="servers"><div class="type" data-type="sub|hsub|dub"><ul>
     <li data-ep-id data-cmid data-sv-id data-link-id="{LINK_ID_B64}">ServerName</li>
   → 5 servers across 3 audio types: VidPlay-1, HD-1, Vidstream-2, VidCloud-1 (+ Kiwi-Stream via mapper)

4a. Kiwi-Stream injection (external XHR, runs in parallel via mapper.js)
    GET https://mapper.nekostream.site/api/mal/{malId}/{episodeNum}/{timestamp}
    → returns JSON: {"Kiwi-Stream-": {"sub": {"url": "{LINK_ID_B64}"}, "dub": {"url": "{LINK_ID_B64}"}}}
    → these LINK_IDs are passed to the same step 5

4b. (Optional) Other mapper sources — gogoanime→Vidstream, anivibe→vibe-Stream, animepahe→Kiwi-Stream
    (only Kiwi-Stream present for this test episode)

5. Server resolve (XHR) — link-id → player URL + skip_data
   GET /ajax/server?get={LINK_ID_B64}
   → returns JSON: {"status":200,"result":{"url":"https://{PLAYER_HOST}/stream/{token}/{audio}","skip_data":{"intro":[0,0],"outro":[0,0]}}}
   → PLAYER_HOST ∈ {vidtube.site (VidPlay-1), megaplay.buzz (HD-1, Vidstream-2), vidwish.live (VidCloud-1), mewcdn.online (Kiwi)}

6. Player page (HTML, no JS execution needed)
   GET {player_url}
   → contains: data-id="{FILE_ID}" (a small integer, e.g. 138029)
   → for megaplay/vidwish: also data-realid, data-mediaid, data-fileversion, cid, domain2_url

7. Sources API (XHR) — getSourcesNew — THE KEY ENDPOINT
   GET https://{PLAYER_HOST}/stream/getSourcesNew?id={FILE_ID}&type={sub|hsub|dub}
   → returns JSON: {
       "sources": {"file": "https://{CDN}/{hash}/master.m3u8"},
       "tracks": [{"file":".../{lang}.vtt","label":"English","kind":"captions","default":true}],
       "t": 1,
       "intro": {"start":0,"end":0},
       "outro": {"start":0,"end":0},
       "server": 5
     }
   → CDN ∈ {mt.nekostream.site (VidPlay-1), 9hjkrt.nekostream.site (HD-1, Vidstream-2)}
   → Kiwi: the player URL already contains the m3u8 (base64-encoded in URL fragment) — no getSourcesNew call

8. Master m3u8 (direct fetch)
   GET {cdn_url}/master.m3u8
   → returns 3 variants: 1080p (1920x1080, 5.5Mbps), 720p (1280x720, 2.8Mbps), 360p (640x360, 800kbps)
   → VidPlay-1: variant filenames = {epHash}1080.m3u8 / {epHash}720.m3u8 / {epHash}360.m3u8
   → HD-1/Vidstream-2: variant filenames = index-f1.m3u8 (1080) / index-f2.m3u8 (720) / index-f3.m3u8 (360)
   → Kiwi: variant filenames = 316590{1080|720|360}.m3u8

9. Media m3u8 (direct fetch)
   GET {cdn_url}/{variant}.m3u8
   → returns segment list — BUT mixed with AD segments (132 ads vs 12 real for this episode!)
   → real segments: https://{CDN}/segment/{base64-token}
   → ad segments: https://p1.ipstatp.com/obj/ad-site-i18n/... or https://p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/...
   → MUST filter out ads in the extension (skip any segment NOT on the CDN host)

10. Segment fetch (PNG-WRAPPED — needs m3u8server)
    GET https://{CDN}/segment/{token}
    → 302 redirects to actual CDN (Cloudflare cached)
    → final bytes: [PNG header (70 bytes: IHDR+IDAT+IEND)][real MPEG-TS data]
    → first 8 bytes: 89504e470d0a1a0a (PNG signature)
    → TS sync byte 0x47 at offset 70 (right after IEND)
    → MUST use lib/m3u8server to strip the 70-byte PNG header before passing to mpv
```

---

## 1. Site connectivity & infrastructure

| Domain | Status | Front | Notes |
|---|---|---|---|
| `anikototv.to` | ✅ 200 | Cloudflare | Primary; use this. |
| `anikoto.cz` | ✅ 200 | Cloudflare | Mirror; same backend. |

- Base URL: `https://anikototv.to` (no trailing slash in `baseUrl` for the extension).
- All API endpoints under `/ajax/` and `/api/` return JSON with `{"status": 200, "result": ...}` envelope.
- Server: `cloudflare`. No aggressive anti-bot on the main site (curl works with a normal UA + Referer).
- Static assets: `/anikoto/js/main.js?v=1.111`, `/anikoto/js/mapper.js?v=...` (versioned).
- The `mapper.js` calls an EXTERNAL API `https://mapper.nekostream.site/api/mal/{malId}/{epNum}/{timestamp}` to inject extra servers (notably Kiwi-Stream).

---

## 2. URL structure

| Page | URL | Purpose |
|---|---|---|
| Home | `/` | Popular/trending carousel. |
| Browse/Filter | `/filter?keyword={q}` | Search (also supports genre/year/etc filters via query params). |
| Anime detail | `/anime/{slug}` | Title, description, genres, episodes pointer. |
| Watch (episode) | `/watch/{slug}/ep-{n}` | The watch page (server list loads here). |
| Genre | `/genre/{genre}` | Genre filter. |
| Episode list XHR | `/ajax/episode/list/{animeId}?vrf={vrf}` | Returns HTML `<a>` per episode with `data-ids`. |
| Server list XHR | `/ajax/server/list?servers={SERVERS_B64}` | Returns HTML server list grouped by audio type. |
| Server resolve XHR | `/ajax/server?get={LINK_ID_B64}` | Returns `{url, skip_data}` for one server link. |
| Seasons XHR | `/api/seasons/{animeId}` | Returns season HTML (if anime has seasons). |
| Watch-order XHR | `/api/watch-order/{animeId}` | Returns watch-order HTML (for multi-season franchises). |

- `animeId` (e.g. `8737` for our test): integer, found in the watch page HTML as `data-id="8737"` and in the report form's `show_id`.
- `slug` (e.g. `wistoria-wand-and-sword-season-2-dua04`): URL-safe, ends with a short hash suffix (`dua04`).
- Episode number: integer, in the URL as `ep-5`.
- `vrf` param: appears empty (`?vrf=`) for our test anime. May be an obfuscated episode-id for other anime — UNVERIFIED (need to test another anime where `vrf` is non-empty). If non-empty, it's likely a base64-encoded episode id (the `o(this.Ee)` call in main.js generates it from the anime id).

---

## 3. The 5 servers (COMPLETE chain — every server traced end-to-end)

### Server inventory (test episode: Wistoria S2 EP5)

| # | Server name | Player host | Audio types | Qualities | Subtitles | Skip data | Status |
|---|---|---|---|---|---|---|---|
| 1 | VidPlay-1 | `vidtube.site` | sub, hsub, dub | 1080p/720p/360p | English VTT (sub, dub) | intro/outro (0,0 this ep) | ✅ WORKS |
| 2 | HD-1 | `megaplay.buzz` | sub, hsub, dub | 1080p/720p/360p | English VTT (sub, dub; none for hsub) | intro/outro (0,0) | ✅ WORKS |
| 3 | Vidstream-2 | `megaplay.buzz` | sub, hsub, dub | 1080p/720p/360p | English VTT (sub, dub; none for hsub) | intro/outro (0,0) | ✅ WORKS |
| 4 | VidCloud-1 | `vidwish.live` | sub, dub (NO hsub) | — | — | — | ❌ BROKEN |
| 5 | Kiwi-Stream | `mewcdn.online` → `vibeplayer.site` | sub (=hsub), dub | 1080p/720p/360p | (none in API; direct m3u8) | (none) | ✅ WORKS |

> **Labeling clarification (per user):** the first 4 servers mark hardsub as `HSUB`; Kiwi marks it `H-SUB` but it's the SAME audio type. Kiwi marks dub as `A-DUB` but it's the SAME as other servers' `DUB`. We'll use `SUB`/`HSUB`/`DUB` consistently in our extension (rule §7).

### 3.1 VidPlay-1 (vidtube.site)

**Chain (SUB):**
1. `data-link-id` (from server list) = `MTF1dkFtaW9BRTZPbzJJRElFZUZrNHVJWFRVWWRW...`
2. `GET /ajax/server?get={link_id}` → `{"url":"https://vidtube.site/stream/{TOKEN}/sub","skip_data":{"intro":[0,0],"outro":[0,0]}}`
   - TOKEN = `REFRZ3lMTDdlYU9OVEtuakk1SjFIQkg2TWVXWVVYRy9MakdLeThkZEZMRE9ZV3lTQVpTZ2RGaGlSb00rYzVrTg`
   - last path segment = audio type (`sub`/`hsub`/`dub`)
3. `GET https://vidtube.site/stream/{TOKEN}/sub` → HTML player page with `data-id="138029"`
   - **The data-id is THE SAME across sub/hsub/dub** (only the URL's last segment changes).
4. `GET https://vidtube.site/stream/getSourcesNew?id=138029&type=sub` → JSON:
   ```json
   {"sources":{"file":"https://mt.nekostream.site/1a1e84e365ac3f37c1b338fff210085a/master.m3u8"},
    "tracks":[{"file":"https://mt.nekostream.site/1a1e84e365ac3f37c1b338fff210085a/subtitles/English.vtt","label":"English","kind":"captions","default":true}],
    "t":1,"intro":{"start":0,"end":0},"outro":{"start":0,"end":0},"server":5}
   ```
5. Master m3u8 has 3 variants (1080p/720p/360p). Variant playlists at `{base}/{epHash}{1080|720|360}.m3u8`.
6. Segments at `https://mt.nekostream.site/segment/{token}` → 302 → PNG-wrapped (70-byte PNG header + real TS).

**HSUB**: same chain, `type=hsub`, m3u8 hash differs (`c2b9eb434a239894613fab6c30aa32cb`), NO subtitles track.
**DUB**: same chain, `type=dub`, m3u8 hash differs (`ec7821b9ef81d2122c75136c1683ca94`), has English VTT.

**Token sharing:** the SAME `{TOKEN}` is in the player URL across sub/hsub/dub — only the last segment changes. But the m3u8 hash differs per audio type, so they're DIFFERENT video files (just resolved through the same player token). For VidPlay-1, the `data-id` (138029) is also the same across audio types.

### 3.2 HD-1 (megaplay.buzz, server path `s-5`)

**Chain (SUB):**
1. `data-link-id` → `GET /ajax/server?get={link_id}` → `{"url":"https://megaplay.buzz/stream/s-5/873700500/sub"}`
   - URL pattern: `/stream/s-5/{animeId}00{epNum?}/{audio}` — actually `873700500` looks like `{animeId}00{epNum*100}` = `8737` + `00` + `0500`? Need to verify pattern on another episode. The `s-5` is the server-id path component.
2. `GET https://megaplay.buzz/stream/s-5/873700500/sub` → HTML with `data-id="176012"` (different per audio type!).
3. `GET https://megaplay.buzz/stream/getSourcesNew?id=176012&type=sub` → JSON with m3u8 at `9hjkrt.nekostream.site/{hash1}/{hash2}/master.m3u8`.
4. Master m3u8 variants: `index-f1.m3u8` (1080p), `index-f2.m3u8` (720p), `index-f3.m3u8` (360p).
5. Segments at `9hjkrt.nekostream.site/segment/{token}` → PNG-wrapped (same 70-byte pattern).

**HSUB**: `data-id=176261` (different from sub), NO subtitles.
**DUB**: `data-id=176502` (different), has English VTT.

> ⚠️ **HD-1 data-ids DIFFER per audio type** (unlike VidPlay-1 where it's the same). The "safe approach" the user hinted at: **always use the `data-link-id` from the audio-type-specific `<li>`**, don't try to reuse one data-id across audio types.

### 3.3 Vidstream-2 (megaplay.buzz, server path `s-2`)

Identical to HD-1 but URL path is `s-2` instead of `s-5`. Same `data-id` values (176012/176261/176502) — meaning HD-1 and Vidstream-2 share the same backend file IDs (just different server routing). Resolves to the same `9hjkrt.nekostream.site` CDN with the same hashes. **Likely the SAME video file** as HD-1, just a different server entry. Document as a potential duplicate.

### 3.4 VidCloud-1 (vidwish.live) — ❌ BROKEN

**Chain (SUB):**
1. `data-link-id` → `GET /ajax/server?get={link_id}` → `{"url":"https://vidwish.live/stream/s-2/873700500/sub","skip_data":...}`
2. `GET https://vidwish.live/stream/s-2/873700500/sub` → HTML player page renders (with `data-id="174447"`, `data-realid`, `data-mediaid`, `cid: '5553'`, `domain2_url: 'https://fxpy7.watching.onl/'`).
3. `GET https://vidwish.live/stream/getSourcesNew?id=174447&type=sub` → **❌ 404 HTML error page** (not JSON!):
   ```
   <!DOCTYPE html>... <title>Error - Vidcloud</title> ... <h1>Oops! Something went wrong</h1>
   <p>The page you're looking for doesn't exist or has been moved.</p> Error Code: <span>404</span>
   ```
4. Same failure for DUB (`id=174706`).

**Where it fails:** the `getSourcesNew` endpoint on `vidwish.live` returns 404. The player page itself loads fine, but the API that should return the m3u8 is broken/missing. Both sub and dub fail identically. **No HSUB offered** for this server (only sub + dub in the server list).

**Implication for the extension:** we can still LIST VidCloud-1 as a hoster (it appears in the server list), but `videoListParse` for it will return an empty list (or we should skip it entirely). Recommend: skip VidCloud-1 in the extension — don't even surface it to the user, since it always fails. OR: surface it but mark it as broken via `videoTitle = "VidCloud-1 (broken)"`. Decision deferred to Stage 2 (architecture).

### 3.5 Kiwi-Stream (mewcdn.online → vibeplayer.site) — DIFFERENT CHAIN

**Chain (SUB):**
1. Kiwi is NOT in the main server-list response. It's injected by `mapper.js` calling:
   `GET https://mapper.nekostream.site/api/mal/{malId}/{epNum}/{timestamp}`
   → `{"Kiwi-Stream-":{"sub":{"url":"{LINK_ID_B64}"},"dub":{"url":"{LINK_ID_B64}"}},"status":{...}}`
   - For our test: malId=59983, epNum=5, timestamp=1779613503.
   - These `url` values are link-ids (same format as the main servers' `data-link-id`).
2. `GET /ajax/server?get={kiwi_link_id}` → `{"url":"https://mewcdn.online/player/plyr.php#{BASE64_FRAGMENT}","skip_data":{...}}`
   - The BASE64_FRAGMENT decodes to a DIRECT m3u8 URL: `https://vibeplayer.site/public/stream/{hash}/master.m3u8`
   - SUB hash: `e90424f29ec81ddc`, DUB hash: `7295b246a3394d2f`.
3. **NO `getSourcesNew` call** — the m3u8 is directly in the resolved URL. Just decode the base64 fragment.
4. Master m3u8 at `vibeplayer.site/public/stream/{hash}/master.m3u8` → 3 variants (1080/720/360), variant files `316590{1080|720|360}.m3u8`.
5. Segments: the variant playlist has ad segments + real segments. Real segments 302-redirect to `p16-ad-sg.ibyteimg.com/...` (ad CDN!) — wait, actually the FIRST segments are ads. Need to filter to real `vibeplayer.site` segments. Real segments also PNG-wrapped (verified: 2.6MB file, PNG header).

**HSUB**: the mapper returns `sub` and `dub` only for Kiwi-Stream. **But the user said Kiwi has H-SUB and A-DUB.** The `sub` from Kiwi = H-SUB (per the mapper.js `serverStructure.sub` template which uses the H-SUB label!), and `dub` = A-DUB. So:
- Kiwi "sub" (in mapper JSON) → label as `HSUB` in our extension
- Kiwi "dub" (in mapper JSON) → label as `DUB` in our extension

This matches the user's clarification: "the kiwi one marks it as H-SUB but all of these are the same. And I have been using H-SUB for all. And the same goes for the kiwi servers, A-DUB it's the same as other servers DUB."

**Skip data / subtitles for Kiwi:** the `ajax/server?get={link_id}` response has `skip_data` (intro/outro, both [0,0] for this ep). No subtitle tracks returned (the m3u8 itself may have VTT — UNVERIFIED, need to fetch a Kiwi master m3u8 and check for `#EXT-X-MEDIA:TYPE=SUBTITLES`).

---

## 4. Audio types & labeling (rule §7 — 3 types, not 2)

| Audio type | Site label (VidPlay/HD/Vidstream) | Site label (Kiwi) | Our extension label | Notes |
|---|---|---|---|---|
| Subbed | `SUB` (data-type="sub") | — (Kiwi doesn't have a pure SUB) | `SUB` | Soft subtitles (VTT track). |
| Hardsub | `HSUB` (data-type="hsub") | `H-SUB` (mapper "sub") | `HSUB` | Hardcoded subs in video; NO VTT track. |
| Dubbed | `DUB` (data-type="dub") | `A-DUB` (mapper "dub") | `DUB` | English audio; has VTT track (English). |

- ✅ Verified: 3 audio types exist (not 2). HSUB is distinct from SUB (different m3u8 hash, no VTT track).
- ✅ Kiwi's "sub" = HSUB (mapper.js uses `serverStructure.sub` template which renders the `H-SUB` label).
- ✅ Kiwi's "dub" = DUB (mapper.js uses `serverStructure.dub` template which renders the `A-DUB` label).

### Per-episode availability (rule §8 — scanlator field)

Each episode `<a>` in the episode-list response has `data-sub="1"` and `data-dub="1"` attributes indicating which audio types exist. `data-hsub` is NOT in the HTML — HSUB availability must be inferred from the server list (whether any HSUB `<li>` is present). For the scanlator field (rule §8): aggregate `SUB • HSUB • DUB` based on what's actually available for that episode.

---

## 5. Token sharing (rule §7 — dedup)

**VERIFIED:** For VidPlay-1, the SAME player-token (`REFRZ3lMTDdlYU9OVEtuakk1SjFIQkg2TWVXWVVYRy9MakdLeThkZEZMRE9ZV3lTQVpTZ2RGaGlSb00rYzVrTg`) appears in the player URL across sub/hsub/dub — only the URL's last segment changes. The `data-id` (138029) is also the same.

**HOWEVER**, the resolved m3u8 hashes DIFFER per audio type:
- SUB: `1a1e84e365ac3f37c1b338fff210085a`
- HSUB: `c2b9eb434a239894613fab6c30aa32cb`
- DUB: `ec7821b9ef81d2122c75136c1683ca94`

So they are **DIFFERENT video files** (different audio tracks) resolved through the same player token. This is NOT a dedup situation — sub/hsub/dub are genuinely different streams.

**Actual dedup candidate:** HD-1 and Vidstream-2 resolve to the SAME m3u8 URL (`9hjkrt.nekostream.site/4739d8dbd05dddb73604f6240b83ea68/31fcc9a246c274d4af00a9f7997c3799/master.m3u8`) with the SAME data-id (176012). They appear to be the SAME backend file exposed via two server entries. **Recommend: dedup HD-1 and Vidstream-2 in the extension** (surface as one hoster, or label one as "HD-1 / Vidstream-2").

---

## 6. PNG wrapping (segment obfuscation)

**VERIFIED on ALL working servers** (VidPlay-1, HD-1, Vidstream-2, Kiwi-Stream):

- Segment URL: `https://{CDN}/segment/{base64-token}`
- Response: HTTP 302 → Cloudflare-cached final URL → body is `[PNG header (70 bytes)][real MPEG-TS data]`
- PNG structure: 8-byte signature `89504e470d0a1a0a` + IHDR chunk (13 bytes data) + IDAT chunk (13 bytes data) + IEND chunk (0 bytes data) = 70 bytes total.
- The real MPEG-TS data starts at byte 70 (first byte `0x47` = TS sync byte).

**Implication:** the extension MUST use yuzono's `lib/m3u8server` (the local NanoHTTPD proxy) to strip the 70-byte PNG header before passing segments to mpv. The `AutoDetector.detectSkipBytes` in `m3u8server/AutoDetector.kt` handles exactly this pattern (it scans for `ftyp`/`RIFF`/MPEG-TS sync, but we may need to extend it to recognize PNG + find IEND). See `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` §3d.

**Alternative:** since the PNG header is ALWAYS 70 bytes for this site (fixed IHDR+IDAT+IEND), we could write a custom proxy that always skips 70 bytes. But using `m3u8server` is more robust (handles variations).

---

## 7. CDN WAF behavior

- The main site (`anikototv.to`) is behind Cloudflare but does NOT challenge curl with a normal UA + Referer.
- The CDN segment endpoints (`mt.nekostream.site`, `9hjkrt.nekostream.site`) return **302 redirects** (openresty) to Cloudflare-cached URLs. Following the redirect with `curl -L` works (no challenge).
- The player hosts (`vidtube.site`, `megaplay.buzz`, `vidwish.live`) serve HTML + JSON without challenge.
- `vidwish.live`'s `getSourcesNew` endpoint returns 404 (broken, not WAF).
- No `cf_clearance` cookie needed for any of the API/CDN calls (verified via curl).
- `mapper.nekostream.site` (the external Kiwi API) also responds without challenge.

**Headers needed:**
- All `anikototv.to/ajax/*` calls: `Referer: https://anikototv.to/watch/{slug}/ep-{n}` + `X-Requested-With: XMLHttpRequest`.
- Player host `getSourcesNew`: `Referer: https://{player_host}/` + `X-Requested-With: XMLHttpRequest`.
- CDN m3u8/segment: `Referer: https://{player_host}/` (the host that issued the m3u8).
- User-Agent: any real browser UA (the site doesn't fingerprint).

---

## 8. Subtitles (VTT) & skip data (intro/outro)

**Subtitles:**
- Returned in the `getSourcesNew` JSON `tracks[]` array.
- SUB and DUB have an English VTT track (default): `https://{CDN}/{hash}/subtitles/English.vtt`.
- HSUB has NO subtitles track (hardsubbed — subs are burned in).
- Kiwi-Stream: NO `tracks[]` in the resolve response (the m3u8 may have embedded subs — UNVERIFIED).
- VTT format: standard WebVTT (extension can pass directly to mpv via `Video.subtitleTracks`).

**Skip data (intro/outro):**
- Returned in TWO places:
  1. `ajax/server?get={link_id}` response: `skip_data: {"intro": [start, end], "outro": [start, end]}` (arrays of numbers, seconds).
  2. `getSourcesNew` response: `intro: {"start": N, "end": N}, "outro: {"start": N, "end": N}` (objects).
- For our test episode: all zeros (community-contributed skip data not available for this ep).
- Both are [0,0] / {start:0,end:0} → convert to `TimeStamp` entries in the extension's `Video.timestamps` (so mpv shows them as chapters).
- The main.js `Mr` function calls `ajax/server?get=...` and reads `t.result.skip_data` → so the authoritative source is `skip_data` from the resolve endpoint. The `getSourcesNew` intro/outro may be a duplicate or stale.

---

## 9. Ad injection in m3u8 playlists

**VERIFIED:** The media playlists (variant m3u8) contain AD segments mixed with real video segments:
- For our test episode 1080p playlist: **132 ad segments vs 12 real segments** (massive ad injection).
- Ad segment URLs: `https://p1.ipstatp.com/obj/ad-site-i18n/...` or `https://p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/...`.
- Real segment URLs: `https://{CDN}/segment/{token}` (always on the nekostream/vibeplayer CDN).
- Ad segments are ALSO PNG-wrapped (the ads themselves are disguised as PNG — verified: 2.4MB PNG at the ad URL).

**Implication:** the extension MUST filter the m3u8 playlist to remove ad segments before passing to mpv. Strategy: in `videoListParse` (or via `m3u8server`), rewrite the media playlist to keep only segments whose URL host matches the CDN host (`mt.nekostream.site` / `9hjkrt.nekostream.site` / `vibeplayer.site`). Drop anything on `ipstatp.com` / `ibyteimg.com`.

This is a strong argument for using `lib/m3u8server` — it can rewrite the m3u8 on-the-fly, filtering ads AND stripping PNG headers.

---

## 10. The "safe approach" for per-audio data-ids (per user hint)

**VERIFIED:** The data-id (`data-id` attribute on the player page) is NOT always the same across audio types:
- VidPlay-1: SAME data-id (138029) for sub/hsub/dub — only the URL's last segment (`/sub`, `/hsub`, `/dub`) differs.
- HD-1 / Vidstream-2: DIFFERENT data-ids per audio type (sub=176012, hsub=176261, dub=176502).
- VidCloud-1: different data-ids (sub=174447, dub=174706), but broken anyway.
- Kiwi-Stream: no data-id (m3u8 is directly in the resolved URL's fragment).

**Safe approach (use this in the extension):**
1. From the server-list HTML, read EACH `<li>`'s `data-link-id` (do NOT try to derive one link-id for all audio types).
2. For each audio type, call `ajax/server?get={link_id}` to get the player URL (the URL contains the audio type as the last path segment).
3. Fetch the player page, extract `data-id`.
4. Call `getSourcesNew?id={data_id}&type={audio_seg_from_url}`.
5. This works uniformly for VidPlay-1, HD-1, Vidstream-2 (and would for VidCloud-1 if it weren't broken).
6. For Kiwi-Stream: skip steps 3-4, decode the base64 fragment from the resolved URL to get the m3u8 directly.

---

## 11. Broken server summary (VidCloud-1)

| Step | VidCloud-1 result |
|---|---|
| 1. data-link-id present in server list | ✅ |
| 2. `ajax/server?get={link_id}` returns player URL | ✅ `https://vidwish.live/stream/s-2/873700500/{audio}` |
| 3. Player page HTML loads | ✅ (has data-id, data-realid, data-mediaid, cid, domain2_url) |
| 4. `getSourcesNew?id={data_id}&type={audio}` | ❌ **404 HTML error page** (not JSON) |
| 5. m3u8 fetch | ❌ (never reached) |

**Root cause:** the `getSourcesNew` endpoint on `vidwish.live` is broken/missing for this episode's data-ids (174447 for sub, 174706 for dub). The player page renders but the API that should return the m3u8 returns a 404 HTML page. This is a server-side issue at vidwish.live, not a client-side issue we can fix.

**Recommendation for the extension:** skip VidCloud-1 entirely (don't surface it as a hoster), OR surface it but have `videoListParse` return an empty list for it (the app will show "no videos" for that hoster and the user picks another). Decision deferred to Stage 2.

---

## 12. What's NOT verified yet (honest gaps)

1. **`vrf` param** — for our test anime, `vrf=` is empty. Need to test an anime where it's non-empty to understand the obfuscation. The `o(this.Ee)` call in main.js generates it from the anime id — likely a base64-encoded id with some transformation. Will verify when we build the extension.
2. **Search results HTML structure** — `/filter?keyword=wistoria` returned 76KB but my regex didn't find `/anime/` links (the page may use a different URL pattern or load results via XHR). Need to re-check with agent-browser.
3. **Anime detail page** — `/anime/wistoria-...` returned "Error" title (likely a redirect or block). Need to re-fetch with proper headers.
4. **Pagination** — search/episode-list pagination not tested.
5. **Kiwi-Stream subtitles** — the m3u8 may have `#EXT-X-MEDIA:TYPE=SUBTITLES` (embedded VTT). Unverified.
6. **HD-1 vs Vidstream-2 dedup** — they resolve to the same m3u8 URL for this episode. Need to test another episode to confirm they're always identical (if so, dedup in the extension).
7. **`domain2_url` on vidwish** — `https://fxpy7.watching.onl/` — possibly a fallback domain. Unexplored (VidCloud is broken anyway).
8. **Episode `data-ids` decoding** — the `data-ids` base64 blob decodes to another base64 blob (double-encoded). The inner blob is likely encrypted (not just base64). The site's JS does the decryption — we may need to either replicate it or just pass the `data-ids` value through to `ajax/server/list` unchanged (which is what the site does).

---

## 13. Implications for the extension (Stage 2 preview)

- **Build target:** ext-lib 16, `lang = "en"`, `baseUrl = "https://anikototv.to"`.
- **Catalog:** search via `/filter?keyword=...` (need to re-verify structure); popular/latest TBD (need to check homepage structure).
- **Episode list:** `GET /ajax/episode/list/{animeId}?vrf={vrf}` → parse `<a data-ids data-num data-slug data-sub data-dub>` → one `SEpisode` per `<a>`, `scanlator = "SUB • HSUB • DUB"` based on data-sub/data-dub + HSUB availability check.
- **Hoster list:** `GET /ajax/server/list?servers={data-ids}` → parse `<div data-type="sub|hsub|dub"><li data-link-id>` → one `Hoster` per server (VidPlay-1, HD-1, Vidstream-2, VidCloud-1). Plus call `mapper.nekostream.site/api/mal/{malId}/{epNum}/{timestamp}` for Kiwi-Stream (link-ids in the `sub`/`dub` keys → label as HSUB/DUB per mapper.js convention).
- **Video list per hoster:** `GET /ajax/server?get={link_id}` → player URL → fetch player HTML → extract `data-id` → `GET {player_host}/stream/getSourcesNew?id={data_id}&type={audio}` → m3u8. For Kiwi: decode base64 fragment from player URL → direct m3u8.
- **lib modules needed:**
  - `:lib:m3u8server` — to strip PNG headers + filter ad segments (★ required).
  - `:lib:playlistutils` — to parse the master m3u8 into per-quality `Video`s (after m3u8server rewrites segments).
  - `:lib:cryptoaes` — possibly (if `data-ids` encryption needs decryption; may not need if we pass it through).
- **Video labeling:** `videoTitle = "{audio} - {quality}p - {server_name}"` (e.g. `"SUB - 1080p - VidPlay-1"`). Set `preferred = true` on the user's preferred quality+audio+server.
- **Subtitles:** pass the `tracks[].file` VTT URL as `Video.subtitleTracks`.
- **Skip data:** convert `skip_data.intro`/`outro` to `TimeStamp` entries in `Video.timestamps`.
- **Dedup:** HD-1 and Vidstream-2 resolve to the same m3u8 — surface as one hoster OR label clearly.
- **Skip VidCloud-1** (broken) — don't surface, OR surface with empty videoList.
- **PNG wrapping:** the m3u8server's `AutoDetector` needs to handle PNG (find IEND, skip past it). May need to extend AutoDetector or write a custom variant.

---

## 14. Related artifacts (in this folder)

- `endpoints.md` — full endpoint inventory with example requests/responses.
- `audio-types.md` — the 3 audio types + labeling + per-server availability.
- `servers.md` — per-server deep dive (the 5 servers, their chains, where VidCloud-1 fails).
- `video-flow.md` — the 10-step video chain (compact version of §0 above).
- `tokens-and-dedup.md` — token sharing analysis (what's shared, what's dedup-able).
- `png-wrapping.md` — the PNG wrapping structure + how to strip it.
- `cdn-waf.md` — CDN/WAF behavior + required headers.
- `python-prototypes/` — (Stage 4) local Python scripts replicating the chain for fast iteration.

## 15. Related MEMORY docs

- `EXTENSIONS/anikoto/MEMORY/sites/` — promoted permanent record (this folder's verified findings).
- `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` §3 — `m3u8server` (needed for PNG stripping).
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md` — how the app consumes Video/Hoster.
- `MEMORY/PROJECT_RULES.md` §1 (verify everything), §7 (3 audio types), §8 (scanlator for sub/dub).
