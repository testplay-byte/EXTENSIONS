# ADR-02: Multi-Extension Folder Architecture (revised)

> Date: 2026-06-22 (original) · **Revised: 2027-06-28 (session 52 — multi-extension restructure)** · Status: Accepted (supersedes original)

## Context

**Original (2026-06-22):** The user provided a spec for a `WORKSPACE/` directory with strict
UPPER_CASE naming, a 7-step WORKFLOW, a `DEV/` folder with per-extension subfolders, and a
workspace-level APK repository. This worked for a single extension (AniKoto).

**Revision trigger (2027-06-28):** The project's purpose is to build AND manage **multiple**
extensions, and to isolate issues to one extension. The original `WORKSPACE/` layout was
single-extension-centric: `MEMORY/session-logs/`, `MEMORY/sites/`, `MEMORY/issues-resolutions/`,
and `MEMORY/EXTENSIONS/anikoto/modules/` were shared folders that would not scale to a second
extension. The `WORKSPACE/DEV/ANIKOTO/DEVELOPMENT_CODE/` path was also deep and AniKoto-specific.

## Decision

**Move to a per-extension-isolated `EXTENSIONS/` layout.** Each extension is fully self-contained
in `EXTENSIONS/<name>/` (Gradle project, keystore, APKs, its own knowledge base, analysis, and an
`EXTENSION.md` quick-ref). Project-wide knowledge stays in `MEMORY/`. Shared binary/reference
resources move to `SHARED/`.

### The structure (as implemented)
```
/home/z/my-project/
├── EXTENSIONS/                      ← ★ per-extension workspaces (one folder per extension)
│   ├── _template/                   ← copyable scaffold for new extensions
│   └── <name>/                      ← one folder per extension, fully self-contained
│       ├── EXTENSION.md             ← per-extension quick-ref (identity, build, status)
│       ├── APK_INFO.md              ← full APK info sheet
│       ├── DEV/                     ← Gradle project (source, stubs, build config, keystore)
│       ├── APK/                     ← built APKs (debug + release)
│       ├── ANALYSIS/                ← per-extension analysis scripts
│       └── MEMORY/                  ← this extension's knowledge base
│           ├── session-logs/        ← one log per session (THIS extension only)
│           ├── sites/               ← target-site analysis (THIS extension's site)
│           ├── issues-resolutions/  ← resolved issues (THIS extension only)
│           ├── modules/             ← code module docs (THIS extension's code)
│           ├── research/            ← extension-specific research
│           ├── workflow/            ← numbered research workflow (01-07)
│           └── TEMPORARY_MEMORY/    ← raw/unverified notes (THIS extension)
├── MEMORY/                          ← project-level knowledge base (shared across ALL extensions)
│   ├── README.md  PROJECT_RULES.md  EXTENSIONS.md  (registry)
│   ├── guides/  decisions/  ext-lib/  research/  build-env/
├── SHARED/                          ← cross-extension binary/reference resources
│   ├── REFERENCE_HUB/               ← cloned reference repos (read-only)
│   └── APK_REFERENCE/               ← reference APKs (cross-check only)
├── JDK/  ANDROID_SDK/  .android-env.sh   ← shared build toolchain
├── src/  public/                    ← Next.js download webpage
└── worklog.md  PROJECT_INDEX.md  STARTUP_PROMPT.md  RESTORE.md
```

### Migration mapping (old → new)
| Old path | New path |
|---|---|
| `WORKSPACE/DEV/ANIKOTO/DEVELOPMENT_CODE/` | `EXTENSIONS/anikoto/DEV/` |
| `WORKSPACE/DEV/ANIKOTO/APK/` | `EXTENSIONS/anikoto/APK/` |
| `WORKSPACE/DEV/ANIKOTO/ANALYSIS/` | `EXTENSIONS/anikoto/ANALYSIS/` |
| `WORKSPACE/WORKFLOW/` | `EXTENSIONS/anikoto/MEMORY/workflow/` |
| `WORKSPACE/APK/` (consolidated) | merged into `EXTENSIONS/anikoto/APK/` |
| `MEMORY/session-logs/` | `EXTENSIONS/anikoto/MEMORY/session-logs/` |
| `MEMORY/sites/anikoto/` | `EXTENSIONS/anikoto/MEMORY/sites/` |
| `MEMORY/issues-resolutions/` | `EXTENSIONS/anikoto/MEMORY/issues-resolutions/` |
| `MEMORY/EXTENSIONS/anikoto/modules/` | `EXTENSIONS/anikoto/MEMORY/modules/` |
| `MEMORY/TEMPORARY_MEMORY/` | `EXTENSIONS/anikoto/MEMORY/TEMPORARY_MEMORY/` |
| `MEMORY/research/apk-reference/` (+ episode-* plans) | `EXTENSIONS/anikoto/MEMORY/research/` |
| `REFERENCE_HUB/` | `SHARED/REFERENCE_HUB/` |
| `APK/REFERENCE/` | `SHARED/APK_REFERENCE/` |
| `APK_INFO.md` (top-level) | `EXTENSIONS/anikoto/APK_INFO.md` |
| `MEMORY/guides/`, `MEMORY/decisions/`, `MEMORY/ext-lib/`, `MEMORY/research/` (general 01-05) | **unchanged** (stays project-level) |

### Why per-extension `MEMORY/` subfolders?
The two-tier memory model (TEMPORARY_MEMORY → mature folders) is preserved, but now each extension
has its OWN set of mature folders. This means:
- **Session logs** for extension A don't clutter extension B's resume flow.
- **Site analysis** is inherently per-extension (each scrapes a different site).
- **Issues-resolutions** for one extension don't confuse another's debugging.
- **Module docs** describe one extension's code structure.
A second extension simply gets its own `EXTENSIONS/<name>/MEMORY/` with the same subfolder set —
zero interference.

## Rationale

- **Isolation principle:** an issue in one extension never affects another. Everything needed to
  diagnose, fix, and log is inside that extension's folder.
- **Scalability:** adding a new extension is `cp -r EXTENSIONS/_template EXTENSIONS/<name>` — no
  shared folders to partition.
- **Easier resume:** reading one extension's `EXTENSION.md` + `MEMORY/session-logs/` gives full
  context without filtering out other extensions' history.
- **Shared stays shared:** project-wide knowledge (rules, build guides, ext-lib API, general
  Aniyomi research, reference repos, build toolchain) is correctly factored out to `MEMORY/` and
  `SHARED/` — no duplication across extensions.
- **Gradle project relocatable:** verified that the Gradle project uses relative module paths
  (`:stubs`, `:src:en:anikoto`) and `rootProject.file("anikoto-release.jks")` for the keystore, so
  moving `DEVELOPMENT_CODE/` → `EXTENSIONS/anikoto/DEV/` required no build-config changes. Build
  reproduces byte-identical APK (268,142 bytes).

## Consequences

- **Positive:** clean per-extension isolation; adding extensions is trivial; resuming one extension
  doesn't require reading others' logs.
- **Positive:** `EXTENSION.md` gives each extension a single-file quick-ref — the first thing to
  read when resuming.
- **Positive:** `MEMORY/EXTENSIONS.md` registry shows all extensions + status at a glance.
- **Positive:** the download webpage's APK API now reads from `EXTENSIONS/anikoto/DEV/build/` —
  co-located with the extension. (Future: support per-extension download selection.)
- **Negative (minor):** more path references in docs needed updating after the move. Mitigated by
  updating all navigation docs (README, PROJECT_RULES, PROJECT_INDEX, STARTUP_PROMPT, RESTORE,
  guides/04) + bulk path cleanup in extension docs.
- **Negative (minor):** the shared `MEMORY/research/` vs per-extension `EXTENSIONS/<name>/MEMORY/research/`
  split requires judgment on where a research note belongs. Rule of thumb: general Aniyomi mechanics
  (video pipeline, network layer, ext-lib) → shared; site/extension-specific → per-extension.

## Alternatives considered

1. **Keep `WORKSPACE/` and partition per-extension inside it** (e.g. `WORKSPACE/DEV/<ext>/`).
   Rejected — the deeper problem was the shared `MEMORY/` subfolders (session-logs, sites, issues),
   not just the DEV layout. A full per-extension isolation is cleaner.
2. **Keep shared `MEMORY/session-logs/` with per-extension filename prefixes.** Rejected — filename
   prefixes don't scale and make "read the latest 1-2 logs" require filtering. Per-extension
   folders are unambiguous.
3. **Single flat `EXTENSIONS/` with no per-extension `MEMORY/`.** Rejected — loses the two-tier
   memory model's per-extension granularity; issues from one extension would pollute another's space.

## References

- `EXTENSIONS/README.md` — EXTENSIONS/ overview + how to add a new extension.
- `EXTENSIONS/_template/README.md` — the scaffold.
- `EXTENSIONS/anikoto/EXTENSION.md` — AniKoto's quick-ref (example of the per-extension pattern).
- `MEMORY/README.md` — project-level navigation (rewritten for multi-ext).
- `MEMORY/EXTENSIONS.md` — extensions registry.
- `MEMORY/PROJECT_RULES.md` §5 (workspace mgmt — updated), §9 (file mgmt — updated).
- `MEMORY/guides/02-how-to-create-a-new-extension.md` — the source skeleton procedure.
- Original ADR-02 text (preserved in git history) described the single-extension `WORKSPACE/` layout.
