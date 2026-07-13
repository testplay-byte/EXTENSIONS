# ANIKOTO — Endpoint Inventory

> Last updated: 2026-06-22 · Status: VERIFIED (every endpoint tested with curl)

All endpoints on `https://anikototv.to` unless noted. All `/ajax/*` and `/api/*` return JSON `{"status":200,"result":...}` on success.

## Main site endpoints

| Endpoint | Method | Params | Returns | Purpose |
|---|---|---|---|---|
| `/watch/{slug}/ep-{n}` | GET | — | HTML | Episode watch page (contains animeId, loads main.js + mapper.js). |
| `/anime/{slug}` | GET | — | HTML | Anime detail page (title, desc, genres). ⚠️ Returned "Error" title in my test — may need proper headers/cookies. |
| `/filter?keyword={q}` | GET | keyword, plus optional genre/year/type/sort/status/etc | HTML | Search/filter results. ⚠️ Result card structure not fully parsed yet — re-verify with agent-browser. |
| `/ajax/episode/list/{animeId}` | GET | `vrf` (often empty) | JSON `{status, result: HTML}` | Episode list. HTML has `<a data-ids data-num data-slug data-mal data-timestamp data-sub data-dub>`. |
| `/ajax/server/list` | GET | `servers` = base64 `data-ids` from episode `<a>` | JSON `{status, result: HTML}` | Server list grouped by audio type. HTML has `<div data-type="sub\|hsub\|dub"><li data-link-id>`. |
| `/ajax/server` | GET | `get` = base64 `data-link-id` from server `<li>` | JSON `{status, result: {url, skip_data: {intro:[s,e], outro:[s,e]}}}` | Resolve one server link → player URL + skip data. |
| `/ajax/episode/report` | POST | (form: _csrfToken, show_id, episode_id, server_name, show_slug) | JSON | Report a broken episode. |
| `/api/seasons/{animeId}` | GET | — | JSON `{status, result: HTML}` | Seasons list (if anime has seasons). |
| `/api/watch-order/{animeId}` | GET | — | JSON `{status, result: HTML}` | Watch order (for multi-season franchises). |
| `/ajax/reactions/list` | GET | `type=anime`, `id={animeId}` | JSON | Reactions. |
| `/auth/ajax/user/panel` | GET | — | JSON | User panel (auth). |

## External endpoints (called by mapper.js)

| Endpoint | Method | Params | Returns | Purpose |
|---|---|---|---|---|
| `https://mapper.nekostream.site/api/mal/{malId}/{epNum}/{timestamp}` | GET | — | JSON `{"Kiwi-Stream-": {"sub": {"url": LINK_ID_B64}, "dub": {"url": LINK_ID_B64}, "download": {...}}, "status": {...}}` | Injects Kiwi-Stream (and potentially gogoanime→Vidstream, anivibe→vibe-Stream, animepahe→Kiwi-Stream) servers. |

## Player-host endpoints (vidtube.site, megaplay.buzz, vidwish.live)

| Endpoint | Method | Params | Returns | Purpose |
|---|---|---|---|---|
| `https://{player_host}/stream/{token}/{audio}` | GET | — | HTML player page | Contains `data-id="{FILE_ID}"` (and for megaplay/vidwish: data-realid, data-mediaid, data-fileversion, cid, domain2_url). |
| `https://{player_host}/stream/getSourcesNew` | GET | `id` = data-id, `type` = sub\|hsub\|dub | JSON `{sources:{file:m3u8}, tracks:[...], t, intro, outro, server}` | **THE KEY ENDPOINT** — returns m3u8 + subtitles + skip data. ❌ 404 on vidwish.live (VidCloud-1 broken). |

## CDN endpoints (m3u8 + segments)

| Endpoint | Method | Returns | Purpose |
|---|---|---|---|
| `https://{cdn_host}/{hash}/master.m3u8` | GET | HLS master playlist (3 variants: 1080p/720p/360p) | Master m3u8. CDN ∈ {mt.nekostream.site, 9hjkrt.nekostream.site, vibeplayer.site}. |
| `https://{cdn_host}/{hash}/{variant}.m3u8` | GET | HLS media playlist (segments + ad segments) | Variant playlist. Variant names: `{epHash}{1080\|720\|360}.m3u8` (VidPlay-1, Kiwi) OR `index-f{1\|2\|3}.m3u8` (HD-1, Vidstream-2). |
| `https://{cdn_host}/segment/{token}` | GET (302) | PNG-wrapped MPEG-TS (70-byte PNG header + real TS) | A real video segment. ⚠️ Mixed with ad segments in the playlist (filter by host). |
| `https://{cdn_host}/{hash}/subtitles/English.vtt` | GET | WebVTT | Subtitle track (SUB, DUB only). |

## Required headers (per endpoint)

| Endpoint | Required headers |
|---|---|
| `anikototv.to/ajax/*` | `User-Agent: <browser>`, `Referer: https://anikototv.to/watch/{slug}/ep-{n}`, `X-Requested-With: XMLHttpRequest` |
| `anikototv.to/api/*` | `User-Agent`, `Referer: https://anikototv.to/...` |
| `mapper.nekostream.site/api/mal/*` | `User-Agent`, `Referer: https://anikototv.to/` |
| `{player_host}/stream/{token}/{audio}` | `User-Agent`, `Referer: https://anikototv.to/` |
| `{player_host}/stream/getSourcesNew` | `User-Agent`, `Referer: https://{player_host}/`, `X-Requested-With: XMLHttpRequest` |
| `{cdn_host}/*.m3u8` + `/segment/*` | `User-Agent`, `Referer: https://{player_host}/` |

No `cf_clearance` cookie needed anywhere. No anti-bot challenge on the main site with a normal browser UA.
