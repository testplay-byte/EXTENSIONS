# AniKoto 180 — Extension Quick-Reference

> **The single file to read when resuming work on this extension.** Identity, build commands,
> current status, and key file locations. For deep context, read `MEMORY/` (this folder's
> knowledge base) and the latest `MEMORY/session-logs/`.
>
> **★ New AI agent?** Start with [`AGENT_ONBOARDING.md`](AGENT_ONBOARDING.md) (turnkey guide:
> golden rules, build recipe, publishing flow, live quirks), then come back here for facts.

---

## Identity (★ DO NOT CHANGE without an ADR)

| Field | Value | Notes |
|---|---|---|
| **Display name** | `AniKoto 180` | Source ID = `MD5("anikoto 180/en/11")` |
| **versionId** | `11` (STABLE) | Bumping orphans saved anime. NEVER change. |
| **Package** | `eu.kanade.tachiyomi.animeextension.en.anikoto180` | Distinguishes from other publishers (s49) |
| **extClass** | `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` | FULL path, no leading dot (applicationId ≠ source package) |
| **versionCode** | `16` | Bump per build. ★ Test-build numbers are NOT reserved (s58): v16.13 was reused as a test-build number by s57 AND s60; the release after them took v16.14 |
| **versionName** | `16.16` | = `16.<extVersionCode>` (auto-derived) |
| **Target site** | `anikototv.to` | |
| **Signing key** | `anikoto-release.jks` (SHA-256 `B4:67:CA:…:6A:5A`, alias `anikoto`) | At `DEV/anikoto-release.jks` — keep secure |
| **Current release** | `aniyomi-en.anikoto180-v16.16-release.apk` | 296,659 B · MD5 `1f8d20cdb33aab32dc952aae073e9e75` · SHA256 `723f4aa2…ed5f` · published 2026-10-07 (session 63) |

## Build (⚠️ GitHub Actions ONLY — never install the Android SDK in a sandbox)

All builds run on CI (`testplay-byte/EXTENSIONS` → workflow `release.yml`, signs with the
repo keystore). Full recipe: [`AGENT_ONBOARDING.md`](AGENT_ONBOARDING.md) §5–§6.

```bash
# TEST BUILD (default mode): commit → push (NO tag) → dispatch publish=false
# → signed APKs appear ONLY as workflow artifact test-build-apks-v16.X (no Release, no dist change)
curl -s -X POST -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/testplay-byte/EXTENSIONS/actions/workflows/release.yml/dispatches \
  -d '{"ref":"main","inputs":{"tag":"v16.X","publish":"false"}}'

# RELEASE (only on explicit user go-ahead): tag the release commit → CI builds + creates the GitHub Release
git tag v16.X && git push origin main v16.X
# then publish to users via the dist repo (surgical) — see AGENT_ONBOARDING.md §6
```

Before every push: **tree-sitter Kotlin parse** every changed `.kt` file (recipe in
`MEMORY/workflow/sandbox/TOOLS.md` §5) — CI compile errors cost runs. Historical local-build
guides live at repo-root `MEMORY/guides/` (e.g. `04-build-checklist.md`) — legacy reference only.

## Current status (v16.16 Build 16, session 63) — ✅ RELEASED (build-repo release + downloads page); dist-repo update pending user testing

- **WebView URL root fix (s63, user-reported: "got https://anikoto.cz/<slug>, expected /watch/<slug>")**: anime.url is now STORED as the site path `/watch/<slug>` — the yuzono/anikototheme reference behavior — instead of the bare slug. Live-verified 2026-10-07: the site 404s EVERY path without `/watch/`, so any app-side construction of `baseUrl + "/" + anime.url` (forks that bypass `getAnimeUrl`) produced exactly the reported bad URL. Now EVERY construction lands on the real page; older persisted shapes (bare slug from ≤v16.15, `/watch/…`, full URLs, malformed hybrids) still normalize via `animeSlug()`/`animeWatchPath()` (query-string safe too).
- **Fresh per-request headers (s63)**: the base class `headers` val is lazy and froze `Referer: <first-domain>` for the whole process — after a Preferred-domain switch request URLs were correct (s62 live getter) but the Referer leaked the old domain. Popular/latest/search/details now build headers each request (`docHeaders()`).
- **Mapper pipeline un-deaded (s63)**: live recon proved the mapper returns keys like `Kiwi` (no trailing dash) while `parseMapperResponse` required `endsWith("-")` → ZERO tokens ever parsed; and mapper tokens are FULL player URLs but were fed through `/ajax/server?get=` as link-ids. Now: keys accepted with/without dash, `status`/`error`/`message` skipped, names mapped gogoanime→Vidstream / anivibe→Vibe-Stream / kiwi*→Kiwi-Stream (yuzono parity), ALL streaming mapper servers surfaced (not just Kiwi), tokens used directly as embed URLs, mewcdn `HOST_MAP` honored, and a new direct-m3u8 Flow C handles plain-HLS mapper entries. (Live mapper currently offers only Kiwi DOWNLOAD links — see resolutions note.)
- **MegaPlay CDN HMAC token (s63, yuzono parity)**: decrypted getSources m3u8 URLs get the same `?token=` HMAC-SHA256 signature the site's own player appends (secret from yuzono maintainers). Live A/B 2026-10-07: no behavioral difference today (same master) — future-proofing against the CDN starting to require it.
- **Resolutions (user report, re-verified 2026-10-07)**: single-quality episodes (Sakamoto Days etc.) are a SOURCE limitation — every server entry × s-candidate × endpoint × BOTH domains, with AND without the CDN token, returns the same single-variant 1080p master; the site's extra qualities exist only in its DOWNLOAD menu (Kiwi/pahe 360p/720p/1080p links → download page, not a stream); other shows list 1080/720/360. Settings note updated with verified wording.
- **Review process (s63)**: sub-agent adversarial review loop ×2 iterations (final verdict CONFIDENT SOLVED: fixed M1 Kiwi-variant referer mismatch, m2 direct-m3u8 mapper branch, m3 token-aware dedup, stale comments) + an independent second verifier (verdict SHIP IT; verified URL-normalizer semantics, mapper end-to-end, HMAC parity, regression sweep).

- **Preferred-domain logic fix (s62)**: `baseUrl` was `by lazy` → the preference was read ONCE per process, so changing Settings → Playback → Preferred domain had NO visible effect (browsing AND "Open in WebView" stayed on the old domain until force-stop) — the user's "it does not get applied" report. Now a live getter that re-reads on every access; domain switches apply to the very next request. All 6 mirror domains re-verified live (HTTP 200).
- **Thin-ladder richness scan (s62)**: a full CDN-candidate win with ≤2 variants no longer ends the candidate scan — remaining s-candidates are probed (seen-master dedup keeps cost ≈1 getSources call each) and the RICHEST full result wins; >2-variant ladders still break immediately (zero extra requests). Live-verified 2026-10-07 on Sakamoto Days ep-4: the show is 1080p-only at SOURCE on every candidate/endpoint (raw master = one #EXT-X-STREAM-INF + I-FRAME track; sub 2226 / dub 2217; all 6 servers → megaplay; getSources carries no quality map) while Dorohedoro S2 / Dr Stone / Tensura S4 etc. still list 1080/720/360 — extraction not broken; the site's other qualities are download-only pahe file links (not streams). Settings gains an "About missing qualities" note; preferred-server list gains the current live name "Vidstream-1".
- **WebView URL fix (s61)**: "Open in WebView" on an ANIME previously opened `baseUrl + bare-slug` → the site's 404 page ("random URL" bug). `getAnimeUrl()` now overridden → `$baseUrl/watch/<slug>` (live-verified 200). `animeSlug()` normalizer applied at every anime-url entry point (details/episodes/WebView/search parsing) — domain-independent, tolerant of old persisted shapes and of the site ever serving foreign-domain listing links. (The EPISODE-level `getEpisodeUrl` was already correct since s43.)
- **Quality-list robustness (s61)**: the CDN-candidate loop no longer locks in a truncated quality list when a candidate loads only SOME of the master's variants (transient CDN failure) — best-partial fallback + remaining candidates; fast paths unchanged. "Only 1080p" on some shows is a SOURCE limitation (single-variant masters, live-verified: beyblade-x-aj6fn = 1080p-only, yuu-gi-ou-go-rush = 720p-only — identical on all mirrors/servers/candidates; the site's own player shows the same) and now logs an explanatory line.
- **Playback fix (s52)**: megaplay.buzz encrypted its getSources response ("enc" AES-256-CBC blob — playback was 100% broken on megaplay servers). Extension now tries `getSourcesNew` (plaintext, all hosts) first, then `getSources` + AES-256-CBC decrypt of the `enc` blob (`video/MegaPlayDecrypt.kt`, key/IV extracted from megaplay's own `newclient.min.js`). Re-verified live 2026-10-07 (s61): constants still valid; ALL CDN candidates (bcdn/tcdn/default) currently serve master+variants+segments to plain TLS.

- **Preferred domain (s52)**: user-selectable baseUrl — 6 official/verified domains (anikototv.to, anikoto.cz, anikoto.me, anikoto.net, anikototv.se, anikototv.com) in Settings → Playback. Source ID is domain-independent (no orphaned anime); episode URLs are relative so saved episodes follow the domain.
- **Catalog**: popular, latest, search (paginated `/filter?keyword=`, 30/page, filters work with search), filters, details, episode list. Cover images load.
- **Filters** (s51): all 43 genre values verified, sort uses slug format, Year = multi-select checkboxes, Source filter (18 types) added, TV_SHORT type added.
- **Video servers** (all 4 + 1 toggleable): VidPlay-1 (OkHttp), HD-1 (WebView CDN), Vidstream-2 (WebView fallback for WAF), VidCloud-1 (per-stream Referer), Kiwi-Stream (toggleable, default ON).
- **Audio/resolution**: SUB / HSUB / DUB × 1080p / 720p / 360p. Quality labels derive from the master-m3u8 `RESOLUTION=WxH` attribute (s56 — playlists can lie with `NAME="480p"`).
- **Server tolerance** (s56): unexpected/unknown watch-page servers no longer drop expected ones — tolerant per-episode dispatch (ep-8 case).
- **Performance** (s51): WebView pre-warming (2-30s saved), parallel variant fetching, parallel PATH A+B — first play 5-10s.
- **Smart Search** (s56–s58, `smartsearch/` package): dual-engine AI search — **Google AI Search** (default) and **Gemini API**. ON by default; activation phrase (default `?`) optional. Both engines instruct the AI to wrap the title in `[{[Title]}]` brackets (lenient S0 parser, plus S1–S7 fallbacks). Gemini: model list Gemini 3.1 Flash Lite (default, top) / 3.5 / 3.8 / custom, working Test connection (thinkingConfig only for gemini-2.5*). **"Copy response"** toggle (default OFF) at the bottom of the section. Usage instructions in the "Details" category.
- **Episode metadata** (s34-38): multi-source enrichment — thumbnails (Anikage→AniList→Kitsu→banner→cover), titles (Jikan→Anikage→Kitsu), descriptions (Anikage→Kitsu).
- **Settings**: 5 categories (Playback, Servers, Episode metadata, Smart Search, Details); all dropdowns show "Currently: %s".
- **Fork compatibility**: `getVideoList(SEpisode)` override + `/watch/slug/ep-N#fragment` episode.url format (no DNS errors in legacy-pipeline forks).
- **Promo line**: "Thank the Confused_creature_180" appended to every anime description.
- **Logging**: logcat-only (tag "Anikoto"), no file I/O, no permissions.
- **R8 release builds**: proguard rules keep `$$serializer` classes (prevents serialization crash).
- **Signed release APK**: reproducible (v16.16: 296,659 bytes).

## Key file locations (relative to `EXTENSIONS/anikoto/`)

| Path | What |
|---|---|
| `AGENT_ONBOARDING.md` | ★ Turnkey agent guide (start here) |
| `DEV/` | Gradle project (source, stubs module, build config, keystore) |
| `DEV/src/en/anikoto/src/main/kotlin/.../anikoto/Anikoto.kt` | Main source class |
| `DEV/src/en/anikoto/src/main/kotlin/.../anikoto/video/` | Extractors, MegaPlayDecrypt (enc AES), LocalProxyServer, WebViewFetcher, Models |
| `DEV/src/en/anikoto/src/main/kotlin/.../anikoto/metadata/` | EpisodeMetadataFetcher |
| `DEV/src/en/anikoto/src/main/kotlin/.../anikoto/smartsearch/` | SmartSearch (AI search module) |
| `DEV/src/en/anikoto/build.gradle.kts` | Build config + signing config |
| `DEV/common/proguard-rules.pro` | ProGuard rules (keep `$$serializer`) |
| `DEV/anikoto-release.jks` | Signing keystore (⚠️ sensitive, in .gitignore) |
| `APK/` | Built APKs (debug + release copies) |
| `ANALYSIS/` | Python analysis scripts + chain analysis JSON |
| `MEMORY/` | This extension's knowledge base (see `MEMORY/README.md`) |
| `MEMORY/session-logs/` | Sessions 01-58 (one log per session; read the last 2–3 first) |
| `MEMORY/sites/` | Site analysis (anikototv.to: endpoints, servers, audio types, CDN/WAF) |
| `MEMORY/issues-resolutions/` | 4 resolved issues (extclass doubling, stub crash, versionId, episode URL DNS) |
| `MEMORY/modules/` | 7 module docs (00-06: architecture, catalog, details, video, metadata, settings, smart-search) |
| `MEMORY/research/` | AniKoto-specific research (apk-reference analysis, episode metadata plans) |
| `MEMORY/workflow/` | Numbered research workflow (01-07: research→architecture→catalog→video→prefs→build→release) |
| `APK_INFO.md` | Full APK info sheet for current release |

## Critical build rules (DO NOT VIOLATE — see `MEMORY/guides/04-build-checklist.md`)

1. **extClass** = full path `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto` (no leading dot).
2. **Stubs** in `:stubs` module — `compileOnly`, NOT in APK.
3. **versionCode** bumps per build; **versionId** stays STABLE at 11.
4. **Video constructor**: ALL 14 positional args, `initialized=false`.
5. **Use inherited `client`** (has CloudflareInterceptor + cookieJar).
6. **WebViewFetcher** required for WAF-blocked CDNs — don't remove `isWafBlockedHost()`.
7. **Full desktop Chrome UA** — `Chrome/120.0.0.0` (desktop, not mobile).
8. **Per-stream Referer** — each AudioStream has a `referer` field.
9. **ProGuard**: keep ALL `...anikoto.**` classes + `$$serializer` classes.
10. **One change at a time** (project rule §2) — verify each change before the next build.
11. **Megaplay sources (s52)**: use `getSourcesNew` first; `getSources` may return the `enc` AES blob — decrypt via `MegaPlayDecrypt` (constants from megaplay's `newclient.min.js`).
12. **No Android SDK in the sandbox; all builds on GitHub Actions** — pre-push: tree-sitter parse every touched `.kt`, and grep new call-sites for static-vs-instance access (past CI failures).
13. **Verify against live endpoints before writing Kotlin** — the site rotates CDNs; pace probes (site AND Google CAPTCHA after ~8 rapid hits).
