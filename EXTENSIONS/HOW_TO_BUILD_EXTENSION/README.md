# HOW_TO_BUILD_EXTENSION — Master Guide

> **This is the step-by-step procedure for building a new Aniyomi extension from scratch.** It is
> GENERAL (not site-specific) because every site is different — one rigid recipe does not fit all.
> Instead, this guide gives you a **disciplined workflow**: analyze → implement → verify, one layer
> at a time, asking the user when you're unsure.
>
> **Read this file first.** Then follow the step files (01-05) in order. Use the
> [AniKoto reference](reference-prior-solutions.md) and [common pitfalls](common-pitfalls.md)
> as lookup tables when you hit a problem.

---

## 0. The philosophy (read this before starting)

These come from [`MEMORY/PROJECT_RULES.md`](../../MEMORY/PROJECT_RULES.md) — 51 sessions of hard-won
lessons. The four most important:

1. **Verify before trusting.** Don't trust the user, the reference APK, or a single API response.
   Use a real browser (`agent-browser`) and test ALL servers from ALL endpoints. Curl alone misses
   things — load the page, capture network requests, see what ACTUALLY happens.
2. **One change at a time.** No bundled multi-fix changes. Verify each change before the next build.
   (Bundled fixes caused regressions in early sessions.)
3. **Don't force anything.** If you can't handle something properly, say so. Revert rather than
   break. Ask the user rather than guess.
4. **Document everything.** Research, guides, decisions, issues, resolutions, session logs. Future
   sessions (and future extensions) depend on what you write down now.

➡️ Full rules: [`../../MEMORY/PROJECT_RULES.md`](../../MEMORY/PROJECT_RULES.md)
➡️ When to ask the user: [`00-philosophy-and-rules.md`](00-philosophy-and-rules.md) §when-to-ask

---

## 1. The 5-step workflow (MUST be done in order)

Each step builds on the previous one. **Do not skip ahead.** A step is "done" only when its
verification passes — otherwise you're building on sand.

| Step | What | Detail file | Done when |
|---|---|---|---|
| **1** | **Analyze the website** | [`01-analyze-the-website.md`](01-analyze-the-website.md) | Site analysis doc in `MEMORY/sites/` is complete + browser-verified; identity fields (name, package, extClass, domain) are confirmed. |
| **2** | **Catalog: popular, latest, search, filters** | [`02-catalog-popular-latest-search-filters.md`](02-catalog-popular-latest-search-filters.md) | A debug APK loads popular + latest + search (with filters) + details page. Cover images render. |
| **3** **Details + episodes** | [`03-details-and-episodes.md`](03-details-and-episodes.md) | Details page shows full anime info; episode list loads; sub/dub shown via scanlator; EpisodeMeta encoding decided. |
| **4** | **Playback + video extraction** | [`04-playback-and-video-extraction.md`](04-playback-and-video-extraction.md) | EVERY server from EVERY endpoint resolves to a playable video. WAF-blocked CDNs handled. Local proxy working if needed. |
| **5** | **Build, test, release** | [`05-build-test-and-release.md`](05-build-test-and-release.md) | Release APK signed, R8-clean, passes the build checklist. Registered in `MEMORY/EXTENSIONS.md`. |

> **Why this order?** Each layer depends on the one below it. You can't implement search without
> knowing the URL structure (Step 1). You can't implement episodes without a working details page
> (Step 3 needs Step 2). You can't extract video without an episode list (Step 4 needs Step 3).
> Skipping ahead = rework.

---

## 2. The iterative loop (within each step)

Every step follows the same inner loop:

```
   ┌──────────────────────────────────────────────────────┐
   │  1. OBSERVE  — load the site in agent-browser,        │
   │     capture network requests, read HTML/JSON          │
   │  2. HYPOTHESIZE — "I think endpoint X returns Y"      │
   │  3. TEST — verify the hypothesis (curl + browser)     │
   │  4. IMPLEMENT — write the Kotlin code                 │
   │  5. BUILD + TEST — assembleDebug, install, click it   │
   │  6. VERIFY — does it actually work? (rule §1)         │
   │     ├─ YES → promote notes, write a session log,      │
   │     │         move to the next sub-task               │
   │     └─ NO  → debug (logcat), revert if needed,        │
   │             re-observe, ask the user if stuck         │
   └──────────────────────────────────────────────────────┘
```

**Never implement more than one thing before building + testing.** One change at a time (rule §2).

---

## 2b. After Steps 1-3: add features from `FEATURES/`

Once the core is working (popular + search + filters + details + episodes), you can add
**optional features** using the guides in [`FEATURES/`](FEATURES/README.md). Features are
composable — pick only the ones you want:

| Feature | Guide | What it adds |
|---|---|---|
| Episode metadata enrichment | [`FEATURES/episode-metadata-enrichment.md`](FEATURES/episode-metadata-enrichment.md) | Episode thumbnails + titles + descriptions (multi-source: Jikan + AniList + Anikage + Kitsu) |
| Multi-season episode renumbering | [`FEATURES/multi-season-episode-renumbering.md`](FEATURES/multi-season-episode-renumbering.md) | Renumbers episodes when a site continues numbering across seasons (Season 2 starts at ep 13 → renumbered to 1) |
| Video playback (Kwik HLS) | [`FEATURES/video-playback-kwik-hls.md`](FEATURES/video-playback-kwik-hls.md) | Extracts m3u8 HLS streams from Kwik embed links via packed-JS unpacking |

Each feature guide is self-contained: tells you which files to create, what code to write, how to
wire it in, and how to verify it. **Follow the conventions exactly** (wording, formats) so all
extensions are consistent.

When you implement a NEW feature not in the index, **write a guide for it** in `FEATURES/` so
future extensions can reuse the pattern. See [`FEATURES/README.md`](FEATURES/README.md) §how-to-write-a-new-feature-guide.

---

## 3. When to ask the user (don't guess these)

Ask the user — don't guess — when:

- **The site's current live domain is ambiguous** (animepahe has rotated .com/.org/.ru/.app). Confirm with a browser; if still unclear, ask.
- **A feature is genuinely ambiguous on the site** (e.g. is "HSUB" labeled as "Sub" or "Hardsub"?). Verify first; if the site is inconsistent, ask the user how they want it labeled.
- **An endpoint returns errors or unexpected shapes** that you can't resolve by testing alternatives.
- **You're about to change something irreversible** (the `versionId`, the package name, the extClass) — these affect saved-anime / update compatibility. Confirm with the user first.
- **A video server is WAF-blocked and WebView can't get past it** — tell the user; don't silently drop the server.
- **You're unsure whether a behavior is a bug or intentional** (e.g. duplicate videos across audio types). Test, document, then ask.
- **You hit a fork-compatibility question** (legacy vs new pipeline). Reference AniKoto's solution; if the situation differs, ask.

➡️ Details + scripts for asking well: [`00-philosophy-and-rules.md`](00-philosophy-and-rules.md) §when-to-ask

---

## 4. Reference resources (use these constantly)

| Resource | What it's for | Path |
|---|---|---|
| **★ Feature implementation guides** | How to implement specific FEATURES (metadata enrichment, smart search, etc.) — reusable across extensions | [`FEATURES/README.md`](FEATURES/README.md) |
| **★ Prior solutions lookup** | "How did prior extensions solve problem X?" — grow this as you discover new patterns | [`reference-prior-solutions.md`](reference-prior-solutions.md) |
| **Common pitfalls** | Known gotchas from 51 sessions (extClass, R8, versionId, Video ctor, ...) | [`common-pitfalls.md`](common-pitfalls.md) |
| **Decision log template** | Record THIS extension's architecture decisions (ADR-style) | [`decision-log-template.md`](decision-log-template.md) |
| **AniKoto's full module docs** | Deep reference: how AniKoto's catalog / video / metadata / settings work | `../anikoto/MEMORY/modules/00-06` |
| **AniKoto's site analysis** | Example of a complete, browser-verified site analysis | `../anikoto/MEMORY/sites/` |
| **AniKoto's issues-resolutions** | 4 resolved issues with symptom→cause→fix→verification | `../anikoto/MEMORY/issues-resolutions/` |
| **ext-lib 16 API reference** | The compile-time API you build against | `../../MEMORY/ext-lib/02-ext-lib-16-api-reference.md` |
| **Key ext-lib extractors** | Reusable `lib/` building blocks (playlistutils, cryptoaes, m3u8server, ...) | `../../MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md` |
| **How Aniyomi works** | The app's video pipeline, network layer, source API | `../../MEMORY/research/01-aniyomi-video-pipeline-and-player.md`, `04-network-layer-and-interceptors.md` |
| **Build checklist** | ★ MANDATORY pre/post-build checklist | `../../MEMORY/guides/04-build-checklist.md` |
| **How to create an extension** | Per-extension file layout + source skeleton | `../../MEMORY/guides/02-how-to-create-a-new-extension.md` |
| **Reference extensions** | yuzono/anime-extensions examples (read-only) | `../../SHARED/REFERENCE_HUB/anime-extensions-ref/src/<lang>/` |

---

## 5. This guide is MODIFIABLE — grow it as you learn

This guide is a **living document**. As you build extensions, you WILL discover:

- New site patterns not covered here → add a section to the relevant step file.
- New pitfalls → add to [`common-pitfalls.md`](common-pitfalls.md).
- New solutions to problems → add a row to [`reference-prior-solutions.md`](reference-prior-solutions.md) (covers AniKoto + AnimePahe + MKissa + future extensions).
- A step that should be split or reordered → edit the step file + log the change in a decision ADR.

**Rule:** when you change this guide, log WHY in the extension's `MEMORY/decisions/` (or the
project-level `MEMORY/decisions/`) so future sessions understand the revision. Never silently
change the process (project rule §3).

---

## 6. Starting a new extension (checklist)

Before Step 1, set up the extension folder:

- [ ] `cp -r EXTENSIONS/_template EXTENSIONS/<name>` (lowercase, no hyphens — package-name convention)
- [ ] Fill in `EXTENSIONS/<name>/EXTENSION.md` identity (mark unverified fields **[ANALYSIS]**)
- [ ] Write `EXTENSIONS/<name>/MEMORY/README.md` (model on AniKoto's)
- [ ] Add a row to [`../../MEMORY/EXTENSIONS.md`](../../MEMORY/EXTENSIONS.md) (status: 🚧 setup)
- [ ] Read this guide end-to-end
- [ ] Read [`../../MEMORY/PROJECT_RULES.md`](../../MEMORY/PROJECT_RULES.md)
- [ ] Read [`../../MEMORY/guides/04-build-checklist.md`](../../MEMORY/guides/04-build-checklist.md)
- [ ] Begin Step 1: [`01-analyze-the-website.md`](01-analyze-the-website.md)

---

## 7. File index

```
HOW_TO_BUILD_EXTENSION/
├── README.md                                    ← THIS FILE — the master guide
├── 00-philosophy-and-rules.md                   ← the principles + when to ask the user
├── 01-analyze-the-website.md                    ← Step 1: site analysis (verify with browser)
├── 02-catalog-popular-latest-search-filters.md  ← Step 2: popular/latest/search/filters
├── 03-details-and-episodes.md                   ← Step 3: details + episode list
├── 04-playback-and-video-extraction.md          ← Step 4: video pipeline
├── 05-build-test-and-release.md                 ← Step 5: build + signing + release
├── FEATURES/                                    ← ★ reusable feature implementation guides
│   ├── README.md                                ← index of feature guides + how to write new ones
│   └── episode-metadata-enrichment.md           ← thumbnails + descriptions + titles (multi-source)
├── reference-prior-solutions.md               ← ★ how prior extensions solved problems (GROWABLE)
├── common-pitfalls.md                           ← known gotchas (GROWABLE)
└── decision-log-template.md                     ← ADR template for per-extension decisions
```
