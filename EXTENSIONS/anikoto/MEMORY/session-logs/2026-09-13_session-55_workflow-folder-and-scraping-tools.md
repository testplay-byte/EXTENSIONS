# Session 55 — Standalone workflow folder + scrapegraphai/scrapling installed & researched

**Date:** 2026-09-13 · **Task:** User asked to (1) document the complete maintenance workflow
"from start to finish including publishing" in a **new separate folder**, and (2) **install and
research only** (not yet use) `scrapegraphai` and `scrapling` as advanced diagnosis helpers.

## Delivered 1 — `/home/z/anikoto-workflow/` (the new separate folder)

| File | Contents |
|---|---|
| `README.md` | Reading order, 30-second returning-session quick start, non-negotiables |
| `WORKFLOW.md` | Master process: Phase 0 sandbox restore → 1 intake/triage → 2 diagnosis (2A runtime chain-probe, 2B in-app update path, 2C website, 2D cosmetic) → 3 fix & build → 4 publish to dist repo (surgical) → 5 end-to-end verification → 6 memory + ntfy → 7 tool hooks. Plus 10 Golden Rules distilled from sessions 52–54. |
| `REFERENCE.md` | Extension identity (pkg/source-ID/version-code rules), release table v16.9→16.11 w/ sha256s, both repos' anatomy incl. app URL schemes + 24 h cache gate, full anikototv→megaplay chain (RC4 vrf, AES key/IV, `s=` CDN param, PNG-wrap 252-byte strip, Kiwi gone), sandbox paths |
| `TROUBLESHOOTING.md` | Playbooks: §1 "extension broke" 10-stage probe table with historical outcomes; §2 in-app download/stale version (never-delete-APKs rule); §3 website 404s/stale (rebuild window, cache-bust); §4 icon (real launcher icon `b14f03…`); §5 search/metadata; §6 CI. Current open items: Kiwi streaming gone; CDN rotation may recur (v16.11 loop self-corrects); tools awaiting activation. |
| `TOOLS.md` | Full research on installed versions (below) + activation plan |
| `SECRETS.md` | PAT1, PAT2, ntfy topic — sandbox-local only |

## Delivered 2 — Tools installed (isolated venv, verified)

- Isolated venv `/home/z/scrape-venv` (main uv-managed `/home/z/.venv` deliberately untouched).
- `scrapling 0.4.15` + `[fetchers]` extra (`patchright 1.62.3`, `playwright 1.62.0`,
  `curl_cffi 0.16.3`, `browserforge 1.2.4`); `scrapegraphai 2.2.4` (+ `scrapegraph-py 2.3.1`).
- ⚠️ `scrapling install` **always fails here** (`playwright install-deps` needs apt/sudo) —
  engines installed directly instead: `python -m playwright install chromium` +
  `python -m patchright install chromium` (both exit 0). This bypass is documented in TOOLS.md.
- Smoke tests passed: `Fetcher.get('https://example.com')` → 200;
  `StealthyFetcher.fetch(...)` → 200 via real patchright-chromium launch.

## Research highlights (introspected on the installed versions, not from tutorials)

- **scrapling 0.4.15 API changed vs older docs**: no `css_first`; `css()` returns a list;
  fetchers usable as classmethods. Adaptive/healing surface confirmed present:
  `find_similar`, `find_by_text/_regex`, `generate_css_selector` /
  `generate_full_xpath_selector`, `relocate`/`retrieve`, `captured_xhr` (browser fetchers),
  `markdown` (HTML→md summary). Maps to selector healing when anikototv HTML changes.
- **scrapegraphai 2.2.4**: 26 graph classes inventoried (`SmartScraperGraph`,
  `SmartScraperMultiGraph`, `ScriptCreatorGraph`, `JSONScraperGraph`, …). Requires an
  OpenAI-compatible LLM endpoint at runtime (`graph_config["llm"]`) — **no key provisioned
  yet**; that's the user's "next" step. Maps to JSON/HTML schema mapping after API changes.
- Rules of engagement written: tools assist diagnosis only; shipped Kotlin must still be
  verified against raw requests (v16.11 TLS-gating lesson); rate-limit politely.

## Stage Summary

- ★ Complete turnkey workflow now lives in `/home/z/anikoto-workflow/` — future sessions start
  at `README.md` → `WORKFLOW.md` Phase 1.
- ★ Scraping toolchain ready and verified in `/home/z/scrape-venv`; **not used** on the site
  (per instruction). Next session: user picks LLM endpoint/key, then integrate per TOOLS.md §4.
- ★ No extension code, releases, or dist-repo files touched this session.
