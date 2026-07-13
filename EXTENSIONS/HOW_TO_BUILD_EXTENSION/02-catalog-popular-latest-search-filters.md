# Step 2 — Catalog: Popular, Latest, Search, Filters

> **Implement the browse + search layer.** By the end of this step, a debug APK loads popular,
> latest, search (paginated, with filters), and the anime details page — with cover images.
>
> **Prerequisite:** Step 1 complete (site analysis in `MEMORY/sites/`).
> **Done when:** debug APK loads all four (popular/latest/search/filters) + details page; cover
> images render; pagination works; search + filters work together.

---

## 2.0 Scaffold the Gradle project (if not already done)

> ℹ️ The full Gradle scaffolding procedure is in [`05-build-test-and-release.md`](05-build-test-and-release.md).
> For Step 2, you need a **minimal compilable project** — even if it only returns empty lists at first.
> The fastest path: copy AniKoto's `DEV/` build system and adapt the package/extClass/source.

- [ ] `EXTENSIONS/<name>/DEV/` exists with `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `common/`, `stubs/`, `gradlew`
- [ ] `EXTENSIONS/<name>/DEV/src/<lang>/<name>/` exists with `build.gradle.kts` + `src/.../<ClassName>.kt` (minimal skeleton)
- [ ] `./gradlew :src:<lang>:<name>:assembleDebug` produces a debug APK (even if it crashes on load — that's fine for now)
- [ ] The APK installs in Aniyomi/Animiru without crashing the app

> **AniKoto reference (build system to copy):** `../anikoto/DEV/` — settings.gradle.kts, build.gradle.kts, gradle/, common/, stubs/. The `:stubs` module provides ext-lib v16 stubs (`compileOnly`, NOT in APK).
> **Pitfall:** extClass path — see [`common-pitfalls.md`](common-pitfalls.md) §extClass. Get this right BEFORE writing any scraper code.

---

## 2.1 Implement the source class skeleton

Create `src/<lang>/<name>/src/eu/kanade/tachiyomi/animeextension/<lang>/<name>/<ClassName>.kt`:

```kotlin
class <ClassName> : ConfigurableAnimeSource<AnimePreferences> {
    override val name = "<Display Name>"          // from EXTENSION.md identity
    override val baseUrl = "https://<domain>"      // from Step 1 §1.1
    override val lang = "<lang>"                   // from EXTENSION.md
    override val supportsLatest = true             // unless the site has no "latest" view

    // ★ versionId — STABLE once published. Pick now, never bump.
    override val versionId = 1

    // Use the inherited client (has CloudflareInterceptor + cookieJar).
    // Do NOT create a separate OkHttpClient.

    override fun popularAnimeRequest(page: Int)  = GET("$baseUrl/<popular-path>?page=$page")
    override fun popularAnimeParse(response: Response) = <parse popular list>

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/<latest-path>?page=$page")
    override fun latestUpdatesParse(response: Response) = <parse latest list>

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) = <build search URL>
    override fun searchAnimeParse(response: Response) = <parse search results>

    override fun animeDetailsParse(response: Response) = SAnime.create().apply { <fill fields> }

    override fun episodeListParse(response: Response) = <parse episodes>  // Step 3

    override fun getVideoList(episode: SEpisode) = <extract videos>       // Step 4

    override fun getFilterList() = AnimeFilterList(<filters>)             // §2.5

    // preferences (Step 5 / settings)
    // ...
}
```

> **AniKoto reference (full source skeleton):** `../../MEMORY/guides/02-how-to-create-a-new-extension.md` has the complete skeleton with ALL ext-lib 16 methods.
> **ext-lib API:** `../../MEMORY/ext-lib/02-ext-lib-16-api-reference.md` — the authoritative compile-time reference.

---

## 2.2 Implement popularAnime

- [ ] Build the popular URL (from Step 1 §1.2). Usually `$baseUrl/<popular-path>?page=$page`.
- [ ] Load it in agent-browser, capture the HTML, identify the **list container** + **each anime card's** selectors.
- [ ] Implement `popularAnimeRequest` + `popularAnimeParse`.
- [ ] For each card, extract: `url` (relative → resolve with `baseUrl`), `title`, `thumbnail_url`, (optional) `genres`/`status`.
- [ ] Build + install + verify: the "Popular" tab in Aniyomi shows results with cover images.

**One change at a time:** get popular working BEFORE latest. Verify in the app, not just "it compiles".

> **AniKoto reference:** `../anikoto/MEMORY/modules/01-catalog-search.md` §popularAnime. AniKoto's popular URL + parsing.
> **Pitfall:** relative URLs. Use `baseUrl + href` or the ext-lib URL helper. See [`common-pitfalls.md`](common-pitfalls.md) §relative-urls.

---

## 2.3 Implement latestUpdates

- [ ] Same as popular, but with the latest URL. Often the parsing is identical (same card structure) — reuse a shared parse function.
- [ ] If the site has NO "latest" view, set `supportsLatest = false` and skip this.
- [ ] Build + install + verify: the "Latest" tab shows results.

> **AniKoto reference:** `../anikoto/MEMORY/modules/01-catalog-search.md` §latestUpdates.

---

## 2.4 Implement searchAnime

This is where sites differ most. Use the approach you verified in Step 1 §1.3.

- [ ] Build the search URL. Common patterns:
  - `GET $baseUrl/<filter-path>?keyword=$query&page=$page` (AniKoto style — search + filters together)
  - `GET $baseUrl/search?q=$query&page=$page`
  - `POST $baseUrl/ajax/search` with form body (AJAX endpoint)
- [ ] If the search endpoint is AJAX/JSON, parse JSON; if HTML, parse the same way as popular.
- [ ] **Test the autosuggest vs full-search endpoints** (Step 1 §1.3) — use whichever works.
- [ ] Handle the **empty-query** case (some sites' search returns popular when query is empty; others 400).
- [ ] Implement `searchAnimeRequest` + `searchAnimeParse`. Often `searchAnimeParse == popularAnimeParse` (same card structure).
- [ ] Build + install + verify: search "one piece" returns results.

> **AniKoto lesson (session 42):** the autosuggest endpoint returned 500; AniKoto switched to the paginated `/filter?keyword=` endpoint. **If search returns errors, test the alternative endpoint before giving up.** See `../anikoto/MEMORY/sites/getsources-migration-and-id-analysis.md`.
> **AniKoto reference:** `../anikoto/MEMORY/modules/01-catalog-search.md` §searchAnime.

---

## 2.5 Implement getFilterList (filters)

Filters let users narrow browse/search by genre, type, status, year, etc.

- [ ] From Step 1 §1.2-1.3, enumerate the filter categories (genre, type, status, year, sort, source, ...).
- [ ] For EACH filter, get the **actual values** the site expects — do NOT guess. Load the filter page, inspect the `<input>`/`<select>` form elements, extract `name=` + `value=`. (AniKoto session 51 fixed 43 wrong genre values by doing this.)
- [ ] Implement as `AnimeFilter.Header`, `AnimeFilter.Select`, `AnimeFilter.CheckBox` (for multi-select), `AnimeFilter.Sort`.
- [ ] Wire the filters into `searchAnimeRequest` — append the selected values as URL params.
- [ ] **Sort filter:** values usually use a **slug format** (`"latest-updated"`, not `"Latest Updated"`). Verify against the site's form.
- [ ] **Year filter:** the site may use checkboxes (multi-select) not a dropdown — match the site.
- [ ] Build + install + verify: open the filter sheet, set a genre + sort, search → results reflect the filter.

> **AniKoto lesson (session 51):** almost ALL filter values were wrong because they were guessed, not extracted from the site's `<input>` form. **Re-verify every value against the live site's HTML form.** See `../anikoto/MEMORY/session-logs/2027-06-27_session-51_*.md` + `../anikoto/MEMORY/modules/01-catalog-search.md` §filters.
> **Pitfall:** dropdown values that look like display names but the site expects slugs. See [`common-pitfalls.md`](common-pitfalls.md) §filter-values.

---

## 2.6 Implement animeDetailsParse (the details page)

- [ ] From Step 1 §1.2, the detail URL pattern. Load a real anime detail page in agent-browser.
- [ ] Identify the HTML selectors for: `title`, `description`/`synopsis`, `genre` (list), `status` (ongoing/completed), `thumbnail`/`cover`, `author`/`studio` (if shown).
- [ ] Implement `animeDetailsParse` — fill an `SAnime`:
  ```kotlin
  override fun animeDetailsParse(response: Response) = SAnime.create().apply {
      title = ...
      description = ...
      genre = ...   // comma-separated string
      status = parseStatus(...)  // SAnime.ONGOING / SAnime.COMPLETED / SAnime.UNKNOWN
      thumbnail_url = ...
      // initialized = false  ← for Video; SAnime doesn't have this
  }
  ```
- [ ] Build + install + verify: tap an anime → details page shows all fields + cover.

> **AniKoto reference:** `../anikoto/MEMORY/modules/02-anime-details-episodes.md` §details. AniKoto also appends a promo line ("Thank the Confused_creature_180") to the description — your extension may or may not want this.

---

## 2.7 Cover images

- [ ] Confirm cover URLs are correct (relative vs absolute — resolve relative ones with `baseUrl`).
- [ ] If covers 403, you may need to pass a `Referer: $baseUrl` header. AniKoto uses the inherited `client` which handles this.
- [ ] Build + install + verify: covers render in popular/latest/search/details.

> **Pitfall:** some CDNs reject requests without a browser UA. The inherited `client` has a full desktop Chrome UA. See [`common-pitfalls.md`](common-pitfalls.md) §user-agent.

---

## 2.8 Verification checklist (Step 2 is done when ALL pass)

- [ ] `popularAnimeRequest` + `popularAnimeParse` — Popular tab loads 30 results with covers
- [ ] `latestUpdatesRequest` + `latestUpdatesParse` — Latest tab loads with covers (or `supportsLatest = false`)
- [ ] `searchAnimeRequest` + `searchAnimeParse` — search "naruto" / "one piece" returns results with covers
- [ ] `getFilterList` — filter sheet shows all categories; values verified against the live site's form
- [ ] Filters work WITH search together (set a genre + search → filtered results)
- [ ] Pagination works (page 2 loads different results)
- [ ] `animeDetailsParse` — details page shows title, description, genre, status, cover
- [ ] Cover images render on ALL pages (popular, latest, search, details)
- [ ] No crashes in logcat (`adb logcat -s <Tag>:*`)
- [ ] Write a session log in `MEMORY/session-logs/`

**Only when all pass → proceed to Step 3.**

---

## What to ask the user about (common Step 2 questions)

- "Search autosuggest returns 5 results; the filter page returns 30. I recommend the filter page (better pagination + filters). OK?"
- "I found 43 genres. Here's the list — are any missing or wrong?"
- "The site has no 'latest' view. Set `supportsLatest = false`?"
- "Cover images 403 without a Referer header. I'll add `Referer: $baseUrl` — that's standard, OK?"
