# Reference — How Prior Extensions Solved Specific Problems

> **A lookup table of problems → how AniKoto, AnimePahe, and MKissa solved them.** Use this when you
> hit a similar problem on a new extension. **This file is GROWABLE** — as you solve new problems
> on future extensions, APPEND a row to the relevant section so the next extension benefits.
>
> Format per entry:
> ```
> ### Problem: <one-line description>
> - **Symptom:** what goes wrong
> - **Cause:** root reason
> - **<Extension>'s solution:** what was done + which session
> - **Read:** path to the detailed doc
> - **<Next-extension> status:** (leave blank for the next extension to fill)
> ```

---

## Identity & Build Config

### Problem: extClass "doubling" — the loader can't find the source class
- **Symptom:** extension installs but doesn't appear in Aniyomi; or crashes on load with "class not found".
- **Cause:** when `extClass` starts with `.` (e.g. `.Anikoto`), the loader does `applicationId + extClass` = `...anikoto180.Anikoto` — but the class is at `...anikoto.Anikoto` (different package).
- **AniKoto's solution (session 49):** use the FULL path, no leading dot: `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto`. The loader uses it as-is. Required because `applicationId = ...anikoto180` but source package = `...anikoto`.
- **Read:** `../anikoto/MEMORY/issues-resolutions/01-extclass-doubling.md` + `../../MEMORY/guides/04-build-checklist.md`
- **animepahe status:** ⏳ (decide during Step 5 — if applicationId == source package, `.ClassName` is fine; else use full path)

### Problem: versionId orphans saved anime
- **Symptom:** after a version bump, users' saved anime disappear (the source ID changed).
- **Cause:** source ID = `MD5("<name> <lang>/<versionId>")`. Bumping versionId changes the ID → Aniyomi can't match saved anime to the new source.
- **AniKoto's solution (session 25):** versionId = 11, STABLE forever. Bump only `versionCode` per build; NEVER `versionId` after publish.
- **Read:** `../anikoto/MEMORY/issues-resolutions/03-versionid-logo-bumping.md`
- **animepahe status:** ⏳ (start versionId = 1, never bump after publish)

### Problem: stubs crash the extension at runtime
- **Symptom:** extension crashes with a stub class not found / method not implemented.
- **Cause:** the `:stubs` module (ext-lib v16 compile-time stubs) got included in the APK instead of being `compileOnly`.
- **AniKoto's solution (session ~13-16):** `:stubs` is a SEPARATE Gradle module, `compileOnly`. Verify "Stub!" count = 0 in the release DEX after every build.
- **Read:** `../anikoto/MEMORY/issues-resolutions/02-stub-crash.md` + `../../MEMORY/guides/04-build-checklist.md`
- **animepahe status:** ⏳ (copy AniKoto's `:stubs` module setup)

---

## Catalog (Popular / Latest / Search / Filters)

### Problem: search endpoint returns 500 / broken
- **Symptom:** search returns HTTP 500 or empty results.
- **Cause:** the autosuggest endpoint (`/ajax/anime/search`) was broken on the site.
- **AniKoto's solution (session 42):** switched to the paginated `/filter?keyword=<query>&<filters>&page=<page>` endpoint — works with search + filters together, 30/page.
- **Read:** `../anikoto/MEMORY/sites/getsources-migration-and-id-analysis.md` + `../anikoto/MEMORY/session-logs/` (session 42)
- **animepahe status:** ⏳ (test BOTH autosuggest + full-search endpoints in Step 1 §1.3)

### Problem: all filter values are wrong
- **Symptom:** filters don't narrow results correctly (e.g. selecting "Mecha" returns Romance anime).
- **Cause:** filter values were GUESSED instead of extracted from the site's `<input>` form. Mecha was `180` (actually Game); real Mecha = `123`.
- **AniKoto's solution (session 51):** curled the `/filter` page, extracted ALL `<input name="genre[]" value="...">` elements, re-verified all 43 genres + sort (slug format) + year (multi-select checkboxes) + source (18 types) + TV_SHORT type.
- **Read:** `../anikoto/MEMORY/session-logs/2027-06-27_session-51_*.md` + `../anikoto/MEMORY/modules/01-catalog-search.md` §filters
- **animepahe status:** ⏳ (extract values from the live site's form in Step 2 §2.5)

---

## Details & Episodes

### Problem: "DNS error" in legacy-pipeline forks when tapping an episode
- **Symptom:** in older Aniyomi/Animiru forks, tapping an episode gives a DNS resolution error; playback never starts.
- **Cause:** legacy forks try to DNS-resolve `episode.url` as a URL before calling `getVideoList()`. AniKoto's old `episode.url` was a pipe-delimited string (`animeId|episodeId|...`) — not a URL → DNS fails.
- **AniKoto's solution (session 43):** encode `episode.url` as a valid URL path with metadata in the fragment: `/watch/<slug>/ep-N#<url-encoded-meta>`. The path resolves to `baseUrl` (no DNS error); the fragment carries the metadata. ALSO override legacy `getVideoList(SEpisode)` to delegate to `getHosterList` + flatten.
- **Read:** `../anikoto/MEMORY/issues-resolutions/04-episode-url-dns-error-in-forks.md` (★ critical) + `../anikoto/MEMORY/modules/02-anime-details-episodes.md` §EpisodeMeta
- **animepahe status:** ⏳ (use the same `/watch/<slug>/ep-N#fragment` encoding in Step 3 §3.4)

### Problem: sub/dub crammed into episode name
- **Symptom:** episode names look like "Episode 1 [SUB]" — ugly, hard to read.
- **Cause:** developer put audio type in the episode name.
- **AniKoto's solution:** use the `scanlator` field for sub/dub (rule §8). Episode names stay clean: "Ep 1 - title".
- **Read:** `../../MEMORY/PROJECT_RULES.md` §8 + `../anikoto/MEMORY/modules/02-anime-details-episodes.md` §scanlator
- **animepahe status:** ⏳ (use scanlator field in Step 3 §3.3)

---

## Video Pipeline (Servers / WAF / Proxy)

### Problem: WAF-blocked CDN returns 403 to OkHttp
- **Symptom:** fetching a video CDN URL returns 403 (Cloudflare challenge) even with correct headers.
- **Cause:** the CDN requires a real browser TLS handshake + JS execution that OkHttp can't provide.
- **AniKoto's solution (sessions 30-31):** `WebViewFetcher` class — loads the URL in an Android WebView, waits for page load, returns the rendered HTML. Uses Chrome's TLS + JS engine. `isWafBlockedHost()` gates which CDNs use it.
- **Read:** `../anikoto/MEMORY/modules/03-video-pipeline.md` §WebViewFetcher
- **animepahe status:** ⏳ (check Step 1 §1.6 for WAF-blocked CDNs)

### Problem: cold-start WebView takes 2-30s, delaying first play
- **Symptom:** first video play is slow; subsequent plays are fast.
- **Cause:** WebView initializes lazily on first use.
- **AniKoto's solution (session 51):** `WebViewFetcher.warmUp()` called from `getEpisodeList()` — initializes the WebView on a background thread while the user browses episodes. Hides the cold start.
- **Read:** `../anikoto/MEMORY/session-logs/2027-06-27_session-51_*.md`
- **animepahe status:** ⏳ (implement in Step 4 §4.3 if using WebView)

### Problem: PNG-wrapped streams (m3u8/ts disguised as PNG)
- **Symptom:** video URLs end in `.png` but contain video data; ad-blockers don't block them but the player can't play them.
- **Cause:** the site wraps streams as PNG to evade ad-blockers.
- **AniKoto's solution:** `LocalProxyServer` — intercepts requests to `127.0.0.1:<port>/...`, fetches the real stream, strips the PNG header, returns raw bytes. LRU cache (200 entries) + prefetch.
- **Read:** `../anikoto/MEMORY/sites/png-wrapping.md` + `../anikoto/MEMORY/modules/03-video-pipeline.md` §LocalProxyServer
- **animepahe status:** ⏳ (check Step 1 §1.7 — does animepahe use PNG wrapping?)

### Problem: slow multi-variant fetching (sequential)
- **Symptom:** fetching 4 variant m3u8 playlists takes 1.2s (4 × 300ms sequential).
- **AniKoto's solution (session 51):** `coroutineScope { variants.map { async { resolveVariant(it) } }.awaitAll() }` — parallel. Reduces to ~300ms.
- **Read:** `../anikoto/MEMORY/session-logs/2027-06-27_session-51_*.md`
- **animepahe status:** ⏳ (apply in Step 4 §4.2 if multi-variant)

### Problem: same video served under different audio labels (token sharing)
- **Symptom:** the video picker shows duplicate videos (one per audio type, same underlying stream).
- **Cause:** the site shares tokens across audio types — same video, different label.
- **AniKoto's solution:** dedup by stream URL (+ resolution); if dupes have different audio, keep as separate `AudioStream`s on one `Video`.
- **Read:** `../anikoto/MEMORY/sites/tokens-and-dedup.md` + `../anikoto/MEMORY/modules/03-video-pipeline.md` §dedup
- **animepahe status:** ⏳ (check Step 1 §1.5)

---

## Build / Release

### Problem: R8 strips `$$serializer` classes → runtime crash
- **Symptom:** release build (R8 minified) crashes with "type reference not found" / serialization error; debug build works fine.
- **Cause:** R8 minification strips kotlinx.serialization's `$$serializer` companion classes.
- **AniKoto's solution (session 47):** ProGuard rules keep ALL extension classes + `$$serializer` classes:
  ```
  -keep class eu.kanade.tachiyomi.animeextension.<lang>.<name>.** { *; }
  -keepclassmembers class **$$serializer { *; }
  ```
- **Read:** `../anikoto/DEV/common/proguard-rules.pro` + `../anikoto/MEMORY/session-logs/2026-06-26_session-47_*.md`
- **animepahe status:** ⏳ (copy the ProGuard rules in Step 5 §5.4)

### Problem: extension writes logs to user's Download folder (needs WRITE_EXTERNAL_STORAGE)
- **Symptom:** extension requires a storage permission just to log; clutters the user's device.
- **Cause:** old logging design wrote to `Download/1118000/`.
- **AniKoto's solution (session 46):** logcat-only logging. `AnikotoLog` delegates to `android.util.Log` only. No file I/O, no permissions.
- **Read:** `../../MEMORY/PROJECT_RULES.md` §6
- **animepahe status:** ⏳ (use logcat-only from the start in Step 4)

---

## Episode Metadata Enrichment

### Problem: thumbnails + descriptions missing (only titles work)
- **Symptom:** episode list shows titles (from Jikan) but NO thumbnails and NO descriptions.
- **Cause:** AniList/Anikage/Kitsu fetches failing. On animepahe, the WebView origin was `animepahe.pw` — which shows a Cloudflare challenge page with strict CSP (`default-src 'none'`) that blocks ALL cross-origin `fetch()` calls. So WebView-based fetches to AniList/Kitsu silently failed.
- **AniKoto's approach:** WebView-first (origin: megaplay.buzz — a light-Cloudflare CDN that loads easily). Works for AniKoto but FAILS for hard-Cloudflare sites.
- **animepahe's solution (session 02):** **OkHttp-first, WebView-fallback.** Try OkHttp first (works for AniList + Anikage + Jikan on most devices). Fall back to WebView only if OkHttp returns 403. The WebView origin is `data:text/html,<html><body></body></html>` (blank page, no CSP — avoids the challenge-page CSP block).
- **Read:** [`episode-metadata-enrichment.md`](episode-metadata-enrichment.md) ★ the full implementation guide
- **animepahe status:** ✅ fixed (OkHttp-first + data:URL-origin WebView fallback)

### Problem: title format wrong ("Episode N - title" instead of "EP N - title")
- **Symptom:** episode names show "Episode 1 - title" instead of "EP 1 - title".
- **Cause:** used `"Episode $epNum - $title"` instead of the AniKoto convention `"EP $epNum - $title"`.
- **Fix:** `ep.name = "EP $epNum - $sourceTitle"`.
- **animepahe status:** ✅ fixed (session 02)

### Problem: no metadata at all (not even titles)
- **Symptom:** episodes show just "Episode 1", "Episode 2" — no enrichment.
- **Cause:** MAL ID not found — `extractMalId` returned null (no myanimelist.net link on the detail page).
- **Fix:** check the detail page's HTML for MAL external links. If none, implement title-search fallback via Jikan's search API (`api.jikan.moe/v4/anime?q=<title>&limit=1` → first result's `mal_id`).
- **animepahe status:** ⏳ depends on whether the detail page has MAL links (unverifiable off-device due to Cloudflare)

---

## Settings / UX

### Problem: user can't see which dropdown option is currently selected
- **Symptom:** settings dropdowns show just the option list; user has to remember their pick.
- **AniKoto's solution:** dropdowns show "Currently: %s" in the summary.
- **Read:** `../anikoto/MEMORY/modules/05-settings.md`
- **animepahe status:** ⏳ (use the "Currently: %s" pattern in Step 4 §4.7)

### Problem: settings summaries name specific external sources (MAL, AniList, Kitsu)
- **Symptom:** toggle summary says "Fetching preview images from MAL / AniList / Kitsu" — exposes implementation details to the user.
- **Cause:** developer listed the actual API names in the user-facing text.
- **Convention:** NEVER name specific APIs in user-facing text. Use "external sources" instead. This keeps the UI clean and lets you swap implementations without changing visible text.
- **Correct wording (copy verbatim):**
  - Thumbnails ON: `Fetching preview images from external sources`
  - Titles ON: `Fetching episode titles from external sources`
  - Descriptions ON: `Fetching episode descriptions from external sources`
- **Read:** [`FEATURES/episode-metadata-enrichment.md`](FEATURES/episode-metadata-enrichment.md) §4 + [`FEATURES/README.md`](FEATURES/README.md) §convention-user-facing-text
- **animepahe status:** ✅ fixed (session 04)

### Problem: user wants to credit the developer
- **AniKoto's solution (session 50):** append `"\n\nThank the Confused_creature_180"` to every anime description.
- **Read:** `../anikoto/MEMORY/session-logs/` (session 50)
- **animepahe status:** ⏳ (optional — ask the user in Step 5)

---

## Video Playback (Kwik / packed JS)

### Problem: "No available videos" — no logcat output from extension code
- **Symptom:** clicking an episode gives "No available videos" immediately, no extension logs in logcat.
- **Cause:** the app (Animiru/Aniyomi) uses the ext-lib 16 NEW pipeline: `getHosterList(episode)` → `hosterListParse(response)`. If `hosterListParse` returns emptyList() (default), no videos. `videoListParse` is NEVER called in the new pipeline.
- **AniKoto's approach:** AniKoto overrides `getHosterList(episode)` (the suspend version) to do the real extraction.
- **animepahe's solution (session 08):** override `suspend fun getHosterList(episode: SEpisode): List<Hoster>` — fetch the play page, parse resolution buttons, resolve kwik links, return `listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = videos))`.
- **Read:** [`FEATURES/video-playback-kwik-hls.md`](FEATURES/video-playback-kwik-hls.md) §the-ext-lib-16-pipeline
- **animepahe status:** ✅ fixed (session 08)

### Problem: 404 "No available videos" — episode.url is a fake path
- **Symptom:** getHosterList runs, fetches the play page, gets 404.
- **Cause:** episode.url was set to `/watch/<session>/ep-N#<episodeSession>` — a fake path invented for "fork compatibility" that doesn't exist on animepahe.
- **Fix:** use the REAL play page path: `/play/<animeSession>/<episodeSession>`. This is a valid path (returns 200). Fork compat still works because `/play/...` resolves to baseUrl (no DNS error).
- **Read:** [`FEATURES/video-playback-kwik-hls.md`](FEATURES/video-playback-kwik-hls.md) §episode-url
- **animepahe status:** ✅ fixed (session 07)

### Problem: can't extract video URL from Kwik's packed JS
- **Symptom:** kwik.cx page fetched successfully (14,475 chars), but no video URL found. "could not extract m3u8 URL".
- **Cause:** Kwik uses Dean Edwards's JS packer. The video URL is only visible AFTER unpacking — it's assembled from token substitutions. Custom Kotlin unpackers failed because argument parsing was fragile (patterns inside the packed string confused regexes).
- **animepahe's solution (session 09):** ported the PROVEN `JsUnpacker` library from the reference extension (`keiyoushi.lib.jsunpacker`). Uses a single comprehensive regex + Unbaser class. The exact same library the reference uses.
- **★ KEY LESSON:** don't reimplement proven libraries from scratch. Port them from the reference extension. 4 custom unpacker attempts (builds 5-8) all failed; the ported JsUnpacker worked on the first try.
- **Read:** [`FEATURES/video-playback-kwik-hls.md`](FEATURES/video-playback-kwik-hls.md) §critical-lesson-use-the-proven-jsunpacker-library
- **animepahe status:** ✅ fixed (session 09) — video playback confirmed working by the user

---

## GraphQL API Sites (MKissa)

### Problem: GraphQL details query returns all-null fields (GRAPHQL_VALIDATION_FAILED)
- **Symptom:** `animeDetailsParse` returns all-null fields. The details page shows no title, no description, no genres.
- **Cause:** the GraphQL schema treats `season`, `airedStart`, `availableEpisodes` as **scalar Object types** — querying them with subselections (`season { quarter year }`) is a validation error. The server returns `{data: {show: null}}` with errors.
- **MKissa's solution (session 02):** query scalar Object fields WITHOUT subselections: `season` (not `season { quarter year }`). The server returns the full nested JSON as a scalar, which kotlinx.serialization decodes into the DTO's nested data classes. Verified via curl before implementing.
- **Read:** [`common-pitfalls.md`](common-pitfalls.md) §graphql-scalar-object-fields + `EXTENSIONS/mkissa/MEMORY/session-logs/2027-06-29_session-02_*.md`
- **General lesson:** ALWAYS test GraphQL queries via curl before implementing. If you see `"errors"` in the response, read them — they tell you exactly what's wrong.

### Problem: episode order reversed (showing 13→1 instead of 1→13)
- **Symptom:** episodes display in descending order (ep 13 first, ep 1 last) instead of ascending.
- **Cause:** Aniyomi displays episodes in REVERSE of the order returned by the extension. Returning ascending → Aniyomi reverses → user sees descending.
- **MKissa's solution (session 03):** return episodes in **DESCENDING** order (`.sortedDescending()` instead of `.sorted()`). Aniyomi reverses to display ascending (ep 1 first). Matches the allanime reference (returns API's natural descending order) + animepahe (returns `.reversed()` = descending).
- **★ GENERAL RULE:** extensions should return episodes in DESCENDING order (latest first). Aniyomi handles the display reversal.
- **Read:** [`common-pitfalls.md`](common-pitfalls.md) §episode-order-reversed + `EXTENSIONS/mkissa/MEMORY/session-logs/2027-06-29_session-03_*.md`

### Problem: episode metadata enrichment without MAL external links
- **Symptom:** no MAL ID available on the detail page (no `myanimelist.net/anime/` links) → can't use the AniKoto/AnimePahe metadata fetcher pattern (which starts from a MAL ID).
- **Cause:** not all sites provide MAL external links. MKissa's detail page has no external links at all.
- **MKissa's solution (session 02):** extract the **AniList media ID** from the anime's thumbnail URL instead. MKissa (like allanime) uses AniList-hosted thumbnails: `https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx182300-...jpg` → the `182300` is the AniList media ID. Regex: `bx(\d+)-`. Then query **Anikage** (`anikage.cc/api/media/anime/<anilistId>/episodes`) which returns per-episode title + description + thumbnail + airDate in one call. Jikan title-search is the fallback (for titles when Anikage has no data). OkHttp-only — no WebView needed (neither Anikage nor Jikan are behind Cloudflare).
- **★ GENERAL RULE:** if the site uses AniList-hosted thumbnails (URL contains `anilist.co/file/`), extract the AniList ID via `bx(\d+)-` regex. This is cleaner than Jikan title-search.
- **Read:** [`FEATURES/episode-metadata-enrichment.md`](FEATURES/episode-metadata-enrichment.md) §anilist-id-extraction + `EXTENSIONS/mkissa/DEV/src/.../metadata/EpisodeMetadataFetcher.kt`

### Problem: double-`use` on OkHttp Response
- **Symptom:** runtime crash "Response has already been closed".
- **Cause:** `parseJson` extension function calls `use{}` internally, AND the caller wraps it in another `response.use { it.parseJson<>() }`.
- **MKissa's solution (session 02):** call `parseJson` directly without the outer `.use{}` — let the internal `use` handle closing.
- **Read:** [`common-pitfalls.md`](common-pitfalls.md) §double-use-response

### Problem: site uses a GraphQL API (not HTML scraping)
- **Symptom:** the site is a SPA (SvelteKit/React/Vue) — content is rendered client-side, not in the HTML. Curl returns an empty page.
- **Cause:** the site fetches data from a separate API endpoint (GraphQL or REST) via JavaScript.
- **MKissa's solution (session 01):** use `agent-browser` to capture network requests — find the API endpoint (`api.allanime.day/api`). Test via curl POST with full GraphQL query strings (not GET+APQ — persisted query hashes are fragile). Build the extension against the API, not the HTML.
- **★ GENERAL RULE:** for SPA sites, ALWAYS check network requests in agent-browser. The real data source is the API, not the HTML.
- **Read:** `EXTENSIONS/mkissa/MEMORY/sites/site-analysis.md` §3 (the real API)

### Problem: API intermittently returns null for a field declared non-nullable (JsonDecodingException)
- **Symptom:** `JsonDecodingException: Unexpected JSON token ... Unexpected symbol 'n' in numeric literal at path: $.data.shows.pageInfo.total` — crashes while scrolling Latest.
- **Cause:** the API intermittently returns `total: null` (especially during cache refreshes). The DTO declared `total: Int = 0` (non-nullable) — kotlinx.serialization can't decode `null` to non-nullable `Int`.
- **MKissa's solution (session 04):** made `total` nullable (`Int? = null`) in both `PageInfo` and `QueryPopular` DTOs. The `hasNext` pagination logic uses the full-page heuristic (partial page = last page), which doesn't depend on `total` — so pagination still works when `total` is null.
- **★ GENERAL RULE:** for ANY API field that could plausibly be absent or null (totals, counts, optional metadata), declare it nullable in the DTO.
- **Read:** [`common-pitfalls.md`](common-pitfalls.md) §null-total-json-crash + `EXTENSIONS/mkissa/MEMORY/session-logs/2027-06-29_session-04_*.md`

### Problem: Popular tab shows different anime than the site's popular page
- **Symptom:** the extension's Popular tab shows different anime than what the user sees on the site's popular page. Looks like "popular and latest are mixed up".
- **Cause:** the extension used `dateRange=7` (Weekly) for the popular query, but the site's default popular page uses `range=1` (Daily). Different dateRange values return different anime.
- **MKissa's solution (session 04):** navigated the site in agent-browser to find the popular page URL (`mkissa.to/popular?type=anime&range=1`), noted the default `range=1` parameter, and matched it in the extension (`dateRange=1`). Now the Popular tab shows the same anime as the site.
- **★ GENERAL RULE:** always check the site's actual popular/latest/search URLs (click the links in agent-browser, note the final URL + query params) and match the default parameters in your extension.
- **Read:** `01-analyze-the-website.md` §check-for-dedicated-popular-latest-search-urls

---

## GROWING THIS FILE

When you solve a NEW problem on any extension, append an entry to the
relevant section above. Use the format:

```
### Problem: <one-line description>
- **Symptom:** ...
- **Cause:** ...
- **<Extension>'s solution:** what was done + which session
- **Read:** path to the detailed doc
- **<Next-extension> status:** (leave blank for the next extension to fill)
```

If the problem doesn't fit an existing section, add a new section. This file is the project's
institutional memory — every solved problem makes the next extension easier.
