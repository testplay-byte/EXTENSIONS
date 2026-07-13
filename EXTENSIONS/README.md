# EXTENSIONS/ — Per-Extension Workspaces

> **Each Aniyomi extension we build gets its own self-contained folder here.** Everything needed
> to develop, build, and maintain one extension lives in `EXTENSIONS/<name>/` — Gradle project,
> built APKs, its own knowledge base, analysis scripts, and a quick-ref. An issue in one extension
> never affects another.

---

## Layout (per extension)

```
EXTENSIONS/<name>/
├── EXTENSION.md          ← ★ START HERE — identity, build commands, current status, key files
├── APK_INFO.md           ← full APK info sheet for the current release
├── DEV/                  ← Gradle project (source, stubs module, build config, keystore)
├── APK/                  ← built APKs (debug + release copies)
├── ANALYSIS/             ← per-extension analysis scripts + chain analysis
└── MEMORY/               ← this extension's knowledge base
    ├── README.md         ← per-extension navigation
    ├── session-logs/     ← one log per working session
    ├── sites/            ← target-site analysis (URLs, servers, audio types, CDN/WAF)
    ├── issues-resolutions/  ← resolved issues (symptom → cause → fix → verification)
    ├── modules/          ← code module docs (architecture, catalog, video pipeline, etc.)
    ├── research/         ← extension-specific research findings
    ├── workflow/         ← numbered research workflow (01-07)
    └── TEMPORARY_MEMORY/ ← raw/unverified notes (promote out when verified)
```

## Current extensions

| Extension | Folder | Status | Latest version |
|---|---|---|---|
| AniKoto 180 | `anikoto/` | ✅ All features working | v16.9, Build 7 |
| AnimePahe 180 | `animepahe/` | ✅ All features working | v16.10, Build 10 |

See `../MEMORY/EXTENSIONS.md` for the full project-wide registry.

## ★ How to build a new extension

The step-by-step build procedure lives at [`HOW_TO_BUILD_EXTENSION/README.md`](HOW_TO_BUILD_EXTENSION/README.md) —
a general, multi-step guide (analyze the website → catalog → details/episodes → playback →
build/release). **Follow it in order** when starting ANY new extension. It includes:
- When to ask the user vs. decide yourself (`HOW_TO_BUILD_EXTENSION/00-philosophy-and-rules.md`)
- A lookup table of how AniKoto solved specific problems (`HOW_TO_BUILD_EXTENSION/reference-anikoto-solutions.md` — **growable**)
- Known gotchas from 51 sessions (`HOW_TO_BUILD_EXTENSION/common-pitfalls.md` — **growable**)

## Adding a new extension

1. **Copy the template:**
   ```bash
   cp -r EXTENSIONS/_template EXTENSIONS/<new-name>
   ```
2. ★ **Read the build guide:** [`HOW_TO_BUILD_EXTENSION/README.md`](HOW_TO_BUILD_EXTENSION/README.md) — the 5-step procedure
3. **Read the source-skeleton guide:** `../MEMORY/guides/02-how-to-create-a-new-extension.md`
4. **Fill in `EXTENSION.md`** with the new extension's identity (mark unverified fields **[ANALYSIS]**).
5. **Add a row** to `../MEMORY/EXTENSIONS.md` (status: 🚧 setup).
6. **Begin Step 1 (site analysis)** in `MEMORY/sites/` — verify with a real browser (rule §1) BEFORE writing scrapers.
7. **Write session logs** in `MEMORY/session-logs/` as you go.

## Isolation principle

- **Each extension is independent.** Its Gradle project, keystore, APKs, and knowledge base are all inside its own folder.
- **Shared resources** (reference repos, ext-lib API docs, build guides, general Aniyomi research) live at the project level — see `../MEMORY/` and `../SHARED/`.
- **An issue in one extension stays in that extension's folder.** Diagnose, fix, and log it there without touching others.
- **Keystores are per-extension** (each extension can have its own signing key). Never share a keystore across extensions unless intentional.
