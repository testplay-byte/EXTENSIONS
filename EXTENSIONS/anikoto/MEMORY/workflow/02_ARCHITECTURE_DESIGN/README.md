# Step 02 — Architecture Design

> **Status: TEMPLATE.** Filled in when we build the first extension.

## Purpose (from spec)
- Handle blueprints, scaffolding configurations, and resource requirement planning.
- Verify that the target environment dependencies in the DEV folder are properly aligned.

## What belongs here
- `scaffold-plan.md` — the folder/package layout for the extension (which `lib/` modules to port,
  which `core/` utils to use, whether to use a multisrc theme or standalone).
- `dependency-matrix.md` — ext-lib 16 + which yuzono `lib/` modules + their porting status (remember:
  `lib/` modules need the Video-ctor fix for v16 — see `MEMORY/ext-lib/03-...md` §8).
- `env-checklist.md` — JDK 17 ✓, Android SDK ✓ (pending install), Gradle wrapper version, AGP
  version, Kotlin version. Confirm alignment with `MEMORY/guides/01-build-setup-for-ext-lib-16.md`.
- `build-config.md` — the `libs.versions.toml` / `kei.versions.toml` / `settings.gradle.kts` /
  `PluginExtensionLegacy.kt` changes for this extension (the ONE `versionName = "16.$versionCode"`
  change + the aniyomi-lib coordinate swap).

## How to do this step (process)
1. Decide: standalone extension OR multisrc theme consumer? (If a yuzono multisrc theme fits the site,
   consume it; else standalone.)
2. List the `lib/` modules you'll need (from step 01's video-flow analysis: which host extractors?
   `playlistutils`? `cryptoaes`? `m3u8server`? `universalextractor`?).
3. For each `lib/` module: check if it needs the v16 Video-ctor port (almost all do). Plan the port.
4. Copy `EXTENSIONS/_template/` → `EXTENSIONS/<name>/`.
5. Scaffold the Gradle project in `<name>/DEV/` (from
   `MEMORY/guides/01-build-setup-for-ext-lib-16.md`).
6. Verify the env: JDK 17, Android SDK, Gradle wrapper. **Do NOT proceed to step 03 until the env
   builds a minimal stub.**

## MEMORY cross-references
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` — the Gradle config (★ the authoritative source).
- `MEMORY/research/02-reference-extension-build-and-structure.md` — yuzono build system we're adapting.
- `MEMORY/ext-lib/01-...source-and-versioning.md` — the dependency coordinate + v14→v16 migration.
- `MEMORY/ext-lib/03-...md` §8 — the `lib/` Video-ctor porting requirement.
- `MEMORY/research/05-keiyoushi-utils-core.md` §7.2 — `Source.kt` legacy-override deletion for v16.

## Fill-in template
```
02_ARCHITECTURE_DESIGN/
└── <EXTENSION_NAME>/
    ├── scaffold-plan.md
    ├── dependency-matrix.md
    ├── env-checklist.md
    └── build-config.md
```

## Status
Template only. Populated when the first extension is scaffolded.
