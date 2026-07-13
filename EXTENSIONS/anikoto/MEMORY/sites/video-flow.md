# ANIKOTO — Video Flow (Compact 10-Step Chain)

> Last updated: 2026-06-22 · Status: VERIFIED

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ STAGE A: Catalog (search → anime → episode list)                            │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. GET /filter?keyword={q}            → search results HTML                 │
│ 2. GET /anime/{slug}                  → anime detail HTML                   │
│ 3. GET /ajax/episode/list/{animeId}   → episode list HTML                   │
│    Each <a> has: data-ids, data-num, data-slug, data-mal, data-timestamp,   │
│                  data-sub, data-dub                                         │
│    data-ids = base64 blob (the "servers" param for step 5)                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ STAGE B: Hoster list (episode → servers)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│ 4. (parallel) GET https://mapper.nekostream.site/api/mal/{malId}/{epNum}/   │
│                {timestamp}                                             │
│    → {"Kiwi-Stream-":{"sub":{"url":LINK_ID_B64},"dub":{"url":LINK_ID_B64}}}│
│    (mapper "sub"=HSUB, "dub"=DUB per mapper.js serverStructure)             │
│                                                                              │
│ 5. GET /ajax/server/list?servers={data-ids}                                 │
│    → HTML: <div data-type="sub|hsub|dub"><li data-link-id>ServerName</li>   │
│    5 hosters: VidPlay-1, HD-1, Vidstream-2, VidCloud-1 + Kiwi-Stream        │
│                                                                              │
│  → Build List<Hoster>: one per server, with internalData = link_id +        │
│    audio_type + server_name. First hoster = preferred (after sortHosters).   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ STAGE C: Video list (hoster → videos)                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ For each Hoster:                                                             │
│ 6. GET /ajax/server?get={link_id}                                           │
│    → {"url":"https://{player_host}/stream/{token}/{audio}","skip_data":{...}}│
│                                                                              │
│ 7a. (VidPlay/HD/Vidstream/VidCloud) GET {player_url}                        │
│     → HTML with data-id="{FILE_ID}"                                          │
│     GET https://{player_host}/stream/getSourcesNew?id={FILE_ID}&type={audio}│
│     → {"sources":{"file":m3u8},"tracks":[...],"intro":{...},"outro":{...}}  │
│                                                                              │
│ 7b. (Kiwi) DECODE base64 fragment from {player_url}                         │
│     → direct m3u8 URL (https://vibeplayer.site/public/stream/{hash}/master) │
│                                                                              │
│ 8. GET {m3u8}                          → master playlist (3 variants)       │
│    → 1080p / 720p / 360p                                                    │
│                                                                              │
│  → Build List<Video>: one per variant. videoTitle = "{AUDIO} - {QUALITY}p - │
│    {SERVER}". Set Video.headers (Referer = player_host). Set                │
│    Video.subtitleTracks from tracks[]. Set Video.timestamps from skip_data. │
│    Set Video.preferred = true on user's preferred (audio+quality+server).   │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ STAGE D: Playback (resolveVideo → m3u8server proxy → mpv)                   │
├─────────────────────────────────────────────────────────────────────────────┤
│ 9. (resolveVideo override) Rewrap the m3u8 via lib/m3u8server:              │
│    - Filter out ad segments (anything not on the CDN host)                  │
│    - Strip 70-byte PNG header from each segment                              │
│    - Return local proxy URL (http://localhost:{port}/m3u8?url={encoded})    │
│                                                                              │
│ 10. mpv loads the local proxy URL → m3u8server fetches real segments,       │
│     strips PNG, streams clean TS to mpv.                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Key decision points (Stage 2 will finalize)

- **VidCloud-1**: skip entirely OR surface with empty videoList (broken).
- **HD-1 vs Vidstream-2**: dedup (same m3u8) OR keep separate with clear labels.
- **m3u8server usage**: required (PNG + ad filtering). Port `lib/m3u8server` from yuzono with v16 Video-ctor fix.
- **resolveVideo**: needed to rewrite the m3u8 via m3u8server at play time (the m3u8 URL itself doesn't need per-play resolution, but the proxy rewrite does).
- **Lazy hosters**: probably not needed (only 5 hosters, fetch is fast). Set `Hoster.videoList = null` and let the app call `getVideoList(hoster)` for each.
