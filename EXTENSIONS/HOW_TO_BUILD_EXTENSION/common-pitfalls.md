# Common Pitfalls — Known Gotchas from 51 Sessions

> **Hard-won lessons from AniKoto's 51 development sessions.** Read this BEFORE you start a step,
> and again when you hit a weird bug. Most "mysterious" failures are one of these.
>
> This file is GROWABLE — add new pitfalls as you discover them on new extensions.

---

## extClass

### Pitfall: extClass with a leading dot when applicationId ≠ source package
- **What goes wrong:** extension installs but doesn't appear, or crashes ("class not found").
- **Why:** the loader does `applicationId + extClass` when extClass starts with `.`. If applicationId is `...anikoto180` but the class is in package `...anikoto`, the loader looks for `...anikoto180.Anikoto` — doesn't exist.
- **Fix:** use the FULL path, no leading dot: `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto`. The loader uses it as-is.
- **When to use full path:** ANY time `applicationId ≠ source package`. (Common when you rename the package to avoid conflicts with other publishers.)
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §extclass-doubling

### Pitfall: forgetting to bump versionCode per build
- **What goes wrong:** Aniyomi doesn't recognize the new APK as an update.
- **Fix:** bump `versionCode` every build. `versionId` stays STABLE (never bump after publish).

---

## Video Constructor (ext-lib 16)

### Pitfall: wrong number of args / `initialized = true`
- **What goes wrong:** runtime crash when Aniyomi tries to play a video; or videos don't show resolutions.
- **Why:** ext-lib 16's `Video` constructor has 14 positional args. Omitting one shifts the rest. `initialized` must be `false` (Aniyomi populates it).
- **The 14 args:** `videoUrl, videoTitle, resolution(Int?), bitrate(Int?), headers(Headers?), preferred(Boolean), subtitleTracks, audioTracks, timestamps, mpvArgs, ffmpegStreamArgs, ffmpegVideoArgs, internalData(String), initialized(Boolean)`.
- **Fix:** always use ALL 14, `initialized = false`.
- **Read:** `../../MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` §video-ctor-migration

---

## User-Agent

### Pitfall: mobile or missing UA → 403 / broken pages
- **What goes wrong:** site returns 403, or serves a different (mobile) page that your selectors don't match.
- **Why:** many anime sites block non-browser UAs or serve mobile content.
- **Fix:** use a full **desktop Chrome UA**: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36`. The inherited `client` has this. WebView must use the same.
- **Read:** `../../MEMORY/PROJECT_RULES.md` + AniKoto's `client` setup

---

## Relative URLs

### Pitfall: passing a relative URL to Aniyomi
- **What goes wrong:** cover images don't load; episode links 404.
- **Why:** the site returns relative URLs (`/anime/123`) but Aniyomi needs absolute (`https://site/anime/123`).
- **Fix:** always resolve: `baseUrl + href` (or use the ext-lib `UrlUtils`). Check cover URLs especially — they're often relative.

---

## Filter Values

### Pitfall: guessing filter values instead of extracting from the site's form
- **What goes wrong:** filters don't narrow correctly (selecting "Mecha" returns Romance).
- **Why:** the site's internal genre IDs don't match display names. Mecha displays as "Mecha" but the form value is `123`, not `mecha`.
- **Fix:** load the filter page, inspect the `<input name="genre[]" value="...">` elements, extract the REAL values. AniKoto session 51 fixed 43 wrong values this way.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §filter-values-wrong

### Pitfall: sort values as display names instead of slugs
- **What goes wrong:** sort doesn't work.
- **Why:** the site expects `"latest-updated"` (slug) but you sent `"Latest Updated"` (display).
- **Fix:** use the slug format from the site's form.

---

## Audio Types

### Pitfall: only 2 audio types (forgetting HSUB)
- **What goes wrong:** some episodes' audio is mislabeled or missing.
- **Why:** sites have 3 types (SUB, HSUB/hardsub, DUB), not 2. HSUB is easy to miss.
- **Fix:** verify ALL 3 in Step 1 §1.5. Match the site's labels (rule §7).
- **Read:** `../../MEMORY/PROJECT_RULES.md` §7

### Pitfall: showing "H-Sub" for what the site calls "Sub"
- **What goes wrong:** labels don't match the site; users get confused.
- **Fix:** verify which `data-type`/attribute serves which audio. Match the site's terminology exactly.

---

## Test ALL Servers

### Pitfall: testing one server and assuming the rest work
- **What goes wrong:** extension "works" in testing but users report broken servers.
- **Why:** sites have MULTIPLE server-list paths and MULTIPLE servers per episode. Each has a different resolve chain. One working ≠ all working.
- **Fix:** in Step 1 §1.4 + Step 4, test EVERY server from EVERY endpoint. Each must resolve to a playable video. (Rule §1.)
- **Read:** `../../MEMORY/PROJECT_RULES.md` §1

---

## R8 / Release Builds

### Pitfall: R8 strips `$$serializer` → release crash (debug works)
- **What goes wrong:** debug build works; release build crashes with "type reference not found" / serialization error.
- **Why:** R8 minification strips kotlinx.serialization's `$$serializer` classes.
- **Fix:** ProGuard rules:
  ```
  -keep class eu.kanade.tachiyomi.animeextension.<lang>.<name>.** { *; }
  -keepclassmembers class **$$serializer { *; }
  ```
- **Verify after build:** `unzip -p ...release.apk classes.dex | strings | grep -c '\$\$serializer'` should be > 0.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §r8-serialization

### Pitfall: stubs included in the APK
- **What goes wrong:** extension crashes with stub class errors at runtime.
- **Why:** the `:stubs` module (compileOnly) leaked into the APK.
- **Fix:** `:stubs` must be `compileOnly`. Verify "Stub!" count = 0 in the release DEX after every build.
- **Read:** `../../MEMORY/guides/04-build-checklist.md`

---

## Keystore

### Pitfall: losing the keystore → no updates
- **What goes wrong:** you can't publish an update because the signature doesn't match.
- **Why:** each release must be signed with the SAME key as the previous. Lose the key → can't update.
- **Fix:** generate a per-extension keystore, store it securely (it's in `.gitignore`), keep backups. Record info in `keystore-info.txt`.
- **Read:** [`05-build-test-and-release.md`](05-build-test-and-release.md) §5.3

### Pitfall: reusing another extension's keystore
- **What goes wrong:** if one extension's key is compromised, all extensions using it are compromised.
- **Fix:** each extension gets its OWN keystore. Never share.

---

## Fork Compatibility

### Pitfall: "DNS error" in legacy-pipeline forks
- **What goes wrong:** in older Aniyomi/Animiru forks, tapping an episode gives a DNS error.
- **Why:** legacy forks DNS-resolve `episode.url` as a URL. If it's not a URL (e.g. pipe-delimited), DNS fails.
- **Fix:** encode `episode.url` as `/watch/<slug>/ep-N#<meta>` (valid path + metadata in fragment). Override legacy `getVideoList(SEpisode)` to delegate to `getHosterList`.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §dns-error-forks (★ critical)

---

## Logging

### Pitfall: file logging requires permissions + clutters the user's device
- **What goes wrong:** extension needs `WRITE_EXTERNAL_STORAGE` just to log; user sees a `Download/<id>/` folder.
- **Fix:** logcat-only logging. Use `android.util.Log` (tag = your extension name). No file I/O, no permissions.
- **Read:** `../../MEMORY/PROJECT_RULES.md` §6

### Pitfall: logcat 4KB line limit truncates long strings
- **What goes wrong:** URLs / tokens / response bodies get cut off in logs.
- **Fix:** a `trunc(s, maxLen)` helper. AniKoto has one in `AnikotoLog`.

---

## Performance

### Pitfall: sequential network calls when they could be parallel
- **What goes wrong:** video load is slow (e.g. 1.2s for 4 variant playlists).
- **Fix:** `coroutineScope { list.map { async { fetch(it) } }.awaitAll() }` for independent calls.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §slow-variant-fetching

### Pitfall: WebView cold-start delays first play
- **What goes wrong:** first video play takes 2-30s; subsequent plays are fast.
- **Fix:** `webViewFetcher.warmUp()` from `getEpisodeList()` — pre-initialize on a background thread.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §webview-cold-start

---

## Settings / UX

### Pitfall: naming specific external APIs in user-facing settings text
- **What goes wrong:** settings toggle says "Fetching preview images from MAL / AniList / Kitsu" — exposes implementation details, makes text long, locks you into showing specific source names.
- **Fix:** use "external sources" instead. Correct wording: `Fetching preview images from external sources` / `Fetching episode titles from external sources` / `Fetching episode descriptions from external sources`.
- **Read:** [`FEATURES/episode-metadata-enrichment.md`](FEATURES/episode-metadata-enrichment.md) §4 (exact wording table) + [`FEATURES/README.md`](FEATURES/README.md) §convention-user-facing-text

### Pitfall: episode title format inconsistent across extensions
- **What goes wrong:** one extension uses "Episode 1 - title", another uses "EP 1 - title" — inconsistent UX.
- **Fix:** ALWAYS use `"EP $epNum - $sourceTitle"` (the AniKoto convention). Never "Episode N - title".
- **Read:** [`FEATURES/episode-metadata-enrichment.md`](FEATURES/episode-metadata-enrichment.md) §title-format

---

## Video Playback

### Pitfall: overriding videoListParse instead of getHosterList (ext-lib 16 pipeline)
- **What goes wrong:** "No available videos" — no extension logs in logcat.
- **Why:** the app uses the ext-lib 16 NEW pipeline: `getHosterList(episode)` → `hosterListParse(response)`. `videoListParse` is NEVER called. If `hosterListParse` returns empty (default), no videos.
- **Fix:** override `suspend fun getHosterList(episode: SEpisode): List<Hoster>` — do the real extraction there. Return `listOf(Hoster(hosterName = Hoster.NO_HOSTER_LIST, videoList = videos))`.
- **Read:** [`FEATURES/video-playback-kwik-hls.md`](FEATURES/video-playback-kwik-hls.md) §the-ext-lib-16-pipeline

### Pitfall: using a fake episode.url path (causes 404)
- **What goes wrong:** getHosterList runs but gets 404 when fetching the play page.
- **Why:** episode.url was set to a made-up path (e.g. `/watch/...`) instead of the real site URL.
- **Fix:** use the REAL play page path: `/play/<animeSession>/<episodeSession>`. Fork compat still works (valid path resolves to baseUrl).
- **Read:** [`FEATURES/video-playback-kwik-hls.md`](FEATURES/video-playback-kwik-hls.md) §episode-url

### Pitfall: reimplementing a JS unpacker instead of porting the proven library
- **What goes wrong:** can't extract the video URL from Dean Edwards packed JS. Custom unpacker produces garbled output.
- **Why:** argument parsing is fragile — patterns inside the packed string confuse regexes. 4 custom attempts (builds 5-8) all failed.
- **Fix:** port the PROVEN `JsUnpacker` library from the reference extension (`SHARED/REFERENCE_HUB/anime-extensions-ref/lib/unpacker/`). It uses a single comprehensive regex + Unbaser class. Worked on the first try.
- **★ GENERAL LESSON:** when a reference extension has a proven solution for a problem, PORT it — don't reimplement from scratch.
- **Read:** [`FEATURES/video-playback-kwik-hls.md`](FEATURES/video-playback-kwik-hls.md) §critical-lesson

### Pitfall: parsing button text instead of data attributes
- **What goes wrong:** quality labels include the fansub name ("SubsPlease · 1080p") instead of just "1080p".
- **Fix:** use `data-resolution` and `data-audio` HTML attributes, not the button text.

---

## GraphQL API Sites (MKissa / allanime-style)

### Pitfall: querying subfields on scalar Object types causes GRAPHQL_VALIDATION_FAILED
- **What goes wrong:** the details query returns `{data: {show: null}}` — all fields null. The raw response contains `"errors":[{"message":"Field \"season\" must not have a selection since type \"Object\" has no subfields."}]`.
- **Why:** some GraphQL schemas define fields like `season`, `airedStart`, `availableEpisodes` as **scalar Object types** (not structured types). Querying them with subselections (`season { quarter year }`) is a validation error. The server returns the full nested JSON object as a scalar — you must query the field name alone (no `{ ... }`).
- **Fix:** query scalar Object fields WITHOUT subselections: `season` (not `season { quarter year }`). The server returns the full nested JSON, which kotlinx.serialization decodes into your DTO's nested data classes automatically.
- **How to verify:** test the query via curl before implementing. If you see `"errors"` in the response, read the error messages — they tell you exactly which fields can't have subselections.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §graphql-scalar-object-fields (MKissa session 02)

### Pitfall: using GET+APQ (persisted queries) instead of POST+full-query
- **What goes wrong:** you capture persisted-query hashes from the site's network traffic, hardcode them, and they break when the site rotates hashes.
- **Why:** persisted queries (APQ) are an optimization — the hash maps to a query string on the server. Sites can change the query (and hash) at any time. Maintaining hardcoded hashes is fragile.
- **Fix:** use POST with the full GraphQL query string in the request body. The API accepts both, but POST+full-query is stable (no hash dependency). Match the proven allanime reference pattern.
- **Read:** `SHARED/REFERENCE_HUB/anime-extensions-ref/src/en/allanime/AllAnime.kt` (uses POST+full-query)

---

## Episode Display Order

### Pitfall: returning episodes in ascending order causes them to display reversed (13→1 instead of 1→13)
- **What goes wrong:** you return episodes sorted ascending (ep 1 first, ep 12 last), but Aniyomi displays them descending (ep 12 first, ep 1 last) — the OPPOSITE of what you intended.
- **Why:** Aniyomi displays episodes in **REVERSE** of the order returned by the extension (so the latest episode appears at the top by default). If you return ascending, Aniyomi reverses to descending. If you return descending, Aniyomi reverses to ascending.
- **Fix:** return episodes in **DESCENDING** order (ep 12 first, ep 0 last). Use `.sortedDescending()` instead of `.sorted()`. Aniyomi will reverse to display ascending (ep 1 first). This matches the allanime reference (returns API's natural descending order) and animepahe (returns `.reversed()` = descending).
- **★ GENERAL RULE:** extensions should return episodes in DESCENDING order (latest first). Aniyomi handles the display reversal. Do NOT return ascending.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §episode-order-reversed (MKissa session 03)

---

## Response Parsing

### Pitfall: double-`use` on OkHttp Response causes "already closed" exception
- **What goes wrong:** runtime crash with "Response has already been closed" or similar IllegalStateException.
- **Why:** if your `parseJson` extension function calls `use { }` on the Response internally, AND you also wrap the call in `response.use { it.parseJson<>() }`, the Response gets closed twice.
- **Fix:** call `parseJson` directly without the outer `.use { }` — the internal `use` handles closing. OR make your `parseJson` NOT call `use` and always use the outer `.use { }`. Pick ONE pattern, not both.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §double-use-response (MKissa session 02)

---

## JSON Parsing (Null Fields)

### Pitfall: API intermittently returns `null` for a field declared non-nullable in the DTO
- **What goes wrong:** `JsonDecodingException: Unexpected JSON token ... Unexpected symbol 'n' in numeric literal at path: $.data.shows.pageInfo.total` — crashes the extension while scrolling.
- **Why:** some APIs intermittently return `null` for fields that are normally integers (e.g. `pageInfo.total`), especially during server-side cache refreshes. If your kotlinx.serialization DTO declares the field as non-nullable (`val total: Int = 0`), decoding `null` crashes.
- **Fix:** make the field nullable (`val total: Int? = null`). kotlinx.serialization decodes `null` gracefully. Your code should use a fallback (e.g. full-page heuristic for pagination) that doesn't depend on the nullable field.
- **★ GENERAL RULE:** for ANY API field that could plausibly be absent or null (totals, counts, optional metadata), declare it nullable in the DTO. It's safer than non-nullable + default.
- **Read:** [`reference-prior-solutions.md`](reference-prior-solutions.md) §null-total-json-crash (MKissa session 04)

---

## GROWING THIS FILE

When you hit a NEW pitfall on a new extension, append it here with the same format (What goes wrong / Why / Fix / Read). Every pitfall documented saves the next extension from repeating it.
