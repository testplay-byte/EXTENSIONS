# Step 1 — Analyze the Website

> **The first and most important step.** Everything else builds on this. A wrong assumption here
> cascades into broken code in every later step. **Verify everything with a real browser.**
>
> **Done when:** a complete site-analysis document lives in `EXTENSIONS/<name>/MEMORY/sites/` and
> the identity fields (name, package, extClass, domain, language, NSFW) in `EXTENSION.md` are
> confirmed. If any field can't be confirmed by analysis, ask the user (see
> [`00-philosophy-and-rules.md`](00-philosophy-and-rules.md) §when-to-ask).

---

## Why this step exists

Every site is different. AniKoto (anikototv.to) and AnimePahe (animepahe.ru) have completely
different URL structures, endpoint shapes, server discovery paths, and audio-type conventions. You
CANNOT copy AniKoto's scrapers — you can only copy its *approach*. This step is where you learn the
target site well enough to write scrapers that actually work.

**Rule §1 (verify before trusting) is non-negotiable here.** A single curl response is not enough.
Load pages in agent-browser, capture network requests, test ALL servers from ALL endpoints.

---

## 1.1 Confirm the live domain

Many anime sites rotate domains (animepahe has used .com, .org, .ru, .app). Before anything else:

- [ ] Search for the current domain. Try the known candidates in a browser.
- [ ] Confirm which one is live and serves the actual content (not a placeholder/mirror/parked page).
- [ ] Check for redirects (e.g. `animepahe.com` → `animepahe.ru`).
- [ ] Record the canonical base URL in `MEMORY/sites/site-analysis.md`.

**If the domain is ambiguous or you find multiple live candidates → ask the user** which to target.

> **AniKoto reference:** AniKoto's base URL + analysis: `../anikoto/MEMORY/sites/site-analysis.md`

---

## 1.2 Map the URL structure

Using `agent-browser open <url>` + `agent-browser snapshot` + network capture, document EVERY URL
pattern you'll need to scrape:

- [ ] **Home / popular** — what URL serves the "popular" / default browse view?
- [ ] **Latest / recent** — what URL serves the "latest updates" / newest view?
- [ ] **Search** — what endpoint does search use? Is it the same URL with a `?query=` param, a separate `/search` path, or an AJAX/JSON API?
- [ ] **Filters** — is there a `/filter` or `/browse` page with genre/type/year/status filters? What are the filter parameter names + values?
- [ ] **Anime detail** — what's the URL pattern for a single anime's page? (e.g. `/anime/<slug>` or `/anime?id=N`)
- [ ] **Episode list** — is the episode list ON the detail page, or fetched via a separate AJAX endpoint? What's the endpoint + params?
- [ ] **Watch / embed** — what's the URL for the video player page? (e.g. `/play/<id>/<ep>` or `/watch/<slug>/ep-N`)
- [ ] **Cover images** — where do cover image URLs come from? Are they relative (need base URL) or absolute?

For each, capture:
- The full URL (with example params)
- The HTTP method (GET/POST)
- Required headers (Referer, User-Agent, cookies, CSRF tokens)
- The response shape (HTML? JSON? what fields?)

> **AniKoto reference:** how AniKoto mapped endpoints: `../anikoto/MEMORY/sites/endpoints.md`
> **MKissa reference:** how MKissa mapped a GraphQL API: `../mkissa/MEMORY/sites/site-analysis.md` §3
> **Pitfall:** some sites break if you send a mobile UA. Use a full **desktop Chrome UA** (`Chrome/120.0.0.0`). See [`common-pitfalls.md`](common-pitfalls.md) §user-agent.

### ★ Check for dedicated popular / latest / search URLs (MKissa lesson, session 04)

Many sites have **dedicated pages** for Popular, Latest, and Search — each with its own URL pattern
and parameters. Don't assume the home page IS the popular page. Instead:

1. **Navigate the site in `agent-browser`** — click the "Popular" link, the "Latest" link, and
   perform a search. Note the FINAL URL of each (after any redirects).
2. **Compare the URLs** — are they different paths? Different query params on the same path?
   Different API endpoints entirely?
3. **Match the site's default parameters exactly.** For example, MKissa's popular page uses
   `range=1` (Daily) by default — if your extension uses `dateRange=7` (Weekly), the Popular tab
   shows DIFFERENT anime than what the user sees on the site. This looks like a bug ("popular and
   latest are mixed up") even though the code is technically correct. **Always match the site's
   default query parameters.**

| Site page | URL to check | What to verify |
|---|---|---|
| Popular | `<site>/popular` or `<site>/popular?type=anime&range=1` | What `range`/`dateRange` value is the default? (Daily=1, Weekly=7, Monthly=30) |
| Latest | `<site>/search/anime?sortBy=Recent&...` or `<site>/latest` | What `sortBy` value? What `sortDirection`? What `translationType`? |
| Search | `<site>/search/anime?...` or `<site>/filter?keyword=...` | Does search compose with filters? What are the filter param names? |

4. **Test each URL via curl** with the exact parameters the site uses — confirm the response shape
   matches what your extension will parse.
5. **Document the exact default parameters** in your site-analysis doc, and use them in your
   extension's `popularAnimeRequest` / `latestUpdatesRequest` / `searchAnimeRequest`.

> **MKissa lesson (session 04):** the Popular tab used `dateRange=7` (Weekly) while the site's
> default popular page uses `range=1` (Daily). The mismatch made the Popular tab show different
> anime than the site, which the user reported as "not configured properly". Fix: match the site's
> default `range=1`. See `EXTENSIONS/mkissa/MEMORY/session-logs/2027-06-29_session-04_*.md`.

### ★ If the site is a SPA (SvelteKit / React / Vue) — check network requests

Many modern anime sites are **single-page applications** that render content client-side via JavaScript.
Curl alone returns an empty `<div id="app"></div>` — no useful content. For these sites:

1. **Use `agent-browser`** to load the page and capture network requests:
   ```bash
   agent-browser open https://site.example/anime
   agent-browser wait --load networkidle
   agent-browser network requests  # ← find the API calls here
   ```
2. **Find the API endpoint** — look for `fetch` / `xhr` requests to an API domain (e.g. `api.allanime.day`, `api.site.example/graphql`). The real data comes from there, not the HTML.
3. **Test the API via curl** — replicate the request (method, headers, body) and verify the JSON response.
4. **Build against the API, not the HTML.** Your extension's `popularAnimeRequest` / `searchAnimeRequest` / `animeDetailsRequest` should POST/GET to the API endpoint, not the HTML page.

**MKissa lesson (session 01):** mkissa.to is a SvelteKit SPA on the `api.allanime.day` GraphQL API. The HTML page has no useful content — all data comes from GraphQL POST requests. Building against the API (not the HTML) was the key insight.

See [`reference-prior-solutions.md`](reference-prior-solutions.md) §graphql-api-sites for the full pattern.

---

## 1.3 Identify the search mechanism (critical — sites differ a lot)

Search is where sites differ most. Investigate carefully:

- [ ] Is there an **autosuggest / live-search** AJAX endpoint? (e.g. `/ajax/anime/search?q=`)
- [ ] Is there a **full search page** with filters? (e.g. `/filter?keyword=&genre[]=&page=`)
- [ ] Do the two return the same results? (Often they DON'T — the autosuggest may return fewer/different results.)
- [ ] **Test both** with agent-browser: type in the search box (triggers autosuggest) AND submit a full search (loads the filter page). Compare.
- [ ] Does search work **with filters simultaneously**? (AniKoto: yes — `/filter?keyword=&genre[]=&page=`.) Some sites make you choose.
- [ ] **Pagination**: how does page 2 work? `?page=2`? `?offset=30`? Infinite scroll (AJAX)?

> **AniKoto lesson (session 42):** the autosuggest endpoint returned HTTP 500; AniKoto switched to the paginated `/filter?keyword=` endpoint. **Test both before choosing.** See `../anikoto/MEMORY/issues-resolutions/` and `../anikoto/MEMORY/sites/getsources-migration-and-id-analysis.md`.

Record the chosen search approach + WHY in `MEMORY/sites/site-analysis.md`.

---

## 1.4 Test the server-list paths (ALL of them)

A site usually has **multiple ways to discover video servers**. Enumerate and test EACH one's full
resolve chain:

```
   anime detail page
        │
        ├─ PATH A: server list embedded in HTML → each server's embed URL
        ├─ PATH B: AJAX/mapper API → returns server list JSON
        └─ PATH C: episode page → player iframe → server list
        │
        ▼
   for each server:
        ├─ embed page HTML → extract video source URL
        ├─ AJAX API → returns m3u8/mp4 URL
        └─ m3u8 playlist → variant streams (1080p/720p/360p)
        │
        ▼
   playable video? (TEST with agent-browser or ffprobe)
```

For EACH path + EACH server:
- [ ] What's the URL?
- [ ] What params/headers does it need?
- [ ] What's the response shape?
- [ ] Does it resolve to a **playable** video? (Don't assume — test.)
- [ ] Is it **WAF-blocked** (Cloudflare challenge, 403, JS challenge)? If so, note it — you'll need WebView fallback (see Step 4).
- [ ] Are there **tokens** that need extraction (e.g. RC4-decrypted `vrf` param)? Note the crypto.

> **AniKoto reference:** AniKoto's server analysis (4 servers + Kiwi-Stream): `../anikoto/MEMORY/sites/servers.md`. The mapper API (PATH B): `../anikoto/MEMORY/sites/getsources-migration-and-id-analysis.md`.
> **Pitfall:** don't conclude from ONE server's success. Test ALL servers from ALL paths. See [`common-pitfalls.md`](common-pitfalls.md) §test-all-servers.

---

## 1.5 Confirm audio types + labeling (rule §7)

Sites have 3 audio types: **SUB** (subbed), **HSUB** (hardsub), **DUB** (dubbed) — not 2. Verify:

- [ ] Does THIS site have all 3? Or just SUB + DUB? Or something else?
- [ ] What **labels** does the site use? ("Sub", "Subbed", "HSUB", "Hardsub", "Dub", "Dubbed"?)
- [ ] Which HTML attribute / data field indicates the audio type? (`data-type`? `data-audio`? a CSS class? the server name?)
- [ ] Are the same videos served under different audio labels? (Token sharing — you'll need dedup.) Test by comparing video URLs across audio types.
- [ ] How should each be **labeled in the extension**? Match the site's own terminology (rule §7). Don't show "H-Sub" for what the site calls "Sub".

> **AniKoto reference:** audio-type analysis: `../anikoto/MEMORY/sites/audio-types.md`. Token sharing + dedup: `../anikoto/MEMORY/sites/tokens-and-dedup.md`.
> **Pitfall:** mislabeling audio is a common bug. See [`common-pitfalls.md`](common-pitfalls.md) §audio-types.

---

## 1.6 Check for CDN / WAF protection

Many anime CDNs sit behind Cloudflare or custom WAFs. Investigate:

- [ ] Does the main site use Cloudflare? (Check response headers for `cf-ray`, `server: cloudflare`.)
- [ ] Do the video CDNs use Cloudflare or a custom WAF?
- [ ] Which CDNs **403 / challenge** a plain OkHttp request? (Test with curl — if 403, you'll need WebView.)
- [ ] Are there **per-stream Referer** requirements? (Some CDNs only serve video if `Referer: <embed-page>` is set.) Test by fetching a video URL with and without the Referer header.

> **AniKoto reference:** CDN/WAF analysis: `../anikoto/MEMORY/sites/cdn-waf.md`. WebView fallback design: `../anikoto/MEMORY/modules/03-video-pipeline.md` + `../anikoto/MEMORY/issues-resolutions/` (session 30-31).

---

## 1.7 Check for PNG-wrapped streams (some sites do this)

Some sites wrap m3u8/ts streams as PNG images to evade ad-blockers. Investigate:

- [ ] Do any video URLs end in `.png` but contain video data? (Download one, `file` it — if it says "PNG image data" but the bytes look like ts, it's wrapped.)
- [ ] If yes, you'll need a local proxy to strip the PNG header. See how AniKoto did it.

> **AniKoto reference:** PNG wrapping analysis + LocalProxyServer design: `../anikoto/MEMORY/sites/png-wrapping.md` + `../anikoto/MEMORY/modules/03-video-pipeline.md`.

---

## 1.8 Verify the identity fields

By now you should be able to confirm (or know what to ask the user):

| Field | How to verify |
|---|---|
| **Display name** | What does the site call itself? (Check `<title>`, footer, logo alt-text.) |
| **Language** | What language is the site UI? (`en`, `ja`, etc.) |
| **Is NSFW** | Does the site host adult content? (Check for 18+ warnings.) |
| **Domain** | Confirmed in §1.1. |
| **Package name** | Convention: `eu.kanade.tachiyomi.animeextension.<lang>.<name>` |
| **extClass** | Convention: `...<lang>.<name>.<ClassName>`. Use FULL path (no leading dot) if applicationId ≠ source package. See [`common-pitfalls.md`](common-pitfalls.md) §extClass. |
| **versionId** | Start at `1`. Pick carefully — STABLE once published. |

Update `EXTENSIONS/<name>/EXTENSION.md` with confirmed values. Mark anything still uncertain **[ASK USER]**.

---

## 1.9 Write the site-analysis document

Create `EXTENSIONS/<name>/MEMORY/sites/site-analysis.md` with everything you found. Structure:

```markdown
# <Site Name> — Site Analysis

> Status: VERIFIED (date) · Last updated: YYYY-MM-DD
> Analyst: <session# or name>

## 1. Base URL + domain
## 2. URL structure (home, popular, latest, search, filters, detail, episodes, watch)
## 3. Search mechanism (autosuggest vs filter page; pagination; filters+search together?)
## 4. Server-list paths (PATH A/B/C; each server's full resolve chain)
## 5. Audio types + labels (SUB/HSUB/DUB; which attr; dedup needed?)
## 6. CDN/WAF (Cloudflare? per-stream Referer? WAF-blocked CDNs?)
## 7. PNG wrapping? (yes/no; if yes, LocalProxyServer needed)
## 8. Identity fields confirmed (name, lang, NSFW, domain, package, extClass)
## 9. Open questions (anything to ask the user)
```

> **AniKoto's site-analysis is the gold-standard example:** `../anikoto/MEMORY/sites/site-analysis.md` (+ the 8 sibling files: endpoints, servers, audio-types, cdn-waf, png-wrapping, tokens-and-dedup, getsources-migration-and-id-analysis, catalog-and-episodes-analysis).

---

## 1.10 Verification checklist (Step 1 is done when ALL pass)

- [ ] Live domain confirmed (agent-browser loads it; it serves real content)
- [ ] URL structure mapped (all 8 patterns: home, popular, latest, search, filters, detail, episodes, watch)
- [ ] Search mechanism tested (autosuggest vs filter page; both verified; chosen approach documented)
- [ ] ALL server-list paths tested (each server's full resolve chain → playable video confirmed)
- [ ] Audio types + labels confirmed (which exist; what attribute; dedup needed?)
- [ ] CDN/WAF behavior documented (which CDNs need WebView; per-stream Referer requirements)
- [ ] PNG wrapping checked (yes/no)
- [ ] Identity fields confirmed in `EXTENSION.md` (or marked [ASK USER])
- [ ] `MEMORY/sites/site-analysis.md` written + browser-verified
- [ ] Any open questions listed for the user

**Only when all pass → proceed to Step 2.** If any fail, you don't have enough to implement
correctly — investigate more or ask the user.

---

## What to ask the user about (common Step 1 questions)

- "The live domain seems to be `animepahe.ru` — `animepahe.com` redirects there. Confirm?"
- "The site has SUB and DUB but I don't see HSUB. Should I implement just SUB+DUB, or look harder?"
- "Server X returns 403 to OkHttp but works in WebView. Should I implement WebView fallback for it, or skip that server?"
- "Search autosuggest returns 5 results; the filter page returns 30. Which should I use?" (Recommendation: filter page, for pagination + filters — but confirm.)
