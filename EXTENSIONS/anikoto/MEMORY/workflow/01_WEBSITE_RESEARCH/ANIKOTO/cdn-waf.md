# ANIKOTO — CDN & WAF Behavior

> Last updated: 2026-06-22 · Status: VERIFIED

## Infrastructure

| Layer | Provider | Behavior |
|---|---|---|
| Main site (`anikototv.to`) | Cloudflare | No challenge for curl with normal UA + Referer. |
| External API (`mapper.nekostream.site`) | Cloudflare | No challenge. |
| Player hosts (`vidtube.site`, `megaplay.buzz`, `vidwish.live`) | Cloudflare | No challenge. |
| CDN m3u8/segments (`mt.nekostream.site`, `9hjkrt.nekostream.site`) | Cloudflare + openresty | 302 redirect on `/segment/{token}` → Cloudflare-cached final URL. No `cf_clearance` needed. |
| Kiwi CDN (`vibeplayer.site`, `p16-ad-sg.ibyteimg.com`) | Cloudflare/bytedance | Direct fetch works. |

## No anti-bot challenge

- No Cloudflare "Just a moment" JS challenge on any endpoint.
- No `cf_clearance` cookie required.
- No reCAPTCHA.
- A normal browser User-Agent + Referer header is sufficient everywhere.

## Required headers per endpoint

| Endpoint | User-Agent | Referer | X-Requested-With | Other |
|---|---|---|---|---|
| `anikototv.to/*` (HTML pages) | ✅ browser UA | (none) | (none) | — |
| `anikototv.to/ajax/*` | ✅ | `https://anikototv.to/watch/{slug}/ep-{n}` | ✅ `XMLHttpRequest` | — |
| `anikototv.to/api/*` | ✅ | `https://anikototv.to/...` | (none) | — |
| `mapper.nekostream.site/api/mal/*` | ✅ | `https://anikototv.to/` | (none) | — |
| `{player_host}/stream/{token}/{audio}` (HTML) | ✅ | `https://anikototv.to/` | (none) | — |
| `{player_host}/stream/getSourcesNew` | ✅ | `https://{player_host}/` | ✅ `XMLHttpRequest` | — |
| `{cdn_host}/*.m3u8` | ✅ | `https://{player_host}/` | (none) | — |
| `{cdn_host}/segment/{token}` | ✅ | `https://{player_host}/` | (none) | Follow 302 redirect |

## The 302 redirect on segment fetch

`GET https://{cdn}/segment/{token}` returns:
```
HTTP/2 302
location: https://{cloudflare-cached-url}
server: cloudflare
cf-cache-status: HIT
```
- The redirect target is a Cloudflare-cached URL (e.g. `https://p1.ipstatp.com/obj/ad-site-i18n/...` for ad segments, or a CDN URL for real segments).
- `curl -L` follows the redirect automatically.
- mpv / ExoPlayer / m3u8server must follow redirects (default OkHttp behavior).

## CDN hosts

| CDN host | Used by | Content |
|---|---|---|
| `mt.nekostream.site` | VidPlay-1 | master.m3u8, variant m3u8, segments, subtitles (VTT) |
| `9hjkrt.nekostream.site` | HD-1, Vidstream-2 | master.m3u8, variant m3u8, segments, subtitles (VTT) |
| `vibeplayer.site` | Kiwi-Stream | master.m3u8, variant m3u8, segments |
| `p1.ipstatp.com` | (ad CDN) | ad segments (PNG-wrapped) |
| `p16-ad-sg.ibyteimg.com` | (ad CDN, bytedance) | ad segments (PNG-wrapped) |

## Ad segment filtering

The media playlists (variant m3u8) mix real segments with ad segments:
- Real segments: on `{cdn}.nekostream.site/segment/...` or `vibeplayer.site/segment/...`.
- Ad segments: on `p1.ipstatp.com/obj/ad-site-i18n/...` or `p16-ad-sg.ibyteimg.com/obj/ad-site-i18n/...`.

For our test episode 1080p playlist: 132 ad segments vs 12 real segments.

**Filtering strategy (in the extension):**
- In `resolveVideo` (or via m3u8server), rewrite the variant m3u8 to keep only segments whose URL host matches the CDN host (`mt.nekostream.site` / `9hjkrt.nekostream.site` / `vibeplayer.site`).
- Drop any segment on `ipstatp.com` / `ibyteimg.com`.
- This is a strong argument for using `lib/m3u8server` — it can rewrite the m3u8 on-the-fly.

## No special cookies/tokens needed

- No session cookies.
- No CSRF token (the `_csrfToken` field in the report form is empty).
- No auth required for any video-chain endpoint.
- The `data-ids` and `data-link-id` base64 blobs are the only "tokens" — and they're passed through unchanged (no decryption needed client-side; the server decodes them).

## Conclusion: no special interceptor needed

Unlike sites that need `lib/cloudflareinterceptor` or `lib/cookieinterceptor`, ANIKOTO works with:
- A normal `headersBuilder()` adding `Referer: https://anikototv.to/`.
- Standard `network.client` (Cloudflare bypass already baked in per `MEMORY/research/04-network-layer-and-interceptors.md` §1).
- Per-request Referer overrides for the player hosts / CDN.

No lib interceptor modules required for the basic chain. The complexity is in the m3u8 rewriting (PNG + ads), not in anti-bot bypass.
