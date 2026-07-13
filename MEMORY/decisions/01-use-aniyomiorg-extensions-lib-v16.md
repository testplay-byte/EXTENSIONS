# ADR-01: Use `aniyomiorg/extensions-lib:v16` (NOT the komikku fork)

> Date: 2026-06-22 · Status: Accepted

## Context

The yuzono reference repo (`SHARED/REFERENCE_HUB/anime-extensions-ref/`) pins its ext-lib dependency to:
```
com.github.komikku-app:aniyomi-extensions-lib:bdc8184127
```
The user flagged that this pinned version "may NOT have all ext-lib 16 features" (Hoster, TimeStamp,
sortHosters/sortVideos, resolveVideo) and asked us to verify which version actually contains them and
document how to get it.

Four ext-lib repos were probed and compared (see
`MEMORY/ext-lib/01-ext-lib-16-source-and-versioning.md` §2 for the full table):

| Repo | libVersion | Has Hoster? | Has resolveVideo/sortHosters/sortVideos? | `@since ext-lib 16` count |
|---|---|---|---|---|
| **`aniyomiorg/extensions-lib`** | **v16** | ✅ | ✅ | **21** |
| `komikku-app/aniyomi-extensions-lib` (yuzono's pin) | 14-era | ❌ | ❌ | 0 |
| `komikku-app/extensions-lib` | 1.4.5.1 | ❌ | ❌ | 0 |
| `keiyoushi/extensions-lib` | 1.4.5 | ❌ | ❌ | 0 |

The komikku fork (what yuzono uses) is stale at the ext-lib 14 era. Only the official
`aniyomiorg/extensions-lib` at tag `v16` (commit `782a5a6b`) has the ext-lib 16 Hoster-based video
pipeline.

## Decision

**Our extensions will depend on `com.github.aniyomiorg:extensions-lib:v16`** (JitPack coordinate),
NOT the komikku fork.

Concretely, in `gradle/libs.versions.toml`:
```toml
aniyomi-lib = { module = "com.github.aniyomiorg:extensions-lib", version = "v16" }
```

And the build-logic's `PluginExtensionLegacy.kt` sets `versionName = "16.$versionCode"` (not `"14."`)
so the Aniyomi app's `AnimeExtensionLoader` (which accepts `libVersion` in `[12, 16]`) loads our
extensions as ext-lib 16 and uses the Hoster flow.

## Rationale

- ext-lib 16 is where the Hoster/Video/resolveVideo/sortHosters/sortVideos/FetchType/seasons API
  lives — the entire modern video pipeline the user wants to build on.
- The komikku fork lacks all of these. Compiling against it makes the Hoster API unreachable.
- The official `aniyomiorg/extensions-lib` is the repo the Aniyomi app's own `source-api/` is derived
  from (the ext-lib's `dokkatoo` `sourceLink` points at `aniyomiorg/aniyomi/.../source-api/`). Using
  it guarantees API/runtime alignment.
- Tag `v16` is the release tag the official README instructs (`libVersion = 'v16'`).

## Consequences

- **Positive:** full access to ext-lib 16 features (Hoster lazy resolution, sortHosters/sortVideos,
  FetchType.Seasons, TimeStamp chapters, etc.).
- **Positive:** API matches the app's runtime `source-api` — no compile-vs-runtime field surprises
  beyond the documented stub subset (see `MEMORY/research/03-...discrepancy.md`).
- **Negative:** the v16 jar is Java 17 bytecode (`.jitpack.yml` `jdk: openjdk17`,
  `library/build.gradle.kts` `VERSION_17`). Our build MUST use JDK 17 (yuzono's Java 11 + tapmoc
  setup won't read Java 17 class files). → `kei.versions.java = "17"`.
- **Negative:** legacy ext-lib 14 patterns in yuzono's `core/Source.kt` (overrides of
  `videoListParse(response)`/`videoUrlParse` etc. that throw `UnsupportedOperationException`) won't
  compile on v16 (those methods are absent from the v16 interface). Must delete them when adapting
  `core/`.
- **Negative:** `ParsedAnimeHttpSource` is `@Deprecated` on v16 and lacks hoster/video selector hooks
  in the published lib. New extensions extend `AnimeHttpSource` directly.
- **Cost:** migrating any yuzono extension we reference requires: dependency swap, versionName bump
  to `"16."`, legacy ctor → named-arg `Video(...)`, `sort()` → `sortVideos()`, `quality` →
  `videoTitle`, `getVideoList(episode)`+`videoListParse(response)` → `hosterListParse`+
  `videoListParse(response, hoster)`+optional `resolveVideo`.
- **Verification pending:** confirm JitPack actually serves `:v16` and JDK 17 reads it, at the first
  real build. Fallback if JitPack fails: `includeBuild("SHARED/REFERENCE_HUB/ext-lib-aniyomiorg")` +
  `compileOnly(project(":library"))`.

## Alternatives considered

1. **Stay on komikku fork (bdc8184127).** Rejected — no Hoster/TimeStamp/resolveVideo; can't build
   the modern pipeline the user wants.
2. **keiyoushi/extensions-lib.** Rejected — same stale state (1.4.5), no ext-lib 16.
3. **`includeBuild` the ext-lib source instead of JitPack.** Held as fallback. Pros: no JitPack
   dependency, recompiles with our Java. Cons: couples our build to ext-lib source tree, slower
   config. Try JitPack first.

## References

- `MEMORY/ext-lib/01-ext-lib-16-source-and-versioning.md` — full versioning/sourcing guide.
- `MEMORY/ext-lib/02-ext-lib-16-api-reference.md` — the v16 API.
- `MEMORY/research/03-compile-time-vs-runtime-discrepancy.md` — stub vs runtime, loader compat, Java 17.
- `MEMORY/guides/01-build-setup-for-ext-lib-16.md` — build config implementing this decision.
- Verification commands: `MEMORY/ext-lib/01-...md` §8.
