# Step 4 — Playback and Video Extraction

> **Implement the video pipeline.** This is the hardest step. By the end, EVERY server from EVERY
> endpoint resolves to a playable video, WAF-blocked CDNs are handled, and a local proxy is in
> place if the site uses PNG-wrapped streams.
>
> **Prerequisite:** Step 3 complete (episode list loads).
> **Done when:** tapping an episode → video list appears → selecting a server → video PLAYS. Test
> ALL servers from ALL endpoints (rule §1).

---

## 4.0 Re-read your Step 1 server analysis

Before writing ANY extractor code, re-read:
- `MEMORY/sites/servers.md` (or your equivalent) — the server-list paths + each server's resolve chain
- `MEMORY/sites/cdn-waf.md` — which CDNs are WAF-blocked
- `MEMORY/sites/png-wrapping.md` — does the site wrap streams as PNG?
- `MEMORY/sites/tokens-and-dedup.md` — token extraction + dedup strategy

If any of these are incomplete, **go back and finish Step 1** before proceeding. You cannot write
working extractors from incomplete analysis.

---

## 4.1 Implement getHosterList (server discovery)

The ext-lib 16 method is `getHosterList(episode: SEpisode): List<Hoster>`. It returns the list of
available servers (hosters) for an episode.

- [ ] Decode the EpisodeMeta from `episode.url` (the fragment — see Step 3 §3.4).
- [ ] Based on the meta, fetch the server list (from Step 1 §1.4 — PATH A / B / C).
- [ ] For each server, create a `Hoster`:
  ```kotlin
  Hoster(
      name = "<ServerName>",     // shown in the server picker
      url = "<embed-url>",       // the server's player page URL
      video = null,              // ← initialized=false; populated in getVideoList(server)
      // ... other fields per ext-lib 16 Hoster API
  )
  ```
- [ ] **Performance (AniKoto session 51):** if there are TWO independent server-list paths (PATH A:
  HTML scrape + PATH B: AJAX mapper API), run them **concurrently**:
  ```kotlin
  return coroutineScope {
      val a = async { fetchServerListPathA(meta) }
      val b = async { fetchServerListPathB(meta) }  // if enabled
      (a.await() + b.await()).distinctBy { it.url }
  }
  ```
  This saves 200-500ms. Only do this if the two paths are truly independent.
- [ ] Build + install + verify: tap an episode → server picker shows all servers.

> **AniKoto reference:** `../anikoto/MEMORY/modules/03-video-pipeline.md` §getHosterList. AniKoto's parallel PATH A+B: `../anikoto/MEMORY/session-logs/2027-06-27_session-51_*.md`.
> **ext-lib Hoster API:** `../../MEMORY/ext-lib/02-ext-lib-16-api-reference.md`.

---

## 4.2 Implement the per-server extractors

For EACH server, write an extractor that takes the embed URL → returns `List<Video>`.

- [ ] For each server from Step 1 §1.4, implement a `resolve<ServerName>(url): List<Video>` function.
- [ ] Each extractor:
  1. Fetches the embed page (use the inherited `client` — it has CloudflareInterceptor).
  2. Extracts the video source URL (m3u8, mp4, or a nested player iframe).
  3. If m3u8: fetch the playlist, parse variant streams (1080p/720p/360p), create one `Video` per variant.
  4. If mp4: create one `Video`.
  5. Set the `Video` constructor's ALL 14 positional args correctly, `initialized = false`. (See [`common-pitfalls.md`](common-pitfalls.md) §video-constructor.)
- [ ] **Per-stream Referer:** if the CDN requires `Referer: <embed-page>` (Step 1 §1.6), set it on each `Video`'s headers / `AudioStream.referer`.
- [ ] **Performance (AniKoto session 51):** if a server has multiple variant playlists to fetch, do them **in parallel**:
  ```kotlin
  coroutineScope {
      variants.map { async { resolveVariant(it) } }.awaitAll()
  }
  ```
  Reduces 4×300ms → ~300ms.

> **AniKoto reference:** AniKoto has 4 server extractors (VidPlay-1, HD-1, Vidstream-2, VidCloud-1) + Kiwi-Stream. See `../anikoto/MEMORY/modules/03-video-pipeline.md` §extractors.
> **ext-lib Video constructor:** `../../MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` §video-ctor-migration.
> **Reusable lib extractors:** `../../MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` (playlistutils, cryptoaes, m3u8server, universalextractor, host extractors).

---

## 4.3 Handle WAF-blocked CDNs (WebView fallback)

If Step 1 §1.6 found CDNs that 403/challenge OkHttp, you need a **WebView fallback** — use Chrome's
TLS stack + JS engine to pass the WAF.

- [ ] Create a `WebViewFetcher` class (per extension — don't share across extensions).
- [ ] Implement `fetchRenderedText(url, headers): String` — loads the URL in a WebView, waits for
  page load, returns the rendered HTML (after JS runs).
- [ ] Use it for WAF-blocked CDNs: `if (isWafBlockedHost(host)) webViewFetcher.fetchRenderedText(url) else client.newCall(...).execute().body!!.string()`.
- [ ] Implement `isWafBlockedHost(host): Boolean` — returns true for the WAF'd CDNs from Step 1 §1.6.
- [ ] **DO NOT remove `isWafBlockedHost()`** even if empty — future CDNs may need it.
- [ ] **Pre-warm the WebView** (AniKoto session 51): call `webViewFetcher.warmUp()` from
  `getEpisodeList()` so the WebView initializes on a background thread while the user browses
  episodes. Hides the 2-30s cold start from click-to-play.
- [ ] Build + install + verify: a WAF-blocked server now plays (via WebView fetch).

> **AniKoto reference (★ critical):** `../anikoto/MEMORY/modules/03-video-pipeline.md` §WebViewFetcher. The WebView fallback was added in sessions 30-31. See also `../anikoto/MEMORY/issues-resolutions/` for the WAF debugging journey.
> **Pitfall:** WebView must use a full desktop Chrome UA. See [`common-pitfalls.md`](common-pitfalls.md) §user-agent.

---

## 4.4 Implement the local proxy (if PNG-wrapped streams)

If Step 1 §1.7 found PNG-wrapped streams, you need a `LocalProxyServer` to strip the PNG header.

- [ ] Create a `LocalProxyServer` class (per extension).
- [ ] It intercepts requests to `http://127.0.0.1:<port>/...`, fetches the real stream URL, strips
  the PNG header, returns the raw ts/m3u8 bytes.
- [ ] Add an **LRU cache** (AniKoto uses 200 entries) so repeated segment requests don't re-fetch.
- [ ] Add **prefetch** for the next segment (reduces buffering).
- [ ] The `Video.url` for PNG-wrapped streams points to `http://127.0.0.1:<port>/...` instead of the CDN.
- [ ] Build + install + verify: a PNG-wrapped server plays through the proxy.

> **AniKoto reference (★ critical):** `../anikoto/MEMORY/modules/03-video-pipeline.md` §LocalProxyServer. The PNG-wrapping analysis: `../anikoto/MEMORY/sites/png-wrapping.md`.

---

## 4.5 Token extraction + crypto (if the site uses encrypted params)

Some sites encrypt video URLs or params (e.g. AniKoto uses RC4-decrypted `vrf` param). If Step 1
found tokens:

- [ ] Identify the crypto (RC4? AES? custom?) by reading the site's JS.
- [ ] Implement the decrypt function in Kotlin (use ext-lib `cryptoaes` for AES; roll your own for RC4/custom).
- [ ] Extract the token from the embed page, decrypt it, use the real URL.
- [ ] Build + install + verify: the token-protected server plays.

> **AniKoto reference:** `../anikoto/MEMORY/sites/tokens-and-dedup.md`. AniKoto's RC4 `vrf` decrypt: `../anikoto/MEMORY/modules/03-video-pipeline.md` §crypto.
> **ext-lib cryptoaes:** `../../MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` §cryptoaes.

---

## 4.6 Deduplication (if the site shares tokens across audio types)

If Step 1 §1.5 found that the same video is served under different audio labels (token sharing),
implement dedup so the user doesn't see duplicate videos.

- [ ] After collecting all `Video`s across servers/audio types, dedup by a stable key (the underlying
  stream URL, or stream URL + resolution).
- [ ] If duplicates have different audio labels, keep them as separate `AudioStream`s on one `Video`
  (ext-lib 16 supports multi-audio).
- [ ] Build + install + verify: no duplicate videos in the picker.

> **AniKoto reference:** `../anikoto/MEMORY/sites/tokens-and-dedup.md` + `../anikoto/MEMORY/modules/03-video-pipeline.md` §dedup.

---

## 4.7 Settings (server toggles, audio/resolution preferences)

Add a settings UI so the user can toggle servers and set defaults.

- [ ] Create a preferences screen (AniKoto uses 4 categories: Playback, Servers, Episode metadata, Smart Search).
- [ ] For each server, add a toggle (default ON — unless a server is unreliable, then OFF).
- [ ] Add audio-type preference (SUB / HSUB / DUB default).
- [ ] Add resolution preference (1080p / 720p / 360p default).
- [ ] **Dropdowns should show "Currently: %s"** so the user sees their current selection.
- [ ] Wire the preferences into `getHosterList` (skip disabled servers) + `getVideoList` (sort by preferred audio/res).
- [ ] Build + install + verify: change a setting → behavior changes.

> **AniKoto reference:** `../anikoto/MEMORY/modules/05-settings.md`. AniKoto's 4 settings categories + the "Currently: %s" pattern.

---

## 4.8 Verification checklist (Step 4 is done when ALL pass)

- [ ] `getHosterList` returns all servers for an episode
- [ ] EACH server's extractor resolves to a playable `Video` list — **test ALL servers, not just one** (rule §1)
- [ ] WAF-blocked CDNs handled via WebView fallback (`isWafBlockedHost()` present + used)
- [ ] WebView pre-warmed from `getEpisodeList()` (perf)
- [ ] Local proxy works IF the site uses PNG-wrapped streams (skip if not)
- [ ] Token extraction works IF the site uses encrypted params (skip if not)
- [ ] Dedup works IF the site shares tokens across audio types (skip if not)
- [ ] Per-stream Referer set where required
- [ ] Settings UI: server toggles + audio/resolution defaults; dropdowns show "Currently: %s"
- [ ] No crashes in logcat across ALL servers
- [ ] Video pipeline documented in `MEMORY/modules/03-video-pipeline.md`
- [ ] Write a session log in `MEMORY/session-logs/`

**Only when all pass → proceed to Step 5.**

---

## What to ask the user about (common Step 4 questions)

- "Server X is WAF-blocked and WebView can't pass it either. Should I skip this server, or try harder (e.g. solve the JS challenge)?"
- "The site uses RC4 to encrypt the `vrf` param. I'll implement RC4 decrypt in Kotlin. OK?"
- "Servers A and B return the same video (token sharing). I'll dedup by stream URL + show audio as AudioStreams. OK?"
- "Server C is flaky (works 50% of the time). Default its toggle OFF?"
- "I want to add a 'Smart Search' AI feature like AniKoto. Defer to after release?"
