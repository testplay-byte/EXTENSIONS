# _template/ — New Extension Scaffold

> **Copy this folder to start a new extension:** `cp -r EXTENSIONS/_template EXTENSIONS/<new-name>`
>
> Then read `../../MEMORY/guides/02-how-to-create-a-new-extension.md` for the full procedure.

---

## What's here

This template provides the **folder skeleton** and a fill-in `EXTENSION.md`. It does NOT include a
Gradle project — scaffold one in `DEV/` by following guide 02 (or copy the build system from an
existing extension like `../anikoto/DEV/` and adapt the package/extClass/source).

## Steps to start a new extension

1. `cp -r EXTENSIONS/_template EXTENSIONS/<new-name>` (use lowercase-kebab-case for the folder name)
2. Fill in `EXTENSION.md` with the new identity (display name, package, extClass, target site)
3. Scaffold `DEV/` (Gradle project) — follow `../../MEMORY/guides/02-how-to-create-a-new-extension.md`
4. **Do site analysis FIRST** in `MEMORY/sites/` — verify with a real browser (rule §1) before any code
5. Register the extension in `../../MEMORY/EXTENSIONS.md`
6. Begin session logs in `MEMORY/session-logs/`

## Folder purposes

| Path | Purpose |
|---|---|
| `EXTENSION.md` | ★ Fill-in quick-ref (identity, build, status) — the first file to read when resuming |
| `DEV/` | Gradle project (source, stubs, build config, keystore) — scaffold per guide 02 |
| `APK/` | Built APKs (debug + release) — populated by `./gradlew assembleDebug/Release` |
| `ANALYSIS/` | Per-extension analysis scripts (Python prototypes, chain analysis) |
| `MEMORY/session-logs/` | One log per session (mandatory end-of-session artifact) |
| `MEMORY/sites/` | Target-site analysis (URLs, servers, audio types, CDN/WAF) |
| `MEMORY/issues-resolutions/` | Resolved issues (symptom → cause → fix → verification) |
| `MEMORY/modules/` | Code module docs (architecture, catalog, video pipeline, etc.) |
| `MEMORY/research/` | Extension-specific research findings |
| `MEMORY/workflow/` | Numbered research workflow (01-07) |
| `MEMORY/TEMPORARY_MEMORY/` | Raw/unverified notes — promote out when verified |

## After scaffolding

- Update this `README.md` or delete it (it's template-only guidance).
- The real navigation for your extension should be in `MEMORY/README.md` (create it modeled on `../anikoto/MEMORY/README.md`).
