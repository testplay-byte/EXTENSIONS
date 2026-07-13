# AniKoto 180 — Knowledge Base

> **This is AniKoto's own knowledge base.** Everything learned, decided, and built for THIS
> extension lives here. Project-wide knowledge (rules, build guides, ext-lib API, general
> Aniyomi research) lives in the project-level `/home/z/my-project/MEMORY/`.

---

## Folder map

| Folder | Purpose |
|---|---|
| **`session-logs/`** | One log per working session (sessions 01-51). **Always read the most recent 1-2 before resuming.** Always write one at the end. |
| **`sites/`** | Deep analysis of anikototv.to: URL structure, endpoints, server list paths, audio types (SUB/HSUB/DUB), CDN/WAF behavior, tokens & dedup, PNG wrapping. |
| **`issues-resolutions/`** | Resolved issues (symptom → root cause → fix → verification). 4 issues documented. Read to avoid repeating mistakes. |
| **`modules/`** | 7 module docs (00-06): architecture map, catalog/search, details/episodes, video pipeline, episode metadata, settings, smart search. **★ The best place to understand how the code fits together.** |
| **`research/`** | AniKoto-specific research: reference-APK cross-check analysis, episode-metadata implementation plans. |
| **`workflow/`** | Numbered research workflow (01-07) — the living build guide built alongside the extension. |
| **`TEMPORARY_MEMORY/`** | Raw, unverified notes. Promote (move, don't copy) to a mature folder when verified. |

## Quick links

- **Extension quick-ref**: `../EXTENSION.md` — identity, build commands, current status, key files
- **APK info sheet**: `../APK_INFO.md`
- **Latest session**: `session-logs/2027-06-27_session-51_filter-fixes-and-performance-optimizations.md`
- **Architecture overview**: `modules/00-architecture.md`
- **Build checklist** (project-level): `/home/z/my-project/MEMORY/guides/04-build-checklist.md` ★ mandatory
- **Fork-compat fix**: `issues-resolutions/04-episode-url-dns-error-in-forks.md`

## How to start a new session on AniKoto

1. Read `../EXTENSION.md` (this extension's quick-ref).
2. Read the 1-2 most recent `session-logs/`.
3. Scan `TEMPORARY_MEMORY/` for open threads.
4. Read the relevant `modules/` doc for the area you're touching.
5. Read the project-level `MEMORY/guides/04-build-checklist.md` before building.
6. Do the work; write raw notes to `TEMPORARY_MEMORY/`; promote when verified.
7. Write a session log in `session-logs/` before ending.
