# `research/apk-reference/` — Reference APK Analysis (✅ COMPLETE)

> **Status: COMPLETE (session 10, 2026-06-22).** Both reference APKs were decompiled with jadx 1.5.3
> and deeply analyzed. This folder holds the analysis record. Per rule §1: cross-check / understanding
> only — no code copied into our extensions.

## The rule (non-negotiable)

From `MEMORY/PROJECT_RULES.md` §1:
> **Don't copy the reference APK.** Build from your own understanding and research. Even if there
> are errors, they're learning lessons. The APK is a cross-check only, not a copy source.

The analysis here is for **understanding and cross-checking only.** Findings may inform our
implementation decisions, but NO code, manifest entries, selectors, or logic is copied from the APK
into our extensions. We build from our own research against the live site + ext-lib 16 API.

## What the APK is

`SHARED/APK_REFERENCE/anikoto-by-1118000-v3.apk` (~250 KB, multiple `classes*.dex`, Android package with
APK Signing Block). Based on size + structure, this is a reference **extension** APK (not the full
Aniyomi app) — i.e. an Aniyomi-loadable extension built by `1118000` for a site (likely "anikoto"),
version 3.

## Planned analysis structure (when the user directs)

When analysis begins, create these files in this folder:

- `01-apk-overview.md` — high-level: package name, versionName/versionCode (→ ext-lib major),
  `tachiyomi.animeextension.class` meta-data (the source class), NSFW flag, signing info, dex count,
  permissions, min/target SDK. Whether it's ext-lib 14 or 16 (from `versionName` prefix).
- `02-manifest-and-source-class.md` — the `AndroidManifest.xml` (uses-feature, meta-data, any
  `*UrlActivity` deep-link intent filters), and the source class name(s) the app instantiates.
- `03-site-and-flow.md` — which site the extension targets, the URL structure it expects, the
  search → details → episodes → hoster/video flow it implements (cross-checked against the LIVE site
  via agent-browser, per rule §1).
- `04-audio-types-and-labeling.md` — ★ how it handles SUB/HSUB/DUB (rule §7), how it labels videos,
  whether it uses `SEpisode.scanlator` for sub/dub availability (rule §8). Cross-check against the
  site's actual data-types.
- `05-video-extraction.md` — which host extractors / patterns it uses, whether it uses the Hoster
  pipeline (ext-lib 16) or legacy `getVideoList(episode)` (ext-lib ≤14), how it resolves videos.
- `06-logging.md` — ★ whether/how it implements in-app file logging to `Download/1118000/` (rule §6).
  This is of special interest since `1118000` is the folder name in the rule — suggesting this APK
  may be the origin of the logging convention.
- `07-cross-check-lessons.md` — what we can LEARN from this APK (without copying): patterns to
  verify, anti-patterns to avoid, behaviors to replicate from understanding. Each lesson must cite
  the specific decompiled evidence AND be verified against our own research.

## How analysis will be done (method, when directed)

- **Decompilation tools:** `jadx` (DEX → Java), `apktool` (resources + manifest), `unzip` (raw
  structure), `aapt` (manifest binary → xml). The user will confirm which tool(s) to use and whether
  the environment has them.
- **Verification per rule §1:** every finding from the APK must be cross-checked against the LIVE
  site (agent-browser) and/or the ext-lib 16 source before being trusted. The APK is a cross-check,
  not a source of truth.
- **Promotion to mature folders:** once an analysis finding is verified AND useful, it gets promoted
  (moved, not copied) to the appropriate `MEMORY/` mature folder:
  - Site behavior → `MEMORY/sites/<sitename>/`
  - ext-lib 16 behavior confirmed → `MEMORY/ext-lib/` or `MEMORY/research/`
  - A reusable lesson → `MEMORY/research/` or `MEMORY/guides/`
  - The raw analysis stays here as the "APK cross-check record."
- **Temporary findings** (unverified hypotheses from the APK) go to `EXTENSIONS/anikoto/MEMORY/TEMPORARY_MEMORY/`
  first, per the two-tier memory model, and are promoted only after verification.

## What NOT to do

- ❌ Copy any code from the APK into our extensions.
- ❌ Copy selectors, regexes, API endpoints, or crypto keys verbatim.
- ❌ Trust the APK's behavior without verifying against the live site.
- ❌ Analyze the APK before the user directs (current state).
- ❌ Decompile more than needed to answer the specific question the user asks.

## Current contents

1. **`01-apk-overview.md`** — v3 vs v16.4 at-a-glance comparison (manifest, structure, sizes, authors, the `anikoto.cz`→`anikototv.to` domain migration, debug-vs-release, the bloat analysis).
2. **`02-video-pipeline-and-proxy.md`** — ★ the ext-lib 16 Hoster flow (discovery → parallel resolution → VidTube extractor), the two-client split, `AnikotoRC4` (key `"simple-hash"`), `MapperStreamToken` (v3 only, dropped in v16.4), and the `LocalProxyServer` (index-based URL scheme, build-from-scratch m3u8, PNG-strip two-pass algorithm, LRU cache, generation-cancellable prefetch). Complete endpoint inventory.
3. **`03-catalog-and-dtos.md`** — catalog endpoints + selectors, the 5 filters (43 genres, 6 types, 3 statuses, 2 langs, 8 sorts), the `EpisodeMeta` pipe-encoding pattern, and the 6 `@Serializable` DTOs.
4. **`04-toolkit-and-utils.md`** — the custom `extensions.utils` toolkit (Source base with v16-correct sigs, JSON×5, Preferences×9, PreferenceDelegate, LazyMutable, Collections, Date, Format) vs `keiyoushi.utils`. Why we use a minimal self-rolled toolkit.
5. **`05-cross-check-lessons.md`** — what the reference CONFIRMED about our live-site research, what it CORRECTED, the 10 techniques worth adopting, the 6 anti-patterns to avoid, the best-method synthesis, and the open live-verification items (★ all 7 resolved in session 11).
6. **`06-live-verification-results.md`** — ★ session-11 live verification of all 7 open items against `anikototv.to`. All confirmed. Stage 4 unblocked.

The best-method ADR: `MEMORY/decisions/03-best-method-to-build-extensions.md` (12-point method).

## Related

- `SHARED/APK_REFERENCE/README.md` — the APK folder itself.
- `MEMORY/PROJECT_RULES.md` §1 (don't copy), §6 (logging to `Download/1118000/`), §7 (audio types),
  §8 (episode display), §9 (APK file management).
- `MEMORY/README.md` §2 — the two-tier memory model (temp → mature promotion).