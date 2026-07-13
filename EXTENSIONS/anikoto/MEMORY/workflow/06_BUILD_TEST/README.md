# Step 06 — Build & Test

> **Status: TEMPLATE.** Filled in when we build the first extension. This step covers the iterative
> debug-build loop + the in-app file logger (project rule §6).

## Purpose (from spec)
- Generate iterative **Debug APKs** configured to output comprehensive, formatted runtime logs
  directly to the target mobile directory: **`Download/1118000`**.
- Logs must capture: **raw site responses, parsing execution steps, and player state transformations**
  for rapid debugging.
- **Learnings or unmapped edge cases discovered during user testing are retroactively integrated into
  earlier workflow stages.**

## What belongs here
- `build-commands.md` — the exact `./gradlew` commands for debug builds (per extension).
- `logger-design.md` — the in-app file-logger design (a `lib/filelogger` module or `core/` util):
  file naming, format, what gets logged, rotation, the `Download/1118000/` path creation.
- `logger-impl.md` — how the logger is wired into `hosterListParse` / `videoListParse` / `resolveVideo`
  / `sortVideos` / network interceptors.
- `test-cases.md` — the golden-path test cases the user runs per build (search → details → episodes →
  each hoster → each audio type → each quality → play).
- `bug-log.md` — per-build bug reports + resolutions (promotes to `EXTENSIONS/anikoto/MEMORY/issues-resolutions/` once
  verified fixed).
- `retroactive-updates.md` — learnings that get pushed back into earlier WORKFLOW steps (per spec).

## How to do this step (process)
1. **Build debug APK:**
   ```bash
   cd EXTENSIONS/<extension_name>/DEV
   ./gradlew :src:<lang>:<name>:assembleDebug
   # output: src/<lang>/<name>/build/outputs/apk/debug/aniyomi-<lang>.<name>-v16.<code>.apk
   ```
2. **Copy to per-extension APK folder:**
   ```bash
   cp .../apk/debug/*.apk ../APK/                       # EXTENSIONS/<EXTENSION>/APK/
   ```
3. **User installs + tests** (`adb install -r ...` or tap APK). Enable "Untrusted extensions" in
   Aniyomi settings (debug-built extensions are untrusted — verified in
   `MEMORY/research/03-...md` §7).
4. **Check logs at `Download/1118000/`** on device — the in-app logger writes there (NOT logcat).
5. **Iterate**: fix → rebuild → retest. One change at a time (rule §2).
6. **On any bug**: log in `bug-log.md`, investigate, fix, verify. Once verified, promote the
   resolution to `EXTENSIONS/<extension>/MEMORY/issues-resolutions/`.
7. **Retroactive updates**: if a test reveals a gap in an earlier step (e.g. step 01 missed a server
   path), update that step's docs AND log the revision in `MEMORY/decisions/` (rule §3).

## The in-app file logger (project rule §6) — design requirements
- **Path:** `Download/1118000/` (create the folder if it doesn't exist).
- **NOT logcat** — write logs to that folder from inside the extension.
- **Session-based file naming** — one file per app session, with date + extension info.
- **Content:** device info, extension info, date, AND:
  - Raw site responses (request URL + headers + response status + body excerpt).
  - Parsing execution steps (which selector matched, which DTO decoded, how many items).
  - Player state transformations (hoster list → video list → resolveVideo → play / error).
- **Detail level:** enough to pinpoint issues — not too noisy, not too silent.
- **Format:** structured (one event per line, timestamped, with a clear event-type tag).

> This logger is a **first-extension deliverable** — it doesn't exist yet. When we build the first
> extension, we'll create a `lib/filelogger` module (or add it to `core/`) and wire it into every
> network call + parse method + resolveVideo. The design goes in `logger-design.md` + `logger-impl.md`.

## MEMORY cross-references
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` §7 (build commands), §8 (install & test).
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` §7 (loader compat: `versionName = "16.x"`,
  debug = Untrusted).
- `MEMORY/PROJECT_RULES.md` §6 (logging to `Download/1118000/`), §2 (one change at a time), §9 (APK file mgmt).

## Fill-in template
```
06_BUILD_TEST/
└── <EXTENSION_NAME>/
    ├── build-commands.md
    ├── logger-design.md
    ├── logger-impl.md
    ├── test-cases.md
    ├── bug-log.md
    └── retroactive-updates.md
```

## Status
Template only. Populated when the first extension enters the build-test loop.
