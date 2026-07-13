# MEMORY — Project Knowledge Base

> **This is the project-level knowledge base — shared across ALL extensions.** It holds the rules,
> build guides, ext-lib API reference, and general Aniyomi research that apply to every extension.
> Per-extension knowledge (session logs, site analysis, issues, module docs) lives inside each
> extension's own `MEMORY/` at `EXTENSIONS/<name>/MEMORY/`.
>
> **Starting a new session? Read this file first**, then follow the navigation map below.

---

## 0. What this project is

We build **Aniyomi anime extensions** (ext-lib 16 era) from our own understanding and research.
Aniyomi is an anime/manga tracker & player app for Android (forked from Mihon/Tachiyomi).
Extensions are small Android modules (APKs) that Aniyomi loads to scrape a specific anime site:
search, details, episode lists, and video extraction.

**We build and manage MULTIPLE extensions.** Each one is fully self-contained in
`EXTENSIONS/<name>/` (Gradle project, keystore, APKs, its own knowledge base). An issue in one
extension never affects another.

- **Our extensions:** `EXTENSIONS/` — see [`EXTENSIONS.md`](EXTENSIONS.md) for the registry
- **App repo (reference, read-only):** `SHARED/REFERENCE_HUB/aniyomi-app` — cloned from `aniyomiorg/aniyomi`
- **Extension repo (reference, read-only):** `SHARED/REFERENCE_HUB/anime-extensions-ref` — cloned from `yuzono/anime-extensions`
- **Reference APKs (cross-check only, never copy):** `SHARED/APK_REFERENCE/`

The reference APKs are a **cross-check only, never a copy source.** We build from understanding.

---

## 1. Golden rules (read [`PROJECT_RULES.md`](PROJECT_RULES.md) for the full list)

The four most important principles:

1. **Verify before trusting** — don't trust the user, the reference APK, or a single API response.
   Use a real browser (agent-browser) and test ALL servers from ALL endpoints.
2. **One change at a time** — no bundled multi-fix changes. Verify each change before the next build.
3. **Don't force anything** — if you can't handle something properly, say so. Revert rather than break.
4. **Document everything** — research, guides, decisions, issues, resolutions, session logs.

➡️ Full rules live in **[`PROJECT_RULES.md`](PROJECT_RULES.md)** (do not duplicate them here).

---

## 2. Project structure (multi-extension)

```
/home/z/my-project/
├── EXTENSIONS/                  ← per-extension workspaces (one folder per extension)
│   ├── _template/               ← copy to start a new extension
│   └── anikoto/                 ← AniKoto 180 (DEV/ + APK/ + ANALYSIS/ + MEMORY/ + EXTENSION.md)
│       └── MEMORY/              ← AniKoto's own knowledge base (session-logs, sites, issues, modules, research, workflow)
├── MEMORY/                      ← ★ project-level knowledge base (THIS folder — shared across all extensions)
│   ├── README.md                (this file)
│   ├── PROJECT_RULES.md         (non-negotiable rules)
│   ├── EXTENSIONS.md            (registry of all extensions)
│   ├── guides/                  (general build guides — shared)
│   ├── decisions/               (project-level ADRs — shared)
│   ├── ext-lib/                 (ext-lib 16 API reference — shared)
│   ├── research/                (general Aniyomi research — shared)
│   └── build-env/               (JDK + Android SDK install state)
├── SHARED/                      ← cross-extension binary/reference resources
│   ├── REFERENCE_HUB/           (cloned reference repos — read-only)
│   └── APK_REFERENCE/           (reference APKs — cross-check only)
├── src/  public/                ← Next.js download webpage
├── JDK/  ANDROID_SDK/          ← build toolchain (shared across all extensions)
├── .android-env.sh              ← source before every build
├── worklog.md                   ← shared worklog (all sessions, all extensions)
├── PROJECT_INDEX.md  STARTUP_PROMPT.md  RESTORE.md
└── (sandbox: Caddyfile, .zscripts/, package.json, prisma/, etc.)
```

**Key principle:** project-wide knowledge is HERE (in this `MEMORY/`); extension-specific knowledge
is in `EXTENSIONS/<name>/MEMORY/`. When resuming an extension, read its `EXTENSION.md` and
`MEMORY/session-logs/` first, then come here for shared guides/rules.

---

## 3. The two-tier memory model (MANDATORY workflow)

Nothing goes directly into mature folders. Everything starts in `TEMPORARY_MEMORY` and is
**promoted** (moved, not copied) only after it is verified.

```
        RAW / UNVERIFIED                          VERIFIED / MATURE
   ┌─────────────────────────┐            ┌─────────────────────────────────┐
   │  TEMPORARY_MEMORY/      │   verify    │  research/  guides/  decisions/ │
   │  (drafts, hypotheses,   │ ──────────► │  issues-resolutions/            │
   │   raw notes, issues     │   + move    │  session-logs/  ext-lib/        │
   │   under investigation)  │   (delete   │  modules/  sites/               │
   └─────────────────────────┘    temp)    └─────────────────────────────────┘
```

**Note:** `TEMPORARY_MEMORY/` and the mature extension-specific folders (`session-logs/`, `sites/`,
`issues-resolutions/`, `modules/`, `research/`) live **inside each extension's `MEMORY/`**, not here.
This project-level `MEMORY/` only holds shared mature folders (`guides/`, `decisions/`, `ext-lib/`,
`research/`).

### Promotion rules
- **Promote by MOVING, never copying.** When a temp note is verified, move its file to the
  appropriate mature folder and **delete** the temp file.
- A note is "verified" when confirmed against the live site / source code / a successful build.
- **Unresolved issues stay in `TEMPORARY_MEMORY/`** until resolved and verified. Only then are they
  promoted (key finding + resolution) to `issues-resolutions/`.

### Naming convention
- Files: `YYYY-MM-DD_short-kebab-case-title.md`
- Session logs: `YYYY-MM-DD_session-NN_short-title.md`
- One topic per file. Keep files focused and linkable.

---

## 4. Folder map & navigation

### This project-level `MEMORY/`

| Folder | Purpose | When to read / write |
|---|---|---|
| **`PROJECT_RULES.md`** | All project-wide rules. | Read at session start. |
| **`EXTENSIONS.md`** | ★ Registry of all extensions + status. | Read to see what exists. Update when adding/retiring an extension. |
| **`guides/`** | Verified how-to guides: create an extension, build, debug, SDK install. | Read when doing a task. |
| **`decisions/`** | Architecture & design decisions with rationale (ADR-style). | Read to understand *why*. Write when committing to an approach. |
| **`ext-lib/`** | ext-lib 16 specifics: extractors, helpers, multisrc themes, APIs, version notes. | Read when building extension logic. |
| **`research/`** | Verified general research: how Aniyomi works, source-API, video player, ext-lib features, network layer. (Extension-specific research lives in `EXTENSIONS/<name>/MEMORY/research/`.) | Read to understand. Write after verifying. |
| **`build-env/`** | Current build toolchain state (JDK + Android SDK locations, env script). | Read when setting up a new environment. |

### Per-extension `EXTENSIONS/<name>/MEMORY/`

| Folder | Purpose |
|---|---|
| **`session-logs/`** | One log per working session for THIS extension. **Always read the most recent 1-2 before resuming.** |
| **`sites/`** | Deep analysis of THIS extension's target site (URLs, servers, audio types, CDN/WAF). |
| **`issues-resolutions/`** | Resolved issues for THIS extension (symptom → cause → fix → verification). |
| **`modules/`** | Code module docs for THIS extension (architecture, catalog, video pipeline, etc.). |
| **`research/`** | Extension-specific research findings. |
| **`workflow/`** | Numbered research workflow (01-07) built alongside the extension. |
| **`TEMPORARY_MEMORY/`** | Raw/unverified notes for THIS extension. |

### Outside MEMORY

| Path | Purpose |
|---|---|
| `EXTENSIONS/<name>/DEV/` | The Gradle project for an extension (source, stubs, build config, keystore). |
| `EXTENSIONS/<name>/APK/` | Built APKs for an extension (debug + release). |
| `EXTENSIONS/<name>/EXTENSION.md` | ★ Per-extension quick-ref (identity, build, status) — read first when resuming. |
| `SHARED/REFERENCE_HUB/` | Cloned reference repos (read-only). Key: `aniyomi-app/` (app source), `anime-extensions-ref/` (extension examples + `lib/`). |
| `SHARED/APK_REFERENCE/` | Reference APKs for cross-check only. **Never copy from these.** |
| `JDK/` + `ANDROID_SDK/` + `.android-env.sh` | Build toolchain (shared). Source `.android-env.sh` before every build. |

---

## 5. How to start a new session (checklist)

1. **Read this `README.md`** (you are here) + [`PROJECT_RULES.md`](PROJECT_RULES.md).
2. **Check [`EXTENSIONS.md`](EXTENSIONS.md)** — see which extension you're working on and its status.
3. **Read the extension's `EXTENSION.md`** at `EXTENSIONS/<name>/EXTENSION.md` — identity, build, status.
4. **Read the 1-2 most recent session logs** at `EXTENSIONS/<name>/MEMORY/session-logs/`.
5. **Scan `EXTENSIONS/<name>/MEMORY/TEMPORARY_MEMORY/`** for open threads.
6. **Check the relevant module doc** in `EXTENSIONS/<name>/MEMORY/modules/` for the area you're touching.
7. **Read the relevant shared guide** here (e.g. `guides/04-build-checklist.md` before building).
8. **Do the work**, writing raw notes to the extension's `TEMPORARY_MEMORY/` as you go.
9. **Promote** verified findings to the right mature folder (move, don't copy).
10. **Write a session log** in `EXTENSIONS/<name>/MEMORY/session-logs/` before ending (mandatory).

---

## 6. How to add a new extension

1. `cp -r EXTENSIONS/_template EXTENSIONS/<new-name>`
2. Read [`guides/02-how-to-create-a-new-extension.md`](guides/02-how-to-create-a-new-extension.md)
3. Fill in `EXTENSIONS/<new-name>/EXTENSION.md` (identity: name, package, extClass, site)
4. Scaffold the Gradle project in `EXTENSIONS/<new-name>/DEV/` (follow guide 02)
5. **Do site analysis FIRST** in `EXTENSIONS/<new-name>/MEMORY/sites/` — verify with a real browser (rule §1)
6. **Add a row to [`EXTENSIONS.md`](EXTENSIONS.md)**
7. Begin session logs in `EXTENSIONS/<new-name>/MEMORY/session-logs/`

---

## 7. Quick links

**Start here:**
- Project rules: [`PROJECT_RULES.md`](PROJECT_RULES.md)
- Extensions registry: [`EXTENSIONS.md`](EXTENSIONS.md)
- Build env state: [`build-env/README.md`](build-env/README.md)

**AniKoto 180 (current active extension):**
- Quick-ref: [`../EXTENSIONS/anikoto/EXTENSION.md`](../EXTENSIONS/anikoto/EXTENSION.md)
- Latest session: `../EXTENSIONS/anikoto/MEMORY/session-logs/2027-06-27_session-51_filter-fixes-and-performance-optimizations.md`
- Architecture: `../EXTENSIONS/anikoto/MEMORY/modules/00-architecture.md`
- Build checklist (shared): [`guides/04-build-checklist.md`](guides/04-build-checklist.md) ★ mandatory

**ext-lib 16 (the video-pipeline API we build on):**
- [`ext-lib/01-ext-lib-16-source-and-versioning.md`](ext-lib/01-ext-lib-16-source-and-versioning.md) — which repo/tag, how to depend on `v16`
- [`ext-lib/02-ext-lib-16-api-reference.md`](ext-lib/02-ext-lib-16-api-reference.md) — authoritative compile-time API reference
- [`ext-lib/03-key-lib-extractors-and-helpers.md`](ext-lib/03-key-lib-extractors-and-helpers.md) — reusable `lib/` building blocks

**How the Aniyomi app works (general research):**
- [`research/01-aniyomi-video-pipeline-and-player.md`](research/01-aniyomi-video-pipeline-and-player.md) — mpv player, Video/Hoster consumption
- [`research/02-reference-extension-build-and-structure.md`](research/02-reference-extension-build-and-structure.md) — yuzono build system
- [`research/04-network-layer-and-interceptors.md`](research/04-network-layer-and-interceptors.md) — NetworkHelper, interceptors
- [`research/05-keiyoushi-utils-core.md`](research/05-keiyoushi-utils-core.md) — standard toolkit

**How-to guides:**
- [`guides/01-build-setup-for-ext-lib-16.md`](guides/01-build-setup-for-ext-lib-16.md) — Gradle config for ext-lib 16
- [`guides/02-how-to-create-a-new-extension.md`](guides/02-how-to-create-a-new-extension.md) — per-extension layout + source skeleton
- [`guides/03-android-sdk-install.md`](guides/03-android-sdk-install.md) — verified SDK install procedure
- [`guides/04-build-checklist.md`](guides/04-build-checklist.md) — ★ mandatory pre/post-build checklist

**Decisions:**
- [`decisions/01-use-aniyomiorg-extensions-lib-v16.md`](decisions/01-use-aniyomiorg-extensions-lib-v16.md) — ADR: use official `aniyomiorg/extensions-lib:v16`
- [`decisions/02-workspace-folder-architecture.md`](decisions/02-workspace-folder-architecture.md) — ADR for the multi-extension layout
- [`decisions/03-best-method-to-build-extensions.md`](decisions/03-best-method-to-build-extensions.md) — ADR for the build method

---

## 8. How to write good memory files

- **Lead with a one-line summary** at the top: what this file is and its status (verified / draft).
- **Be specific and cite sources**: file paths in `SHARED/REFERENCE_HUB/`, URLs, commit hashes, API endpoints.
- **Distinguish fact from hypothesis.** Use "VERIFIED:" / "HYPOTHESIS:" / "TODO:" labels.
- **Keep it navigable**: use headings, bullet lists, and links to other memory files.
- **Update, don't duplicate.** If a fact changes, edit the existing file; don't write a parallel one.
- **Date every file.** Put the date in the filename and a `Last updated:` line in the body.

---

## 9. Workflow is modifiable

This workflow is a living document. If you discover a gap at a later stage that should have been
handled earlier, **add it to the workflow** and log the revision in `decisions/` with the reason and
date. Never silently change the process.
