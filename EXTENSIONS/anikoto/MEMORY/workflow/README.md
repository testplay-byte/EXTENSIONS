# WORKFLOW — The Living Extension-Build Guide

> **Status: LIVING DOCUMENT.** This is a continuous-learning guide that tracks how the initial test
> extension is built, to streamline the creation of all future extensions. Because websites vary in
> structure, this workflow remains **flexible and adaptive** — not rigid.
>
> The scaffolding + templates are in place now. The actual content gets filled in **as we build the
> first extension**, after which it serves as the reusable playbook for all future extensions.

---

## 1. The 7 steps

| Step | Folder | Purpose |
|---|---|---|
| 1 | `01_WEBSITE_RESEARCH/` | Site analysis: URL structure, API endpoints, streaming servers, video qualities, audio versions (SUB/HSUB/DUB). Historical reference + onboarding for new sites. |
| 2 | `02_ARCHITECTURE_DESIGN/` | Blueprints, scaffolding config, resource/dependency planning. Verify DEV-folder env dependencies are aligned. |
| 3 | `03_CATALOG_EPISODES_MANAGEMENT/` | Popular/Latest/Search/Filters UI, Anime Details, episode lists, metadata, explicit audio-track markers (Sub/Dub/H-Sub). |
| 4 | `04_VIDEO_EXTRACTION_PLAYBACK/` | Hoster pipeline + player comms. **Local Python prototyping** to isolate HTTP responses / dissect payloads before app integration. Runtime server/quality/audio/subtitle switching. |
| 5 | `05_PREFERENCES/` | Global settings: Preferred Server, Preferred Audio Language, Preferred Video Quality, Preferred Title Language. |
| 6 | `06_BUILD_TEST/` | Debug APKs with comprehensive logging to `Download/1118000/` (raw site responses, parsing steps, player state). Iterative debugging. Learnings retroactively integrated into earlier steps. |
| 7 | `07_FINAL_RELEASE/` | Transition from debug → production-ready deployment packages (signing, versioning, stable build). |

Each folder has its own `README.md` defining: purpose, what artifacts belong there, MEMORY
cross-references, and fill-in templates for when we build.

---

## 2. How this connects to MEMORY

`MEMORY/` is the **verified knowledge base** (theory: ext-lib 16 API, how the player works, build
setup, site analyses). `WORKFLOW/` is the **practice** (how to apply that theory to build an
extension, step by step). Each WORKFLOW step README cross-references the relevant MEMORY docs so you
don't duplicate research.

| WORKFLOW step | Key MEMORY references |
|---|---|
| 01 Website Research | `EXTENSIONS/anikoto/MEMORY/sites/` (AniKoto site analyses), `MEMORY/research/04-network-layer-and-interceptors.md`, project rule §1 (verify with agent-browser), §7 (audio types) |
| 02 Architecture Design | `MEMORY/guides/01-build-setup-for-ext-lib-16.md`, `MEMORY/research/02-reference-extension-build-and-structure.md`, `MEMORY/ext-lib/01-...source-and-versioning.md` |
| 03 Catalog & Episodes | `MEMORY/ext-lib/02-...api-reference.md` (AnimeHttpSource, SAnime, SEpisode), project rule §8 (scanlator for sub/dub) |
| 04 Video Extraction | `MEMORY/research/01-aniyomi-video-pipeline-and-player.md`, `MEMORY/ext-lib/03-key-lib-extractors-and-helpers.md`, `MEMORY/research/05-keiyoushi-utils-core.md` (coroutines, parseAs) |
| 05 Preferences | `MEMORY/research/05-keiyoushi-utils-core.md` §6 (Preferences API) |
| 06 Build & Test | `MEMORY/guides/01-build-setup-for-ext-lib-16.md` (build commands), project rule §6 (logging to Download/1118000/) |
| 07 Final Release | `MEMORY/guides/01-...md` (release build, signing), `MEMORY/research/02-...md` (versioning scheme) |

---

## 3. The "living guide" principle

This workflow is built **by doing**. The process:

1. When we build the **first extension**, we fill in each step's templates with what we actually did
   (the real site analysis, the real scaffolding commands, the real extractor choices, the real
   bugs + fixes).
2. What we learn that's **generalizable** (applies to any extension) gets promoted to `MEMORY/`
   (research, guides, decisions).
3. What's **site-specific** stays in `EXTENSIONS/anikoto/MEMORY/workflow/<step>/` and `EXTENSIONS/anikoto/MEMORY/sites/`.
4. When we build the **second extension**, we follow this workflow as a playbook, updating any step
   where the new site reveals a gap or better approach.
5. **Learnings or unmapped edge cases discovered during user testing are retroactively integrated
   into earlier workflow stages** (per the user's spec) — the workflow evolves backward as well as
   forward.

Per project rule §3: "The workflow is modifiable. If you discover a gap at a later stage that should
have been handled earlier, add it to the workflow and log the revision." Revisions are logged in
`MEMORY/decisions/`.

---

## 4. Naming convention (mandatory)

- **Folders:** STRICT UPPER_CASE_WITH_UNDERSCORES. No spaces, no lowercase. Examples:
  `01_WEBSITE_RESEARCH`, `ANIMIX`, `DEV`.
- **Files:** lowercase-kebab-case for content files (e.g. `site-analysis.md`), UPPER_CASE for
  convention markers (`.gitkeep`). `README.md` stays as-is (universal convention).
- **Extension names:** UPPER_CASE_WITH_UNDERSCORES for the folder (e.g. `ANIMIX`, `ANIMEPAHE`),
  matching the site's common name. The package name inside uses lowercase
  (`eu.kanade.tachiyomi.animeextension.en.animepahe`).

---

## 5. When to write here vs. in MEMORY

| Write to WORKFLOW/ when… | Write to MEMORY/ when… |
|---|---|
| It's about the **process** of building (how to do a step). | It's **verified knowledge** that applies to any extension (API facts, how the app works). |
| It's a **template** to fill per-extension. | It's a **decision** with rationale (ADR). |
| It's **site-specific** build notes for the current extension. | It's a **site analysis** (goes to `MEMORY/sites/<sitename>/`). |
| It's the **living record** of how the first extension was built. | It's a **session log** or a **resolved issue**. |

When in doubt: WORKFLOW = "how to build" (process, templates, per-extension); MEMORY = "what's true"
(verified facts, decisions, history).

---

## 6. Current status

- ✅ All 7 step folders + READMEs created (templates, not yet filled).
- ⏳ First extension not started — no site chosen yet.
- ⏳ Android SDK not installed (pending user direction).

When the user picks the first target site, we start at `01_WEBSITE_RESEARCH/` and work through the
steps in order, filling templates as we go.
