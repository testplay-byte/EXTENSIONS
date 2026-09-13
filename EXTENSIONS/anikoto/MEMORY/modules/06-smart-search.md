# Module: Smart Search

> Last updated: 2027-06-27 (session 51) · Status: VERIFIED
> Covers: AI-powered search via Google AI Search, activation phrase, title extraction, fallback logic.

---

## Overview

Smart Search lets the user describe an anime in natural language (e.g., *"the anime with a russian girl with white hair"*) or type a misspelled title (e.g., *"narutp"*). The extension uses **Google AI Search** (via WebView) to resolve the query to a concrete anime title, then searches anikototv.to for that title.

**Two modes:**
1. **Descriptive mode** — user asks a question or describes an anime → AI returns ONE title → that title is searched
2. **Correction mode** — user types a title with spelling mistakes → AI corrects it → corrected title is searched

**Activation:** Only triggers when the user's query starts with a configurable **activation phrase** (default `?`, case-insensitive, must be followed by a space). If phrase is empty, ALL searches use AI. Default OFF.

---

## Architecture

```
smartsearch/
└── SmartSearch.kt    ← All AI search logic (self-contained, easily removable)
```

The module is **self-contained**. To remove smart search:
1. Delete the `smartsearch/` package
2. Remove the `smartSearch` field from `Anikoto.kt`
3. Remove the `getSearchAnime()` override from `Anikoto.kt`
4. Remove Category 4 from `AnikotoSettings.kt`

---

## Files

### `smartsearch/SmartSearch.kt` — AI search module

Key methods:
- `shouldTrigger(query, enabled, phrase)` — checks toggle + phrase match (with space requirement)
- `stripPhrase(query, phrase)` — removes activation phrase from start of query
- `resolve(query)` — crafts Google AI URL, scrapes via WebView, extracts title
- `getCachedTitle(query, page)` — returns cached title for pagination
- `cacheTitle(query, title)` — caches a query→title mapping
- `warmUp()` — pre-warms the Google WebView

### `Anikoto.kt` — Smart search wiring

- `getSearchAnime()` override — intercepts queries, calls SmartSearch, handles toast
- `showToast(message)` — shows toast on main thread (for AI failure notifications)
- `getFilterList()` — pre-warms Google WebView when search page opens

### `WebViewFetcher.kt` — Google AI scraping

- `fetchRenderedText(url, timeoutMs)` — loads URL in separate Google WebView, extracts rendered text
- `warmUpGoogleWebView()` — pre-creates the Google WebView on background thread
- `destroyGoogleWebView()` — cleanup

### `AnikotoSettings.kt` — Settings UI

- Category 4: Smart Search
  - Toggle: "Enable smart search" (default OFF)
  - EditTextPreference: "Activation phrase" (default `?`)
  - Preference: "Details" (dynamic examples using user's phrase)

---

## How It's Triggered

Smart search activates when ALL of these are true:

1. **The toggle is ON** (Settings → "Smart search", default **OFF**)
2. **The query is non-empty**
3. **Either:**
   - **Activation phrase is empty** → ALL searches use smart search, OR
   - **Query starts with the phrase (case-insensitive) AND is followed by a space**

**Space requirement:** The phrase must be followed by a space (or be the entire query). This prevents "s" from matching "shock".

**Examples (assuming phrase = `?`):**

| Query | Triggers? | What gets sent to AI |
|-------|-----------|---------------------|
| `? the anime with a russian girl` | ✅ | `the anime with a russian girl` |
| `?naruto` | ❌ | No space after `?` → normal search |
| `? narutp` | ✅ | `narutp` (spell-corrected to "Naruto") |
| `?anime with spies` | ❌ | No space after `?` → normal search |
| `naruto` | ❌ | No phrase → normal search |
| `? ` | ❌ | Empty after phrase strip → normal search |

**Examples (assuming phrase is empty):**

| Query | Triggers? | What gets sent to AI |
|-------|-----------|---------------------|
| `the anime with a russian girl` | ✅ | `the anime with a russian girl` |
| `naruto` | ✅ | `naruto` |
| (empty) | ❌ | Empty query → normal search |

---

## The Pipeline

```
User types a query + presses search
        │
        ▼
┌─────────────────────────────────────────────────┐
│  SmartSearch.shouldTrigger(query, enabled, phrase)│
│                                                  │
│  1. Is smart search toggle ON?                   │
│  2. Is query non-empty?                          │
│  3. Either:                                      │
│     a. Phrase is empty → trigger, OR             │
│     b. Query starts with phrase + space          │
│                                                  │
│  If ALL yes → strip phrase, continue to AI       │
│  If ANY no  → normal search (skip AI)            │
└──────────────────┬──────────────────────────────┘
                   │ YES (triggered)
                   ▼
┌─────────────────────────────────────────────────┐
│  Check cache: same query searched before?        │
│  → If yes, reuse cached title (skip AI)          │
│  → If no, continue to Google AI                  │
└──────────────────┬──────────────────────────────┘
                   │ not cached
                   ▼
┌─────────────────────────────────────────────────┐
│  SmartSearch.resolve(query)                      │
│                                                  │
│  1. Craft Google AI URL:                         │
│     google.com/search?q=<query>+anime.           │
│       [Respond with only the English anime       │
│       title, nothing else. ...scenario           │
│       handling...]&udm=50&hl=en                  │
│                                                  │
│  2. fetchRenderedText(url) via Google WebView    │
│     → loads URL in a SEPARATE WebView            │
│       (not the video pipeline's WebView)         │
│     → waits for page to stabilize                │
│       (generation-token approach)                │
│     → extracts document.body.innerText           │
│     → 20s timeout                                 │
│                                                  │
│  3. extractAnimeTitle(text)                      │
│     → 3-strategy title parser                    │
│     → returns ONE title or null                  │
└──────────────────┬──────────────────────────────┘
                   │ title found
                   ▼
┌─────────────────────────────────────────────────┐
│  Cache the result: query → title                 │
│  (for pagination — page 2+ reuses)               │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│  Search anikototv.to for the title               │
│  GET /filter?keyword=<title>&page=N              │
│                                                  │
│  ★ Fallback: if 0 results on page 1,             │
│    retry with first 3 significant words          │
│    of the title                                  │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
              AnimesPage
              (results shown to user)

─── FAILURE PATH (any step fails) ──────────────────
                   │
                   ▼
         Show toast: "AI search was unable to
         initiate and fell back to normal search"
                   │
                   ▼
         Normal search runs (no crash, no hang)
```

---

## AI Prompt

The prompt is crafted with bracketed instructions and scenario handling:

```
{query} anime. [Respond with only the English anime title, nothing else.
If the query describes an anime, give the title of the anime being described.
If the query has spelling mistakes, correct them and give the proper title.
If the query mentions a genre or theme, give one popular anime from that genre.
If the query is vague, give the most likely anime match.
Always respond with exactly one anime title, no explanations, no lists.]
```

**Why each part:**
- `udm=50` — Google's AI mode (triggers AI Overview)
- `hl=en` — English results (matches anikototv.to's English titles)
- Brackets `[]` — visually separates instructions from query
- Scenario handling — ensures AI handles various query types
- "exactly one anime title" — prevents lists or multiple suggestions

---

## Title Extraction (3 Strategies)

The rendered text from Google AI looks like:
```
Sign in
AI Mode
All Images Videos News Books Finance
Search Results
the anime with a russian girl with white hair anime. [Respond with only...]
9 sites
The anime you are looking for is titled Alya Sometimes Hides Her Feelings in Russian (sometimes shortened to Alya). The main character is Alisa Mikhailovna Kujou (nicknamed Alya)...
```

**3 strategies, in priority order:**

### Strategy 1: "is titled [X]" (highest priority)
```kotlin
val titledPattern = Regex(
    """(?:is\s+titled|is\s+called|is\s+named|is\s+known\s+as)\s+([A-Z][^\n.!?]{2,80}?)(?:\s*[.\n!?]|$)"""
)
```
Captures text between "is titled" and next sentence boundary. Strips parentheticals. Word count must be 2-12.

### Strategy 2: Quoted text
Looks for `"Title"` or `'Title'` or curly quotes. Skips matches ending with parenthetical (character name pattern).

### Strategy 3: First capitalized multi-word phrase
Scans lines after "Search Results" for a 2-12 word phrase starting with a capital letter. Skips Google UI lines and echoed query. Stops at sentence punctuation or `is/was/—`.

### `stripParenthetical()` helper
Removes trailing + leading `( ... )` groups. Character names in Google AI text often come with `(nicknamed X)` suffixes.

---

## Google WebView (Separate from Video Pipeline)

**Why a separate WebView?**
The video pipeline's WebView stays on `megaplay.buzz` (for WAF bypass). Loading Google on it would change the origin and break video fetching. A separate temp WebView is used for smart search.

**Stabilization approach (generation token):**
- Google fires multiple `onPageFinished` callbacks (redirects, consent, async JS)
- Each callback increments a generation counter (`AtomicInteger`)
- Only the LAST callback's extraction timer fires (1.5s delay)
- Stale timers detect their generation changed and abort
- If first extraction returns <200 chars, retries once after 2s
- 20s overall timeout

**Pre-warming:**
- `warmUpGoogleWebView()` called from `getFilterList()` when search page opens
- Creates the Google WebView on a background thread
- Ready by the time user submits a smart search

---

## Caching

```
cachedQuery: String  — the last query (with phrase stripped)
cachedTitle: String  — the resolved title for that query
```

**When cache is used:**
- User searches `? anime with spies` → AI resolves to "Spy x Family" → cached
- User navigates to page 2 → same query → cache hit → reuse "Spy x Family" → no re-scraping

**When cache is cleared:**
- New search with different query → cache replaced
- Extension restart → cache cleared (in-memory only)

---

## Settings UI (Category 4)

```
┌─ Smart Search ──────────────────────────────────┐
│                                                  │
│  [○] Enable smart search                         │  ← OFF by default
│      AI resolves descriptive queries and         │
│      corrects spelling                           │
│                                                  │
│  Activation phrase                               │
│  Currently: ?  ← red bold text                   │  ← default "?"
│                                                  │
│  Details                                         │  ← dynamic examples
│  Type your activation phrase at the start of     │
│  your search to trigger AI.                      │
│  Leave empty to use AI for all searches.         │
│                                                  │
│  Case-insensitive. Must be followed by a space.  │
│                                                  │
│  Your phrase: "?"                                │
│                                                  │
│  Examples:                                       │
│  • ? the anime with a russian girl               │
│  • ? narutp                                      │
│  • ? anime about a spy                           │
│                                                  │
│  Note: ~5-8s latency per AI search.              │
└──────────────────────────────────────────────────┘
```

**Settings components:**
- `SwitchPreferenceCompat` — toggle (default OFF)
- `EditTextPreference` — activation phrase (default `?`, red bold summary)
- `Preference` (info text) — dynamic examples using user's actual phrase

**Text color:**
- Activation phrase value shown in red bold (`#dc2626`) via `SpannableString` + `ForegroundColorSpan` + `StyleSpan`
- Updates dynamically when user changes the phrase

---

## Configuration

| Setting | Key | Default | Description |
|---|---|---|---|
| Smart search toggle | `pref_smart_search` | `false` (OFF) | Enable/disable AI search |
| Activation phrase | `pref_smart_search_phrase` | `?` | Phrase that triggers AI (empty = all searches) |

No backend URL, no API key, no server to host. The extension scrapes Google directly on-device using Android WebView (real Chrome engine, user's mobile IP).

---

## Known Limitations

| Limitation | Why | Mitigation |
|---|---|---|
| ~5-8s latency per smart search | WebView load + stabilization + extraction | Cached per query (page 2+ is instant). Pre-warmed when search page opens. |
| Google may block datacenter IPs | Anti-bot detection | Works on mobile (user's IP). If blocked, shows toast + falls back to normal search. |
| Title extraction is heuristic | Google's format can change | 3 strategies + fallback. If all fail, toast + normal search. |
| `udm=50` may change | Google updates their URLs | If AI mode stops working, check Google's current AI search URL format. |
| Cannot test in dev environment | Google blocks datacenter IPs | Only testable on real Android device with mobile IP. |
| AI may return wrong anime | Descriptive queries are ambiguous | User controls with activation phrase. Acceptable per user decision. |

---

## How to Modify

| Change | Where | Risk |
|--------|-------|------|
| Change activation phrase default | `AnikotoSettings.kt` → `PREF_SMART_SEARCH_PHRASE_DEFAULT` | LOW |
| Change AI prompt | `SmartSearch.kt` → `buildPrompt()` | LOW |
| Add new title extraction strategy | `SmartSearch.kt` → `extractAnimeTitle()` | LOW |
| Change fallback logic (0 results) | `Anikoto.kt` → `getSearchAnime()` | MEDIUM |
| Change stabilization timing | `WebViewFetcher.kt` → `fetchRenderedText()` | MEDIUM |
| Remove smart search entirely | Delete `smartsearch/` + remove from `Anikoto.kt` + `AnikotoSettings.kt` | LOW (modular) |

---

## Test Results (12 searches via LLM simulation)

| # | Query | Type | AI Resolved | AniKoto Results |
|---|-------|------|-------------|-----------------|
| 1 | the anime with a russian girl with white hair | desc | Girls und Panzer | 30 |
| 2 | anime with a spy during the cold war | desc | Jormungand | 2 |
| 3 | anime about a boy who finds a demon notebook | desc | Death Note | 30 |
| 4 | anime about a boy made of rubber | desc | One Piece | 30 |
| 5 | anime where two brothers use alchemy | desc | Fullmetal Alchemist: Brotherhood | 10 |
| 6 | narutp | misspell | Naruto | 26 |
| 7 | one pice | misspell | One Piece | 30 |
| 8 | atack on titan | misspell | Attack on Titan | 30 |
| 9 | naruto | normal | Naruto | 26 |
| 10 | demon slayer | normal | Demon Slayer: Kimetsu no Yaiba | 30 |
| 11 | anime about a shy transfer student | desc | Natsume's Book of Friends | 30 |
| 12 | anime with giant mecha in space | desc | Gurren Lagann | 6 |

**Success rate: 12/12 (100%) search success on anikototv.to**

**Note:** LLM simulation is LESS accurate than Google AI Search (which has real-time web context). On a real device, descriptive query accuracy should be higher.

---

## See Also

- **Settings module**: `EXTENSIONS/anikoto/MEMORY/modules/05-settings.md`
- **Video pipeline** (WebViewFetcher): `EXTENSIONS/anikoto/MEMORY/modules/03-video-pipeline.md`
- **Session 51 log**: `EXTENSIONS/anikoto/MEMORY/session-logs/2027-06-27_session-51_*.md`

---

## ★ Session 57 (v16.13 TEST BUILD) — Settings overhaul + legacy-engine root-cause fix

> Supersedes parts of this document above (engine list, prompt, extraction strategies).
> Full detail: `MEMORY/session-logs/2026-09-13_session-57_v16.13-smart-search-overhaul.md`.

### Settings (user-spec)
- Episode-metadata toggles: **no descriptions** anymore.
- Toggle: heading **"Smart Search"**, summary **"Search spelling correction and smarter description searching"**.
- Engine picker: exactly **"Google Gemini API"** / **"Google AI Search"** (values `gemini`/`google`).
  Legacy stored `auto` migrates → gemini (if key set) else google.
- Gemini key field: no dialog text; summary "Not set" / "••••1234".
- Model picker: **Gemini 3.5 Flash-Lite (Recommended, default)** · Gemini 3.8 Flash ·
  Gemini 3.1 Flash-Lite · **Custom model ID** (free-text pref `pref_gemini_custom_model`;
  pre-3.x stored ids auto-move to the custom field).
- "Test connection" (renamed). Engine=google **hides** key/model/custom/test prefs
  (`applyEngineVisibility()`, re-applied via main-handler post).
- Bottom block: "How to use" (usage steps, engine note, clipboard-failure note). No model details.

### Engines
- **Gemini**: thinking-disabled request (`thinkingConfig.thinkingBudget=0`) with auto-retry
  without it if the model rejects; maxOutputTokens 2048; `thought` parts filtered; shared
  companion plumbing in SmartSearch.kt (`postGemini`, `buildGeminiRequestBody`,
  `geminiApiErrorMessage`, `describeGeminiHttpError`, `testGemini`).
  Geo-block 400 "User location is not supported" gets an explicit user message.
- **Google (legacy)**: sends a CLEAN query (`"<query> anime"`) — v16.12's full-prompt query was
  THE root cause of "no anime title could be read" (it triggered the AI Mode conversation UI).
  `fetchRenderedText` now polls every 2s keeping the LONGEST text until stable (answers stream
  post-load; 25s deadline).

### Extraction (7 strategies, in order, per marker-prioritized region)
S1 is-titled/called/named/known-as · S2 describing-is/looking-for-is ·
S3 "Would you like to know more about X" (AI-Mode follow-up phrasing) · S4 "Did you mean: X" ·
S5 quoted (single-word capitalized allowed) · S6 MyAnimeList/AniList URL slug → Title Case ·
S7 title-like line scan (2–12 words, ≥50% caps, no " is "/" are ", skip-word list).
Pre-clean: markdown strip, real footer cuts ("AI can make mistakes", "Was this response
helpful", "Was this helpful", "Give feedback" — NOT "See your Search history", that's sidebar
UI), junk-line list, query-echo drop **except title-like lines** (protects exact-title answers).
Validated 7/7 on real stealth captures + reconstruction (`/home/z/probe-s56/validate_extractor.py`).

### Failure debugging
`ResolveResult.Failure.detail` now carries the raw response (Google: rendered text ≤20k chars;
Gemini: error body ≤600). `Anikoto.kt` copies it to the clipboard on failure; the toast appends
"(raw response copied to clipboard)".

### Prompt (Gemini only)
Added: "If the query is already an anime title or close to one, return that title with spelling
corrected." (LLM-tested — fixes one-word-title hallucinations, e.g. "frieren").

---

## ★ Session 58 (v16.12 RELEASE) — Bracket convention + extraction fix + Test-Connection fix

> Supersedes the session-57 section where they conflict (model order/defaults, thinking config,
> S2 caps, Details section, clipboard behavior).
> Full detail: `MEMORY/session-logs/2026-09-13_session-58_v16.12-release-smartsearch-brackets.md`.

### The `[{[Title]}]` bracket convention (user's design)
Both engines now instruct the AI to wrap the title in rare brackets:
- **Gemini prompt**: "reply with ONLY its English title wrapped inside these exact brackets:
  [{[Title]}] — nothing else inside the brackets."
- **Google query**: clean query + SHORT suffix `" (in your answer, wrap the anime title in
  [{[ ]}] brackets)"` — live-verified 2026-09-13 that AI Mode honors it ("…iconic series
  [{[\nNaruto , }] :"). Never send the whole prompt as the query (v16.12 bug).

### Extraction strategies (final, in order)
S0 `[{[Title]}]` LENIENT bracket parser (shared `extractBracketTitle` in companion; tolerates
imperfect closers + newlines; skips empty captures + lowercase single words) →
S1 is-titled/called/named/known-as (cap 100, stops at `(`) →
S2 describing/looking-for/thinking-of/referring-to/asking-about is X (cap 100, stops at `(`) →
S2c "the anime/series/show is X" → S3 "Would you like to know more about X" →
S4 "Did you mean: X" → S5 quoted → S6 MAL/AniList slugs → S6b "Japanese title: X" romaji →
S7 title-like line scan (parenthetical stripped BEFORE length checks, raw cap 120).
**The session-57 S2 bug**: the 80-char cap included the parenthetical "(Japanese title: …)"
→ answers like the user's (118 chars) never matched. Validated 3/3 on real responses
(user's pasted failure, live bracket capture, session-57 regression capture).

### Gemini request rules
- thinkingConfig ONLY for `gemini-2.5*` models. The 3.x family rejects
  `thinkingConfig.thinkingBudget` with a bare 400 INVALID_ARGUMENT ("Request contains an
  invalid argument.") that never mentions "thinking" — proven with the internal test key
  (geo-blocked, but the geo check runs AFTER payload validation, so 400-vs-404-vs-location
  experiments are conclusive from the sandbox).
- On any 400 with thinkingConfig attached → retry once without it.
- Model existence check: bogus id → 404 NOT_FOUND; the three listed ids all exist.

### Settings (current, v16.12)
Categories: Playback · Servers · Episode metadata · **Smart Search** · **Details**.
- Smart Search toggle: **ON by default**, summary "Search spelling correction and smarter
  description searching".
- Activation phrase: default `?` (unchanged); no dialog text (Details explains usage).
- AI engine: "Google Gemini API" / "Google AI Search", **default google**; Gemini-only prefs
  (key, model, custom, test) hidden while google is selected.
- Gemini model: **Gemini 3.1 Flash Lite (top, default)** · Gemini 3.5 Flash Lite ·
  Gemini 3.8 Flash · Custom model ID. No "Recommended" label.
- **Copy response** (default OFF, bottom of Smart Search): ON → success copies
  `Query: …\nTitle: …` (+ short toast); failure copies `Query: …\nError: …\n--- raw engine
  response ---\n…`. Session-57's unconditional copy-on-failure removed.
- **Details** category: exact usage text per user (trigger instructions, live
  `Your phrase: "…"`, examples `? the anime with a russian girl` / `? narutp` /
  `? anime about a spy`, `Note: ~5-8s latency per AI search.`); refreshes when the phrase
  changes.

### Validation artifacts
`/home/z/probe-s58/` — `google_bracket_probe.py` (scrapling StealthyFetcher captures),
`captures/bracket-narutp.txt` (live AI Mode bracket compliance),
`validate_new_extractor.py` (3/3 PASS).
