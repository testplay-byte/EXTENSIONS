# ANIKOTO Site Analysis (Permanent Record)

> Verified 2026-06-22 (session 06). Promoted from `EXTENSIONS/anikoto/MEMORY/workflow/01_WEBSITE_RESEARCH/ANIKOTO/`.
> Target: `https://anikototv.to` · lang: `en` · ext-lib: 16

## Documents

1. **`site-analysis.md`** — ★ the full 10-step video chain, server inventory, TL;DR. Start here.
2. **`catalog-and-episodes-analysis.md`** — ★ the catalog side: popular/latest/search/filters/details/episodes. Start here for catalog impl.
3. **`endpoints.md`** — every API endpoint with params + response shape + required headers.
4. **`audio-types.md`** — the 3 audio types (SUB/HSUB/DUB) + per-server availability + Kiwi mapping.
5. **`servers.md`** — per-server deep dive (5 servers, their chains). Updated session 26 (getSources migration).
6. **`video-flow.md`** — compact 10-step chain diagram (Stage A→D).
7. **`tokens-and-dedup.md`** — token sharing analysis (what's shared, HD-1/Vidstream-2 dedup).
8. **`png-wrapping.md`** — the 70-byte PNG header structure + how to strip it (needs m3u8server).
9. **`cdn-waf.md`** — CDN/WAF behavior + required headers + ad filtering.
10. **`getsources-migration-and-id-analysis.md`** — ★ session 26: the getSourcesNew→getSources migration + extension ID stability analysis. Read this if Vidstream-2/VidCloud-1 break or if saved anime disappear after updates.

## Key facts (quick reference)

- **5 servers**: VidPlay-1 (vidtube.site), HD-1 (megaplay.buzz `s-5`), Vidstream-2 (megaplay.buzz `s-2`), VidCloud-1 (vidwish.live, ✅ fixed session 26), Kiwi-Stream (mewcdn.online → vibeplayer.site).
- **3 audio types**: SUB, HSUB, DUB (Kiwi's mapper "sub" = HSUB, "dub" = DUB).
- **3 qualities**: 1080p, 720p, 360p (all servers × all audio types).
- **Key video API** (★ updated session 26): `GET /ajax/server?get={link_id}` → player URL; then:
  - vidtube.site → `GET {host}/stream/getSourcesNew?id={file_id}&type={audio}` → m3u8 + subs (data-id SHARED across audio, needs type param)
  - megaplay.buzz / vidwish.live → `GET {host}/stream/getSources?id={file_id}` → m3u8 + subs (data-id is audio-specific, NO type param)
- **Key catalog API**: `GET /ajax/anime/search?keyword={q}` → live autosuggest (VERIFIED working). `/filter?{params}` is currently broken via curl (may need session).
- **Anime details**: `GET /watch/{slug}` (NOT `/anime/{slug}` which 404s). Parse `.binfo` + `.bmeta` + `.brating`.
- **Episode list**: `GET /ajax/episode/list/{animeId}?vrf=` → parse `<a data-ids data-num data-sub data-dub>`.
- **PNG wrapping**: every segment has a 70-byte PNG header before the real MPEG-TS data. Needs `lib/m3u8server`.
- **Ad injection**: 132 ad segments vs 12 real in test playlist. Filter by CDN host.
- **VidCloud-1** (session 26): uses `getSources` (not `getSourcesNew`). Works where vidwish.live has the data; fails gracefully (server-side error) on some episodes.
- **HD-1 ≡ Vidstream-2**: same m3u8, same data-ids. Dedup candidate.
- **Extension ID** (session 26): source `id = MD5("anikoto/en/11")`. versionId=11 is STABLE — do NOT bump with versionCode. See `getsources-migration-and-id-analysis.md` §2.
- **No popular/latest endpoints**: homepage is static SEO; use `/filter?sort=most-viewed` (may need session) or fallback to hardcoded "Popular searches" list.

## Related MEMORY

- `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` §3 — `m3u8server` (required for PNG stripping).
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md` — how the app consumes Video/Hoster.
- `MEMORY/PROJECT_RULES.md` §1, §7, §8.
