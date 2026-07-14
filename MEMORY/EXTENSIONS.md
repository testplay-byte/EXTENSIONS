# Extensions Registry

> **The project-wide index of every extension we build.** One row per extension with status and
> pointers. Read this to see what exists and where each lives.

---

## Active extensions

| Extension | Folder | Language | Status | Version | Target site | extClass |
|---|---|---|---|---|---|---|
| **AniKoto 180** | [`EXTENSIONS/anikoto/`](../EXTENSIONS/anikoto/) | `en` | ✅ All features working | v16.9 (Build 7, versionCode 9, versionId 11) | anikototv.to | `...en.anikoto.Anikoto` |
| **AnimePahe 180** | [`EXTENSIONS/animepahe/`](../EXTENSIONS/animepahe/) | `en` | ✅ All features working | v16.10 (Build 10, versionCode 10, versionId 1) | animepahe.pw | `...en.animepahe.AnimePahe` |
| **MKissa 180** | [`EXTENSIONS/mkissa/`](../EXTENSIONS/mkissa/) | `en` | 🚧 In progress (3/6 video servers working) | v16.17 (Build 17, versionCode 17, versionId 1) | mkissa.to | `...en.mkissa.MKissa` |
| **AniDB 180** | [`EXTENSIONS/anidb/`](../EXTENSIONS/anidb/) | `en` | 🚧 In progress (initial build, debug only) | v16.1 (Build 1, versionCode 1, versionId 1) | anidb.app | `...en.anidb.AniDB` |
| **Re:ANIME 180** | [`EXTENSIONS/reanime/`](../EXTENSIONS/reanime/) | `en` | 🚧 In progress (initial build) | v16.1 (Build 1, versionCode 1, versionId 1) | reanime.to | `...en.reanime.Reanime` |
| **Miruro 180** | [`EXTENSIONS/miruro/`](../EXTENSIONS/miruro/) | `en` | 🚧 In progress (pipe API + 11 providers + 4 sub-types, HLS working) | v16.1 (Build 1, versionCode 1, versionId 1) | miruro.tv | `...en.miruro.Miruro` |

## Status legend

- ✅ **All features working** — stable, published-ready
- 🚧 **In progress** — under active development
- ⚠️ **Has known issues** — see the extension's `MEMORY/issues-resolutions/`
- 🧪 **Experimental** — early prototype
- 🗄️ **Archived** — no longer maintained

## Per-extension quick-ref

Each extension has an `EXTENSION.md` at its folder root — the single file to read when resuming:

- AniKoto 180 → [`EXTENSIONS/anikoto/EXTENSION.md`](../EXTENSIONS/anikoto/EXTENSION.md)
- AnimePahe → [`EXTENSIONS/animepahe/EXTENSION.md`](../EXTENSIONS/animepahe/EXTENSION.md)
- MKissa → [`EXTENSIONS/mkissa/EXTENSION.md`](../EXTENSIONS/mkissa/EXTENSION.md)
- AniDB → [`EXTENSIONS/anidb/EXTENSION.md`](../EXTENSIONS/anidb/EXTENSION.md)
- Re:ANIME → [`EXTENSIONS/reanime/EXTENSION.md`](../EXTENSIONS/reanime/EXTENSION.md)

## ★ How to build a new extension

The step-by-step procedure lives at [`EXTENSIONS/HOW_TO_BUILD_EXTENSION/README.md`](../EXTENSIONS/HOW_TO_BUILD_EXTENSION/README.md) —
the multi-step guide (analyze → catalog → details/episodes → playback → build/release). Follow it
in order when starting ANY new extension.

## Adding a new extension

1. Copy the template: `cp -r EXTENSIONS/_template EXTENSIONS/<new-name>`
2. ★ **Read the build guide**: [`EXTENSIONS/HOW_TO_BUILD_EXTENSION/README.md`](../EXTENSIONS/HOW_TO_BUILD_EXTENSION/README.md) — the 5-step procedure
3. Follow [`MEMORY/guides/02-how-to-create-a-new-extension.md`](guides/02-how-to-create-a-new-extension.md) for source-skeleton details
4. Fill in the new extension's `EXTENSION.md`
5. **Add a row to the table above** (this file)
6. Begin Step 1 (site analysis) in `EXTENSIONS/<new-name>/MEMORY/sites/` — verify with a real browser (rule §1)

## Isolation principle

Each extension is fully self-contained in `EXTENSIONS/<name>/` (Gradle project, keystore, APKs,
knowledge base). Shared resources (reference repos, ext-lib API, build guides, general research)
live at the project level in `MEMORY/` and `SHARED/`. An issue in one extension never affects others.
