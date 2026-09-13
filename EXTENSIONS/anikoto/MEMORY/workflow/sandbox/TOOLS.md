# TOOLS.md — Advanced scraping tools (installed & researched, NOT yet in active use)

> **Status (2026-09-13, session 55):** Both tools installed in an **isolated venv** and verified
> with smoke tests. Per user instruction: *"for the tools only install and research about them —
> next I'll tell you and then we will do the next."* Nothing in the extension pipeline uses them yet.

---

## 1. The venv (read this first)

Everything lives in a dedicated virtualenv so the scraping stack can never break the sandbox's
pinned main venv (`/home/z/.venv`, managed by uv + uv.lock).

```bash
# Use it
source /home/z/scrape-venv/bin/activate
# or call binaries directly (preferred in scripts):
/home/z/scrape-venv/bin/python
/home/z/scrape-venv/bin/scrapling        # CLI (scrapling install / scrapling parse ...)
```

| Package | Version | Purpose |
|---|---|---|
| `scrapling` | **0.4.15** | Adaptive/anti-healing scraping library (fetch + self-correcting selectors) |
| `scrapling[fetchers]` | — | Extra: `patchright 1.62.3` (stealth Chromium), `playwright 1.62.0`, `curl_cffi 0.16.3`, `browserforge 1.2.4` |
| `scrapegraphai` | **2.2.4** | LLM-powered scraping pipelines ("graphs") |
| `scrapegraph-py` | 2.3.1 | ScrapeGraph hosted-API client (dep of scrapegraphai 2.x) |

### ⚠️ Sandbox install caveats (IMPORTANT for future sessions)
- **Never run `scrapling install` here** — it always dies on
  `playwright install-deps chromium` (needs apt/sudo, sandbox has none).
- Engines are installed **directly** instead (already done, exit 0):
  ```bash
  /home/z/scrape-venv/bin/python -m playwright install chromium    # ok
  /home/z/scrape-venv/bin/python -m patchright install chromium    # ok
  ```
- Browser binaries are shared in `~/.cache/ms-playwright` (chromium-1200, chromium-1234,
  headless shells, ffmpeg). If a future playwright upgrade wants a new build, run the two
  commands above again. **Verified working:** `StealthyFetcher.fetch('https://example.com')`
  → 200 via real Chromium launch.
- If the venv is ever wiped, rebuild with:
  ```bash
  uv venv /home/z/scrape-venv --python 3.12
  uv pip install --python /home/z/scrape-venv/bin/python 'scrapling[fetchers]' scrapegraphai
  /home/z/scrape-venv/bin/python -m playwright install chromium
  /home/z/scrape-venv/bin/python -m patchright install chromium
  ```

---

## 2. Scrapling 0.4.15 — adaptive scraping (API introspected on the installed version)

**What it is:** a scraping library whose core idea is *self-healing selectors*. When a site
changes its DOM (the #1 reason Anikoto parsers break), scrapling can re-locate elements by
similarity/text/structure and even generate the new CSS/XPath for you — exactly the failure
mode this extension keeps hitting (anikototv HTML changes, megaplay iframe changes).

### Verified API surface (0.4.15 — note: changed vs older tutorials!)
- Top-level exports: `Fetcher, AsyncFetcher, StealthyFetcher, DynamicFetcher, Selector`
- ⚠️ **No `css_first`** — `css()` returns a list; use `css('sel')[0]` or `.extract_first`.
- ⚠️ `auto_match` kwarg on `Fetcher(...)` is deprecated — ignore it.
- Fetchers can be used **without instantiation** (`Fetcher.get(url)` works).

```python
from scrapling import Fetcher, StealthyFetcher, DynamicFetcher

# Plain HTTP (httpx engine, fast) — good for API/JSON endpoints
r = Fetcher.get('https://anikototv.to/watch/some-slug/ep-1')
r.status                    # 200
r.css('#watch-main::attr(data-id)')        # list of matches
r.css('title::text')[0]                    # first match
r.xpath('//title/text()')
r.json()                                   # if body is JSON (e.g. megaplay getSources)
r.markdown                                 # HTML→markdown summary (quick structure read)

# Stealth browser (patchright Chromium, real fingerprint) — for WAF/JS pages
p = StealthyFetcher.fetch(url, headless=True, network_idle=True)
# DynamicFetcher = standard playwright chromium (less stealth, simpler)
```

**Adaptive / healing methods (on any Response or Selector):**
| Method | What it does | Anikoto use case |
|---|---|---|
| `find_similar(elem)` | find elements structurally similar to one | server list / episode list items changed class names |
| `find_by_text('...')`, `find_by_regex(r'...')` | locate by content | renamed labels ("Server", "HD-1") |
| `generate_css_selector()` / `generate_full_xpath_selector()` | **generate the working selector** for an element found by text/similarity → paste into Kotlin | the actual "repair" step |
| `relocate()` / `retrieve()` | re-find an element after page changes | long-lived debugging session |
| `below_elements`, `siblings`, `parent`, `children`, `iterancestors` | structural navigation | DOM chains without brittle selectors |
| `captured_xhr` (browser fetchers) | capture XHR traffic | see what megaplay's own player requests live |

CLI extras: `scrapling parse <url> --css '...'` for one-off probing.

**Rules of engagement:** 1–2 requests per endpoint per diagnosis step, small sleep between
steps (the site sits behind a WAF; sessions 36/43 showed rate-limit sensitivity). Prefer
`Fetcher` (plain HTTP) unless the page genuinely needs JS — that mirrors what the extension's
OkHttp client sees, which is the ground truth we care about.

---

## 3. ScrapeGraphAI 2.2.4 — LLM-driven extraction (API inventoried)

**What it is:** scraping pipelines ("graphs") where an LLM reads the page/API response and
returns structured output. Best for *understanding unknown/changed structures* — e.g.
"here's megaplay's new getSources JSON, what are the field names for streams/subtitles?"

**Available graph classes (full inventory of the installed version):**
`SmartScraperGraph` (page→structured data), `SmartScraperLiteGraph`, `SmartScraperMultiGraph`
(+MultiBatch/MultiConcat), `ScriptCreatorGraph` (generate reusable extraction scripts),
`SearchGraph`/`OmniSearchGraph`/`SearchLinkGraph` (search-engine driven),
`JSONScraperGraph`/`CSVScraperGraph`/`XMLScraperGraph`/`DocumentScraperGraph`,
`OmniScraperGraph` (with images), `ScreenshotScraperGraph`, `SpeechGraph`, `CodeGeneratorGraph`,
`DepthSearchGraph` (+ Multi variants, `AbstractGraph`/`BaseGraph` base classes).

**Runtime requirement — an LLM backend.** scrapegraphai does nothing without one. Any
OpenAI-compatible endpoint works via `graph_config["llm"]`:

```python
from scrapegraphai.graphs import SmartScraperGraph

graph_config = {
    "llm": {
        "model": "openai/gpt-4o-mini",          # or any OpenAI-compatible model id
        "api_key": "<KEY>",                      # DO NOT hardcode in repo files
        "openai_api_base": "<OPENAI-COMPATIBLE-BASE-URL>",  # e.g. a local/other gateway
        "temperature": 0,
    },
    "verbose": True,
    "headless": False,
}
g = SmartScraperGraph(
    prompt="List every stream URL, its quality label and audio type in JSON.",
    source="https://megaplay.buzz/stream/s-2/131394/sub",   # or a local HTML file path
    config=graph_config,
)
print(g.run())
```

> We did **not** configure a key yet (user hasn't chosen a provider — that's the "next" step).
> When the user says go: ask which endpoint/key to use, then fill it in at runtime via env var,
> never in committed files.

**Anikoto use cases:**
1. **Schema mapping after API changes** — megaplay/site changes JSON shape → feed a captured
   response + prompt "map fields to our SourcesData DTO" → get the rename table.
2. **Diff explanation** — old HTML vs new HTML → "what changed that would break selector X".
3. **ScriptCreatorGraph** — auto-write a python extraction prototype we then port to Kotlin
   (same flow as the hand-written python sims in sessions 52–54, but faster).
4. `SmartScraperMultiGraph` — run the same question across all 6 anikototv mirrors at once.

**Rules of engagement:** LLM calls cost tokens and can hallucinate field names — always
**verify graph output against a real raw response** (curl) before touching Kotlin code.
Never point graphs at the site with aggressive parallelism.

---

## 4. How the two tools fit the existing workflow (preview — activate on user's go)

```
User reports breakage
   └─ WORKFLOW.md Phase 2 (diagnosis) enhanced:
        a) curl/python chain probe           (existing, ground truth)
        b) scrapling Fetcher replay of each step          ← same probe, structured output
        c) if HTML structure changed → scrapling find_similar +
           generate_css_selector  ⇒ new selectors for Kotlin
        d) if API/JSON changed → scrapegraphai SmartScraperGraph
           over captured response ⇒ field-mapping table (then VERIFY manually)
        e) continue with Phase 3 (fix & build) unchanged
```

Hard rule: **tools assist diagnosis; shipped Kotlin code must still be verified against raw
curl-style requests** (browser-TLS quirks like the v16.11 CDN rotation can't be seen by an
LLM — only by replicating the app's exact request path).

---

## 5. ★ SESSION 56 — FIRST REAL-USE REPORT (the honest verdict)

Both tools were used for actual diagnosis work (ep-8 Calamity server bug + smart-search design).

### Scrapling 0.4.15 — **genuinely helpful, earned its place** ✅
Used as the primary live-probe client for the whole anikototv→megaplay chain:
- Walked watch page → episode list → server list → server?get → megaplay iframe → getSources
  (enc/AES decrypt via pycryptodome in the venv) → master m3u8 — all with `Fetcher` (HTTP),
  plus one `StealthyFetcher` run.
- **Finding 1 (server bug)**: with correct AES-256 (key zero-padded to 32), `s=bcdn` →
  `ncdn.imgnex.top` master 200 OK; `tcdn`/default → 403 `fetch.nexabloom.top`. This pinpointed
  the root cause of "only HD-2 shows": Vidstream-2's iframe has no `s=` and our hardcoded
  `tcdn` fallback is now TLS-gated. **`bcdn` is the current OkHttp-friendly selector.**
- **Finding 2 (quality bug)**: the master literally ships `RESOLUTION=640x360 NAME="480p"` —
  mislabel proven live; extension now trusts RESOLUTION (site's hls.js player does the same).
- **Finding 3**: megaplay's inline bypass-check (`"tcdn"!==s&&"bcdn"!==s`) whitelists its CDN
  selectors → we now auto-discover candidates from that pattern (self-updating against
  rotation). Also `megaplay-1.buzz` spotted via the page's analytics beacon.
- **Smart-search experiment**: `StealthyFetcher` reproducing the extension's legacy Google
  scrape got **HTTP 429 + "unusual traffic" bot-wall** — empirically proves why legacy smart
  search fails for users and validates our new error-taxonomy markers (`unusual traffic`,
  captcha, consent, sign-in).
- Small caveats: `css_first` gone in 0.4.x (use `css()[0]`); `auto_match` deprecated warning.

### ScrapeGraphAI 2.2.4 — **installed & tested, but unusable without an LLM key** ⚠️
- Attempted a real `SmartScraperGraph` run with no LLM config → clean immediate
  `KeyError: 'llm'`. The tool is architecturally an LLM-pipeline wrapper: **every** graph
  needs `config["llm"]` (any OpenAI-compatible endpoint or hosted scrapegraph-py key).
- Verdict for the workflow: it would shine for "map a NEW API/HTML schema" days (feed a
  captured response → structured field-mapping), but **cannot participate until the user
  provides an endpoint/key** (drop it into SECRETS.md, wire per §3). Not a loss: every job
  it would have done today was done deterministically with scrapling + curl-level probing.

### Bonus tool added to the venv: **tree-sitter-kotlin** ✅
- `uv pip install tree-sitter tree-sitter-kotlin` (in scrape-venv). Parses Kotlin and gives
  REAL syntax verification without an SDK — caught/pre-verified all v16.12 edits before CI
  (CI then confirmed after 2 real compile fixes: companion-object statics, explicit casts,
  builder imports). New standard: **tree-sitter parse before every push.**

### Process lesson (CI iteration cost)
3 workflow runs to green: (1) unresolved refs (static-call + smart-cast on sealed failure
types), (2) missing `buildJsonObject/buildJsonArray` imports, (3) ✅. Rule: after writing
Kotlin without a local compiler, run tree-sitter AND grep your new call-sites for
static-vs-instance access before pushing.

---

## 6. ★ SESSION 57 — SECOND REAL-USE REPORT (honest update)

### Scrapling — decisive again ✅
- StealthyFetcher captured Google's AI Mode pages live and produced the ROOT CAUSE of the
  user-reported legacy-engine failure: the v16.12 code searched Google with the entire
  bracket-laden LLM prompt; AI Mode answered in a conversation layout with none of the
  markers the extractor expected. The capture also revealed the streaming-answer behavior
  ("response is ready" shell before the answer) → drove the WebView stability-polling design
  and the new marker/pattern set.
- Caveat: ~8 udm=50 fetches in 30 min got the sandbox IP CAPTCHA-walled (new data point for
  the block-classifier, but pace the probes).
- tree-sitter-kotlin (venv) again gated all 4 edited Kotlin files pre-push: all OK.

### ScrapeGraphAI — first REAL attempt, blocked by region (not by the tool) ⚠️
- Wired with the user-provided Gemini key via Google's OpenAI-compatible endpoint
  (`model: openai/gemini-3.5-flash-lite`, `openai_api_base:
  https://generativelanguage.googleapis.com/v1beta/openai/`). The graph built and ran
  end-to-end (chromium fetch + LLM call) but the Gemini call failed with
  `400 FAILED_PRECONDITION — User location is not supported` because the sandbox egresses
  from Hong Kong. Verdict: mechanically sound, correct wiring now documented, but
  LLM-powered graphs remain unusable FROM THIS SANDBOX regardless of key. Also noted:
  scrapegraphai 2.2.4 does not accept the `gemini/` provider prefix (use the openai/ route).
- The LLM job it would have done (validate the smart-search prompt) was done via the z-ai
  LLM skill instead: exact-title anchoring added after catching a "frieren"→"My Hero
  Academia" hallucination; 7/7 then stable.
