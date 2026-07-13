# AnimePahe 180 — APK Info Sheet

> Current release: v16.10 (Build 10, versionCode 10, versionId 1 STABLE)

## Identity

| Field | Value |
|---|---|
| Display name | AnimePahe 180 |
| Package | `eu.kanade.tachiyomi.animeextension.en.animepahe180` |
| extClass | `eu.kanade.tachiyomi.animeextension.en.animepahe.AnimePahe` |
| versionCode | 10 |
| versionName | 16.10 |
| versionId | 1 (STABLE — never bump after publish) |
| Source ID | `MD5("animepahe 180/en/1")` |
| NSFW | false |
| Target site | animepahe.pw |
| Language | en |

## Signing

| Field | Value |
|---|---|
| Keystore | `animepahe-release.jks` (at `DEV/`) |
| Alias | `animepahe` |
| SHA-256 | `cfaee692a7bf280f76027b56408477a0b77a8309fe0b64a8ccadbd9965762c9d` |
| Validity | 10000 days (until 2053) |

## APK builds

| Build | Type | Size | Signing | R8 |
|---|---|---|---|---|
| v16.10 | Release | 262 KB | Signed (v1+v2) | Yes (minified) |
| v16.10 | Debug | 348 KB | Unsigned | No |

## Features

- ✅ Popular + Latest (JSON API `/api?m=airing`)
- ✅ Search (JSON API `/api?m=search`)
- ✅ Filters (22 genres, 52 themes, 5 demographics, season+year)
- ✅ Anime details (HTML parse — title, cover, studios, status, genres, synopsis)
- ✅ Episode list (JSON API + recursive pagination + multi-season renumbering)
- ✅ Episode metadata enrichment (Jikan + AniList + Anikage + Kitsu via OkHttp-first)
- ✅ Video playback (Kwik HLS via JsUnpacker)
- ✅ Settings (Video playback: quality + domain + audio; Episode metadata: thumbnails + titles + descriptions)
- ✅ Fork compatibility (episode.url = `/play/<session>/<epSession>` — valid path)
- ✅ Cloudflare handling (inherited client with CloudflareInterceptor)
- ✅ Toast notifications for errors
- ✅ Comprehensive logcat logging (tag: `Animepahe`)

## Build verification (release v16.10)

- ✅ "Stub!" count: 0
- ✅ $$serializer classes: 19 (R8 didn't strip them)
- ✅ JsUnpacker class: present in release DEX
- ✅ Signing: verified (SHA-256 cfaee692...)
- ✅ ProGuard rules: keep all extension classes + jsunpacker classes + $$serializer classes
