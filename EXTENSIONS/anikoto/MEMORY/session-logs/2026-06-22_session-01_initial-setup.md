# Session 01 — Initial Setup & Scaffolding

> Date: 2026-06-22 · Session #: 01 · Duration: ~short · Timezone: America/Los_Angeles

## Goal

Set up the project foundation for building Aniyomi anime extensions (ext-lib 16 era):
clone the reference repos, create the two-tier MEMORY system with guide files, save the project
rules, and stage the reference APK — without analyzing anything yet.

## What was done

- Inspected environment: git available, 8 GB free disk, Next.js project present at
  `/home/z/my-project`, reference APK present at `upload/anikoto-by-1118000-v3.apk` (255,830 bytes,
  valid Android package with multiple `classes*.dex`).
- Created top-level folders: `REFERENCE_HUB/`, `MEMORY/` (with subfolders), `APK/`.
- **Cloned `aniyomiorg/aniyomi`** → `REFERENCE_HUB/aniyomi-app/` (full clone, 111 MB, 2015 files,
  no submodules). Contains the app: `source-api/`, `app/`, `core/`, `data/`, `domain/`, `i18n/`, etc.
  The `source-api/` holds the `eu.kanade.tachiyomi.animesource` / `source` interfaces extensions
  implement.
- **Cloned `yuzono/anime-extensions`** → `REFERENCE_HUB/anime-extensions-ref/` (full clone, 171 MB,
  2598 files, no submodules). Modern ext-lib structure observed:
  - `lib/` — 73 individual extractor/helper modules (e.g. `playlistutils`, `m3u8server`,
    `cryptoaes`, `lzstring`, `unpacker`, `cloudflareinterceptor`, `cookieinterceptor`, `randomua`,
    `i18n`, `universalextractor`, and host extractors like `filemoonextractor`,
    `streamtapeextractor`, `vidmolyextractor`, `voeextractor`, `mixdropextractor`, `doodextractor`).
  - `lib-multisrc/` — 10 theme templates (`anilist`, `animekaitheme`, `animestream`,
    `datalifeengine`, `dooplay`, `dopeflix`, `pelisplus`, `wcotheme`, `yflixtheme`, `zorotheme`).
  - `src/<lang>/` — 18 language folders (`all`, `ar`, `de`, `en`, `es`, `fr`, `hi`, `id`, `it`,
    `ko`, `pl`, `pt`, `ru`, `sr`, `tr`, `uk`, `zh`) with concrete extension examples.
  - `core/`, `common/`, `template/` (with `README-TEMPLATE.md`).
  - NOTE: the user flagged this repo as "old / not that good" — use as reference only, verify
    against latest ext-lib 16, and don't copy.
- Copied the reference APK into `APK/anikoto-by-1118000-v3.apk` (first copy was 0 bytes due to an
  interruption; re-copied successfully and verified zip/APK integrity). The small size (~250 KB,
  multiple dex) suggests this is a reference **extension** APK, not the full app. **Not analyzed
  yet** — staged for future cross-check only (rule §1: never copy from it).
- Built the **two-tier MEMORY system**:
  - `MEMORY/README.md` — main entry/navigation guide (the "new session starting guide"), including
    the temp→mature promotion workflow and folder map.
  - `MEMORY/PROJECT_RULES.md` — all 9 rule sections the user provided.
  - `MEMORY/TEMPORARY_MEMORY/README.md` — draft-zone rules + promotion workflow.
  - `MEMORY/research/`, `guides/`, `decisions/`, `issues-resolutions/`, `session-logs/`,
    `ext-lib/`, `extensions/`, `sites/` — each with a README defining purpose, what belongs,
    entry checklists, and naming conventions.
- Created this session log.

## Key findings / decisions

- Both reference repos use **no submodules**, so full clones are self-contained — good for an
  offline-readable reference hub.
- The reference extension repo already uses the **modern `lib/` + `lib-multisrc/` split** (ext-lib
  16 era layout), which is the structure we will follow.
- `REFERENCE_HUB` is **read-only** by rule §5; all our own work goes in `WORKSPACE/` (not yet
  created — planned for when we start building).
- Decided: MEMORY entry point = `MEMORY/README.md`. A top-level `PROJECT_INDEX.md` pointer was
  added at the project root for discoverability.

## Files created / modified

- `REFERENCE_HUB/aniyomi-app/` — cloned aniyomi app (reference, read-only)
- `REFERENCE_HUB/anime-extensions-ref/` — cloned extension repo (reference, read-only)
- `APK/anikoto-by-1118000-v3.apk` — reference APK staged (not analyzed)
- `MEMORY/README.md` — main navigation / starting guide
- `MEMORY/PROJECT_RULES.md` — all project rules
- `MEMORY/TEMPORARY_MEMORY/README.md`
- `MEMORY/research/README.md`
- `MEMORY/guides/README.md`
- `MEMORY/decisions/README.md`
- `MEMORY/issues-resolutions/README.md`
- `MEMORY/session-logs/README.md`
- `MEMORY/ext-lib/README.md`
- `MEMORY/extensions/README.md`
- `MEMORY/sites/README.md`
- `MEMORY/session-logs/2026-06-22_session-01_initial-setup.md` — this log
- `PROJECT_INDEX.md` — top-level pointer to MEMORY

## Status at end of session

- ✅ Reference repos fully cloned and verified (281 MB total).
- ✅ Reference APK staged in `APK/` (integrity verified, NOT analyzed).
- ✅ MEMORY system scaffolded with all guide files.
- ✅ All project rules saved in `MEMORY/PROJECT_RULES.md`.
- ⏳ No analysis done yet — by design (user will guide next steps).
- ⏳ `WORKSPACE/` not yet created (planned for when building begins).

## Next steps (for the next session)

Awaiting user guidance. Likely candidates (in suggested order):
1. **Study the Aniyomi app** — read `REFERENCE_HUB/aniyomi-app/source-api/` to document the
   `AnimeSource` / `AnimeCatalogueSource` / `Video` interfaces and the video player expectations.
   Promote findings to `MEMORY/research/`.
2. **Study ext-lib 16** — inventory `REFERENCE_HUB/anime-extensions-ref/lib/` and `lib-multisrc/`,
   document key extractors/helpers/themes in `MEMORY/ext-lib/`. Flag anything outdated vs latest.
3. **Pick the first target site** and do a browser-verified analysis (rule §1, §7) → `MEMORY/sites/`.
4. **Create `WORKSPACE/`** with the `_TEMPLATE` copy convention when we start building.
5. Plan the **in-app file logger** to `Download/1118000/` (rule §6).

## Open issues (still in TEMPORARY_MEMORY)

- None. No technical work was attempted beyond setup, so no unresolved issues exist yet.
