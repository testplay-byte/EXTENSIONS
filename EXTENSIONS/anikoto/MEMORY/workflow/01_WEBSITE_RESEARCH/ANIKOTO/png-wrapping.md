# ANIKOTO — PNG Wrapping (Segment Obfuscation)

> Last updated: 2026-06-22 · Status: VERIFIED (bytes inspected)
> Affects: ALL working servers (VidPlay-1, HD-1, Vidstream-2, Kiwi-Stream)

## The wrapping pattern

Every real video segment at `https://{CDN}/segment/{token}` returns a file with this structure:

```
[ 70-byte PNG image ][ real MPEG-TS data ... ]
```

### PNG header breakdown (70 bytes total)
| Offset | Length | Content | Description |
|---|---|---|---|
| 0 | 8 | `89 50 4E 47 0D 0A 1A 0A` | PNG signature |
| 8 | 4 | `00 00 00 0D` | IHDR chunk length (13) |
| 12 | 4 | `49 48 44 52` | "IHDR" |
| 16 | 13 | (IHDR data: 1×1 pixel, 8-bit RGBA) | tiny 1×1 image |
| 29 | 4 | (IHDR CRC) | |
| 33 | 4 | `00 00 00 0D` | IDAT chunk length (13) |
| 37 | 4 | `49 44 41 54` | "IDAT" |
| 41 | 13 | (IDAT data: compressed pixel data) | |
| 54 | 4 | (IDAT CRC) | |
| 58 | 4 | `00 00 00 00` | IEND chunk length (0) |
| 62 | 4 | `49 45 4E 44` | "IEND" |
| 66 | 4 | (IEND CRC) | |
| **70** | ... | `47 40 11 10 00 42 F0 25 ...` | **Real MPEG-TS data starts here** (0x47 = TS sync byte) |

### Verified on
- VidPlay-1 (`mt.nekostream.site/segment/{token}`): 2,396,500 bytes, PNG header at offset 0, TS sync at offset 70.
- HD-1 (`9hjkrt.nekostream.site/segment/{token}`): same 70-byte PNG header, TS sync at offset 70.
- Vidstream-2: same as HD-1 (same CDN, same segment URLs).
- Kiwi-Stream (`vibeplayer.site` → `p16-ad-sg.ibyteimg.com`): 2,638,832 bytes, same PNG header pattern.

## Why they do this

The PNG wrapper disguises the video segments as image files — likely to evade naive content-type filtering by ad blockers, CDNs, or network middleware. The player JS knows to skip the PNG header; standard video players (mpv) would choke on the PNG bytes.

## How to strip it

### Option A: Use yuzono's `lib/m3u8server` (RECOMMENDED)

`lib/m3u8server` (see `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` §3) is a local NanoHTTPD proxy that:
1. Fetches the original m3u8.
2. Rewrites every segment URL to a local proxy URL (`http://localhost:{port}/segment?url={encoded}`).
3. On segment request: fetches the real segment, runs `AutoDetector.detectSkipBytes` to find how many bytes to skip, streams the rest as `video/mp2t`.

The `AutoDetector.detectSkipBytes` (in `m3u8server/AutoDetector.kt`) currently handles JPEG/PNG/GIF-disguised streams by scanning for `ftyp`/`RIFF`/MPEG-TS sync. **For our PNG case, we need it to recognize the PNG signature and find the IEND chunk, then skip past it.** The existing `MP4_FTYP` branch returns `ftypOffset - 4`; we'd add a `PNG` branch that finds IEND and returns `iendOffset + 8` (IEND chunk = 4-byte length + 4-byte type "IEND" + 0-byte data + 4-byte CRC = 12 bytes past the IEND start, but since IEND has 0-length data, the skip = offset_of_IEND + 4 (length) + 4 (type) + 0 (data) + 4 (CRC) = offset_of_IEND + 12).

Actually, simpler: for our site, the PNG header is ALWAYS exactly 70 bytes (fixed IHDR+IDAT+IEND structure). We could write a custom variant that skips 70 bytes. But extending `AutoDetector` is more robust (handles variations).

### Option B: Custom proxy in the extension

Write a small `Interceptor` or `NanoHTTPD` server in the extension's own code that:
1. Fetches the m3u8, filters ads + rewrites segment URLs to local proxy.
2. On segment request: fetches real segment, drops first 70 bytes, streams rest.

This duplicates `m3u8server` logic. Not recommended — reuse `m3u8server`.

### Option C: Pre-process the m3u8 in `resolveVideo`

In `resolveVideo(video)`:
1. Fetch the master m3u8.
2. Fetch each variant m3u8.
3. Filter out ad segments.
4. For each real segment URL, wrap as `http://localhost:{m3u8server_port}/segment?url={encoded}`.
5. Return a new m3u8 (as a data: URL or a local m3u8server-served URL) with the rewritten segment URLs.
6. m3u8server strips the PNG header on each segment request.

This is the cleanest approach — `resolveVideo` does the m3u8 rewrite, `m3u8server` does the byte stripping.

## Implication for the extension (Stage 2)

- **Port `lib/m3u8server` from yuzono** with the v16 Video-ctor fix (per `MEMORY/ext-lib/03-...md` §8).
- **Extend `AutoDetector.detectSkipBytes`** to handle PNG: find `IEND` chunk, skip past it (IEND + 4-byte CRC).
- **OR** write a site-specific skip: since the PNG is always 70 bytes for ANIKOTO, hardcode 70. (Fragile — if the site changes the PNG structure, this breaks.)
- **Use `resolveVideo`** to rewrite the m3u8 via m3u8server (filter ads + delegate segment fetching).
- **Required `lib` modules:** `:lib:m3u8server` (★ required), `:lib:playlistutils` (for m3u8 parsing — may need adaptation to work with m3u8server).
