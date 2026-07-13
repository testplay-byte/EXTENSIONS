# Session 02 — ext-lib 16 Research & Documentation

> Date: 2026-06-22 · Session #: 02 · Duration: ~long · Timezone: America/Los_Angeles

## Goal

Understand the Aniyomi extensions ecosystem thoroughly enough to build any extension easily, focused
on the **latest ext-lib 16** (not v14/v15). Specifically research & document:
1. The ext-lib 16 API (Hoster, Video, TimeStamp, AnimeHttpSource suspend methods, sortHosters/sortVideos, resolveVideo).
2. How the Aniyomi video player consumes `Video` objects (which fields it reads).
3. How reference extensions are structured (build system, manifest, source class patterns).
4. The build setup needed for ext-lib 16 (plugins, dependencies, versioning).

Also address the user's flag: the pinned `aniyomi-extensions-lib` version (`bdc8184127`) may NOT
have all ext-lib 16 features — verify which version actually does and document how to get it.

## What was done

### A. Traced the ext-lib dependency & discovered the versioning truth
- Read `REFERENCE_HUB/anime-extensions-ref/gradle/libs.versions.toml` → yuzono pins
  `aniyomi-lib = com.github.komikku-app:aniyomi-extensions-lib:bdc8184127`.
- Cloned `komikku-app/aniyomi-extensions-lib` → `REFERENCE_HUB/aniyomi-extensions-lib/`. Verified
  `bdc8184127` IS its latest commit. Grepped for ext-lib 16 features → **NONE present** (no Hoster,
  no TimeStamp, no sortHosters/sortVideos, no resolveVideo). Confirmed the user's warning.
- Found the aniyomi app's own `source-api/` has `@since extensions-lib 16` annotations + `Hoster.kt`
  + `resolveVideo` etc. — so ext-lib 16 IS defined in the app. But that's the runtime, not a
  publishable compile dependency.
- Probed 4 candidate ext-lib repos via `git ls-remote` (no API rate limit): `aniyomiorg/extensions-lib`
  (exists, HEAD `59418e2`), `keiyoushi/extensions-lib` (exists, 1.4.5), `komikku-app/extensions-lib`
  (exists, 1.4.5.1), `aniyomiorg/aniyomi-extensions-lib` (404 — doesn't exist).
- Shallow-cloned all 3 existing candidates + compared. **Only `aniyomiorg/extensions-lib` has ext-lib
  16**: `libVersion = 'v16'` in README, `Hoster.kt` present, `TimeStamp` in `Video.kt`, `sortHosters`/
  `sortVideos`/`resolveVideo` in `AnimeHttpSource.kt`, 21 `@since extensions-lib 16` annotations.
  Replaced the shallow clone with a full clone → `REFERENCE_HUB/ext-lib-aniyomiorg/`.
- Verified tags: `1.0, 1.1, 1.2, 1.3, 13, 14, 16, 16-rc1..rc4, v16`. Tag `v16` → commit `782a5a6b`
  (annotated); tag `16` → `9822a39e`. README instructs `libVersion = 'v16'`.

### B. Deep-read the authoritative ext-lib v16 source (myself, for verification)
- Read `REFERENCE_HUB/ext-lib-aniyomiorg/library/src/main/java/.../` — `Hoster.kt`, `Video.kt`,
  `FetchType.kt`, `AnimeHttpSource.kt`, `AnimeSource.kt`, `ParsedAnimeHttpSource.kt`,
  `ResolvableAnimeSource.kt`, `SAnime.kt`, `SEpisode.kt`, `library/build.gradle.kts`, `README.md`,
  `.jitpack.yml`, `gradle/libs.versions.toml`, `gradle.properties`.
- **Key discovery:** the published v16 is a **STUB library** — every method body throws
  `Exception("Stub!")`. The app's runtime `source-api/` is a fuller superset. Extensions `compileOnly`
  the stub; the app injects real impls at runtime.
- Documented the exact compile-time vs runtime field/method differences (e.g. published `Video` has
  no `status`/`url`/`quality` getters and the legacy ctor is `@Deprecated(level=ERROR)`; published
  `Hoster` is a plain `class` with no `copy()`/`status`; published `ParsedAnimeHttpSource` lacks
  `hosterListSelector`/`videoListSelector`).
- Verified ext-lib's own build config: `.jitpack.yml` `jdk: openjdk17`, `library/build.gradle.kts`
  `JavaVersion.VERSION_17` + `JvmTarget.JVM_17` + `compileSdk = 36`. **The published v16 jar is Java
  17 bytecode** — has real implications for our build (Java 11 can't read it).

### C. Dispatched two Explore subagents (parallel) for breadth
- **Task 3-A (video-player-consumption):** mapped how the Aniyomi app's **mpv-based** player (NOT
  ExoPlayer — zero hits for ExoPlayer/Media3 in the codebase) consumes `Video`/`Hoster`. Documented
  the 3-stage ext-lib 16 pipeline (`getHosterList` → `getVideoList(hoster)` → `resolveVideo`), the
  new-vs-legacy flow detection (`EpisodeLoader.checkHasHosters` by method name), the full resolution
  flow diagram, every `Video` field the player reads, auto-selection + failure-fallback logic, and
  subtitle/audio/timestamp mpv wiring. Agent appended to `worklog.md`.
- **Task 3-B (reference-extension-structure):** mapped yuzono's build system — `settings.gradle.kts`,
  `libs.versions.toml`/`kei.versions.toml`, all 5 build-logic convention plugins
  (`PluginAndroidBase`/`PluginExtensionLegacy`/`PluginLibrary`/`PluginMultiSrc`/`PluginSpotless`), the
  `core/` utils module (13 `keiyoushi.utils.*` files), shared `common/AndroidManifest.xml`, concrete
  extension anatomy (`src/en/tokuzilla/`), multisrc theme anatomy (`zorotheme` + consumer `zoro`),
  the 73 `lib/` helper modules, and the versioning scheme. (Agent hit a 429 rate-limit on first
  attempt; succeeded on retry.) Agent appended to `worklog.md`.

### D. Verified the remaining open questions myself
- **Extension-loader version-compat rule** — read `AnimeExtensionLoader.kt:47-48,254-260`:
  `LIB_VERSION_MIN = 12`, `LIB_VERSION_MAX = 16`; `libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()`;
  rejects if `< 12 || > 16`. So our `versionName` MUST be `"16.<code>"`. Also confirmed
  `AnimeExtensionManager.kt:379` update detection and `AnimeExtensionLoader.kt:263-274` signature-trust
  check (debug-built extensions = "Untrusted", user must enable them).
- **tapmoc** — `com.gradleup.tapmoc:0.4.2`, used via `configureJavaCompatibility(<java>)` in
  `gradle/build-logic/.../configurations/Kotlin.kt`. It's a Java-compat shim for newer AGP.
- **Published v16 jar bytecode** — Java 17 (confirmed above). Conclusion: our build needs JDK 17
  (yuzono's Java 11 + tapmoc won't read Java 17 class files). Documented as a hard requirement +
  open verification item (confirm at first build) + fallback (`includeBuild` the ext-lib source).

### E. Wrote the documentation (8 new files + 4 index updates)

New files:
- `MEMORY/ext-lib/01-ext-lib-16-source-and-versioning.md` — ★ the answer to "how do I get ext-lib 16":
  4-repo comparison table, tags, version history, the exact dependency coordinate
  (`com.github.aniyomiorg:extensions-lib:v16`), v14→v16 migration checklist, verification commands.
- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` — authoritative compile-time API reference:
  `AnimeSource`, `AnimeCatalogueSource`, `AnimeHttpSource` (all ext-lib 16 methods with signatures),
  `Hoster`, `Video`, `Track`, `TimeStamp`, `FetchType`, `SAnime`, `SEpisode`,
  `ParsedAnimeHttpSource` (deprecated), `ResolvableAnimeSource`, `ConfigurableAnimeSource`,
  `AnimeSourceFactory`, + "what to implement" checklist.
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md` — runtime behavior: mpv player, 3-stage
  pipeline, full resolution flow diagram, Video/Hoster field-read tables, auto-selection logic,
  subtitle/audio/timestamp wiring, practical implications, honest surprises.
- `MEMORY/research/02-reference-extension-build-and-structure.md` — yuzono build system map,
  version catalogs, all 5 convention plugins, `core/` utils, shared manifest, tokuzilla anatomy,
  zorotheme multisrc, 73 lib/ modules inventory, versioning, "to create a new extension" checklist.
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` — ★ critical: field-by-field diff
  (published v16 stub vs app runtime source-api), what's usable in extension code, loader compat
  rule, Java 17 requirement, signature-trust note, "safe to use" / "do NOT use" summary.
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` — the Gradle config implementing v16: full
  `libs.versions.toml`/`kei.versions.toml`/`settings.gradle.kts`, the ONE `PluginExtensionLegacy.kt`
  change (`versionName = "16.$versionCode"`), open verification items, build/install/test commands.
- `MEMORY/guides/02-how-to-create-a-new-extension.md` — per-extension file layout, `build.gradle`
  template, full `AnimeHttpSource` skeleton (all ext-lib 16 methods), `SAnime`/`SEpisode` filling,
  audio-type labeling + dedup (rule §7), `scanlator` for sub/dub (rule §8), optional `*UrlActivity`,
  build/test, verified-pitfalls checklist.
- `MEMORY/decisions/01-use-aniyomiorg-extensions-lib-v16.md` — ADR: use official
  `aniyomiorg/extensions-lib:v16` not the komikku fork. Context, rationale, consequences,
  alternatives, references.

Index updates:
- `MEMORY/ext-lib/README.md` — added "Current contents (read in this order)" + companion-doc links.
- `MEMORY/research/README.md` — added "Current contents (read in this order)".
- `MEMORY/guides/README.md` — added "Current contents" with both guides + verification caveat.
- `MEMORY/decisions/README.md` — added the ADR entry.
- `MEMORY/README.md` §7 — populated "Quick links" with all 8 new docs grouped by category.

## Key findings / decisions

1. **The komikku fork (`bdc8184127`, what yuzono uses) is ext-lib 14 era — NO Hoster/TimeStamp/
   resolveVideo/sortHosters/sortVideos.** Confirmed the user's warning. (ADR-01)
2. **Only `aniyomiorg/extensions-lib` at tag `v16` has ext-lib 16.** Dependency:
   `compileOnly 'com.github.aniyomiorg:extensions-lib:v16'`. (ADR-01)
3. **The published v16 is a STUB library** (all methods throw `Exception("Stub!")`). Extensions
   compile against the stub; the app provides real impls at runtime. You can only use fields/methods
   in the published stub at compile time, even though the app has more at runtime.
4. **Critical compile-vs-runtime gaps:** published `Video` has no `status`/`url`/`quality`; legacy
   ctor is `@Deprecated(level=ERROR)` (won't compile); published `Hoster` is a plain `class` (no
   `copy()`/`status`); published `ParsedAnimeHttpSource` lacks hoster/video selector hooks → extend
   `AnimeHttpSource` directly.
5. **The player is mpv-based, NOT ExoPlayer** (zero ExoPlayer/Media3 hits in the app). Video fields
   read by player: `videoUrl`, `videoTitle`, `headers`, `preferred`, `subtitleTracks`, `audioTracks`,
   `timestamps`, `mpvArgs`, `initialized`. NOT read: `resolution`, `bitrate`, `ffmpegStreamArgs`,
   `ffmpegVideoArgs`, `internalData`, `status`.
6. **Loader compat:** `LIB_VERSION_MIN=12`, `LIB_VERSION_MAX=16`, parsed from `versionName` prefix.
   Our `versionName` MUST be `"16.<code>"`. Debug builds = "Untrusted" (user must enable).
7. **Java 17 required** to consume the v16 jar (Java 17 bytecode). yuzono's Java 11 + tapmoc setup
   won't work for v16. → `kei.versions.java = "17"`.
8. **`ParsedAnimeHttpSource` is `@Deprecated` on v16** — new extensions extend `AnimeHttpSource`
   directly with Jsoup.
9. **Stale ext-lib KDoc bug:** the `sortVideos()` example uses `it.quality.contains(...)` but
   `quality` doesn't exist on v16 `Video`. Use `it.videoTitle`.
10. **`SEpisode.scanlator` exists on v16** — this is where sub/dub availability goes (rule §8),
    confirmed renders below episode name in the UI.

## Files created / modified

New (10):
- `REFERENCE_HUB/aniyomi-extensions-lib/` — komikku fork clone (bdc8184127, for comparison)
- `REFERENCE_HUB/ext-lib-aniyomiorg/` — ★ official ext-lib full clone (has v16 tag)
- `REFERENCE_HUB/ext-lib-keiyoushi/` — keiyoushi fork shallow clone (comparison)
- `REFERENCE_HUB/ext-lib-komikku-new/` — komikku "new" fork shallow clone (comparison)
- `MEMORY/ext-lib/01-ext-lib-16-source-and-versioning.md`
- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md`
- `MEMORY/research/01-aniyomi-video-pipeline-and-player.md`
- `MEMORY/research/02-reference-extension-build-and-structure.md`
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md`
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md`
- `MEMORY/guides/02-how-to-create-a-new-extension.md`
- `MEMORY/decisions/01-use-aniyomiorg-extensions-lib-v16.md`
- `MEMORY/session-logs/2026-06-22_session-02_ext-lib-16-research.md` — this log

Modified (5):
- `MEMORY/README.md` — §7 quick-links populated
- `MEMORY/ext-lib/README.md` — index + companion links
- `MEMORY/research/README.md` — index
- `MEMORY/guides/README.md` — index + verification caveat
- `MEMORY/decisions/README.md` — ADR entry

(Note: subagents appended to `worklog.md` per the Task-ID protocol — not duplicated in MEMORY.)

## Status at end of session

- ✅ ext-lib 16 source located, cloned (official `aniyomiorg/extensions-lib:v16`), and verified to
  contain Hoster/TimeStamp/resolveVideo/sortHosters/sortVideos/FetchType/seasons.
- ✅ The ext-lib 16 compile-time API fully documented (signatures copied from source, not paraphrased).
- ✅ Video player consumption documented (mpv, 3-stage pipeline, field-read table, resolution flow).
- ✅ Reference extension structure documented (build system, manifest, source patterns, core utils,
  lib helpers, multisrc themes, versioning).
- ✅ Build setup for ext-lib 16 documented (the exact Gradle config + the ONE versionName change).
- ✅ Compile-time vs runtime discrepancy documented (the critical "what you can't use" reference).
- ✅ All open questions from session 01's "next steps" #1 and #2 are answered.
- ✅ ADR-01 records the dependency decision with rationale.
- ⏳ **NOT verified by a real build yet** — the Gradle config is derived from source reading. The
  first real `./gradlew assembleDebug` (when WORKSPACE is set up) will confirm: (a) JitPack serves
  `:v16`, (b) JDK 17 reads the jar, (c) tapmoc compat, (d) `core/Source.kt` legacy overrides must
  be deleted, (e) R8 keep rules suffice. These are explicitly listed as open verification items in
  `guides/01` §6 and `research/03` §8.
- ⏳ `WORKSPACE/` not yet created (user's plan: DEV + workflow folders, next session).
- ⏳ No target site chosen yet (awaiting user guidance).
- ⏳ In-app file logger to `Download/1118000/` not yet implemented (rule §6, future).

## Next steps (for the next session)

Awaiting user guidance on the WORKSPACE layout (the user mentioned: DEV folder for development +
WORKFLOW folder to be built alongside the first extension, capturing the site→analyze→plan→build
process). Suggested order:
1. **Create `WORKSPACE/`** with the layout in `guides/01-build-setup-for-ext-lib-16.md` §1 (adapt
   yuzono's build-logic: swap the aniyomi-lib coordinate, bump `java` to 17, change `versionName`
   to `"16."`, strip `core/Source.kt` legacy overrides, create `_TEMPLATE/`).
2. **Verify the build** with a minimal stub extension (just to confirm JitPack + JDK 17 + AGP 9.2.1
   work together) BEFORE investing in a real site. This closes the open verification items.
3. **Pick the first target site** (user to provide URL) → run the site-analysis workflow (agent-browser
   verified, per rule §1/§7) → `MEMORY/sites/`.
4. **Build the first real extension** following `guides/02`, capturing the process into the
   `WORKFLOW/` folder as we go (the reusable site→analyze→plan→build playbook for future extensions).
5. Implement the **in-app file logger** to `Download/1118000/` (rule §6) — likely a `lib/filelogger`
   module wired into `hosterListParse`/`videoListParse`/`resolveVideo`.

## Open issues (still in TEMPORARY_MEMORY)

- None currently in `TEMPORARY_MEMORY/`. The open verification items (JitPack serving v16, JDK 17
  build, tapmoc compat, `core/Source.kt` cleanup, R8 keep rules) are documented in
  `guides/01-build-setup-for-ext-lib-16.md` §6 and `research/03-...discrepancy.md` §8, and will be
  resolved at the first real build. If any fails, the resolution goes to `TEMPORARY_MEMORY/` first,
  then `issues-resolutions/` once verified.

## Honest notes

- **No code was written this session** — purely research + documentation, per the user's instruction
  ("for now, focus on understanding and documenting").
- **Everything is sourced.** Every API claim cites a file path + line range in `REFERENCE_HUB/`. The
  two subagent reports were cross-checked against my own direct reads of the ext-lib v16 source
  (discrepancies found and documented — e.g. the published stub is smaller than the app runtime;
  the `sortVideos` KDoc example is stale).
- **The build config is NOT yet build-verified.** I've been explicit about this in `guides/01` §6
  and `research/03` §8. Do not claim it works until the first real `./gradlew assembleDebug`
  succeeds. If JitPack fails, the documented fallback is `includeBuild` of the ext-lib source
  (which we have fully cloned).
- **The Next.js / React-Flight parser in yuzono's `core/`** (`NextJs.kt` + `reactFlight/`) is
  advanced and I didn't deep-dive it — it's only relevant if a target site uses Next.js RSC. Noted
  for future reference.
