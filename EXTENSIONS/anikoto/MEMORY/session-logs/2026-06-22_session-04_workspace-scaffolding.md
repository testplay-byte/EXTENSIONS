# Session 04 — WORKSPACE Scaffolding

> Date: 2026-06-22 · Session #: 04 · Duration: ~medium · Timezone: America/Los_Angeles

## Goal

Implement the user's detailed `WORKSPACE/` folder-architecture spec:
- `WORKSPACE/` with `WORKFLOW/` (6 numbered step folders + Step 7 Final Release described in text),
  `DEV/` (with `TEMPORARY_DOCUMENTATION/` + per-extension folders), and a workspace-level `APK/`.
- Strict UPPER_CASE_WITH_UNDERSCORES naming. Living, adaptive WORKFLOW guide.
- Set everything up properly, improve where needed, document.
- Do NOT install the Android SDK yet — the user will direct how/where (ask at the end).

## What was done

### A. Created the full WORKSPACE folder structure
```
WORKSPACE/
├── WORKFLOW/{01_WEBSITE_RESEARCH, 02_ARCHITECTURE_DESIGN, 03_CATALOG_EPISODES_MANAGEMENT,
│             04_VIDEO_EXTRACTION_PLAYBACK, 05_PREFERENCES, 06_BUILD_TEST, 07_FINAL_RELEASE}/
├── DEV/{TEMPORARY_DOCUMENTATION, _TEMPLATE/{APK, DEVELOPMENT_CODE}}/
└── APK/
```
`.gitkeep` added to empty leaf dirs (`_TEMPLATE/APK`, `_TEMPLATE/DEVELOPMENT_CODE`, `APK`) to
preserve structure in git.

### B. Two improvements (beyond the literal spec) — both flagged in ADR-02
1. **`07_FINAL_RELEASE/` folder** — the spec describes Step 7 in text but lists only folders 01–06.
   Gave it a folder for release-specific docs (signing, versioning, distribution, post-release).
   Reversible if the user prefers.
2. **`DEV/_TEMPLATE/` instead of pre-creating `ANIMIX/`** — no site chosen yet, so a copyable
   scaffold is neutral. When the user picks a site: `cp -r _TEMPLATE <EXTENSION_NAME>/`.

### C. Reconciled the two `APK/` folders
The spec adds `WORKSPACE/APK/` + `DEV/<EXT>/APK/`, which overlaps with rule §9's "/APK/ at project
root." Reconciliation (logged as a rule §3 workflow revision in ADR-02):
- `/APK/REFERENCE/` (root) = reference APKs only (never copy).
- `/WORKSPACE/DEV/<EXT>/APK/` = per-extension build output (accumulates).
- `/WORKSPACE/APK/` = consolidated latest-good builds the user installs.

Build flow: `./gradlew assembleDebug` → `DEV/<EXT>/APK/` → copy latest good → `WORKSPACE/APK/` → user.

### D. Wrote 13 documentation files (all structured templates, ready to fill as we build)

**WORKSPACE top-level + WORKFLOW:**
- `WORKSPACE/README.md` — workspace overview, layout, the APK-folder reconciliation, MEMORY relationship, prerequisites (Android SDK pending), status.
- `WORKSPACE/WORKFLOW/README.md` — the living-guide overview, 7-step table, how it connects to MEMORY, the "living guide" principle (build the first extension → fill templates → reuse for future), naming convention, when to write here vs MEMORY.
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/README.md` — site analysis purpose, artifacts, process (agent-browser, all servers, 3 audio types), MEMORY cross-refs, fill-in template.
- `WORKSPACE/WORKFLOW/02_ARCHITECTURE_DESIGN/README.md` — scaffolding blueprints, dependency matrix (incl. the v16 lib/ Video-ctor port), env checklist, MEMORY cross-refs.
- `WORKSPACE/WORKFLOW/03_CATALOG_EPISODES_MANAGEMENT/README.md` — popular/latest/search/filters/details/episodes, ★ scanlator for sub/dub (rule §8), MEMORY cross-refs.
- `WORKSPACE/WORKFLOW/04_VIDEO_EXTRACTION_PLAYBACK/README.md` — hoster pipeline, ★ local Python prototyping (per spec), extractor choices, server/quality switching, dedup, MEMORY cross-refs.
- `WORKSPACE/WORKFLOW/05_PREFERENCES/README.md` — the 4 spec prefs (server/audio/quality/title-lang) + keiyoushi.utils Preferences API, MEMORY cross-refs.
- `WORKSPACE/WORKFLOW/06_BUILD_TEST/README.md` — debug builds, ★ the in-app file logger to `Download/1118000/` (rule §6), test cases, retroactive updates, MEMORY cross-refs.
- `WORKSPACE/WORKFLOW/07_FINAL_RELEASE/README.md` — release checklist, signing, versioning, distribution, debug-vs-release table, MEMORY cross-refs.

**DEV:**
- `WORKSPACE/DEV/README.md` — how to start a new extension (`cp -r _TEMPLATE <NAME>`), the two subfolders (APK + DEVELOPMENT_CODE), naming convention.
- `WORKSPACE/DEV/TEMPORARY_DOCUMENTATION/README.md` — volatile engineering notes; ★ distinction from `MEMORY/TEMPORARY_MEMORY/` (engineering scratch vs unverified research); lifecycle (discard or promote).
- `WORKSPACE/DEV/_TEMPLATE/README.md` — how to use the scaffold, the post-copy structure (mirrors `guides/01-build-setup-for-ext-lib-16.md`), key v16 reminders (ext-lib coord, Java 17, versionName, Source.kt cleanup, Video-ctor port, AnimeHttpSource not ParsedAnimeHttpSource).

**APK:**
- `WORKSPACE/APK/README.md` — workspace-level build repository role, the 3-APK-folder table, build flow, naming, git policy.

### E. Updated MEMORY + PROJECT_INDEX
- `MEMORY/decisions/02-workspace-folder-architecture.md` — ADR-02: the architecture decision, 2 improvements, APK-folder reconciliation (rule §9 workflow revision), rationale, consequences, alternatives.
- `MEMORY/decisions/README.md` — added ADR-02 entry.
- `MEMORY/README.md` §7 — added WORKSPACE section to quick-links; bumped latest-session-log pointer.
- `PROJECT_INDEX.md` — updated top-level layout table (added `REFERENCE_HUB/ext-lib-aniyomiorg/`, split `APK/REFERENCE/`, expanded `WORKSPACE/` description).

## Key findings / decisions

1. **WORKSPACE architecture adopted as specified** — the user's spec is clear and well-structured; implemented verbatim with 2 improvements (ADR-02).
2. **Two `APK/` folders reconciled** — root `/APK/REFERENCE/` (reference) vs `WORKSPACE/APK/` + `DEV/<EXT>/APK/` (built). Rule §9 workflow revision logged (ADR-02).
3. **`TEMPORARY_DOCUMENTATION/` is distinct from `MEMORY/TEMPORARY_MEMORY/`** — engineering scratch during a build vs unverified research pending promotion. Both have clear purposes; documented in `DEV/TEMPORARY_DOCUMENTATION/README.md`.
4. **WORKFLOW = living guide built by doing** — all 7 step READMEs are structured templates now, cross-referencing the relevant MEMORY docs. They get filled as we build the first extension, then serve as the reusable playbook.
5. **Step 04's Python prototyping** (per spec) — local Python scripts to isolate HTTP responses / dissect payloads BEFORE porting to Kotlin. Documented a `python-prototypes/` convention per extension. This is a faster iteration loop than the full Android build cycle.
6. **Step 06's in-app logger** (rule §6) — the `Download/1118000/` file logger is a first-extension deliverable (doesn't exist yet). Design requirements captured in `06_BUILD_TEST/README.md`: session-based files, raw site responses + parsing steps + player state, structured format, NOT logcat.
7. **Android SDK NOT installed** — per the user's instruction. Will ask how/where to install it (they flagged it needs a specific folder to avoid wasting space).

## Files created / modified

New (14):
- `WORKSPACE/README.md`
- `WORKSPACE/WORKFLOW/README.md`
- `WORKSPACE/WORKFLOW/01_WEBSITE_RESEARCH/README.md`
- `WORKSPACE/WORKFLOW/02_ARCHITECTURE_DESIGN/README.md`
- `WORKSPACE/WORKFLOW/03_CATALOG_EPISODES_MANAGEMENT/README.md`
- `WORKSPACE/WORKFLOW/04_VIDEO_EXTRACTION_PLAYBACK/README.md`
- `WORKSPACE/WORKFLOW/05_PREFERENCES/README.md`
- `WORKSPACE/WORKFLOW/06_BUILD_TEST/README.md`
- `WORKSPACE/WORKFLOW/07_FINAL_RELEASE/README.md`
- `WORKSPACE/DEV/README.md`
- `WORKSPACE/DEV/TEMPORARY_DOCUMENTATION/README.md`
- `WORKSPACE/DEV/_TEMPLATE/README.md`
- `WORKSPACE/APK/README.md`
- `MEMORY/decisions/02-workspace-folder-architecture.md`
- `MEMORY/session-logs/2026-06-22_session-04_workspace-scaffolding.md` — this log

Modified (4):
- `MEMORY/README.md` §7 (WORKSPACE section added to quick-links; latest-session pointer bumped)
- `MEMORY/decisions/README.md` (ADR-02 entry added)
- `PROJECT_INDEX.md` (top-level layout table updated)

## Status at end of session

- ✅ Full `WORKSPACE/` folder structure created per spec (+ 2 improvements).
- ✅ All 13 WORKSPACE documentation files written (structured templates, MEMORY cross-referenced).
- ✅ ADR-02 records the architecture decision + APK-folder reconciliation (rule §9 revision).
- ✅ `PROJECT_INDEX.md` + `MEMORY/README.md` + `decisions/README.md` updated.
- ⏳ **Android SDK NOT installed** — pending user direction (user will tell me how/where).
- ⏳ No extension started yet (no site chosen).
- ⏳ Open build-verification items from session 02 still stand (JitPack serves v16, JDK 17 reads it,
  tapmoc compat, `core/Source.kt` legacy-override deletion, R8 keep rules) — close at first real build.

## Next steps (for the next session)

1. **Android SDK install** — user will direct how/where (specific folder, avoid wasted space). After
   install: set `local.properties` `sdk.dir` in each extension's `DEVELOPMENT_CODE/`.
2. **User picks the first target site** → start at `WORKFLOW/01_WEBSITE_RESEARCH/` (agent-browser
   analysis → `MEMORY/sites/<sitename>/`).
3. **Scaffold the first extension** (`cp -r DEV/_TEMPLATE DEV/<NAME>`) → `WORKFLOW/02_ARCHITECTURE_DESIGN/`.
4. **Build a minimal stub** to close the open verification items (JitPack v16, JDK 17, etc.) BEFORE
   investing in full site logic.
5. **Reference APK analysis** — when the user directs (structure ready in `research/apk-reference/`).

## Open issues (still in TEMPORARY_MEMORY)

- None in `MEMORY/TEMPORARY_MEMORY/`. The Android SDK install is a pending user-directed task (not an
  issue). The open build-verification items are documented in `guides/01-build-setup-for-ext-lib-16.md`
  §6 and `research/03-...md` §8 — they close at the first real build.

## Honest notes

- **No code written** — purely scaffolding + docs, per the user's instruction ("for now do whats told.
  and setup everything properly and improve too if needed.").
- **Two improvements made** (07_FINAL_RELEASE folder; _TEMPLATE scaffold) — both flagged in ADR-02 as
  reversible if the user disagrees. I did NOT pre-create `ANIMIX/` because no site is chosen.
- **The APK-folder overlap with rule §9** was a real ambiguity. I resolved it via the 3-location
  reconciliation table (root=reference, DEV/<EXT>/=per-extension, WORKSPACE/=consolidated) and
  logged it as a rule §3 workflow revision in ADR-02 rather than silently changing rule §9.
- **Each WORKFLOW step README cross-references MEMORY** — this leverages the existing verified
  research (ext-lib API, player pipeline, build setup, etc.) rather than duplicating it. The WORKFLOW
  is the "how to build" (process); MEMORY is the "what's true" (facts).
- **The WORKFLOW READMEs are templates, not content.** They define purpose + artifacts + process +
  fill-in structure, but the actual content (specific to a site) gets filled as we build the first
  extension. This matches the spec's "living, evolving guide" intent.
- **Python prototyping (step 04)** and the **in-app file logger (step 06)** are both first-extension
  deliverables that don't exist yet — their design requirements are captured, but implementation
  waits for the first build.
