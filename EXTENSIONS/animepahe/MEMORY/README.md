# AnimePahe — Knowledge Base

> **This is AnimePahe's own knowledge base.** Everything learned, decided, and built for THIS
> extension lives here. Project-wide knowledge (rules, build guides, ext-lib API, general
> Aniyomi research) lives in the project-level `/home/z/my-project/MEMORY/`.

---

## Status: 🚧 SETUP (pre-analysis)

This extension is at the pre-analysis stage. Follow the build guide:
[`../../HOW_TO_BUILD_EXTENSION/README.md`](../../HOW_TO_BUILD_EXTENSION/README.md)

## Folder map

| Folder | Purpose |
|---|---|
| **`session-logs/`** | One log per working session (THIS extension only). **Always read the most recent 1-2 before resuming.** Always write one at the end. |
| **`sites/`** | Deep analysis of the animepahe site: URL structure, endpoints, server list paths, audio types, CDN/WAF behavior, tokens & dedup. **Fill in Step 1.** |
| **`issues-resolutions/`** | Resolved issues (symptom → root cause → fix → verification). Read to avoid repeating mistakes. |
| **`modules/`** | Code module docs (architecture, catalog, details/episodes, video pipeline, settings). **Fill as code is written.** |
| **`research/`** | Extension-specific research findings. |
| **`workflow/`** | Numbered research workflow (01-07) — per-extension living build guide. |
| **`TEMPORARY_MEMORY/`** | Raw, unverified notes. Promote (move, don't copy) to a mature folder when verified. |

## Quick links

- **Extension quick-ref**: `../EXTENSION.md` — identity, build commands, current status, key files
- **★ Build guide**: `../../HOW_TO_BUILD_EXTENSION/README.md` — the multi-step procedure for building this extension
- **★ AniKoto reference**: `../../HOW_TO_BUILD_EXTENSION/reference-anikoto-solutions.md` — how AniKoto solved specific problems (lookup table)
- **Common pitfalls**: `../../HOW_TO_BUILD_EXTENSION/common-pitfalls.md` — known gotchas from 51 sessions
- **Project rules**: `/home/z/my-project/MEMORY/PROJECT_RULES.md`
- **Build checklist** (project-level): `/home/z/my-project/MEMORY/guides/04-build-checklist.md` ★ mandatory

## How to start a new session on AnimePahe

1. Read `../EXTENSION.md` (this extension's quick-ref).
2. Read the build guide: `../../HOW_TO_BUILD_EXTENSION/README.md` — confirm which step you're on.
3. Read the 1-2 most recent `session-logs/` (if any exist).
4. Scan `TEMPORARY_MEMORY/` for open threads.
5. If resuming mid-step, read the relevant step file in `../../HOW_TO_BUILD_EXTENSION/0X-*.md`.
6. Read the project-level `MEMORY/guides/04-build-checklist.md` before building.
7. Do the work; write raw notes to `TEMPORARY_MEMORY/`; promote when verified.
8. Write a session log in `session-logs/` before ending.
