# ANIKOTO — Endpoint Inventory

> Last updated: 2026-06-22 (session 11) · Status: VERIFIED (every endpoint tested with curl + reference APK confirmed)

All endpoints on `https://anikototv.to` unless noted. All `/ajax/*` and `/api/*` return JSON `{"status":200,"result":...}` on success.

## Main site endpoints

| Endpoint | Method | Params | Returns | Purpose |
|---|---|---|---|---|
| `/most-viewed?page={n}` | GET | — | HTML | Popular anime list (★ SEO alias for `/filter?sort=most-viewed`). 40 items/page. Selectors: `div#list-items > div.item` + `a.name.d-title` + `div.ani.poster.tip img`. |
| `/latest-updated?page={n}` | GET | — | HTML | Latest-updated anime list (★ SEO alias for `/filter?sort=latest-updated`). 40 items/page. |
| `/filter?keyword={q}&sort={s}&genre[]={g}&term_type[]={t}&status[]={st}&language[]={l}&page={n}` | GET | all optional except `page` | HTML | Search/filter results. 40 items/page. Same selectors as above. Sort values: `default, latest-updated, latest-added, score, name-az, release-date, most-viewed, number_of_episodes`. |
| `/watch/{slug}/ep-{n}` | GET | — | HTML | Episode watch page. Contains `#watch-main[data-id]={animeId}`. Details in `#w-info` (`.binfo > .poster img + .info > h1.title.d-title[data-jp] + .synopsis.mb-3 > .shorting > .content + .bmeta`). |
| `/anime/{slug}` | GET | — | HTML | Anime detail page (title, desc, genres). ⚠️ Returned "Error" title in my test — may need proper headers/cookies. Use `/watch/{slug}/ep-1` instead (confirmed working). |
| `/ajax/episode/list/{animeId}` | GET | `vrf` (★ server does NOT validate — but implement RC4 key `"simple-hash"` for safety), `style=default` | JSON `{status, result: HTML}` | Episode list. HTML has `<ul class="ep-range"><li title="..."><a data-id data-num data-slug data-mal data-timestamp data-sub data-dub data-ids class="active"><b>{n}</b><span class="d-title" data-jp="...">{title}</span></a></li>`. |
| `/ajax/server/list` | GET | `servers` = base64 `data-ids` from episode `<a>` | JSON `{status, result: HTML}` | Server list grouped by audio type. HTML has `<div class="servers"><div class="type" data-type="sub\|hsub\|dub"><li data-link-id="{token}">{ServerName}</li>`. 5 servers: VidPlay-1, HD-1, Vidstream-2, VidCloud-1, Kiwi-Stream. |
| `/ajax/server` | GET | `get` = URL-encoded `data-link-id` from server `<li>` | JSON `{status, result: {url, skip_data: {intro:[s,e], outro:[s,e]}}}` | Resolve one server link → player URL (vidtube.site) + skip data. Non-vidtube URLs are skipped (implicit VidCloud-1 filter). |
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
